package com.opendevelopment.opensensor

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first

class ServiceManager(private val context: Context) {

    suspend fun startAllServices() {
        val settingsDataStore = SettingsDataStore(context)
        val settings = settingsDataStore.settingsFlow.first()

        if (settings.isMqttEnabled) {
             startMqtt()
        }

        if (settings.isAccelerometerEnabled) {
            startAccelerometer()
        }

        if (settings.isGyroscopeEnabled) {
            startGyroscope()
        }
    }

    suspend fun startMqtt() {
        val settingsDataStore = SettingsDataStore(context)
        val settings = settingsDataStore.settingsFlow.first()
        val intent = Intent(context, MqttService::class.java).apply {
            action = MqttService.ACTION_CONNECT
            putExtra("BROKER_URL", settings.broker)
            putExtra("USERNAME", settings.username)
            putExtra("PASSWORD", settings.password)
        }
        context.startService(intent)
    }

    fun stopMqtt() {
        val intent = Intent(context, MqttService::class.java).apply {
            action = MqttService.ACTION_DISCONNECT
        }
        context.startService(intent)
    }

    suspend fun startAccelerometer() {
        val settingsDataStore = SettingsDataStore(context)
        val settings = settingsDataStore.settingsFlow.first()
        val intent = Intent(context, AccelerometerService::class.java).apply {
            action = AccelerometerService.ACTION_START_ACCELEROMETER
            putExtra("MULTIPLIER_X", settings.accelerometerMultiplierX.toFloatOrNull() ?: 1.0f)
            putExtra("MULTIPLIER_Y", settings.accelerometerMultiplierY.toFloatOrNull() ?: 1.0f)
            putExtra("MULTIPLIER_Z", settings.accelerometerMultiplierZ.toFloatOrNull() ?: 1.0f)
            putExtra("ROUNDING", settings.accelerometerRounding.toIntOrNull() ?: 2)
            putExtra("SAMPLING_PERIOD", settings.accelerometerSamplingPeriod)
        }
        context.startService(intent)
    }

    fun stopAccelerometer() {
        val intent = Intent(context, AccelerometerService::class.java).apply {
            action = AccelerometerService.ACTION_STOP_ACCELEROMETER
        }
        context.startService(intent)
    }

    suspend fun updateAccelerometerConfig() {
        startAccelerometer()
    }

    suspend fun startGyroscope() {
        val settingsDataStore = SettingsDataStore(context)
        val settings = settingsDataStore.settingsFlow.first()
        val intent = Intent(context, GyroscopeService::class.java).apply {
            action = GyroscopeService.ACTION_START_GYROSCOPE
            putExtra("GYROSCOPE_MULTIPLIER_X", settings.gyroscopeMultiplierX.toFloatOrNull() ?: 1.0f)
            putExtra("GYROSCOPE_MULTIPLIER_Y", settings.gyroscopeMultiplierY.toFloatOrNull() ?: 1.0f)
            putExtra("GYROSCOPE_MULTIPLIER_Z", settings.gyroscopeMultiplierZ.toFloatOrNull() ?: 1.0f)
            putExtra("GYROSCOPE_ROUNDING", settings.gyroscopeRounding.toIntOrNull() ?: 2)
            putExtra("GYROSCOPE_SAMPLING_PERIOD", settings.gyroscopeSamplingPeriod)
        }
        context.startService(intent)
    }

    fun stopGyroscope() {
        val intent = Intent(context, GyroscopeService::class.java).apply {
            action = GyroscopeService.ACTION_STOP_GYROSCOPE
        }
        context.startService(intent)
    }

    suspend fun updateGyroscopeConfig() {
        startGyroscope()
    }
}
