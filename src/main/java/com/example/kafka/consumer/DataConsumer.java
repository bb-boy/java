package com.example.kafka.consumer;

import com.example.kafka.entity.*;
import com.example.kafka.repository.*;
import com.example.kafka.controller.WebSocketController;
import com.example.kafka.model.WaveformSegmentRequest;
import com.example.kafka.service.InfluxDBService;
import com.example.kafka.service.ProtectionEventService;
import com.example.kafka.service.WaveformSegmentService;
import com.example.kafka.util.WaveformCompression;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据消费者 - 从Kafka消费数据并存入数据库
 */
@Service
public class DataConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(DataConsumer.class);
    private static final int MAX_LOG_MESSAGE_LENGTH = 200;
    private static final int DATE_PARTS_WITHOUT_NANO = 6;
    private static final int DATE_NANO_INDEX = 6;
    private static final int DATE_YEAR_INDEX = 0;
    private static final int DATE_MONTH_INDEX = 1;
    private static final int DATE_DAY_INDEX = 2;
    private static final int DATE_HOUR_INDEX = 3;
    private static final int DATE_MINUTE_INDEX = 4;
    private static final int DATE_SECOND_INDEX = 5;
    private static final int DEFAULT_NANO_VALUE = 0;
    private static final double MIN_SAMPLE_RATE = 0.0d;
    private static final double SAMPLE_INTERVAL_SCALAR = 1.0d;
    private static final DateTimeFormatter DATE_TIME_WITH_MS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter DATE_TIME_WITHOUT_MS =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    @Autowired
    private ShotMetadataRepository metadataRepository;
    
    @Autowired
    private WaveDataRepository waveDataRepository;
    
    @Autowired
    private OperationLogRepository operationLogRepository;
    
    @Autowired
    private PlcInterlockRepository plcInterlockRepository;

    @Autowired
    private ProtectionEventService protectionEventService;

    @Autowired
    private IngestErrorRepository ingestErrorRepository;

    @Autowired(required = false)
    private WebSocketController webSocketController;
    
    @Autowired(required = false)
    private InfluxDBService influxDBService;

    @Autowired
    private WaveformSegmentService waveformSegmentService;

    @Autowired
    private com.example.kafka.repository.ChannelRepository channelRepository;
    
    private final ObjectMapper objectMapper;
    
    public DataConsumer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * 消费元数据消息
     */
    @KafkaListener(topics = "${app.kafka.topic.metadata:shot-metadata}", 
                   groupId = "${app.kafka.group-id:data-consumer-group}")
    @Transactional
    public void consumeMetadata(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            
            Integer shotNo = (Integer) data.get("shotNo");
            
            // 创建或更新实体
            ShotMetadataEntity entity = metadataRepository.findById(shotNo)
                .orElse(new ShotMetadataEntity(shotNo));
            
            // 映射字段
            entity.setFileName((String) data.get("fileName"));
            entity.setFilePath((String) data.get("filePath"));
            entity.setExpectedDuration(toDouble(data.get("expectedDuration")));
            entity.setActualDuration(toDouble(data.get("actualDuration")));
            entity.setStatus((String) data.get("status"));
            entity.setReason((String) data.get("reason"));
            entity.setTolerance(toDouble(data.get("tolerance")));
            entity.setTotalSamples(toInteger(data.get("totalSamples")));
            entity.setSampleRate(toDouble(data.get("sampleRate")));
            
            // 解析时间
            if (data.get("startTime") != null) {
                entity.setStartTime(parseDateTime(data.get("startTime")));
            }
            if (data.get("endTime") != null) {
                entity.setEndTime(parseDateTime(data.get("endTime")));
            }
            
            // 设置数据源类型
            String sourceType = (String) data.get("sourceType");
            if (sourceType != null) {
                entity.setSourceType(ShotMetadataEntity.DataSourceType.valueOf(sourceType));
            }
            
            metadataRepository.save(entity);
            logger.info("元数据已存入数据库: ShotNo={}", shotNo);
            
            // 推送WebSocket通知
            if (webSocketController != null) {
                webSocketController.pushDataSourceStatus();
            }
            
        } catch (Exception e) {
            logger.error("处理元数据消息失败: {}", message, e);
        }
    }
    
    /**
     * 消费波形数据消息
     */
    @KafkaListener(topics = "${app.kafka.topic.wavedata:wave-data}", 
                   groupId = "${app.kafka.group-id:data-consumer-group}")
    @Transactional
    public void consumeWaveData(String message) {
        WaveformMessage parsed = null;
        try {
            parsed = parseWaveformMessage(message);
            if (parsed == null) {
                return;
            }
            WaveDataEntity entity = upsertWaveDataEntity(parsed);
            writeInflux(parsed);
            updateChannelMetadata(entity, parsed);
            WaveformSegmentRequest segmentRequest = buildSegmentRequest(parsed);
            if (segmentRequest != null) {
                waveformSegmentService.upsertMetadataAndSegments(segmentRequest);
            }
        } catch (Exception e) {
            Integer shotNo = parsed != null ? parsed.shotNo() : null;
            String channelName = parsed != null ? parsed.channelName() : null;
            logger.error("处理波形数据消息失败: ShotNo={}, Channel={}, Error={}", 
                shotNo, channelName, e.getMessage(), e);
        }
    }

    /**
     * 消费采集/同步失败事件
     */
    @KafkaListener(topics = "${app.kafka.topic.error:ingest-error}",
                   groupId = "${app.kafka.group-id:data-consumer-group}")
    @Transactional
    public void consumeIngestError(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            IngestErrorEntity entity = new IngestErrorEntity();
            entity.setEventTime(parseDateTime(data.get("timestamp")));
            entity.setErrorType((String) data.get("errorType"));
            entity.setTopic((String) data.get("topic"));
            entity.setMessageKey((String) data.get("messageKey"));
            entity.setShotNo(toInteger(data.get("shotNo")));
            entity.setDataType((String) data.get("dataType"));
            entity.setErrorMessage(truncateString((String) data.get("errorMessage"), 1024));
            entity.setPayloadSize(toInteger(data.get("payloadSize")));
            entity.setPayloadPreview(truncateString((String) data.get("payloadPreview"), 4096));
            entity.setRawPayload(message);
            ingestErrorRepository.save(entity);
            logger.warn("失败事件已记录: type={}, topic={}, shotNo={}",
                entity.getErrorType(), entity.getTopic(), entity.getShotNo());
        } catch (Exception e) {
            logger.error("处理失败事件消息失败: {}", message, e);
        }
    }

    private WaveformMessage parseWaveformMessage(String message) throws Exception {
        if (message == null) {
            return null;
        }
        int prefixLength = Math.min(MAX_LOG_MESSAGE_LENGTH, message.length());
        logger.debug("收到波形数据消息: {}", message.substring(0, prefixLength));
        Map<String, Object> data = objectMapper.readValue(message, Map.class);
        Integer shotNo = toInteger(data.get("shotNo"));
        String channelName = (String) data.get("channelName");
        if (shotNo == null || channelName == null) {
            logger.warn("波形消息缺少必要字段: shotNo={}, channelName={}", shotNo, channelName);
            return null;
        }
        String fileSource = (String) data.get("fileSource");
        String dataType = extractDataType(fileSource);
        Double sampleRate = toDouble(data.get("sampleRate"));
        LocalDateTime startTime = parseDateTime(data.get("startTime"));
        LocalDateTime endTime = parseDateTime(data.get("endTime"));
        List<Double> values = toDoubleList(data.get("data"));
        ShotMetadataEntity.DataSourceType sourceType = parseSourceType(data.get("sourceType"));
        String deviceId = (String) data.get("deviceId");
        return new WaveformMessage(
            data,
            shotNo,
            channelName,
            dataType,
            fileSource,
            sampleRate,
            startTime,
            endTime,
            values,
            sourceType,
            deviceId
        );
    }

    private WaveDataEntity upsertWaveDataEntity(WaveformMessage message) {
        WaveDataEntity entity = waveDataRepository
            .findByShotNoAndChannelNameAndDataType(message.shotNo(), message.channelName(), message.dataType())
            .orElse(new WaveDataEntity(message.shotNo(), message.channelName()));
        applyWaveDataPayload(entity, message);
        WaveDataEntity saved = waveDataRepository.save(entity);
        logger.info("波形数据已存入MySQL: ShotNo={}, Channel={}, Type={}, ID={}",
            message.shotNo(), message.channelName(), message.dataType(), saved.getId());
        return saved;
    }

    private void applyWaveDataPayload(WaveDataEntity entity, WaveformMessage message) {
        entity.setDataType(message.dataType());
        entity.setFileSource(message.fileSource());
        entity.setSampleRate(message.sampleRate());
        if (message.startTime() != null) {
            entity.setStartTime(message.startTime());
        }
        if (message.endTime() != null) {
            entity.setEndTime(message.endTime());
        }
        if (message.values() != null) {
            entity.setData(WaveformCompression.compress(message.values()));
            entity.setSamples(message.values().size());
        }
        if (message.sourceType() != null) {
            entity.setSourceType(message.sourceType());
        }
    }

    private void writeInflux(WaveformMessage message) {
        if (influxDBService == null) {
            return;
        }
        influxDBService.writeFromKafkaMessage(message.data());
        logger.debug("波形数据已同步到InfluxDB: ShotNo={}, Channel={}",
            message.shotNo(), message.channelName());
    }

    private void updateChannelMetadata(WaveDataEntity entity, WaveformMessage message) {
        try {
            ChannelEntity channelEntity = channelRepository
                .findByShotNoAndChannelNameAndDataType(message.shotNo(), message.channelName(), message.dataType())
                .orElse(new ChannelEntity(message.shotNo(), message.channelName(), message.dataType()));
            channelEntity.setDataPoints(entity.getSamples());
            Double sampleRate = entity.getSampleRate();
            if (sampleRate != null && sampleRate > MIN_SAMPLE_RATE) {
                channelEntity.setSampleInterval(SAMPLE_INTERVAL_SCALAR / sampleRate);
            }
            channelEntity.setFileSource(entity.getFileSource());
            channelEntity.setLastUpdated(LocalDateTime.now());
            channelRepository.save(channelEntity);
            logger.debug("通道元数据已写入 channels 表: ShotNo={}, Channel={}",
                message.shotNo(), message.channelName());
        } catch (Exception e) {
            logger.warn("写入通道元数据失败: ShotNo={}, Channel={} - {}",
                message.shotNo(), message.channelName(), e.getMessage());
        }
    }

    private WaveformSegmentRequest buildSegmentRequest(WaveformMessage message) {
        if (message.values() == null || message.values().isEmpty()) {
            return null;
        }
        return new WaveformSegmentRequest(
            message.shotNo(),
            message.deviceId(),
            message.channelName(),
            message.dataType(),
            message.fileSource(),
            message.sampleRate(),
            message.startTime(),
            message.endTime(),
            message.values(),
            null
        );
    }

    private List<Double> toDoubleList(Object value) {
        if (!(value instanceof List<?> list)) {
            return null;
        }
        List<Double> result = new ArrayList<>(list.size());
        for (Object item : list) {
            Double number = toDouble(item);
            if (number == null) {
                throw new IllegalArgumentException("invalid waveform data value");
            }
            result.add(number);
        }
        return result;
    }

    private ShotMetadataEntity.DataSourceType parseSourceType(Object value) {
        if (!(value instanceof String source)) {
            return null;
        }
        try {
            return ShotMetadataEntity.DataSourceType.valueOf(source);
        } catch (IllegalArgumentException e) {
            logger.warn("未知的数据源类型: {}", source);
            return null;
        }
    }

    private record WaveformMessage(
        Map<String, Object> data,
        Integer shotNo,
        String channelName,
        String dataType,
        String fileSource,
        Double sampleRate,
        LocalDateTime startTime,
        LocalDateTime endTime,
        List<Double> values,
        ShotMetadataEntity.DataSourceType sourceType,
        String deviceId
    ) {
    }
    
    /**
     * 消费操作日志消息
     */
    @KafkaListener(topics = "${app.kafka.topic.operation:operation-log}", 
                   groupId = "${app.kafka.group-id:data-consumer-group}")
    @Transactional
    public void consumeOperationLog(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            
            Integer shotNo = (Integer) data.get("shotNo");
            String channelName = (String) data.get("channelName");
            
            // 解析时间戳
            LocalDateTime timestamp = null;
            if (data.get("timestamp") != null) {
                timestamp = parseDateTime(data.get("timestamp"));
            }
            
            // 使用upsert逻辑：先查询是否存在，避免重复插入
            OperationLogEntity entity;
            if (timestamp != null) {
                entity = operationLogRepository
                    .findByShotNoAndTimestampAndChannelName(shotNo, timestamp, channelName)
                    .orElse(new OperationLogEntity());
            } else {
                entity = new OperationLogEntity();
                logger.warn("OperationLog timestamp为null: shotNo={}", shotNo);
            }
            
            entity.setShotNo(shotNo);
            entity.setOperationType((String) data.get("operationType"));
            entity.setChannelName(channelName);
            entity.setUserId(toLong(data.get("userId")));
            entity.setDeviceId((String) data.get("deviceId"));
            entity.setCommand((String) data.get("command"));
            entity.setParameters(toJsonString(data.get("parameters")));
            entity.setResultCode((String) data.get("resultCode"));
            entity.setResultMessage((String) data.get("resultMessage"));
            entity.setSource((String) data.get("source"));
            entity.setCorrelationId((String) data.get("correlationId"));
            entity.setStepType((String) data.get("stepType"));
            entity.setOldValue(toDouble(data.get("oldValue")));
            entity.setNewValue(toDouble(data.get("newValue")));
            entity.setDelta(toDouble(data.get("delta")));
            entity.setConfidence(toDouble(data.get("confidence")));
            entity.setFileSource((String) data.get("fileSource"));
            entity.setTimestamp(timestamp);
            
            // 设置数据源类型
            String sourceType = (String) data.get("sourceType");
            if (sourceType != null) {
                entity.setSourceType(ShotMetadataEntity.DataSourceType.valueOf(sourceType));
            }
            
            operationLogRepository.save(entity);
            logger.debug("操作日志已存入数据库: ShotNo={}, Channel={}, Time={}", 
                        shotNo, channelName, timestamp);
            
        } catch (Exception e) {
            logger.error("处理操作日志消息失败: {}", message, e);
        }
    }
    
    /**
     * 消费PLC互锁消息
     */
    @KafkaListener(topics = "${app.kafka.topic.plc:plc-interlock}", 
                   groupId = "${app.kafka.group-id:data-consumer-group}")
    @Transactional
    public void consumePlcInterlock(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            
            PlcInterlockEntity entity = new PlcInterlockEntity();
            entity.setShotNo((Integer) data.get("shotNo"));
            entity.setInterlockName((String) data.get("interlockName"));
            entity.setStatus((Boolean) data.get("status"));
            entity.setCurrentValue(toDouble(data.get("currentValue")));
            entity.setThreshold(toDouble(data.get("threshold")));
            entity.setThresholdOperation((String) data.get("thresholdOperation"));
            entity.setDescription((String) data.get("description"));
            
            // 额外数据以JSON格式存储
            Object additionalData = data.get("additionalData");
            if (additionalData != null) {
                entity.setAdditionalData(objectMapper.writeValueAsString(additionalData));
            }
            
            // 解析时间
            if (data.get("timestamp") != null) {
                entity.setTimestamp(parseDateTime(data.get("timestamp")));
            }
            
            // 设置数据源类型
            String sourceType = (String) data.get("sourceType");
            if (sourceType != null) {
                entity.setSourceType(ShotMetadataEntity.DataSourceType.valueOf(sourceType));
            }
            
            plcInterlockRepository.save(entity);
            logger.debug("PLC互锁已存入数据库: ShotNo={}, Name={}", 
                        entity.getShotNo(), entity.getInterlockName());
            
        } catch (Exception e) {
            logger.error("处理PLC互锁消息失败: {}", message, e);
        }
    }

    /**
     * 消费保护事件消息
     */
    @KafkaListener(topics = "${app.kafka.topic.protection:protection-event}", 
                   groupId = "${app.kafka.group-id:data-consumer-group}")
    @Transactional
    public void consumeProtectionEvent(String message) {
        try {
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            protectionEventService.saveFromMessage(data, message);
            logger.debug("保护事件已存入数据库: shotNo={}, interlock={}",
                data.get("shotNo"), data.get("interlockName"));
        } catch (Exception e) {
            logger.error("处理保护事件消息失败: {}", message, e);
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Integer) return ((Integer) value).doubleValue();
        if (value instanceof String) {
            try {
                return Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }
    
    private Integer toInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer) return (Integer) value;
        if (value instanceof Double) return ((Double) value).intValue();
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long) return (Long) value;
        if (value instanceof Integer) return ((Integer) value).longValue();
        if (value instanceof Double) return ((Double) value).longValue();
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String toJsonString(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
    
    private LocalDateTime parseDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list) {
            return parseDateTimeFromList(list);
        }
        if (value instanceof Map<?, ?> map) {
            return parseDateTimeFromMap(map);
        }
        if (value instanceof String str) {
            return parseDateTimeFromString(str);
        }
        return null;
    }

    private LocalDateTime parseDateTimeFromList(List<?> value) {
        if (value.size() < DATE_PARTS_WITHOUT_NANO) {
            return null;
        }
        Integer year = toInteger(value.get(DATE_YEAR_INDEX));
        Integer month = toInteger(value.get(DATE_MONTH_INDEX));
        Integer day = toInteger(value.get(DATE_DAY_INDEX));
        Integer hour = toInteger(value.get(DATE_HOUR_INDEX));
        Integer minute = toInteger(value.get(DATE_MINUTE_INDEX));
        Integer second = toInteger(value.get(DATE_SECOND_INDEX));
        Integer nano = value.size() > DATE_NANO_INDEX ? toInteger(value.get(DATE_NANO_INDEX)) : DEFAULT_NANO_VALUE;
        if (year == null || month == null || day == null || hour == null || minute == null || second == null) {
            logger.warn("无法从List解析时间: {}", value);
            return null;
        }
        int safeNano = nano != null ? nano : DEFAULT_NANO_VALUE;
        return LocalDateTime.of(year, month, day, hour, minute, second, safeNano);
    }

    private LocalDateTime parseDateTimeFromMap(Map<?, ?> value) {
        Integer year = toInteger(value.get("year"));
        Integer month = toInteger(value.get("monthValue"));
        Integer day = toInteger(value.get("dayOfMonth"));
        Integer hour = toInteger(value.get("hour"));
        Integer minute = toInteger(value.get("minute"));
        Integer second = toInteger(value.get("second"));
        Integer nano = toInteger(value.get("nano"));
        if (year == null || month == null || day == null || hour == null || minute == null || second == null) {
            logger.warn("无法从Map解析时间: {}", value);
            return null;
        }
        int safeNano = nano != null ? nano : DEFAULT_NANO_VALUE;
        return LocalDateTime.of(year, month, day, hour, minute, second, safeNano);
    }

    private LocalDateTime parseDateTimeFromString(String value) {
        try {
            if (value.contains("T")) {
                return LocalDateTime.parse(value.replace(" ", "T").split("\\.")[0]);
            }
            return LocalDateTime.parse(value, DATE_TIME_WITH_MS);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(value, DATE_TIME_WITHOUT_MS);
            } catch (Exception e2) {
                logger.warn("无法解析时间: {}", value);
                return null;
            }
        }
    }

    private String truncateString(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
    
    private String extractDataType(String fileSource) {
        if (fileSource != null) {
            if (fileSource.contains("Water")) return "Water";
            if (fileSource.contains("Tube")) return "Tube";
        }
        return "Tube";
    }
    
    /**
     * 解压波形数据
     */
    public static List<Double> decompressWaveData(byte[] compressed) {
        return WaveformCompression.decompress(compressed);
    }
}
