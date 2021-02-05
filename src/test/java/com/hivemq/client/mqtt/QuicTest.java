package com.hivemq.client.mqtt;

import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import org.junit.jupiter.api.Test;

/**
 * @author Simon Baier
 */
public class QuicTest {
    @Test
    public void connectLocal() {
        final Mqtt5BlockingClient client = MqttClient.builder()
                .transportConfig()
                .transportType(MqttTransportProtocol.QUIC)
                .applyTransportConfig()
                .useMqttVersion5()
                .serverHost("localhost")
                .serverPort(1884)
                .buildBlocking();
        client.connect();
    }
}
