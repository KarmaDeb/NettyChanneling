package es.karmadev.api.netty;

import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import io.netty.channel.Channel;
import lombok.Getter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a virtual channel
 * through a netty server
 */
public class VirtualChannel extends SubscriberImpl implements es.karmadev.api.channel.VirtualChannel {

    @Getter
    private final List<Channel> connections = new ArrayList<>();

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
            channelAppender.write(message.readAll());
            BaseMessage channeledMessage = channelAppender
                    .build(Messages.CHANNEL_MESSAGE);

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
