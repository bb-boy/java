package com.example.kafka.service;

import com.example.kafka.entity.ShotEntity;
import com.example.kafka.entity.SignalCatalogEntity;
import com.example.kafka.entity.SignalSourceMapEntity;
import com.example.kafka.entity.SourceRecordEntity;
import com.example.kafka.entity.TdmsArtifactEntity;
import com.example.kafka.repository.ShotRepository;
import com.example.kafka.repository.SignalCatalogRepository;
import com.example.kafka.repository.SignalSourceMapRepository;
import com.example.kafka.repository.TdmsArtifactRepository;
import com.example.kafka.util.KafkaPayloadParser;
import com.example.kafka.util.PayloadValidator;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ArtifactProjectionService {
    @Autowired
    private TdmsArtifactRepository tdmsArtifactRepository;
    @Autowired
    private ShotRepository shotRepository;
    @Autowired
    private SignalCatalogRepository signalCatalogRepository;
    @Autowired
    private SignalSourceMapRepository signalSourceMapRepository;

    public void handleArtifact(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        TdmsArtifactEntity entity = new TdmsArtifactEntity();
        entity.setArtifactId(PayloadValidator.requireString(payload, "artifact_id"));
        entity.setShotNo(PayloadValidator.requireInt(payload, "shot_no"));
        entity.setDataType(PayloadValidator.requireString(payload, "data_type"));
        entity.setFilePath(PayloadValidator.requireString(payload, "file_path"));
        entity.setFileName(KafkaPayloadParser.asString(payload, "file_name"));
        entity.setFileSizeBytes(KafkaPayloadParser.asLong(payload, "file_size_bytes"));
        entity.setFileMtime(KafkaPayloadParser.asDateTime(payload, "file_mtime"));
        entity.setSha256Hex(PayloadValidator.requireString(payload, "sha256_hex"));
        entity.setArtifactStatus(PayloadValidator.requireString(payload, "artifact_status"));
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        tdmsArtifactRepository.save(entity);
    }

    public void handleShotMeta(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        Integer shotNo = PayloadValidator.requireInt(payload, "shot_no");
        ShotEntity entity = shotRepository.findById(shotNo).orElseGet(ShotEntity::new);
        entity.setShotNo(shotNo);
        entity.setShotStartTime(KafkaPayloadParser.asDateTime(payload, "shot_start_time"));
        entity.setShotEndTime(KafkaPayloadParser.asDateTime(payload, "shot_end_time"));
        entity.setExpectedDuration(KafkaPayloadParser.asDouble(payload, "expected_duration"));
        entity.setActualDuration(KafkaPayloadParser.asDouble(payload, "actual_duration"));
        entity.setStatusCode(KafkaPayloadParser.asString(payload, "status_code"));
        entity.setStatusReason(KafkaPayloadParser.asString(payload, "status_reason"));
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(LocalDateTime.now());
        }
        entity.setUpdatedAt(LocalDateTime.now());
        shotRepository.save(entity);
    }

    public void handleSignalCatalog(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        String processId = PayloadValidator.requireString(payload, "process_id");
        SignalCatalogEntity catalog = signalCatalogRepository.findById(processId)
            .orElseGet(SignalCatalogEntity::new);
        catalog.setProcessId(processId);
        catalog.setDisplayName(KafkaPayloadParser.asString(payload, "display_name"));
        catalog.setSignalClass(resolveSignalClass(payload));
        catalog.setUnit(KafkaPayloadParser.asString(payload, "unit"));
        catalog.setValueType(KafkaPayloadParser.asString(payload, "value_type"));
        catalog.setDeviceScope(KafkaPayloadParser.asString(payload, "device_scope"));
        catalog.setKeySignal(resolveKeySignal(payload));
        signalCatalogRepository.save(catalog);

        SignalSourceMapEntity mapping = resolveSignalMapping(payload);
        signalSourceMapRepository.save(mapping);
    }

    private SignalSourceMapEntity resolveSignalMapping(Map<String, Object> payload) {
        String sourceSystem = PayloadValidator.requireString(payload, "source_system");
        String sourceName = PayloadValidator.requireString(payload, "source_name");
        String dataType = KafkaPayloadParser.asString(payload, "data_type");
        String sourceType = KafkaPayloadParser.asString(payload, "source_type");
        Optional<SignalSourceMapEntity> existing = signalSourceMapRepository
            .findBySourceSystemAndSourceTypeAndSourceNameAndDataType(
                sourceSystem,
                sourceType,
                sourceName,
                dataType
            );
        SignalSourceMapEntity mapping = existing.orElseGet(SignalSourceMapEntity::new);
        mapping.setSourceSystem(sourceSystem);
        mapping.setSourceType(sourceType);
        mapping.setSourceName(sourceName);
        mapping.setDataType(dataType);
        mapping.setProcessId(PayloadValidator.requireString(payload, "process_id"));
        mapping.setPrimaryWaveform(resolveBoolean(payload, "is_primary_waveform"));
        mapping.setPrimaryOperationSignal(resolveBoolean(payload, "is_primary_operation_signal"));
        mapping.setPrimaryProtectionSignal(resolveBoolean(payload, "is_primary_protection_signal"));
        mapping.setActive(true);
        return mapping;
    }

    private String resolveSignalClass(Map<String, Object> payload) {
        String sourceType = KafkaPayloadParser.asString(payload, "source_type");
        return sourceType != null ? sourceType : KafkaPayloadParser.asString(payload, "data_type");
    }

    private Boolean resolveKeySignal(Map<String, Object> payload) {
        return resolveBoolean(payload, "is_primary_waveform")
            || resolveBoolean(payload, "is_primary_operation_signal")
            || resolveBoolean(payload, "is_primary_protection_signal");
    }

    private boolean resolveBoolean(Map<String, Object> payload, String key) {
        Boolean value = KafkaPayloadParser.asBoolean(payload, key);
        return value != null && value;
    }
}
