package com.example.kafka.consumer;

import com.example.kafka.entity.*;
import com.example.kafka.repository.*;
import com.example.kafka.controller.WebSocketController;
import com.example.kafka.service.InfluxDBService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * 数据消费者 - 从Kafka消费数据并存入数据库
 */
@Service
public class DataConsumer {
    
    private static final Logger logger = LoggerFactory.getLogger(DataConsumer.class);
    
    @Autowired
    private ShotMetadataRepository metadataRepository;
    
    @Autowired
    private WaveDataRepository waveDataRepository;
    
    @Autowired
    private OperationLogRepository operationLogRepository;
    
    @Autowired
    private PlcInterlockRepository plcInterlockRepository;
    
    @Autowired(required = false)
    private WebSocketController webSocketController;
    
    @Autowired(required = false)
    private InfluxDBService influxDBService;
    
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
        Integer shotNo = null;
        String channelName = null;
        try {
            logger.debug("收到波形数据消息: {}", message.substring(0, Math.min(200, message.length())));
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            
            shotNo = (Integer) data.get("shotNo");
            channelName = (String) data.get("channelName");
            String fileSource = (String) data.get("fileSource");
            String dataType = extractDataType(fileSource);
            
            logger.info("开始处理波形数据: shotNo={}, channelName={}, dataType={}", shotNo, channelName, dataType);
            
            // 使用upsert逻辑：先查询是否存在，存在则更新，不存在则插入
            WaveDataEntity entity = waveDataRepository
                .findByShotNoAndChannelNameAndDataType(shotNo, channelName, dataType)
                .orElse(new WaveDataEntity(shotNo, channelName));
            
            entity.setDataType(dataType);
            entity.setFileSource(fileSource);
            entity.setSampleRate(toDouble(data.get("sampleRate")));
            entity.setSamples(toInteger(data.get("samples")));
            
            // 解析时间
            if (data.get("startTime") != null) {
                entity.setStartTime(parseDateTime(data.get("startTime")));
            }
            if (data.get("endTime") != null) {
                entity.setEndTime(parseDateTime(data.get("endTime")));
            }
            
            // 压缩存储波形数据
            Object waveDataObj = data.get("data");
            if (waveDataObj instanceof List) {
                List<Double> waveList = (List<Double>) waveDataObj;
                entity.setData(compressWaveData(waveList));
                entity.setSamples(waveList.size());
            }
            
            // 设置数据源类型
            String sourceType = (String) data.get("sourceType");
            if (sourceType != null) {
                entity.setSourceType(ShotMetadataEntity.DataSourceType.valueOf(sourceType));
            }
            
            waveDataRepository.save(entity);
            logger.info("波形数据已存入MySQL: ShotNo={}, Channel={}, Type={}, ID={}", 
                        shotNo, channelName, dataType, entity.getId());
            
            // 同时写入InfluxDB时序数据库
            if (influxDBService != null) {
                influxDBService.writeFromKafkaMessage(data);
                logger.debug("波形数据已同步到InfluxDB: ShotNo={}, Channel={}", shotNo, channelName);
            }
            
        } catch (Exception e) {
            logger.error("处理波形数据消息失败: ShotNo={}, Channel={}, Error={}", 
                        shotNo, channelName, e.getMessage(), e);
        }
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
    
    private LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        
        // 处理List类型（Jackson反序列化LocalDateTime的数组格式）
        if (value instanceof List) {
            try {
                List<Integer> dateList = (List<Integer>) value;
                if (dateList.size() >= 6) {
                    int year = dateList.get(0);
                    int month = dateList.get(1);
                    int day = dateList.get(2);
                    int hour = dateList.get(3);
                    int minute = dateList.get(4);
                    int second = dateList.get(5);
                    int nano = dateList.size() > 6 ? dateList.get(6) : 0;
                    return LocalDateTime.of(year, month, day, hour, minute, second, nano);
                }
            } catch (Exception e) {
                logger.warn("无法从List解析时间: {}", value, e);
                return null;
            }
        }
        
        // 处理Map类型（Jackson反序列化LocalDateTime的Map格式）
        if (value instanceof Map) {
            try {
                Map<String, Object> dateMap = (Map<String, Object>) value;
                int year = (Integer) dateMap.get("year");
                int month = (Integer) dateMap.get("monthValue");
                int day = (Integer) dateMap.get("dayOfMonth");
                int hour = (Integer) dateMap.get("hour");
                int minute = (Integer) dateMap.get("minute");
                int second = (Integer) dateMap.get("second");
                int nano = dateMap.containsKey("nano") ? (Integer) dateMap.get("nano") : 0;
                return LocalDateTime.of(year, month, day, hour, minute, second, nano);
            } catch (Exception e) {
                logger.warn("无法从Map解析时间: {}", value);
                return null;
            }
        }
        
        if (value instanceof String) {
            String str = (String) value;
            try {
                // 尝试多种格式
                if (str.contains("T")) {
                    return LocalDateTime.parse(str.replace(" ", "T").split("\\.")[0]);
                } else {
                    return LocalDateTime.parse(str, 
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                }
            } catch (Exception e) {
                try {
                    return LocalDateTime.parse(str, 
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (Exception e2) {
                    logger.warn("无法解析时间: {}", str);
                    return null;
                }
            }
        }
        return null;
    }
    
    private String extractDataType(String fileSource) {
        if (fileSource != null) {
            if (fileSource.contains("Water")) return "Water";
            if (fileSource.contains("Tube")) return "Tube";
        }
        return "Tube";
    }
    
    /**
     * 压缩波形数据
     */
    private byte[] compressWaveData(List<Double> data) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(baos);
            ObjectOutputStream oos = new ObjectOutputStream(gzip);
            
            // 转换为double数组以减少空间
            double[] arr = data.stream().mapToDouble(Double::doubleValue).toArray();
            oos.writeObject(arr);
            
            oos.close();
            gzip.close();
            
            return baos.toByteArray();
        } catch (IOException e) {
            logger.error("压缩波形数据失败", e);
            return null;
        }
    }
    
    /**
     * 解压波形数据
     */
    public static List<Double> decompressWaveData(byte[] compressed) {
        if (compressed == null) return null;
        
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
            GZIPInputStream gzip = new GZIPInputStream(bais);
            ObjectInputStream ois = new ObjectInputStream(gzip);
            
            double[] arr = (double[]) ois.readObject();
            ois.close();
            
            return java.util.Arrays.stream(arr).boxed().toList();
        } catch (Exception e) {
            return null;
        }
    }
}
