package com.example.kafka.config;

public final class TopicNames {
    public static final String TDMS_ARTIFACT = "ecrh.tdms.artifact.v1";
    public static final String SHOT_META = "ecrh.shot.meta.v1";
    public static final String SIGNAL_CATALOG = "ecrh.signal.catalog.v1";
    public static final String EVENT_OPERATION = "ecrh.event.operation.v1";
    public static final String EVENT_PROTECTION = "ecrh.event.protection.v1";
    public static final String DICT_PROTECTION_TYPE = "ecrh.dict.protection_type.v1";
    public static final String DICT_OPERATION_MODE = "ecrh.dict.operation_mode.v1";
    public static final String DICT_OPERATION_TASK = "ecrh.dict.operation_task.v1";
    public static final String DICT_OPERATION_TYPE = "ecrh.dict.operation_type.v1";
    public static final String WAVEFORM_INGEST_REQUEST = "ecrh.waveform.ingest.request.v1";
    public static final String WAVEFORM_CHANNEL_BATCH = "ecrh.waveform.channel.batch.v1";
    public static final String PIPELINE_DLQ = "ecrh.pipeline.dlq.v1";

    private TopicNames() {
        throw new IllegalStateException("Utility class");
    }
}
