#include "mqtt_client_wrapper.h"
#include <android/log.h>

#include <boost/asio/dispatch.hpp>
#include <boost/asio/executor_work_guard.hpp>
#include <boost/url/url_view.hpp>

#define LOG_TAG "MqttClientWrapper"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)

// External customization point.
namespace boost::mqtt5 {

// Specify that the TLS handshake will be performed as a client.
template<typename StreamBase>
struct tls_handshake_type<boost::asio::ssl::stream<StreamBase>> {
    static constexpr auto client = boost::asio::ssl::stream_base::client;
};

// This client uses this function to indicate which hostname it is
// attempting to connect to at the start of the handshaking process.
template<typename StreamBase>
void assign_tls_sni(
        const authority_path &ap,
        boost::asio::ssl::context & /* ctx */,
        boost::asio::ssl::stream<StreamBase> &stream
) {
    SSL_set_tlsext_host_name(stream.native_handle(), ap.host.c_str());
}

}

MqttClientWrapper::MqttClientWrapper(std::string log_file_path) : logger_{*this, std::move(log_file_path)} {
    ioc_thread_ = std::thread([this]() {
        LOGD("Starting io_context thread.");
        auto work_guard = boost::asio::make_work_guard(ioc_);
        ioc_.run();
        LOGD("io_context thread finished.");
    });
}

MqttClientWrapper::~MqttClientWrapper() {
    if (ioc_thread_.joinable()) {
        disconnect();
        ioc_.stop();
        ioc_thread_.join();
    }
}

void MqttClientWrapper::connect(const std::string& broker_url, const std::string& client_id, const std::string& username, const std::string& password, const std::string& will_topic, const std::string& will_payload) {
    boost::asio::dispatch(ioc_, [this, broker_url, client_id, username, password, will_topic, will_payload] {
        logger_.log("Trying to connect...");

        will_topic_ = will_topic;
        will_payload_ = will_payload;

        boost::urls::url_view u(broker_url);

        try {
            if (u.scheme() == "tls" || u.scheme() == "mqtts") {
                auto& client = client_.emplace<mqtts_client_t>(
                        ioc_,
                        boost::asio::ssl::context(boost::asio::ssl::context::tls_client),
                        logger_);

                if (!will_topic.empty()) {
                    client.will(boost::mqtt5::will(will_topic, will_payload, boost::mqtt5::qos_e::at_most_once, boost::mqtt5::retain_e::yes));
                }

                client.brokers(u.host(), u.port_number())
                        .credentials(client_id, username, password)
                        .connect_property(boost::mqtt5::prop::session_expiry_interval, 60)
                        .keep_alive(30)
                        .async_run(boost::asio::detached);
            } else {
                auto& client = client_.emplace<mqtt_client_t>(
                        ioc_,
                        std::monostate{},
                        logger_);

                if (!will_topic.empty()) {
                    client.will(boost::mqtt5::will(will_topic, will_payload, boost::mqtt5::qos_e::at_most_once, boost::mqtt5::retain_e::yes));
                }

                client.brokers(u.host(), u.port_number())
                        .credentials(client_id, username, password)
                        .connect_property(boost::mqtt5::prop::session_expiry_interval, 60)
                        .keep_alive(30)
                        .async_run(boost::asio::detached);
            }
        } catch (const std::exception& e) {
            LOGE("MQTT connection failed: %s", e.what());
            logger_.log("MQTT connection failed: " + std::string(e.what()));
            client_.emplace<std::monostate>();
        }
    });
}

void MqttClientWrapper::disconnect() {
    boost::asio::dispatch(ioc_, [this]() {
        if (!std::holds_alternative<std::monostate>(client_)) {
            try {
                std::visit([this](auto&& cli) {
                    using T = std::decay_t<decltype(cli)>;
                    if constexpr (!std::is_same_v<T, std::monostate>) {
                        if (!will_topic_.empty()) {
                            logger_.log("Publishing offline status before disconnect...");
                            cli.template async_publish<boost::mqtt5::qos_e::at_most_once>(
                                    will_topic_, will_payload_,
                                    boost::mqtt5::retain_e::yes, boost::mqtt5::publish_props {},
                                    [this](boost::mqtt5::error_code ec) {
                                        if (ec) logger_.log("Graceful offline publish failed: " + ec.message());
                                    });
                        }

                        logger_.log("Disconnecting from broker...");
                        cli.async_disconnect([this](boost::mqtt5::error_code ec) {
                            if (!ec) logger_.log("Disconnected from broker.");
                            else logger_.log("Disconnecting from broker failed: " + ec.message());
                        });
                    }
                }, client_);
            } catch (const std::exception& e) {
                logger_.log(std::string{"Disconnecting from broker failed: "} + e.what());
            }
        }
    });
}

bool MqttClientWrapper::publish(const std::string& topic, const std::string& payload, bool retain) {
    boost::asio::dispatch(ioc_, [this, topic, payload, retain] {
        if (!std::holds_alternative<std::monostate>(client_)) {
            try {
                std::visit([&](auto&& cli) {
                    using T = std::decay_t<decltype(cli)>;
                    if constexpr (!std::is_same_v<T, std::monostate>) {
                        cli.template async_publish<boost::mqtt5::qos_e::at_most_once>(
                                topic,
                                payload,
                                retain ? boost::mqtt5::retain_e::yes : boost::mqtt5::retain_e::no,
                                boost::mqtt5::publish_props {},
                                [this](boost::mqtt5::error_code ec) {
                                    if (ec) logger_.log(ec.message());
                                });
                    }
                }, client_);
            } catch (const std::exception& e) {
                LOGE("MQTT publish error: %s", e.what());
            }
        } else {
            LOGW("MQTT publish called but client is not connected.");
        }
    });

    return true; 
}
