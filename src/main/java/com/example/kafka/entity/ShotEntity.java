package com.example.kafka.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "shots")
public class ShotEntity {
    @Id
    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;

    @Column(name = "shot_start_time")
    private LocalDateTime shotStartTime;

    @Column(name = "shot_end_time")
    private LocalDateTime shotEndTime;

    @Column(name = "expected_duration")
    private Double expectedDuration;

    @Column(name = "actual_duration")
    private Double actualDuration;

    @Column(name = "status_code", length = 32)
    private String statusCode;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Integer getShotNo() {
        return shotNo;
    }

    public void setShotNo(Integer shotNo) {
        this.shotNo = shotNo;
    }

    public LocalDateTime getShotStartTime() {
        return shotStartTime;
    }

    public void setShotStartTime(LocalDateTime shotStartTime) {
        this.shotStartTime = shotStartTime;
    }

    public LocalDateTime getShotEndTime() {
        return shotEndTime;
    }

    public void setShotEndTime(LocalDateTime shotEndTime) {
        this.shotEndTime = shotEndTime;
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

    public String getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(String statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
