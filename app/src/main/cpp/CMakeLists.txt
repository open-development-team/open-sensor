cmake_minimum_required(VERSION 3.22.1)

include(ExternalProject)
include(ProcessorCount)

ProcessorCount(NPROC)

project("opensensor_native")

set(OPENSSL_SOURCE_DIR ${CMAKE_CURRENT_BINARY_DIR}/openssl-src)
set(OPENSSL_INSTALL_DIR ${CMAKE_CURRENT_BINARY_DIR}/openssl)
set(OPENSSL_INCLUDE_DIR ${OPENSSL_INSTALL_DIR}/include)

if(ANDROID_ABI STREQUAL "armeabi-v7a")
    set(OPENSSL_ARCH android-arm)
elseif(ANDROID_ABI STREQUAL "arm64-v8a")
    set(OPENSSL_ARCH android-arm64)
elseif(ANDROID_ABI STREQUAL "x86")
    set(OPENSSL_ARCH android-x86)
elseif(ANDROID_ABI STREQUAL "x86_64")
    set(OPENSSL_ARCH android-x86_64)
else()
    message(FATAL_ERROR "Unsupported ANDROID_ABI: ${ANDROID_ABI}")
endif()

string(SUBSTRING "${ANDROID_PLATFORM}" 8 -1 ANDROID_API_LEVEL)

ExternalProject_Add(
        OpenSSL
        SOURCE_DIR ${OPENSSL_SOURCE_DIR}
        GIT_REPOSITORY https://github.com/openssl/openssl.git
        GIT_TAG openssl-3.6.0
        GIT_SHALLOW TRUE
        USES_TERMINAL_DOWNLOAD TRUE
        CONFIGURE_COMMAND
            ${CMAKE_COMMAND} -E env
                ANDROID_NDK_ROOT=${CMAKE_ANDROID_NDK}
                PATH=${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin:$ENV{PATH}
            ${OPENSSL_SOURCE_DIR}/config
            ${OPENSSL_ARCH}
            --prefix=${OPENSSL_INSTALL_DIR}
            --openssldir=${OPENSSL_INSTALL_DIR}
            no-shared
            no-docs
            no-apps
            no-tests
        BUILD_COMMAND
            ${CMAKE_COMMAND} -E env
            ANDROID_NDK_ROOT=${CMAKE_ANDROID_NDK}
            PATH=${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin:$ENV{PATH}
            make -j${NPROC}
        TEST_COMMAND ""
        INSTALL_COMMAND
            ${CMAKE_COMMAND} -E env
            ANDROID_NDK_ROOT=${CMAKE_ANDROID_NDK}
            PATH=${CMAKE_ANDROID_NDK}/toolchains/llvm/prebuilt/linux-x86_64/bin:$ENV{PATH}
            make install_sw
        INSTALL_DIR ${OPENSSL_INSTALL_DIR}
        BUILD_BYPRODUCTS
            ${OPENSSL_INSTALL_DIR}/lib/libssl.a
            ${OPENSSL_INSTALL_DIR}/lib/libcrypto.a
)

add_library(OpenSSL::SSL STATIC IMPORTED)
set_property(TARGET OpenSSL::SSL PROPERTY IMPORTED_LOCATION ${OPENSSL_INSTALL_DIR}/lib/libssl.a)
add_dependencies(OpenSSL::SSL OpenSSL)

add_library(OpenSSL::Crypto STATIC IMPORTED)
set_property(TARGET OpenSSL::Crypto PROPERTY IMPORTED_LOCATION ${OPENSSL_INSTALL_DIR}/lib/libcrypto.a)
add_dependencies(OpenSSL::Crypto OpenSSL)

set(PAHO_MQTT_CPP_INSTALL_DIR ${CMAKE_CURRENT_BINARY_DIR}/paho-mqtt-cpp-prefix)

ExternalProject_Add(
        paho-mqtt-cpp
        GIT_REPOSITORY https://github.com/eclipse-paho/paho.mqtt.cpp.git
        GIT_TAG v1.5.3
        GIT_SHALLOW TRUE
        USES_TERMINAL_DOWNLOAD TRUE
        CMAKE_ARGS
            -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
            -DCMAKE_C_FLAGS=${CMAKE_C_FLAGS}
            -DCMAKE_CXX_FLAGS=${CMAKE_CXX_FLAGS}
            -DANDROID_ABI=${ANDROID_ABI}
            -DANDROID_PLATFORM=${ANDROID_PLATFORM}
            -DANDROID_STL=c++_static
            -DPAHO_WITH_MQTT_C:BOOL=TRUE
            -DPAHO_BUILD_STATIC:BOOL=TRUE
            -DPAHO_BUILD_SHARED:BOOL=FALSE
            -DCMAKE_INSTALL_PREFIX:PATH=${PAHO_MQTT_CPP_INSTALL_DIR}
            -DOPENSSL_USE_STATIC_LIBS:BOOL=TRUE
            -DOPENSSL_INCLUDE_DIR:PATH=${OPENSSL_INCLUDE_DIR}
            -DOPENSSL_SSL_LIBRARY:FILEPATH=${OPENSSL_INSTALL_DIR}/lib/libssl.a
            -DOPENSSL_CRYPTO_LIBRARY:FILEPATH=${OPENSSL_INSTALL_DIR}/lib/libcrypto.a
        DEPENDS OpenSSL
        BUILD_BYPRODUCTS
            ${PAHO_MQTT_CPP_INSTALL_DIR}/lib/libpaho-mqttpp3.a
            ${PAHO_MQTT_CPP_INSTALL_DIR}/lib/libpaho-mqtt3as.a
)

add_library(paho-mqttpp3 STATIC IMPORTED)
set_property(TARGET paho-mqttpp3 PROPERTY IMPORTED_LOCATION ${PAHO_MQTT_CPP_INSTALL_DIR}/lib/libpaho-mqttpp3.a)
add_dependencies(paho-mqttpp3 paho-mqtt-cpp)

add_library(paho-mqtt3as STATIC IMPORTED)
set_property(TARGET paho-mqtt3as PROPERTY IMPORTED_LOCATION ${PAHO_MQTT_CPP_INSTALL_DIR}/lib/libpaho-mqtt3as.a)
add_dependencies(paho-mqtt3as paho-mqtt-cpp)

add_library(opensensor_native SHARED
    native-lib.cpp
    mqtt_client_wrapper.cpp
    sensor_processor.cpp
    light_sensor_processor.cpp
    temperature_sensor_processor.cpp
)

target_include_directories(opensensor_native PRIVATE
    ${PAHO_MQTT_CPP_INSTALL_DIR}/include
    ${OPENSSL_INCLUDE_DIR}
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(opensensor_native
    paho-mqttpp3
    paho-mqtt3as
    OpenSSL::SSL
    OpenSSL::Crypto
    ${log-lib}
    ${android-lib}
)
