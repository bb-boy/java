package com.example.kafka.controller;

import com.example.kafka.model.WaveformQueryCriteria;
import com.example.kafka.model.WaveformQueryRequest;
import com.example.kafka.model.WaveformSeries;
import com.example.kafka.service.WaveformQueryService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/waveform")
public class WaveformController {

    private final WaveformQueryService waveformQueryService;

    public WaveformController(
        WaveformQueryService waveformQueryService
    ) {
        this.waveformQueryService = waveformQueryService;
    }

    @GetMapping
    public ResponseEntity<?> getWaveform(
        WaveformQueryRequest request
    ) {
        try {
            WaveformQueryCriteria criteria = buildCriteria(request);
            Instant startTime = criteria.getStart();
            Instant endTime = criteria.getEnd();
            validateTimeOrder(startTime, endTime);
            WaveformSeries series = waveformQueryService.querySeries(criteria);
            return ResponseEntity.ok(series);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/channels")
    public ResponseEntity<?> getAvailableChannels(
        @RequestParam Integer shotNo,
        @RequestParam(required = false) String dataType
    ) {
        List<String> names = waveformQueryService.queryAvailableChannels(shotNo, dataType);
        String dt = dataType != null
            ? dataType.substring(0, 1).toUpperCase() + dataType.substring(1).toLowerCase()
            : "";
        List<Map<String, String>> result = names.stream().map(name -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("channelName", name);
            m.put("dataType", dt);
            return m;
        }).toList();
        return ResponseEntity.ok(result);
    }

    private void validateTimeOrder(Instant startTime, Instant endTime) {
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }

    private WaveformQueryCriteria buildCriteria(WaveformQueryRequest request) {
        if (request == null || request.getShotNo() == null || request.getChannelName() == null) {
            throw new IllegalArgumentException("shotNo and channelName are required");
        }
        WaveformQueryCriteria criteria = new WaveformQueryCriteria();
        criteria.setShotNo(request.getShotNo());
        criteria.setChannelName(request.getChannelName());
        criteria.setDataType(request.getDataType());
        criteria.setStart(parseRequiredInstant(request.getStart(), "start"));
        criteria.setEnd(parseRequiredInstant(request.getEnd(), "end"));
        criteria.setMaxPoints(request.getMaxPoints());
        return criteria;
    }

    private Instant parseRequiredInstant(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return OffsetDateTime.parse(normalizeDateTime(value)).toInstant();
        } catch (Exception ex) {
            throw new IllegalArgumentException(name + " must include timezone offset");
        }
    }

    private String normalizeDateTime(String value) {
        return value.contains("T") ? value : value.replace(" ", "T");
    }
}
