package es.karmadev.api.netty.secure;

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
import es.karmadev.api.core.ExceptionCollector;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;

/**
 * Secure keys generation
 */
public class SecureGen {

    public final static String PAIR_ALGORITHM = "RSA";
    public final static String SECRET_ALGORITHM = "AES";

    public final static SecurityProvider PAIR_PROVIDER = SecurityProvider.getInstance(PAIR_ALGORITHM);
    public final static SecurityProvider SECRET_PROVIDER = SecurityProvider.getInstance(SECRET_ALGORITHM);

    /**
     * Generate key pairs
     *
     * @return the key pairs
     */
    public static KeyPair generateKeyPair() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(PAIR_ALGORITHM);
            generator.initialize(2048);

            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            ExceptionCollector.catchException(SecureGen.class, ex);
        }

        return null;
    }

    /**
     * Generate a secret key
     *
     * @return the secret key
     */
    public static SecretKey generateSecret() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance(SECRET_ALGORITHM);
            generator.init(256);

            return generator.generateKey();
        } catch (NoSuchAlgorithmException ex) {
            ExceptionCollector.catchException(SecureGen.class, ex);
        }

        return null;
    }

    /**
     * Protect a key
     *
     * @param key the key to protect
     * @param encoder the key encoder
     * @return the encrypted key
     */
    public static byte[] protectKey(final SecretKey key, final PublicKey encoder) {
        try {
            Cipher cipher = Cipher.getInstance(PAIR_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, encoder);

            return cipher.doFinal(key.getEncoded());
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                    | IllegalBlockSizeException | BadPaddingException ex) {
            ExceptionCollector.catchException(SecureGen.class, ex);
        }

        return null;
    }

    /**
     * Decode a key
     *
     * @param encoded the encoded key
     * @param decoder the key decoder
     * @return the decrypted key
     */
    public static SecretKey decodeKey(final byte[] encoded, final PrivateKey decoder) {
        try {
            Cipher cipher = Cipher.getInstance(PAIR_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, decoder);

            byte[] decoded = cipher.doFinal(encoded);
            return new SecretKeySpec(decoded, 0, decoded.length, SECRET_ALGORITHM);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException
                 | IllegalBlockSizeException | BadPaddingException ex) {
            ExceptionCollector.catchException(SecureGen.class, ex);
        }

        return null;
    }
}
