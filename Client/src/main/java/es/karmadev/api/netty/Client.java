package es.karmadev.api.netty;

import es.karmadev.api.channel.IClient;
import es.karmadev.api.channel.com.remote.IRemoteServer;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.future.ConnectionFuture;
import es.karmadev.api.channel.subscription.AChannelSubscription;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.netty.connection.LocalConnection;
import es.karmadev.api.netty.handler.ClientHandler;
import es.karmadev.api.netty.handler.DataDecoder;
import es.karmadev.api.netty.handler.DataEncoder;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.SocketAddress;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * Represents a netty client
 */
public class Client implements IClient, Writeable {

    private final EventLoopGroup workGroup = new NioEventLoopGroup();
    private final long id = ThreadLocalRandom.current().nextLong();

    private boolean bridgeSupport = false;
    private Channel channel;
    private LocalConnection connection;
    @Getter
    @Setter
    private boolean ready;
    @Getter
    private final List<BaseMessage> messageQue = new ArrayList<>();

    private final Map<Class<? extends NetworkEvent>, List<SubscriptionData>> subscriptions = new HashMap<>();

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
     * Connect the client to a server
     *
     * @param address the server address
     * @param bridge  if the connection supports
     *                bridging
     * @return the connection task
     */
    @Override
    public ConnectionFuture connect(final SocketAddress address, final boolean bridge) {
        ConnectionFuture future = new ConnectionFuture();
        this.bridgeSupport = bridge;

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workGroup);
        bootstrap.channel(NioSocketChannel.class);
        bootstrap.option(ChannelOption.SO_KEEPALIVE, true);

        bootstrap.handler(new ChannelInitializer<SocketChannel>() {
            @Override
            protected void initChannel(SocketChannel ch) {
                ClientHandler handler = new ClientHandler(Client.this);
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
                IRemoteServer server = new RemoteServer(address, channel);
                connection = new LocalConnection(this, server);

                future.setConnection(connection);
            } else {
                future.setError(channelFuture.cause());
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
     * Get the client connection
     *
     * @return the client connection
     */
    @Override
    public LocalConnection getConnection() {
        return connection;
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
     * Add a subscription to the client.
     *
     * @param subscription the subscription to add
     */
    @Override
    public void subscribe(final AChannelSubscription subscription) {
        Class<? extends AChannelSubscription> subClass = subscription.getClass();

        Map<Class<? extends NetworkEvent>, List<MethodHandle>> handleList = new HashMap<>();
        for (Method method : subClass.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers) || Modifier.isStatic(modifiers)) continue;

            Parameter[] parameters = method.getParameters();
            if (parameters.length != 1) continue;

            Parameter parameter = parameters[0];
            Class<?> parameterType = parameter.getType();

            if (!NetworkEvent.class.isAssignableFrom(parameterType)) continue;
            Class<? extends NetworkEvent> eventClass = parameterType.asSubclass(NetworkEvent.class);

            List<MethodHandle> handles = handleList.computeIfAbsent(eventClass, (l) -> new ArrayList<>());
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            try {
                MethodHandle handle = lookup.unreflect(method)
                        .bindTo(subscription);
                handles.add(handle);
            } catch (IllegalAccessException ignored) {}
        }

        for (Class<? extends NetworkEvent> eventClass : handleList.keySet()) {
            List<MethodHandle> handles = handleList.get(eventClass);

            SubscriptionData data = SubscriptionData.of(subscription, handles);

            List<SubscriptionData> dataList = this.subscriptions.computeIfAbsent(eventClass, (l) -> new ArrayList<>());
            if (dataList.contains(data)) {
                continue;
            }

            dataList.add(data);
            this.subscriptions.put(eventClass, dataList);
        }
    }

    /**
     * Remove a subscriptor from the
     * client
     *
     * @param subscription the subscription
     */
    @Override
    public void unsubscribe(final AChannelSubscription subscription) {
        for (Class<? extends NetworkEvent> eventClass : subscriptions.keySet()) {
            List<SubscriptionData> dataList = this.subscriptions.computeIfAbsent(eventClass, (l) -> new ArrayList<>());
            dataList.removeIf(data -> data.getHandler().equals(subscription));
        }
    }

    /**
     * Handle an event for the client
     *
     * @param event the event to handle
     */
    @Override
    public void handle(final NetworkEvent event) {
        List<SubscriptionData> data = this.subscriptions.get(event.getClass());
        if (data == null || data.isEmpty()) return;

        for (SubscriptionData subscription : data) {
            List<MethodHandle> handles = subscription.getInvokers();
            for (MethodHandle handle : handles) {
                try {
                    handle.invokeWithArguments(event);
                } catch (Throwable ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
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
     * Closes this stream and releases any system resources associated
     * with it. If the stream is already closed then invoking this
     * method has no effect.
     *
     * <p> As noted in {@link AutoCloseable#close()}, cases where the
     * close may fail require careful attention. It is strongly advised
     * to relinquish the underlying resources and to internally
     * <em>mark</em> the {@code Closeable} as closed, prior to throwing
     * the {@code IOException}.
     *
     * @throws IOException if an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        try {
            this.channel.close().sync();
        } catch (InterruptedException ex) {
            throw new IOException(ex);
        }
    }
}
