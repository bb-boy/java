package com.example.kafka.model;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * PLC互锁日志模型
 */
public class PlcInterlock {
    private Integer shotNo;                    // 炮号
    private LocalDateTime timestamp;           // 时间戳
    private String interlockName;              // 互锁名称
    private Boolean status;                    // 互锁状态 (true=正常, false=触发)
    private Double currentValue;               // 当前值
    private Double threshold;                  // 阈值
    private String thresholdOperation;         // 阈值操作 (如: 设置阈值)
    private String description;                // 描述信息
    private Map<String, Object> additionalData; // 额外数据
    private WaveData.DataSourceType sourceType; // 数据来源类型

    // Constructors
    public PlcInterlock() {}

    public PlcInterlock(Integer shotNo, LocalDateTime timestamp) {
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

    public String getInterlockName() {
        return interlockName;
    }

    public void setInterlockName(String interlockName) {
        this.interlockName = interlockName;
    }

    public Boolean getStatus() {
        return status;
    }

    public void setStatus(Boolean status) {
        this.status = status;
    }

    public Double getCurrentValue() {
        return currentValue;
    }

    public void setCurrentValue(Double currentValue) {
        this.currentValue = currentValue;
    }

    public Double getThreshold() {
        return threshold;
    }

    public void setThreshold(Double threshold) {
        this.threshold = threshold;
    }

    public String getThresholdOperation() {
        return thresholdOperation;
    }

    public void setThresholdOperation(String thresholdOperation) {
        this.thresholdOperation = thresholdOperation;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Map<String, Object> getAdditionalData() {
        return additionalData;
    }

    public void setAdditionalData(Map<String, Object> additionalData) {
        this.additionalData = additionalData;
    }

    public WaveData.DataSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(WaveData.DataSourceType sourceType) {
        this.sourceType = sourceType;
    }

    @Override
    public String toString() {
        return "PlcInterlock{" +
                "shotNo=" + shotNo +
                ", timestamp=" + timestamp +
                ", interlockName='" + interlockName + '\'' +
                ", status=" + status +
                ", currentValue=" + currentValue +
                ", threshold=" + threshold +
                '}';
    }
}
