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
            accelerometerTopic = "opensensor/sensor/accelerometer",
            accelerometerMultiplierX = "1.0",
            accelerometerMultiplierY = "1.0",
            accelerometerMultiplierZ = "1.0",
            accelerometerRounding = "5",
            accelerometerSamplingPeriod = SensorManager.SENSOR_DELAY_NORMAL,
            gyroscopeTopic = "opensensor/sensor/gyroscope",
            gyroscopeMultiplierX = "1.0",
            gyroscopeMultiplierY = "1.0",
            gyroscopeMultiplierZ = "1.0",
            gyroscopeRounding = "5",
            gyroscopeSamplingPeriod = SensorManager.SENSOR_DELAY_NORMAL
        )
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

    init {
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