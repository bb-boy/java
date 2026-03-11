package com.example.kafka.util;

import java.time.LocalDateTime;

public final class DedupKeyBuilder {
    private static final String SEPARATOR = "|";

    private DedupKeyBuilder() {
        throw new IllegalStateException("Utility class");
    }

    public static String build(
        String sourceSystem,
        String eventFamily,
        String eventCode,
        Integer shotNo,
        LocalDateTime eventTime,
        String processId,
        String sourceEntityId,
        String payloadHash
    ) {
        if (sourceSystem == null || eventFamily == null || shotNo == null || eventTime == null) {
            throw new IllegalArgumentException("Missing required dedup key fields");
        }
        String entityId = resolveEntityId(sourceEntityId, payloadHash);
        return String.join(
            SEPARATOR,
            sourceSystem,
            eventFamily,
            safe(eventCode),
            shotNo.toString(),
            eventTime.toString(),
            safe(processId),
            entityId
        );
    }

    private static String resolveEntityId(String sourceEntityId, String payloadHash) {
        if (sourceEntityId != null && !sourceEntityId.isBlank()) {
            return sourceEntityId;
        }
        if (payloadHash == null || payloadHash.isBlank()) {
            throw new IllegalArgumentException("Missing source entity id and payload hash");
        }
        return payloadHash;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
