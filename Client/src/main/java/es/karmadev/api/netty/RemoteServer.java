package es.karmadev.api.netty;

import es.karmadev.api.channel.com.remote.IRemoteServer;
import es.karmadev.api.channel.future.Future;
import io.netty.channel.Channel;
import lombok.AllArgsConstructor;

import java.net.SocketAddress;

/**
 * Represents a remote
 * server
 */
@AllArgsConstructor
public class RemoteServer implements IRemoteServer {

    private final SocketAddress address;
    private final Channel channel;

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
     * Request access to a server channel
     *
     * @param channelId the channel id
     * @return the channel request
     */
    @Override
    public Future joinChannel(final long channelId) {
        return null;
    }

    /**
     * Leave a channel
     *
     * @param channelId the channel id to leave
     */
    @Override
    public void leaveChannel(final long channelId) {

    }
}
