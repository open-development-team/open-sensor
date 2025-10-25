#ifndef HAANDROIDACCELEROMETER_MQTT_CLIENT_WRAPPER_H
#define HAANDROIDACCELEROMETER_MQTT_CLIENT_WRAPPER_H

#include <string>
#include <functional>
#include <memory>
#include <jni.h>

#include <mqtt/async_client.h>

class MqttClientWrapper {
public:
    MqttClientWrapper(JavaVM* vm, jobject callback_obj);
    ~MqttClientWrapper();

    void connect(const std::string& broker_url, const std::string& client_id, const std::string& username, const std::string& password);
    void disconnect();
    bool publish(const std::string& topic, const std::string& payload);

private:
    void sendStatusUpdate(const std::string& status);

    std::unique_ptr<mqtt::async_client> client_;
    mqtt::connect_options connOpts_;

    JavaVM* javaVM_;
    jobject jniCallbackObj_;
    jclass callbackClass_;
    jmethodID statusUpdateMethodId_;

    class ActionCallback : public virtual mqtt::iaction_listener {
        MqttClientWrapper& wrapper_;
    public:
        ActionCallback(MqttClientWrapper& wrapper) : wrapper_(wrapper) {}
        void on_failure(const mqtt::token& tok) override;
        void on_success(const mqtt::token& tok) override;
    };
    ActionCallback actionCallback_;

    class MqttCallback : public virtual mqtt::callback {
        MqttClientWrapper& wrapper_;
    public:
        MqttCallback(MqttClientWrapper& wrapper) : wrapper_(wrapper) {}
        void connection_lost(const std::string& cause) override;
        void delivery_complete(mqtt::delivery_token_ptr tok) override;
        void message_arrived(mqtt::const_message_ptr msg) override;
    };
    MqttCallback mqttCallback_;
};

#endif //HAANDROIDACCELEROMETER_MQTT_CLIENT_WRAPPER_H