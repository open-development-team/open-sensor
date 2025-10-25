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

class AccelerometerService : Service(), SensorEventListener {

    private val tag = "AccelerometerService"
    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())

    private val notificationId = 1
    private val channelId = "AccelerometerServiceChannel"

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var isStarted = false

    override fun onCreate() {
        super.onCreate()
        Log.d(tag, "Service created.")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        System.loadLibrary("opensensor_native")

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(notificationId, notification)

        Log.d(tag, "onStartCommand received action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_ACCELEROMETER -> {
                val multiplierX = intent.getFloatExtra("MULTIPLIER_X", 1.0f)
                val multiplierY = intent.getFloatExtra("MULTIPLIER_Y", 1.0f)
                val multiplierZ = intent.getFloatExtra("MULTIPLIER_Z", 1.0f)
                val rounding = intent.getIntExtra("ROUNDING", 2)
                val samplingPeriod = intent.getIntExtra("SAMPLING_PERIOD", SensorManager.SENSOR_DELAY_NORMAL)
                updateSettings(multiplierX, multiplierY, multiplierZ, rounding)
                start(samplingPeriod)
                _isAccelerometerEnabled.value = true
            }
            ACTION_STOP_ACCELEROMETER -> {
                stopSelf()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }

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

            // For the UI
            serviceScope.launch {
                _accelerometerData.emit(Triple(x, y, z))
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
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Accelerometer Service")
            .setContentText("Monitoring accelerometer in the background.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
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

        private val _accelerometerData = MutableSharedFlow<Triple<Float, Float, Float>>()
        val accelerometerData = _accelerometerData.asSharedFlow()

        private val _isAccelerometerEnabled = MutableStateFlow(false)
        val isAccelerometerEnabled = _isAccelerometerEnabled.asStateFlow()
    }

    private external fun nativeUpdateSettings(multiplierX: Float, multiplierY: Float, multiplierZ: Float, rounding: Int)
    private external fun nativeProcessData(x: Float, y: Float, z: Float)
}
