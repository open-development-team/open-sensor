package com.opendevelopment.opensensor

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ServiceManager(private val context: Context) {

    suspend fun ensureStarted() {
        val settingsDataStore = SettingsDataStore(context)
        val settings = settingsDataStore.settingsFlow.first()

        if (settings.isMqttEnabled) startMqtt()
        if (settings.isAccelerometerEnabled) startAccelerometer()
        if (settings.isGyroscopeEnabled) startGyroscope()
        if (settings.isGravityEnabled) startGravity()
        if (settings.isLightSensorEnabled) startLightSensor()
        if (settings.isTemperatureSensorEnabled) startTemperatureSensor()
    }

    fun startMqtt() { context.startService(Intent(context, MqttService::class.java)) }
    fun startAccelerometer() { context.startService(Intent(context, AccelerometerService::class.java)) }
    fun startGyroscope() { context.startService(Intent(context, GyroscopeService::class.java)) }
    fun startGravity() { context.startService(Intent(context, GravityService::class.java)) }
    fun startLightSensor() { context.startService(Intent(context, LightSensorService::class.java)) }
    fun startTemperatureSensor() { context.startService(Intent(context, TemperatureSensorService::class.java)) }

    fun stopAll() {
        context.stopService(Intent(context, MqttService::class.java))
        context.stopService(Intent(context, AccelerometerService::class.java))
        context.stopService(Intent(context, GyroscopeService::class.java))
        context.stopService(Intent(context, GravityService::class.java))
        context.stopService(Intent(context, LightSensorService::class.java))
        context.stopService(Intent(context, TemperatureSensorService::class.java))
    }
}
