package com.example.kafka.controller;

import com.example.kafka.entity.OperationLogEntity;
import com.example.kafka.entity.ProtectionEventEntity;
import com.example.kafka.model.SpectrumRequest;
import com.example.kafka.model.SpectrumResult;
import com.example.kafka.model.SyntheticGenerationRequest;
import com.example.kafka.model.SyntheticGenerationResult;
import com.example.kafka.model.WaveformWindowRequest;
import com.example.kafka.model.WaveformWindowResult;
import com.example.kafka.repository.OperationLogRepository;
import com.example.kafka.repository.ProtectionEventRepository;
import com.example.kafka.service.SpectrumService;
import com.example.kafka.service.SyntheticDataGeneratorService;
import com.example.kafka.service.WaveformWindowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ecrh")
@CrossOrigin(origins = "*")
public class EcrhController {

    private static final DateTimeFormatter FORMAT_WITH_MS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FORMAT_WITHOUT_MS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String PARAM_START = "start";
    private static final String PARAM_END = "end";

    private final ProtectionEventRepository protectionEventRepository;
    private final OperationLogRepository operationLogRepository;
    private final WaveformWindowService waveformWindowService;
    private final SpectrumService spectrumService;
    private final SyntheticDataGeneratorService syntheticDataGeneratorService;

    @Autowired
    public EcrhController(
        ProtectionEventRepository protectionEventRepository,
        OperationLogRepository operationLogRepository,
        WaveformWindowService waveformWindowService,
        SpectrumService spectrumService,
        SyntheticDataGeneratorService syntheticDataGeneratorService
    ) {
        this.protectionEventRepository = protectionEventRepository;
        this.operationLogRepository = operationLogRepository;
        this.waveformWindowService = waveformWindowService;
        this.spectrumService = spectrumService;
        this.syntheticDataGeneratorService = syntheticDataGeneratorService;
    }

    @GetMapping("/protection-events")
    public ResponseEntity<?> getProtectionEvents(
        @RequestParam Integer shotNo,
        @RequestParam(required = false) String start,
        @RequestParam(required = false) String end,
        @RequestParam(required = false) String severity,
        @RequestParam(required = false) String deviceId,
        @RequestParam(required = false) String interlockName
    ) {
        try {
            validateRangeParams(start, end);
            LocalDateTime startTime = parseDateTime(start);
            LocalDateTime endTime = parseDateTime(end);
            validateTimeOrder(startTime, endTime);
            List<String> severityList = parseCsv(severity);
            List<ProtectionEventEntity> events = queryProtectionEvents(shotNo, startTime, endTime);
            List<ProtectionEventEntity> filtered = filterProtectionEvents(
                events,
                severityList,
                deviceId,
                interlockName
            );
            return ResponseEntity.ok(filtered);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @GetMapping("/waveform/window")
    public ResponseEntity<?> getWaveformWindow(
        @RequestParam Integer shotNo,
        @RequestParam String channelName,
        @RequestParam String dataType,
        @RequestParam(required = false) String start,
        @RequestParam(required = false) String end,
        @RequestParam(required = false) Integer maxPoints
    ) {
        try {
            validateRangeParams(start, end);
            LocalDateTime startTime = parseDateTime(start);
            LocalDateTime endTime = parseDateTime(end);
            validateTimeOrder(startTime, endTime);
            WaveformWindowRequest request = new WaveformWindowRequest(
                shotNo,
                channelName,
                dataType,
                startTime,
                endTime,
                maxPoints
            );
            WaveformWindowResult result = waveformWindowService.getWindow(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @GetMapping("/waveform/spectrum")
    public ResponseEntity<?> getSpectrum(
        @RequestParam Integer shotNo,
        @RequestParam String channelName,
        @RequestParam String dataType,
        @RequestParam(required = false) String start,
        @RequestParam(required = false) String end,
        @RequestParam(required = false) Integer maxPoints
    ) {
        try {
            validateRangeParams(start, end);
            LocalDateTime startTime = parseDateTime(start);
            LocalDateTime endTime = parseDateTime(end);
            validateTimeOrder(startTime, endTime);
            SpectrumRequest request = new SpectrumRequest(
                shotNo,
                channelName,
                dataType,
                startTime,
                endTime,
                maxPoints
            );
            SpectrumResult result = spectrumService.computeSpectrum(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @GetMapping("/operation-logs")
    public ResponseEntity<?> getOperationLogs(
        @RequestParam Integer shotNo,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String deviceId,
        @RequestParam(required = false) String command,
        @RequestParam(required = false) String start,
        @RequestParam(required = false) String end
    ) {
        try {
            validateRangeParams(start, end);
            LocalDateTime startTime = parseDateTime(start);
            LocalDateTime endTime = parseDateTime(end);
            validateTimeOrder(startTime, endTime);
            List<OperationLogEntity> logs = queryOperationLogs(shotNo, startTime, endTime);
            List<OperationLogEntity> filtered = filterOperationLogs(logs, userId, deviceId, command);
            return ResponseEntity.ok(filtered);
        } catch (IllegalArgumentException ex) {
            return badRequest(ex.getMessage());
        }
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateSyntheticData(@RequestBody SyntheticGenerationRequest request) {
        try {
            SyntheticGenerationResult result = syntheticDataGeneratorService.generateProtectionOperation(request);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return badRequest(ex.getMessage());
        }
    }

    private List<ProtectionEventEntity> queryProtectionEvents(
        Integer shotNo,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        if (startTime != null && endTime != null) {
            return protectionEventRepository
                .findByShotNoAndTriggerTimeBetweenOrderByTriggerTimeAsc(shotNo, startTime, endTime);
        }
        return protectionEventRepository.findByShotNoOrderByTriggerTimeAsc(shotNo);
    }

    private List<ProtectionEventEntity> filterProtectionEvents(
        List<ProtectionEventEntity> events,
        List<String> severityList,
        String deviceId,
        String interlockName
    ) {
        if (events.isEmpty()) {
            return events;
        }
        List<ProtectionEventEntity> filtered = new ArrayList<>();
        for (ProtectionEventEntity event : events) {
            if (!matchesSeverity(event, severityList)) {
                continue;
            }
            if (!matchesDevice(event, deviceId)) {
                continue;
            }
            if (!matchesInterlock(event, interlockName)) {
                continue;
            }
            filtered.add(event);
        }
        return filtered;
    }

    private List<OperationLogEntity> queryOperationLogs(
        Integer shotNo,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        if (startTime != null && endTime != null) {
            return operationLogRepository.findByShotNoAndTimeRange(shotNo, startTime, endTime);
        }
        return operationLogRepository.findByShotNoOrderByTimestampAsc(shotNo);
    }

    private List<OperationLogEntity> filterOperationLogs(
        List<OperationLogEntity> logs,
        Long userId,
        String deviceId,
        String command
    ) {
        List<OperationLogEntity> filtered = new ArrayList<>();
        for (OperationLogEntity log : logs) {
            if (!matchesUser(log, userId)) {
                continue;
            }
            if (!matchesDevice(log, deviceId)) {
                continue;
            }
            if (!matchesCommand(log, command)) {
                continue;
            }
            filtered.add(log);
        }
        return filtered;
    }

    private boolean matchesUser(OperationLogEntity log, Long userId) {
        return userId == null || userId.equals(log.getUserId());
    }

    private boolean matchesDevice(OperationLogEntity log, String deviceId) {
        return deviceId == null || deviceId.equals(log.getDeviceId());
    }

    private boolean matchesCommand(OperationLogEntity log, String command) {
        return command == null || command.equalsIgnoreCase(log.getCommand());
    }

    private boolean matchesSeverity(ProtectionEventEntity event, List<String> severityList) {
        if (severityList.isEmpty()) {
            return true;
        }
        String severity = event.getSeverity();
        if (severity == null) {
            return false;
        }
        for (String candidate : severityList) {
            if (severity.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesDevice(ProtectionEventEntity event, String deviceId) {
        return deviceId == null || deviceId.equals(event.getDeviceId());
    }

    private boolean matchesInterlock(ProtectionEventEntity event, String interlockName) {
        return interlockName == null || interlockName.equalsIgnoreCase(event.getInterlockName());
    }

    private void validateRangeParams(String start, String end) {
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
            return LocalDateTime.parse(value.replace(" ", "T").split("\\.")[0]);
        }
        try {
            return LocalDateTime.parse(value, FORMAT_WITH_MS);
        } catch (Exception ex) {
            return LocalDateTime.parse(value, FORMAT_WITHOUT_MS);
        }
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] parts = value.split(",");
        List<String> result = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private ResponseEntity<Map<String, String>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }
}
