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
            temperatureSensorSamplingPeriod = SensorManager.SENSOR_DELAY_NORMAL
        )
    )

    private data class MqttSettings(
        val isEnabled: Boolean,
        val broker: String,
        val username: String,
        val password: String
    )

    private data class AccelerometerSettings(
        val isEnabled: Boolean,
        val multiplierX: String,
        val multiplierY: String,
        val multiplierZ: String,
        val rounding: String,
        val samplingPeriod: Int,
        val mqttTopic: String
    )

    private data class GyroscopeSettings(
        val isEnabled: Boolean,
        val multiplierX: String,
        val multiplierY: String,
        val multiplierZ: String,
        val rounding: String,
        val samplingPeriod: Int,
        val mqttTopic: String
    )

    private data class GravitySettings(
        val isEnabled: Boolean,
        val multiplierX: String,
        val multiplierY: String,
        val multiplierZ: String,
        val rounding: String,
        val samplingPeriod: Int,
        val mqttTopic: String
    )

    private data class LightSensorSettings(
        val isEnabled: Boolean,
        val rounding: String,
        val samplingPeriod: Int,
        val mqttTopic: String
    )

    private data class TemperatureSensorSettings(
        val isEnabled: Boolean,
        val rounding: String,
        val samplingPeriod: Int,
        val mqttTopic: String
    )

    init {
        // Observe MQTT settings changes
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map {
                    MqttSettings(
                        it.isMqttEnabled,
                        it.broker,
                        it.username,
                        it.password
                    )
                }
                .distinctUntilChanged()
                .collect {
                    if (it.isEnabled) {
                        serviceManager.startMqtt()
                    } else {
                        serviceManager.stopMqtt()
                    }
                }
        }

        // Observe accelerometer settings changes
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map {
                    AccelerometerSettings(
                        it.isAccelerometerEnabled,
                        it.accelerometerMultiplierX,
                        it.accelerometerMultiplierY,
                        it.accelerometerMultiplierZ,
                        it.accelerometerRounding,
                        it.accelerometerSamplingPeriod,
                        it.accelerometerTopic
                    )
                }
                .distinctUntilChanged()
                .collect {
                    if (it.isEnabled) {
                        serviceManager.updateAccelerometerConfig()
                    } else {
                        serviceManager.stopAccelerometer()
                    }
                }
        }

        // Observe gyroscope settings changes
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map {
                    GyroscopeSettings(
                        it.isGyroscopeEnabled,
                        it.gyroscopeMultiplierX,
                        it.gyroscopeMultiplierY,
                        it.gyroscopeMultiplierZ,
                        it.gyroscopeRounding,
                        it.gyroscopeSamplingPeriod,
                        it.gyroscopeTopic
                    )
                }
                .distinctUntilChanged()
                .collect {
                    if (it.isEnabled) {
                        serviceManager.updateGyroscopeConfig()
                    } else {
                        serviceManager.stopGyroscope()
                    }
                }
        }

        // Observe gravity settings changes
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map {
                    GravitySettings(
                        it.isGravityEnabled,
                        it.gravityMultiplierX,
                        it.gravityMultiplierY,
                        it.gravityMultiplierZ,
                        it.gravityRounding,
                        it.gravitySamplingPeriod,
                        it.gravityTopic
                    )
                }
                .distinctUntilChanged()
                .collect {
                    if (it.isEnabled) {
                        serviceManager.updateGravityConfig()
                    } else {
                        serviceManager.stopGravity()
                    }
                }
        }

        // Observe light sensor settings changes
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map {
                    LightSensorSettings(
                        it.isLightSensorEnabled,
                        it.lightSensorRounding,
                        it.lightSensorSamplingPeriod,
                        it.lightSensorTopic
                    )
                }
                .distinctUntilChanged()
                .collect {
                    if (it.isEnabled) {
                        serviceManager.updateLightSensorConfig()
                    } else {
                        serviceManager.stopLightSensor()
                    }
                }
        }

        // Observe temperature sensor settings changes
        viewModelScope.launch {
            settingsDataStore.settingsFlow
                .map {
                    TemperatureSensorSettings(
                        it.isTemperatureSensorEnabled,
                        it.temperatureSensorRounding,
                        it.temperatureSensorSamplingPeriod,
                        it.temperatureSensorTopic
                    )
                }
                .distinctUntilChanged()
                .collect {
                    if (it.isEnabled) {
                        serviceManager.updateTemperatureSensorConfig()
                    } else {
                        serviceManager.stopTemperatureSensor()
                    }
                }
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
