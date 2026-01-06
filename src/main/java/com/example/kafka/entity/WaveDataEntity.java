package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 波形数据实体 - 数据库表映射
 */
@Entity
@Table(name = "wave_data", indexes = {
    @Index(name = "idx_wave_shot_channel", columnList = "shot_no, channel_name, data_type")
})
public class WaveDataEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;
    
    @Column(name = "channel_name", nullable = false)
    private String channelName;
    
    @Column(name = "data_type")
    private String dataType;  // Tube 或 Water
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "sample_rate")
    private Double sampleRate;
    
    @Column(name = "samples")
    private Integer samples;
    
    @Lob
    @Column(name = "data", columnDefinition = "LONGBLOB")
    private byte[] data;  // 压缩存储波形数据
    
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
    public WaveDataEntity() {}
    
    public WaveDataEntity(Integer shotNo, String channelName) {
        this.shotNo = shotNo;
        this.channelName = channelName;
    }
    
    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    
    public Integer getShotNo() { return shotNo; }
    public void setShotNo(Integer shotNo) { this.shotNo = shotNo; }
    
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    
    public Double getSampleRate() { return sampleRate; }
    public void setSampleRate(Double sampleRate) { this.sampleRate = sampleRate; }
    
    public Integer getSamples() { return samples; }
    public void setSamples(Integer samples) { this.samples = samples; }
    
    public byte[] getData() { return data; }
    public void setData(byte[] data) { this.data = data; }
    
    public String getFileSource() { return fileSource; }
    public void setFileSource(String fileSource) { this.fileSource = fileSource; }
    
    public ShotMetadataEntity.DataSourceType getSourceType() { return sourceType; }
    public void setSourceType(ShotMetadataEntity.DataSourceType sourceType) { this.sourceType = sourceType; }
    
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
