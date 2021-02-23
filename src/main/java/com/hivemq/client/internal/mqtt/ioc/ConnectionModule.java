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

import com.hivemq.client.internal.mqtt.MqttClientTransportConfigImpl;
import com.hivemq.client.internal.mqtt.handler.MqttChannelInitializer;
import com.hivemq.client.internal.mqtt.handler.auth.MqttAuthHandler;
import com.hivemq.client.internal.mqtt.handler.auth.MqttConnectAuthHandler;
import com.hivemq.client.internal.mqtt.handler.auth.MqttDisconnectOnAuthHandler;
import com.hivemq.client.internal.mqtt.message.connect.MqttConnect;
import com.hivemq.client.internal.netty.NettyEventLoopProviderTcp;
import com.hivemq.client.internal.netty.NettyEventLoopProviderUdp;
import com.hivemq.client.mqtt.exceptions.ConnectionFailedException;
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.incubator.codec.quic.*;
import org.jetbrains.annotations.NotNull;

import javax.inject.Named;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * @author Silvio Giebl
 */
@Module
abstract class ConnectionModule {

    @Provides
    static Bootstrap provideTcpBootstrap(final @NotNull MqttChannelInitializer channelInitializer) {
        return new Bootstrap().channelFactory(NettyEventLoopProviderTcp.INSTANCE.getChannelFactory())
                .handler(channelInitializer);
    }

    // FIXME hack, inject this as a singleton at some point. Will also need a different datastructure (need multiple channels per remote at some point)
    @NotNull
    final static Map<String, QuicChannel> quicChannels = new ConcurrentHashMap<>();

    @Provides
    static QuicStreamChannel provideQuicBootstrap(
            final @NotNull MqttChannelInitializer channelInitializer,
            final @NotNull InetSocketAddress remoteAddress,
            final @NotNull EventLoop eventLoop) {
        final QuicChannel quicChannel;
        final QuicChannel quicChannelExisting = quicChannels.get(remoteAddress.toString());
        if (quicChannels.containsKey(remoteAddress.toString()) && quicChannelExisting.isActive() &&
                quicChannelExisting.isOpen()) {
            // TODO temp
            System.out.println("Reusing channel for remote " + remoteAddress.toString());
            quicChannel = quicChannelExisting;
        } else {
            QuicSslContext context =
                    QuicSslContextBuilder.forClient().trustManager(InsecureTrustManagerFactory.INSTANCE).
                            applicationProtocols("MQTT").build();
            System.out.println("Creating a new QUIC channel");
            final ChannelHandler codec = new QuicClientCodecBuilder().maxIdleTimeout(5000, TimeUnit.MILLISECONDS)
                    .initialMaxData(10000000)
                    .maxIdleTimeout(10, TimeUnit.SECONDS)
                    .sslContext(context)
                    // As we don't want to support remote initiated streams just setup the limit for local initiated
                    // streams in this example.
                    .initialMaxStreamsBidirectional(99999999)
                    .initialMaxStreamDataBidirectionalLocal(1000000)
                    .build();
            try {
                final Channel channel;
                channel = new Bootstrap()
                        // TODO executor?
                        .group(NettyEventLoopProviderUdp.INSTANCE.acquireEventLoop(Executors.newFixedThreadPool(8), 8))
                        .channel(NioDatagramChannel.class)
                        .handler(codec)
                        .bind(0)
                        .sync()
                        .channel();
                System.out.println("Bound channel to local address " + channel.localAddress().toString());
                final QuicChannelBootstrap quicBootstrap = QuicChannel.newBootstrap(channel);
                quicChannel = quicBootstrap.streamHandler(new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext ctx) {
                        // As we did not allow any remote initiated streams we will never see this method called.
                        // That said just let us keep it here to demonstrate that this handle would be called
                        // for each remote initiated stream.
                        ctx.close();
                    }
                })
                        .remoteAddress(remoteAddress).streamHandler(channelInitializer).connect().get();
                System.out.println("Opened channel to remote " + remoteAddress.toString());
                quicChannels.put(remoteAddress.toString(), quicChannel);
                    /*TODO not sure if we can reconnect a stream like this
                       .remoteAddress(transportConfig.getRemoteAddress()).connect().addListener(future -> {
                final Throwable cause = future.cause();
                if (cause != null) {
                    final ConnectionFailedException e = new ConnectionFailedException(cause);
                    if (eventLoop.inEventLoop()) {
                        reconnect(clientConfig, MqttDisconnectSource.CLIENT, e, connect, flow, eventLoop);
                    } else {
                        eventLoop.execute(() -> reconnect(clientConfig, MqttDisconnectSource.CLIENT, e, connect, flow,
                                eventLoop));
                    }
                }
            }).get();
            // TODO: What we really want from the connection module is actually quic stream in this case.
            // The connection module should also somehow store the (target address, bootstrap) mapping as a singleton to auto-multiplex when new connections to the same broker are created.
            */
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
                // TODO proper exception
                throw new IllegalStateException("nop");
            }
        }
        try {
            return quicChannel.createStream(QuicStreamType.BIDIRECTIONAL, channelInitializer).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            // TODO handle properly
            throw new IllegalStateException("nop");
        }
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
