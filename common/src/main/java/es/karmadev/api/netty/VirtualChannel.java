package es.karmadev.api.netty;

import es.karmadev.api.channel.IChannel;
import es.karmadev.api.channel.IServer;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.future.Future;
import es.karmadev.api.channel.subscription.AChannelSubscription;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.core.ExceptionCollector;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import io.netty.channel.Channel;
import lombok.Getter;
import lombok.Value;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a virtual channel
 * through a netty server
 */
public class VirtualChannel implements IChannel {

    private final static SecureRandom random = new SecureRandom();

    private final long id = random.nextLong();
    private final Map<Class<? extends NetworkEvent>, List<SubscriptionData>> subscriptions = new HashMap<>();
    @Getter
    private final List<Channel> connections = new ArrayList<>();

    private boolean published = false;

    private final Writeable writeable;
    private String name;

    public VirtualChannel(final Writeable writeable, final String name) {
        this.writeable = writeable;
        this.name = name;
    }

    /**
     * Get the channel ID
     *
     * @return the channel ID
     */
    @Override
    public long getId() {
        return id;
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
     * Publish the channel on
     * the server
     *
     * @return if the channel could be
     * published
     */
    @Override
    public boolean publish() {
        if (published || !writeable.isReady()) return false;

        MessageBuilder builder = new MessageBuilder();
        builder.writeUTF(name);
        try {
            writeable.push(builder.build(Messages.CHANNEL_OPEN));
            published = true;

            return true;
        } catch (IOException ex) {
            ExceptionCollector.catchException(VirtualChannel.class, ex);
            return false;
        }
    }

    /**
     * Get if the channel has been
     * published
     *
     * @return if the channel has been
     * published
     */
    @Override
    public boolean isPublished() {
        return published;
    }

    /**
     * Creates a bridge between the current
     * client and the desired client
     *
     * @param to the client destination
     * @return the bridge
     */
    @Override
    public Future createBridge(final long to) {
        return null;
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
            channelAppender.writeInt64(id);
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

    /**
     * Add a subscription
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
     * Remove a subscription
     *
     * @param subscription the subscription to remove
     */
    @Override
    public void unsubscribe(final AChannelSubscription subscription) {
        for (Class<? extends NetworkEvent> eventClass : subscriptions.keySet()) {
            List<SubscriptionData> dataList = this.subscriptions.computeIfAbsent(eventClass, (l) -> new ArrayList<>());
            dataList.removeIf(data -> data.getHandler().equals(subscription));
        }
    }

    /**
     * Handle an event
     *
     * @param event the event
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
}

@Value(staticConstructor = "of")
class SubscriptionData {

    AChannelSubscription handler;
    List<MethodHandle> invokers;
}
