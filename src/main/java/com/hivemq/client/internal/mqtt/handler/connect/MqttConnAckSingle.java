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

package com.hivemq.client.internal.mqtt.handler.connect;

import com.hivemq.client.internal.logging.InternalLogger;
import com.hivemq.client.internal.logging.InternalLoggerFactory;
import com.hivemq.client.internal.mqtt.MqttClientConfig;
import com.hivemq.client.internal.mqtt.MqttClientTransportConfigImpl;
import com.hivemq.client.internal.mqtt.exceptions.MqttClientStateExceptions;
import com.hivemq.client.internal.mqtt.handler.MqttChannelInitializer;
import com.hivemq.client.internal.mqtt.ioc.ConnectionComponent;
import com.hivemq.client.internal.mqtt.lifecycle.MqttClientDisconnectedContextImpl;
import com.hivemq.client.internal.mqtt.lifecycle.MqttClientReconnector;
import com.hivemq.client.internal.mqtt.message.connect.MqttConnect;
import com.hivemq.client.internal.util.InetSocketAddressUtil;
import com.hivemq.client.mqtt.MqttTransportProtocol;
import com.hivemq.client.mqtt.exceptions.ConnectionFailedException;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.lifecycle.MqttDisconnectSource;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.EventLoop;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicChannelBootstrap;
import io.netty.incubator.codec.quic.QuicClientCodecBuilder;
import io.netty.incubator.codec.quic.QuicStreamType;
import io.reactivex.Single;
import io.reactivex.SingleObserver;
import io.reactivex.internal.disposables.EmptyDisposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.InetSocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.hivemq.client.mqtt.MqttClientState.*;

/**
 * @author Silvio Giebl
 */
public class MqttConnAckSingle extends Single<Mqtt5ConnAck> {

    private static final @NotNull InternalLogger LOGGER = InternalLoggerFactory.getLogger(MqttConnAckSingle.class);

    private final @NotNull MqttClientConfig clientConfig;
    private final @NotNull MqttConnect connect;

    public MqttConnAckSingle(final @NotNull MqttClientConfig clientConfig,
            final @NotNull MqttConnect connect) {
        this.clientConfig = clientConfig;
        this.connect = connect.setDefaults(clientConfig);
    }

    @Override
    protected void subscribeActual(final @NotNull SingleObserver<? super Mqtt5ConnAck> observer) {
        if (!clientConfig.getRawState().compareAndSet(DISCONNECTED, CONNECTING)) {
            EmptyDisposable.error(MqttClientStateExceptions.alreadyConnected(), observer);
            return;
        }

        final MqttConnAckFlow flow = new MqttConnAckFlow(observer);
        observer.onSubscribe(flow.getDisposable());
        connect(clientConfig, connect, flow, clientConfig.acquireEventLoop());
    }

    private static void connect(
            final @NotNull MqttClientConfig clientConfig,
            final @NotNull MqttConnect connect,
            final @NotNull MqttConnAckFlow flow,
            final @NotNull EventLoop eventLoop) {
        if (flow.getDisposable().isDisposed()) {
            clientConfig.releaseEventLoop();
            clientConfig.getRawState().set(DISCONNECTED);
        } else {
            final QuicChannelBootstrap bootstrap;
            if (MqttTransportProtocol.TCP.equals(clientConfig.getTransportConfig().getTransportProtocol())) {
                bootstrap = clientConfig.getClientComponent()
                        .connectionComponentBuilder()
                        .connect(connect)
                        .connAckFlow(flow)
                        .build()
                        .tcpBootstrap();
            } else {
                final ConnectionComponent component = clientConfig.getClientComponent()
                        .connectionComponentBuilder()
                        .connect(connect)
                        .connAckFlow(flow)
                        .build();
                bootstrap = component
                        .udpBootstrap();
                final MqttChannelInitializer initializer = component.initializer();
                final MqttClientTransportConfigImpl transportConfig = clientConfig.getCurrentTransportConfig();

                try {
                    final QuicChannel channel = bootstrap.streamHandler(new ChannelInboundHandlerAdapter() {
                        @Override
                        public void channelActive(ChannelHandlerContext ctx) {
                            // As we did not allow any remote initiated streams we will never see this method called.
                            // That said just let us keep it here to demonstrate that this handle would be called
                            // for each remote initiated stream.
                            System.out.println("nope");
                            ctx.close();
                        }
                    }).remoteAddress(transportConfig.getRemoteAddress()).connect().addListener(future -> {
                        final Throwable cause = future.cause();
                        if (cause != null) {
                            System.out.println("Test " + cause);
                            final ConnectionFailedException e = new ConnectionFailedException(cause);
                            if (eventLoop.inEventLoop()) {
                                reconnect(clientConfig, MqttDisconnectSource.CLIENT, e, connect, flow, eventLoop);
                            } else {
                                eventLoop.execute(
                                        () -> reconnect(clientConfig, MqttDisconnectSource.CLIENT, e, connect, flow,
                                                eventLoop));
                            }
                        }
                    }).get();
                    // TODO need the initializer here
                    channel.createStream(QuicStreamType.BIDIRECTIONAL, initializer);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void reconnect(
            final @NotNull MqttClientConfig clientConfig,
            final @NotNull MqttDisconnectSource source,
            final @NotNull Throwable cause,
            final @NotNull MqttConnect connect,
            final @NotNull MqttConnAckFlow flow,
            final @NotNull EventLoop eventLoop) {

        if (flow.setDone()) {
            reconnect(clientConfig, source, cause, connect, flow.getAttempts() + 1, flow, eventLoop);
        }
    }

    public static void reconnect(
            final @NotNull MqttClientConfig clientConfig,
            final @NotNull MqttDisconnectSource source,
            final @NotNull Throwable cause,
            final @NotNull MqttConnect connect,
            final @NotNull EventLoop eventLoop) {

        reconnect(clientConfig, source, cause, connect, 0, null, eventLoop);
    }

    private static void reconnect(
            final @NotNull MqttClientConfig clientConfig,
            final @NotNull MqttDisconnectSource source,
            final @NotNull Throwable cause,
            final @NotNull MqttConnect connect,
            final int attempts,
            final @Nullable MqttConnAckFlow flow,
            final @NotNull EventLoop eventLoop) {

        final MqttClientReconnector reconnector =
                new MqttClientReconnector(eventLoop, attempts, connect, clientConfig.getCurrentTransportConfig());
        final MqttClientDisconnectedContext context =
                MqttClientDisconnectedContextImpl.of(clientConfig, source, cause, reconnector);

        for (final MqttClientDisconnectedListener disconnectedListener : clientConfig.getDisconnectedListeners()) {
            try {
                disconnectedListener.onDisconnected(context);
            } catch (final Throwable t) {
                LOGGER.error("Unexpected exception thrown by disconnected listener.", t);
            }
        }

        if (reconnector.isReconnect()) {
            clientConfig.getRawState().set(DISCONNECTED_RECONNECT);
            eventLoop.schedule(() -> {
                reconnector.getFuture().whenComplete((ignored, throwable) -> {
                    if (reconnector.isReconnect()) {
                        if (clientConfig.getRawState().compareAndSet(DISCONNECTED_RECONNECT, CONNECTING_RECONNECT)) {

                            clientConfig.setCurrentTransportConfig(reconnector.getTransportConfig());
                            connect(clientConfig, reconnector.getConnect(), new MqttConnAckFlow(flow), eventLoop);
                        }

                    } else if (clientConfig.getRawState().compareAndSet(DISCONNECTED_RECONNECT, DISCONNECTED)) {
                        clientConfig.releaseEventLoop();
                        if (flow != null) {
                            if (throwable == null) {
                                flow.onError(new ConnectionFailedException("Reconnect was cancelled."));
                            } else {
                                flow.onError(new ConnectionFailedException(throwable));
                            }
                        }
                    }
                });
            }, reconnector.getDelay(TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS);
            clientConfig.setResubscribeIfSessionExpired(reconnector.isResubscribeIfSessionExpired());
            clientConfig.setRepublishIfSessionExpired(reconnector.isRepublishIfSessionExpired());
            reconnector.afterOnDisconnected();
        } else {
            clientConfig.getRawState().set(DISCONNECTED);
            clientConfig.releaseEventLoop();
            if (flow != null) {
                flow.onError(cause);
            }
        }
    }
}
