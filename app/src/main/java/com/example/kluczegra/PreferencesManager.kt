package com.example.kluczegra

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "klucze_gra_prefs"
        private const val KEY_HOME_SSID = "home_ssid"
        private const val KEY_MONITORING_ENABLED = "monitoring_enabled"
        private const val KEY_WAS_CONNECTED = "was_connected"
        private const val KEY_LAST_NOTIFICATION_TIME = "last_notification_time"
    }

    var homeSSID: String?
        get() = prefs.getString(KEY_HOME_SSID, null)
        set(value) = prefs.edit().putString(KEY_HOME_SSID, value).apply()

    var isMonitoringEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITORING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MONITORING_ENABLED, value).apply()

    var wasConnectedToHome: Boolean
        get() = prefs.getBoolean(KEY_WAS_CONNECTED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAS_CONNECTED, value).apply()

    var lastNotificationTime: Long
        get() = prefs.getLong(KEY_LAST_NOTIFICATION_TIME, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_NOTIFICATION_TIME, value).apply()
}
