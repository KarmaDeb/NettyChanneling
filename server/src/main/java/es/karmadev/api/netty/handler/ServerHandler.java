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
import es.karmadev.api.channel.subscription.event.connection.server.ClientDisconnectedEvent;
import es.karmadev.api.channel.subscription.event.connection.server.ClientPreConnectEvent;
import es.karmadev.api.netty.Server;
import es.karmadev.api.netty.message.nat.Messages;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import javax.annotation.concurrent.ThreadSafe;

@ThreadSafe
public class ServerHandler extends ChannelInboundHandlerAdapter {

    private final Server server;
    private final BaseServerHandlers handler;

    public ServerHandler(final Server server) {
        this.server = server;
        this.handler = new BaseServerHandlers(server);
    }

    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        Channel channel = ctx.channel();

        if (msg instanceof BaseMessage) {
            BaseMessage message = (BaseMessage) msg;
            long id = message.getId();

            Messages type = Messages.getById(id);
            if (type == null) return;

            if (type.equals(Messages.KEY_EXCHANGE)) {
                CryptoHelper.performKeyExchange(message, server, channel);
            }

            if (server.getAccessKey() != null && type.equals(Messages.ACCESS_KEY)) {
                CryptoHelper.validateAccessKey(message, channel, server);
            }

            if (type.equals(Messages.ENCODED)) {
                CryptoHelper.handleEncoded(message, server, channel, handler);
            }

            return;
        }

        super.channelRead(ctx, msg);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        NetworkEvent event = new ClientPreConnectEvent(ctx.channel().remoteAddress());
        server.handle(event);

        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        Channel channel = ctx.channel();
        RemoteClient rm = server.getConnectedClients().get(channel.id().asLongText());
        if (rm == null) {
            super.channelInactive(ctx);
            return;
        }; //Unsafe communication

        NetworkEvent event = new ClientDisconnectedEvent(rm);
        server.handle(event);

        server.getConnectedClients().remove(channel.id().asLongText());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}
