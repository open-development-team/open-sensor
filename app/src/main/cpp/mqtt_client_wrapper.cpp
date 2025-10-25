#include "mqtt_client_wrapper.h"
#include <android/log.h>

#define LOG_TAG "MqttClientWrapper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

MqttClientWrapper::MqttClientWrapper(JavaVM* vm, jobject callback_obj)
    : actionCallback_(*this),
      mqttCallback_(*this),
      javaVM_(vm) {

    JNIEnv* env;
    javaVM_->GetEnv((void**)&env, JNI_VERSION_1_6);
    jniCallbackObj_ = env->NewGlobalRef(callback_obj);
    callbackClass_ = (jclass)env->NewGlobalRef(env->GetObjectClass(jniCallbackObj_));
    statusUpdateMethodId_ = env->GetMethodID(callbackClass_, "onMqttStatusUpdate", "(Ljava/lang/String;)V");
}

MqttClientWrapper::~MqttClientWrapper() {
    if (client_ && client_->is_connected()) {
        try {
            LOGD("Disconnecting from broker in destructor (sync).");
            client_->disconnect()->wait();
        } catch (const mqtt::exception& exc) {
            LOGE("MQTT disconnection error in destructor: %s", exc.what());
        }
    }
    if (jniCallbackObj_) {
        JNIEnv* env;
        javaVM_->GetEnv((void**)&env, JNI_VERSION_1_6);
        env->DeleteGlobalRef(jniCallbackObj_);
        env->DeleteGlobalRef(callbackClass_);
    }
}

void MqttClientWrapper::connect(const std::string& broker_url, const std::string& client_id, const std::string& username, const std::string& password) {
    client_ = std::make_unique<mqtt::async_client>(broker_url, client_id);
    client_->set_callback(mqttCallback_);

    connOpts_.set_keep_alive_interval(20);
    connOpts_.set_clean_session(true); 
    connOpts_.set_automatic_reconnect(true);
    connOpts_.set_user_name(username);
    connOpts_.set_password(password);

    try {
        LOGD("Connecting to broker...");
        sendStatusUpdate("CONNECTING");
        client_->connect(connOpts_, nullptr, actionCallback_);
    } catch (const mqtt::exception& exc) {
        LOGE("MQTT connection error: %s", exc.what());
        sendStatusUpdate("ERROR");
    }
}

void MqttClientWrapper::disconnect() {
    try {
        if (client_ && client_->is_connected()) {
            LOGD("Disconnecting from broker.");
            client_->disconnect(0, nullptr, actionCallback_);
        }
    } catch (const mqtt::exception& exc) {
        LOGE("MQTT disconnection error: %s", exc.what());
        sendStatusUpdate("ERROR");
    }
}

bool MqttClientWrapper::publish(const std::string& topic, const std::string& payload) {
    if (client_ && client_->is_connected()) {
        try {
            client_->publish(topic, payload.c_str(), payload.length(), 0, true);
            return true;
        } catch (const mqtt::exception& exc) {
            LOGE("MQTT publish error: %s", exc.what());
        }
    }

    return false;
}

void MqttClientWrapper::sendStatusUpdate(const std::string& status) {
    JNIEnv* env;
    int getEnvStat = javaVM_->GetEnv((void**)&env, JNI_VERSION_1_6);

    bool mustDetach = false;
    if (getEnvStat == JNI_EDETACHED) {
        if (javaVM_->AttachCurrentThread(&env, NULL) != 0) {
            LOGE("Failed to attach current thread to JVM");
            return;
        }
        mustDetach = true;
    }

    jstring jStatus = env->NewStringUTF(status.c_str());
    env->CallVoidMethod(jniCallbackObj_, statusUpdateMethodId_, jStatus);
    env->DeleteLocalRef(jStatus);

    if (mustDetach) {
        javaVM_->DetachCurrentThread();
    }
}

void MqttClientWrapper::ActionCallback::on_failure(const mqtt::token& tok) {
    LOGE("MQTT Action failure.");
    wrapper_.sendStatusUpdate("ERROR");
}

void MqttClientWrapper::ActionCallback::on_success(const mqtt::token& tok) {
    LOGD("MQTT Action success.");
    if (tok.get_type() == mqtt::token::Type::CONNECT) {
        wrapper_.sendStatusUpdate("CONNECTED");
    } else if (tok.get_type() == mqtt::token::Type::DISCONNECT) {
        wrapper_.sendStatusUpdate("DISCONNECTED");
    }
}

void MqttClientWrapper::MqttCallback::connection_lost(const std::string& cause) {
    LOGW("MQTT connection lost: %s", cause.c_str());
    wrapper_.sendStatusUpdate("DISCONNECTED");
}

void MqttClientWrapper::MqttCallback::delivery_complete(mqtt::delivery_token_ptr tok) {
    LOGD("MQTT delivery complete for token: %d", tok->get_message_id());
}

void MqttClientWrapper::MqttCallback::message_arrived(mqtt::const_message_ptr msg) {
    LOGD("Message arrived [topic: %s, payload: %s]", msg->get_topic().c_str(), msg->to_string().c_str());
}
