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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.opendevelopment.opensensor.ui.theme.OpendevelopmentOpensensorTheme
import com.opendevelopment.opensensor.ui.theme.IconToast
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels {
        SettingsViewModelFactory(SettingsDataStore(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val serviceManager = ServiceManager(this)
        lifecycleScope.launch {
            serviceManager.startAllServices()
        }

        setContent {
            OpendevelopmentOpensensorTheme {
                AppNavigation(settingsViewModel)
            }
        }
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

    val sinks = listOf(
        NavItem("mqtt", "MQTT", Icons.Default.CloudQueue)
    )

    val sources = listOf(
        NavItem("accelerometer", "Accelerometer", Icons.Default.Sensors),
        NavItem("gyroscope", "Gyroscope", Icons.AutoMirrored.Filled.RotateRight),
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
    var connectionStatus by remember { mutableStateOf(MqttService.MqttState.DISCONNECTED.name) }

    LaunchedEffect(Unit) {
        val intent = Intent(MqttService.ACTION_REQUEST_STATUS)
        context.sendBroadcast(intent)
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == MqttService.MQTT_STATUS_ACTION) {
                    connectionStatus = intent.getStringExtra("status") ?: MqttService.MqttState.DISCONNECTED.name
                }
            }
        }
        val filter = IntentFilter(MqttService.MQTT_STATUS_ACTION)
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("MQTT Status: $connectionStatus")
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun AccelerometerScreen(settingsViewModel: SettingsViewModel) {
    val dataHistory = remember { mutableStateListOf<Triple<Float, Float, Float>>() }
    val maxHistorySize = 100

    LaunchedEffect(Unit) {
        AccelerometerService.accelerometerData.collect { newData ->
            dataHistory.add(newData)
            if (dataHistory.size > maxHistorySize) {
                dataHistory.removeAt(0)
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
        LineGraph(
            data = dataHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun GyroscopeScreen(settingsViewModel: SettingsViewModel) {
    val dataHistory = remember { mutableStateListOf<Triple<Float, Float, Float>>() }
    val maxHistorySize = 100

    LaunchedEffect(Unit) {
        GyroscopeService.gyroscopeData.collect { newData ->
            dataHistory.add(newData)
            if (dataHistory.size > maxHistorySize) {
                dataHistory.removeAt(0)
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
        LineGraph(
            data = dataHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun LightScreen(settingsViewModel: SettingsViewModel) {
    val dataHistory = remember { mutableStateListOf<Float>() }
    val maxHistorySize = 100

    LaunchedEffect(Unit) {
        LightSensorService.lightSensorData.collect { newData ->
            dataHistory.add(newData)
            if (dataHistory.size > maxHistorySize) {
                dataHistory.removeAt(0)
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
        LineGraph(
            data = dataHistory.map { Triple(it, 0f, 0f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun TemperatureScreen(settingsViewModel: SettingsViewModel) {
    val dataHistory = remember { mutableStateListOf<Float>() }
    val maxHistorySize = 100

    LaunchedEffect(Unit) {
        TemperatureSensorService.temperatureSensorData.collect { newData ->
            dataHistory.add(newData)
            if (dataHistory.size > maxHistorySize) {
                dataHistory.removeAt(0)
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
        LineGraph(
            data = dataHistory.map { Triple(it, 0f, 0f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
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
                keyboardType = KeyboardType.Number
            )
            "accelerometerMultiplierY" -> EditTextPreferenceDialog(
                title = "Y Multiplier",
                initialValue = settings.accelerometerMultiplierY,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateAccelerometerMultiplierY(it); onDismiss() },
                keyboardType = KeyboardType.Number
            )
            "accelerometerMultiplierZ" -> EditTextPreferenceDialog(
                title = "Z Multiplier",
                initialValue = settings.accelerometerMultiplierZ,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateAccelerometerMultiplierZ(it); onDismiss() },
                keyboardType = KeyboardType.Number
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
                keyboardType = KeyboardType.Number
            )
            "gyroscopeMultiplierY" -> EditTextPreferenceDialog(
                title = "Gyroscope Y Multiplier",
                initialValue = settings.gyroscopeMultiplierY,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGyroscopeMultiplierY(it); onDismiss() },
                keyboardType = KeyboardType.Number
            )
            "gyroscopeMultiplierZ" -> EditTextPreferenceDialog(
                title = "Gyroscope Z Multiplier",
                initialValue = settings.gyroscopeMultiplierZ,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGyroscopeMultiplierZ(it); onDismiss() },
                keyboardType = KeyboardType.Number
            )
            "gyroscopeRounding" -> EditTextPreferenceDialog(
                title = "Gyroscope Rounding (Decimals)",
                initialValue = settings.gyroscopeRounding,
                onDismiss = onDismiss,
                onSave = { settingsViewModel.updateGyroscopeRounding(it); onDismiss() },
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
        }
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        SettingsCategory(title = "MQTT Broker")
        EditTextPreference(title = "Broker URL", summary = settings.broker.ifBlank { "Not set" }) { launchDialog("broker") }
        EditTextPreference(title = "Username", summary = settings.username) { launchDialog("username") }
        EditTextPreference(title = "Password", summary = "********") { launchDialog("password") }
        EditTextPreference(title = "Accelerometer Topic", summary = settings.accelerometerTopic.ifBlank { "Publishing disabled" }) { launchDialog("accelerometerTopic") }
        EditTextPreference(title = "Gyroscope Topic", summary = settings.gyroscopeTopic.ifBlank { "Publishing disabled" }) { launchDialog("gyroscopeTopic") }
        EditTextPreference(title = "Light Sensor Topic", summary = settings.lightSensorTopic.ifBlank { "Publishing disabled" }) { launchDialog("lightSensorTopic") }
        EditTextPreference(title = "Temperature Sensor Topic", summary = settings.temperatureSensorTopic.ifBlank { "Publishing disabled" }) { launchDialog("temperatureSensorTopic") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Accelerometer Processing")
        EditTextPreference(title = "X Multiplier", summary = settings.accelerometerMultiplierX) { launchDialog("accelerometerMultiplierX") }
        EditTextPreference(title = "Y Multiplier", summary = settings.accelerometerMultiplierY) { launchDialog("accelerometerMultiplierY") }
        EditTextPreference(title = "Z Multiplier", summary = settings.accelerometerMultiplierZ) { launchDialog("accelerometerMultiplierZ") }
        EditTextPreference(title = "Rounding (Decimals)", summary = settings.accelerometerRounding) { launchDialog("accelerometerRounding") }
        ListPreference(title = "Sampling Period", summary = samplingPeriodOptions[settings.accelerometerSamplingPeriod] ?: "Normal") { launchDialog("accelerometerSamplingPeriod") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Gyroscope Processing")
        EditTextPreference(title = "X Multiplier", summary = settings.gyroscopeMultiplierX) { launchDialog("gyroscopeMultiplierX") }
        EditTextPreference(title = "Y Multiplier", summary = settings.gyroscopeMultiplierY) { launchDialog("gyroscopeMultiplierY") }
        EditTextPreference(title = "Z Multiplier", summary = settings.gyroscopeMultiplierZ) { launchDialog("gyroscopeMultiplierZ") }
        EditTextPreference(title = "Rounding (Decimals)", summary = settings.gyroscopeRounding) { launchDialog("gyroscopeRounding") }
        ListPreference(title = "Sampling Period", summary = samplingPeriodOptions[settings.gyroscopeSamplingPeriod] ?: "Normal") { launchDialog("gyroscopeSamplingPeriod") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Light Sensor Processing")
        EditTextPreference(title = "Rounding (Decimals)", summary = settings.lightSensorRounding) { launchDialog("lightSensorRounding") }
        ListPreference(title = "Sampling Period", summary = samplingPeriodOptions[settings.lightSensorSamplingPeriod] ?: "Normal") { launchDialog("lightSensorSamplingPeriod") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Temperature Sensor Processing")
        EditTextPreference(title = "Rounding (Decimals)", summary = settings.temperatureSensorRounding) { launchDialog("temperatureSensorRounding") }
        ListPreference(title = "Sampling Period", summary = samplingPeriodOptions[settings.temperatureSensorSamplingPeriod] ?: "Normal") { launchDialog("temperatureSensorSamplingPeriod") }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "Application")
        SwitchPreference(
            title = "Auto-start on boot",
            summary = "Start service and connect automatically",
            isChecked = settings.autoStart
        ) { settingsViewModel.updateAutoStart(it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsCategory(title = "About")
        AppVersionPreference()
    }
}

@Composable
fun AppVersionPreference() {
    val context = LocalContext.current
    val versionName = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "Unknown" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "App Version", style = MaterialTheme.typography.titleMedium)
            Text(text = versionName, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

@Composable
fun LineGraph(data: List<Triple<Float, Float, Float>>, modifier: Modifier) {
    Canvas(modifier = modifier) {
        val (width, height) = size
        val pathX = Path()
        val pathY = Path()
        val pathZ = Path()

        // Calculate min/max for scaling
        val allValues = data.flatMap { listOf(it.first, it.second, it.third) }
        val overallMin = allValues.minOrNull() ?: -1f
        val overallMax = allValues.maxOrNull() ?: 1f

        if (data.isNotEmpty()) {
            data.forEachIndexed { index, (x, y, z) ->
                val pX = index.toFloat() / (data.size - 1).coerceAtLeast(1) * width

                // Scale values relative to the overall min/max
                val effectiveMax = if (overallMax > overallMin) overallMax - overallMin else 1f
                val scaledX = (x - overallMin) / effectiveMax
                val scaledY = (y - overallMin) / effectiveMax
                val scaledZ = (z - overallMin) / effectiveMax

                // Invert y-axis for drawing (0 at top, height at bottom)
                val pYx = (1 - scaledX) * height
                val pYy = (1 - scaledY) * height
                val pYz = (1 - scaledZ) * height

                if (index == 0) {
                    pathX.moveTo(pX, pYx)
                    pathY.moveTo(pX, pYy)
                    pathZ.moveTo(pX, pYz)
                } else {
                    pathX.lineTo(pX, pYx)
                    pathY.lineTo(pX, pYy)
                    pathZ.lineTo(pX, pYz)
                }
            }
        } else {
            // Draw a flat line or nothing if no data
            pathX.moveTo(0f, height / 2f)
            pathX.lineTo(width, height / 2f)
            pathY.moveTo(0f, height / 2f)
            pathY.lineTo(width, height / 2f)
            pathZ.moveTo(0f, height / 2f)
            pathZ.lineTo(width, height / 2f)
        }

        drawPath(pathX, Color.Red, style = Stroke(2f))
        drawPath(pathY, Color.Green, style = Stroke(2f))
        drawPath(pathZ, Color.Blue, style = Stroke(2f))
    }
}

@Composable
fun EditTextPreference(title: String, summary: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

@Composable
fun ListPreference(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
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
                onValueChange = { text = it },
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
        modifier = Modifier.padding(16.dp)
    )
}

@Composable
fun SwitchPreference(
    title: String,
    summary: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(text = summary, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
        Switch(checked = isChecked, onCheckedChange = onCheckedChange)
    }
}
