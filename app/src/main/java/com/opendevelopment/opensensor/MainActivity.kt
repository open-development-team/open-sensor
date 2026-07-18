package com.opendevelopment.opensensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.SensorManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opendevelopment.opensensor.ui.theme.OpendevelopmentOpensensorTheme
import com.opendevelopment.opensensor.ui.theme.IconToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(SettingsDataStore(applicationContext))
    }

    // Use a state variable to hold the intent that needs to be processed for navigation
    private var pendingNavigationIntent: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Process the intent that started the activity
        pendingNavigationIntent = intent

        setContent {
            OpendevelopmentOpensensorTheme {
                AppNavigation(settingsViewModel)
            }
        }
    }

    // Provide a way for the Composable to access the pending intent
    fun getPendingNavigationIntent(): Intent? = pendingNavigationIntent

    // Clear the pending intent after it has been processed
    fun clearPendingNavigationIntent() {
        pendingNavigationIntent = null
    }
}

data class NavItem(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation(settingsViewModel: SettingsViewModel) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Access the MainActivity to get the pending intent
    val activity = context as MainActivity
    val pendingIntent = activity.getPendingNavigationIntent()

    // Handle navigation from the initial intent or new intents
    LaunchedEffect(pendingIntent) {
        val destination = pendingIntent?.getStringExtra("destination")
        if (destination != null) {
            navController.navigate(destination) {
                popUpTo(navController.graph.startDestinationId)
                launchSingleTop = true
            }
            // Clear the pending intent after navigation to prevent re-navigation
            activity.clearPendingNavigationIntent()
        }
    }

    val sinks = listOf(
        NavItem("mqtt", "MQTT", Icons.Default.CloudQueue)
    )

    val sources = listOf(
        NavItem("accelerometer", "Accelerometer", Icons.Default.Sensors),
        NavItem("gyroscope", "Gyroscope", Icons.AutoMirrored.Filled.RotateRight),
        NavItem("gravity", "Gravity", Icons.Default.Public),
        NavItem("light", "Light", Icons.Default.Highlight),
        NavItem("temperature", "Ambient Temperature", Icons.Default.Thermostat)
    )

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == MqttService.MQTT_ERROR_ACTION) {
                    val errorMessage = intent.getStringExtra("error") ?: "MQTT Connection Error"
                    IconToast.show(context, errorMessage, duration = Toast.LENGTH_SHORT)
                }
            }
        }
        val filter = IntentFilter(MqttService.MQTT_ERROR_ACTION)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    "Open Sensor",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text("Sinks", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                sinks.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text("Sources", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp))
                sources.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = {
                            scope.launch { drawerState.close() }
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId)
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentRoute == "settings",
                    onClick = {
                        scope.launch { drawerState.close() }
                        navController.navigate("settings") {
                            popUpTo(navController.graph.startDestinationId)
                            launchSingleTop = true
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        val titleText = when (currentRoute) {
                            "settings" -> "Settings"
                            "mqtt" -> "MQTT"
                            "accelerometer" -> "Accelerometer"
                            "gyroscope" -> "Gyroscope"
                            "gravity" -> "Gravity"
                            "light" -> "Light"
                            "temperature" -> "Ambient Temperature"
                            else -> "Accelerometer"
                        }
                        Text(titleText)
                    },
                    navigationIcon = {
                        if (currentRoute == "settings") {
                            IconButton(onClick = { navController.popBackStack() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        }
                    },
                    actions = {
                        when (currentRoute) {
                            "mqtt" -> {
                                val settings by settingsViewModel.settings.collectAsState()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Switch(
                                        checked = settings.isMqttEnabled,
                                        onCheckedChange = { enabled ->
                                            settingsViewModel.updateMqttEnabled(enabled)
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            "accelerometer" -> {
                                val settings by settingsViewModel.settings.collectAsState()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Switch(
                                        checked = settings.isAccelerometerEnabled,
                                        onCheckedChange = { enabled ->
                                            settingsViewModel.updateAccelerometerEnabled(enabled)
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            "gyroscope" -> {
                                val settings by settingsViewModel.settings.collectAsState()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Switch(
                                        checked = settings.isGyroscopeEnabled,
                                        onCheckedChange = { enabled ->
                                            settingsViewModel.updateGyroscopeEnabled(enabled)
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            "gravity" -> {
                                val settings by settingsViewModel.settings.collectAsState()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Switch(
                                        checked = settings.isGravityEnabled,
                                        onCheckedChange = { enabled ->
                                            settingsViewModel.updateGravityEnabled(enabled)
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            "light" -> {
                                val settings by settingsViewModel.settings.collectAsState()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Switch(
                                        checked = settings.isLightSensorEnabled,
                                        onCheckedChange = { enabled ->
                                            settingsViewModel.updateLightSensorEnabled(enabled)
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                            "temperature" -> {
                                val settings by settingsViewModel.settings.collectAsState()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(end = 16.dp)
                                ) {
                                    Switch(
                                        checked = settings.isTemperatureSensorEnabled,
                                        onCheckedChange = { enabled ->
                                            settingsViewModel.updateTemperatureSensorEnabled(enabled)
                                        },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = "mqtt",
                modifier = Modifier.padding(innerPadding)
            ) {
                composable("mqtt") {
                    MqttScreen(settingsViewModel)
                }
                composable("accelerometer") {
                    AccelerometerScreen(settingsViewModel)
                }
                composable("gyroscope") {
                    GyroscopeScreen(settingsViewModel)
                }
                composable("gravity") {
                    GravityScreen(settingsViewModel)
                }
                composable("light") {
                    LightScreen(settingsViewModel)
                }
                composable("temperature") {
                    TemperatureScreen(settingsViewModel)
                }
                composable("settings") {
                    SettingsScreen(settingsViewModel)
                }
            }
        }
    }
}

@Composable
fun MqttScreen(settingsViewModel: SettingsViewModel) {
    val context = LocalContext.current
    val settings by settingsViewModel.settings.collectAsState()
    var logs by remember { mutableStateOf(listOf<String>()) }

    LaunchedEffect(Unit) {
        val logFile = File(context.filesDir, "mqtt_log.txt")
        while (isActive) {
            if (logFile.exists()) {
                val lines = logFile.readLines().reversed()
                if (lines != logs) {
                    logs = lines
                }
            }
            delay(1000) // Poll every second
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val status = if (settings.isMqttEnabled) "ENABLED" else "DISABLED"
        Text("MQTT Status: $status")
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            "Logs:",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Start)
        )
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            LazyColumn(modifier = Modifier.padding(8.dp)) {
                items(logs) { log ->
                    Text(
                        text = log,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun AccelerometerScreen(settingsViewModel: SettingsViewModel) {
    val dataHistory = remember { mutableStateListOf<Triple<Float, Float, Float>>() }
    val maxHistorySize = 100
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            AccelerometerService.accelerometerData.collect { newData ->
                dataHistory.add(newData)
                if (dataHistory.size > maxHistorySize) {
                    dataHistory.removeAt(0)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SensorGraph(
            data = dataHistory.map { floatArrayOf(it.first, it.second, it.third) },
            labels = listOf("X", "Y", "Z"),
            unit = "m/s²",
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        val last = dataHistory.lastOrNull()
        CurrentValueCard(
            values = listOf(
                "X" to last?.first,
                "Y" to last?.second,
                "Z" to last?.third
            ),
            unit = "m/s²"
        )
    }
}

@Composable
fun GyroscopeScreen(settingsViewModel: SettingsViewModel) {
    val dataHistory = remember { mutableStateListOf<Triple<Float, Float, Float>>() }
    val maxHistorySize = 100
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            GyroscopeService.gyroscopeData.collect { newData ->
                dataHistory.add(newData)
                if (dataHistory.size > maxHistorySize) {
                    dataHistory.removeAt(0)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SensorGraph(
            data = dataHistory.map { floatArrayOf(it.first, it.second, it.third) },
            labels = listOf("X", "Y", "Z"),
            unit = "rad/s",
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        val last = dataHistory.lastOrNull()
        CurrentValueCard(
            values = listOf(
                "X" to last?.first,
                "Y" to last?.second,
                "Z" to last?.third
            ),
            unit = "rad/s"
        )
    }
}

@Composable
fun GravityScreen(settingsViewModel: SettingsViewModel) {
    val dataHistory = remember { mutableStateListOf<Triple<Float, Float, Float>>() }
    val maxHistorySize = 100
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            GravityService.gravityData.collect { newData ->
                dataHistory.add(newData)
                if (dataHistory.size > maxHistorySize) {
                    dataHistory.removeAt(0)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SensorGraph(
            data = dataHistory.map { floatArrayOf(it.first, it.second, it.third) },
            labels = listOf("X", "Y", "Z"),
            unit = "m/s²",
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        val last = dataHistory.lastOrNull()
        CurrentValueCard(
            values = listOf(
                "X" to last?.first,
                "Y" to last?.second,
                "Z" to last?.third
            ),
            unit = "m/s²"
        )
    }
}

@Composable
fun LightScreen(settingsViewModel: SettingsViewModel) {
    val dataHistory = remember { mutableStateListOf<Float>() }
    val maxHistorySize = 100
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            LightSensorService.lightSensorData.collect { newData ->
                dataHistory.add(newData)
                if (dataHistory.size > maxHistorySize) {
                    dataHistory.removeAt(0)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SensorGraph(
            data = dataHistory.map { floatArrayOf(it) },
            labels = listOf("Light"),
            unit = "lx",
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        CurrentValueCard(
            values = listOf("Intensity" to dataHistory.lastOrNull()),
            unit = "lx"
        )
    }
}

@Composable
fun TemperatureScreen(settingsViewModel: SettingsViewModel) {
    val dataHistory = remember { mutableStateListOf<Float>() }
    val maxHistorySize = 100
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            TemperatureSensorService.temperatureSensorData.collect { newData ->
                dataHistory.add(newData)
                if (dataHistory.size > maxHistorySize) {
                    dataHistory.removeAt(0)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SensorGraph(
            data = dataHistory.map { floatArrayOf(it) },
            labels = listOf("Temperature"),
            unit = "°C",
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))

        CurrentValueCard(
            values = listOf("Value" to dataHistory.lastOrNull()),
            unit = "°C"
        )
    }
}

@Composable
fun SettingsScreen(settingsViewModel: SettingsViewModel) {
    val settings by settingsViewModel.settings.collectAsState()
    var openDialog by remember { mutableStateOf<String?>(null) }

    val launchDialog = { key: String -> openDialog = key }
    val onDismiss = { openDialog = null }

    val samplingPeriodOptions = mapOf(
        SensorManager.SENSOR_DELAY_FASTEST to "Fastest",
        SensorManager.SENSOR_DELAY_GAME to "Faster",
        SensorManager.SENSOR_DELAY_UI to "Fast",
        SensorManager.SENSOR_DELAY_NORMAL to "Normal"
    )

    openDialog?.let { key ->
        when (key) {
            "broker" -> EditTextPreferenceDialog(
                title = "Broker URL",
                initialValue = settings.broker,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateBroker(it); onDismiss() },
                hint = "tcp://192.168.1.10:1883"
            )
            "username" -> EditTextPreferenceDialog(
                title = "Username",
                initialValue = settings.username,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateUsername(it); onDismiss() }
            )
            "password" -> EditTextPreferenceDialog(
                title = "Password",
                initialValue = settings.password,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updatePassword(it); onDismiss() },
                keyboardType = KeyboardType.Password,
                isPassword = true
            )
            "accelerometerTopic" -> EditTextPreferenceDialog(
                title = "Accelerometer Topic",
                initialValue = settings.accelerometerTopic,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateAccelerometerTopic(it); onDismiss() },
                hint = "Leave empty to disable publishing"
            )
            "accelerometerMultiplierX" -> EditTextPreferenceDialog(
                title = "X Multiplier",
                initialValue = settings.accelerometerMultiplierX,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateAccelerometerMultiplierX(it); onDismiss() },
                keyboardType = KeyboardType.Decimal
            )
            "accelerometerMultiplierY" -> EditTextPreferenceDialog(
                title = "Y Multiplier",
                initialValue = settings.accelerometerMultiplierY,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateAccelerometerMultiplierY(it); onDismiss() },
                keyboardType = KeyboardType.Decimal
            )
            "accelerometerMultiplierZ" -> EditTextPreferenceDialog(
                title = "Z Multiplier",
                initialValue = settings.accelerometerMultiplierZ,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateAccelerometerMultiplierZ(it); onDismiss() },
                keyboardType = KeyboardType.Decimal
            )
            "accelerometerRounding" -> EditTextPreferenceDialog(
                title = "Rounding (Decimals)",
                initialValue = settings.accelerometerRounding,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateAccelerometerRounding(it); onDismiss() },
                keyboardType = KeyboardType.Number
            )
            "gyroscopeTopic" -> EditTextPreferenceDialog(
                title = "Gyroscope Topic",
                initialValue = settings.gyroscopeTopic,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGyroscopeTopic(it); onDismiss() },
                hint = "Leave empty to disable publishing"
            )
            "gyroscopeMultiplierX" -> EditTextPreferenceDialog(
                title = "Gyroscope X Multiplier",
                initialValue = settings.gyroscopeMultiplierX,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGyroscopeMultiplierX(it); onDismiss() },
                keyboardType = KeyboardType.Decimal
            )
            "gyroscopeMultiplierY" -> EditTextPreferenceDialog(
                title = "Gyroscope Y Multiplier",
                initialValue = settings.gyroscopeMultiplierY,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGyroscopeMultiplierY(it); onDismiss() },
                keyboardType = KeyboardType.Decimal
            )
            "gyroscopeMultiplierZ" -> EditTextPreferenceDialog(
                title = "Gyroscope Z Multiplier",
                initialValue = settings.gyroscopeMultiplierZ,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGyroscopeMultiplierZ(it); onDismiss() },
                keyboardType = KeyboardType.Decimal
            )
            "gyroscopeRounding" -> EditTextPreferenceDialog(
                title = "Gyroscope Rounding (Decimals)",
                initialValue = settings.gyroscopeRounding,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGyroscopeRounding(it); onDismiss() },
                keyboardType = KeyboardType.Number
            )
            "gravityTopic" -> EditTextPreferenceDialog(
                title = "Gravity Topic",
                initialValue = settings.gravityTopic,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGravityTopic(it); onDismiss() },
                hint = "Leave empty to disable publishing"
            )
            "gravityMultiplierX" -> EditTextPreferenceDialog(
                title = "Gravity X Multiplier",
                initialValue = settings.gravityMultiplierX,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGravityMultiplierX(it); onDismiss() },
                keyboardType = KeyboardType.Decimal
            )
            "gravityMultiplierY" -> EditTextPreferenceDialog(
                title = "Gravity Y Multiplier",
                initialValue = settings.gravityMultiplierY,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGravityMultiplierY(it); onDismiss() },
                keyboardType = KeyboardType.Decimal
            )
            "gravityMultiplierZ" -> EditTextPreferenceDialog(
                title = "Gravity Z Multiplier",
                initialValue = settings.gravityMultiplierZ,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGravityMultiplierZ(it); onDismiss() },
                keyboardType = KeyboardType.Decimal
            )
            "gravityRounding" -> EditTextPreferenceDialog(
                title = "Gravity Rounding (Decimals)",
                initialValue = settings.gravityRounding,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGravityRounding(it); onDismiss() },
                keyboardType = KeyboardType.Number
            )
            "lightSensorTopic" -> EditTextPreferenceDialog(
                title = "Light Sensor Topic",
                initialValue = settings.lightSensorTopic,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateLightSensorTopic(it); onDismiss() },
                hint = "Leave empty to disable publishing"
            )
            "lightSensorRounding" -> EditTextPreferenceDialog(
                title = "Light Sensor Rounding (Decimals)",
                initialValue = settings.lightSensorRounding,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateLightSensorRounding(it); onDismiss() },
                keyboardType = KeyboardType.Number
            )
            "temperatureSensorTopic" -> EditTextPreferenceDialog(
                title = "Temperature Sensor Topic",
                initialValue = settings.temperatureSensorTopic,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateTemperatureSensorTopic(it); onDismiss() },
                hint = "Leave empty to disable publishing"
            )
            "temperatureSensorRounding" -> EditTextPreferenceDialog(
                title = "Temperature Sensor Rounding (Decimals)",
                initialValue = settings.temperatureSensorRounding,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateTemperatureSensorRounding(it); onDismiss() },
                keyboardType = KeyboardType.Number
            )
            "accelerometerSamplingPeriod" -> ListPreferenceDialog(
                title = "Accelerometer Sampling Period",
                options = samplingPeriodOptions,
                currentValue = settings.accelerometerSamplingPeriod,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateAccelerometerSamplingPeriod(it); onDismiss() }
            )
            "gyroscopeSamplingPeriod" -> ListPreferenceDialog(
                title = "Gyroscope Sampling Period",
                options = samplingPeriodOptions,
                currentValue = settings.gyroscopeSamplingPeriod,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGyroscopeSamplingPeriod(it); onDismiss() }
            )
            "gravitySamplingPeriod" -> ListPreferenceDialog(
                title = "Gravity Sampling Period",
                options = samplingPeriodOptions,
                currentValue = settings.gravitySamplingPeriod,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGravitySamplingPeriod(it); onDismiss() }
            )
            "lightSensorSamplingPeriod" -> ListPreferenceDialog(
                title = "Light Sensor Sampling Period",
                options = samplingPeriodOptions,
                currentValue = settings.lightSensorSamplingPeriod,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateLightSensorSamplingPeriod(it); onDismiss() }
            )
            "temperatureSensorSamplingPeriod" -> ListPreferenceDialog(
                title = "Temperature Sensor Sampling Period",
                options = samplingPeriodOptions,
                currentValue = settings.temperatureSensorSamplingPeriod,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateTemperatureSensorSamplingPeriod(it); onDismiss() }
            )
            "haDiscoveryPrefix" -> EditTextPreferenceDialog(
                title = "HA Discovery Prefix",
                initialValue = settings.haDiscoveryPrefix,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateHaDiscoveryPrefix(it); onDismiss() }
            )
            "haDeviceName" -> EditTextPreferenceDialog(
                title = "HA Device Name",
                initialValue = settings.haDeviceName,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateHaDeviceName(it); onDismiss() }
            )
            "haDeviceId" -> EditTextPreferenceDialog(
                title = "HA Device ID",
                initialValue = settings.haDeviceId,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateHaDeviceId(it); onDismiss() }
            )
            "availabilityTopic" -> EditTextPreferenceDialog(
                title = "Availability Topic",
                initialValue = settings.availabilityTopic,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateAvailabilityTopic(it); onDismiss() }
            )
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SettingsCategory(title = "MQTT Broker")
        EditTextPreference(
            title = "Broker URL",
            description = "The address of your MQTT broker (e.g., tcp://192.168.1.10:1883). Use tls:// for secure connections (server cert is not verified).",
            summary = settings.broker.ifBlank { "Not set" }
        ) { launchDialog("broker") }
        EditTextPreference(
            title = "Username",
            description = "Optional username for MQTT broker authentication.",
            summary = settings.username
        ) { launchDialog("username") }
        EditTextPreference(
            title = "Password",
            description = "Optional password for MQTT broker authentication.",
            summary = "********"
        ) { launchDialog("password") }
        EditTextPreference(
            title = "Availability Topic",
            description = "MQTT topic where the app reports its online/offline status to Home Assistant. Also used as a prefix for individual sensor statuses.",
            summary = settings.availabilityTopic
        ) { launchDialog("availabilityTopic") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Accelerometer")
        EditTextPreference(
            title = "Topic",
            description = "MQTT topic for accelerometer sensor data. Leave empty to disable.",
            summary = settings.accelerometerTopic.ifBlank { "Disabled" }
        ) { launchDialog("accelerometerTopic") }
        EditTextPreference(
            title = "X Multiplier",
            description = "Factor applied to the X-axis reading. Use for calibration.",
            summary = settings.accelerometerMultiplierX
        ) { launchDialog("accelerometerMultiplierX") }
        EditTextPreference(
            title = "Y Multiplier",
            description = "Factor applied to the Y-axis reading. Use for calibration.",
            summary = settings.accelerometerMultiplierY
        ) { launchDialog("accelerometerMultiplierY") }
        EditTextPreference(
            title = "Z Multiplier",
            description = "Factor applied to the Z-axis reading. Use for calibration.",
            summary = settings.accelerometerMultiplierZ
        ) { launchDialog("accelerometerMultiplierZ") }
        EditTextPreference(
            title = "Rounding (Decimals)",
            description = "Precision of the published sensor values.",
            summary = settings.accelerometerRounding
        ) { launchDialog("accelerometerRounding") }
        ListPreference(
            title = "Sampling Period",
            description = "Determines how frequently sensor data is read and published.",
            summary = samplingPeriodOptions[settings.accelerometerSamplingPeriod] ?: "Normal"
        ) { launchDialog("accelerometerSamplingPeriod") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Gyroscope")
        EditTextPreference(
            title = "Topic",
            description = "MQTT topic for gyroscope sensor data. Leave empty to disable.",
            summary = settings.gyroscopeTopic.ifBlank { "Disabled" }
        ) { launchDialog("gyroscopeTopic") }
        EditTextPreference(
            title = "X Multiplier",
            description = "Factor applied to the X-axis reading.",
            summary = settings.gyroscopeMultiplierX
        ) { launchDialog("gyroscopeMultiplierX") }
        EditTextPreference(
            title = "Y Multiplier",
            description = "Factor applied to the Y-axis reading.",
            summary = settings.gyroscopeMultiplierY
        ) { launchDialog("gyroscopeMultiplierY") }
        EditTextPreference(
            title = "Z Multiplier",
            description = "Factor applied to the Z-axis reading.",
            summary = settings.gyroscopeMultiplierZ
        ) { launchDialog("gyroscopeMultiplierZ") }
        EditTextPreference(
            title = "Rounding (Decimals)",
            description = "Precision of the published gyroscope values.",
            summary = settings.gyroscopeRounding
        ) { launchDialog("gyroscopeRounding") }
        ListPreference(
            title = "Sampling Period",
            description = "Determines how frequently gyroscope data is read.",
            summary = samplingPeriodOptions[settings.gyroscopeSamplingPeriod] ?: "Normal"
        ) { launchDialog("gyroscopeSamplingPeriod") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Gravity")
        EditTextPreference(
            title = "Topic",
            description = "MQTT topic for gravity sensor data. Leave empty to disable.",
            summary = settings.gravityTopic.ifBlank { "Disabled" }
        ) { launchDialog("gravityTopic") }
        EditTextPreference(
            title = "X Multiplier",
            description = "Factor applied to the X-axis gravity reading.",
            summary = settings.gravityMultiplierX
        ) { launchDialog("gravityMultiplierX") }
        EditTextPreference(
            title = "Y Multiplier",
            description = "Factor applied to the Y-axis gravity reading.",
            summary = settings.gravityMultiplierY
        ) { launchDialog("gravityMultiplierY") }
        EditTextPreference(
            title = "Z Multiplier",
            description = "Factor applied to the Z-axis gravity reading.",
            summary = settings.gravityMultiplierZ
        ) { launchDialog("gravityMultiplierZ") }
        EditTextPreference(
            title = "Rounding (Decimals)",
            description = "Precision of the published gravity values.",
            summary = settings.gravityRounding
        ) { launchDialog("gravityRounding") }
        ListPreference(
            title = "Sampling Period",
            description = "Determines how frequently gravity data is read.",
            summary = samplingPeriodOptions[settings.gravitySamplingPeriod] ?: "Normal"
        ) { launchDialog("gravitySamplingPeriod") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Ambient Light")
        EditTextPreference(
            title = "Topic",
            description = "MQTT topic for light sensor data.",
            summary = settings.lightSensorTopic.ifBlank { "Disabled" }
        ) { launchDialog("lightSensorTopic") }
        EditTextPreference(
            title = "Rounding (Decimals)",
            description = "Precision of the published light intensity values.",
            summary = settings.lightSensorRounding
        ) { launchDialog("lightSensorRounding") }
        ListPreference(
            title = "Sampling Period",
            description = "Determines how frequently light data is read.",
            summary = samplingPeriodOptions[settings.lightSensorSamplingPeriod] ?: "Normal"
        ) { launchDialog("lightSensorSamplingPeriod") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Ambient Temperature")
        EditTextPreference(
            title = "Topic",
            description = "MQTT topic for temperature sensor data.",
            summary = settings.temperatureSensorTopic.ifBlank { "Disabled" }
        ) { launchDialog("temperatureSensorTopic") }
        EditTextPreference(
            title = "Rounding (Decimals)",
            description = "Precision of the published temperature values.",
            summary = settings.temperatureSensorRounding
        ) { launchDialog("temperatureSensorRounding") }
        ListPreference(
            title = "Sampling Period",
            description = "Determines how frequently temperature data is read.",
            summary = samplingPeriodOptions[settings.temperatureSensorSamplingPeriod] ?: "Normal"
        ) { launchDialog("temperatureSensorSamplingPeriod") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Application")
        SwitchPreference(
            title = "Auto-start on boot",
            summary = settings.autoStart.let { if (it) "Enabled" else "Disabled" },
            description = "Automatically start the MQTT service when the device boots up.",
            isChecked = settings.autoStart,
            onCheckedChange = { settingsViewModel.updateAutoStart(it) }
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Home Assistant Discovery")
        SwitchPreference(
            title = "Enable Discovery",
            summary = settings.isHaDiscoveryEnabled.let { if (it) "Enabled" else "Disabled" },
            description = "Automatically register and configure sensors in Home Assistant using MQTT Discovery.",
            isChecked = settings.isHaDiscoveryEnabled,
            onCheckedChange = { settingsViewModel.updateHaDiscoveryEnabled(it) }
        )
        EditTextPreference(
            title = "Discovery Prefix",
            description = "The prefix Home Assistant uses for discovery (default is 'homeassistant').",
            summary = settings.haDiscoveryPrefix
        ) { launchDialog("haDiscoveryPrefix") }
        EditTextPreference(
            title = "Device Name",
            description = "The name of this device as it will appear in Home Assistant.",
            summary = settings.haDeviceName
        ) { launchDialog("haDeviceName") }
        EditTextPreference(
            title = "Device ID",
            description = "A unique identifier for this device. Used to avoid collisions in Home Assistant.",
            summary = settings.haDeviceId
        ) { launchDialog("haDeviceId") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "About")
        AppVersionPreference()
    }
}

@Composable
fun AppVersionPreference() {
    val context = LocalContext.current
    val versionName = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown" }

    ListItem(
        headlineContent = { Text(text = "App Version") },
        supportingContent = { Text(text = versionName) }
    )
}

@Composable
fun CurrentValueCard(values: List<Pair<String, Float?>>, unit: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Current Raw Values",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            values.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "$label:", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = if (value != null) String.format("%.4f %s", value, unit) else "---",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
fun SensorGraph(
    data: List<FloatArray>,
    labels: List<String>,
    unit: String,
    minRange: Float = 0.0001f,
    modifier: Modifier = Modifier
) {
    val colors = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary
    )

    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column(modifier = modifier) {
        // Legend
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            labels.forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Surface(
                        modifier = Modifier.size(12.dp, 2.dp),
                        color = colors.getOrElse(index) { Color.Gray }
                    ) {}
                    Spacer(Modifier.width(4.dp))
                    Text(text = label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Canvas(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val (width, height) = size
            val padding = 40f // Padding for labels
            val graphWidth = width - padding
            val graphHeight = height - 20f

            // Calculate min/max for scaling
            var dataMin = Float.MAX_VALUE
            var dataMax = -Float.MAX_VALUE
            if (data.isEmpty()) {
                dataMin = -1f
                dataMax = 1f
            } else {
                for (array in data) {
                    for (v in array) {
                        if (v < dataMin) dataMin = v
                        if (v > dataMax) dataMax = v
                    }
                }
            }

            val center = (dataMax + dataMin) / 2f
            val span = (dataMax - dataMin).coerceAtLeast(minRange) * 1.2f // 20% breathing room
            val overallMin = center - span / 2f
            val overallMax = center + span / 2f
            val range = span.coerceAtLeast(0.00001f) // Avoid division by zero

            // Draw Grid and Y-axis labels
            val gridLines = 5
            for (i in 0 until gridLines) {
                val y = 10f + (graphHeight / (gridLines - 1)) * i
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = androidx.compose.ui.geometry.Offset(padding, y),
                    end = androidx.compose.ui.geometry.Offset(width, y),
                    strokeWidth = 1f
                )

                val labelValue = overallMax - (range / (gridLines - 1)) * i
                drawText(
                    textMeasurer = textMeasurer,
                    text = String.format("%.2f", labelValue),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, y - 15f),
                    style = textStyle
                )
            }

            // Draw Zero line if in range
            if (overallMin < 0 && overallMax > 0) {
                val zeroY = 10f + ((overallMax - 0f) / range) * graphHeight
                drawLine(
                    color = Color.Gray,
                    start = androidx.compose.ui.geometry.Offset(padding, zeroY),
                    end = androidx.compose.ui.geometry.Offset(width, zeroY),
                    strokeWidth = 2f
                )
            }

            if (data.isNotEmpty()) {
                val numSeries = data.first().size
                for (seriesIndex in 0 until numSeries) {
                    val path = Path()
                    data.forEachIndexed { index, values ->
                        val x = padding + index.toFloat() / (data.size - 1).coerceAtLeast(1) * graphWidth
                        val valAtIdx = values.getOrElse(seriesIndex) { 0f }
                        val y = 10f + ((overallMax - valAtIdx) / range) * graphHeight

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }
                    drawPath(
                        path = path,
                        color = colors.getOrElse(seriesIndex) { Color.Gray },
                        style = Stroke(width = 3f)
                    )
                }
            }
        }
        
        Text(
            text = "Unit: $unit",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun EditTextPreference(title: String, summary: String, description: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = {
            Column {
                if (description != null) {
                    Text(text = description, style = MaterialTheme.typography.bodySmall)
                }
                Text(text = "Value: $summary", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun ListPreference(
    title: String,
    summary: String,
    description: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = {
            Column {
                if (description != null) {
                    Text(text = description, style = MaterialTheme.typography.bodySmall)
                }
                Text(text = "Value: $summary", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun ListPreferenceDialog(
    title: String,
    options: Map<Int, String>,
    currentValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var selectedValue by remember { mutableStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    text = "Faster sampling will use more CPU power and drain battery.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                )
                HorizontalDivider()
                options.forEach { (value, label) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (value == selectedValue),
                                onClick = { selectedValue = value }
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        RadioButton(
                            selected = (value == selectedValue),
                            onClick = null // Recommended for accessibility with screenreaders
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(selectedValue)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditTextPreferenceDialog(
    title: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    hint: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                value = text,
                onValueChange = { newValue ->
                    val filteredValue = when (keyboardType) {
                        KeyboardType.Number -> newValue.filter { it.isDigit() }
                        KeyboardType.Decimal -> {
                            val filtered = newValue.filter { it.isDigit() || it == '.' || it == '-' }
                            val signed = if (filtered.startsWith("-")) {
                                "-" + filtered.substring(1).replace("-", "")
                            } else {
                                filtered.replace("-", "")
                            }
                            if (signed.count { it == '.' } > 1) {
                                val firstDotIndex = signed.indexOf('.')
                                signed.substring(0, firstDotIndex + 1) +
                                        signed.substring(firstDotIndex + 1).replace(".", "")
                            } else {
                                signed
                            }
                        }
                        else -> newValue
                    }
                    text = filteredValue
                },
                singleLine = true,
                placeholder = { Text(hint) },
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(text)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SettingsCategory(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, end = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String,
    isChecked: Boolean,
    description: String? = null,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(text = title) },
        supportingContent = {
            Column {
                if (description != null) {
                    Text(text = description, style = MaterialTheme.typography.bodySmall)
                }
                Text(text = summary, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        },
        trailingContent = { Switch(checked = isChecked, onCheckedChange = onCheckedChange) },
        modifier = Modifier.clickable { onCheckedChange(!isChecked) }
    )
}
