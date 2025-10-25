#ifndef HAANDROIDACCELEROMETER_SENSOR_PROCESSOR_H
#define HAANDROIDACCELEROMETER_SENSOR_PROCESSOR_H

#include <string>
#include <limits> // Required for std::numeric_limits
#include <utility>
#include "mqtt_client_wrapper.h"

class SensorProcessor {
public:
    explicit SensorProcessor(MqttClientWrapper* mqttClientWrapper, std::string topic);

    void updateSettings(float multiplierX, float multiplierY, float multiplierZ, int rounding);
    void processData(float x, float y, float z);

private:
    MqttClientWrapper* mqttClientWrapper_;
    std::string topic_;

    float multiplierX_ = 1.0f;
    float multiplierY_ = 1.0f;
    float multiplierZ_ = 1.0f;
    int rounding_ = 2;

    // Variables to store the last published values
    float lastX_ = std::numeric_limits<float>::quiet_NaN();
    float lastY_ = std::numeric_limits<float>::quiet_NaN();
    float lastZ_ = std::numeric_limits<float>::quiet_NaN();
};

#endif //HAANDROIDACCELEROMETER_SENSOR_PROCESSOR_H
