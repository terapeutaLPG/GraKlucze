package com.example.kluczegra

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class WiFiMonitorService : Service() {
    
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var wifiManager: WifiManager
    private lateinit var preferencesManager: PreferencesManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    companion object {
        const val CHANNEL_ID = "wifi_monitor_channel"
        const val NOTIFICATION_ID = 1
        const val KEYS_NOTIFICATION_ID = 2
        
        // Minimalne opóźnienie między powiadomieniami (5 minut)
        const val MIN_NOTIFICATION_INTERVAL = 5 * 60 * 1000L
        
        // Dozwolone sieci
        private val ALLOWED_NETWORKS = listOf("Igor", "Igor_5")
    }
    
    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        preferencesManager = PreferencesManager(this)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createForegroundNotification())
        startNetworkMonitoring()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Monitorowanie Wi-Fi",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitoruje połączenie z domową siecią Wi-Fi"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔑 Klucze - Monitorowanie aktywne")
            .setContentText("Sprawdzam połączenie z domową siecią Wi-Fi")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }
    
    private fun startNetworkMonitoring() {
        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                checkWiFiConnection()
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)
                onWiFiDisconnected()
            }
        }
        
        networkCallback?.let {
            connectivityManager.registerNetworkCallback(request, it)
        }
        
        // Sprawdź stan początkowy
        checkWiFiConnection()
    }
    
    private fun checkWiFiConnection() {
        val homeSSID = preferencesManager.homeSSID ?: return
        val currentSSID = getCurrentSSID()
        
        // Sprawdź czy to dozwolona sieć
        if (currentSSID != null && isAllowedNetwork(currentSSID) && 
            currentSSID.equals(homeSSID, ignoreCase = true)) {
            // Połączony z dozwoloną domową siecią
            preferencesManager.wasConnectedToHome = true
        }
    }
    
    private fun onWiFiDisconnected() {
        val homeSSID = preferencesManager.homeSSID ?: return
        
        // Sprawdź czy był połączony z domową siecią
        if (preferencesManager.wasConnectedToHome) {
            // Sprawdź czy nie jest nadal połączony (może przełączył się na inną sieć)
            val currentSSID = getCurrentSSID()
            if (currentSSID == null || 
                !currentSSID.equals(homeSSID, ignoreCase = true) ||
                !isAllowedNetwork(currentSSID)) {
                sendKeysReminder()
                preferencesManager.wasConnectedToHome = false
            }
        }
    }
    
    private fun isAllowedNetwork(ssid: String): Boolean {
        return ALLOWED_NETWORKS.contains(ssid)
    }
    
    private fun getCurrentSSID(): String? {
        try {
            val wifiInfo = wifiManager.connectionInfo
            var ssid = wifiInfo.ssid
            
            // Usuń cudzysłowy jeśli są
            if (ssid != null && ssid.startsWith("\"") && ssid.endsWith("\"")) {
                ssid = ssid.substring(1, ssid.length - 1)
            }
            
            return if (ssid == "<unknown ssid>" || ssid.isNullOrBlank()) null else ssid
        } catch (e: SecurityException) {
            // Brak uprawnień do lokalizacji
            return null
        }
    }
    
    private fun sendKeysReminder() {
        val currentTime = System.currentTimeMillis()
        val lastNotificationTime = preferencesManager.lastNotificationTime
        
        // Sprawdź czy minęło wystarczająco czasu od ostatniego powiadomienia
        if (currentTime - lastNotificationTime < MIN_NOTIFICATION_INTERVAL) {
            return
        }
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔑 Pamiętaj o kluczach!")
            .setContentText("Wygląda na to, że wyszedłeś z domu. Masz klucze?")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .build()
        
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(KEYS_NOTIFICATION_ID, notification)
        
        preferencesManager.lastNotificationTime = currentTime
    }
}
