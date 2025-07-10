package com.example.kluczegra

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun HomeNetworkField(
    index: Int,
    label: String,
    currentSSID: String?,
    preferencesManager: PreferencesManager
) {
    val allowedNetworks = listOf("Igor", "Igor_5")
    var networkSSID by remember { mutableStateOf(preferencesManager.getHomeNetwork(index)) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        OutlinedTextField(
            value = networkSSID,
            onValueChange = { newValue ->
                if (allowedNetworks.contains(newValue) || newValue.isEmpty()) {
                    networkSSID = newValue
                }
            },
            label = { Text(label) },
            placeholder = { Text("Igor lub Igor_5") },
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
                    if (currentSSID != null && allowedNetworks.contains(currentSSID)) {
                        networkSSID = currentSSID
                    }
                },
                enabled = currentSSID != null && allowedNetworks.contains(currentSSID),
                modifier = Modifier.weight(1f)
            ) {
                Text("Użyj aktualnej")
            }

            Button(
                onClick = {
                    if (allowedNetworks.contains(networkSSID.trim()) || networkSSID.trim().isEmpty()) {
                        preferencesManager.setHomeNetwork(index, networkSSID.trim())
                    }
                },
                enabled = allowedNetworks.contains(networkSSID.trim()) || networkSSID.trim().isEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Zapisz")
            }
        }
    }
}
