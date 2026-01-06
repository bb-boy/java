package com.example.kafka.model;

import java.time.LocalDateTime;

/**
 * 操作日志模型
 */
public class OperationLog {
    private Integer shotNo;              // 炮号
    private LocalDateTime timestamp;     // 操作时间
    private String operationType;        // 操作类型 (如: 调参)
    private String channelName;          // 通道名称 (如: 阴极电压, 阳极电压)
    private String stepType;             // 步进类型
    private Double oldValue;             // 旧值
    private Double newValue;             // 新值
    private Double delta;                // 变化量
    private Double confidence;           // 置信度 (σ)
    private String fileSource;           // 数据来源文件
    private WaveData.DataSourceType sourceType; // 数据来源类型

    // Constructors
    public OperationLog() {}

    public OperationLog(Integer shotNo, LocalDateTime timestamp) {
        this.shotNo = shotNo;
        this.timestamp = timestamp;
    }

    // Getters and Setters
    public Integer getShotNo() {
        return shotNo;
    }

    public void setShotNo(Integer shotNo) {
        this.shotNo = shotNo;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public String getStepType() {
        return stepType;
    }

    public void setStepType(String stepType) {
        this.stepType = stepType;
    }

    public Double getOldValue() {
        return oldValue;
    }

    public void setOldValue(Double oldValue) {
        this.oldValue = oldValue;
    }

    public Double getNewValue() {
        return newValue;
    }

    public void setNewValue(Double newValue) {
        this.newValue = newValue;
    }

    public Double getDelta() {
        return delta;
    }

    public void setDelta(Double delta) {
        this.delta = delta;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public String getFileSource() {
        return fileSource;
    }

    public void setFileSource(String fileSource) {
        this.fileSource = fileSource;
    }

    public WaveData.DataSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(WaveData.DataSourceType sourceType) {
        this.sourceType = sourceType;
    }

    @Override
    public String toString() {
        return "OperationLog{" +
                "shotNo=" + shotNo +
                ", timestamp=" + timestamp +
                ", operationType='" + operationType + '\'' +
                ", channelName='" + channelName + '\'' +
                ", delta=" + delta +
                ", confidence=" + confidence +
                '}';
    }
}
