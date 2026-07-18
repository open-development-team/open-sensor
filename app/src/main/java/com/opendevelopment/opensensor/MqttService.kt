package com.opendevelopment.opensensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.opendevelopment.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MqttService : Service() {

    private val tag = "MqttService"

    enum class MqttState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    private var status: MqttState = MqttState.DISCONNECTED
    private val notificationId = 3 // Unique ID for the notification
    private val channelId = "MqttServiceChannel"

    private var lastDiscoveryPrefix: String? = null
    private var lastDiscoveryDeviceId: String? = null
    private var lastAvailabilityTopic: String? = null

    private lateinit var settingsDataStore: SettingsDataStore

    private val statusRequestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_REQUEST_STATUS) {
                Log.d(tag, "Received status request broadcast")
                val statusIntent = Intent(MQTT_STATUS_ACTION).apply {
                    putExtra("status", status.name)
                }
                sendBroadcast(statusIntent)
            }
        }
    }

    init {
        System.loadLibrary("opensensor_native")
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "MQTT Service created.")

        createNotificationChannel()
        startForeground(notificationId, createNotification())

        settingsDataStore = SettingsDataStore(this)
        val settings = runBlocking { settingsDataStore.settingsFlow.first() }
        val logFile = File(filesDir, "mqtt_log.txt")

        nativeInit(
            this,
            logFile.absolutePath,
            settings.accelerometerTopic,
            settings.gyroscopeTopic,
            settings.gravityTopic,
            settings.lightSensorTopic,
            settings.temperatureSensorTopic
        )

        val filter = IntentFilter(ACTION_REQUEST_STATUS)
        ContextCompat.registerReceiver(this, statusRequestReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun onMqttStatusUpdate(newStatus: String, reason: String) {
        Log.d(tag, "onMqttStatusUpdate: $newStatus")
        status = when (newStatus) {
            "CONNECTED" -> MqttState.CONNECTED
            "CONNECTING" -> MqttState.CONNECTING
            "DISCONNECTED" -> MqttState.DISCONNECTED
            else -> MqttState.ERROR
        }

        val statusIntent = Intent(MQTT_STATUS_ACTION).apply {
            putExtra("status", status.name)
        }
        sendBroadcast(statusIntent)

        if (status == MqttState.CONNECTED) {
            val settings = runBlocking { settingsDataStore.settingsFlow.first() }
            val availabilityTopic = settings.availabilityTopic
            nativePublish(availabilityTopic, "online", true)
            
            // Sync individual sensor statuses
            publishSensorStatus(availabilityTopic, "accel", settings.isAccelerometerEnabled)
            publishSensorStatus(availabilityTopic, "gyro", settings.isGyroscopeEnabled)
            publishSensorStatus(availabilityTopic, "gravity", settings.isGravityEnabled)
            publishSensorStatus(availabilityTopic, "light", settings.isLightSensorEnabled)
            publishSensorStatus(availabilityTopic, "temp", settings.isTemperatureSensorEnabled)

            if (settings.isHaDiscoveryEnabled) {
                sendHaDiscovery(settings)
            }
        }

        when (status) {
            MqttState.ERROR -> {
                val intent = Intent(MQTT_ERROR_ACTION).apply {
                    putExtra("error", reason)
                }
                sendBroadcast(intent)
            }
            else -> {
                // Other states do not require special handling here
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(tag, "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_CONNECT -> {
                val brokerUrl = intent.getStringExtra("BROKER_URL") ?: ""
                val username = intent.getStringExtra("USERNAME") ?: ""
                val password = intent.getStringExtra("PASSWORD") ?: ""
                val availabilityTopic = intent.getStringExtra("AVAILABILITY_TOPIC") ?: "opensensor/status"

                Log.d(tag, "Connecting to MQTT broker.")
                nativeConnect(brokerUrl, "", username, password, availabilityTopic, "offline")
            }
            ACTION_DISCONNECT -> {
                nativeDisconnect()
                stopSelf()
            }
            ACTION_REFRESH_DISCOVERY -> {
                if (status == MqttState.CONNECTED) {
                    val settingsDataStore = SettingsDataStore(this)
                    val settings = runBlocking { settingsDataStore.settingsFlow.first() }
                    sendHaDiscovery(settings)
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "MQTT Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("destination", "mqtt")
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("MQTT Service")
            .setContentText("Publishing MQTT messages in the background.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun sendHaDiscovery(settings: Settings) {
        val prefix = settings.haDiscoveryPrefix
        val deviceName = settings.haDeviceName
        val deviceId = settings.haDeviceId
        val availabilityTopic = settings.availabilityTopic

        // Clear old discovery if settings changed
        if (lastDiscoveryPrefix != null && lastDiscoveryDeviceId != null && lastAvailabilityTopic != null) {
            if (lastDiscoveryPrefix != prefix || lastDiscoveryDeviceId != deviceId || lastAvailabilityTopic != availabilityTopic) {
                clearHaDiscovery(lastDiscoveryPrefix!!, lastDiscoveryDeviceId!!, lastAvailabilityTopic!!)
            }
        }

        if (!settings.isHaDiscoveryEnabled) {
            if (lastDiscoveryPrefix != null && lastDiscoveryDeviceId != null && lastAvailabilityTopic != null) {
                clearHaDiscovery(lastDiscoveryPrefix!!, lastDiscoveryDeviceId!!, lastAvailabilityTopic!!)
                lastDiscoveryPrefix = null
                lastDiscoveryDeviceId = null
                lastAvailabilityTopic = null
            }
            return
        }

        lastDiscoveryPrefix = prefix
        lastDiscoveryDeviceId = deviceId
        lastAvailabilityTopic = availabilityTopic

        val device = JSONObject().apply {
            put("identifiers", JSONArray().put(deviceId))
            put("name", deviceName)
            put("model", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("sw_version", Build.VERSION.RELEASE)
        }

        val globalAvailability = JSONObject().apply {
            put("topic", availabilityTopic)
        }

        // Helper to create sensor availability array
        fun getAvailability(sensorKey: String): JSONArray {
            return JSONArray().apply {
                put(globalAvailability)
                put(JSONObject().apply {
                    put("topic", "$availabilityTopic/$sensorKey")
                })
            }
        }

        // Accelerometer - Always publish config, but sync status
        publishSensorStatus(availabilityTopic, "accel", settings.isAccelerometerEnabled)
        listOf("x", "y", "z").forEach { axis ->
            val config = JSONObject().apply {
                put("name", "$deviceName Accelerometer $axis")
                put("state_topic", settings.accelerometerTopic)
                put("unit_of_measurement", "m/s²")
                put("value_template", "{{ value_json.$axis }}")
                put("unique_id", "${deviceId}_accel_$axis")
                put("device", device)
                put("availability", getAvailability("accel"))
            }
            nativePublish("$prefix/sensor/$deviceId/accel_$axis/config", config.toString(), true)
        }

        // Gyroscope
        publishSensorStatus(availabilityTopic, "gyro", settings.isGyroscopeEnabled)
        listOf("x", "y", "z").forEach { axis ->
            val config = JSONObject().apply {
                put("name", "$deviceName Gyroscope $axis")
                put("state_topic", settings.gyroscopeTopic)
                put("unit_of_measurement", "rad/s")
                put("value_template", "{{ value_json.$axis }}")
                put("unique_id", "${deviceId}_gyro_$axis")
                put("device", device)
                put("availability", getAvailability("gyro"))
            }
            nativePublish("$prefix/sensor/$deviceId/gyro_$axis/config", config.toString(), true)
        }

        // Gravity
        publishSensorStatus(availabilityTopic, "gravity", settings.isGravityEnabled)
        listOf("x", "y", "z").forEach { axis ->
            val config = JSONObject().apply {
                put("name", "$deviceName Gravity $axis")
                put("state_topic", settings.gravityTopic)
                put("unit_of_measurement", "m/s²")
                put("value_template", "{{ value_json.$axis }}")
                put("unique_id", "${deviceId}_gravity_$axis")
                put("device", device)
                put("availability", getAvailability("gravity"))
            }
            nativePublish("$prefix/sensor/$deviceId/gravity_$axis/config", config.toString(), true)
        }

        // Light
        publishSensorStatus(availabilityTopic, "light", settings.isLightSensorEnabled)
        val lightConfig = JSONObject().apply {
            put("name", "$deviceName Light")
            put("state_topic", settings.lightSensorTopic)
            put("unit_of_measurement", "lx")
            put("value_template", "{{ value_json.value }}")
            put("unique_id", "${deviceId}_light")
            put("device", device)
            put("availability", getAvailability("light"))
            put("device_class", "illuminance")
        }
        nativePublish("$prefix/sensor/$deviceId/light/config", lightConfig.toString(), true)

        // Temperature
        publishSensorStatus(availabilityTopic, "temp", settings.isTemperatureSensorEnabled)
        val tempConfig = JSONObject().apply {
            put("name", "$deviceName Ambient Temperature")
            put("state_topic", settings.temperatureSensorTopic)
            put("unit_of_measurement", "°C")
            put("value_template", "{{ value_json.value }}")
            put("unique_id", "${deviceId}_temp")
            put("device", device)
            put("availability", getAvailability("temp"))
            put("device_class", "temperature")
        }
        nativePublish("$prefix/sensor/$deviceId/temp/config", tempConfig.toString(), true)
    }

    private fun publishSensorStatus(baseTopic: String, sensorKey: String, isEnabled: Boolean) {
        if (status == MqttState.CONNECTED) {
            nativePublish("$baseTopic/$sensorKey", if (isEnabled) "online" else "offline", true)
        }
    }

    private fun clearHaDiscovery(prefix: String, deviceId: String, availabilityTopic: String) {
        Log.d(tag, "Clearing old HA discovery for device: $deviceId with prefix: $prefix and availability topic: $availabilityTopic")
        // Also publish offline status to the old topics
        nativePublish(availabilityTopic, "offline", true)
        listOf("accel", "gyro", "gravity", "light", "temp").forEach {
            nativePublish("$availabilityTopic/$it", "offline", true)
        }

        // We need to unpublish all possible sensors for this device ID
        val sensors = mutableListOf<String>()
        listOf("accel_x", "accel_y", "accel_z", "gyro_x", "gyro_y", "gyro_z", "gravity_x", "gravity_y", "gravity_z", "light", "temp").forEach {
            sensors.add(it)
        }

        sensors.forEach { sensor ->
            nativePublish("$prefix/sensor/$deviceId/$sensor/config", "", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(statusRequestReceiver)
        Log.d(tag, "MQTT Service destroyed.")
        nativeCleanup()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private external fun nativeInit(
        callback_obj: MqttService,
        logFilePath: String,
        accelerometerTopic: String,
        gyroscopeTopic: String,
        gravityTopic: String,
        lightSensorTopic: String,
        temperatureSensorTopic: String
    )

    private external fun nativeConnect(brokerUrl: String, clientId: String, username: String, password: String, willTopic: String, willPayload: String)
    private external fun nativeDisconnect()
    private external fun nativeCleanup()
    private external fun nativePublish(topic: String, payload: String, retain: Boolean)


    companion object {
        const val MQTT_ERROR_ACTION = "com.opendevelopment.opensensor.MQTT_ERROR"
        const val MQTT_STATUS_ACTION = "com.opendevelopment.opensensor.MQTT_STATUS"
        const val MQTT_LOG_ACTION = "com.opendevelopment.opensensor.MQTT_LOG"

        const val ACTION_CONNECT = "com.opendevelopment.opensensor.MQTT_CONNECT"
        const val ACTION_DISCONNECT = "com.opendevelopment.opensensor.MQTT_DISCONNECT"
        const val ACTION_REQUEST_STATUS = "com.opendevelopment.opensensor.MQTT_REQUEST_STATUS"
        const val ACTION_REFRESH_DISCOVERY = "com.opendevelopment.opensensor.MQTT_REFRESH_DISCOVERY"
    }
}
