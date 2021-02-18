package com.hivemq.client.mqtt;

import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import org.junit.jupiter.api.Test;

/**
 * @author Simon Baier
 */
public class QuicTest {
    @Test
    public void connectLocal() throws Exception {
        final Mqtt3BlockingClient client = MqttClient.builder()
                .transportConfig()
                .transportType(MqttTransportProtocol.QUIC)
                .applyTransportConfig()
                .useMqttVersion3()
                .serverHost("127.0.0.1")
                .serverPort(1884)
                .buildBlocking();
        client.connect();
        System.out.println("Connected");
        client.publish(Mqtt3Publish.builder().topic("test").payload("hello QUIC".getBytes()).build());
    }
}
