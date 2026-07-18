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

class TemperatureSensorService : Service(), SensorEventListener {

    private val tag = "TemperatureSensorService"
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val notificationId = 5 // Unique ID for the notification
    private val channelId = "TemperatureSensorServiceChannel"

    private lateinit var sensorManager: SensorManager
    private var temperatureSensor: Sensor? = null
    private var isStarted = false
    private lateinit var settingsDataStore: SettingsDataStore

    private data class TemperatureSensorConfig(
        val isEnabled: Boolean,
        val rounding: Int,
        val samplingPeriod: Int
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created.")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        settingsDataStore = SettingsDataStore(this)
        System.loadLibrary("opensensor_native")

        createNotificationChannel()

        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings ->
                    TemperatureSensorConfig(
                        s.isTemperatureSensorEnabled,
                        s.temperatureSensorRounding.toIntOrNull() ?: 2,
                        s.temperatureSensorSamplingPeriod
                    )
                }
                .distinctUntilChanged()
                .collect { config: TemperatureSensorConfig ->
                    if (!config.isEnabled) {
                        stopSelf()
                        return@collect
                    }

                    updateSettings(config.rounding)
                    start(config.samplingPeriod)
                    _isTemperatureSensorEnabled.value = isStarted

                    if (!isStarted) {
                        settingsDataStore.updateTemperatureSensorEnabled(false)
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
        Log.d(tag, "Starting temperature sensor listener with sampling period: $samplingPeriod")
        if (isStarted) {
            sensorManager.unregisterListener(this)
        }
        if (temperatureSensor != null) {
            sensorManager.registerListener(this, temperatureSensor, samplingPeriod)
            isStarted = true
        } else {
            Log.e(tag, "Temperature sensor not available on this device.")
            IconToast.show(this, "Temperature sensor not available on this device.")
            isStarted = false
        }
    }

    private fun stop() {
        if (isStarted) {
            Log.d(tag, "Stopping temperature sensor listener.")
            sensorManager.unregisterListener(this)
            isStarted = false
        }
    }

    private fun updateSettings(rounding: Int) {
        nativeUpdateTemperatureSensorSettings(rounding)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            val value = event.values[0]

            // For the UI
            serviceScope.launch {
                _temperatureSensorData.emit(value)
            }

            // Process data in C++
            nativeProcessTemperatureSensorData(value)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Temperature Sensor Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("destination", "temperature")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Temperature Sensor Service")
            .setContentText("Monitoring temperature sensor in the background.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed.")
        if (isTemperatureSensorEnabled.value) {
            stop()
        }
        _isTemperatureSensorEnabled.value = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_TEMPERATURE_SENSOR = "com.opendevelopment.opensensor.START_TEMPERATURE_SENSOR"
        const val ACTION_STOP_TEMPERATURE_SENSOR = "com.opendevelopment.opensensor.STOP_TEMPERATURE_SENSOR"

        private val _temperatureSensorData = MutableSharedFlow<Float>()
        val temperatureSensorData = _temperatureSensorData.asSharedFlow()

        private val _isTemperatureSensorEnabled = MutableStateFlow(false)
        val isTemperatureSensorEnabled = _isTemperatureSensorEnabled.asStateFlow()
    }

    private external fun nativeUpdateTemperatureSensorSettings(rounding: Int)
    private external fun nativeProcessTemperatureSensorData(value: Float)
}
