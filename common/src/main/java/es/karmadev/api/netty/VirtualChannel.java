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

import es.karmadev.api.channel.com.remote.RemoteClient;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import lombok.Getter;

import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.util.*;

/**
 * Represents a virtual channel
 * through a netty server
 */
@ThreadSafe
public class VirtualChannel extends SubscriberImpl implements es.karmadev.api.channel.VirtualChannel {

    @Getter
    private final Queue<RemoteClient> connections = new ArrayDeque<>();

    private final Writeable writeable;
    private final String name;

    public VirtualChannel(final Writeable writeable, final String name) {
        this.writeable = writeable;
        this.name = name;
    }

    /**
     * Get the channel name
     *
     * @return the channel name
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Write a message to the clients
     * connected on the channel
     *
     * @param message the channel to send
     */
    @Override
    public void write(final BaseMessage message) {
        try {
            MessageBuilder channelAppender = new MessageBuilder();
            channelAppender.writeUTF(name);
            channelAppender.writeInt64(message.getId());
            channelAppender.write(message.readAll());
            BaseMessage channeledMessage = channelAppender
                    .build(Messages.CHANNEL_MESSAGE);

            System.out.println("Writing: " + Arrays.toString(message.readAll()));

            if (writeable.isReady()) {
                writeable.push(channeledMessage);
            } else {
                writeable.addToQue(channeledMessage);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}
