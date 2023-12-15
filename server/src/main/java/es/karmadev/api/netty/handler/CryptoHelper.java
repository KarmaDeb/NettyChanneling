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
import es.karmadev.api.channel.com.security.SecurityProvider;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.channel.subscription.event.connection.server.ClientConnectedEvent;
import es.karmadev.api.channel.subscription.event.data.server.direct.DirectMessageEvent;
import es.karmadev.api.netty.Server;
import es.karmadev.api.netty.message.DecMessage;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import es.karmadev.api.netty.secure.SecureGen;
import io.netty.channel.Channel;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DataFormatException;

/**
 * Crypto helper
 */
class CryptoHelper {

    private final static Map<String, SecretKey> clientKeys = new ConcurrentHashMap<>();
    private final static Map<String, String> clientKeyAlgorithms = new ConcurrentHashMap<>();
    private final static Map<String, Long> connectionIds = new ConcurrentHashMap<>();

    /**
     * Perform a key exchange server-side
     *
     * @param message the message which contains information
     *                for the key exchange to work
     * @param server  the server
     * @param channel the sender
     * @throws IOException if the exchange message fails to build
     */
    public static void performKeyExchange(final BaseMessage message, final Server server, final Channel channel) throws IOException {
        Long connectionId = message.getInt64();
        if (connectionId == null) return;

        byte[] encodedSecret = message.getBytes();
        String algorithm = message.getUTF();

        KeyPair serverPair = server.getPair();
        assert serverPair != null;

        PrivateKey serverPrivate = serverPair.getPrivate();
        SecretKey decrypted = decryptSecret(encodedSecret, algorithm, serverPrivate);
        SecretKey serverSecret = server.getSecret();

        server.mapKey(channel, decrypted, algorithm);

        MessageBuilder builder = new MessageBuilder();
        if (server.getAccessKey() == null) {
            builder.writeUTF(SecureGen.SECRET_ALGORITHM);
            builder.write(serverSecret.getEncoded());
        }

        builder.writeBoolean(server.getAccessKey() != null);
        channel.writeAndFlush(builder.build(Messages.KEY_EXCHANGE));

        if (server.getAccessKey() == null) {
            RemoteClient rm = new es.karmadev.api.netty.RemoteClient(connectionId, server, channel);
            rm.getProperties().put("id", channel.id().asShortText());

            server.getConnectedClients().put(channel.id().asLongText(), rm);

            NetworkEvent event = new ClientConnectedEvent(rm);
            server.handle(event);
        } else {
            connectionIds.put(channel.id().asLongText(), connectionId);
            clientKeys.put(channel.id().asLongText(), decrypted);
            clientKeyAlgorithms.put(channel.id().asLongText(), algorithm);
        }
    }

    /**
     * Validate an access key
     *
     * @param message the access key message
     * @param channel the client validating the access key
     * @param server  the server
     * @throws IOException if the key message fails to build
     */
    public static void validateAccessKey(final BaseMessage message, final Channel channel, final Server server) throws IOException {
        SecretKey secret = server.getSecret();

        Long connectionId = connectionIds.get(channel.id().asLongText());
        byte[] encodedAccessKey = message.clone().getBytes();
        if (encodedAccessKey == null || connectionId == null) return;

        SecretKey clientKey = clientKeys.get(channel.id().asLongText());
        String clientAlgorithm = clientKeyAlgorithms.get(channel.id().asLongText());

        if (clientKey == null || clientAlgorithm == null) return;

        byte[] accessKey = SecurityProvider.getInstance(clientAlgorithm)
                .decodeData(encodedAccessKey, clientKey);
        byte[] serverKnownKey = SecureGen.SECRET_PROVIDER
                .encodeData(accessKey, secret);

        if (!Arrays.equals(serverKnownKey, server.getAccessKey())) {
            MessageBuilder builder = new MessageBuilder();
            BaseMessage response = builder.build(Messages.DISCONNECTION);
            channel.writeAndFlush(response)
                    .addListener((future) -> channel.disconnect());

            return;
        }

        MessageBuilder builder = new MessageBuilder();
        builder.writeUTF(SecureGen.SECRET_ALGORITHM);
        builder.write(secret.getEncoded());
        builder.writeBoolean(false);

        channel.writeAndFlush(builder.build(Messages.KEY_EXCHANGE));

        RemoteClient rm = new es.karmadev.api.netty.RemoteClient(connectionId, server, channel);
        rm.getProperties().put("id", channel.id().asShortText());

        server.getConnectedClients().put(channel.id().asLongText(), rm);

        NetworkEvent event = new ClientConnectedEvent(rm);
        server.handle(event);
    }

    /**
     * Handle an encoded message
     *
     * @param message the message
     * @param server  the server
     * @param channel the sender
     * @param handler the message handler
     * @throws IOException if the decoded message fails to build
     * @throws DataFormatException if the decoded message is invalid or not compressed
     */
    public static void handleEncoded(final BaseMessage message, final Server server,
                                     final Channel channel, final BaseServerHandlers handler) throws IOException, DataFormatException {
        Long originalId = message.getInt64();
        if (originalId == null) return;
        byte[] encodedData = message.getBytes();
        byte[] decodedData = SecurityProvider.getInstance(SecureGen.SECRET_ALGORITHM)
                .decodeData(encodedData, server.getSecret());

        DecMessage decoded = new DecMessage(originalId, decodedData);
        if (Messages.isValid(decoded.getId())) {
            Messages decodedMessage = Messages.getById(decoded.getId());
            assert decodedMessage != null;

            RemoteClient rm = server.getConnectedClients().get(channel.id().asLongText());
            if (rm == null) return; //Unsafe communication

            DirectMessageEvent event = new DirectMessageEvent(rm, decoded.clone());
            server.handle(event);

            if (event.isCancelled()) return;

            Messages decodedType = Messages.getById(decoded.getId());
            assert decodedType != null;

            boolean handled = handler.handleEvent(decodedType, decoded.clone(), channel);
            if (!handled) {
                //TODO: Log warning
            }
        }
    }

    private static SecretKey decryptSecret(final byte[] data, final String algorithm, final PrivateKey key) {
        byte[] decoded = SecureGen.PAIR_PROVIDER
                .decodeData(data, key);

        return new SecretKeySpec(decoded, algorithm);
    }
}
