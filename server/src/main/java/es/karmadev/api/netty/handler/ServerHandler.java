package es.karmadev.api.netty.handler;

import es.karmadev.api.channel.com.remote.RemoteClient;
import es.karmadev.api.channel.com.security.SecurityProvider;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.channel.subscription.event.connection.server.ClientConnectedEvent;
import es.karmadev.api.channel.subscription.event.connection.server.ClientPreConnectEvent;
import es.karmadev.api.channel.subscription.event.data.server.direct.DirectMessageEvent;
import es.karmadev.api.netty.Server;
import es.karmadev.api.netty.message.DecMessage;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import es.karmadev.api.netty.secure.SecureGen;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.PrivateKey;

public class ServerHandler extends ChannelInboundHandlerAdapter {

    private final Server server;

    public ServerHandler(final Server server) {
        this.server = server;
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
                builder.writeUTF(SecureGen.SECRET_ALGORITHM);
                builder.write(serverSecret.getEncoded());

                channel.writeAndFlush(builder.build(Messages.KEY_EXCHANGE));

                RemoteClient rm = new es.karmadev.api.netty.RemoteClient(connectionId, server, channel);
                rm.getProperties().put("id", channel.id().asShortText());

                server.getConnectedClients().put(channel.id().asLongText(), rm);
            }

            if (type.equals(Messages.ENCODED)) {
                RemoteClient rm = server.getConnectedClients().get(channel.id().asLongText());
                if (rm == null) return; //Unsafe communication

                Long originalId = message.getInt64();
                if (originalId == null) return;
                byte[] encodedData = message.getBytes();
                byte[] decodedData = SecurityProvider.getInstance(SecureGen.SECRET_ALGORITHM)
                                .decodeData(encodedData, server.getSecret());

                DecMessage decoded = new DecMessage(originalId, decodedData);
                DirectMessageEvent event = new DirectMessageEvent(rm, decoded);

                server.handle(event);

                if (event.isCancelled()) {
                    //TODO: Discard message
                }
                //TODO: Forward message (if needed)
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

        NetworkEvent event = new ClientConnectedEvent(rm);
        server.handle(event);

        server.getConnectedClients().remove(channel.id().asLongText());
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }

    private SecretKey decryptSecret(final byte[] data, final String algorithm, final PrivateKey key) {
        byte[] decoded = SecureGen.PAIR_PROVIDER
                .decodeData(data, key);

        return new SecretKeySpec(decoded, algorithm);
    }
}
