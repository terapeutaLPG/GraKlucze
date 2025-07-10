package com.example.kluczegra

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager

class WiFiStateReceiver : BroadcastReceiver() {

    companion object {
        // Dozwolone sieci
        private val ALLOWED_NETWORKS = listOf("Igor", "Igor_5")
    }

    override fun onReceive(context: Context, intent: Intent) {
        val preferencesManager = PreferencesManager(context)

        // Sprawdź czy monitoring jest włączony
        if (!preferencesManager.isMonitoringEnabled) {
            return
        }

        // Sprawdź czy są zapisane sieci domowe
        val homeNetworks = preferencesManager.getAllHomeNetworks()
        if (homeNetworks.isEmpty()) {
            return
        }

        when (intent.action) {
            WifiManager.WIFI_STATE_CHANGED_ACTION -> {
                val wifiState =
                    intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)

                if (wifiState == WifiManager.WIFI_STATE_DISABLED) {
                    // Wi-Fi zostało wyłączone - może oznaczać wyjście z domu
                    handleWiFiDisconnection(context, preferencesManager)
                } else if (wifiState == WifiManager.WIFI_STATE_ENABLED) {
                    // Wi-Fi zostało włączone - sprawdź połączenie
                    handleWiFiConnection(context, preferencesManager)
                }
            }

            WifiManager.NETWORK_STATE_CHANGED_ACTION -> {
                // Obsługa zmian stanu sieci
                val networkInfo =
                    intent.getParcelableExtra<android.net.NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)

                if (networkInfo != null) {
                    if (networkInfo.isConnected) {
                        // Nowe połączenie - sprawdź czy to sieć domowa
                        handleWiFiConnection(context, preferencesManager)
                    } else {
                        // Rozłączenie
                        handleWiFiDisconnection(context, preferencesManager)
                    }
                }
            }
        }
    }

    private fun handleWiFiDisconnection(context: Context, preferencesManager: PreferencesManager) {
        // Uruchom usługę, która sprawdzi czy to była dozwolona domowa sieć
        val serviceIntent = Intent(context, WiFiMonitorService::class.java)

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Nie można uruchomić usługi w tle
        }
    }

    private fun handleWiFiConnection(context: Context, preferencesManager: PreferencesManager) {
        // Uruchom usługę, która sprawdzi połączenie i wyśle powiadomienie jeśli potrzeba
        val serviceIntent = Intent(context, WiFiMonitorService::class.java)

        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Nie można uruchomić usługi w tle
        }
    }
}
