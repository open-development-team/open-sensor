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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class AccelerometerService : Service(), SensorEventListener {

    private val tag = "AccelerometerService"
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val notificationId = 1
    private val channelId = "AccelerometerServiceChannel"

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isStarted = false
    private lateinit var settingsDataStore: SettingsDataStore

    private data class AccelerometerConfig(
        val isEnabled: Boolean,
        val multiplierX: Float,
        val multiplierY: Float,
        val multiplierZ: Float,
        val rounding: Int,
        val samplingPeriod: Int
    )

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created.")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        settingsDataStore = SettingsDataStore(this)
        System.loadLibrary("opensensor_native")

        createNotificationChannel()

        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings ->
                    AccelerometerConfig(
                        s.isAccelerometerEnabled,
                        s.accelerometerMultiplierX.toFloatOrNull() ?: 1.0f,
                        s.accelerometerMultiplierY.toFloatOrNull() ?: 1.0f,
                        s.accelerometerMultiplierZ.toFloatOrNull() ?: 1.0f,
                        s.accelerometerRounding.toIntOrNull() ?: 2,
                        s.accelerometerSamplingPeriod
                    )
                }
                .distinctUntilChanged()
                .collect { config: AccelerometerConfig ->
                    if (!config.isEnabled) {
                        stopSelf()
                        return@collect
                    }

                    updateSettings(config.multiplierX, config.multiplierY, config.multiplierZ, config.rounding)
                    start(config.samplingPeriod)
                    _isAccelerometerEnabled.value = isStarted
                    
                    if (!isStarted) {
                        settingsDataStore.updateAccelerometerEnabled(false)
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
        Log.d(tag, "Starting accelerometer listener with sampling period: $samplingPeriod")
        if (isStarted) {
            sensorManager.unregisterListener(this)
        }
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, samplingPeriod)
            isStarted = true
        } else {
            Log.e(tag, "Accelerometer not available on this device.")
            IconToast.show(this, "Accelerometer not available on this device.")
            isStarted = false
        }
    }

    private fun stop() {
        if (isStarted) {
            Log.d(tag, "Stopping accelerometer listener.")
            sensorManager.unregisterListener(this)
            isStarted = false
        }
    }

    private fun updateSettings(multiplierX: Float, multiplierY: Float, multiplierZ: Float, rounding: Int) {
        nativeUpdateSettings(multiplierX, multiplierY, multiplierZ, rounding)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val (x, y, z) = event.values

            // For the UI - only emit if there are active collectors
            if (_accelerometerData.subscriptionCount.value > 0) {
                _accelerometerData.tryEmit(Triple(x, y, z))
            }

            // Process data in C++
            nativeProcessData(x, y, z)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Accelerometer Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("destination", "accelerometer")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Accelerometer Service")
            .setContentText("Monitoring accelerometer in the background.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed.")
        if (isAccelerometerEnabled.value) {
            stop()
        }
        _isAccelerometerEnabled.value = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_ACCELEROMETER = "com.opendevelopment.opensensor.START_ACCELEROMETER"
        const val ACTION_STOP_ACCELEROMETER = "com.opendevelopment.opensensor.STOP_ACCELEROMETER"

        const val ACTION_STOP_SERVICE = "com.opendevelopment.opensensor.STOP_SERVICE"

        private val _accelerometerData = MutableSharedFlow<Triple<Float, Float, Float>>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val accelerometerData = _accelerometerData.asSharedFlow()

        private val _isAccelerometerEnabled = MutableStateFlow(false)
        val isAccelerometerEnabled = _isAccelerometerEnabled.asStateFlow()
    }

    private external fun nativeUpdateSettings(multiplierX: Float, multiplierY: Float, multiplierZ: Float, rounding: Int)
    private external fun nativeProcessData(x: Float, y: Float, z: Float)
}
