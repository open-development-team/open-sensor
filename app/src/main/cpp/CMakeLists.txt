cmake_minimum_required(VERSION 3.22.1)

include(ExternalProject)
include(ProcessorCount)

ProcessorCount(NPROC)

project("opensensor_native")

include("openssl.cmake")

add_subdirectory(boost)

add_library(opensensor_native SHARED
    native-lib.cpp
    mqtt_client_wrapper.cpp
    sensor_processor.cpp
    light_sensor_processor.cpp
    temperature_sensor_processor.cpp
)

target_include_directories(opensensor_native PRIVATE
    ${OPENSSL_INCLUDE_DIR}
)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(opensensor_native
    OpenSSL::SSL
    OpenSSL::Crypto
    Boost::mqtt5
    Boost::url
    ${log-lib}
    ${android-lib}
)
