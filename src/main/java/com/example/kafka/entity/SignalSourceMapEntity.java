package com.example.kafka.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "signal_source_map",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_signal_source",
        columnNames = {"source_system", "source_type", "source_name", "data_type"}
    )
)
public class SignalSourceMapEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "map_id")
    private Long mapId;

    @Column(name = "source_system", nullable = false, length = 64)
    private String sourceSystem;

    @Column(name = "source_type", length = 64)
    private String sourceType;

    @Column(name = "source_name", nullable = false, length = 128)
    private String sourceName;

    @Column(name = "process_id", nullable = false, length = 128)
    private String processId;

    @Column(name = "data_type", length = 32)
    private String dataType;

    @Column(name = "is_primary_waveform", nullable = false)
    private Boolean primaryWaveform;

    @Column(name = "is_primary_operation_signal", nullable = false)
    private Boolean primaryOperationSignal;

    @Column(name = "is_primary_protection_signal", nullable = false)
    private Boolean primaryProtectionSignal;

    @Column(name = "is_active", nullable = false)
    private Boolean active;

    public Long getMapId() {
        return mapId;
    }

    public void setMapId(Long mapId) {
        this.mapId = mapId;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public String getProcessId() {
        return processId;
    }

    public void setProcessId(String processId) {
        this.processId = processId;
    }

    public String getDataType() {
        return dataType;
    }

    public void setDataType(String dataType) {
        this.dataType = dataType;
    }

    public Boolean getPrimaryWaveform() {
        return primaryWaveform;
    }

    public void setPrimaryWaveform(Boolean primaryWaveform) {
        this.primaryWaveform = primaryWaveform;
    }

    public Boolean getPrimaryOperationSignal() {
        return primaryOperationSignal;
    }

    public void setPrimaryOperationSignal(Boolean primaryOperationSignal) {
        this.primaryOperationSignal = primaryOperationSignal;
    }

    public Boolean getPrimaryProtectionSignal() {
        return primaryProtectionSignal;
    }

    public void setPrimaryProtectionSignal(Boolean primaryProtectionSignal) {
        this.primaryProtectionSignal = primaryProtectionSignal;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
