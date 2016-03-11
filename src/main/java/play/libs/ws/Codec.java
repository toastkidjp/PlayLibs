package play.libs.ws;

import java.io.UnsupportedEncodingException;
import java.rmi.UnexpectedException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;

/**
 * Codec utils.
 */
public class Codec {

    /**
     * @return an UUID String
     */
    public static String UUID() {
        return UUID.randomUUID().toString();
    }

    /**
     * Encode a String to base64
     * @param value The plain String
     * @return The base64 encoded String
     * @throws UnsupportedEncodingException
     */
    public static String encodeBASE64(String value) throws UnsupportedEncodingException {
        return new String(Base64.encodeBase64(value.getBytes("utf-8")));
    }

    /**
     * Encode binary data to base64
     * @param value The binary data
     * @return The base64 encoded String
     */
    public static String encodeBASE64(byte[] value) {
        return new String(Base64.encodeBase64(value));
    }

    /**
     * Decode a base64 value
     * @param value The base64 encoded String
     * @return decoded binary data
     * @throws UnsupportedEncodingException
     */
    public static byte[] decodeBASE64(String value) throws UnsupportedEncodingException {
        return Base64.decodeBase64(value.getBytes("utf-8"));
    }

    /**
     * Build an hexadecimal MD5 hash for a String
     * @param value The String to hash
     * @return An hexadecimal Hash
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public static String hexMD5(String value)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest messageDigest = MessageDigest.getInstance("MD5");
        messageDigest.reset();
        messageDigest.update(value.getBytes("utf-8"));
        byte[] digest = messageDigest.digest();
        return byteToHexString(digest);
    }

    /**
     * Build an hexadecimal SHA1 hash for a String
     * @param value The String to hash
     * @return An hexadecimal Hash
     * @throws NoSuchAlgorithmException
     * @throws UnsupportedEncodingException
     */
    public static String hexSHA1(String value)
            throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md;
        md = MessageDigest.getInstance("SHA-1");
        md.update(value.getBytes("utf-8"));
        byte[] digest = md.digest();
        return byteToHexString(digest);
    }

    /**
     * Write a byte array as hexadecimal String.
     */
    public static String byteToHexString(byte[] bytes) {
        return String.valueOf(Hex.encodeHex(bytes));
    }

    /**
     * Transform an hexadecimal String to a byte array.
     * @throws DecoderException
     */
    public static byte[] hexStringToByte(String hexString) throws DecoderException {
        return Hex.decodeHex(hexString.toCharArray());
    }

}
