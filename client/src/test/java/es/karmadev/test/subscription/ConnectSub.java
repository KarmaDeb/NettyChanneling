package es.karmadev.test.subscription;

import es.karmadev.api.channel.com.remote.RemoteServer;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.ChannelSubscription;
import es.karmadev.api.channel.subscription.Subscription;
import es.karmadev.api.channel.subscription.event.connection.PostConnectEvent;
import es.karmadev.api.netty.message.MessageBuilder;


public class ConnectSub extends ChannelSubscription {

    @Subscription
    public void onConnect(PostConnectEvent e) {
        RemoteServer server = e.getServer();
        for (String name : server.getAvailableChannels()) {
            System.out.println("Joining: " + name);
            server.joinChannel(name).whenComplete((channel, error) -> {
                if (channel == null) {
                    error.printStackTrace();
                    return;
                }

                System.out.println("Joined " + name);

                MessageBuilder builder = new MessageBuilder();
                builder.writeUTF("Hello world!");

                try {
                    BaseMessage message = builder.build(500);
                    channel.write(message);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            });
        }
    }
}
