/*
 * Copyright 2018-present HiveMQ and the HiveMQ Community
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hivemq.client.internal.mqtt.ioc;

import com.hivemq.client.internal.mqtt.handler.MqttChannelInitializer;
import com.hivemq.client.internal.mqtt.handler.auth.MqttAuthHandler;
import com.hivemq.client.internal.mqtt.handler.auth.MqttConnectAuthHandler;
import com.hivemq.client.internal.mqtt.handler.auth.MqttDisconnectOnAuthHandler;
import com.hivemq.client.internal.mqtt.message.connect.MqttConnect;
import com.hivemq.client.internal.netty.NettyEventLoopProviderUdp;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;
import org.jetbrains.annotations.NotNull;

import javax.inject.Named;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Silvio Giebl
 */
@Module
abstract class ConnectionModule {

    @Provides
    static QuicChannelBootstrap provideTcpBootstrap(final @NotNull MqttChannelInitializer channelInitializer) {

        return udpInit(channelInitializer);
        /*
        // TODO screwed up the TCP bootstrap here for now
        return new Bootstrap().channelFactory(NettyEventLoopProviderTcp.INSTANCE.getChannelFactory())
                .handler(channelInitializer);*/
    }

    @Provides
    @Named("BLA")
    static QuicChannelBootstrap provideUdpBootstrap(final @NotNull MqttChannelInitializer channelInitializer) {
        return udpInit(channelInitializer);
    }

    private static QuicChannelBootstrap udpInit(@NotNull MqttChannelInitializer channelInitializer) {
        QuicSslContext context = QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).
                applicationProtocols("MQTT").build();

        final ChannelHandler codec = new QuicClientCodecBuilder().maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                .initialMaxData(10000000)
                .maxIdleTimeout(10, TimeUnit.SECONDS)
                .sslContext(context)
                // As we don't want to support remote initiated streams just setup the limit for local initiated
                // streams in this example.
                .initialMaxStreamDataBidirectionalLocal(1000000)
                .build();
        final Channel channel;
        try {
            channel = new Bootstrap().group(
                    NettyEventLoopProviderUdp.INSTANCE.acquireEventLoop(Executors.newFixedThreadPool(8), 8))
                    .channel(NioDatagramChannel.class)
                    .handler(codec)
                    .bind(0)
                    .sync()
                    .channel();
            return QuicChannel.newBootstrap(channel);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // TODO spax
        return null;
    }

    @Provides
    @ConnectionScope
    static @NotNull MqttAuthHandler provideAuthHandler(
            final @NotNull MqttConnect connect,
            final @NotNull Lazy<MqttConnectAuthHandler> connectAuthHandlerLazy,
            final @NotNull Lazy<MqttDisconnectOnAuthHandler> disconnectOnAuthHandlerLazy) {

        return (connect.getRawEnhancedAuthMechanism() == null) ? disconnectOnAuthHandlerLazy.get() :
                connectAuthHandlerLazy.get();
    }
}
