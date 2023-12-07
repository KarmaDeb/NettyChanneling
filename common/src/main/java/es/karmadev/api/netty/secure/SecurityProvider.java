package es.karmadev.api.netty.secure;

import java.security.Key;

/**
 * Represents a security
 * provider utilities
 */
public interface SecurityProvider {

    /**
     * Get the security provider
     * instance
     *
     * @param algorithm the algorithm to use
     *                  on the security provider
     * @return the security provider
     */
    static SecurityProvider getInstance(final String algorithm) {
        return new Provider(algorithm);
    }

    /**
     * Encode the data
     *
     * @param data the data to encode
     * @param key the key to use to encode
     *            the data
     * @return the encoded data
     */
    byte[] encodeData(final byte[] data, final Key key);

    /**
     * Decode the data
     *
     * @param data the data to decode
     * @param key the key to use to decode
     *            the data
     * @return the decoded data
     */
    byte[] decodeData(final byte[] data, final Key key);
}