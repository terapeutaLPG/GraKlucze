package com.example.kluczegra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.kluczegra.ui.theme.KluczeGraTheme

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager

    // Launcher do requestowania uprawnień
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            checkLocationServices()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)

        setContent {
            KluczeGraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }

        // Sprawdź uprawnienia przy starcie
        checkPermissions()
    }

    @Composable
    fun MainScreen() {
        val context = LocalContext.current
        var homeSSID by remember { mutableStateOf(preferencesManager.homeSSID ?: "") }
        var isMonitoring by remember { mutableStateOf(preferencesManager.isMonitoringEnabled) }
        var currentSSID by remember { mutableStateOf(getCurrentSSID()) }
        var showPermissionWarning by remember { mutableStateOf(!hasAllPermissions()) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nagłówek
            Text(
                text = "🔑 Przypomnienie o Kluczach",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // Ostrzeżenie o uprawnieniach
            if (showPermissionWarning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            "⚠️ Wymagane uprawnienia",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            "Aplikacja potrzebuje uprawnień do lokalizacji i powiadomień, aby działać poprawnie.",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Button(
                            onClick = {
                                checkPermissions()
                                showPermissionWarning = !hasAllPermissions()
                            },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Sprawdź uprawnienia")
                        }
                    }
                }
            }

            // Aktualna sieć Wi-Fi
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📶 Aktualne połączenie Wi-Fi:", fontWeight = FontWeight.Medium)
                    Text(
                        text = currentSSID ?: "Brak połączenia lub brak uprawnień",
                        fontSize = 16.sp,
                        color = if (currentSSID != null) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error
                    )
                    Button(
                        onClick = { currentSSID = getCurrentSSID() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("Odśwież")
                    }
                }
            }

            // Konfiguracja domowej sieci
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🏠 Domowa sieć Wi-Fi:", fontWeight = FontWeight.Medium)

                    OutlinedTextField(
                        value = homeSSID,
                        onValueChange = { homeSSID = it },
                        label = { Text("Nazwa sieci (SSID)") },
                        placeholder = { Text("np. MojDom_WiFi") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                homeSSID = currentSSID ?: ""
                            },
                            enabled = currentSSID != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Użyj aktualnej")
                        }

                        Button(
                            onClick = {
                                preferencesManager.homeSSID = homeSSID.trim()
                            },
                            enabled = homeSSID.trim().isNotEmpty(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Zapisz")
                        }
                    }
                }
            }

            // Przełącznik monitorowania
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🔍 Monitorowanie aktywne", fontWeight = FontWeight.Medium)
                        Text(
                            "Otrzymuj powiadomienia gdy wyjdziesz z domu",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isMonitoring,
                        onCheckedChange = { enabled ->
                            isMonitoring = enabled
                            preferencesManager.isMonitoringEnabled = enabled

                            if (enabled && hasAllPermissions() && homeSSID.trim().isNotEmpty()) {
                                startWiFiMonitoring()
                            } else if (!enabled) {
                                stopWiFiMonitoring()
                            }
                        },
                        enabled = hasAllPermissions() && homeSSID.trim().isNotEmpty()
                    )
                }
            }

            // Status aplikacji
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("ℹ️ Status aplikacji:", fontWeight = FontWeight.Medium)

                    val statusText = when {
                        !hasAllPermissions() -> "❌ Brak wymaganych uprawnień"
                        homeSSID.trim().isEmpty() -> "❌ Nie ustawiono domowej sieci Wi-Fi"
                        !isMonitoring -> "⏸️ Monitorowanie wyłączone"
                        else -> "✅ Aplikacja gotowa do działania"
                    }

                    Text(
                        text = statusText,
                        color = when {
                            statusText.contains("✅") -> MaterialTheme.colorScheme.primary
                            statusText.contains("⏸️") -> MaterialTheme.colorScheme.onSurfaceVariant
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
                }
            }

            // Przycisk testowy
            if (isMonitoring && hasAllPermissions()) {
                Button(
                    onClick = { sendTestNotification() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🧪 Wyślij testowe powiadomienie")
                }
            }
        }
    }

    private fun getCurrentSSID(): String? {
        return try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            var ssid = wifiInfo.ssid

            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }

            if (ssid == "<unknown ssid>" || ssid.isNullOrBlank()) null else ssid
        } catch (e: SecurityException) {
            null
        }
    }

    private fun hasAllPermissions(): Boolean {
        val permissions = listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.POST_NOTIFICATIONS
        } else null

        return permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        } && (notificationPermission == null ||
               ContextCompat.checkSelfPermission(this, notificationPermission) == PackageManager.PERMISSION_GRANTED)
    }

    private fun checkPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        } else {
            checkLocationServices()
        }
    }

    private fun checkLocationServices() {
        // Na Android 10+ sprawdź czy usługi lokalizacji są włączone
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                // Nie można otworzyć ustawień lokalizacji
            }
        }
    }

    private fun startWiFiMonitoring() {
        val serviceIntent = Intent(this, WiFiMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun stopWiFiMonitoring() {
        val serviceIntent = Intent(this, WiFiMonitorService::class.java)
        stopService(serviceIntent)
    }

    private fun sendTestNotification() {
        // Zapisz poprzedni czas powiadomienia i resetuj, aby test działał
        val previousTime = preferencesManager.lastNotificationTime
        preferencesManager.lastNotificationTime = 0L

        // Symuluj rozłączenie z domową siecią
        preferencesManager.wasConnectedToHome = true

        val serviceIntent = Intent(this, WiFiMonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Przywróć poprzedni czas po krótkiej chwili
        android.os.Handler(mainLooper).postDelayed({
            preferencesManager.lastNotificationTime = previousTime
        }, 2000)
    }
}