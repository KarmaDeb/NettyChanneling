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

import es.karmadev.api.channel.subscription.ChannelSubscription;
import es.karmadev.api.channel.subscription.Subscription;
import es.karmadev.api.channel.subscription.Subscriptor;
import es.karmadev.api.channel.subscription.event.Cancellable;
import es.karmadev.api.channel.subscription.event.NetworkEvent;

import javax.annotation.concurrent.ThreadSafe;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation for subscriptor
 */
@ThreadSafe
public class SubscriberImpl implements Subscriptor {

    private final Map<Class<? extends NetworkEvent>, List<SubscriptionData>> subscriptions = new ConcurrentHashMap<>();

    /**
     * Add a subscription
     *
     * @param subscription the subscription to add
     */
    @Override
    public void subscribe(final ChannelSubscription subscription) {
        Class<? extends ChannelSubscription> subClass = subscription.getClass();

        Map<Class<? extends NetworkEvent>, Map<MethodHandle, Subscription>> handleList = new HashMap<>();
        for (Method method : subClass.getDeclaredMethods()) {
            int modifiers = method.getModifiers();
            if (!Modifier.isPublic(modifiers) || Modifier.isAbstract(modifiers) || Modifier.isStatic(modifiers)) continue;

            if (!method.isAnnotationPresent(Subscription.class)) return;

            Subscription sub = method.getAnnotation(Subscription.class);
            Parameter[] parameters = method.getParameters();
            if (parameters.length != 1) continue;

            Parameter parameter = parameters[0];
            Class<?> parameterType = parameter.getType();

            if (!NetworkEvent.class.isAssignableFrom(parameterType)) continue;
            Class<? extends NetworkEvent> eventClass = parameterType.asSubclass(NetworkEvent.class);

            Map<MethodHandle, Subscription> handles = handleList.computeIfAbsent(eventClass, (l) -> new HashMap<>());
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            try {
                MethodHandle handle = lookup.unreflect(method)
                        .bindTo(subscription);
                handles.put(handle, sub);
            } catch (IllegalAccessException ignored) {}
        }

        for (Class<? extends NetworkEvent> eventClass : handleList.keySet()) {
            Map<MethodHandle, Subscription> handles = handleList.get(eventClass);

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
    public void unsubscribe(final ChannelSubscription subscription) {
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
            Map<MethodHandle, Subscription> handles = sorted(
                    subscription.getInvokers()
            );

            for (MethodHandle handle : handles.keySet()) {
                Subscription sub = handles.get(handle);
                if (event instanceof Cancellable) {
                    Cancellable cancellable = (Cancellable) event;
                    if (cancellable.isCancelled() && sub.ignoreCancelled()) continue;
                }

                try {
                    handle.invokeWithArguments(event);
                } catch (Throwable ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    private Map<MethodHandle, Subscription> sorted(final Map<MethodHandle, Subscription> map) {
        List<Map.Entry<MethodHandle, Subscription>> list = new ArrayList<>(map.entrySet());
        list.sort(Map.Entry.comparingByValue(Comparator.comparingInt(Subscription::priority).reversed()));

        Map<MethodHandle, Subscription> result = new LinkedHashMap<>();
        for (Map.Entry<MethodHandle, Subscription> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }
}