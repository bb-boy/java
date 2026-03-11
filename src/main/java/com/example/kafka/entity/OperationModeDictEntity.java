package com.example.kafka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "operation_mode_dict")
public class OperationModeDictEntity {
    @Id
    @Column(name = "operation_mode_code", nullable = false, length = 64)
    private String operationModeCode;

    @Column(name = "display_name_zh", length = 255)
    private String displayNameZh;

    @Column(name = "scope_note", length = 255)
    private String scopeNote;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    public String getOperationModeCode() {
        return operationModeCode;
    }

    public void setOperationModeCode(String operationModeCode) {
        this.operationModeCode = operationModeCode;
    }

    public String getDisplayNameZh() {
        return displayNameZh;
    }

    public void setDisplayNameZh(String displayNameZh) {
        this.displayNameZh = displayNameZh;
    }

    public String getScopeNote() {
        return scopeNote;
    }

    public void setScopeNote(String scopeNote) {
        this.scopeNote = scopeNote;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
