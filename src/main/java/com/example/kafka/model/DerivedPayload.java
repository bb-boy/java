package com.example.kafka.model;

import java.util.List;
import java.util.Map;

public class DerivedPayload {
    private List<Map<String, Object>> artifacts = List.of();
    private Map<String, Object> shotMeta = Map.of();
    private List<Map<String, Object>> signalCatalog = List.of();
    private List<Map<String, Object>> operationEvents = List.of();
    private List<Map<String, Object>> protectionEvents = List.of();
    private Map<String, Object> waveformRequest = Map.of();
    private List<Map<String, Object>> waveformBatches = List.of();

    public List<Map<String, Object>> getArtifacts() {
        return artifacts;
    }

    public void setArtifacts(List<Map<String, Object>> artifacts) {
        this.artifacts = artifacts;
    }

    public Map<String, Object> getShotMeta() {
        return shotMeta;
    }

    public void setShotMeta(Map<String, Object> shotMeta) {
        this.shotMeta = shotMeta;
    }

    public List<Map<String, Object>> getSignalCatalog() {
        return signalCatalog;
    }

    public void setSignalCatalog(List<Map<String, Object>> signalCatalog) {
        this.signalCatalog = signalCatalog;
    }

    public List<Map<String, Object>> getOperationEvents() {
        return operationEvents;
    }

    public void setOperationEvents(List<Map<String, Object>> operationEvents) {
        this.operationEvents = operationEvents;
    }

    public List<Map<String, Object>> getProtectionEvents() {
        return protectionEvents;
    }

    public void setProtectionEvents(List<Map<String, Object>> protectionEvents) {
        this.protectionEvents = protectionEvents;
    }

    public Map<String, Object> getWaveformRequest() {
        return waveformRequest;
    }

    public void setWaveformRequest(Map<String, Object> waveformRequest) {
        this.waveformRequest = waveformRequest;
    }

    public List<Map<String, Object>> getWaveformBatches() {
        return waveformBatches;
    }

    public void setWaveformBatches(List<Map<String, Object>> waveformBatches) {
        this.waveformBatches = waveformBatches;
    }
}
