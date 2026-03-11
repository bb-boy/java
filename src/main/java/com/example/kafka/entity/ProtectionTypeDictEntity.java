package com.example.kafka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "protection_type_dict")
public class ProtectionTypeDictEntity {
    @Id
    @Column(name = "protection_type_code", nullable = false, length = 64)
    private String protectionTypeCode;

    @Column(name = "iter_name", length = 128)
    private String iterName;

    @Column(name = "display_name_zh", length = 255)
    private String displayNameZh;

    @Column(name = "protection_group", length = 128)
    private String protectionGroup;

    @Column(name = "equipment_scope", length = 128)
    private String equipmentScope;

    @Column(name = "detection_mechanism", length = 128)
    private String detectionMechanism;

    @Column(name = "default_severity", length = 32)
    private String defaultSeverity;

    @Column(name = "default_action", length = 128)
    private String defaultAction;

    @Column(name = "authority_default", length = 32)
    private String authorityDefault;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    public String getProtectionTypeCode() {
        return protectionTypeCode;
    }

    public void setProtectionTypeCode(String protectionTypeCode) {
        this.protectionTypeCode = protectionTypeCode;
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

    public String getProtectionGroup() {
        return protectionGroup;
    }

    public void setProtectionGroup(String protectionGroup) {
        this.protectionGroup = protectionGroup;
    }

    public String getEquipmentScope() {
        return equipmentScope;
    }

    public void setEquipmentScope(String equipmentScope) {
        this.equipmentScope = equipmentScope;
    }

    public String getDetectionMechanism() {
        return detectionMechanism;
    }

    public void setDetectionMechanism(String detectionMechanism) {
        this.detectionMechanism = detectionMechanism;
    }

    public String getDefaultSeverity() {
        return defaultSeverity;
    }

    public void setDefaultSeverity(String defaultSeverity) {
        this.defaultSeverity = defaultSeverity;
    }

    public String getDefaultAction() {
        return defaultAction;
    }

    public void setDefaultAction(String defaultAction) {
        this.defaultAction = defaultAction;
    }

    public String getAuthorityDefault() {
        return authorityDefault;
    }

    public void setAuthorityDefault(String authorityDefault) {
        this.authorityDefault = authorityDefault;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
