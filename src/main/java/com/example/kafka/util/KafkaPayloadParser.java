package com.example.kafka.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class KafkaPayloadParser {
    private static final DateTimeFormatter FALLBACK_DATE_TIME =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private KafkaPayloadParser() {
        throw new IllegalStateException("Utility class");
    }

    public static String asString(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value == null ? null : value.toString();
    }

    public static Integer asInteger(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Integer.parseInt(text);
        }
        return null;
    }

    public static Long asLong(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Long.parseLong(text);
        }
        return null;
    }

    public static Double asDouble(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        return null;
    }

    public static Boolean asBoolean(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Boolean.parseBoolean(text);
        }
        return null;
    }

    public static LocalDateTime asDateTime(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime dateTime) {
            return dateTime;
        }
        String text = value.toString();
        if (text.isBlank()) {
            return null;
        }
        return parseDateTime(text);
    }

    public static Instant asInstant(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        String text = value.toString();
        if (text.isBlank()) {
            return null;
        }
        return parseInstant(text);
    }

    public static Map<String, Object> asMap(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            return map;
        }
        return null;
    }

    public static List<String> asStringList(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    private static LocalDateTime parseDateTime(String text) {
        try {
            return OffsetDateTime.parse(text).toLocalDateTime();
        } catch (Exception ex) {
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception fallback) {
                return LocalDateTime.parse(text, FALLBACK_DATE_TIME);
            }
        }
    }

    private static Instant parseInstant(String text) {
        try {
            return OffsetDateTime.parse(text).toInstant();
        } catch (Exception ex) {
            try {
                return Instant.parse(text);
            } catch (Exception fallback) {
                return null;
            }
        }
    }
}
