package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 波形元数据实体 - 通道、时间轴与采样参数
 */
@Entity
@Table(name = "waveform_metadata", indexes = {
    @Index(name = "idx_wave_shot_time", columnList = "shot_no, start_time"),
    @Index(name = "idx_wave_device_time", columnList = "device_id, start_time")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_wave_shot_device_channel", columnNames = {"shot_no", "device_id", "channel_name"})
})
public class WaveformMetadataEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long waveformId;

    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;

    @Column(name = "system_name", nullable = false)
    private String systemName;

    @Column(name = "device_id", nullable = false)
    private String deviceId;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "unit")
    private String unit;

    @Column(name = "sample_rate_hz")
    private Double sampleRateHz;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "total_samples")
    private Long totalSamples;

    @Lob
    @Column(name = "tags", columnDefinition = "TEXT")
    private String tags;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getWaveformId() { return waveformId; }
    public void setWaveformId(Long waveformId) { this.waveformId = waveformId; }

    public Integer getShotNo() { return shotNo; }
    public void setShotNo(Integer shotNo) { this.shotNo = shotNo; }

    public String getSystemName() { return systemName; }
    public void setSystemName(String systemName) { this.systemName = systemName; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public Double getSampleRateHz() { return sampleRateHz; }
    public void setSampleRateHz(Double sampleRateHz) { this.sampleRateHz = sampleRateHz; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public Long getTotalSamples() { return totalSamples; }
    public void setTotalSamples(Long totalSamples) { this.totalSamples = totalSamples; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
