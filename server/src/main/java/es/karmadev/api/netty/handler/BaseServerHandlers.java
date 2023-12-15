package es.karmadev.api.netty.handler;

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

import es.karmadev.api.channel.com.remote.RemoteClient;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.channel.subscription.event.data.server.ClientDiscoverEvent;
import es.karmadev.api.channel.subscription.event.data.server.channel.ClientJoinChannelEvent;
import es.karmadev.api.channel.subscription.event.data.server.channel.ClientLeaveChannelEvent;
import es.karmadev.api.netty.Server;
import es.karmadev.api.netty.VirtualChannel;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import io.netty.channel.Channel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class BaseServerHandlers {

    private final Server server;

    BaseServerHandlers(final Server server) {
        this.server = server;
    }

    public boolean handleEvent(final Messages type, final BaseMessage message, final Channel channel) {
        boolean handled = false;
        RemoteClient client = locateClient(channel);
        if (client == null) return false;

        switch (type) {
            case CHANNEL_JOIN:
                handleChannelJoin(message.clone(), client);
                handled = true;
                break;
            case CHANNEL_LEAVE:
                handleChannelLeave(message.clone(), client);
                handled = true;
                break;
            case DISCOVER:
                handleDiscover(client);
                handled = true;
                break;
            case CHANNEL_MESSAGE:
                handleChannelMessage(client, message.clone());
                handled = true;
                break;
            case DISCONNECTION:
                handleDisconnect(client);
                handled = true;
                break;
        }

        return handled;
    }

    private RemoteClient locateClient(final Channel channel) {
        String id = channel.id().asLongText();
        return server.getConnectedClients().get(id);
    }

    private void handleChannelJoin(final BaseMessage message, final RemoteClient client) {
        String targetChannel = message.getUTF();
        if (targetChannel == null) return;

        VirtualChannel channel = server.getChannel(targetChannel);
        if (channel == null) return;

        if (channel.getConnections().contains(client)) {
            sendChannelJoinSuccess(targetChannel, client); //We won't return silently, instead, we send a "200" response
            return;
        }

        ClientJoinChannelEvent event = new ClientJoinChannelEvent(client, channel, message.clone());
        channel.handle(event);
        if (event.isCancelled()) {
            return;
        }

        channel.getConnections().add(client);
        sendChannelJoinSuccess(targetChannel, client);
    }

    /**
     * Send a channel join success message to
     * a client who requested to join a channel
     *
     * @param targetChannel the target channel
     * @param client the client
     */
    private void sendChannelJoinSuccess(final String targetChannel, final RemoteClient client) {
        MessageBuilder builder = new MessageBuilder();
        builder.writeUTF(targetChannel);

        try {
            BaseMessage response = builder.build(Messages.CHANNEL_JOIN);
            client.write(response);
        } catch (IOException ignored) {}
    }

    private void handleChannelLeave(final  BaseMessage message, final RemoteClient client) {
        String targetChannel = message.getUTF();
        if (targetChannel == null) return;

        VirtualChannel channel = server.getChannel(targetChannel);
        if (channel == null || !channel.getConnections().contains(client)) return;

        ClientLeaveChannelEvent event = new ClientLeaveChannelEvent(client, channel);
        channel.handle(event);

        channel.getConnections().remove(client);
    }

    /**
     * Handle the discover request from a client. Usually
     * each client calls this once in the connection, but
     * the server will anyway check and remove from the list
     * the channels from where the client is already in.
     *
     * @param client the client which wants to discover
     *               the server channels.
     */
    private void handleDiscover(final RemoteClient client) {
        List<String> names = new ArrayList<>();
        for (VirtualChannel channel : server.getChannels()) {
            if (channel.getConnections().contains(client)) continue;
            names.add(channel.getName());
        }


        if (names.isEmpty()) return;

        ClientDiscoverEvent event = new ClientDiscoverEvent(client);
        event.getChannels().addAll(names);

        server.handle(event);
        names.retainAll(event.getChannels());

        if (names.isEmpty()) return;

        MessageBuilder builder = new MessageBuilder();
        for (String name : names) {
            builder.writeUTF(name);
        }

        try {
            BaseMessage response = builder.build(Messages.DISCOVER);
            client.write(response);
        } catch (IOException ignored) {}
    }

    /**
     * Handle client channel message
     *
     * @param client the client that is sending
     *               the message
     * @param message the message that the user wants to send
     *                on the channel
     */
    private void handleChannelMessage(final RemoteClient client, final BaseMessage message) {
        BaseMessage cloned = message.clone();

        System.out.print("Channel message from " + client);
        String channelName = cloned.getUTF();

        System.out.print(" at channel " + channelName);
        if (channelName == null) return;

        VirtualChannel channel = server.getChannel(channelName);
        System.out.print(" which is instantiated as " + channel);
        if (channel == null) return;

        if (!channel.getConnections().contains(client)) return;
        /*
        Prevent clients from sending messages on channels that they
        are not connected to
         */

        System.out.println(" and contains the message sender.");

        Long messageId = cloned.getInt64();
        byte[] realMessage = cloned.getBytes();

        System.out.println("The embedded message id is " + messageId + " and its data is " + Arrays.toString(realMessage));
        if (messageId == null || realMessage == null) return;

        for (RemoteClient rc : channel.getConnections()) {
            //if (rc.equals(client)) continue;
            /*
            Prevent sending the message to ourselves, most clients should
            expect the server to handle this
            */

            rc.write(message);
            /*
            The server simply forwards the message, the client is responsible for handling
            the data
             */
        }
    }

    /**
     * Handle client disconnection
     *
     * @param client the client that is disconnecting
     */
    private void handleDisconnect(final RemoteClient client) {
        Collection<VirtualChannel> channels = server.getChannels();
        for (VirtualChannel vc : channels) {
            vc.getConnections().remove(client); //Remove the client from the channel

            NetworkEvent event = new ClientLeaveChannelEvent(client, vc);
            vc.handle(event);
        }
    }
}
