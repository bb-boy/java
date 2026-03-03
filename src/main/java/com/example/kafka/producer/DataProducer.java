package com.example.kafka.producer;

import com.example.kafka.model.*;
import com.example.kafka.entity.ProtectionEventEntity;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 数据生产者 - 将数据发送到Kafka
 * 无论是文件读取还是网络接收的数据,都通过这个类发送到Kafka
 */
@Service
public class DataProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(DataProducer.class);
    private static final String ERROR_TYPE_SYNC_FAILED = "SYNC_FAILED";
    private static final String DATA_TYPE_SYNC = "SYNC";
    private static final String SYNC_TOPIC = "sync-shot";
    private static final String SYNC_KEY_PREFIX = "shot-";
    
    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    
    private final ObjectMapper objectMapper;
    
    @Value("${app.kafka.topic.metadata:shot-metadata}")
    private String metadataTopic;
    
    @Value("${app.kafka.topic.wavedata:wave-data}")
    private String waveDataTopic;
    
    @Value("${app.kafka.topic.operation:operation-log}")
    private String operationTopic;
    
    @Value("${app.kafka.topic.plc:plc-interlock}")
    private String plcTopic;

    @Value("${app.kafka.topic.protection:protection-event}")
    private String protectionTopic;

    @Value("${app.kafka.topic.error:ingest-error}")
    private String errorTopic;
    
    public DataProducer() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    /**
     * 发送元数据到Kafka
     */
    public CompletableFuture<Void> sendMetadata(ShotMetadata metadata) {
        return sendMessage(metadataTopic, 
                          String.valueOf(metadata.getShotNo()), 
                          metadata, 
                          "元数据");
    }
    
    /**
     * 发送波形数据到Kafka
     */
    public CompletableFuture<Void> sendWaveData(WaveData waveData) {
        String key = String.format("%d_%s_%s", 
            waveData.getShotNo(), 
            waveData.getChannelName(),
            extractDataType(waveData.getFileSource()));
        return sendMessage(waveDataTopic, key, waveData, "波形数据");
    }
    
    /**
     * 发送操作日志到Kafka
     */
    public CompletableFuture<Void> sendOperationLog(OperationLog log) {
        String key = String.format("%d_%s", log.getShotNo(), log.getTimestamp());
        return sendMessage(operationTopic, key, log, "操作日志");
    }
    
    /**
     * 发送PLC互锁到Kafka
     */
    public CompletableFuture<Void> sendPlcInterlock(PlcInterlock interlock) {
        String key = String.format("%d_%s", interlock.getShotNo(), interlock.getTimestamp());
        return sendMessage(plcTopic, key, interlock, "PLC互锁");
    }

    /**
     * 发送保护事件到Kafka
     */
    public CompletableFuture<Void> sendProtectionEvent(ProtectionEventEntity event) {
        String key = String.format("%d_%s", event.getShotNo(), event.getTriggerTime());
        return sendMessage(protectionTopic, key, event, "保护事件");
    }

    /**
     * 发布同步失败事件到 ingest-error 主题
     */
    public void sendSyncFailure(Integer shotNo, String stage, String errorMessage, Throwable ex) {
        if (errorTopic == null || errorTopic.isBlank()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", LocalDateTime.now());
        payload.put("errorType", ERROR_TYPE_SYNC_FAILED);
        payload.put("topic", SYNC_TOPIC);
        payload.put("messageKey", buildSyncKey(shotNo));
        if (shotNo != null) {
            payload.put("shotNo", shotNo);
        }
        payload.put("dataType", DATA_TYPE_SYNC);
        if (stage != null && !stage.isBlank()) {
            payload.put("stage", stage);
        }
        String finalMessage = errorMessage != null ? errorMessage : (ex != null ? ex.getMessage() : null);
        if (finalMessage != null && !finalMessage.isBlank()) {
            payload.put("errorMessage", finalMessage);
            payload.put("payloadSize", finalMessage.length());
            payload.put("payloadPreview", truncate(finalMessage));
        }
        if (ex != null) {
            payload.put("exceptionType", ex.getClass().getSimpleName());
        }
        sendErrorPayload(payload);
    }
    
    /**
     * 通用发送方法
     */
    private <T> CompletableFuture<Void> sendMessage(String topic, String key, T data, String dataType) {
        try {
            String json = objectMapper.writeValueAsString(data);
            
            return kafkaTemplate.send(topic, key, json)
                .thenAccept(result -> {
                    logger.debug("发送{}成功: topic={}, key={}, partition={}, offset={}", 
                        dataType, topic, key,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                })
                .exceptionally(ex -> {
                    logger.error("发送{}失败: topic={}, key={}, error={}", 
                        dataType, topic, key, ex.getMessage());
                    publishIngestError(topic, key, dataType, json, ex);
                    return null;
                });
                
        } catch (JsonProcessingException e) {
            logger.error("序列化{}失败: {}", dataType, e.getMessage());
            publishSerializationError(topic, key, dataType, data, e);
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * 批量发送元数据
     */
    public void sendMetadataBatch(Iterable<ShotMetadata> metadataList) {
        for (ShotMetadata metadata : metadataList) {
            sendMetadata(metadata);
        }
        kafkaTemplate.flush();
    }
    
    /**
     * 批量发送波形数据
     */
    public void sendWaveDataBatch(Iterable<WaveData> waveDataList) {
        for (WaveData waveData : waveDataList) {
            sendWaveData(waveData);
        }
        kafkaTemplate.flush();
    }
    
    /**
     * 批量发送操作日志
     */
    public void sendOperationLogBatch(Iterable<OperationLog> logs) {
        for (OperationLog log : logs) {
            sendOperationLog(log);
        }
        kafkaTemplate.flush();
    }
    
    /**
     * 批量发送PLC互锁
     */
    public void sendPlcInterlockBatch(Iterable<PlcInterlock> interlocks) {
        for (PlcInterlock interlock : interlocks) {
            sendPlcInterlock(interlock);
        }
        kafkaTemplate.flush();
    }
    
    private String extractDataType(String fileSource) {
        if (fileSource != null) {
            if (fileSource.contains("Water")) return "Water";
            if (fileSource.contains("Tube")) return "Tube";
        }
        return "Tube";
    }

    private void publishIngestError(String topic, String key, String dataType, String payloadJson, Throwable ex) {
        if (errorTopic == null || errorTopic.isBlank() || errorTopic.equals(topic)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", LocalDateTime.now());
        payload.put("errorType", "PRODUCE_FAILED");
        payload.put("topic", topic);
        payload.put("messageKey", key);
        payload.put("dataType", dataType);
        Integer shotNo = extractShotNo(key);
        if (shotNo != null) {
            payload.put("shotNo", shotNo);
        }
        if (payloadJson != null) {
            payload.put("payloadSize", payloadJson.length());
            payload.put("payloadPreview", truncate(payloadJson));
        }
        payload.put("errorMessage", ex != null ? ex.getMessage() : null);
        sendErrorPayload(payload);
    }

    private void publishSerializationError(String topic, String key, String dataType, Object data, Throwable ex) {
        if (errorTopic == null || errorTopic.isBlank() || errorTopic.equals(topic)) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("timestamp", LocalDateTime.now());
        payload.put("errorType", "SERIALIZE_FAILED");
        payload.put("topic", topic);
        payload.put("messageKey", key);
        payload.put("dataType", dataType);
        Integer shotNo = extractShotNo(key);
        if (shotNo != null) {
            payload.put("shotNo", shotNo);
        }
        String preview = data != null ? String.valueOf(data) : null;
        if (preview != null) {
            payload.put("payloadSize", preview.length());
            payload.put("payloadPreview", truncate(preview));
        }
        payload.put("errorMessage", ex != null ? ex.getMessage() : null);
        sendErrorPayload(payload);
    }

    private void sendErrorPayload(Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String key = String.valueOf(payload.getOrDefault("shotNo", "error"));
            kafkaTemplate.send(errorTopic, key, json)
                .exceptionally(ex -> {
                    logger.error("发送失败事件失败: {}", ex.getMessage());
                    return null;
                });
        } catch (Exception ex) {
            logger.error("序列化失败事件失败: {}", ex.getMessage());
        }
    }

    private String truncate(String payload) {
        if (payload == null) {
            return null;
        }
        int max = 512;
        if (payload.length() <= max) {
            return payload;
        }
        return payload.substring(0, max);
    }

    private Integer extractShotNo(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        int idx = 0;
        while (idx < key.length() && Character.isDigit(key.charAt(idx))) {
            idx++;
        }
        if (idx == 0) {
            return null;
        }
        try {
            return Integer.parseInt(key.substring(0, idx));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String buildSyncKey(Integer shotNo) {
        if (shotNo == null) {
            return "sync";
        }
        return SYNC_KEY_PREFIX + shotNo;
    }
}
