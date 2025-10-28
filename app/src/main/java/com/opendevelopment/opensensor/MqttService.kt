package com.opendevelopment.opensensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

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

    private var currentBrokerUrl: String? = null
    private var currentUsername: String? = null
    private var currentPassword: String? = null
    private var reconnectIntent: Intent? = null

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
        startForeground(notificationId, createNotification(status.name))

        val settingsDataStore = SettingsDataStore(this)
        val settings = runBlocking { settingsDataStore.settingsFlow.first() }

        nativeInit(
            this,
            settings.accelerometerTopic,
            settings.gyroscopeTopic,
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
        updateNotification("Status is ${status.name.lowercase()}.")

        val statusIntent = Intent(MQTT_STATUS_ACTION).apply {
            putExtra("status", status.name)
        }
        sendBroadcast(statusIntent)

        when (status) {
            MqttState.ERROR -> {
                val intent = Intent(MQTT_ERROR_ACTION).apply {
                    putExtra("error", reason)
                }
                sendBroadcast(intent)
            }
            MqttState.DISCONNECTED -> {
                reconnectIntent?.let { intent ->
                    val errIntent = Intent(MQTT_ERROR_ACTION).apply {
                        putExtra("error", reason)
                    }
                    sendBroadcast(errIntent)

                    reconnectIntent = null
                    Log.d(tag, "Reconnecting with new parameters.")
                    startService(intent)
                } ?: run {
                    Log.d(tag, "Disconnected, stopping service.")
                    stopSelf()
                }
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
                val brokerUrl = intent.getStringExtra("BROKER_URL")
                val username = intent.getStringExtra("USERNAME")
                val password = intent.getStringExtra("PASSWORD")

                if (brokerUrl.isNullOrEmpty() || username.isNullOrEmpty() || password == null) {
                    Log.e(tag, "Connection parameters are missing.")
                    return START_STICKY
                }

                val paramsChanged = brokerUrl != currentBrokerUrl ||
                        username != currentUsername ||
                        password != currentPassword

                if (!paramsChanged && (status == MqttState.CONNECTED || status == MqttState.CONNECTING)) {
                    Log.d(tag, "Already connected or connecting with same parameters.")
                } else {
                    currentBrokerUrl = brokerUrl
                    currentUsername = username
                    currentPassword = password

                    if (status == MqttState.CONNECTED || status == MqttState.CONNECTING) {
                        Log.d(tag, "Parameters changed, reconnecting.")
                        reconnectIntent = intent
                        nativeDisconnect()
                    } else {
                        Log.d(tag, "Connecting to MQTT broker.")
                        nativeConnect(brokerUrl, "", username, password)
                    }
                }
            }
            ACTION_DISCONNECT -> {
                reconnectIntent = null
                if (status == MqttState.CONNECTED || status == MqttState.CONNECTING) {
                    nativeDisconnect()
                } else {
                    stopSelf()
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

    private fun createNotification(text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("MQTT Service")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    private fun updateNotification(status: String) {
        val notification = createNotification(status)
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(notificationId, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(statusRequestReceiver)
        Log.d(tag, "MQTT Service destroyed.")
        reconnectIntent = null
        nativeCleanup()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private external fun nativeInit(
        callback_obj: MqttService,
        accelerometerTopic: String,
        gyroscopeTopic: String,
        lightSensorTopic: String,
        temperatureSensorTopic: String
    )

    private external fun nativeConnect(brokerUrl: String, clientId: String, username: String, password: String)
    private external fun nativeDisconnect()
    private external fun nativeCleanup()


    companion object {
        const val MQTT_ERROR_ACTION = "com.opendevelopment.opensensor.MQTT_ERROR"
        const val MQTT_STATUS_ACTION = "com.opendevelopment.opensensor.MQTT_STATUS"

        const val ACTION_CONNECT = "com.opendevelopment.opensensor.MQTT_CONNECT"
        const val ACTION_DISCONNECT = "com.opendevelopment.opensensor.MQTT_DISCONNECT"
        const val ACTION_REQUEST_STATUS = "com.opendevelopment.opensensor.MQTT_REQUEST_STATUS"
    }
}
