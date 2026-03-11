package com.example.kafka.util;

public final class EventConstants {
    public static final String SOURCE_SYSTEM_TDMS = "TDMS_DERIVED";
    public static final String AUTHORITY_PROVISIONAL = "PROVISIONAL";
    public static final String EVENT_FAMILY_OPERATION = "OPERATION";
    public static final String EVENT_FAMILY_PROTECTION = "PROTECTION";
    public static final String EVENT_STATUS_ACTIVE = "ACTIVE";
    public static final String INGEST_STATUS_RECEIVED = "RECEIVED";
    public static final String INGEST_STATUS_PROCESSED = "PROCESSED";
    public static final String INGEST_STATUS_FAILED = "FAILED";
    public static final String WAVEFORM_STATUS_REQUESTED = "REQUESTED";
    public static final String WAVEFORM_STATUS_FAILED = "FAILED";
    public static final String WAVEFORM_STATUS_INGESTED = "INGESTED";
    public static final String WAVEFORM_INGEST_MODE = "BATCH";
    public static final String WAVEFORM_CHANNEL_SCOPE_ALL = "ALL";
    public static final String ARTIFACT_STATUS_PARSED = "PARSED";

    private EventConstants() {
        throw new IllegalStateException("Utility class");
    }
}
