#include <jni.h>
#include <string>
#include "mqtt_client_wrapper.h"
#include "sensor_processor.h"
#include "light_sensor_processor.h"
#include "temperature_sensor_processor.h"
#include <android/log.h>

#define LOG_TAG "NativeLib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static MqttClientWrapper* mqttClientWrapper = nullptr;
static SensorProcessor* accelerometerProcessor = nullptr;
static SensorProcessor* gyroscopeProcessor = nullptr;
static SensorProcessor* gravityProcessor = nullptr;
static LightSensorProcessor* lightSensorProcessor = nullptr;
static TemperatureSensorProcessor* temperatureSensorProcessor = nullptr;

static JavaVM* g_jvm = nullptr;
static jobject g_callback_obj = nullptr;

jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    return JNI_VERSION_1_6;
}

void notifyStatusUpdate(const std::string& status, const std::string& reason) {
    if (g_jvm == nullptr || g_callback_obj == nullptr) return;

    JNIEnv* env;
    bool attached = false;
    jint res = g_jvm->GetEnv((void**)&env, JNI_VERSION_1_6);
    if (res == JNI_EDETACHED) {
        if (g_jvm->AttachCurrentThread(&env, nullptr) != 0) return;
        attached = true;
    } else if (res != JNI_OK) {
        return;
    }

    jclass cls = env->GetObjectClass(g_callback_obj);
    jmethodID method = env->GetMethodID(cls, "onMqttStatusUpdate", "(Ljava/lang/String;Ljava/lang/String;)V");
    if (method != nullptr) {
        jstring statusStr = env->NewStringUTF(status.c_str());
        jstring reasonStr = env->NewStringUTF(reason.c_str());
        env->CallVoidMethod(g_callback_obj, method, statusStr, reasonStr);
        env->DeleteLocalRef(statusStr);
        env->DeleteLocalRef(reasonStr);
    }

    if (attached) {
        g_jvm->DetachCurrentThread();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_MqttService_nativeInit(
    JNIEnv* env,
    jobject /* this */,
    jobject callback_obj,
    jstring logFilePath,
    jstring accelerometerTopic,
    jstring gyroscopeTopic,
    jstring gravityTopic,
    jstring lightSensorTopic,
    jstring temperatureSensorTopic) {
    if (mqttClientWrapper == nullptr) {
        g_callback_obj = env->NewGlobalRef(callback_obj);

        const char* logFilePathCStr = env->GetStringUTFChars(logFilePath, nullptr);
        const char* accelerometerTopicCStr = env->GetStringUTFChars(accelerometerTopic, nullptr);
        const char* gyroscopeTopicCStr = env->GetStringUTFChars(gyroscopeTopic, nullptr);
        const char* gravityTopicCStr = env->GetStringUTFChars(gravityTopic, nullptr);
        const char* lightSensorTopicCStr = env->GetStringUTFChars(lightSensorTopic, nullptr);
        const char* temperatureSensorTopicCStr = env->GetStringUTFChars(temperatureSensorTopic, nullptr);

        mqttClientWrapper = new MqttClientWrapper(logFilePathCStr);
        mqttClientWrapper->set_status_callback([](const std::string& status, const std::string& reason) {
            notifyStatusUpdate(status, reason);
        });

        accelerometerProcessor = new SensorProcessor(mqttClientWrapper, accelerometerTopicCStr);
        gyroscopeProcessor = new SensorProcessor(mqttClientWrapper, gyroscopeTopicCStr);
        gravityProcessor = new SensorProcessor(mqttClientWrapper, gravityTopicCStr);
        lightSensorProcessor = new LightSensorProcessor(mqttClientWrapper, lightSensorTopicCStr);
        temperatureSensorProcessor = new TemperatureSensorProcessor(mqttClientWrapper, temperatureSensorTopicCStr);

        env->ReleaseStringUTFChars(logFilePath, logFilePathCStr);
        env->ReleaseStringUTFChars(accelerometerTopic, accelerometerTopicCStr);
        env->ReleaseStringUTFChars(gyroscopeTopic, gyroscopeTopicCStr);
        env->ReleaseStringUTFChars(gravityTopic, gravityTopicCStr);
        env->ReleaseStringUTFChars(lightSensorTopic, lightSensorTopicCStr);
        env->ReleaseStringUTFChars(temperatureSensorTopic, temperatureSensorTopicCStr);

        LOGD("MqttClientWrapper and SensorProcessors initialized.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_MqttService_nativeConnect(JNIEnv* env, jobject /* this */, jstring brokerUrl, jstring clientId, jstring username, jstring password, jstring willTopic, jstring willPayload) {
    if (mqttClientWrapper != nullptr) {
        const char* brokerUrlCStr = env->GetStringUTFChars(brokerUrl, nullptr);
        const char* clientIdCStr = env->GetStringUTFChars(clientId, nullptr);
        const char* usernameCStr = env->GetStringUTFChars(username, nullptr);
        const char* passwordCStr = env->GetStringUTFChars(password, nullptr);
        const char* willTopicCStr = env->GetStringUTFChars(willTopic, nullptr);
        const char* willPayloadCStr = env->GetStringUTFChars(willPayload, nullptr);

        mqttClientWrapper->connect(brokerUrlCStr, clientIdCStr, usernameCStr, passwordCStr, willTopicCStr, willPayloadCStr);

        env->ReleaseStringUTFChars(brokerUrl, brokerUrlCStr);
        env->ReleaseStringUTFChars(clientId, clientIdCStr);
        env->ReleaseStringUTFChars(username, usernameCStr);
        env->ReleaseStringUTFChars(password, passwordCStr);
        env->ReleaseStringUTFChars(willTopic, willTopicCStr);
        env->ReleaseStringUTFChars(willPayload, willPayloadCStr);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_MqttService_nativeDisconnect(JNIEnv* env, jobject /* this */) {
    if (mqttClientWrapper != nullptr) {
        mqttClientWrapper->disconnect();
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_MqttService_nativeCleanup(JNIEnv* env, jobject /* this */) {
    if (mqttClientWrapper != nullptr) {
        LOGD("Cleaning up native resources.");

        if (g_callback_obj != nullptr) {
            env->DeleteGlobalRef(g_callback_obj);
            g_callback_obj = nullptr;
        }

        delete accelerometerProcessor;
        accelerometerProcessor = nullptr;
        delete gyroscopeProcessor;
        gyroscopeProcessor = nullptr;
        delete gravityProcessor;
        gravityProcessor = nullptr;
        delete lightSensorProcessor;
        lightSensorProcessor = nullptr;
        delete temperatureSensorProcessor;
        temperatureSensorProcessor = nullptr;

        delete mqttClientWrapper;
        mqttClientWrapper = nullptr;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_MqttService_nativePublish(JNIEnv* env, jobject /* this */, jstring topic, jstring payload, jboolean retain) {
    if (mqttClientWrapper != nullptr) {
        const char* topicCStr = env->GetStringUTFChars(topic, nullptr);
        const char* payloadCStr = env->GetStringUTFChars(payload, nullptr);

        mqttClientWrapper->publish(topicCStr, payloadCStr, retain);

        env->ReleaseStringUTFChars(topic, topicCStr);
        env->ReleaseStringUTFChars(payload, payloadCStr);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_AccelerometerService_nativeUpdateSettings(
        JNIEnv* env, jobject /* this */, jfloat multiplierX, jfloat multiplierY, jfloat multiplierZ, jint rounding) {
    if (accelerometerProcessor != nullptr) {
        accelerometerProcessor->updateSettings(multiplierX, multiplierY, multiplierZ, rounding);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_AccelerometerService_nativeProcessData(
        JNIEnv* env, jobject /* this */, jfloat x, jfloat y, jfloat z) {
    if (accelerometerProcessor != nullptr) {
        accelerometerProcessor->processData(x, y, z);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_GyroscopeService_nativeUpdateGyroscopeSettings(
        JNIEnv* env, jobject /* this */, jfloat multiplierX, jfloat multiplierY, jfloat multiplierZ, jint rounding) {
    if (gyroscopeProcessor != nullptr) {
        gyroscopeProcessor->updateSettings(multiplierX, multiplierY, multiplierZ, rounding);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_GyroscopeService_nativeProcessGyroscopeData(
        JNIEnv* env, jobject /* this */, jfloat x, jfloat y, jfloat z) {
    if (gyroscopeProcessor != nullptr) {
        gyroscopeProcessor->processData(x, y, z);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_GravityService_nativeUpdateGravitySettings(
        JNIEnv* env, jobject /* this */, jfloat multiplierX, jfloat multiplierY, jfloat multiplierZ, jint rounding) {
    if (gravityProcessor != nullptr) {
        gravityProcessor->updateSettings(multiplierX, multiplierY, multiplierZ, rounding);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_GravityService_nativeProcessGravityData(
        JNIEnv* env, jobject /* this */, jfloat x, jfloat y, jfloat z) {
    if (gravityProcessor != nullptr) {
        gravityProcessor->processData(x, y, z);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_LightSensorService_nativeUpdateLightSensorSettings(
        JNIEnv* env, jobject /* this */, jint rounding) {
    if (lightSensorProcessor != nullptr) {
        lightSensorProcessor->updateSettings(rounding);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_LightSensorService_nativeProcessLightSensorData(
        JNIEnv* env, jobject /* this */, jfloat value) {
    if (lightSensorProcessor != nullptr) {
        lightSensorProcessor->processData(value);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_TemperatureSensorService_nativeUpdateTemperatureSensorSettings(
        JNIEnv* env, jobject /* this */, jint rounding) {
    if (temperatureSensorProcessor != nullptr) {
        temperatureSensorProcessor->updateSettings(rounding);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_TemperatureSensorService_nativeProcessTemperatureSensorData(
        JNIEnv* env, jobject /* this */, jfloat value) {
    if (temperatureSensorProcessor != nullptr) {
        temperatureSensorProcessor->processData(value);
    }
}
