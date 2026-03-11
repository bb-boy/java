package com.example.kafka.service;

import com.example.kafka.entity.EventEntity;
import com.example.kafka.entity.EventOperationDetailEntity;
import com.example.kafka.entity.EventProtectionDetailEntity;
import com.example.kafka.model.EventQueryCriteria;
import com.example.kafka.model.OperationEventView;
import com.example.kafka.model.ProtectionEventView;
import com.example.kafka.repository.EventOperationDetailRepository;
import com.example.kafka.repository.EventProtectionDetailRepository;
import com.example.kafka.repository.EventRepository;
import com.example.kafka.util.EventConstants;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class EventQueryService {
    private final EventRepository eventRepository;
    private final EventOperationDetailRepository operationDetailRepository;
    private final EventProtectionDetailRepository protectionDetailRepository;

    public EventQueryService(
        EventRepository eventRepository,
        EventOperationDetailRepository operationDetailRepository,
        EventProtectionDetailRepository protectionDetailRepository
    ) {
        this.eventRepository = eventRepository;
        this.operationDetailRepository = operationDetailRepository;
        this.protectionDetailRepository = protectionDetailRepository;
    }

    public List<OperationEventView> queryOperationEvents(EventQueryCriteria criteria) {
        List<EventEntity> events = fetchEvents(criteria, EventConstants.EVENT_FAMILY_OPERATION);
        List<EventEntity> filtered = filterEvents(criteria, events);
        Map<Long, EventOperationDetailEntity> details = operationDetailRepository
            .findAllById(filtered.stream().map(EventEntity::getEventId).toList())
            .stream()
            .collect(Collectors.toMap(EventOperationDetailEntity::getEventId, detail -> detail));
        return filtered.stream()
            .map(event -> toOperationView(event, details.get(event.getEventId())))
            .toList();
    }

    public List<ProtectionEventView> queryProtectionEvents(EventQueryCriteria criteria) {
        List<EventEntity> events = fetchEvents(criteria, EventConstants.EVENT_FAMILY_PROTECTION);
        List<EventEntity> filtered = filterEvents(criteria, events);
        Map<Long, EventProtectionDetailEntity> details = protectionDetailRepository
            .findAllById(filtered.stream().map(EventEntity::getEventId).toList())
            .stream()
            .collect(Collectors.toMap(EventProtectionDetailEntity::getEventId, detail -> detail));
        return filtered.stream()
            .map(event -> toProtectionView(event, details.get(event.getEventId())))
            .toList();
    }

    private List<EventEntity> fetchEvents(EventQueryCriteria criteria, String eventFamily) {
        if (criteria.getStart() != null && criteria.getEnd() != null) {
            return eventRepository.findByShotNoAndEventFamilyAndEventTimeBetweenOrderByEventTimeAsc(
                criteria.getShotNo(),
                eventFamily,
                criteria.getStart(),
                criteria.getEnd()
            );
        }
        return eventRepository.findByShotNoAndEventFamilyOrderByEventTimeAsc(criteria.getShotNo(), eventFamily);
    }

    private List<EventEntity> filterEvents(EventQueryCriteria criteria, List<EventEntity> events) {
        return events.stream()
            .filter(event -> matchesOptional(event.getSeverity(), criteria.getSeverity()))
            .filter(event -> matchesOptional(event.getProcessId(), criteria.getProcessId()))
            .filter(event -> matchesOptional(event.getEventCode(), criteria.getEventCode()))
            .toList();
    }

    private boolean matchesOptional(String value, String expected) {
        if (expected == null || expected.isBlank()) {
            return true;
        }
        return Objects.equals(value, expected);
    }

    private OperationEventView toOperationView(EventEntity event, EventOperationDetailEntity detail) {
        EventOperationDetailEntity resolved = detail == null ? new EventOperationDetailEntity() : detail;
        OperationEventView view = new OperationEventView();
        view.setEventId(event.getEventId());
        view.setShotNo(event.getShotNo());
        view.setEventCode(event.getEventCode());
        view.setEventName(event.getEventName());
        view.setEventTime(event.getEventTime());
        view.setProcessId(event.getProcessId());
        view.setMessageText(event.getMessageText());
        view.setMessageLevel(event.getMessageLevel());
        view.setSeverity(event.getSeverity());
        view.setSourceSystem(event.getSourceSystem());
        view.setAuthorityLevel(event.getAuthorityLevel());
        view.setEventStatus(event.getEventStatus());
        view.setCorrelationKey(event.getCorrelationKey());
        view.setOperationTypeCode(resolved.getOperationTypeCode());
        view.setOperationModeCode(resolved.getOperationModeCode());
        view.setOperationTaskCode(resolved.getOperationTaskCode());
        view.setChannelName(resolved.getChannelName());
        view.setOldValue(resolved.getOldValue());
        view.setNewValue(resolved.getNewValue());
        view.setDeltaValue(resolved.getDeltaValue());
        view.setCommandName(resolved.getCommandName());
        view.setCommandParamsJson(resolved.getCommandParamsJson());
        view.setOperatorId(resolved.getOperatorId());
        view.setExecutionStatus(resolved.getExecutionStatus());
        view.setConfidence(resolved.getConfidence());
        return view;
    }

    private ProtectionEventView toProtectionView(EventEntity event, EventProtectionDetailEntity detail) {
        EventProtectionDetailEntity resolved = detail == null ? new EventProtectionDetailEntity() : detail;
        ProtectionEventView view = new ProtectionEventView();
        view.setEventId(event.getEventId());
        view.setShotNo(event.getShotNo());
        view.setEventCode(event.getEventCode());
        view.setEventName(event.getEventName());
        view.setEventTime(event.getEventTime());
        view.setProcessId(event.getProcessId());
        view.setMessageText(event.getMessageText());
        view.setMessageLevel(event.getMessageLevel());
        view.setSeverity(event.getSeverity());
        view.setSourceSystem(event.getSourceSystem());
        view.setAuthorityLevel(event.getAuthorityLevel());
        view.setEventStatus(event.getEventStatus());
        view.setCorrelationKey(event.getCorrelationKey());
        view.setProtectionTypeCode(resolved.getProtectionTypeCode());
        view.setProtectionScope(resolved.getProtectionScope());
        view.setTriggerCondition(resolved.getTriggerCondition());
        view.setMeasuredValue(resolved.getMeasuredValue());
        view.setThresholdValue(resolved.getThresholdValue());
        view.setThresholdOp(resolved.getThresholdOp());
        view.setActionTaken(resolved.getActionTaken());
        view.setWindowStart(resolved.getWindowStart());
        view.setWindowEnd(resolved.getWindowEnd());
        view.setRelatedChannels(resolved.getRelatedChannels());
        view.setEvidenceScore(resolved.getEvidenceScore());
        return view;
    }
}
