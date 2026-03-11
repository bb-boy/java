package com.example.kafka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "event_operation_detail")
public class EventOperationDetailEntity {
    @Id
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "operation_type_code", length = 64)
    private String operationTypeCode;

    @Column(name = "operation_mode_code", length = 64)
    private String operationModeCode;

    @Column(name = "operation_task_code", length = 64)
    private String operationTaskCode;

    @Column(name = "channel_name", length = 128)
    private String channelName;

    @Column(name = "old_value")
    private Double oldValue;

    @Column(name = "new_value")
    private Double newValue;

    @Column(name = "delta_value")
    private Double deltaValue;

    @Column(name = "command_name", length = 128)
    private String commandName;

    @Column(name = "command_params_json", columnDefinition = "json")
    private String commandParamsJson;

    @Column(name = "operator_id", length = 64)
    private String operatorId;

    @Column(name = "execution_status", length = 32)
    private String executionStatus;

    @Column(name = "confidence")
    private Double confidence;

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public String getOperationTypeCode() {
        return operationTypeCode;
    }

    public void setOperationTypeCode(String operationTypeCode) {
        this.operationTypeCode = operationTypeCode;
    }

    public String getOperationModeCode() {
        return operationModeCode;
    }

    public void setOperationModeCode(String operationModeCode) {
        this.operationModeCode = operationModeCode;
    }

    public String getOperationTaskCode() {
        return operationTaskCode;
    }

    public void setOperationTaskCode(String operationTaskCode) {
        this.operationTaskCode = operationTaskCode;
    }

    public String getChannelName() {
        return channelName;
    }

    public void setChannelName(String channelName) {
        this.channelName = channelName;
    }

    public Double getOldValue() {
        return oldValue;
    }

    public void setOldValue(Double oldValue) {
        this.oldValue = oldValue;
    }

    public Double getNewValue() {
        return newValue;
    }

    public void setNewValue(Double newValue) {
        this.newValue = newValue;
    }

    public Double getDeltaValue() {
        return deltaValue;
    }

    public void setDeltaValue(Double deltaValue) {
        this.deltaValue = deltaValue;
    }

    public String getCommandName() {
        return commandName;
    }

    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }

    public String getCommandParamsJson() {
        return commandParamsJson;
    }

    public void setCommandParamsJson(String commandParamsJson) {
        this.commandParamsJson = commandParamsJson;
    }

    public String getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(String operatorId) {
        this.operatorId = operatorId;
    }

    public String getExecutionStatus() {
        return executionStatus;
    }

    public void setExecutionStatus(String executionStatus) {
        this.executionStatus = executionStatus;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }
}
