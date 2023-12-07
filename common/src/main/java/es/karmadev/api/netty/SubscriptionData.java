package es.karmadev.api.netty;

import es.karmadev.api.channel.subscription.ChannelSubscription;
import es.karmadev.api.channel.subscription.Subscription;
import lombok.Value;

import java.lang.invoke.MethodHandle;
import java.util.Map;

@Value(staticConstructor = "of")
class SubscriptionData {

    ChannelSubscription handler;
    Map<MethodHandle, Subscription> invokers;
}
