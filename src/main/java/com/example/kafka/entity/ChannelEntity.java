package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "channels", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"shot_no", "channel_name", "data_type"})
})
public class ChannelEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;

    @Column(name = "channel_name", nullable = false)
    private String channelName;

    @Column(name = "data_type", nullable = false)
    private String dataType; // Tube / Water

    @Column(name = "data_points")
    private Integer dataPoints;

    @Column(name = "sample_interval")
    private Double sampleInterval;

    @Column(name = "unit")
    private String unit;

    @Column(name = "ni_name")
    private String niName;

    @Column(name = "file_source")
    private String fileSource;

    @Column(name = "last_updated")
    private LocalDateTime lastUpdated;

    public ChannelEntity() {}

    public ChannelEntity(Integer shotNo, String channelName, String dataType) {
        this.shotNo = shotNo;
        this.channelName = channelName;
        this.dataType = dataType;
        this.lastUpdated = LocalDateTime.now();
    }

    // getters and setters

    public Long getId() { return id; }
    public Integer getShotNo() { return shotNo; }
    public void setShotNo(Integer shotNo) { this.shotNo = shotNo; }
    public String getChannelName() { return channelName; }
    public void setChannelName(String channelName) { this.channelName = channelName; }
    public String getDataType() { return dataType; }
    public void setDataType(String dataType) { this.dataType = dataType; }
    public Integer getDataPoints() { return dataPoints; }
    public void setDataPoints(Integer dataPoints) { this.dataPoints = dataPoints; }
    public Double getSampleInterval() { return sampleInterval; }
    public void setSampleInterval(Double sampleInterval) { this.sampleInterval = sampleInterval; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getNiName() { return niName; }
    public void setNiName(String niName) { this.niName = niName; }
    public String getFileSource() { return fileSource; }
    public void setFileSource(String fileSource) { this.fileSource = fileSource; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(LocalDateTime lastUpdated) { this.lastUpdated = lastUpdated; }
}