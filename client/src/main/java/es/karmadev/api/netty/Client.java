package es.karmadev.api.netty;

/*
 * Copyright 2023 KarmaDev
 *
 * This file is part of NettyChanneling.
 *
 * NettyChanneling is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NettyChanneling is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NettyChanneling. If not, see <http://www.gnu.org/licenses/>.
 */

import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.channel.subscription.event.connection.PreConnectEvent;
import es.karmadev.api.netty.handler.ClientHandler;
import es.karmadev.api.netty.handler.DataDecoder;
import es.karmadev.api.netty.handler.DataEncoder;
import es.karmadev.api.netty.message.DecMessage;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import es.karmadev.api.netty.secure.SecureGen;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.SocketAddress;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;

/**
 * Represents a netty client
 */
@ThreadSafe
public class Client extends SubscriberImpl implements es.karmadev.api.channel.Client, Writeable {

    private final EventLoopGroup workGroup = new NioEventLoopGroup();
    private final long id = ThreadLocalRandom.current().nextLong();
    private final AtomicBoolean connecting = new AtomicBoolean(false);

    private boolean bridgeSupport = false;
    private Channel channel;
    private RemoteServer server;
    @Getter
    @Setter
    private boolean ready;
    @Getter
    private final Queue<BaseMessage> messageQue = new ArrayDeque<>();

    private SecretKey secret;
    private Thread shutdownHook;

    /**
     * Get the client ID
     *
     * @return the client ID
     */
    @Override
    public long getId() {
        return id;
    }

    /**
     * Get the remote server
     *
     * @return the remote server
     */
    @Override
    public @Nullable RemoteServer getServer() {
        return server;
    }

    /**
     * Connect the client to a server
     *
     * @param address the server address
     * @param bridge  if the connection supports
     *                bridging
     * @return the connection task
     */
    @Override
    public CompletableFuture<RemoteServer> connect(final SocketAddress address, final boolean bridge) {
        return connect(address, bridge, null);
    }

    /**
     * Request the client to encode the
     * specified bytes using its secret
     *
     * @param data the data to encode
     * @return the encoded data
     */
    public byte[] encode(final byte[] data) {
        return SecureGen.SECRET_PROVIDER
                .encodeData(data, secret);
    }

    /**
     * Connect the client to a server
     *
     * @param address the server address
     * @param bridge  if the connection supports
     *                bridging
     * @param key the access key
     * @return the connection task
     */
    public CompletableFuture<RemoteServer> connect(final SocketAddress address, final boolean bridge, final String key) {
        if (server != null && channel.isOpen()) throw new RuntimeException("Already connected!");
        if (connecting.get()) throw new RuntimeException("Already trying to connect");
        connecting.set(true);

        CompletableFuture<RemoteServer> future = new CompletableFuture<>();

        Properties properties = new Properties();
        properties.put("bridge", bridge);

        NetworkEvent event = new PreConnectEvent(address, properties);
        handle(event);

        this.bridgeSupport = (boolean) properties.getOrDefault("bridge", bridge);

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

        secret = SecureGen.generateSecret();

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ClientHandler handler = new ClientHandler(Client.this, key);
                DataEncoder encoder = new DataEncoder(((message, channel) -> {
                    try {
                        return handler.encode(message);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }));
                DataDecoder decoder = new DataDecoder();

                ch.pipeline().addLast(
                        encoder,
                        decoder,
                        handler
                );
            }
        });

        bootstrap.connect(address).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                this.channel = channelFuture.channel();
                server = new RemoteServer(address, Client.this, channel);

                if (shutdownHook != null) {
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                }

                shutdownHook = new Thread(() -> {
                    if (this.channel != null) {
                        System.out.println("Waiting for connection to end");

                        try {
                            this.channel.close().sync();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }, "gratefullyClose");
                Runtime.getRuntime().addShutdownHook(shutdownHook);

                future.complete(server);
            } else {
                future.completeExceptionally(channelFuture.cause());
            }
        });

        return future;
    }

    /**
     * Get if the client is connected
     *
     * @return the client connection
     * status
     */
    @Override
    public boolean isConnected() {
        return channel != null && channel.isOpen();
    }

    /**
     * Get if the connection supports bridging.
     * Bridging allows two connections to be
     * directly connected through a virtual channel
     *
     * @return if the connection supports bridging
     */
    @Override
    public boolean supportsBridging() {
        return bridgeSupport;
    }

    /**
     * Push a message directly to the
     * netty object
     *
     * @param message the message
     */
    @Override
    public void push(final BaseMessage message) {
        channel.writeAndFlush(message);
    }

    /**
     * Mark the object as ready to
     * start processing elements
     */
    @Override
    public void markReady() {
        ready = true;
    }

    /**
     * Add a message to the que
     *
     * @param message the message
     */
    @Override
    public void addToQue(final BaseMessage message) {
        messageQue.add(message);
    }

    /**
     * Process the que of messages
     *
     * @param consumer the message consumer
     */
    @Override
    public void processQue(final Consumer<BaseMessage> consumer) {
        messageQue.forEach(consumer);
        messageQue.clear();
    }

    /**
     * Close the connection between
     * the client and the server
     */
    @Override
    public void close() {
        connecting.set(false);

        if (shutdownHook != null)
            Runtime.getRuntime().removeShutdownHook(shutdownHook);

        try {
            this.channel.close().sync();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Perform the connection key exchange.
     * This process is performed automatically
     * when detected, but can also be called
     * when required
     *
     * @param serverKey the server key
     */
    public void performKeyExchange(final PublicKey serverKey) {
        if (secret == null) return;

        byte[] secured = SecureGen.protectKey(secret, serverKey);
        if (secured == null) throw new RuntimeException("Failed to encode our key with the server key");

        MessageBuilder builder = new MessageBuilder();
        builder.writeInt64(id);
        builder.write(secured);
        builder.writeUTF(SecureGen.SECRET_ALGORITHM);

        try {
            BaseMessage message = builder.build(Messages.KEY_EXCHANGE);
            channel.writeAndFlush(message);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Resolve the encoded data
     *
     * @param id the message type id
     * @param encodedData the encoded data
     * @return the resolved message
     */
    public BaseMessage resolve(final long id, final byte[] encodedData) {
        try {
            byte[] resolved = SecureGen.SECRET_PROVIDER
                    .decodeData(encodedData, secret);

            System.out.println("Reading: " + Arrays.toString(resolved));
            return new DecMessage(id, resolved);
        } catch (IOException | DataFormatException ex) {
            throw new RuntimeException(ex);
        }
    }
}
