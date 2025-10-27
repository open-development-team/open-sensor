#include "temperature_sensor_processor.h"
#include <cmath> // For fabs and trunc
#include <cstdio>

// A small epsilon value for float comparison
constexpr float SENSOR_EPSILON = std::numeric_limits<float>::epsilon();

TemperatureSensorProcessor::TemperatureSensorProcessor(MqttClientWrapper* mqttClientWrapper, std::string topic)
    : mqttClientWrapper_(mqttClientWrapper), topic_(std::move(topic)) {}

void TemperatureSensorProcessor::updateSettings(int rounding) {
    rounding_ = rounding;
    lastValue_ = std::numeric_limits<float>::quiet_NaN();
}

void TemperatureSensorProcessor::processData(float value) {
    if (topic_.empty()) {
        return; // Do not process if the topic is empty
    }

    // Round the value first before comparison
    auto powerOf10 = static_cast<float>(std::pow(10, rounding_));
    float roundedValue = std::trunc(value * powerOf10) / powerOf10;

    // Coerce negative zero to positive zero
    if (roundedValue == 0.0f) roundedValue = 0.0f;

    // Check if the rounded value has changed
    if (std::fabs(roundedValue - lastValue_) < SENSOR_EPSILON) {
        return; // Do not publish if data is unchanged
    }

    char buffer[256];
    snprintf(buffer, sizeof(buffer), "{\"value\":%.*f}", rounding_, roundedValue);

    if (mqttClientWrapper_->publish(topic_, buffer)) {
        // Update last value with the new rounded value
        lastValue_ = roundedValue;
    }
}
