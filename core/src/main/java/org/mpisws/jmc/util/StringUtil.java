package org.mpisws.jmc.util;

import java.security.MessageDigest;

/**
 * Utility class for string operations.
 *
 * <p>This class provides methods for common string operations, such as hashing.
 */
public class StringUtil {
    /**
     * Generates a SHA-256 hash of the given input string.
     *
     * @param input the input string to hash
     * @return the SHA-256 hash of the input string as a hexadecimal string
     * @throws Exception if an error occurs during hashing
     */
    public static String sha256Hash(String input) throws Exception {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new Exception(e);
        }
    }
}
