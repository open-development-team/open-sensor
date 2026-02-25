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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created.")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        settingsDataStore = SettingsDataStore(this)
        System.loadLibrary("opensensor_native")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(notificationId, notification)

        Log.d(tag, "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_GYROSCOPE -> {
                val multiplierX = intent.getFloatExtra("GYROSCOPE_MULTIPLIER_X", 1.0f)
                val multiplierY = intent.getFloatExtra("GYROSCOPE_MULTIPLIER_Y", 1.0f)
                val multiplierZ = intent.getFloatExtra("GYROSCOPE_MULTIPLIER_Z", 1.0f)
                val rounding = intent.getIntExtra("GYROSCOPE_ROUNDING", 2)
                val samplingPeriod = intent.getIntExtra("GYROSCOPE_SAMPLING_PERIOD", SensorManager.SENSOR_DELAY_NORMAL)
                updateSettings(multiplierX, multiplierY, multiplierZ, rounding)
                start(samplingPeriod)
                _isGyroscopeEnabled.value = isStarted
                if (!isStarted) {
                    serviceScope.launch {
                        settingsDataStore.updateGyroscopeEnabled(false)
                    }
                    stopSelf()
                }
            }
            ACTION_STOP_GYROSCOPE -> {
                stopSelf()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }

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

            // For the UI
            serviceScope.launch {
                _gyroscopeData.emit(Triple(x, y, z))
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

        private val _gyroscopeData = MutableSharedFlow<Triple<Float, Float, Float>>()
        val gyroscopeData = _gyroscopeData.asSharedFlow()

        private val _isGyroscopeEnabled = MutableStateFlow(false)
        val isGyroscopeEnabled = _isGyroscopeEnabled.asStateFlow()
    }

    private external fun nativeUpdateGyroscopeSettings(multiplierX: Float, multiplierY: Float, multiplierZ: Float, rounding: Int)
    private external fun nativeProcessGyroscopeData(x: Float, y: Float, z: Float)
}
