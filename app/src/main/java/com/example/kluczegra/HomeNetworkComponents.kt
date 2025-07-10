package com.example.kluczegra

import android.content.Context
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeNetworkField(
    index: Int,
    label: String,
    currentSSID: String?,
    preferencesManager: PreferencesManager
) {
    val context = LocalContext.current
    var networkSSID by remember { mutableStateOf(preferencesManager.getHomeNetwork(index)) }
    var availableNetworks by remember { mutableStateOf<List<String>>(emptyList()) }
    var showDropdown by remember { mutableStateOf(false) }
    var isScanning by remember { mutableStateOf(false) }
    var showCustomInput by remember { mutableStateOf(false) }

    // Automatyczne skanowanie przy pierwszym załadowaniu
    LaunchedEffect(Unit) {
        scanForNetworks(context) { networks ->
            availableNetworks = networks
        }
    }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        // Pole wyboru sieci z dropdown
        ExposedDropdownMenuBox(
            expanded = showDropdown,
            onExpandedChange = { showDropdown = it }
        ) {
            OutlinedTextField(
                value = networkSSID,
                onValueChange = { networkSSID = it },
                label = { Text(label) },
                placeholder = { Text("Wybierz lub wpisz nazwę sieci") },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor(),
                trailingIcon = {
                    Row {
                        IconButton(
                            onClick = {
                                isScanning = true
                                scanForNetworks(context) { networks ->
                                    availableNetworks = networks
                                    isScanning = false
                                }
                            }
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Skanuj sieci")
                            }
                        }
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropdown)
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
            )

            ExposedDropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false }
            ) {
                // Opcja dodania własnej sieci
                DropdownMenuItem(
                    text = {
                        Text(
                            "➕ Dodaj własną sieć",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {
                        showCustomInput = true
                        showDropdown = false
                    }
                )

                if (availableNetworks.isNotEmpty()) {
                    HorizontalDivider()

                    availableNetworks.forEach { network ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("📶 ")
                                    Text(network)
                                    if (network == currentSSID) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "(aktualna)",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            onClick = {
                                networkSSID = network
                                showDropdown = false
                            }
                        )
                    }
                } else {
                    DropdownMenuItem(
                        text = { Text("Brak dostępnych sieci") },
                        onClick = { },
                        enabled = false
                    )
                }
            }
        }

        // Dialog dodawania własnej sieci
        if (showCustomInput) {
            CustomNetworkDialog(
                onDismiss = { showCustomInput = false },
                onConfirm = { customNetwork ->
                    networkSSID = customNetwork
                    showCustomInput = false
                }
            )
        }

        // Przyciski akcji
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (currentSSID != null) {
                        networkSSID = currentSSID
                    }
                },
                enabled = currentSSID != null,
                modifier = Modifier.weight(1f)
            ) {
                Text("Użyj aktualnej")
            }

            Button(
                onClick = {
                    preferencesManager.setHomeNetwork(index, networkSSID.trim())

                    // Automatycznie wyślij testowe powiadomienie po zapisaniu sieci
                    if (networkSSID.trim().isNotEmpty()) {
                        sendTestNotificationForNetwork(context, networkSSID.trim())
                    }
                },
                enabled = networkSSID.trim().isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Zapisz")
            }
        }

        // Info o zapisanej sieci
        if (networkSSID.isNotEmpty()) {
            Text(
                text = "Zapisana sieć: $networkSSID",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun CustomNetworkDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var customNetworkName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj własną sieć") },
        text = {
            Column {
                Text("Wpisz nazwę sieci Wi-Fi (SSID):")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customNetworkName,
                    onValueChange = { customNetworkName = it },
                    label = { Text("Nazwa sieci") },
                    placeholder = { Text("np. MojDom_WiFi") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(customNetworkName.trim()) },
                enabled = customNetworkName.trim().isNotEmpty()
            ) {
                Text("Dodaj")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Anuluj")
            }
        }
    )
}

private fun scanForNetworks(context: Context, onResult: (List<String>) -> Unit) {
    try {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

        // Sprawdź czy Wi-Fi jest włączone
        if (!wifiManager.isWifiEnabled) {
            onResult(emptyList())
            return
        }

        // Uruchom skanowanie
        val success = wifiManager.startScan()

        if (success) {
            // Pobierz wyniki skanowania
            val scanResults = wifiManager.scanResults
            val networkNames = scanResults
                .filter { it.SSID.isNotEmpty() && it.SSID != "<unknown ssid>" }
                .map { result ->
                    var ssid = result.SSID
                    // Usuń cudzysłowy jeśli są
                    if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
                        ssid = ssid.substring(1, ssid.length - 1)
                    }
                    ssid
                }
                .distinct()
                .sortedBy { it.lowercase() }

            onResult(networkNames)
        } else {
            onResult(emptyList())
        }
    } catch (e: SecurityException) {
        // Brak uprawnień do skanowania
        onResult(emptyList())
    } catch (e: Exception) {
        // Inne błędy
        onResult(emptyList())
    }
}

private fun sendTestNotificationForNetwork(context: Context, networkName: String) {
    try {
        val preferencesManager = PreferencesManager(context)

        // Zapisz poprzedni czas powiadomienia i resetuj, aby test działał
        val previousTime = preferencesManager.lastNotificationTime
        preferencesManager.lastNotificationTime = 0L

        // Symuluj połączenie z domową siecią
        preferencesManager.wasConnectedToHome = true
        preferencesManager.lastConnectedNetwork = networkName

        // Uruchom usługę do wysłania powiadomienia
        val serviceIntent = android.content.Intent(context, WiFiMonitorService::class.java).apply {
            putExtra("test_network", networkName)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Przywróć poprzedni czas po krótkiej chwili
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            preferencesManager.lastNotificationTime = previousTime
        }, 3000)

    } catch (e: Exception) {
        // Nie można uruchomić usługi - może brakować uprawnień
    }
}
