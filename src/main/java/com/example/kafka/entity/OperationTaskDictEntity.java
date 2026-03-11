package com.example.kafka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "operation_task_dict")
public class OperationTaskDictEntity {
    @Id
    @Column(name = "operation_task_code", nullable = false, length = 64)
    private String operationTaskCode;

    @Column(name = "iter_name", length = 128)
    private String iterName;

    @Column(name = "display_name_zh", length = 255)
    private String displayNameZh;

    @Column(name = "task_group", length = 128)
    private String taskGroup;

    @Column(name = "equipment_scope", length = 128)
    private String equipmentScope;

    @Column(name = "allowed_mode_hint", length = 255)
    private String allowedModeHint;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    public String getOperationTaskCode() {
        return operationTaskCode;
    }

    public void setOperationTaskCode(String operationTaskCode) {
        this.operationTaskCode = operationTaskCode;
    }

    public String getIterName() {
        return iterName;
    }

    public void setIterName(String iterName) {
        this.iterName = iterName;
    }

    public String getDisplayNameZh() {
        return displayNameZh;
    }

    public void setDisplayNameZh(String displayNameZh) {
        this.displayNameZh = displayNameZh;
    }

    public String getTaskGroup() {
        return taskGroup;
    }

    public void setTaskGroup(String taskGroup) {
        this.taskGroup = taskGroup;
    }

    public String getEquipmentScope() {
        return equipmentScope;
    }

    public void setEquipmentScope(String equipmentScope) {
        this.equipmentScope = equipmentScope;
    }

    public String getAllowedModeHint() {
        return allowedModeHint;
    }

    public void setAllowedModeHint(String allowedModeHint) {
        this.allowedModeHint = allowedModeHint;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
