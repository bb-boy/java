package com.example.kafka.service;

import com.example.kafka.entity.SourceRecordEntity;
import com.example.kafka.entity.TdmsArtifactEntity;
import com.example.kafka.model.WaveformBatch;
import com.example.kafka.repository.TdmsArtifactRepository;
import com.example.kafka.util.EventConstants;
import com.example.kafka.util.KafkaPayloadParser;
import com.example.kafka.util.PayloadValidator;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WaveformProjectionService {
    private final TdmsArtifactRepository tdmsArtifactRepository;
    private final WaveformInfluxWriter waveformInfluxWriter;

    public WaveformProjectionService(
        TdmsArtifactRepository tdmsArtifactRepository,
        WaveformInfluxWriter waveformInfluxWriter
    ) {
        this.tdmsArtifactRepository = tdmsArtifactRepository;
        this.waveformInfluxWriter = waveformInfluxWriter;
    }

    public void handleWaveformRequest(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        Object signalsObj = payload.get("signals");
        if (!(signalsObj instanceof List)) {
            throw new IllegalArgumentException("signals must be an array");
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> signals = (List<Map<String, Object>>) signalsObj;
        for (Map<String, Object> signal : signals) {
            upsertWaveformStatus(signal);
        }
    }

    private void upsertWaveformStatus(Map<String, Object> signal) {
        String artifactId = PayloadValidator.requireString(signal, "artifact_id");
        TdmsArtifactEntity entity = tdmsArtifactRepository.findById(artifactId)
            .orElseGet(TdmsArtifactEntity::new);
        entity.setArtifactId(artifactId);
        entity.setShotNo(PayloadValidator.requireInt(signal, "shot_no"));
        entity.setDataType(PayloadValidator.requireString(signal, "data_type"));
        entity.setFilePath(PayloadValidator.requireString(signal, "file_path"));
        entity.setArtifactStatus(EventConstants.ARTIFACT_STATUS_PARSED);
        entity.setWaveformIngestStatus(EventConstants.WAVEFORM_STATUS_REQUESTED);
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        tdmsArtifactRepository.save(entity);
    }

    public void handleWaveformBatch(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        WaveformBatch batch = buildWaveformBatch(payload);
        waveformInfluxWriter.writeBatch(batch);
        updateWaveformStatus(batch.getArtifactId(), EventConstants.WAVEFORM_STATUS_INGESTED);
    }

    private WaveformBatch buildWaveformBatch(Map<String, Object> payload) {
        String encoding = PayloadValidator.requireString(payload, "encoding");
        List<Double> samples = parseSamples(payload.get("samples"), encoding);
        WaveformBatch batch = new WaveformBatch();
        batch.setArtifactId(PayloadValidator.requireString(payload, "artifact_id"));
        batch.setShotNo(PayloadValidator.requireInt(payload, "shot_no"));
        batch.setDataType(KafkaPayloadParser.asString(payload, "data_type"));
        batch.setSourceSystem(PayloadValidator.requireString(payload, "source_system"));
        batch.setChannelName(PayloadValidator.requireString(payload, "channel_name"));
        batch.setProcessId(PayloadValidator.requireString(payload, "process_id"));
        batch.setSampleRateHz(PayloadValidator.requireDouble(payload, "sample_rate_hz"));
        batch.setChunkIndex(PayloadValidator.requireInt(payload, "chunk_index"));
        batch.setChunkCount(PayloadValidator.requireInt(payload, "chunk_count"));
        batch.setChunkStartIndex(PayloadValidator.requireInt(payload, "chunk_start_index"));
        batch.setWindowStart(PayloadValidator.requireInstant(payload, "window_start"));
        batch.setWindowEnd(KafkaPayloadParser.asInstant(payload, "window_end"));
        batch.setEncoding(encoding);
        batch.setSamples(samples);
        return batch;
    }

    private List<Double> parseSamples(Object samplesObj, String encoding) {
        if (!(samplesObj instanceof List)) {
            throw new IllegalArgumentException("samples must be an array");
        }
        if (!"raw".equalsIgnoreCase(encoding)) {
            throw new IllegalArgumentException("Only raw encoding is supported");
        }
        @SuppressWarnings("unchecked")
        List<Object> rawSamples = (List<Object>) samplesObj;
        return rawSamples.stream().map(this::toDouble).toList();
    }

    private Double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            return Double.parseDouble(text);
        }
        throw new IllegalArgumentException("Invalid sample value: " + value);
    }

    private void updateWaveformStatus(String artifactId, String status) {
        if (artifactId == null) {
            return;
        }
        tdmsArtifactRepository.findById(artifactId).ifPresent(entity -> {
            entity.setWaveformIngestStatus(status);
            entity.setUpdatedAt(LocalDateTime.now());
            tdmsArtifactRepository.save(entity);
        });
    }
}
