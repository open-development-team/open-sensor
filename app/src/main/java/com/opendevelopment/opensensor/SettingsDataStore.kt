package com.opendevelopment.opensensor

import android.content.Context
import android.hardware.SensorManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class Settings(
    val broker: String,
    val username: String,
    val password: String,
    val autoStart: Boolean,
    val isMqttEnabled: Boolean,
    val isAccelerometerEnabled: Boolean,
    val isGyroscopeEnabled: Boolean,
    val isLightSensorEnabled: Boolean,
    val isTemperatureSensorEnabled: Boolean,
    val accelerometerTopic: String,
    val accelerometerMultiplierX: String,
    val accelerometerMultiplierY: String,
    val accelerometerMultiplierZ: String,
    val accelerometerRounding: String,
    val accelerometerSamplingPeriod: Int,
    val gyroscopeTopic: String,
    val gyroscopeMultiplierX: String,
    val gyroscopeMultiplierY: String,
    val gyroscopeMultiplierZ: String,
    val gyroscopeRounding: String,
    val gyroscopeSamplingPeriod: Int,
    val lightSensorTopic: String,
    val lightSensorRounding: String,
    val lightSensorSamplingPeriod: Int,
    val temperatureSensorTopic: String,
    val temperatureSensorRounding: String,
    val temperatureSensorSamplingPeriod: Int
)

class SettingsDataStore(val context: Context) {

    private object PreferenceKeys {
        val BROKER = stringPreferencesKey("broker")
        val USERNAME = stringPreferencesKey("username")
        val PASSWORD = stringPreferencesKey("password")
        val AUTO_START = booleanPreferencesKey("auto_start")
        val MQTT_ENABLED = booleanPreferencesKey("mqtt_enabled")

        val ACCELEROMETER_ENABLED = booleanPreferencesKey("accelerometer_enabled")
        val ACCELEROMETER_TOPIC = stringPreferencesKey("accelerometer_topic")
        val ACCELEROMETER_MULTIPLIER_X = stringPreferencesKey("accelerometer_multiplier_x")
        val ACCELEROMETER_MULTIPLIER_Y = stringPreferencesKey("accelerometer_multiplier_y")
        val ACCELEROMETER_MULTIPLIER_Z = stringPreferencesKey("accelerometer_multiplier_z")
        val ACCELEROMETER_ROUNDING = stringPreferencesKey("accelerometer_rounding")
        val ACCELEROMETER_SAMPLING_PERIOD = intPreferencesKey("accelerometer_sampling_period")

        val GYROSCOPE_ENABLED = booleanPreferencesKey("gyroscope_enabled")
        val GYROSCOPE_TOPIC = stringPreferencesKey("gyroscope_topic")
        val GYROSCOPE_MULTIPLIER_X = stringPreferencesKey("gyroscope_multiplier_x")
        val GYROSCOPE_MULTIPLIER_Y = stringPreferencesKey("gyroscope_multiplier_y")
        val GYROSCOPE_MULTIPLIER_Z = stringPreferencesKey("gyroscope_multiplier_z")
        val GYROSCOPE_ROUNDING = stringPreferencesKey("gyroscope_rounding")
        val GYROSCOPE_SAMPLING_PERIOD = intPreferencesKey("gyroscope_sampling_period")

        val LIGHT_SENSOR_ENABLED = booleanPreferencesKey("light_sensor_enabled")
        val LIGHT_SENSOR_TOPIC = stringPreferencesKey("light_sensor_topic")
        val LIGHT_SENSOR_ROUNDING = stringPreferencesKey("light_sensor_rounding")
        val LIGHT_SENSOR_SAMPLING_PERIOD = intPreferencesKey("light_sensor_sampling_period")

        val TEMPERATURE_SENSOR_ENABLED = booleanPreferencesKey("temperature_sensor_enabled")
        val TEMPERATURE_SENSOR_TOPIC = stringPreferencesKey("temperature_sensor_topic")
        val TEMPERATURE_SENSOR_ROUNDING = stringPreferencesKey("temperature_sensor_rounding")
        val TEMPERATURE_SENSOR_SAMPLING_PERIOD = intPreferencesKey("temperature_sensor_sampling_period")
    }

    val settingsFlow: Flow<Settings> = context.dataStore.data
        .map { preferences ->
            Settings(
                broker = preferences[PreferenceKeys.BROKER] ?: "",
                username = preferences[PreferenceKeys.USERNAME] ?: "",
                password = preferences[PreferenceKeys.PASSWORD] ?: "",
                autoStart = preferences[PreferenceKeys.AUTO_START] ?: false,
                isMqttEnabled = preferences[PreferenceKeys.MQTT_ENABLED] ?: false,
                isAccelerometerEnabled = preferences[PreferenceKeys.ACCELEROMETER_ENABLED] ?: false,
                isGyroscopeEnabled = preferences[PreferenceKeys.GYROSCOPE_ENABLED] ?: false,
                isLightSensorEnabled = preferences[PreferenceKeys.LIGHT_SENSOR_ENABLED] ?: false,
                isTemperatureSensorEnabled = preferences[PreferenceKeys.TEMPERATURE_SENSOR_ENABLED] ?: false,

                accelerometerTopic = preferences[PreferenceKeys.ACCELEROMETER_TOPIC] ?: "opensensor/sensor/accelerometer",
                accelerometerMultiplierX = preferences[PreferenceKeys.ACCELEROMETER_MULTIPLIER_X] ?: "1.0",
                accelerometerMultiplierY = preferences[PreferenceKeys.ACCELEROMETER_MULTIPLIER_Y] ?: "1.0",
                accelerometerMultiplierZ = preferences[PreferenceKeys.ACCELEROMETER_MULTIPLIER_Z] ?: "1.0",
                accelerometerRounding = preferences[PreferenceKeys.ACCELEROMETER_ROUNDING] ?: "2",
                accelerometerSamplingPeriod = preferences[PreferenceKeys.ACCELEROMETER_SAMPLING_PERIOD] ?: SensorManager.SENSOR_DELAY_NORMAL,

                gyroscopeTopic = preferences[PreferenceKeys.GYROSCOPE_TOPIC] ?: "opensensor/sensor/gyroscope",
                gyroscopeMultiplierX = preferences[PreferenceKeys.GYROSCOPE_MULTIPLIER_X] ?: "1.0",
                gyroscopeMultiplierY = preferences[PreferenceKeys.GYROSCOPE_MULTIPLIER_Y] ?: "1.0",
                gyroscopeMultiplierZ = preferences[PreferenceKeys.GYROSCOPE_MULTIPLIER_Z] ?: "1.0",
                gyroscopeRounding = preferences[PreferenceKeys.GYROSCOPE_ROUNDING] ?: "2",
                gyroscopeSamplingPeriod = preferences[PreferenceKeys.GYROSCOPE_SAMPLING_PERIOD] ?: SensorManager.SENSOR_DELAY_NORMAL,

                lightSensorTopic = preferences[PreferenceKeys.LIGHT_SENSOR_TOPIC] ?: "opensensor/sensor/light",
                lightSensorRounding = preferences[PreferenceKeys.LIGHT_SENSOR_ROUNDING] ?: "2",
                lightSensorSamplingPeriod = preferences[PreferenceKeys.LIGHT_SENSOR_SAMPLING_PERIOD] ?: SensorManager.SENSOR_DELAY_NORMAL,

                temperatureSensorTopic = preferences[PreferenceKeys.TEMPERATURE_SENSOR_TOPIC] ?: "opensensor/sensor/temperature",
                temperatureSensorRounding = preferences[PreferenceKeys.TEMPERATURE_SENSOR_ROUNDING] ?: "2",
                temperatureSensorSamplingPeriod = preferences[PreferenceKeys.TEMPERATURE_SENSOR_SAMPLING_PERIOD] ?: SensorManager.SENSOR_DELAY_NORMAL
            )
        }

    suspend fun updateBroker(broker: String) {
        context.dataStore.edit { it[PreferenceKeys.BROKER] = broker }
    }

    suspend fun updateUsername(username: String) {
        context.dataStore.edit { it[PreferenceKeys.USERNAME] = username }
    }

    suspend fun updatePassword(password: String) {
        context.dataStore.edit { it[PreferenceKeys.PASSWORD] = password }
    }

    suspend fun updateAutoStart(autoStart: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.AUTO_START] = autoStart }
    }

    suspend fun updateMqttEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.MQTT_ENABLED] = enabled }
    }

    suspend fun updateAccelerometerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.ACCELEROMETER_ENABLED] = enabled }
    }

    suspend fun updateGyroscopeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.GYROSCOPE_ENABLED] = enabled }
    }

    suspend fun updateLightSensorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.LIGHT_SENSOR_ENABLED] = enabled }
    }

    suspend fun updateTemperatureSensorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[PreferenceKeys.TEMPERATURE_SENSOR_ENABLED] = enabled }
    }

    suspend fun updateAccelerometerTopic(topic: String) {
        context.dataStore.edit { it[PreferenceKeys.ACCELEROMETER_TOPIC] = topic }
    }

    suspend fun updateAccelerometerMultiplierX(multiplier: String) {
        context.dataStore.edit { it[PreferenceKeys.ACCELEROMETER_MULTIPLIER_X] = multiplier }
    }

    suspend fun updateAccelerometerMultiplierY(multiplier: String) {
        context.dataStore.edit { it[PreferenceKeys.ACCELEROMETER_MULTIPLIER_Y] = multiplier }
    }

    suspend fun updateAccelerometerMultiplierZ(multiplier: String) {
        context.dataStore.edit { it[PreferenceKeys.ACCELEROMETER_MULTIPLIER_Z] = multiplier }
    }

    suspend fun updateAccelerometerRounding(rounding: String) {
        context.dataStore.edit { it[PreferenceKeys.ACCELEROMETER_ROUNDING] = rounding }
    }

    suspend fun updateAccelerometerSamplingPeriod(samplingPeriod: Int) {
        context.dataStore.edit { it[PreferenceKeys.ACCELEROMETER_SAMPLING_PERIOD] = samplingPeriod }
    }

    suspend fun updateGyroscopeTopic(topic: String) {
        context.dataStore.edit { it[PreferenceKeys.GYROSCOPE_TOPIC] = topic }
    }

    suspend fun updateGyroscopeMultiplierX(multiplier: String) {
        context.dataStore.edit { it[PreferenceKeys.GYROSCOPE_MULTIPLIER_X] = multiplier }
    }

    suspend fun updateGyroscopeMultiplierY(multiplier: String) {
        context.dataStore.edit { it[PreferenceKeys.GYROSCOPE_MULTIPLIER_Y] = multiplier }
    }

    suspend fun updateGyroscopeMultiplierZ(multiplier: String) {
        context.dataStore.edit { it[PreferenceKeys.GYROSCOPE_MULTIPLIER_Z] = multiplier }
    }

    suspend fun updateGyroscopeRounding(rounding: String) {
        context.dataStore.edit { it[PreferenceKeys.GYROSCOPE_ROUNDING] = rounding }
    }

    suspend fun updateGyroscopeSamplingPeriod(samplingPeriod: Int) {
        context.dataStore.edit { it[PreferenceKeys.GYROSCOPE_SAMPLING_PERIOD] = samplingPeriod }
    }

    suspend fun updateLightSensorTopic(topic: String) {
        context.dataStore.edit { it[PreferenceKeys.LIGHT_SENSOR_TOPIC] = topic }
    }

    suspend fun updateLightSensorRounding(rounding: String) {
        context.dataStore.edit { it[PreferenceKeys.LIGHT_SENSOR_ROUNDING] = rounding }
    }

    suspend fun updateLightSensorSamplingPeriod(samplingPeriod: Int) {
        context.dataStore.edit { it[PreferenceKeys.LIGHT_SENSOR_SAMPLING_PERIOD] = samplingPeriod }
    }

    suspend fun updateTemperatureSensorTopic(topic: String) {
        context.dataStore.edit { it[PreferenceKeys.TEMPERATURE_SENSOR_TOPIC] = topic }
    }

    suspend fun updateTemperatureSensorRounding(rounding: String) {
        context.dataStore.edit { it[PreferenceKeys.TEMPERATURE_SENSOR_ROUNDING] = rounding }
    }

    suspend fun updateTemperatureSensorSamplingPeriod(samplingPeriod: Int) {
        context.dataStore.edit { it[PreferenceKeys.TEMPERATURE_SENSOR_SAMPLING_PERIOD] = samplingPeriod }
    }
}
