package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 采集/同步失败记录
 */
@Entity
@Table(name = "ingest_errors", indexes = {
    @Index(name = "idx_ingest_error_time", columnList = "event_time"),
    @Index(name = "idx_ingest_error_topic", columnList = "topic"),
    @Index(name = "idx_ingest_error_shot", columnList = "shot_no")
})
public class IngestErrorEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_time")
    private LocalDateTime eventTime;

    @Column(name = "error_type")
    private String errorType;

    @Column(name = "topic")
    private String topic;

    @Column(name = "message_key")
    private String messageKey;

    @Column(name = "shot_no")
    private Integer shotNo;

    @Column(name = "data_type")
    private String dataType;

    @Column(name = "error_message", length = 1024)
    private String errorMessage;

    @Column(name = "payload_size")
    private Integer payloadSize;

    @Lob
    @Column(name = "payload_preview", columnDefinition = "TEXT")
    private String payloadPreview;

    @Lob
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }

    public String getErrorType() { return errorType; }
    public void setErrorType(String errorType) { this.errorType = errorType; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getMessageKey() { return messageKey; }
    public void setMessageKey(String messageKey) { this.messageKey = messageKey; }

    public Integer getShotNo() { return shotNo; }
    public void setShotNo(Integer shotNo) { this.shotNo = shotNo; }

    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Integer getPayloadSize() { return payloadSize; }
    public void setPayloadSize(Integer payloadSize) { this.payloadSize = payloadSize; }

    public String getPayloadPreview() { return payloadPreview; }
    public void setPayloadPreview(String payloadPreview) { this.payloadPreview = payloadPreview; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
