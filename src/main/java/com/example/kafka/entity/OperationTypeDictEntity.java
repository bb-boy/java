package com.example.kafka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "operation_type_dict")
public class OperationTypeDictEntity {
    @Id
    @Column(name = "operation_type_code", nullable = false, length = 64)
    private String operationTypeCode;

    @Column(name = "display_name_zh", length = 255)
    private String displayNameZh;

    @Column(name = "operation_group", length = 128)
    private String operationGroup;

    @Column(name = "iter_control_function", length = 128)
    private String iterControlFunction;

    @Column(name = "requires_old_new", nullable = false)
    private Boolean requiresOldNew;

    @Column(name = "requires_task_code", nullable = false)
    private Boolean requiresTaskCode;

    @Column(name = "requires_mode_code", nullable = false)
    private Boolean requiresModeCode;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    public String getOperationTypeCode() {
        return operationTypeCode;
    }

    public void setOperationTypeCode(String operationTypeCode) {
        this.operationTypeCode = operationTypeCode;
    }

    public String getDisplayNameZh() {
        return displayNameZh;
    }

    public void setDisplayNameZh(String displayNameZh) {
        this.displayNameZh = displayNameZh;
    }

    public String getOperationGroup() {
        return operationGroup;
    }

    public void setOperationGroup(String operationGroup) {
        this.operationGroup = operationGroup;
    }

    public String getIterControlFunction() {
        return iterControlFunction;
    }

    public void setIterControlFunction(String iterControlFunction) {
        this.iterControlFunction = iterControlFunction;
    }

    public Boolean getRequiresOldNew() {
        return requiresOldNew;
    }

    public void setRequiresOldNew(Boolean requiresOldNew) {
        this.requiresOldNew = requiresOldNew;
    }

    public Boolean getRequiresTaskCode() {
        return requiresTaskCode;
    }

    public void setRequiresTaskCode(Boolean requiresTaskCode) {
        this.requiresTaskCode = requiresTaskCode;
    }

    public Boolean getRequiresModeCode() {
        return requiresModeCode;
    }

    public void setRequiresModeCode(Boolean requiresModeCode) {
        this.requiresModeCode = requiresModeCode;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
