package com.example.kafka.config;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

@Configuration
public class KafkaConfig {
    private static final int DEFAULT_PARTITION_COUNT = 3;
    private static final int DEFAULT_REPLICA_COUNT = 3;
    private static final String CLEANUP_POLICY_CONFIG = "cleanup.policy";
    private static final String RETENTION_MS_CONFIG = "retention.ms";
    private static final String POLICY_COMPACT = "compact";
    private static final String POLICY_DELETE = "delete";
    private static final Duration DELETE_RETENTION = Duration.ofDays(7);

    @Bean
    public KafkaAdmin.NewTopics ecrhTopics() {
        return new KafkaAdmin.NewTopics(
            buildCompactTopic(TopicNames.TDMS_ARTIFACT),
            buildCompactTopic(TopicNames.SHOT_META),
            buildCompactTopic(TopicNames.SIGNAL_CATALOG),
            buildCompactTopic(TopicNames.DICT_PROTECTION_TYPE),
            buildCompactTopic(TopicNames.DICT_OPERATION_MODE),
            buildCompactTopic(TopicNames.DICT_OPERATION_TASK),
            buildCompactTopic(TopicNames.DICT_OPERATION_TYPE),
            buildDeleteTopic(TopicNames.EVENT_OPERATION),
            buildDeleteTopic(TopicNames.EVENT_PROTECTION),
            buildDeleteTopic(TopicNames.WAVEFORM_INGEST_REQUEST),
            buildDeleteTopic(TopicNames.WAVEFORM_CHANNEL_BATCH),
            buildDeleteTopic(TopicNames.PIPELINE_DLQ)
        );
    }

    private NewTopic buildCompactTopic(String name) {
        return buildTopic(name, Map.of(CLEANUP_POLICY_CONFIG, POLICY_COMPACT));
    }

    private NewTopic buildDeleteTopic(String name) {
        String retentionMs = String.valueOf(DELETE_RETENTION.toMillis());
        Map<String, String> config = Map.of(
            CLEANUP_POLICY_CONFIG, POLICY_DELETE,
            RETENTION_MS_CONFIG, retentionMs
        );
        return buildTopic(name, config);
    }

    private NewTopic buildTopic(String name, Map<String, String> config) {
        return TopicBuilder.name(name)
            .partitions(DEFAULT_PARTITION_COUNT)
            .replicas(DEFAULT_REPLICA_COUNT)
            .configs(config)
            .build();
    }
}
