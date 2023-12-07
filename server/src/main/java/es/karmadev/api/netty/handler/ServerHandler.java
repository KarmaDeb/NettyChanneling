package es.karmadev.api.netty.handler;

import es.karmadev.api.channel.com.security.SecurityProvider;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.channel.subscription.event.data.MessageReceiveEvent;
import es.karmadev.api.netty.Server;
import es.karmadev.api.netty.message.DecMessage;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import es.karmadev.api.netty.secure.SecureGen;
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
        if (msg instanceof BaseMessage) {
            BaseMessage message = (BaseMessage) msg;
            long id = message.getId();

            Messages type = Messages.getById(id);
            if (type == null) return;

            if (type.equals(Messages.KEY_EXCHANGE)) {
                byte[] encodedSecret = message.getBytes();
                String algorithm = message.getUTF();

                KeyPair serverPair = server.getPair();
                assert serverPair != null;

                PrivateKey serverPrivate = serverPair.getPrivate();
                SecretKey decrypted = decryptSecret(encodedSecret, algorithm, serverPrivate);
                SecretKey serverSecret = server.getSecret();

                server.mapKey(ctx.channel(), decrypted, algorithm);

                MessageBuilder builder = new MessageBuilder();
                builder.writeUTF(SecureGen.SECRET_ALGORITHM);
                builder.write(serverSecret.getEncoded());

                ctx.channel().writeAndFlush(builder.build(Messages.KEY_EXCHANGE));
            }
            if (type.equals(Messages.ENCODED)) {
                Long originalId = message.getInt64();
                if (originalId == null) return;
                byte[] encodedData = message.getBytes();
                byte[] decodedData = SecurityProvider.getInstance(SecureGen.SECRET_ALGORITHM)
                                .decodeData(encodedData, server.getSecret());

                DecMessage decoded = new DecMessage(originalId, decodedData);
                NetworkEvent event = new MessageReceiveEvent(decoded);

                server.handle(event);
            }

            return;
        }

        super.channelRead(ctx, msg);
    }

    private SecretKey decryptSecret(final byte[] data, final String algorithm, final PrivateKey key) {
        byte[] decoded = SecureGen.PAIR_PROVIDER
                .decodeData(data, key);

        return new SecretKeySpec(decoded, algorithm);
    }
}
