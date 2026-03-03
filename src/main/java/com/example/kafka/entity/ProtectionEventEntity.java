package com.example.kafka.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 保护事件实体 - 记录保护/互锁触发与动作
 */
@Entity
@Table(name = "protection_events", indexes = {
    @Index(name = "idx_pe_shot_time", columnList = "shot_no, trigger_time"),
    @Index(name = "idx_pe_device_time", columnList = "device_id, trigger_time"),
    @Index(name = "idx_pe_name_time", columnList = "interlock_name, trigger_time"),
    @Index(name = "idx_pe_severity_time", columnList = "severity, trigger_time")
})
public class ProtectionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long eventId;

    @Column(name = "shot_no", nullable = false)
    private Integer shotNo;

    @Column(name = "trigger_time", nullable = false)
    private LocalDateTime triggerTime;

    @Column(name = "device_id")
    private String deviceId;

    @Column(name = "severity", nullable = false)
    private String severity;

    @Column(name = "protection_level", nullable = false)
    private String protectionLevel;

    @Column(name = "interlock_name", nullable = false)
    private String interlockName;

    @Column(name = "trigger_condition")
    private String triggerCondition;

    @Column(name = "measured_value")
    private Double measuredValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "threshold_op")
    private String thresholdOp;

    @Column(name = "action_taken")
    private String actionTaken;

    @Column(name = "action_latency_us")
    private Long actionLatencyUs;

    @Column(name = "ack_user_id")
    private Long ackUserId;

    @Column(name = "ack_time")
    private LocalDateTime ackTime;

    @Column(name = "related_waveform_id")
    private Long relatedWaveformId;

    @Column(name = "window_start")
    private LocalDateTime windowStart;

    @Column(name = "window_end")
    private LocalDateTime windowEnd;

    @Lob
    @Column(name = "related_channels", columnDefinition = "TEXT")
    private String relatedChannels;

    @Lob
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public Integer getShotNo() { return shotNo; }
    public void setShotNo(Integer shotNo) { this.shotNo = shotNo; }

    public LocalDateTime getTriggerTime() { return triggerTime; }
    public void setTriggerTime(LocalDateTime triggerTime) { this.triggerTime = triggerTime; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getProtectionLevel() { return protectionLevel; }
    public void setProtectionLevel(String protectionLevel) { this.protectionLevel = protectionLevel; }

    public String getInterlockName() { return interlockName; }
    public void setInterlockName(String interlockName) { this.interlockName = interlockName; }

    public String getTriggerCondition() { return triggerCondition; }
    public void setTriggerCondition(String triggerCondition) { this.triggerCondition = triggerCondition; }

    public Double getMeasuredValue() { return measuredValue; }
    public void setMeasuredValue(Double measuredValue) { this.measuredValue = measuredValue; }

    public Double getThresholdValue() { return thresholdValue; }
    public void setThresholdValue(Double thresholdValue) { this.thresholdValue = thresholdValue; }

    public String getThresholdOp() { return thresholdOp; }
    public void setThresholdOp(String thresholdOp) { this.thresholdOp = thresholdOp; }

    public String getActionTaken() { return actionTaken; }
    public void setActionTaken(String actionTaken) { this.actionTaken = actionTaken; }

    public Long getActionLatencyUs() { return actionLatencyUs; }
    public void setActionLatencyUs(Long actionLatencyUs) { this.actionLatencyUs = actionLatencyUs; }

    public Long getAckUserId() { return ackUserId; }
    public void setAckUserId(Long ackUserId) { this.ackUserId = ackUserId; }

    public LocalDateTime getAckTime() { return ackTime; }
    public void setAckTime(LocalDateTime ackTime) { this.ackTime = ackTime; }

    public Long getRelatedWaveformId() { return relatedWaveformId; }
    public void setRelatedWaveformId(Long relatedWaveformId) { this.relatedWaveformId = relatedWaveformId; }

    public LocalDateTime getWindowStart() { return windowStart; }
    public void setWindowStart(LocalDateTime windowStart) { this.windowStart = windowStart; }

    public LocalDateTime getWindowEnd() { return windowEnd; }
    public void setWindowEnd(LocalDateTime windowEnd) { this.windowEnd = windowEnd; }

    public String getRelatedChannels() { return relatedChannels; }
    public void setRelatedChannels(String relatedChannels) { this.relatedChannels = relatedChannels; }

    public String getRawPayload() { return rawPayload; }
    public void setRawPayload(String rawPayload) { this.rawPayload = rawPayload; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
