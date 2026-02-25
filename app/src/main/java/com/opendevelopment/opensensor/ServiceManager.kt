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

        if (settings.isGravityEnabled) {
            startGravity()
        }

        if (settings.isLightSensorEnabled) {
            startLightSensor()
        }

        if (settings.isTemperatureSensorEnabled) {
            startTemperatureSensor()
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

    suspend fun startGravity() {
        val settingsDataStore = SettingsDataStore(context)
        val settings = settingsDataStore.settingsFlow.first()
        val intent = Intent(context, GravityService::class.java).apply {
            action = GravityService.ACTION_START_GRAVITY
            putExtra("MULTIPLIER_X", settings.gravityMultiplierX.toFloatOrNull() ?: 1.0f)
            putExtra("MULTIPLIER_Y", settings.gravityMultiplierY.toFloatOrNull() ?: 1.0f)
            putExtra("MULTIPLIER_Z", settings.gravityMultiplierZ.toFloatOrNull() ?: 1.0f)
            putExtra("ROUNDING", settings.gravityRounding.toIntOrNull() ?: 2)
            putExtra("SAMPLING_PERIOD", settings.gravitySamplingPeriod)
        }
        context.startService(intent)
    }

    fun stopGravity() {
        val intent = Intent(context, GravityService::class.java).apply {
            action = GravityService.ACTION_STOP_GRAVITY
        }
        context.startService(intent)
    }

    suspend fun updateGravityConfig() {
        startGravity()
    }

    suspend fun startLightSensor() {
        val settingsDataStore = SettingsDataStore(context)
        val settings = settingsDataStore.settingsFlow.first()
        val intent = Intent(context, LightSensorService::class.java).apply {
            action = LightSensorService.ACTION_START_LIGHT_SENSOR
            putExtra("ROUNDING", settings.lightSensorRounding.toIntOrNull() ?: 2)
            putExtra("SAMPLING_PERIOD", settings.lightSensorSamplingPeriod)
        }
        context.startService(intent)
    }

    fun stopLightSensor() {
        val intent = Intent(context, LightSensorService::class.java).apply {
            action = LightSensorService.ACTION_STOP_LIGHT_SENSOR
        }
        context.startService(intent)
    }

    suspend fun updateLightSensorConfig() {
        startLightSensor()
    }

    suspend fun startTemperatureSensor() {
        val settingsDataStore = SettingsDataStore(context)
        val settings = settingsDataStore.settingsFlow.first()
        val intent = Intent(context, TemperatureSensorService::class.java).apply {
            action = TemperatureSensorService.ACTION_START_TEMPERATURE_SENSOR
            putExtra("ROUNDING", settings.temperatureSensorRounding.toIntOrNull() ?: 2)
            putExtra("SAMPLING_PERIOD", settings.temperatureSensorSamplingPeriod)
        }
        context.startService(intent)
    }

    fun stopTemperatureSensor() {
        val intent = Intent(context, TemperatureSensorService::class.java).apply {
            action = TemperatureSensorService.ACTION_STOP_TEMPERATURE_SENSOR
        }
        context.startService(intent)
    }

    suspend fun updateTemperatureSensorConfig() {
        startTemperatureSensor()
    }
}
