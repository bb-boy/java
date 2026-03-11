package com.example.kafka.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class HashingUtil {
    private static final String SHA_256 = "SHA-256";

    private HashingUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static String sha256Hex(String value) {
        if (value == null) {
            return null;
        }
        MessageDigest digest = createDigest();
        byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        return HexUtil.toHex(hash);
    }

    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

}
