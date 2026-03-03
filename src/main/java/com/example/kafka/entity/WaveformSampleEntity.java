package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 波形分段样本实体 - 分段存储与预览统计
 */
@Entity
@Table(name = "waveform_samples", indexes = {
    @Index(name = "idx_seg_wave_time", columnList = "waveform_id, segment_start_time"),
    @Index(name = "idx_seg_shot_time", columnList = "shot_no, segment_start_time")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_seg_wave_index", columnNames = {"waveform_id", "segment_index"})
})
public class WaveformSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sampleId;

    @Column(name = "waveform_id", nullable = false)
    private Long waveformId;

    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;

    @Column(name = "segment_index", nullable = false)
    private Integer segmentIndex;

    @Column(name = "segment_start_time", nullable = false)
    private LocalDateTime segmentStartTime;

    @Column(name = "segment_end_time", nullable = false)
    private LocalDateTime segmentEndTime;

    @Column(name = "start_sample_index", nullable = false)
    private Long startSampleIndex;

    @Column(name = "sample_count", nullable = false)
    private Integer sampleCount;

    @Column(name = "encoding", nullable = false)
    private String encoding;

    @Lob
    @Column(name = "data_blob", columnDefinition = "LONGBLOB")
    private byte[] dataBlob;

    @Column(name = "v_min")
    private Double vMin;

    @Column(name = "v_max")
    private Double vMax;

    @Column(name = "v_rms")
    private Double vRms;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }

    public Long getWaveformId() { return waveformId; }
    public void setWaveformId(Long waveformId) { this.waveformId = waveformId; }

    public Integer getShotNo() { return shotNo; }
    public void setShotNo(Integer shotNo) { this.shotNo = shotNo; }

    public Integer getSegmentIndex() { return segmentIndex; }
    public void setSegmentIndex(Integer segmentIndex) { this.segmentIndex = segmentIndex; }

    public LocalDateTime getSegmentStartTime() { return segmentStartTime; }
    public void setSegmentStartTime(LocalDateTime segmentStartTime) { this.segmentStartTime = segmentStartTime; }

    public LocalDateTime getSegmentEndTime() { return segmentEndTime; }
    public void setSegmentEndTime(LocalDateTime segmentEndTime) { this.segmentEndTime = segmentEndTime; }

    public Long getStartSampleIndex() { return startSampleIndex; }
    public void setStartSampleIndex(Long startSampleIndex) { this.startSampleIndex = startSampleIndex; }

    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }

    public String getEncoding() { return encoding; }
    public void setEncoding(String encoding) { this.encoding = encoding; }

    public byte[] getDataBlob() { return dataBlob; }
    public void setDataBlob(byte[] dataBlob) { this.dataBlob = dataBlob; }

    public Double getVMin() { return vMin; }
    public void setVMin(Double vMin) { this.vMin = vMin; }

    public Double getVMax() { return vMax; }
    public void setVMax(Double vMax) { this.vMax = vMax; }

    public Double getVRms() { return vRms; }
    public void setVRms(Double vRms) { this.vRms = vRms; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
