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

import es.karmadev.api.array.set.ConcurrentWatchdogSet;
import es.karmadev.api.channel.com.Bridge;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.data.direct.MessageEmitEvent;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import io.netty.channel.Channel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Represents a remote server
 */
@AllArgsConstructor
@ThreadSafe
public class RemoteServer implements es.karmadev.api.channel.com.remote.RemoteServer {

    private final SocketAddress address;
    private final Client client;
    private final Channel channel;

    @Getter
    private final ConcurrentWatchdogSet<VirtualChannel> joinedChannels = new ConcurrentWatchdogSet<>();
    @Getter
    private final Set<String> joinAbleChannels = ConcurrentHashMap.newKeySet();

    private final Map<String, Consumer<VirtualChannel>> joinListeners = new ConcurrentHashMap<>();

    {
        joinedChannels.onAdd((channel) -> {
            String name = channel.getName();
            Consumer<VirtualChannel> consumer = joinListeners.remove(name);
            if (consumer == null) return false;

            consumer.accept(channel);
            return true;
        });
    }

    /**
     * Get the server address
     *
     * @return the server address
     */
    @Override
    public SocketAddress getAddress() {
        return address;
    }

    /**
     * Get a list of all the server available
     * channels
     *
     * @return the server available channels
     */
    @Override
    public Collection<String> getAvailableChannels() {
        return Collections.unmodifiableSet(joinAbleChannels);
    }

    /**
     * Get all the channels the client
     * is currently connected to
     *
     * @return the channels the client
     * is connected to
     */
    @Override
    public Collection<VirtualChannel> getChannels() {
        return Collections.unmodifiableSet(joinedChannels);
    }

    /**
     * Get a channel that the current client
     * is connected to
     *
     * @param channel the channel name
     * @return the channel (if we are connected)
     */
    @Override
    public @Nullable VirtualChannel getChannel(final String channel) {
        return joinedChannels.stream().filter((ch) -> ch.getName().equalsIgnoreCase(channel))
                .findAny().orElse(null);
    }

    /**
     * Request access to a server channel
     *
     * @param channel the channel
     * @return the channel request
     */
    @Override
    public CompletableFuture<VirtualChannel> joinChannel(final String channel) {
        if (joinListeners.containsKey(channel)) throw new RuntimeException("Already trying to join channel");

        VirtualChannel joined = getChannel(channel);
        if (joined != null) return CompletableFuture.completedFuture(joined);

        CompletableFuture<VirtualChannel> future = new CompletableFuture<>();
        joinListeners.put(channel, future::complete);

        try {
            MessageBuilder builder = new MessageBuilder();
            builder.writeUTF(channel);

            BaseMessage message = builder.build(Messages.CHANNEL_JOIN);
            this.channel.writeAndFlush(message);
        } catch (IOException ex) {
            return CompletableFuture.completedFuture(null);
        }

        return future;
    }

    /**
     * Leave a channel
     *
     * @param channel the channel to leave
     */
    @Override
    public void leaveChannel(final String channel) {
        if (!client.isReady() || channel == null) {
            return;
        }

        try {
            MessageBuilder builder = new MessageBuilder();
            builder.writeUTF(channel);

            BaseMessage message = builder.build(Messages.CHANNEL_LEAVE);
            this.channel.writeAndFlush(message);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Create a bridge between the
     * current connected client and the
     * destination id
     *
     * @param clientId the client id to
     *                 create a bridge with
     * @return the resulting bridge
     */
    @Override
    public CompletableFuture<Bridge> createBridge(final long clientId) {
        CompletableFuture<Bridge> future = new CompletableFuture<>();
        if (clientId == client.getId()) {
            future.completeExceptionally(new IllegalStateException("Cannot create a bridge with ourselves"));
            return future;
        }

        //TODO: Bridge creation logic
        return future;
    }

    /**
     * Write a message to the
     * server
     *
     * @param message the message to write
     */
    @Override
    public void write(final BaseMessage message) {
        if (client.isReady()) {
            MessageEmitEvent event = new MessageEmitEvent(message);
            client.handle(event);

            if (event.isCancelled()) return;

            channel.writeAndFlush(message);
        } else {
            client.addToQue(message);
        }
    }
}
