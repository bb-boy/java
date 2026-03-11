package com.example.kafka.entity;

import java.time.LocalDateTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "event_protection_detail")
public class EventProtectionDetailEntity {
    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "protection_type_code", length = 64)
    private String protectionTypeCode;

    @Column(name = "protection_scope", length = 64)
    private String protectionScope;

    @Column(name = "trigger_condition", length = 255)
    private String triggerCondition;

    @Column(name = "measured_value")
    private Double measuredValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(name = "threshold_op", length = 16)
    private String thresholdOp;

    @Column(name = "action_taken", length = 128)
    private String actionTaken;

    @Column(name = "action_latency_us")
    private Long actionLatencyUs;

    @Column(name = "window_start")
    private LocalDateTime windowStart;

    @Column(name = "window_end")
    private LocalDateTime windowEnd;

    @Column(name = "related_channels", columnDefinition = "json")
    private String relatedChannels;

    @Column(name = "evidence_score")
    private Double evidenceScore;

    @Column(name = "ack_state", length = 32)
    private String ackState;

    @Column(name = "ack_user_id", length = 64)
    private String ackUserId;

    @Column(name = "ack_time")
    private LocalDateTime ackTime;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getProtectionTypeCode() {
        return protectionTypeCode;
    }

    public void setProtectionTypeCode(String protectionTypeCode) {
        this.protectionTypeCode = protectionTypeCode;
    }

    public String getProtectionScope() {
        return protectionScope;
    }

    public void setProtectionScope(String protectionScope) {
        this.protectionScope = protectionScope;
    }

    public String getTriggerCondition() {
        return triggerCondition;
    }

    public void setTriggerCondition(String triggerCondition) {
        this.triggerCondition = triggerCondition;
    }

    public Double getMeasuredValue() {
        return measuredValue;
    }

    public void setMeasuredValue(Double measuredValue) {
        this.measuredValue = measuredValue;
    }

    public Double getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(Double thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public String getThresholdOp() {
        return thresholdOp;
    }

    public void setThresholdOp(String thresholdOp) {
        this.thresholdOp = thresholdOp;
    }

    public String getActionTaken() {
        return actionTaken;
    }

    public void setActionTaken(String actionTaken) {
        this.actionTaken = actionTaken;
    }

    public Long getActionLatencyUs() {
        return actionLatencyUs;
    }

    public void setActionLatencyUs(Long actionLatencyUs) {
        this.actionLatencyUs = actionLatencyUs;
    }

    public LocalDateTime getWindowStart() {
        return windowStart;
    }

    public void setWindowStart(LocalDateTime windowStart) {
        this.windowStart = windowStart;
    }

    public LocalDateTime getWindowEnd() {
        return windowEnd;
    }

    public void setWindowEnd(LocalDateTime windowEnd) {
        this.windowEnd = windowEnd;
    }

    public String getRelatedChannels() {
        return relatedChannels;
    }

    public void setRelatedChannels(String relatedChannels) {
        this.relatedChannels = relatedChannels;
    }

    public Double getEvidenceScore() {
        return evidenceScore;
    }

    public void setEvidenceScore(Double evidenceScore) {
        this.evidenceScore = evidenceScore;
    }

    public String getAckState() {
        return ackState;
    }

    public void setAckState(String ackState) {
        this.ackState = ackState;
    }

    public String getAckUserId() {
        return ackUserId;
    }

    public void setAckUserId(String ackUserId) {
        this.ackUserId = ackUserId;
    }

    public LocalDateTime getAckTime() {
        return ackTime;
    }

    public void setAckTime(LocalDateTime ackTime) {
        this.ackTime = ackTime;
    }
}
