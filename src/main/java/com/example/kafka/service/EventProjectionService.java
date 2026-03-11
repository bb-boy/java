package com.example.kafka.service;

import com.example.kafka.entity.EventEntity;
import com.example.kafka.entity.EventOperationDetailEntity;
import com.example.kafka.entity.EventProtectionDetailEntity;
import com.example.kafka.entity.SourceRecordEntity;
import com.example.kafka.repository.EventOperationDetailRepository;
import com.example.kafka.repository.EventProtectionDetailRepository;
import com.example.kafka.repository.EventRepository;
import com.example.kafka.util.DedupKeyBuilder;
import com.example.kafka.util.EventConstants;
import com.example.kafka.util.KafkaPayloadParser;
import com.example.kafka.util.PayloadValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class EventProjectionService {
    private static final String MESSAGE_LEVEL_INFO = "INFO";

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private EventOperationDetailRepository eventOperationDetailRepository;
    @Autowired
    private EventProtectionDetailRepository eventProtectionDetailRepository;
    @Autowired
    private ObjectMapper objectMapper;

    public void handleOperationEvent(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        EventEntity event = buildEventEntity(sourceRecord, payload);
        if (eventRepository.findByDedupKey(event.getDedupKey()).isPresent()) {
            return;
        }
        EventEntity saved = eventRepository.save(event);
        EventOperationDetailEntity detail = buildOperationDetail(saved.getEventId(), payload);
        eventOperationDetailRepository.save(detail);
    }

    public void handleProtectionEvent(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        EventEntity event = buildEventEntity(sourceRecord, payload);
        if (eventRepository.findByDedupKey(event.getDedupKey()).isPresent()) {
            return;
        }
        EventEntity saved = eventRepository.save(event);
        EventProtectionDetailEntity detail = buildProtectionDetail(saved.getEventId(), payload);
        eventProtectionDetailRepository.save(detail);
    }

    private EventEntity buildEventEntity(SourceRecordEntity sourceRecord, Map<String, Object> payload) {
        String sourceSystem = PayloadValidator.requireString(payload, "source_system");
        String eventFamily = PayloadValidator.requireString(payload, "event_family");
        Integer shotNo = PayloadValidator.requireInt(payload, "shot_no");
        LocalDateTime eventTime = PayloadValidator.requireDateTime(payload, "event_time");
        DedupKeyContext context = new DedupKeyContext();
        context.setPayload(payload);
        context.setSourceRecord(sourceRecord);
        context.setSourceSystem(sourceSystem);
        context.setEventFamily(eventFamily);
        context.setShotNo(shotNo);
        context.setEventTime(eventTime);
        String dedupKey = resolveDedupKey(context);
        EventEntity entity = new EventEntity();
        entity.setShotNo(shotNo);
        entity.setSourceRecordId(sourceRecord.getSourceRecordId());
        entity.setArtifactId(KafkaPayloadParser.asString(payload, "artifact_id"));
        entity.setEventFamily(eventFamily);
        entity.setEventCode(KafkaPayloadParser.asString(payload, "event_code"));
        entity.setEventName(resolveEventName(payload));
        entity.setEventTime(eventTime);
        entity.setProcessId(KafkaPayloadParser.asString(payload, "process_id"));
        entity.setMessageText(KafkaPayloadParser.asString(payload, "message_text"));
        entity.setMessageLevel(resolveMessageLevel(payload));
        entity.setSeverity(KafkaPayloadParser.asString(payload, "severity"));
        entity.setSourceSystem(sourceSystem);
        entity.setAuthorityLevel(KafkaPayloadParser.asString(payload, "authority_level"));
        entity.setEventStatus(EventConstants.EVENT_STATUS_ACTIVE);
        entity.setCorrelationKey(KafkaPayloadParser.asString(payload, "correlation_key"));
        entity.setDedupKey(dedupKey);
        entity.setRawPayloadJson(sourceRecord.getRawPayloadJson());
        entity.setCreatedAt(LocalDateTime.now());
        return entity;
    }

    private EventOperationDetailEntity buildOperationDetail(Long eventId, Map<String, Object> payload) {
        Map<String, Object> details = KafkaPayloadParser.asMap(payload, "details");
        EventOperationDetailEntity entity = new EventOperationDetailEntity();
        entity.setEventId(eventId);
        if (details == null) {
            return entity;
        }
        entity.setOperationTypeCode(KafkaPayloadParser.asString(details, "operation_type_code"));
        entity.setOperationModeCode(KafkaPayloadParser.asString(details, "operation_mode_code"));
        entity.setOperationTaskCode(KafkaPayloadParser.asString(details, "operation_task_code"));
        entity.setChannelName(resolveChannelName(payload, details));
        entity.setOldValue(KafkaPayloadParser.asDouble(details, "old_value"));
        entity.setNewValue(KafkaPayloadParser.asDouble(details, "new_value"));
        entity.setDeltaValue(KafkaPayloadParser.asDouble(details, "delta_value"));
        entity.setCommandName(KafkaPayloadParser.asString(details, "command_name"));
        entity.setCommandParamsJson(stringify(details.get("command_params")));
        entity.setOperatorId(KafkaPayloadParser.asString(details, "operator_id"));
        entity.setExecutionStatus(KafkaPayloadParser.asString(details, "execution_status"));
        entity.setConfidence(KafkaPayloadParser.asDouble(details, "confidence"));
        return entity;
    }

    private EventProtectionDetailEntity buildProtectionDetail(Long eventId, Map<String, Object> payload) {
        Map<String, Object> details = KafkaPayloadParser.asMap(payload, "details");
        EventProtectionDetailEntity entity = new EventProtectionDetailEntity();
        entity.setEventId(eventId);
        if (details == null) {
            return entity;
        }
        entity.setProtectionTypeCode(KafkaPayloadParser.asString(details, "protection_type_code"));
        entity.setProtectionScope(KafkaPayloadParser.asString(details, "protection_scope"));
        entity.setTriggerCondition(KafkaPayloadParser.asString(details, "trigger_condition"));
        entity.setMeasuredValue(KafkaPayloadParser.asDouble(details, "measured_value"));
        entity.setThresholdValue(KafkaPayloadParser.asDouble(details, "threshold_value"));
        entity.setThresholdOp(KafkaPayloadParser.asString(details, "threshold_op"));
        entity.setActionTaken(KafkaPayloadParser.asString(details, "action_taken"));
        entity.setWindowStart(KafkaPayloadParser.asDateTime(details, "window_start"));
        entity.setWindowEnd(KafkaPayloadParser.asDateTime(details, "window_end"));
        entity.setRelatedChannels(stringify(details.get("related_channels")));
        entity.setEvidenceScore(KafkaPayloadParser.asDouble(details, "evidence_score"));
        return entity;
    }

    private String resolveMessageLevel(Map<String, Object> payload) {
        String level = KafkaPayloadParser.asString(payload, "message_level");
        return level == null ? MESSAGE_LEVEL_INFO : level;
    }

    private String resolveDedupKey(DedupKeyContext context) {
        String provided = KafkaPayloadParser.asString(context.payload, "dedup_key");
        if (provided != null && !provided.isBlank()) {
            return provided;
        }
        return DedupKeyBuilder.build(
            context.sourceSystem,
            context.eventFamily,
            KafkaPayloadParser.asString(context.payload, "event_code"),
            context.shotNo,
            context.eventTime,
            KafkaPayloadParser.asString(context.payload, "process_id"),
            KafkaPayloadParser.asString(context.payload, "source_entity_id"),
            context.sourceRecord.getPayloadHash()
        );
    }

    private String resolveEventName(Map<String, Object> payload) {
        String eventName = KafkaPayloadParser.asString(payload, "event_name");
        if (eventName != null) {
            return eventName;
        }
        String eventCode = KafkaPayloadParser.asString(payload, "event_code");
        String channelName = KafkaPayloadParser.asString(payload, "channel_name");
        if (channelName == null) {
            return eventCode;
        }
        return eventCode + ":" + channelName;
    }

    private String resolveChannelName(Map<String, Object> payload, Map<String, Object> details) {
        String channelName = KafkaPayloadParser.asString(details, "channel_name");
        if (channelName != null) {
            return channelName;
        }
        return KafkaPayloadParser.asString(payload, "process_id");
    }

    private String stringify(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Failed to serialize details", ex);
        }
    }

    private static final class DedupKeyContext {
        private Map<String, Object> payload;
        private SourceRecordEntity sourceRecord;
        private String sourceSystem;
        private String eventFamily;
        private Integer shotNo;
        private LocalDateTime eventTime;

        public void setPayload(Map<String, Object> payload) {
            this.payload = payload;
        }

        public void setSourceRecord(SourceRecordEntity sourceRecord) {
            this.sourceRecord = sourceRecord;
        }

        public void setSourceSystem(String sourceSystem) {
            this.sourceSystem = sourceSystem;
        }

        public void setEventFamily(String eventFamily) {
            this.eventFamily = eventFamily;
        }

        public void setShotNo(Integer shotNo) {
            this.shotNo = shotNo;
        }

        public void setEventTime(LocalDateTime eventTime) {
            this.eventTime = eventTime;
        }
    }
}
