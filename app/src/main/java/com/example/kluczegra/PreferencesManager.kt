package com.example.kluczegra

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "klucze_gra_prefs"
        private const val KEY_HOME_SSID_1 = "home_ssid_1"
        private const val KEY_HOME_SSID_2 = "home_ssid_2"
        private const val KEY_HOME_SSID_3 = "home_ssid_3"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_WAS_CONNECTED = "was_connected"
        private const val KEY_LAST_NOTIFICATION_TIME = "last_notification_time"
        private const val KEY_LAST_CONNECTED_NETWORK = "last_connected_network"
    }

    // Stary homeSSID dla kompatybilności wstecznej
    var homeSSID: String?
        get() = homeSSID1.takeIf { it.isNotEmpty() }
        set(value) { if (!value.isNullOrEmpty()) homeSSID1 = value }

    // Nowe 3 sieci domowe
    var homeSSID1: String
        get() = prefs.getString(KEY_HOME_SSID_1, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOME_SSID_1, value).apply()

    var homeSSID2: String
        get() = prefs.getString(KEY_HOME_SSID_2, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOME_SSID_2, value).apply()

    var homeSSID3: String
        get() = prefs.getString(KEY_HOME_SSID_3, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOME_SSID_3, value).apply()

    var isMonitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    var wasConnectedToHome: Boolean
        get() = prefs.getBoolean(KEY_WAS_CONNECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAS_CONNECTED, value).apply()

    var lastNotificationTime: Long
        get() = prefs.getLong(KEY_LAST_NOTIFICATION_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_NOTIFICATION_TIME, value).apply()

    var lastConnectedNetwork: String
        get() = prefs.getString(KEY_LAST_CONNECTED_NETWORK, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_CONNECTED_NETWORK, value).apply()

    // Pomocne funkcje
    fun getAllHomeNetworks(): List<String> {
        return listOfNotNull(
            homeSSID1.takeIf { it.isNotEmpty() },
            homeSSID2.takeIf { it.isNotEmpty() },
            homeSSID3.takeIf { it.isNotEmpty() }
        )
    }

    fun isHomeNetwork(ssid: String?): Boolean {
        if (ssid.isNullOrEmpty()) return false
        return getAllHomeNetworks().any { it.equals(ssid, ignoreCase = true) }
    }

    fun setHomeNetwork(index: Int, ssid: String) {
        when (index) {
            1 -> homeSSID1 = ssid
            2 -> homeSSID2 = ssid
            3 -> homeSSID3 = ssid
        }
    }

    fun getHomeNetwork(index: Int): String {
        return when (index) {
            1 -> homeSSID1
            2 -> homeSSID2
            3 -> homeSSID3
            else -> ""
        }
    }
}
