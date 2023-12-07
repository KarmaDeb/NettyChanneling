package es.karmadev.api.netty.handler;

import es.karmadev.api.channel.com.security.SecurityProvider;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.subscription.event.NetworkEvent;
import es.karmadev.api.channel.subscription.event.connection.PostConnectEvent;
import es.karmadev.api.channel.subscription.event.data.direct.MessageReceiveEvent;
import es.karmadev.api.netty.Client;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
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

public class ClientHandler extends ChannelInboundHandlerAdapter {

    private final Client client;

    private PublicKey serverKey;

    private String serverAlgo;
    private SecretKey serverSecret;

    public ClientHandler(final Client client) {
        this.client = client;
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
        if (msg instanceof BaseMessage) {
            BaseMessage message = (BaseMessage) msg;
            long id = message.getId();

            if (id == Messages.KEY_EXCHANGE.getId()) {
                byte[] key = message.getBytes();
                String algorithm = message.getUTF();

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

                if (encodedId == Messages.KEY_EXCHANGE.getId()) {
                    if (serverAlgo != null && serverSecret != null) return;

                    byte[] serverKey = resolved.getBytes();
                    String algorithm = resolved.getUTF();
                    if (algorithm == null) return;

                    serverSecret = new SecretKeySpec(serverKey, algorithm);
                    serverAlgo = algorithm;

                    client.setReady(true);

                    NetworkEvent event = new PostConnectEvent(client.getServer());
                    client.handle(event);

                    client.processQue((queMessage) -> client.getServer().write(queMessage));
                    return;
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
