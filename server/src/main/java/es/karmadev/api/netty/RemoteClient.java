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
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.Properties;

/**
 * Represents a remote client
 */
@RequiredArgsConstructor
public class RemoteClient implements es.karmadev.api.channel.com.remote.RemoteClient {

    private final long id;
    private final Server server;
    private final Channel channel;
    private final Properties properties = new Properties();

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
     * Get the client address
     *
     * @return the client address
     */
    @Override
    public SocketAddress getAddress() {
        if (!channel.isOpen()) return null;
        return channel.remoteAddress();
    }

    /**
     * Write a message directly
     * to the client
     *
     * @param message the message to write
     */
    @Override
    public void write(final BaseMessage message) {
        if (!channel.isOpen()) return;
        channel.writeAndFlush(message);
    }

    /**
     * Disconnect the client from the
     * server, with the specified reason
     *
     * @param reason the disconnect reason
     */
    @Override
    public void disconnect(final String reason) {
        server.getConnectedClients().remove(this);

        try {
            MessageBuilder builder = new MessageBuilder();
            builder.writeUTF(reason);

            BaseMessage message = builder.build(Messages.DISCONNECTION);
            channel.writeAndFlush(message).addListener((ChannelFutureListener) channelFuture ->
                    channel.close());

            return;
        } catch (IOException ignored) {}

        channel.close();
    }

    /**
     * Get the remote client properties. Those
     * properties are only known by the server,
     * and are used by the server to store special
     * information that is not API-declared about
     * the client. For instance, those properties
     * could be used to store the remote client name
     * if implemented
     *
     * @return the client properties
     */
    @Override
    public Properties getProperties() {
        return properties;
    }
}
