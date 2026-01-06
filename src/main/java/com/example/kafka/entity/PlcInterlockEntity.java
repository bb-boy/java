package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * PLC互锁日志实体 - 数据库表映射
 */
@Entity
@Table(name = "plc_interlock", indexes = {
    @Index(name = "idx_plc_shot", columnList = "shot_no"),
    @Index(name = "idx_plc_time", columnList = "timestamp")
})
public class PlcInterlockEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;
    
    @Column(name = "timestamp")
    private LocalDateTime timestamp;
    
    @Column(name = "interlock_name")
    private String interlockName;
    
    @Column(name = "status")
    private Boolean status;  // true=正常, false=触发
    
    @Column(name = "current_value")
    private Double currentValue;
    
    @Column(name = "threshold")
    private Double threshold;
    
    @Column(name = "threshold_operation")
    private String thresholdOperation;
    
    @Column(name = "description", length = 1000)
    private String description;
    
    @Column(name = "additional_data", columnDefinition = "TEXT")
    private String additionalData;  // JSON格式存储额外数据
    
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
    public PlcInterlockEntity() {}
    
    public PlcInterlockEntity(Integer shotNo, LocalDateTime timestamp) {
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
    
    public String getInterlockName() { return interlockName; }
    public void setInterlockName(String interlockName) { this.interlockName = interlockName; }
    
    public Boolean getStatus() { return status; }
    public void setStatus(Boolean status) { this.status = status; }
    
    public Double getCurrentValue() { return currentValue; }
    public void setCurrentValue(Double currentValue) { this.currentValue = currentValue; }
    
    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
    
    public String getThresholdOperation() { return thresholdOperation; }
    public void setThresholdOperation(String thresholdOperation) { this.thresholdOperation = thresholdOperation; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getAdditionalData() { return additionalData; }
    public void setAdditionalData(String additionalData) { this.additionalData = additionalData; }
    
    public ShotMetadataEntity.DataSourceType getSourceType() { return sourceType; }
    public void setSourceType(ShotMetadataEntity.DataSourceType sourceType) { this.sourceType = sourceType; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
