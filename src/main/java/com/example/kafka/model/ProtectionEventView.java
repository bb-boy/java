package com.example.kafka.model;

import java.time.LocalDateTime;

public class ProtectionEventView {
    private Long eventId;
    private Integer shotNo;
    private String eventCode;
    private String eventName;
    private LocalDateTime eventTime;
    private String processId;
    private String messageText;
    private String messageLevel;
    private String severity;
    private String sourceSystem;
    private String authorityLevel;
    private String eventStatus;
    private String correlationKey;
    private String protectionTypeCode;
    private String protectionScope;
    private String triggerCondition;
    private Double measuredValue;
    private Double thresholdValue;
    private String thresholdOp;
    private String actionTaken;
    private LocalDateTime windowStart;
    private LocalDateTime windowEnd;
    private String relatedChannels;
    private Double evidenceScore;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Integer getShotNo() {
        return shotNo;
    }

    public void setShotNo(Integer shotNo) {
        this.shotNo = shotNo;
    }

    public String getEventCode() {
        return eventCode;
    }

    public void setEventCode(String eventCode) {
        this.eventCode = eventCode;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public LocalDateTime getEventTime() {
        return eventTime;
    }

    public void setEventTime(LocalDateTime eventTime) {
        this.eventTime = eventTime;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getMessageText() {
        return messageText;
    }

    public void setMessageText(String messageText) {
        this.messageText = messageText;
    }

    public String getMessageLevel() {
        return messageLevel;
    }

    public void setMessageLevel(String messageLevel) {
        this.messageLevel = messageLevel;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getAuthorityLevel() {
        return authorityLevel;
    }

    public void setAuthorityLevel(String authorityLevel) {
        this.authorityLevel = authorityLevel;
    }

    public String getEventStatus() {
        return eventStatus;
    }

    public void setEventStatus(String eventStatus) {
        this.eventStatus = eventStatus;
    }

    public String getCorrelationKey() {
        return correlationKey;
    }

    public void setCorrelationKey(String correlationKey) {
        this.correlationKey = correlationKey;
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
}
