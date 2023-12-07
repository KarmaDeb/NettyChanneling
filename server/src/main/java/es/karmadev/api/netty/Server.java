package es.karmadev.api.netty;

import es.karmadev.api.channel.com.Bridge;
import es.karmadev.api.channel.com.remote.RemoteClient;
import es.karmadev.api.channel.com.security.SecurityProvider;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.data.server.MessageBroadcastEvent;
import es.karmadev.api.netty.handler.DataDecoder;
import es.karmadev.api.netty.handler.DataEncoder;
import es.karmadev.api.netty.handler.ServerHandler;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import es.karmadev.api.netty.secure.SecureGen;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.Getter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.net.*;
import java.security.KeyPair;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Represents the netty server
 */
@SuppressWarnings("unused")
public class Server extends SubscriberImpl implements es.karmadev.api.channel.Server, Writeable {

    private final EventLoopGroup bossGroup = new NioEventLoopGroup();
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    @Getter
    private final Map<String, RemoteClient> connectedClients = new HashMap<>();
    private final List<VirtualChannel> channels = new ArrayList<>();

    private final SocketAddress address;

    private ServerChannel server;

    @Getter
    private final KeyPair pair;
    @Getter
    private final SecretKey secret;

    private final byte[] encoded;

    private final Map<String, SecretKey> keyMap = new HashMap<>();
    private final Map<String, String> keyAlgoMap = new HashMap<>();

    public Server() throws SocketException {
        this(findAddress(4653));
    }

    public Server(final String host) {
        this(new InetSocketAddress(host, 4653));
    }

    public Server(final int port) throws SocketException {
        this(findAddress(port));
    }

    public Server(final String host, final int port) {
        this(new InetSocketAddress(host, port));
    }

    public Server(final SocketAddress address) {
        this.address = address;

        this.pair = SecureGen.generateKeyPair();
        this.secret = SecureGen.generateSecret();

        byte[] encoded = null;
        if (this.pair != null && this.secret != null) {
            encoded = SecureGen.protectKey(secret, pair.getPublic());
        }

        this.encoded = encoded;
    }


    private static InetSocketAddress findAddress(final int port) throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

        InetSocketAddress socket = null;
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            if (networkInterface.isLoopback() || networkInterface.isVirtual()) continue;

            List<InterfaceAddress> addresses = networkInterface.getInterfaceAddresses();
            if (addresses.isEmpty()) continue;

            for (InterfaceAddress address : addresses) {
                if (address == null) continue;

                InetAddress mainAddress = address.getAddress();
                InetAddress broadcast = address.getBroadcast();

                if (mainAddress == null || broadcast == null) continue;
                if (mainAddress.equals(broadcast)) continue;

                String host = mainAddress.getHostAddress();
                if (host == null) continue;

                socket = new InetSocketAddress(host, port);
                break;
            }
        }

        return socket;
    }

    /**
     * Get the server ID
     *
     * @return the server ID
     */
    @Override
    public long getId() {
        return -1;
    }

    @Override
    public SocketAddress getAddress() {
        return address;
    }

    /**
     * Get the server connected clients
     *
     * @return the server connected
     * clients
     */
    @Override
    public Collection<? extends RemoteClient> getClients() {
        return Collections.unmodifiableCollection(connectedClients.values());
    }

    @Override
    public Collection<VirtualChannel> getChannels() {
        return Collections.unmodifiableList(channels);
    }

    /**
     * Get all the server bridges
     *
     * @return the server created
     * bridges
     */
    @Override
    public Collection<? extends Bridge> getBridges() {
        return null;
    }

    @Override
    public VirtualChannel createChannel(final String name) {
        VirtualChannel channel = new VirtualChannel(this, name);
        channels.add(channel);

        return channel;
    }

    /**
     * Write a message to all the clients. Unlike the
     * method {@link VirtualChannel#write(BaseMessage)} this
     * method sends the message to all the clients directly
     *
     * @param message the message to send
     */
    @Override
    public void broadcast(final BaseMessage message) {
        MessageBroadcastEvent event = new MessageBroadcastEvent(message);
        handle(event);

        if (event.isCancelled()) return;
        server.writeAndFlush(message);
    }

    @Override
    public boolean isRunning() {
        return server != null && server.isOpen();
    }

    @Override
    public CompletableFuture<Boolean> start() {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        if (server != null && server.isOpen()) {
            return CompletableFuture.completedFuture(true);
        }

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        DataEncoder encoder = new DataEncoder((message, channel) -> {
                            String channelId = channel.id().asLongText();
                            if (keyMap.containsKey(channelId)) {
                                SecretKey key = keyMap.get(channelId);
                                String algorithm = keyAlgoMap.get(channelId);

                                SecurityProvider provider = SecurityProvider.getInstance(algorithm);
                                byte[] encoded = provider.encodeData(message.readAll(), key);

                                MessageBuilder encodedBuilder = new MessageBuilder();
                                encodedBuilder.writeInt64(message.getId());
                                encodedBuilder.write(encoded);
                                encodedBuilder.writeUTF(SecureGen.SECRET_ALGORITHM);

                                try {
                                    return encodedBuilder.build(Messages.ENCODED);
                                } catch (IOException ex) {
                                    throw new RuntimeException(ex);
                                }
                            }

                            return message;
                        });
                        DataDecoder decoder = new DataDecoder();
                        ServerHandler handler = new ServerHandler(Server.this);

                        ch.pipeline().addLast(encoder, decoder, handler);

                        if (encoded != null) {
                            assert pair != null;

                            try {
                                MessageBuilder builder = new MessageBuilder();
                                builder.write(pair.getPublic().getEncoded());
                                builder.writeUTF(SecureGen.PAIR_ALGORITHM);

                                BaseMessage exchange = builder.build(Messages.KEY_EXCHANGE);
                                ch.writeAndFlush(exchange);
                            } catch (IOException ex) {
                                throw new RuntimeException(ex);
                            }
                        }
                    }
                })
                .option(ChannelOption.SO_BACKLOG, 128)
                .option(ChannelOption.SO_KEEPALIVE, true);

        bootstrap.bind(address).addListener((ChannelFutureListener) channelFuture -> {
            if (channelFuture.isSuccess()) {
                this.server = (ServerChannel) channelFuture.channel();
                future.complete(this.server.isOpen());
            } else {
                future.completeExceptionally(channelFuture.cause());
            }
        });

        return future;
    }

    public void stop() {
        if (server == null || !server.isOpen()) return;

        workerGroup.shutdownGracefully();
        bossGroup.shutdownGracefully();
        server.close();
    }

    public void mapKey(final Channel channel, final SecretKey key, final String algorithm) {
        this.keyMap.put(channel.id().asLongText(), key);
        this.keyAlgoMap.put(channel.id().asLongText(), algorithm);
    }

    /**
     * Push a message directly to the
     * netty object
     *
     * @param message the message
     */
    @Override
    public void push(final BaseMessage message) {
        broadcast(message);
    }

    /**
     * Get if the writeable object
     * is ready to process elements
     *
     * @return if the object
     * is ready
     */
    @Override
    public boolean isReady() {
        return true;
    }

    /**
     * Mark the object as ready to
     * start processing elements
     */
    @Override
    public void markReady() {

    }

    /**
     * Add a message to the que
     *
     * @param message the message
     */
    @Override
    public void addToQue(final BaseMessage message) {
        broadcast(message);
    }

    /**
     * Process the que of messages
     *
     * @param consumer the message consumer
     */
    @Override
    public void processQue(Consumer<BaseMessage> consumer) {}

    /**
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     */
    @Override
    public void close() {
        stop();
    }
}
