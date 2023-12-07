package es.karmadev.api.netty.connection;

import es.karmadev.api.channel.com.IConnection;
import es.karmadev.api.channel.com.remote.IRemoteServer;
import es.karmadev.api.channel.data.BaseMessage;
import es.karmadev.api.channel.exception.NetException;
import es.karmadev.api.channel.exception.connection.CloseException;
import es.karmadev.api.netty.Writeable;
import es.karmadev.api.netty.message.DecMessage;
import es.karmadev.api.netty.message.MessageBuilder;
import es.karmadev.api.netty.message.nat.Messages;
import es.karmadev.api.netty.secure.SecureGen;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.security.PublicKey;
import java.util.zip.DataFormatException;

/**
 * Represents a local connection
 */
public class LocalConnection implements IConnection {

    private final Writeable writeable;
    private final IRemoteServer server;

    private final SecretKey secret;

    public LocalConnection(final Writeable writeable, final IRemoteServer server) {
        this.writeable = writeable;
        this.server = server;

        secret = SecureGen.generateSecret();
    }

    /**
     * Write a message on the connection
     *
     * @param message the message to write
     */
    @Override
    public void write(final BaseMessage message) {
        if (writeable.isReady()) {
            writeable.push(message);
        } else {
            writeable.addToQue(message);
        }
    }

    @Override
    public IRemoteServer getServer() {
        return server;
    }

    /**
     * Perform the connection key exchange.
     * This process is performed automatically
     * when detected, but can also be called
     * when required
     *
     * @param serverKey the server key
     */
    public void performKeyExchange(final PublicKey serverKey) {
        if (secret == null) return;

        byte[] secured = SecureGen.protectKey(secret, serverKey);
        if (secured == null) throw new RuntimeException("Failed to encode our key with the server key");

        MessageBuilder builder = new MessageBuilder();
        builder.write(secured);
        builder.writeUTF(SecureGen.SECRET_ALGORITHM);

        try {
            BaseMessage message = builder.build(Messages.KEY_EXCHANGE);
            writeable.push(message);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Resolve the encoded data
     *
     * @param id the message type id
     * @param encodedData the encoded data
     * @return the resolved message
     */
    public BaseMessage resolve(final long id, final byte[] encodedData) {
        try {
            byte[] resolved = SecureGen.SECRET_PROVIDER
                    .decodeData(encodedData, secret);

            return new DecMessage(id, resolved);
        } catch (IOException | DataFormatException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Tries to close the connection. Once this
     * method is called, the connection will get
     * closed even though {@link NetException exception} is
     * thrown.
     *
     * @throws NetException if there's a network
     *                      problem while closing the connection
     */
    @Override
    public void close() throws NetException {
        try {
            writeable.close();
        } catch (IOException ex) {
            throw new CloseException(ex);
        }
    }
}
