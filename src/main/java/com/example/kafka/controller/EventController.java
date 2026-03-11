package com.example.kafka.controller;

import com.example.kafka.model.EventQueryCriteria;
import com.example.kafka.model.EventQueryRequest;
import com.example.kafka.model.OperationEventView;
import com.example.kafka.model.ProtectionEventView;
import com.example.kafka.service.EventQueryService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {
    private static final DateTimeFormatter FORMAT_WITH_MS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FORMAT_WITHOUT_MS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EventQueryService eventQueryService;

    public EventController(EventQueryService eventQueryService) {
        this.eventQueryService = eventQueryService;
    }

    @GetMapping("/operation")
    public ResponseEntity<?> getOperationEvents(
        EventQueryRequest request
    ) {
        try {
            EventQueryCriteria criteria = buildCriteria(request);
            LocalDateTime startTime = criteria.getStart();
            LocalDateTime endTime = criteria.getEnd();
            validateTimeOrder(startTime, endTime);
            List<OperationEventView> results = eventQueryService.queryOperationEvents(criteria);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/protection")
    public ResponseEntity<?> getProtectionEvents(
        EventQueryRequest request
    ) {
        try {
            EventQueryCriteria criteria = buildCriteria(request);
            LocalDateTime startTime = criteria.getStart();
            LocalDateTime endTime = criteria.getEnd();
            validateTimeOrder(startTime, endTime);
            List<ProtectionEventView> results = eventQueryService.queryProtectionEvents(criteria);
            return ResponseEntity.ok(results);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private EventQueryCriteria buildCriteria(EventQueryRequest request) {
        if (request == null || request.getShotNo() == null) {
            throw new IllegalArgumentException("shotNo is required");
        }
        validateRange(request.getStart(), request.getEnd());
        EventQueryCriteria criteria = new EventQueryCriteria();
        criteria.setShotNo(request.getShotNo());
        criteria.setStart(parseDateTime(request.getStart()));
        criteria.setEnd(parseDateTime(request.getEnd()));
        criteria.setSeverity(request.getSeverity());
        criteria.setProcessId(request.getProcessId());
        criteria.setEventCode(request.getEventCode());
        return criteria;
    }

    private void validateRange(String start, String end) {
        if ((start == null) != (end == null)) {
            throw new IllegalArgumentException("start and end must be provided together");
        }
    }

    private void validateTimeOrder(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.contains("T")) {
            return LocalDateTime.parse(value.replace(" ", "T"));
        }
        try {
            return LocalDateTime.parse(value, FORMAT_WITH_MS);
        } catch (Exception ex) {
            return LocalDateTime.parse(value, FORMAT_WITHOUT_MS);
        }
    }
}
