package es.karmadev.api.netty.secure;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;

class Provider implements SecurityProvider {

    private final String algorithm;

    Provider(final String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * Encode the data
     *
     * @param data the data to encode
     * @param key  the key to use to encode
     *             the data
     * @return the encoded data
     */
    @Override
    public byte[] encodeData(final byte[] data, final Key key) {
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.ENCRYPT_MODE, key);

            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                 InvalidKeyException | IllegalBlockSizeException | BadPaddingException ignored) {}

        return null;
    }

    /**
     * Decode the data
     *
     * @param data the data to decode
     * @param key  the key to use to decode
     *             the data
     * @return the decoded data
     */
    @Override
    public byte[] decodeData(final byte[] data, final Key key) {
        try {
            Cipher cipher = Cipher.getInstance(algorithm);
            cipher.init(Cipher.DECRYPT_MODE, key);

            return cipher.doFinal(data);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException |
                 InvalidKeyException | IllegalBlockSizeException | BadPaddingException ignored) {}

        return null;
    }
}
