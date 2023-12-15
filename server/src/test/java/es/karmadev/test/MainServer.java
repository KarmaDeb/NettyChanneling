package es.karmadev.test;

import es.karmadev.api.netty.Server;
import es.karmadev.test.handler.TestMessageReceive;

public class MainServer {

    public static void main(String[] args) throws Throwable {
        Server server = new Server("192.168.0.3", 1025);
        server.setKey("TestKey");
        server.subscribe(new TestMessageReceive());

        server.createChannel("test");

        server.start().whenComplete((success, error) -> {
            if (success) {
                System.out.println("Successfully started server!");
            } else {
                error.printStackTrace();
            }
        });
    }
}
