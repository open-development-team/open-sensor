# Open Sensor

Open Sensor is mainly written in order to provide an open-source and safe way to access sensor data of the device. It has a source-sink model where the data is collected from the source sensors and are sent to the chosen sinks.

The main objective is to collect sensor data such as accelerometer or gyroscope and publish it to Home Assistant through MQTT.

## Sources

Currently the following sensors are supported as data sources:

- Accelerometer
- Gyroscope
- Light

## Sinks

The only supported sink as of now is an MQTT broker.
