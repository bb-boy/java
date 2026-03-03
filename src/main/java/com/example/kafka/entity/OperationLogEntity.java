package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 操作日志实体 - 数据库表映射
 */
@Entity
@Table(name = "operation_log", indexes = {
    @Index(name = "idx_oplog_shot", columnList = "shot_no"),
    @Index(name = "idx_oplog_time", columnList = "timestamp"),
    @Index(name = "idx_oplog_user_time", columnList = "user_id, timestamp"),
    @Index(name = "idx_oplog_device_time", columnList = "device_id, timestamp")
})
public class OperationLogEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;
    
    @Column(name = "timestamp")
    private LocalDateTime timestamp;

    @Column(name = "operation_type")
    private String operationType;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "command")
    private String command;

    @Lob
    @Column(name = "parameters", columnDefinition = "TEXT")
    private String parameters;

    @Column(name = "result_code")
    private String resultCode;

    @Column(name = "result_message")
    private String resultMessage;

    @Column(name = "source")
    private String source;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "channel_name")
    private String channelName;
    
    @Column(name = "step_type")
    private String stepType;
    
    @Column(name = "old_value")
    private Double oldValue;
    
    @Column(name = "new_value")
    private Double newValue;
    
    @Column(name = "delta")
    private Double delta;
    
    @Column(name = "confidence")
    private Double confidence;
    
    @Column(name = "file_source")
    private String fileSource;
    
    @Column(name = "source_type")
    @Enumerated(EnumType.STRING)
    private ShotMetadataEntity.DataSourceType sourceType;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // Constructors
    public OperationLogEntity() {}
    
    public OperationLogEntity(Integer shotNo, LocalDateTime timestamp) {
        this.shotNo = shotNo;
        this.timestamp = timestamp;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Integer getShotNo() { return shotNo; }
    public void setShotNo(Integer shotNo) { this.shotNo = shotNo; }
    
    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
    
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }

    public String getParameters() { return parameters; }
    public void setParameters(String parameters) { this.parameters = parameters; }

    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }

    public String getResultMessage() { return resultMessage; }
    public void setResultMessage(String resultMessage) { this.resultMessage = resultMessage; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    
    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }
    
    public Double getOldValue() { return oldValue; }
    public void setOldValue(Double oldValue) { this.oldValue = oldValue; }
    
    public Double getNewValue() { return newValue; }
    public void setNewValue(Double newValue) { this.newValue = newValue; }
    
    public Double getDelta() { return delta; }
    public void setDelta(Double delta) { this.delta = delta; }
    
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    
    public String getFileSource() { return fileSource; }
    public void setFileSource(String fileSource) { this.fileSource = fileSource; }
    
    public ShotMetadataEntity.DataSourceType getSourceType() { return sourceType; }
    public void setSourceType(ShotMetadataEntity.DataSourceType sourceType) { this.sourceType = sourceType; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
