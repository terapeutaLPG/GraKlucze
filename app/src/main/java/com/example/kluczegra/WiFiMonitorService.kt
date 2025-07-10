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
    private lateinit var soundManager: SoundManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    companion object {
        const val CHANNEL_ID = "wifi_monitor_channel"
        const val NOTIFICATION_ID = 1
        const val KEYS_NOTIFICATION_ID = 2
        
        // Minimalne opóźnienie między powiadomieniami (5 minut)
        const val MIN_NOTIFICATION_INTERVAL = 5 * 60 * 1000L
    }
    
    override fun onCreate() {
        super.onCreate()
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        preferencesManager = PreferencesManager(this)
        soundManager = SoundManager(this)

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
        soundManager.release()
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
        // Monitoruj połączenia Wi-Fi dla zapisanych sieci domowych
        val wifiRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                // Sprawdź czy to jedna z zapisanych sieci domowych
                checkForHomeNetworkConnection()
            }
            
            override fun onLost(network: Network) {
                super.onLost(network)

                // Sprawdź czy utracono połączenie z domową siecią
                checkForHomeNetworkDisconnection()
            }
        }
        
        networkCallback?.let { callback ->
            connectivityManager.registerNetworkCallback(wifiRequest, callback)
        }
        
        // Sprawdź stan początkowy
        checkForHomeNetworkConnection()
    }
    
    private fun checkWiFiConnection() {
        val homeNetworks = preferencesManager.getAllHomeNetworks()
        if (homeNetworks.isEmpty()) return

        val currentSSID = getCurrentSSID()
        
        // Sprawdź czy to dozwolona sieć domowa
        if (currentSSID != null && preferencesManager.isHomeNetwork(currentSSID)) {
            // Połączony z dozwoloną domową siecią
            preferencesManager.wasConnectedToHome = true

            // Sprawdź czy to nowe połączenie
            if (preferencesManager.lastConnectedNetwork != currentSSID) {
                preferencesManager.lastConnectedNetwork = currentSSID
                sendConnectionNotification(currentSSID)
            }
        }
    }
    
    private fun onWiFiDisconnected() {
        val homeNetworks = preferencesManager.getAllHomeNetworks()
        if (homeNetworks.isEmpty()) return

        // Sprawdź czy był połączony z domową siecią
        if (preferencesManager.wasConnectedToHome) {
            // Sprawdź czy nie jest nadal połączony (może przełączył się na inną sieć)
            val currentSSID = getCurrentSSID()
            if (currentSSID == null || 
                !preferencesManager.isHomeNetwork(currentSSID)) {
                sendKeysReminder()
                preferencesManager.wasConnectedToHome = false
                preferencesManager.lastConnectedNetwork = ""
            }
        }
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
        
        // Odtwórz dźwięk ostrzeżenia przy wyjściu z domu
        soundManager.playExitAlert()

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

    private fun sendConnectionNotification(networkName: String) {
        // Odtwórz łagodny dźwięk przypomnienia przy powrocie do domu
        soundManager.playSoftReminder()

        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("💡 Czy masz klucze?")
            .setContentText("Połączono z siecią $networkName. Sprawdź czy masz klucze!")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300))
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(KEYS_NOTIFICATION_ID + 1, notification)
    }

    // === FUNKCJE DLA AUTOMATYCZNEGO URUCHAMIANIA PRZY INTERNECIE ===

    private fun onInternetConnected(network: Network) {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        if (networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {

            // Za każdym razem gdy mamy internet - automatycznie uruchom pytanie o klucze
            launchKeysCheckAutomatically("Odzyskano połączenie z internetem")
        }
    }

    private fun launchKeysCheckAutomatically(reason: String) {
        val currentTime = System.currentTimeMillis()
        val lastNotificationTime = preferencesManager.lastNotificationTime

        // Krótszy interwał - 2 minuty między powiadomieniami
        val interval = 2 * 60 * 1000L
        if (currentTime - lastNotificationTime < interval) {
            return
        }

        // AUTOMATYCZNIE uruchom aplikację z pytaniem o klucze
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("auto_launched", true)
            putExtra("trigger_reason", reason)
        }

        // Uruchom aplikację natychmiast
        startActivity(intent)

        // Odtwórz dźwięk
        soundManager.playExitAlert()

        // Wyślij powiadomienie z brzęczeniem
        sendAutomaticKeysNotification(reason)

        preferencesManager.lastNotificationTime = currentTime
    }

    private fun sendAutomaticKeysNotification(reason: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔑 CZY MASZ KLUCZE?")
            .setContentText("$reason - Sprawdź czy masz klucze!")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300, 200, 300)) // Intensywne wibracje
            .setDefaults(Notification.DEFAULT_SOUND) // Dźwięk systemowy
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(KEYS_NOTIFICATION_ID + 3, notification)
    }

    // === FUNKCJE DLA TRYBU AGRESYWNEGO ===

    private fun checkInternetConnection(network: Network) {
        val networkCapabilities = connectivityManager.getNetworkCapabilities(network)
        if (networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {

            // Mamy połączenie z internetem - automatycznie uruchom aplikację
            launchAggressiveKeysCheck("Wykryto połączenie z internetem")
        }
    }

    private fun onInternetDisconnected() {
        // W trybie agresywnym każda utrata internetu może oznaczać wyjście z domu
        sendAggressiveKeysReminder()
    }

    private fun checkCurrentInternetStatus() {
        val activeNetwork = connectivityManager.activeNetwork
        if (activeNetwork != null) {
            val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            if (networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                // Już mamy połączenie - sprawdź czy to nowe
                launchAggressiveKeysCheck("Aktywne połączenie z internetem")
            }
        }
    }

    private fun launchAggressiveKeysCheck(reason: String) {
        val currentTime = System.currentTimeMillis()
        val lastNotificationTime = preferencesManager.lastNotificationTime

        // Sprawdź czy minęło wystarczająco czasu od ostatniego powiadomienia (skróć czas dla trybu agresywnego)
        val aggressiveInterval = MIN_NOTIFICATION_INTERVAL / 2 // 2.5 minuty zamiast 5
        if (currentTime - lastNotificationTime < aggressiveInterval) {
            return
        }

        // Automatycznie uruchom aplikację z pytaniem o klucze
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("aggressive_mode", true)
            putExtra("trigger_reason", reason)
        }

        // Uruchom aplikację natychmiast
        startActivity(intent)

        // Odtwórz odpowiedni dźwięk
        val currentSSID = getCurrentSSID()
        if (preferencesManager.isHomeNetwork(currentSSID)) {
            soundManager.playSoftReminder()
        } else {
            soundManager.playExitAlert()
        }

        // Wyślij także powiadomienie jako backup
        sendAggressiveNotification(reason)

        preferencesManager.lastNotificationTime = currentTime
    }

    private fun sendAggressiveKeysReminder() {
        val currentTime = System.currentTimeMillis()
        val lastNotificationTime = preferencesManager.lastNotificationTime

        // Skróć interwał dla trybu agresywnego
        val aggressiveInterval = MIN_NOTIFICATION_INTERVAL / 3 // 1.67 minuty
        if (currentTime - lastNotificationTime < aggressiveInterval) {
            return
        }

        // Automatycznie uruchom aplikację
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("aggressive_mode", true)
            putExtra("trigger_reason", "Utracono połączenie z internetem")
        }

        startActivity(intent)
        soundManager.playExitAlert()

        // Wyślij powiadomienie jako backup
        sendAggressiveNotification("Utracono połączenie z internetem - sprawdź klucze!")

        preferencesManager.lastNotificationTime = currentTime
    }

    private fun sendAggressiveNotification(reason: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⚡ MASZ KLUCZE? (Tryb agresywny)")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 200, 100, 200, 100, 200))
            .setDefaults(Notification.DEFAULT_SOUND)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(KEYS_NOTIFICATION_ID + 2, notification)
    }

    // === GŁÓWNE FUNKCJE DLA ZAPISANYCH SIECI DOMOWYCH ===

    private fun checkForHomeNetworkConnection() {
        val homeNetworks = preferencesManager.getAllHomeNetworks()
        if (homeNetworks.isEmpty()) return

        val currentSSID = getCurrentSSID()

        // Sprawdź czy połączono z jedną z zapisanych sieci domowych
        if (currentSSID != null && preferencesManager.isHomeNetwork(currentSSID)) {
            // Sprawdź czy to nowe połączenie (nie było wcześniej połączone z tą siecią)
            if (preferencesManager.lastConnectedNetwork != currentSSID) {
                // NOWE POŁĄCZENIE Z ZAPISANĄ SIECIĄ DOMOWĄ!
                onHomeNetworkConnected(currentSSID)
                preferencesManager.lastConnectedNetwork = currentSSID
                preferencesManager.wasConnectedToHome = true
            }
        }
    }

    private fun checkForHomeNetworkDisconnection() {
        val homeNetworks = preferencesManager.getAllHomeNetworks()
        if (homeNetworks.isEmpty()) return

        // Sprawdź czy był połączony z domową siecią
        if (preferencesManager.wasConnectedToHome) {
            val currentSSID = getCurrentSSID()

            // Sprawdź czy nadal jest połączony z domową siecią
            if (currentSSID == null || !preferencesManager.isHomeNetwork(currentSSID)) {
                // UTRACONO POŁĄCZENIE Z DOMOWĄ SIECIĄ!
                onHomeNetworkDisconnected()
                preferencesManager.wasConnectedToHome = false
                preferencesManager.lastConnectedNetwork = ""
            }
        }
    }

    private fun onHomeNetworkConnected(networkSSID: String) {
        // Automatycznie uruchom aplikację z pytaniem o klucze
        launchKeysCheckForHomeNetwork(networkSSID)
    }

    private fun onHomeNetworkDisconnected() {
        // Gdy użytkownik opuszcza domową sieć - może wychodzić z domu
        launchKeysCheckForExit()
    }

    private fun launchKeysCheckForHomeNetwork(networkSSID: String) {
        // AUTOMATYCZNIE uruchom aplikację
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("home_network_connected", true)
            putExtra("network_name", networkSSID)
        }

        // Uruchom aplikację natychmiast
        startActivity(intent)

        // Odtwórz dźwięk
        soundManager.playSoftReminder()

        // Wyślij powiadomienie z ikoną klucza
        sendHomeNetworkNotification(networkSSID)
    }

    private fun launchKeysCheckForExit() {
        val currentTime = System.currentTimeMillis()
        val lastNotificationTime = preferencesManager.lastNotificationTime

        // Sprawdź interwał (3 minuty dla wyjścia z domu)
        val exitInterval = 3 * 60 * 1000L
        if (currentTime - lastNotificationTime < exitInterval) {
            return
        }

        // AUTOMATYCZNIE uruchom aplikację
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("home_network_disconnected", true)
        }

        // Uruchom aplikację natychmiast
        startActivity(intent)

        // Odtwórz dźwięk ostrzeżenia
        soundManager.playExitAlert()

        // Wyślij powiadomienie
        sendExitNotification()

        preferencesManager.lastNotificationTime = currentTime
    }

    private fun sendHomeNetworkNotification(networkSSID: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("💡 Czy masz klucze?")
            .setContentText("Połączono z siecią $networkSSID")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 200, 300, 200, 300))
            .setDefaults(Notification.DEFAULT_SOUND)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(KEYS_NOTIFICATION_ID + 10, notification)
    }

    private fun sendExitNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_SHOW_KEYS_CHECK
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔑 Pamiętaj o kluczach!")
            .setContentText("Opuściłeś domową sieć Wi-Fi. Masz klucze?")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 500))
            .setDefaults(Notification.DEFAULT_SOUND)
            .build()

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(KEYS_NOTIFICATION_ID + 11, notification)
    }
}
