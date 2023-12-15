package es.karmadev.test.handler;

import es.karmadev.api.channel.com.remote.RemoteClient;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.ChannelSubscription;
import es.karmadev.api.channel.subscription.Subscription;
import es.karmadev.api.channel.subscription.event.connection.server.ClientConnectedEvent;
import es.karmadev.api.channel.subscription.event.connection.server.ClientDisconnectedEvent;

public class TestMessageReceive extends ChannelSubscription {

    @Subscription
    public void onConnect(ClientConnectedEvent e) {
        RemoteClient client = e.getClient();
        System.out.println(client.getProperties().getProperty("id") + " connected");
    }

    @Subscription
    public void onDisconnect(ClientDisconnectedEvent e) {
        RemoteClient client = e.getClient();
        System.out.println(client.getProperties().getProperty("id") + " disconnected");
    }
}
