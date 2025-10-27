package com.opendevelopment.opensensor

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created.")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        temperatureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
        settingsDataStore = SettingsDataStore(this)
        System.loadLibrary("opensensor_native")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(notificationId, notification)

        Log.d(tag, "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_TEMPERATURE_SENSOR -> {
                val rounding = intent.getIntExtra("ROUNDING", 2)
                val samplingPeriod = intent.getIntExtra("SAMPLING_PERIOD", SensorManager.SENSOR_DELAY_NORMAL)
                updateSettings(rounding)
                start(samplingPeriod)
                _isTemperatureSensorEnabled.value = isStarted
                if (!isStarted) {
                    serviceScope.launch {
                        settingsDataStore.updateTemperatureSensorEnabled(false)
                    }
                    stopSelf()
                }
            }
            ACTION_STOP_TEMPERATURE_SENSOR -> {
                stopSelf()
            }
        }

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
            Toast.makeText(this, "Temperature sensor not available on this device.", Toast.LENGTH_SHORT).show()
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
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Temperature Sensor Service")
            .setContentText("Monitoring temperature sensor in the background.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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
