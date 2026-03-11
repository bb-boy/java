package com.example.kafka.util;

import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Map;

public final class PayloadValidator {
    private PayloadValidator() {
        throw new IllegalStateException("Utility class");
    }

    public static String requireString(Map<String, Object> payload, String key) {
        String value = KafkaPayloadParser.asString(payload, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    public static Integer requireInt(Map<String, Object> payload, String key) {
        Integer value = KafkaPayloadParser.asInteger(payload, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    public static Long requireLong(Map<String, Object> payload, String key) {
        Long value = KafkaPayloadParser.asLong(payload, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    public static Double requireDouble(Map<String, Object> payload, String key) {
        Double value = KafkaPayloadParser.asDouble(payload, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    public static LocalDateTime requireDateTime(Map<String, Object> payload, String key) {
        LocalDateTime value = KafkaPayloadParser.asDateTime(payload, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }

    public static Instant requireInstant(Map<String, Object> payload, String key) {
        Instant value = KafkaPayloadParser.asInstant(payload, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing or invalid field: " + key);
        }
        return value;
    }

    public static Boolean requireBoolean(Map<String, Object> payload, String key) {
        Boolean value = KafkaPayloadParser.asBoolean(payload, key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return value;
    }
}
