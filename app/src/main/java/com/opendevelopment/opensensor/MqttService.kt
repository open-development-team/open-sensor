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

        val settingsDataStore = SettingsDataStore(this)
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

                Log.d(tag, "Connecting to MQTT broker.")
                nativeConnect(brokerUrl, "", username, password)
            }
            ACTION_DISCONNECT -> {
                nativeDisconnect()
                stopSelf()
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

    private external fun nativeConnect(brokerUrl: String, clientId: String, username: String, password: String)
    private external fun nativeDisconnect()
    private external fun nativeCleanup()


    companion object {
        const val MQTT_ERROR_ACTION = "com.opendevelopment.opensensor.MQTT_ERROR"
        const val MQTT_STATUS_ACTION = "com.opendevelopment.opensensor.MQTT_STATUS"
        const val MQTT_LOG_ACTION = "com.opendevelopment.opensensor.MQTT_LOG"

        const val ACTION_CONNECT = "com.opendevelopment.opensensor.MQTT_CONNECT"
        const val ACTION_DISCONNECT = "com.opendevelopment.opensensor.MQTT_DISCONNECT"
        const val ACTION_REQUEST_STATUS = "com.opendevelopment.opensensor.MQTT_REQUEST_STATUS"
    }
}
