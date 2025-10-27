#include <jni.h>
#include <string>
#include "mqtt_client_wrapper.h"
#include "sensor_processor.h"
#include "light_sensor_processor.h"
#include <android/log.h>

#define LOG_TAG "NativeLib"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

static MqttClientWrapper* mqttClientWrapper = nullptr;
static SensorProcessor* accelerometerProcessor = nullptr;
static SensorProcessor* gyroscopeProcessor = nullptr;
static LightSensorProcessor* lightSensorProcessor = nullptr;
static JavaVM* javaVM = nullptr;

// JNI_OnLoad is called when the library is loaded
jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    javaVM = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_MqttService_nativeInit(
    JNIEnv* env,
    jobject /* this */,
    jobject callback_obj,
    jstring accelerometerTopic,
    jstring gyroscopeTopic,
    jstring lightSensorTopic) {
    if (mqttClientWrapper == nullptr) {
        const char* accelerometerTopicCStr = env->GetStringUTFChars(accelerometerTopic, nullptr);
        const char* gyroscopeTopicCStr = env->GetStringUTFChars(gyroscopeTopic, nullptr);
        const char* lightSensorTopicCStr = env->GetStringUTFChars(lightSensorTopic, nullptr);

        mqttClientWrapper = new MqttClientWrapper(javaVM, callback_obj);
        accelerometerProcessor = new SensorProcessor(mqttClientWrapper, accelerometerTopicCStr);
        gyroscopeProcessor = new SensorProcessor(mqttClientWrapper, gyroscopeTopicCStr);
        lightSensorProcessor = new LightSensorProcessor(mqttClientWrapper, lightSensorTopicCStr);

        env->ReleaseStringUTFChars(accelerometerTopic, accelerometerTopicCStr);
        env->ReleaseStringUTFChars(gyroscopeTopic, gyroscopeTopicCStr);
        env->ReleaseStringUTFChars(lightSensorTopic, lightSensorTopicCStr);

        LOGD("MqttClientWrapper and SensorProcessors initialized.");
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_opendevelopment_opensensor_MqttService_nativeConnect(JNIEnv* env, jobject /* this */, jstring brokerUrl, jstring clientId, jstring username, jstring password) {
    if (mqttClientWrapper != nullptr) {
        const char* brokerUrlCStr = env->GetStringUTFChars(brokerUrl, nullptr);
        const char* clientIdCStr = env->GetStringUTFChars(clientId, nullptr);
        const char* usernameCStr = env->GetStringUTFChars(username, nullptr);
        const char* passwordCStr = env->GetStringUTFChars(password, nullptr);

        mqttClientWrapper->connect(brokerUrlCStr, clientIdCStr, usernameCStr, passwordCStr);

        env->ReleaseStringUTFChars(brokerUrl, brokerUrlCStr);
        env->ReleaseStringUTFChars(clientId, clientIdCStr);
        env->ReleaseStringUTFChars(username, usernameCStr);
        env->ReleaseStringUTFChars(password, passwordCStr);
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
        delete accelerometerProcessor;
        accelerometerProcessor = nullptr;
        delete gyroscopeProcessor;
        gyroscopeProcessor = nullptr;
        delete lightSensorProcessor;
        lightSensorProcessor = nullptr;
        delete mqttClientWrapper;
        mqttClientWrapper = nullptr;
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
