package com.example.kafka.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 波形数据模型
 */
public class WaveData {
    private Integer shotNo;           // 炮号
    private String channelName;       // 通道名称
    private LocalDateTime startTime;  // 开始时间
    private LocalDateTime endTime;    // 结束时间
    private Double sampleRate;        // 采样率 (Hz)
    private Integer samples;          // 采样点数
    private List<Double> data;        // 波形数据
    private String fileSource;        // 数据来源文件
    private DataSourceType sourceType; // 数据来源类型

    public enum DataSourceType {
        FILE,      // 文件读取
        NETWORK    // 网络接收
    }

    // Constructors
    public WaveData() {}

    public WaveData(Integer shotNo, String channelName) {
        this.shotNo = shotNo;
        this.channelName = channelName;
    }

    // Getters and Setters
    public Integer getShotNo() {
        return shotNo;
    }

    public void setShotNo(Integer shotNo) {
        this.shotNo = shotNo;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
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

    public Double getSampleRate() {
        return sampleRate;
    }

    public void setSampleRate(Double sampleRate) {
        this.sampleRate = sampleRate;
    }

    public Integer getSamples() {
        return samples;
    }

    public void setSamples(Integer samples) {
        this.samples = samples;
    }

    public List<Double> getData() {
        return data;
    }

    public void setData(List<Double> data) {
        this.data = data;
    }

    public String getFileSource() {
        return fileSource;
    }

    public void setFileSource(String fileSource) {
        this.fileSource = fileSource;
    }

    public DataSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(DataSourceType sourceType) {
        this.sourceType = sourceType;
    }

    @Override
    public String toString() {
        return "WaveData{" +
                "shotNo=" + shotNo +
                ", channelName='" + channelName + '\'' +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", sampleRate=" + sampleRate +
                ", samples=" + samples +
                ", dataPoints=" + (data != null ? data.size() : 0) +
                ", sourceType=" + sourceType +
                '}';
    }
}
