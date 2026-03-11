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
    name = "events",
    uniqueConstraints = @UniqueConstraint(name = "uk_events_dedup", columnNames = "dedup_key")
)
public class EventEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;

    @Column(name = "source_record_id")
    private Long sourceRecordId;

    @Column(name = "artifact_id", length = 64)
    private String artifactId;

    @Column(name = "event_family", nullable = false, length = 32)
    private String eventFamily;

    @Column(name = "event_code", length = 64)
    private String eventCode;

    @Column(name = "event_name", length = 255)
    private String eventName;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "process_id", length = 128)
    private String processId;

    @Column(name = "message_text", columnDefinition = "text")
    private String messageText;

    @Column(name = "message_level", length = 32)
    private String messageLevel;

    @Column(name = "severity", length = 32)
    private String severity;

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "authority_level", length = 32)
    private String authorityLevel;

    @Column(name = "event_status", length = 32)
    private String eventStatus;

    @Column(name = "correlation_key", length = 128)
    private String correlationKey;

    @Column(name = "dedup_key", nullable = false, length = 256)
    private String dedupKey;

    @Column(name = "raw_payload_json", columnDefinition = "json")
    private String rawPayloadJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Integer getShotNo() {
        return shotNo;
    }

    public void setShotNo(Integer shotNo) {
        this.shotNo = shotNo;
    }

    public Long getSourceRecordId() {
        return sourceRecordId;
    }

    public void setSourceRecordId(Long sourceRecordId) {
        this.sourceRecordId = sourceRecordId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getEventFamily() {
        return eventFamily;
    }

    public void setEventFamily(String eventFamily) {
        this.eventFamily = eventFamily;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getMessageLevel() {
        return messageLevel;
    }

    public void setMessageLevel(String messageLevel) {
        this.messageLevel = messageLevel;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getAuthorityLevel() {
        return authorityLevel;
    }

    public void setAuthorityLevel(String authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getRawPayloadJson() {
        return rawPayloadJson;
    }

    public void setRawPayloadJson(String rawPayloadJson) {
        this.rawPayloadJson = rawPayloadJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
