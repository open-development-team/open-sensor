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

class GyroscopeService : Service(), SensorEventListener {

    private val tag = "GyroscopeService"
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val notificationId = 2 // Different ID for the notification
    private val channelId = "GyroscopeServiceChannel"

    private lateinit var sensorManager: SensorManager
    private var gyroscope: Sensor? = null
    private var isStarted = false
    private lateinit var settingsDataStore: SettingsDataStore

    private data class GyroscopeConfig(
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
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        settingsDataStore = SettingsDataStore(this)
        System.loadLibrary("opensensor_native")

        createNotificationChannel()

        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings ->
                    GyroscopeConfig(
                        s.isGyroscopeEnabled,
                        s.gyroscopeMultiplierX.toFloatOrNull() ?: 1.0f,
                        s.gyroscopeMultiplierY.toFloatOrNull() ?: 1.0f,
                        s.gyroscopeMultiplierZ.toFloatOrNull() ?: 1.0f,
                        s.gyroscopeRounding.toIntOrNull() ?: 2,
                        s.gyroscopeSamplingPeriod
                    )
                }
                .distinctUntilChanged()
                .collect { config: GyroscopeConfig ->
                    if (!config.isEnabled) {
                        stopSelf()
                        return@collect
                    }

                    updateSettings(config.multiplierX, config.multiplierY, config.multiplierZ, config.rounding)
                    start(config.samplingPeriod)
                    _isGyroscopeEnabled.value = isStarted

                    if (!isStarted) {
                        settingsDataStore.updateGyroscopeEnabled(false)
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
        Log.d(tag, "Starting gyroscope listener with sampling period: $samplingPeriod")
        if (isStarted) {
            sensorManager.unregisterListener(this)
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, samplingPeriod)
            isStarted = true
        } else {
            Log.e(tag, "Gyroscope not available on this device.")
            IconToast.show(this, "Gyroscope not available on this device.")
            isStarted = false
        }
    }

    private fun stop() {
        if (isStarted) {
            Log.d(tag, "Stopping gyroscope listener.")
            sensorManager.unregisterListener(this)
            isStarted = false
        }
    }

    private fun updateSettings(multiplierX: Float, multiplierY: Float, multiplierZ: Float, rounding: Int) {
        nativeUpdateGyroscopeSettings(multiplierX, multiplierY, multiplierZ, rounding)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GYROSCOPE) {
            val (x, y, z) = event.values

            // For the UI - only emit if there are active collectors
            if (_gyroscopeData.subscriptionCount.value > 0) {
                _gyroscopeData.tryEmit(Triple(x, y, z))
            }

            // Process data in C++
            nativeProcessGyroscopeData(x, y, z)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Gyroscope Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("destination", "gyroscope")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gyroscope Service")
            .setContentText("Monitoring gyroscope in the background.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed.")
        if (isGyroscopeEnabled.value) {
            stop()
        }
        _isGyroscopeEnabled.value = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_GYROSCOPE = "com.opendevelopment.opensensor.START_GYROSCOPE"
        const val ACTION_STOP_GYROSCOPE = "com.opendevelopment.opensensor.STOP_GYROSCOPE"

        const val ACTION_STOP_SERVICE = "com.opendevelopment.opensensor.STOP_SERVICE"

        private val _gyroscopeData = MutableSharedFlow<Triple<Float, Float, Float>>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val gyroscopeData = _gyroscopeData.asSharedFlow()

        private val _isGyroscopeEnabled = MutableStateFlow(false)
        val isGyroscopeEnabled = _isGyroscopeEnabled.asStateFlow()
    }

    private external fun nativeUpdateGyroscopeSettings(multiplierX: Float, multiplierY: Float, multiplierZ: Float, rounding: Int)
    private external fun nativeProcessGyroscopeData(x: Float, y: Float, z: Float)
}
