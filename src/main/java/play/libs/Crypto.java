package play.libs;

import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;

/**
 * Cryptography utils.
 */
public class Crypto {

    /**
     * Define a hash type enumeration for strong-typing
     */
    public enum HashType {
        MD5("MD5"),
        SHA1("SHA-1"),
        SHA256("SHA-256"),
        SHA512("SHA-512");
        private final String algorithm;
        HashType(final String algorithm) { this.algorithm = algorithm; }
        @Override public String toString() { return this.algorithm; }
    }

    /**
     * Set-up MD5 as the default hashing algorithm
     */
    private static final HashType DEFAULT_HASH_TYPE = HashType.MD5;

    static final char[] HEX_CHARS = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};

    /**
     * Sign a message using the application secret key (HMAC-SHA1)
     * @throws Exception
     */
    public static String sign(final String message) throws Exception {
        return sign(message, readSecretKey().getBytes());
    }

    /**
     * TODO implements.
     * @return
     */
    private static String readSecretKey() {
        return System.getProperty("application.secret", "").trim();
    }

    /**
     * Sign a message with a key
     * @param message The message to sign
     * @param key The key to use
     * @return The signed message (in hexadecimal)
     * @throws java.lang.Exception
     */
    public static String sign(final String message, final byte[] key) throws Exception {

        if (key.length == 0) {
            return message;
        }

        try {
            final Mac mac = Mac.getInstance("HmacSHA1");
            final SecretKeySpec signingKey = new SecretKeySpec(key, "HmacSHA1");
            mac.init(signingKey);
            final byte[] messageBytes = message.getBytes("utf-8");
            final byte[] result = mac.doFinal(messageBytes);
            final int len = result.length;
            final char[] hexChars = new char[len * 2];


            for (int charIndex = 0, startIndex = 0; charIndex < hexChars.length;) {
                final int bite = result[startIndex++] & 0xff;
                hexChars[charIndex++] = HEX_CHARS[bite >> 4];
                hexChars[charIndex++] = HEX_CHARS[bite & 0xf];
            }
            return new String(hexChars);
        } catch (final Exception ex) {
            throw ex;
        }

    }

    /**
        * Create a password hash using the default hashing algorithm
        * @param input The password
        * @return The password hash
        */
    public static String passwordHash(final String input) {
        return passwordHash(input, DEFAULT_HASH_TYPE);
    }

    /**
        * Create a password hash using specific hashing algorithm
        * @param input The password
        * @param hashType The hashing algorithm
        * @return The password hash
        */
    public static String passwordHash(final String input, final HashType hashType) {
        try {
            final MessageDigest m = MessageDigest.getInstance(hashType.toString());
            final byte[] out = m.digest(input.getBytes());
            return new String(Base64.encodeBase64(out));
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Encrypt a String with the AES encryption standard using the application secret
     * @param value The String to encrypt
     * @return An hexadecimal encrypted string
     * @throws Exception
     */
    public static String encryptAES(final String value) throws Exception {
        return encryptAES(value, readSecretKey().substring(0, 16));
    }

    /**
     * Encrypt a String with the AES encryption standard. Private key must have a length of 16 bytes
     * @param value The String to encrypt
     * @param privateKey The key used to encrypt
     * @return An hexadecimal encrypted string
     * @throws Exception
     */
    public static String encryptAES(final String value, final String privateKey) throws Exception {
        final byte[] raw = privateKey.getBytes();
        final SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        final Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.ENCRYPT_MODE, skeySpec);
        return Codec.byteToHexString(cipher.doFinal(value.getBytes()));
    }

    /**
     * Decrypt a String with the AES encryption standard using the application secret
     * @param value An hexadecimal encrypted string
     * @return The decrypted String
     * @throws DecoderException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public static String decryptAES(final String value)
            throws IllegalBlockSizeException, BadPaddingException, DecoderException,
            InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException {
        return decryptAES(value, readSecretKey().substring(0, 16));
    }

    /**
     * Decrypt a String with the AES encryption standard. Private key must have a length of 16 bytes
     * @param value An hexadecimal encrypted string
     * @param privateKey The key used to encrypt
     * @return The decrypted String
     * @throws DecoderException
     * @throws BadPaddingException
     * @throws IllegalBlockSizeException
     * @throws NoSuchPaddingException
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException
     */
    public static String decryptAES(final String value, final String privateKey)
            throws IllegalBlockSizeException, BadPaddingException, DecoderException,
            NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException {
        final byte[] raw = privateKey.getBytes();
        final SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
        final Cipher cipher = Cipher.getInstance("AES");
        cipher.init(Cipher.DECRYPT_MODE, skeySpec);
        return new String(cipher.doFinal(Codec.hexStringToByte(value)));
    }

}
