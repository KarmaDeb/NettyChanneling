package es.karmadev.api.netty;

import es.karmadev.api.channel.VirtualChannel;
import es.karmadev.api.channel.com.Bridge;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.channel.subscription.event.data.direct.MessageEmitEvent;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import io.netty.channel.Channel;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.Nullable;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.net.SocketAddress;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Represents a remote
 * server. Please note the remote server
 * is not guaranteed to be thread safe. The
 * channel iterations should be made on a single
 * thread
 */
@AllArgsConstructor
@NotThreadSafe
public class RemoteServer implements es.karmadev.api.channel.com.remote.RemoteServer {

    private final SocketAddress address;
    private final Client client;
    private final Channel channel;

    private final Set<VirtualChannel> channels = new HashSet<>();

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
     * Get all the channels the client
     * is currently connected to
     *
     * @return the channels the client
     * is connected to
     */
    @Override
    public Collection<? extends VirtualChannel> getChannels() {
        return Collections.unmodifiableSet(channels);
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
        return channels.stream().filter((ch) -> ch.getName().equalsIgnoreCase(channel))
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
        VirtualChannel joined = getChannel(channel);
        if (joined != null) return CompletableFuture.completedFuture(joined);

        //TODO: Channel join logic
        return null;
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
