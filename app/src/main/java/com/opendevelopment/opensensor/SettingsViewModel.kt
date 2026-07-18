package com.opendevelopment.opensensor

import android.hardware.SensorManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsDataStore: SettingsDataStore) : ViewModel() {

    private val serviceManager = ServiceManager(settingsDataStore.context)

    val settings = settingsDataStore.settingsFlow.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        // Initial empty state
        Settings(
            broker = "",
            username = "",
            password = "",
            autoStart = false,
            isMqttEnabled = false,
            isAccelerometerEnabled = false,
            isGyroscopeEnabled = false,
            isGravityEnabled = false,
            isLightSensorEnabled = false,
            isTemperatureSensorEnabled = false,
            accelerometerTopic = "opensensor/sensor/accelerometer",
            accelerometerMultiplierX = "1.0",
            accelerometerMultiplierY = "1.0",
            accelerometerMultiplierZ = "1.0",
            accelerometerRounding = "2",
            accelerometerSamplingPeriod = SensorManager.SENSOR_DELAY_NORMAL,
            gyroscopeTopic = "opensensor/sensor/gyroscope",
            gyroscopeMultiplierX = "1.0",
            gyroscopeMultiplierY = "1.0",
            gyroscopeMultiplierZ = "1.0",
            gyroscopeRounding = "2",
            gyroscopeSamplingPeriod = SensorManager.SENSOR_DELAY_NORMAL,
            gravityTopic = "opensensor/sensor/gravity",
            gravityMultiplierX = "1.0",
            gravityMultiplierY = "1.0",
            gravityMultiplierZ = "1.0",
            gravityRounding = "2",
            gravitySamplingPeriod = SensorManager.SENSOR_DELAY_NORMAL,
            lightSensorTopic = "opensensor/sensor/light",
            lightSensorRounding = "2",
            lightSensorSamplingPeriod = SensorManager.SENSOR_DELAY_NORMAL,
            temperatureSensorTopic = "opensensor/sensor/temperature",
            temperatureSensorRounding = "2",
            temperatureSensorSamplingPeriod = SensorManager.SENSOR_DELAY_NORMAL,
            isHaDiscoveryEnabled = false,
            haDiscoveryPrefix = "homeassistant",
            haDeviceName = "OpenSensor",
            haDeviceId = "opensensor_device",
            availabilityTopic = "opensensor/status"
        )
    )

    init {
        // Services will observe SettingsDataStore directly once they are running.
        // We need to ensure they are started (or "woken up") when their enabled flag is set to true.
        
        // MQTT
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings -> s.isMqttEnabled }
                .distinctUntilChanged()
                .collect { enabled: Boolean -> if (enabled) serviceManager.startMqtt() }
        }

        // Accelerometer
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings -> s.isAccelerometerEnabled }
                .distinctUntilChanged()
                .collect { enabled: Boolean -> if (enabled) serviceManager.startAccelerometer() }
        }

        // Gyroscope
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings -> s.isGyroscopeEnabled }
                .distinctUntilChanged()
                .collect { enabled: Boolean -> if (enabled) serviceManager.startGyroscope() }
        }

        // Gravity
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings -> s.isGravityEnabled }
                .distinctUntilChanged()
                .collect { enabled: Boolean -> if (enabled) serviceManager.startGravity() }
        }

        // Light
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings -> s.isLightSensorEnabled }
                .distinctUntilChanged()
                .collect { enabled: Boolean -> if (enabled) serviceManager.startLightSensor() }
        }

        // Temperature
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map { s: Settings -> s.isTemperatureSensorEnabled }
                .distinctUntilChanged()
                .collect { enabled: Boolean -> if (enabled) serviceManager.startTemperatureSensor() }
        }
    }

    fun updateBroker(broker: String) = viewModelScope.launch { settingsDataStore.updateBroker(broker) }
    fun updateUsername(username: String) = viewModelScope.launch { settingsDataStore.updateUsername(username) }
    fun updatePassword(password: String) = viewModelScope.launch { settingsDataStore.updatePassword(password) }
    fun updateAutoStart(autoStart: Boolean) = viewModelScope.launch { settingsDataStore.updateAutoStart(autoStart) }
    fun updateMqttEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.updateMqttEnabled(enabled) }
    fun updateAccelerometerEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.updateAccelerometerEnabled(enabled) }
    fun updateGyroscopeEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.updateGyroscopeEnabled(enabled) }
    fun updateGravityEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.updateGravityEnabled(enabled) }
    fun updateLightSensorEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.updateLightSensorEnabled(enabled) }
    fun updateTemperatureSensorEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.updateTemperatureSensorEnabled(enabled) }
    fun updateAccelerometerTopic(topic: String) = viewModelScope.launch { settingsDataStore.updateAccelerometerTopic(topic) }
    fun updateAccelerometerMultiplierX(multiplier: String) = viewModelScope.launch { settingsDataStore.updateAccelerometerMultiplierX(multiplier) }
    fun updateAccelerometerMultiplierY(multiplier: String) = viewModelScope.launch { settingsDataStore.updateAccelerometerMultiplierY(multiplier) }
    fun updateAccelerometerMultiplierZ(multiplier: String) = viewModelScope.launch { settingsDataStore.updateAccelerometerMultiplierZ(multiplier) }
    fun updateAccelerometerRounding(rounding: String) = viewModelScope.launch { settingsDataStore.updateAccelerometerRounding(rounding) }
    fun updateAccelerometerSamplingPeriod(samplingPeriod: Int) = viewModelScope.launch { settingsDataStore.updateAccelerometerSamplingPeriod(samplingPeriod) }
    fun updateGyroscopeTopic(topic: String) = viewModelScope.launch { settingsDataStore.updateGyroscopeTopic(topic) }
    fun updateGyroscopeMultiplierX(multiplier: String) = viewModelScope.launch { settingsDataStore.updateGyroscopeMultiplierX(multiplier) }
    fun updateGyroscopeMultiplierY(multiplier: String) = viewModelScope.launch { settingsDataStore.updateGyroscopeMultiplierY(multiplier) }
    fun updateGyroscopeMultiplierZ(multiplier: String) = viewModelScope.launch { settingsDataStore.updateGyroscopeMultiplierZ(multiplier) }
    fun updateGyroscopeRounding(rounding: String) = viewModelScope.launch { settingsDataStore.updateGyroscopeRounding(rounding) }
    fun updateGyroscopeSamplingPeriod(samplingPeriod: Int) = viewModelScope.launch { settingsDataStore.updateGyroscopeSamplingPeriod(samplingPeriod) }
    fun updateGravityTopic(topic: String) = viewModelScope.launch { settingsDataStore.updateGravityTopic(topic) }
    fun updateGravityMultiplierX(multiplier: String) = viewModelScope.launch { settingsDataStore.updateGravityMultiplierX(multiplier) }
    fun updateGravityMultiplierY(multiplier: String) = viewModelScope.launch { settingsDataStore.updateGravityMultiplierY(multiplier) }
    fun updateGravityMultiplierZ(multiplier: String) = viewModelScope.launch { settingsDataStore.updateGravityMultiplierZ(multiplier) }
    fun updateGravityRounding(rounding: String) = viewModelScope.launch { settingsDataStore.updateGravityRounding(rounding) }
    fun updateGravitySamplingPeriod(samplingPeriod: Int) = viewModelScope.launch { settingsDataStore.updateGravitySamplingPeriod(samplingPeriod) }
    fun updateLightSensorTopic(topic: String) = viewModelScope.launch { settingsDataStore.updateLightSensorTopic(topic) }
    fun updateLightSensorRounding(rounding: String) = viewModelScope.launch { settingsDataStore.updateLightSensorRounding(rounding) }
    fun updateLightSensorSamplingPeriod(samplingPeriod: Int) = viewModelScope.launch { settingsDataStore.updateLightSensorSamplingPeriod(samplingPeriod) }
    fun updateTemperatureSensorTopic(topic: String) = viewModelScope.launch { settingsDataStore.updateTemperatureSensorTopic(topic) }
    fun updateTemperatureSensorRounding(rounding: String) = viewModelScope.launch { settingsDataStore.updateTemperatureSensorRounding(rounding) }
    fun updateTemperatureSensorSamplingPeriod(samplingPeriod: Int) = viewModelScope.launch { settingsDataStore.updateTemperatureSensorSamplingPeriod(samplingPeriod) }

    fun updateHaDiscoveryEnabled(enabled: Boolean) = viewModelScope.launch { settingsDataStore.updateHaDiscoveryEnabled(enabled) }
    fun updateHaDiscoveryPrefix(prefix: String) = viewModelScope.launch { settingsDataStore.updateHaDiscoveryPrefix(prefix) }
    fun updateHaDeviceName(name: String) = viewModelScope.launch { settingsDataStore.updateHaDeviceName(name) }
    fun updateHaDeviceId(id: String) = viewModelScope.launch { settingsDataStore.updateHaDeviceId(id) }
    fun updateAvailabilityTopic(topic: String) = viewModelScope.launch { settingsDataStore.updateAvailabilityTopic(topic) }
}

class SettingsViewModelFactory(private val settingsDataStore: SettingsDataStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsDataStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
