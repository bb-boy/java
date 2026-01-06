package com.example.kafka.model;

import java.time.LocalDateTime;

/**
 * 炮号元数据模型
 */
public class ShotMetadata {
    private Integer shotNo;               // 炮号
    private String fileName;              // 文件名
    private String filePath;              // 文件路径
    private LocalDateTime startTime;      // 开始时间
    private LocalDateTime endTime;        // 结束时间
    private Double expectedDuration;      // 预期持续时间 (秒)
    private Double actualDuration;        // 实际持续时间 (秒)
    private String status;                // 状态 (正常完成, 异常等)
    private String reason;                // 状态原因
    private Double tolerance;             // 容差 (秒)
    private Integer totalSamples;         // 总采样数
    private Double sampleRate;            // 采样率

    // Constructors
    public ShotMetadata() {}

    public ShotMetadata(Integer shotNo) {
        this.shotNo = shotNo;
    }

    // Getters and Setters
    public Integer getShotNo() {
        return shotNo;
    }

    public void setShotNo(Integer shotNo) {
        this.shotNo = shotNo;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Double getExpectedDuration() {
        return expectedDuration;
    }

    public void setExpectedDuration(Double expectedDuration) {
        this.expectedDuration = expectedDuration;
    }

    public Double getActualDuration() {
        return actualDuration;
    }

    public void setActualDuration(Double actualDuration) {
        this.actualDuration = actualDuration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Double getTolerance() {
        return tolerance;
    }

    public void setTolerance(Double tolerance) {
        this.tolerance = tolerance;
    }

    public Integer getTotalSamples() {
        return totalSamples;
    }

    public void setTotalSamples(Integer totalSamples) {
        this.totalSamples = totalSamples;
    }

    public Double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Double sampleRate) {
        this.sampleRate = sampleRate;
    }

    @Override
    public String toString() {
        return "ShotMetadata{" +
                "shotNo=" + shotNo +
                ", fileName='" + fileName + '\'' +
                ", startTime=" + startTime +
                ", actualDuration=" + actualDuration +
                ", status='" + status + '\'' +
                '}';
    }
}
