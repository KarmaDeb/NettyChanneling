package es.karmadev.test;

import es.karmadev.api.netty.Client;
import es.karmadev.test.subscription.ConnectSub;

import java.net.InetSocketAddress;

public class MainClient {

    public static void main(String[] args) {
        Client client = new Client();
        client.subscribe(new ConnectSub());

        InetSocketAddress address = new InetSocketAddress("192.168.0.3", 1025);

        client.connect(address, true, "TestKey").whenComplete((server, error) -> {
            if (server != null) {

            } else {
                error.printStackTrace();
            }
        });
    }
}
