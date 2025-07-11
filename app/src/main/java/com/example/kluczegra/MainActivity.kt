package com.example.kluczegra

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.example.kluczegra.ui.theme.KluczeGraTheme
import kotlinx.coroutines.delay

enum class AppScreen {
    MAIN,
    KEYS_CHECK,
    CONFIRMATION,
    NOTIFICATION_SETTINGS
}

class MainActivity : ComponentActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var soundManager: SoundManager
    private var vibrator: Vibrator? = null

    companion object {
        const val ACTION_SHOW_KEYS_CHECK = "com.example.kluczegra.SHOW_KEYS_CHECK"
    }

    // Launcher do requestowania uprawnień
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            // Jeśli nie ma uprawnień, nie pokazuj dodatkowych ekranów
            // Użytkownik może je przyznać z głównego ekranu
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferencesManager = PreferencesManager(this)

        // Inicjalizuj vibrator
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService<VibratorManager>()
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService<Vibrator>()
        }

        setContent {
            KluczeGraTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }

        // Sprawdź uprawnienia w tle, ale nie blokuj aplikacji
        checkPermissionsInBackground()
    }

    @Composable
    fun AppContent() {
        var currentScreen by remember { mutableStateOf(
            if (intent?.action == ACTION_SHOW_KEYS_CHECK) AppScreen.KEYS_CHECK else AppScreen.MAIN
        ) }
        val currentSSID = getCurrentSSID()

        // Sprawdź czy aplikacja została uruchomiona przez powiadomienie
        LaunchedEffect(intent?.action) {
            if (intent?.action == ACTION_SHOW_KEYS_CHECK) {
                currentScreen = AppScreen.KEYS_CHECK
            }
        }

        // Sprawdź czy użytkownik właśnie połączył się z domową siecią
        LaunchedEffect(currentSSID) {
            if (preferencesManager.isHomeNetwork(currentSSID) &&
                currentScreen == AppScreen.MAIN &&
                hasAllPermissions()) {
                // Sprawdź czy to nowe połączenie
                if (preferencesManager.lastConnectedNetwork != currentSSID) {
                    currentScreen = AppScreen.KEYS_CHECK
                    preferencesManager.lastConnectedNetwork = currentSSID ?: ""
                }
            }
        }

        when (currentScreen) {
            AppScreen.MAIN -> {
                MainScreen(
                    onNavigateToNotifications = { currentScreen = AppScreen.NOTIFICATION_SETTINGS }
                )
            }
            AppScreen.KEYS_CHECK -> {
                KeysCheckScreen(
                    onKeySelected = {
                        currentScreen = AppScreen.CONFIRMATION
                    }
                )
            }
            AppScreen.CONFIRMATION -> {
                ConfirmationScreen(
                    onComplete = {
                        currentScreen = AppScreen.MAIN
                    }
                )
            }
            AppScreen.NOTIFICATION_SETTINGS -> {
                NotificationSettingsScreen(
                    onNavigateBack = { currentScreen = AppScreen.MAIN }
                )
            }
        }
    }

    @Composable
    fun KeysCheckScreen(onKeySelected: () -> Unit) {
        var isVibrating by remember { mutableStateOf(true) }
        val currentSSID = getCurrentSSID()
        val isConnectedToHome = preferencesManager.isHomeNetwork(currentSSID)

        // Sprawdź różne tryby uruchomienia aplikacji
        val isAggressiveMode = intent?.getBooleanExtra("aggressive_mode", false) ?: false
        val isAutoLaunched = intent?.getBooleanExtra("auto_launched", false) ?: false
        val isHomeNetworkConnected = intent?.getBooleanExtra("home_network_connected", false) ?: false
        val isHomeNetworkDisconnected = intent?.getBooleanExtra("home_network_disconnected", false) ?: false
        val networkName = intent?.getStringExtra("network_name") ?: ""
        val triggerReason = intent?.getStringExtra("trigger_reason") ?: ""

        // Określ typ sytuacji i odtwórz odpowiedni dźwięk
        LaunchedEffect(Unit) {
            // Inicjalizuj SoundManager jeśli nie został zainicjalizowany
            if (!::soundManager.isInitialized) {
                soundManager = SoundManager(this@MainActivity)
            }

            when {
                isHomeNetworkConnected -> soundManager.playSoftReminder()
                isHomeNetworkDisconnected -> soundManager.playExitAlert()
                isConnectedToHome -> soundManager.playSoftReminder()
                else -> soundManager.playExitAlert()
            }
        }

        // Wibracje co sekundę przez 20 sekund
        LaunchedEffect(Unit) {
            repeat(20) { // 20 sekund
                if (isVibrating) {
                    vibrate()
                    delay(1000) // 1 sekunda
                }
            }
            isVibrating = false
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dodaj elastyczne miejsce na górze
            Spacer(modifier = Modifier.weight(1f))

            // Wyświetl informacje o tym co wywołało pytanie o klucze
            if (isHomeNetworkConnected && networkName.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🏠 Połączenie z domową siecią",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "Wykryto połączenie z siecią: $networkName",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            } else if (isHomeNetworkDisconnected) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🚪 Opuszczanie domu",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = "Rozłączono z domową siecią Wi-Fi",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            } else if ((isAggressiveMode || isAutoLaunched) && triggerReason.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = if (isAutoLaunched) "🌐 Automatyczne uruchomienie" else "⚡ Tryb agresywny",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = triggerReason,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Text(
                text = preferencesManager.reminderText.ifEmpty { "Czy masz klucze?" },
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 48.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                KeyButton(
                    icon = "🔴",
                    label = "Nie mam kluczy",
                    backgroundColor = Color(0xFFFF5252),
                    onClick = {
                        isVibrating = false
                        onKeySelected()
                    }
                )

                KeyButton(
                    icon = "🟡",
                    label = "Nie jestem pewien",
                    backgroundColor = Color(0xFFFFEB3B),
                    onClick = {
                        isVibrating = false
                        onKeySelected()
                    }
                )

                KeyButton(
                    icon = "🟢",
                    label = "Mam klucze",
                    backgroundColor = Color(0xFF4CAF50),
                    onClick = {
                        isVibrating = false
                        onKeySelected()
                    }
                )
            }

            // Dodaj elastyczne miejsce na dole
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    @Composable
    fun KeyButton(
        icon: String,
        label: String,
        backgroundColor: Color,
        onClick: () -> Unit
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(backgroundColor)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = icon,
                    fontSize = 40.sp
                )
            }

            Text(
                text = label,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .width(100.dp)
            )
        }
    }

    @Composable
    fun ConfirmationScreen(onComplete: () -> Unit) {
        LaunchedEffect(Unit) {
            delay(2000) // 2 sekundy
            onComplete()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Dodaj elastyczne miejsce na górze
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "😊",
                fontSize = 120.sp,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Text(
                text = "Dzięki za potwierdzenie!",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            // Dodaj elastyczne miejsce na dole
            Spacer(modifier = Modifier.weight(1f))
        }
    }

    @Composable
    fun MainScreen(onNavigateToNotifications: () -> Unit) {
        val context = LocalContext.current
        var homeSSID by remember { mutableStateOf(preferencesManager.homeSSID ?: "") }
        var isMonitoring by remember { mutableStateOf(preferencesManager.isMonitoringEnabled) }
        var currentSSID by remember { mutableStateOf(getCurrentSSID()) }
        var showPermissionWarning by remember { mutableStateOf(!hasAllPermissions()) }

        // Stan klucza na podstawie aktualnej sytuacji
        val keyStatus = getKeyStatus(currentSSID, isMonitoring, hasAllPermissions())

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = 80.dp) // Dodatkowy padding na dole dla paska nawigacji
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nagłówek z ikoną klucza
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = keyStatus.icon,
                    fontSize = 32.sp,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Przypomnienie o Kluczach",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

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

            // Konfiguracja 3 sieci domowych
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("🏠 Sieci domowe (max 3):", fontWeight = FontWeight.Medium)
                    Text(
                        "Dodaj dowolne sieci Wi-Fi jako domowe",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Dom 1
                    HomeNetworkField(
                        index = 1,
                        label = "Dom 1",
                        currentSSID = currentSSID,
                        preferencesManager = preferencesManager
                    )

                    // Dom 2
                    HomeNetworkField(
                        index = 2,
                        label = "Dom 2",
                        currentSSID = currentSSID,
                        preferencesManager = preferencesManager
                    )

                    // Dom 3
                    HomeNetworkField(
                        index = 3,
                        label = "Dom 3",
                        currentSSID = currentSSID,
                        preferencesManager = preferencesManager
                    )
                }
            }

            // Przełącznik monitorowania
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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

                                if (enabled && hasAllPermissions() && preferencesManager.getAllHomeNetworks().isNotEmpty()) {
                                    startWiFiMonitoring()
                                } else if (!enabled) {
                                    stopWiFiMonitoring()
                                }
                            },
                            enabled = hasAllPermissions() && preferencesManager.getAllHomeNetworks().isNotEmpty()
                        )
                    }

                    // Agresywne powiadomienia
                    if (isMonitoring) {
                        Spacer(modifier = Modifier.height(16.dp))

                        var isAggressiveEnabled by remember {
                            mutableStateOf(preferencesManager.isAggressiveNotificationsEnabled)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("⚡ Powiadomienia agresywne", fontWeight = FontWeight.Medium)
                                Text(
                                    "Automatyczne uruchamianie aplikacji przy dostępie do internetu",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Switch(
                                checked = isAggressiveEnabled,
                                onCheckedChange = { enabled ->
                                    isAggressiveEnabled = enabled
                                    preferencesManager.isAggressiveNotificationsEnabled = enabled

                                    // Restart serwisu z nowymi ustawieniami
                                    if (isMonitoring) {
                                        stopWiFiMonitoring()
                                        startWiFiMonitoring()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Status aplikacji z ikoną klucza
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Text(
                            text = keyStatus.icon,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Status aplikacji:", fontWeight = FontWeight.Medium)
                    }

                    Text(
                        text = keyStatus.description,
                        color = keyStatus.color
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

            // Przycisk do ustawień powiadomień
            Button(
                onClick = onNavigateToNotifications,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Ustawienia powiadomień")
            }
        }
    }

    @Composable
    private fun getKeyStatus(currentSSID: String?, isMonitoring: Boolean, hasPermissions: Boolean): KeyStatus {
        return when {
            !hasPermissions -> KeyStatus(
                icon = "🔴",
                description = "Brak wymaganych uprawnień",
                color = MaterialTheme.colorScheme.error
            )
            !isMonitoring -> KeyStatus(
                icon = "⚫",
                description = "Monitorowanie wyłączone",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            currentSSID == null -> KeyStatus(
                icon = "🟡",
                description = "Monitorowanie włączone, ale brak połączenia Wi-Fi",
                color = Color(0xFFFF9800) // Pomarańczowy
            )
            preferencesManager.isHomeNetwork(currentSSID) -> KeyStatus(
                icon = "🟢",
                description = "Połączono z domową siecią - wszystko OK",
                color = MaterialTheme.colorScheme.primary
            )
            else -> KeyStatus(
                icon = "🟡",
                description = "Połączono z siecią - skonfiguruj jako domową jeśli potrzeba",
                color = Color(0xFFFF9800)
            )
        }
    }

    data class KeyStatus(
        val icon: String,
        val description: String,
        val color: androidx.compose.ui.graphics.Color
    )

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

    private fun vibrate() {
        vibrator?.let { v ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(200)
            }
        }
    }

    private fun checkPermissionsInBackground() {
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
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::soundManager.isInitialized) {
            soundManager.release()
        }
    }

    @Composable
    fun NotificationSettingsScreen(onNavigateBack: () -> Unit) {
        var reminderText by remember { mutableStateOf(preferencesManager.reminderText) }
        var selectedIcon by remember { mutableStateOf(preferencesManager.reminderIcon) }
        var showSnackbar by remember { mutableStateOf(false) }

        val availableIcons = listOf("🔑", "📦", "🎒", "💼", "📱", "💻", "🔐", "🗝️")
        val textSuggestions = listOf("Czy masz klucze?", "Czy masz portfel?", "Czy masz telefon?", "Czy masz laptop?", "Czy masz plecak?", "Czy masz torby?")

        LaunchedEffect(showSnackbar) {
            if (showSnackbar) {
                delay(2000)
                showSnackbar = false
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Nagłówek z przyciskiem powrotu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Powrót")
                }
                Text(
                    text = "Ustawienia przypomnienia",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.width(48.dp)) // Balansuje przycisk z lewej strony
            }

            // Wybór ikony powiadomienia
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Ikona powiadomienia",
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableIcons) { icon ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (icon == selectedIcon)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedIcon = icon },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = icon,
                                    fontSize = 24.sp
                                )
                            }
                        }
                    }
                }
            }

            // Edycja tekstu przypomnienia
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Tekst przypomnienia",
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = reminderText,
                        onValueChange = { reminderText = it },
                        label = { Text("Pytanie o przypomnienie") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "Sugerowane teksty:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(textSuggestions) { suggestion ->
                            AssistChip(
                                onClick = { reminderText = suggestion },
                                label = { Text(suggestion, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Podgląd
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Podgląd powiadomienia",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedIcon,
                            fontSize = 24.sp,
                            modifier = Modifier.padding(end = 12.dp)
                        )
                        Column {
                            Text(
                                text = "$selectedIcon $reminderText",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Kliknij, by potwierdzić, które masz ze sobą",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            // Przycisk zapisz
            Button(
                onClick = {
                    preferencesManager.reminderText = reminderText
                    preferencesManager.reminderIcon = selectedIcon
                    showSnackbar = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Zapisz ustawienia")
            }

            // Snackbar
            if (showSnackbar) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF4CAF50)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Ustawienia zostały zapisane ✅",
                        color = Color.White,
                        modifier = Modifier.padding(16.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }

    @Composable
    fun HomeNetworkField(
        index: Int,
        label: String,
        currentSSID: String?,
        preferencesManager: PreferencesManager
    ) {
        var ssidValue by remember { mutableStateOf(preferencesManager.getHomeNetwork(index)) }
        var showSaved by remember { mutableStateOf(false) }

        LaunchedEffect(showSaved) {
            if (showSaved) {
                delay(2000)
                showSaved = false
            }
        }

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = ssidValue,
                    onValueChange = { ssidValue = it },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )

                if (!currentSSID.isNullOrEmpty()) {
                    Button(
                        onClick = { ssidValue = currentSSID },
                        modifier = Modifier.padding(start = 8.dp),
                        contentPadding = PaddingValues(8.dp)
                    ) {
                        Text("Użyj aktualnej", fontSize = 10.sp)
                    }
                }
            }

            if (!currentSSID.isNullOrEmpty()) {
                Text(
                    text = "Aktualna sieć: $currentSSID",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        preferencesManager.setHomeNetwork(index, ssidValue)
                        showSaved = true
                    },
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Zapisz")
                }
            }

            if (showSaved) {
                Text(
                    text = "Sieć została zapisana ✅",
                    color = Color(0xFF4CAF50),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
