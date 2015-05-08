package de.viaboxx.markdown.utils;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Description: compute a md5 from a file or resource-url.<br>
 * User: roman.stumm<br>
 * Date: 13.04.2011<br>
 * Time: 15:57:06<br>
 */
public class Md5Util {
    /**
     * compute a hexadecimal, 32-char md5 string for content from a stream.
     *
     * @param is - the stream to read content data from
     * @return the hexadecimal md5 string
     * @throws java.security.NoSuchAlgorithmException
     *                                  - no MD5 Digester in JDK
     * @throws java.io.IOException      - cannot read stream
     * @throws IllegalArgumentException - internal error computing the Md5 Hex String
     */
    public static String computeMd5(InputStream is) throws NoSuchAlgorithmException, IOException {
        if (is == null) return null;
        MessageDigest digest = MessageDigest.getInstance("MD5");
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return toHex(digest.digest());
        } finally {
            is.close();
        }
    }


    private static final char[] HEXES =
        {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F'};

    private static String toHex(byte[] raw) {
        if (raw.length != 16) throw new IllegalArgumentException("length must be 16, not " + raw.length);
        final char[] hex = new char[32];
        short i = 0;
        for (final byte b : raw) {
            hex[i] = HEXES[(b & 0xF0) >> 4];
            hex[i + 1] = HEXES[(b & 0x0F)];
            i += 2;
        }
        return new String(hex);
    }
}


