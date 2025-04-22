package org.mpisws.jmc.util;

import java.security.MessageDigest;

public class StringUtil {
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
