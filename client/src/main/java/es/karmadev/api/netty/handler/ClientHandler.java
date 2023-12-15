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

import es.karmadev.api.channel.com.security.SecurityProvider;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.channel.subscription.event.connection.PostConnectEvent;
import es.karmadev.api.channel.subscription.event.data.channel.ChannelReceiveEvent;
import es.karmadev.api.channel.subscription.event.data.direct.MessageReceiveEvent;
import es.karmadev.api.netty.Client;
import es.karmadev.api.netty.RemoteServer;
import es.karmadev.api.netty.VirtualChannel;
import es.karmadev.api.netty.message.DecMessage;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import es.karmadev.api.netty.secure.SecureGen;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandler;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private final Client client;
    private final String accessKey;

    private PublicKey serverKey;

    private String serverAlgo;
    private SecretKey serverSecret;

    public ClientHandler(final Client client, final String accessKey) {
        this.client = client;
        this.accessKey = accessKey;
    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelActive()} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     */
    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {

    }

    /**
     * Calls {@link ChannelHandlerContext#fireChannelRead(Object)} to forward
     * to the next {@link ChannelInboundHandler} in the {@link ChannelPipeline}.
     * <p>
     * Sub-classes may override this method to change behavior.
     *
     * @param ctx
     * @param msg
     */
    @Override
    public void channelRead(final ChannelHandlerContext ctx, final Object msg) throws Exception {
        RemoteServer remote = client.getServer();
        assert remote != null; //Otherwise we wouldn't receive this message

        if (msg instanceof BaseMessage) {
            BaseMessage message = (BaseMessage) msg;
            long id = message.getId();

            if (id == Messages.KEY_EXCHANGE.getId()) {
                byte[] key = message.getBytes();
                String algorithm = message.getUTF();

                if (key == null || algorithm == null) return;

                if (serverKey == null) {
                    serverKey = loadKey(key, algorithm);
                    if (serverKey != null) {
                        client.performKeyExchange(serverKey);
                    }
                }
            }

            if (id == Messages.ENCODED.getId()) {
                byte[] encodedData = message.getBytes();
                Long encodedId = message.getInt64();
                if (encodedId == null) return;

                BaseMessage resolved = client.resolve(encodedId, encodedData);
                if (resolved == null) return;

                //TODO: Move this to another class
                if (encodedId == Messages.KEY_EXCHANGE.getId()) {
                    if (serverAlgo != null && serverSecret != null) return;

                    Boolean requiresAccessKey = resolved.getBoolean();
                    if (requiresAccessKey != null && requiresAccessKey) {
                        if (accessKey == null) {
                            throw new SecurityException("Failed to connect to server. Server required an access key but we provided nothing");
                        }

                        MessageBuilder builder = new MessageBuilder();
                        builder.writeInt64(client.getId());
                        builder.write(client.encode(accessKey.getBytes()));
                        BaseMessage keyAuth = builder.build(Messages.ACCESS_KEY);
                        ctx.channel().writeAndFlush(keyAuth); //Write directly to channel, as our client is not ready yet

                        return;
                    }

                    byte[] serverKey = resolved.getBytes();
                    String algorithm = resolved.getUTF();

                    if (algorithm == null) return;

                    serverSecret = new SecretKeySpec(serverKey, algorithm);
                    serverAlgo = algorithm;

                    client.setReady(true);
                    client.processQue((queMessage) -> client.getServer().write(queMessage));

                    //Do discover
                    try {
                        MessageBuilder builder = new MessageBuilder();
                        BaseMessage request = builder.build(Messages.DISCOVER);

                        remote.write(request);
                    } catch (IOException ignored) {}

                    return;
                } else if (encodedId == Messages.DISCOVER.getId()) {
                    String name;
                    while ((name = resolved.getUTF()) != null) {
                        remote.getJoinAbleChannels().add(name);
                    }

                    /*
                    This is where the client is ready to communicate
                    with the server, as now he knows which channels he's
                    able to join at
                     */
                    NetworkEvent event = new PostConnectEvent(remote);
                    client.handle(event);
                } else if (encodedId == Messages.CHANNEL_OPEN.getId()) {
                    String name = resolved.getUTF();
                    if (name == null) return;

                    remote.getAvailableChannels().add(name);
                } else if (encodedId == Messages.CHANNEL_CLOSE.getId()) {
                    String name = resolved.getUTF();
                    if (name == null) return;

                    remote.getAvailableChannels().remove(name);
                    VirtualChannel connectedAt = remote.getChannel(name);
                    if (connectedAt == null) return;

                    remote.getJoinedChannels().remove(connectedAt);
                } else if (encodedId == Messages.CHANNEL_MESSAGE.getId()) {
                    String name = resolved.getUTF();
                    if (name == null) return;

                    VirtualChannel connectedAt = remote.getChannel(name);
                    if (connectedAt == null) return;

                    Long messageId = resolved.getInt64();
                    if (messageId == null) return;

                    byte[] messageData = resolved.getBytes();
                    if (messageData == null) return;

                    DecMessage encoded = new DecMessage(messageId, messageData);
                    ChannelReceiveEvent event = new ChannelReceiveEvent(connectedAt, encoded);
                    connectedAt.handle(event);

                    System.out.println("From channel " + name + ": " + encoded);
                } else if (encodedId == Messages.CHANNEL_JOIN.getId()) {
                    String name = resolved.getUTF();
                    if (name == null) return;

                    VirtualChannel instance = new VirtualChannel(client, name);
                    remote.getJoinedChannels().add(instance);
                }

                NetworkEvent received = new MessageReceiveEvent(resolved);
                client.handle(received);
            }
        }
    }

    private PublicKey loadKey(final byte[] data, final String algorithm) throws NoSuchAlgorithmException,
            InvalidKeySpecException {
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);

        KeyFactory factory = KeyFactory.getInstance(algorithm);
        return factory.generatePublic(spec);
    }

    /**
     * Encode a server message
     *
     * @param message the message to encode
     * @return the encoded message
     * @throws IOException if the encoded message fails to
     * build
     */
    public BaseMessage encode(final BaseMessage message) throws IOException {
        if (message.getId() == Messages.ENCODED.getId()) {
            return message;
        }
        if (serverSecret == null) return message;

        SecurityProvider provider = SecurityProvider.getInstance(serverAlgo);
        byte[] encoded = provider.encodeData(message.readAll(), serverSecret);

        MessageBuilder encodedBuilder = new MessageBuilder();
        encodedBuilder.writeInt64(message.getId()); // Original message id
        encodedBuilder.write(encoded); // Encoded message

        return encodedBuilder.build(Messages.ENCODED);
    }
}
