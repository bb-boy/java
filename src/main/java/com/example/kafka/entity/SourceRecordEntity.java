package com.example.kafka.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "source_records",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_source_records_offset",
        columnNames = {"topic_name", "partition_id", "offset_value"}
    )
)
public class SourceRecordEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "source_record_id")
    private Long sourceRecordId;

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "source_entity_type", length = 64)
    private String sourceEntityType;

    @Column(name = "source_entity_id", length = 128)
    private String sourceEntityId;

    @Column(name = "topic_name", nullable = false, length = 128)
    private String topicName;

    @Column(name = "partition_id", nullable = false)
    private Integer partitionId;

    @Column(name = "offset_value", nullable = false)
    private Long offsetValue;

    @Column(name = "message_key", length = 256)
    private String messageKey;

    @Column(name = "payload_hash", length = 64)
    private String payloadHash;

    @Column(name = "produced_at")
    private LocalDateTime producedAt;

    @Column(name = "consumed_at", nullable = false)
    private LocalDateTime consumedAt;

    @Column(name = "ingest_status", nullable = false, length = 32)
    private String ingestStatus;

    @Column(name = "raw_payload_json", columnDefinition = "json")
    private String rawPayloadJson;

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceEntityType() {
        return sourceEntityType;
    }

    public void setSourceEntityType(String sourceEntityType) {
        this.sourceEntityType = sourceEntityType;
    }

    public String getSourceEntityId() {
        return sourceEntityId;
    }

    public void setSourceEntityId(String sourceEntityId) {
        this.sourceEntityId = sourceEntityId;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public Integer getPartitionId() {
        return partitionId;
    }

    public void setPartitionId(Integer partitionId) {
        this.partitionId = partitionId;
    }

    public Long getOffsetValue() {
        return offsetValue;
    }

    public void setOffsetValue(Long offsetValue) {
        this.offsetValue = offsetValue;
    }

    public String getMessageKey() {
        return messageKey;
    }

    public void setMessageKey(String messageKey) {
        this.messageKey = messageKey;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public LocalDateTime getProducedAt() {
        return producedAt;
    }

    public void setProducedAt(LocalDateTime producedAt) {
        this.producedAt = producedAt;
    }

    public LocalDateTime getConsumedAt() {
        return consumedAt;
    }

    public void setConsumedAt(LocalDateTime consumedAt) {
        this.consumedAt = consumedAt;
    }

    public String getIngestStatus() {
        return ingestStatus;
    }

    public void setIngestStatus(String ingestStatus) {
        this.ingestStatus = ingestStatus;
    }

    public String getRawPayloadJson() {
        return rawPayloadJson;
    }

    public void setRawPayloadJson(String rawPayloadJson) {
        this.rawPayloadJson = rawPayloadJson;
    }
}
