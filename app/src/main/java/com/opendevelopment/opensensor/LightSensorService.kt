package com.opendevelopment.opensensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.opendevelopment.R
import com.opendevelopment.opensensor.ui.theme.IconToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LightSensorService : Service(), SensorEventListener {

    private val tag = "LightSensorService"
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val notificationId = 4 // Unique ID for the notification
    private val channelId = "LightSensorServiceChannel"

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var isStarted = false
    private lateinit var settingsDataStore: SettingsDataStore

    private data class LightSensorConfig(
        val isEnabled: Boolean,
        val rounding: Int,
        val samplingPeriod: Int
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created.")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)
        settingsDataStore = SettingsDataStore(this)
        System.loadLibrary("opensensor_native")

        createNotificationChannel()

        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings ->
                    LightSensorConfig(
                        s.isLightSensorEnabled,
                        s.lightSensorRounding.toIntOrNull() ?: 2,
                        s.lightSensorSamplingPeriod
                    )
                }
                .distinctUntilChanged()
                .collect { config: LightSensorConfig ->
                    if (!config.isEnabled) {
                        stopSelf()
                        return@collect
                    }

                    updateSettings(config.rounding)
                    start(config.samplingPeriod)
                    _isLightSensorEnabled.value = isStarted

                    if (!isStarted) {
                        settingsDataStore.updateLightSensorEnabled(false)
                        stopSelf()
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(notificationId, notification)
        return START_STICKY
    }

    private fun start(samplingPeriod: Int) {
        Log.d(tag, "Starting light sensor listener with sampling period: $samplingPeriod")
        if (isStarted) {
            sensorManager.unregisterListener(this)
        }
        if (lightSensor != null) {
            sensorManager.registerListener(this, lightSensor, samplingPeriod)
            isStarted = true
        } else {
            Log.e(tag, "Light sensor not available on this device.")
            IconToast.show(this, "Light sensor not available on this device.")
            isStarted = false
        }
    }

    private fun stop() {
        if (isStarted) {
            Log.d(tag, "Stopping light sensor listener.")
            sensorManager.unregisterListener(this)
            isStarted = false
        }
    }

    private fun updateSettings(rounding: Int) {
        nativeUpdateLightSensorSettings(rounding)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val value = event.values[0]

            // For the UI
            serviceScope.launch {
                _lightSensorData.emit(value)
            }

            // Process data in C++
            nativeProcessLightSensorData(value)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Light Sensor Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("destination", "light")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Light Sensor Service")
            .setContentText("Monitoring light sensor in the background.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed.")
        if (isLightSensorEnabled.value) {
            stop()
        }
        _isLightSensorEnabled.value = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_LIGHT_SENSOR = "com.opendevelopment.opensensor.START_LIGHT_SENSOR"
        const val ACTION_STOP_LIGHT_SENSOR = "com.opendevelopment.opensensor.STOP_LIGHT_SENSOR"

        private val _lightSensorData = MutableSharedFlow<Float>()
        val lightSensorData = _lightSensorData.asSharedFlow()

        private val _isLightSensorEnabled = MutableStateFlow(false)
        val isLightSensorEnabled = _isLightSensorEnabled.asStateFlow()
    }

    private external fun nativeUpdateLightSensorSettings(rounding: Int)
    private external fun nativeProcessLightSensorData(value: Float)
}
