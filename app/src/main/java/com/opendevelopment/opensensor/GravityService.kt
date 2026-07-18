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

class GravityService : Service(), SensorEventListener {

    private val tag = "GravityService"
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val notificationId = 5 // Unique ID for GravityService
    private val channelId = "GravityServiceChannel"

    private lateinit var sensorManager: SensorManager
    private var gravitySensor: Sensor? = null
    private var isStarted = false
    private lateinit var settingsDataStore: SettingsDataStore

    private data class GravityConfig(
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
        gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
        settingsDataStore = SettingsDataStore(this)
        System.loadLibrary("opensensor_native")

        createNotificationChannel()

        serviceScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings ->
                    GravityConfig(
                        s.isGravityEnabled,
                        s.gravityMultiplierX.toFloatOrNull() ?: 1.0f,
                        s.gravityMultiplierY.toFloatOrNull() ?: 1.0f,
                        s.gravityMultiplierZ.toFloatOrNull() ?: 1.0f,
                        s.gravityRounding.toIntOrNull() ?: 2,
                        s.gravitySamplingPeriod
                    )
                }
                .distinctUntilChanged()
                .collect { config: GravityConfig ->
                    if (!config.isEnabled) {
                        stopSelf()
                        return@collect
                    }

                    updateSettings(config.multiplierX, config.multiplierY, config.multiplierZ, config.rounding)
                    start(config.samplingPeriod)
                    _isGravityEnabled.value = isStarted

                    if (!isStarted) {
                        settingsDataStore.updateGravityEnabled(false)
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
        Log.d(tag, "Starting gravity listener with sampling period: $samplingPeriod")
        if (isStarted) {
            sensorManager.unregisterListener(this)
        }
        if (gravitySensor != null) {
            sensorManager.registerListener(this, gravitySensor, samplingPeriod)
            isStarted = true
        } else {
            Log.e(tag, "Gravity sensor not available on this device.")
            IconToast.show(this, "Gravity sensor not available on this device.")
            isStarted = false
        }
    }

    private fun stop() {
        if (isStarted) {
            Log.d(tag, "Stopping gravity listener.")
            sensorManager.unregisterListener(this)
            isStarted = false
        }
    }

    private fun updateSettings(multiplierX: Float, multiplierY: Float, multiplierZ: Float, rounding: Int) {
        nativeUpdateGravitySettings(multiplierX, multiplierY, multiplierZ, rounding)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_GRAVITY) {
            val (x, y, z) = event.values

            // For the UI - only emit if there are active collectors
            if (_gravityData.subscriptionCount.value > 0) {
                _gravityData.tryEmit(Triple(x, y, z))
            }

            // Process data in C++
            nativeProcessGravityData(x, y, z)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                channelId,
                "Gravity Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra("destination", "gravity")
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Gravity Service")
            .setContentText("Monitoring gravity in the background.")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(tag, "Service destroyed.")
        if (isGravityEnabled.value) {
            stop()
        }
        _isGravityEnabled.value = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START_GRAVITY = "com.opendevelopment.opensensor.START_GRAVITY"
        const val ACTION_STOP_GRAVITY = "com.opendevelopment.opensensor.STOP_GRAVITY"

        const val ACTION_STOP_SERVICE = "com.opendevelopment.opensensor.STOP_SERVICE"

        private val _gravityData = MutableSharedFlow<Triple<Float, Float, Float>>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val gravityData = _gravityData.asSharedFlow()

        private val _isGravityEnabled = MutableStateFlow(false)
        val isGravityEnabled = _isGravityEnabled.asStateFlow()
    }

    private external fun nativeUpdateGravitySettings(multiplierX: Float, multiplierY: Float, multiplierZ: Float, rounding: Int)
    private external fun nativeProcessGravityData(x: Float, y: Float, z: Float)
}
