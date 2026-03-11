package com.example.kafka.service;

import com.example.kafka.entity.OperationModeDictEntity;
import com.example.kafka.entity.OperationTaskDictEntity;
import com.example.kafka.entity.OperationTypeDictEntity;
import com.example.kafka.entity.ProtectionTypeDictEntity;
import com.example.kafka.entity.SourceRecordEntity;
import com.example.kafka.repository.OperationModeDictRepository;
import com.example.kafka.repository.OperationTaskDictRepository;
import com.example.kafka.repository.OperationTypeDictRepository;
import com.example.kafka.repository.ProtectionTypeDictRepository;
import com.example.kafka.util.KafkaPayloadParser;
import com.example.kafka.util.PayloadValidator;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class DictProjectionService {
    private final ProtectionTypeDictRepository protectionTypeRepository;
    private final OperationModeDictRepository operationModeRepository;
    private final OperationTaskDictRepository operationTaskRepository;
    private final OperationTypeDictRepository operationTypeRepository;

    public DictProjectionService(
        ProtectionTypeDictRepository protectionTypeRepository,
        OperationModeDictRepository operationModeRepository,
        OperationTaskDictRepository operationTaskRepository,
        OperationTypeDictRepository operationTypeRepository
    ) {
        this.protectionTypeRepository = protectionTypeRepository;
        this.operationModeRepository = operationModeRepository;
        this.operationTaskRepository = operationTaskRepository;
        this.operationTypeRepository = operationTypeRepository;
    }

    public void handleProtectionType(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        String code = PayloadValidator.requireString(payload, "protection_type_code");
        ProtectionTypeDictEntity entity = protectionTypeRepository.findById(code)
            .orElseGet(ProtectionTypeDictEntity::new);
        entity.setProtectionTypeCode(code);
        entity.setIterName(KafkaPayloadParser.asString(payload, "iter_name"));
        entity.setDisplayNameZh(KafkaPayloadParser.asString(payload, "display_name_zh"));
        entity.setProtectionGroup(KafkaPayloadParser.asString(payload, "protection_group"));
        entity.setEquipmentScope(KafkaPayloadParser.asString(payload, "equipment_scope"));
        entity.setDetectionMechanism(KafkaPayloadParser.asString(payload, "detection_mechanism"));
        entity.setDefaultSeverity(KafkaPayloadParser.asString(payload, "default_severity"));
        entity.setDefaultAction(KafkaPayloadParser.asString(payload, "default_action"));
        entity.setAuthorityDefault(KafkaPayloadParser.asString(payload, "authority_default"));
        entity.setActive(PayloadValidator.requireBoolean(payload, "is_active"));
        protectionTypeRepository.save(entity);
    }

    public void handleOperationMode(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        String code = PayloadValidator.requireString(payload, "operation_mode_code");
        OperationModeDictEntity entity = operationModeRepository.findById(code)
            .orElseGet(OperationModeDictEntity::new);
        entity.setOperationModeCode(code);
        entity.setDisplayNameZh(KafkaPayloadParser.asString(payload, "display_name_zh"));
        entity.setScopeNote(KafkaPayloadParser.asString(payload, "scope_note"));
        entity.setActive(PayloadValidator.requireBoolean(payload, "is_active"));
        operationModeRepository.save(entity);
    }

    public void handleOperationTask(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        String code = PayloadValidator.requireString(payload, "operation_task_code");
        OperationTaskDictEntity entity = operationTaskRepository.findById(code)
            .orElseGet(OperationTaskDictEntity::new);
        entity.setOperationTaskCode(code);
        entity.setIterName(KafkaPayloadParser.asString(payload, "iter_name"));
        entity.setDisplayNameZh(KafkaPayloadParser.asString(payload, "display_name_zh"));
        entity.setTaskGroup(KafkaPayloadParser.asString(payload, "task_group"));
        entity.setEquipmentScope(KafkaPayloadParser.asString(payload, "equipment_scope"));
        entity.setAllowedModeHint(KafkaPayloadParser.asString(payload, "allowed_mode_hint"));
        entity.setActive(PayloadValidator.requireBoolean(payload, "is_active"));
        operationTaskRepository.save(entity);
    }

    public void handleOperationType(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        String code = PayloadValidator.requireString(payload, "operation_type_code");
        OperationTypeDictEntity entity = operationTypeRepository.findById(code)
            .orElseGet(OperationTypeDictEntity::new);
        entity.setOperationTypeCode(code);
        entity.setDisplayNameZh(KafkaPayloadParser.asString(payload, "display_name_zh"));
        entity.setOperationGroup(KafkaPayloadParser.asString(payload, "operation_group"));
        entity.setIterControlFunction(KafkaPayloadParser.asString(payload, "iter_control_function"));
        entity.setRequiresOldNew(PayloadValidator.requireBoolean(payload, "requires_old_new"));
        entity.setRequiresTaskCode(PayloadValidator.requireBoolean(payload, "requires_task_code"));
        entity.setRequiresModeCode(PayloadValidator.requireBoolean(payload, "requires_mode_code"));
        entity.setActive(PayloadValidator.requireBoolean(payload, "is_active"));
        operationTypeRepository.save(entity);
    }
}
