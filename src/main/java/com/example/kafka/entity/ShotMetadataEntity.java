package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 炮号元数据实体 - 数据库表映射
 */
@Entity
@Table(name = "shot_metadata")
public class ShotMetadataEntity {
    
    @Id
    @Column(name = "shot_no")
    private Integer shotNo;
    
    @Column(name = "file_name")
    private String fileName;
    
    @Column(name = "file_path")
    private String filePath;
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "expected_duration")
    private Double expectedDuration;
    
    @Column(name = "actual_duration")
    private Double actualDuration;
    
    @Column(name = "status")
    private String status;
    
    @Column(name = "reason")
    private String reason;
    
    @Column(name = "tolerance")
    private Double tolerance;
    
    @Column(name = "total_samples")
    private Integer totalSamples;
    
    @Column(name = "sample_rate")
    private Double sampleRate;
    
    @Column(name = "source_type")
    @Enumerated(EnumType.STRING)
    private DataSourceType sourceType;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum DataSourceType {
        FILE, NETWORK
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Constructors
    public ShotMetadataEntity() {}
    
    public ShotMetadataEntity(Integer shotNo) {
        this.shotNo = shotNo;
    }
    
    // Getters and Setters
    public Integer getShotNo() { return shotNo; }
    public void setShotNo(Integer shotNo) { this.shotNo = shotNo; }
    
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public Double getExpectedDuration() { return expectedDuration; }
    public void setExpectedDuration(Double expectedDuration) { this.expectedDuration = expectedDuration; }
    
    public Double getActualDuration() { return actualDuration; }
    public void setActualDuration(Double actualDuration) { this.actualDuration = actualDuration; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    
    public Double getTolerance() { return tolerance; }
    public void setTolerance(Double tolerance) { this.tolerance = tolerance; }
    
    public Integer getTotalSamples() { return totalSamples; }
    public void setTotalSamples(Integer totalSamples) { this.totalSamples = totalSamples; }
    
    public Double getSampleRate() { return sampleRate; }
    public void setSampleRate(Double sampleRate) { this.sampleRate = sampleRate; }
    
    public DataSourceType getSourceType() { return sourceType; }
    public void setSourceType(DataSourceType sourceType) { this.sourceType = sourceType; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
