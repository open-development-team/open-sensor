#include "sensor_processor.h"
#include <cstdio>
#include <cmath> // For fabs and trunc
#include <android/log.h>

#define LOG_TAG "SensorProcessor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// A small epsilon value for float comparison
constexpr float SENSOR_EPSILON = std::numeric_limits<float>::epsilon();

SensorProcessor::SensorProcessor(MqttClientWrapper* mqttClientWrapper, std::string topic)
    : mqttClientWrapper_(mqttClientWrapper), topic_(std::move(topic)) {}

void SensorProcessor::updateSettings(float multiplierX, float multiplierY, float multiplierZ, int rounding) {
    LOGD("Updating settings for topic %s: multipliers(%.2f, %.2f, %.2f), rounding(%d)",
         topic_.c_str(), multiplierX, multiplierY, multiplierZ, rounding);
    multiplierX_ = multiplierX;
    multiplierY_ = multiplierY;
    multiplierZ_ = multiplierZ;
    rounding_ = rounding;
    // Reset last values to ensure the next event is published
    lastX_ = std::numeric_limits<float>::quiet_NaN();
    lastY_ = std::numeric_limits<float>::quiet_NaN();
    lastZ_ = std::numeric_limits<float>::quiet_NaN();
}

void SensorProcessor::processData(float x, float y, float z) {
    if (topic_.empty()) {
        return; // Do not process if the topic is empty
    }

    float processedX = x * multiplierX_;
    float processedY = y * multiplierY_;
    float processedZ = z * multiplierZ_;

    // Round the values first before comparison
    auto powerOf10 = static_cast<float>(std::pow(10, rounding_));
    float roundedX = std::trunc(processedX * powerOf10) / powerOf10;
    float roundedY = std::trunc(processedY * powerOf10) / powerOf10;
    float roundedZ = std::trunc(processedZ * powerOf10) / powerOf10;

    // Coerce negative zero to positive zero
    if (roundedX == 0.0f) roundedX = 0.0f;
    if (roundedY == 0.0f) roundedY = 0.0f;
    if (roundedZ == 0.0f) roundedZ = 0.0f;

    // Check if the rounded values have changed
    if (std::fabs(roundedX - lastX_) < SENSOR_EPSILON &&
        std::fabs(roundedY - lastY_) < SENSOR_EPSILON &&
        std::fabs(roundedZ - lastZ_) < SENSOR_EPSILON) {
        return; // Do not publish if data is unchanged
    }

    char buffer[256];
    snprintf(buffer, sizeof(buffer), "{\"x\":%.*f,\"y\":%.*f,\"z\":%.*f}",
             rounding_, roundedX,
             rounding_, roundedY,
             rounding_, roundedZ);

    if (mqttClientWrapper_->publish(topic_, buffer)) {
        // Update last values with the new rounded values
        lastX_ = roundedX;
        lastY_ = roundedY;
        lastZ_ = roundedZ;
    }
}
