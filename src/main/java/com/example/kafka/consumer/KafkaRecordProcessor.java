package com.example.kafka.consumer;

import com.example.kafka.config.TopicNames;
import com.example.kafka.entity.SourceRecordEntity;
import com.example.kafka.producer.KafkaMessagePublisher;
import com.example.kafka.repository.SourceRecordRepository;
import com.example.kafka.util.EventConstants;
import com.example.kafka.util.HashingUtil;
import com.example.kafka.util.KafkaPayloadParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
public class KafkaRecordProcessor {
    private static final Logger logger = LoggerFactory.getLogger(KafkaRecordProcessor.class);
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;
    private final SourceRecordRepository sourceRecordRepository;
    private final KafkaMessagePublisher publisher;

    public KafkaRecordProcessor(
        ObjectMapper objectMapper,
        SourceRecordRepository sourceRecordRepository,
        KafkaMessagePublisher publisher
    ) {
        this.objectMapper = objectMapper;
        this.sourceRecordRepository = sourceRecordRepository;
        this.publisher = publisher;
    }

    public void processRecord(
        ConsumerRecord<String, String> record,
        Acknowledgment ack,
        RecordHandler handler
    ) {
        Map<String, Object> payload = parsePayload(record.value());
        SourceRecordEntity sourceRecord = ensureSourceRecord(record, payload);
        if (EventConstants.INGEST_STATUS_PROCESSED.equals(sourceRecord.getIngestStatus())) {
            ack.acknowledge();
            return;
        }
        try {
            handler.handle(sourceRecord, payload);
            sourceRecord.setIngestStatus(EventConstants.INGEST_STATUS_PROCESSED);
            sourceRecordRepository.save(sourceRecord);
            ack.acknowledge();
        } catch (Exception ex) {
            sourceRecord.setIngestStatus(EventConstants.INGEST_STATUS_FAILED);
            sourceRecordRepository.save(sourceRecord);
            publishDlq(record, ex);
            throw ex;
        }
    }

    private SourceRecordEntity ensureSourceRecord(ConsumerRecord<String, String> record, Map<String, Object> payload) {
        Optional<SourceRecordEntity> existing = sourceRecordRepository
            .findByTopicNameAndPartitionIdAndOffsetValue(record.topic(), record.partition(), record.offset());
        SourceRecordEntity entity = existing.orElseGet(SourceRecordEntity::new);
        entity.setTopicName(record.topic());
        entity.setPartitionId(record.partition());
        entity.setOffsetValue(record.offset());
        entity.setMessageKey(record.key());
        entity.setPayloadHash(HashingUtil.sha256Hex(record.value()));
        entity.setSourceSystem(resolveSourceSystem(payload));
        entity.setSourceEntityType(record.topic());
        entity.setSourceEntityId(resolveSourceEntityId(payload, record.key()));
        entity.setProducedAt(parseKafkaTime(record.timestamp()));
        entity.setConsumedAt(LocalDateTime.now());
        if (existing.isEmpty()) {
            entity.setIngestStatus(EventConstants.INGEST_STATUS_RECEIVED);
            entity.setRawPayloadJson(record.value());
        }
        return sourceRecordRepository.save(entity);
    }

    private String resolveSourceSystem(Map<String, Object> payload) {
        String sourceSystem = KafkaPayloadParser.asString(payload, "source_system");
        return sourceSystem == null ? EventConstants.SOURCE_SYSTEM_TDMS : sourceSystem;
    }

    private String resolveSourceEntityId(Map<String, Object> payload, String messageKey) {
        String artifactId = KafkaPayloadParser.asString(payload, "artifact_id");
        if (artifactId != null) {
            return artifactId;
        }
        String correlationKey = KafkaPayloadParser.asString(payload, "correlation_key");
        if (correlationKey != null) {
            return correlationKey;
        }
        return messageKey;
    }

    private LocalDateTime parseKafkaTime(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }
        return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Map<String, Object> parsePayload(String rawJson) {
        try {
            return objectMapper.readValue(rawJson, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to parse Kafka payload", ex);
        }
    }

    private void publishDlq(ConsumerRecord<String, String> record, Exception ex) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("failed_topic", record.topic());
        payload.put("failed_partition", record.partition());
        payload.put("failed_offset", record.offset());
        payload.put("error_type", ex.getClass().getSimpleName());
        payload.put("error_message", ex.getMessage());
        payload.put("raw_payload_json", record.value());
        payload.put("retryable", true);
        try {
            publisher.publish(TopicNames.PIPELINE_DLQ, record.key(), payload);
        } catch (Exception dlqEx) {
            logger.error("Failed to publish DLQ", dlqEx);
        }
    }

    public interface RecordHandler {
        void handle(SourceRecordEntity sourceRecord, Map<String, Object> payload);
    }
}
