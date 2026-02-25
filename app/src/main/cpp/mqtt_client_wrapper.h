#ifndef HAANDROIDACCELEROMETER_MQTT_CLIENT_WRAPPER_H
#define HAANDROIDACCELEROMETER_MQTT_CLIENT_WRAPPER_H

#include <boost/asio/ssl.hpp>
#include <boost/asio/io_context.hpp>
#include <boost/mqtt5/logger.hpp>
#include <boost/mqtt5/mqtt_client.hpp>
#include <boost/mqtt5/ssl.hpp>

#include <string>
#include <memory>
#include <thread>
#include <variant>
#include <functional>
#include <fstream>
#include <chrono>
#include <ctime>
#include <iterator>

class MqttClientWrapper {
public:
    explicit MqttClientWrapper(std::string log_file_path);
    ~MqttClientWrapper();

    void connect(const std::string& broker_url, const std::string& client_id, const std::string& username, const std::string& password);
    void disconnect();
    bool publish(const std::string& topic, const std::string& payload);

private:
    struct custom_logger {
        std::string log_file_path_;
        // Using size-based limit to avoid reading the file for line counts.
        // Limit to 40KB, and when trimming, reduce to 20KB.
        const size_t MAX_LOG_SIZE = 40000;
        const size_t TRIM_TO_SIZE = 20000;

        explicit custom_logger(std::string log_file_path) : log_file_path_(std::move(log_file_path)) {
        }

        void log(const std::string& message) const {
            if (log_file_path_.empty()) return;

            std::ofstream log_file(log_file_path_, std::ios_base::app);
            if (!log_file.is_open()) return;

            auto now = std::chrono::system_clock::now();
            auto in_time_t = std::chrono::system_clock::to_time_t(now);

            char time_buf[80];
            std::strftime(time_buf, sizeof(time_buf), "%F %T", std::localtime(&in_time_t));

            log_file << time_buf << " | " << message << std::endl;

            if (log_file.tellp() > MAX_LOG_SIZE) {
                log_file.close();
                // File exceeds max size, need to trim it.
                std::ifstream infile(log_file_path_);
                if (!infile.is_open()) return;

                // Seek to a point to keep the last TRIM_TO_SIZE bytes
                infile.seekg(0, std::ios::end);
                size_t current_size = infile.tellg();
                if (current_size > TRIM_TO_SIZE) {
                    infile.seekg(current_size - TRIM_TO_SIZE);
                     // Discard the first partial line to not corrupt log messages
                    std::string dummy;
                    std::getline(infile, dummy);
                } else {
                    infile.seekg(0);
                }

                std::string content((std::istreambuf_iterator<char>(infile)),
                                     std::istreambuf_iterator<char>());
                infile.close();

                // Write the trimmed content back to the file
                std::ofstream outfile(log_file_path_, std::ios_base::trunc);
                outfile << content;
            }
        }


        void at_resolve(boost::system::error_code ec, std::string_view host, std::string_view port, const boost::asio::ip::tcp::resolver::results_type& eps) {
            log("resolve: " + std::string(host) + ":" + std::string(port) + " - " + ec.message());
        }

        void at_tcp_connect(boost::system::error_code ec, boost::asio::ip::tcp::endpoint ep) {
            log("TCP connect: " + ep.address().to_string() + ":" + std::to_string(ep.port()) + " - " + ec.message());
        }

        void at_tls_handshake(boost::system::error_code ec, boost::asio::ip::tcp::endpoint ep) {
            log("TLS handshake: " + ep.address().to_string() + ":" + std::to_string(ep.port()) + " - " + ec.message());
        }

        void at_ws_handshake(boost::system::error_code ec, boost::asio::ip::tcp::endpoint ep) {
            log("WebSocket handshake: " + ep.address().to_string() + ":" + std::to_string(ep.port()) + " - " + ec.message());
        }

        void at_connack(boost::mqtt5::reason_code rc, bool session_present, const boost::mqtt5::connack_props& props) const {
            std::string msg = "connack: " + std::string(rc.message());
            msg += ", session_present: " + std::to_string(session_present);
            log(msg);
        }

        void at_disconnect(boost::mqtt5::reason_code rc, const boost::mqtt5::disconnect_props& props) const {
            log("disconnect: " + std::string(rc.message()));
        }

        void at_transport_error(boost::system::error_code ec) {
            log("transport layer error: " + ec.message());
        }
    };

    using mqtt_client_t = boost::mqtt5::mqtt_client<
        boost::asio::ip::tcp::socket,
        std::monostate /* TlsContext */,
        custom_logger
    >;
    using mqtts_client_t = boost::mqtt5::mqtt_client<
            boost::asio::ssl::stream<boost::asio::ip::tcp::socket>,
            boost::asio::ssl::context,
            custom_logger
    >;

    custom_logger logger_;
    boost::asio::io_context ioc_;
    std::variant<std::monostate, mqtt_client_t, mqtts_client_t> client_;
    std::thread ioc_thread_;
};

#endif //HAANDROIDACCELEROMETER_MQTT_CLIENT_WRAPPER_H
