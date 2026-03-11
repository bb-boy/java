package com.example.kafka.util;

public final class HexUtil {
    private HexUtil() {
        throw new IllegalStateException("Utility class");
    }

    public static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }
}
