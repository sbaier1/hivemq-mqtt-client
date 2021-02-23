package com.hivemq.client.mqtt;

import com.google.common.collect.Lists;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * @author Simon Baier
 */
public class QuicTest {

    @Test
    public void connectLocal() throws Exception {
        final List<MqttClient> clientList = Lists.newArrayList();
        for (int i = 0; i < 99; i++) {
            final @NotNull Mqtt5BlockingClient client = MqttClient.builder()
                    .transportConfig()
                    .transportType(MqttTransportProtocol.QUIC)
                    .applyTransportConfig()
                    .useMqttVersion5()
                    .serverHost("10.2.7.247")
                    .serverPort(1883)
                    .buildBlocking();
            client.connect();
            client.publish(Mqtt5Publish.builder().topic("test").payload(("hello QUIC"+i).getBytes()).build());
            clientList.add(client);
        }
        System.out.println("Connected");
    }
}
