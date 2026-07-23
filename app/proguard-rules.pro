# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# JNI: Keep MqttService callback for native code
-keepclassmembers class com.opendevelopment.opensensor.MqttService {
    public void onMqttStatusUpdate(java.lang.String, java.lang.String);
}

# Keep the MqttState enum names for broadcasts and state mapping
-keepclassmembers enum com.opendevelopment.opensensor.MqttService$MqttState {
    *;
}

# Keep data models used in settings and IPC
-keep class com.opendevelopment.opensensor.Settings { *; }
-keep class com.opendevelopment.opensensor.MqttService$ConnectionConfig { *; }
-keep class com.opendevelopment.opensensor.MqttService$TopicConfig { *; }
-keep class com.opendevelopment.opensensor.AccelerometerService$AccelerometerConfig { *; }
-keep class com.opendevelopment.opensensor.GyroscopeService$GyroscopeConfig { *; }
-keep class com.opendevelopment.opensensor.GravityService$GravityConfig { *; }
-keep class com.opendevelopment.opensensor.LightSensorService$LightSensorConfig { *; }
-keep class com.opendevelopment.opensensor.TemperatureSensorService$TemperatureSensorConfig { *; }
