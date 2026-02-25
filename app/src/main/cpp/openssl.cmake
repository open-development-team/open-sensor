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
        UPDATE_DISCONNECTED 1
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