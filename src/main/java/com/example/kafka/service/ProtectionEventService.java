package com.example.kafka.service;

import com.example.kafka.entity.ProtectionEventEntity;
import com.example.kafka.repository.ProtectionEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class ProtectionEventService {

    private static final long DEFAULT_WINDOW_BEFORE_MS = 100L;
    private static final long DEFAULT_WINDOW_AFTER_MS = 200L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final String DEFAULT_SEVERITY = "INFO";
    private static final String DEFAULT_PROTECTION_LEVEL = "LOW";
    private static final int DATE_LIST_MIN_SIZE = 6;
    private static final int DATE_YEAR_INDEX = 0;
    private static final int DATE_MONTH_INDEX = 1;
    private static final int DATE_DAY_INDEX = 2;
    private static final int DATE_HOUR_INDEX = 3;
    private static final int DATE_MINUTE_INDEX = 4;
    private static final int DATE_SECOND_INDEX = 5;
    private static final int DATE_NANO_INDEX = 6;
    private static final int DEFAULT_NANO = 0;

    private final ProtectionEventRepository protectionEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.protection.window.before-ms:" + DEFAULT_WINDOW_BEFORE_MS + "}")
    private long windowBeforeMs;

    @Value("${app.protection.window.after-ms:" + DEFAULT_WINDOW_AFTER_MS + "}")
    private long windowAfterMs;

    @Autowired
    public ProtectionEventService(
        ProtectionEventRepository protectionEventRepository,
        ObjectMapper objectMapper
    ) {
        this.protectionEventRepository = protectionEventRepository;
        this.objectMapper = objectMapper;
    }

    public ProtectionEventEntity saveFromMessage(Map<String, Object> data, String rawMessage) {
        Integer shotNo = toInteger(firstNonNull(data, "shotNo", "shot_no"));
        LocalDateTime triggerTime = parseDateTime(firstNonNull(data, "triggerTime", "timestamp"));
        String interlockName = toStringValue(firstNonNull(data, "interlockName", "event"));

        if (shotNo == null || triggerTime == null || interlockName == null) {
            throw new IllegalArgumentException("protection event missing required fields");
        }

        ProtectionEventEntity entity = new ProtectionEventEntity();
        entity.setShotNo(shotNo);
        entity.setTriggerTime(triggerTime);
        entity.setInterlockName(interlockName);
        entity.setSeverity(toStringValue(data.get("severity"), DEFAULT_SEVERITY));
        entity.setProtectionLevel(toStringValue(data.get("protectionLevel"), DEFAULT_PROTECTION_LEVEL));
        entity.setDeviceId(toStringValue(data.get("deviceId")));
        entity.setTriggerCondition(toStringValue(data.get("triggerCondition")));
        entity.setMeasuredValue(toDouble(data.get("measuredValue")));
        entity.setThresholdValue(toDouble(data.get("thresholdValue")));
        entity.setThresholdOp(toStringValue(data.get("thresholdOp")));
        entity.setActionTaken(toStringValue(data.get("actionTaken")));
        entity.setActionLatencyUs(toLong(data.get("actionLatencyUs")));
        entity.setAckUserId(toLong(data.get("ackUserId")));
        entity.setAckTime(parseDateTime(data.get("ackTime")));
        entity.setRelatedWaveformId(toLong(data.get("relatedWaveformId")));
        entity.setWindowStart(resolveWindowStart(triggerTime, data.get("windowStart")));
        entity.setWindowEnd(resolveWindowEnd(triggerTime, data.get("windowEnd")));
        entity.setRelatedChannels(serializeJson(data.get("relatedChannels")));
        entity.setRawPayload(resolveRawPayload(rawMessage));

        return protectionEventRepository.save(entity);
    }

    private LocalDateTime resolveWindowStart(LocalDateTime triggerTime, Object windowStart) {
        LocalDateTime provided = parseDateTime(windowStart);
        if (provided != null) {
            return provided;
        }
        return triggerTime.minusNanos(windowBeforeMs * NANOS_PER_MILLI);
    }

    private LocalDateTime resolveWindowEnd(LocalDateTime triggerTime, Object windowEnd) {
        LocalDateTime provided = parseDateTime(windowEnd);
        if (provided != null) {
            return provided;
        }
        return triggerTime.plusNanos(windowAfterMs * NANOS_PER_MILLI);
    }

    private String resolveRawPayload(String rawMessage) {
        if (rawMessage == null) {
            return null;
        }
        return rawMessage;
    }

    private Object firstNonNull(Map<String, Object> data, String firstKey, String secondKey) {
        Object first = data.get(firstKey);
        return first != null ? first : data.get(secondKey);
    }

    private String toStringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String toStringValue(Object value, String defaultValue) {
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private Integer toInteger(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Double toDouble(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return parseDateTimeFromString((String) value);
        }
        if (value instanceof List) {
            return parseDateTimeFromList((List<?>) value);
        }
        if (value instanceof Map) {
            return parseDateTimeFromMap((Map<?, ?>) value);
        }
        return null;
    }

    private LocalDateTime parseDateTimeFromString(String value) {
        try {
            if (value.contains("T")) {
                return LocalDateTime.parse(value.replace(" ", "T").split("\\.")[0]);
            }
            return LocalDateTime.parse(
                value,
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            );
        } catch (Exception ex) {
            try {
                return LocalDateTime.parse(
                    value,
                    java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                );
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private LocalDateTime parseDateTimeFromList(List<?> value) {
        if (value.size() < DATE_LIST_MIN_SIZE) {
            return null;
        }
        try {
            int year = ((Number) value.get(DATE_YEAR_INDEX)).intValue();
            int month = ((Number) value.get(DATE_MONTH_INDEX)).intValue();
            int day = ((Number) value.get(DATE_DAY_INDEX)).intValue();
            int hour = ((Number) value.get(DATE_HOUR_INDEX)).intValue();
            int minute = ((Number) value.get(DATE_MINUTE_INDEX)).intValue();
            int second = ((Number) value.get(DATE_SECOND_INDEX)).intValue();
            int nano = value.size() > DATE_NANO_INDEX
                ? ((Number) value.get(DATE_NANO_INDEX)).intValue()
                : DEFAULT_NANO;
            return LocalDateTime.of(year, month, day, hour, minute, second, nano);
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime parseDateTimeFromMap(Map<?, ?> value) {
        try {
            int year = ((Number) value.get("year")).intValue();
            int month = ((Number) value.get("monthValue")).intValue();
            int day = ((Number) value.get("dayOfMonth")).intValue();
            int hour = ((Number) value.get("hour")).intValue();
            int minute = ((Number) value.get("minute")).intValue();
            int second = ((Number) value.get("second")).intValue();
            int nano = value.get("nano") != null
                ? ((Number) value.get("nano")).intValue()
                : DEFAULT_NANO;
            return LocalDateTime.of(year, month, day, hour, minute, second, nano);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String serializeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
