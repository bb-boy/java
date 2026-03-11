package com.example.kafka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "signal_catalog")
public class SignalCatalogEntity {
    @Id
    @Column(name = "process_id", nullable = false, length = 128)
    private String processId;

    @Column(name = "display_name", length = 255)
    private String displayName;

    @Column(name = "signal_class", length = 64)
    private String signalClass;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "value_type", length = 32)
    private String valueType;

    @Column(name = "device_scope", length = 64)
    private String deviceScope;

    @Column(name = "is_key_signal", nullable = false)
    private Boolean keySignal;

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getSignalClass() {
        return signalClass;
    }

    public void setSignalClass(String signalClass) {
        this.signalClass = signalClass;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public String getDeviceScope() {
        return deviceScope;
    }

    public void setDeviceScope(String deviceScope) {
        this.deviceScope = deviceScope;
    }

    public Boolean getKeySignal() {
        return keySignal;
    }

    public void setKeySignal(Boolean keySignal) {
        this.keySignal = keySignal;
    }
}
