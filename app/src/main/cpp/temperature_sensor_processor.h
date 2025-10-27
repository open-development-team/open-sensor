#ifndef OPEN_SENSOR_TEMPERATURE_SENSOR_PROCESSOR_H
#define OPEN_SENSOR_TEMPERATURE_SENSOR_PROCESSOR_H

#include <string>
#include <limits>
#include "mqtt_client_wrapper.h"

class TemperatureSensorProcessor {
public:
    explicit TemperatureSensorProcessor(MqttClientWrapper* mqttClientWrapper, std::string topic);

    void updateSettings(int rounding);
    void processData(float value);

private:
    MqttClientWrapper* mqttClientWrapper_;
    std::string topic_;
    int rounding_ = 2;
    float lastValue_ = std::numeric_limits<float>::quiet_NaN();
};

#endif //OPEN_SENSOR_TEMPERATURE_SENSOR_PROCESSOR_H
