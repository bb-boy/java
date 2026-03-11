package com.example.kafka.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class FileHashUtil {
    private static final String SHA_256 = "SHA-256";
    private static final int BUFFER_SIZE = 8192;

    private FileHashUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static String sha256Hex(Path path) {
        MessageDigest digest = createDigest();
        try (InputStream inputStream = Files.newInputStream(path)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read file for hashing", ex);
        }
        return HexUtil.toHex(digest.digest());
    }

    private static MessageDigest createDigest() {
        try {
            return MessageDigest.getInstance(SHA_256);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
