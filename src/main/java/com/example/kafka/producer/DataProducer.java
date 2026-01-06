package com.example.kafka.producer;

import com.example.kafka.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;

/**
 * 数据生产者 - 将数据发送到Kafka
 * 无论是文件读取还是网络接收的数据,都通过这个类发送到Kafka
 */
@Service
public class DataProducer {
    
    private static final Logger logger = LoggerFactory.getLogger(DataProducer.class);
    
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
                    return null;
                });
                
        } catch (JsonProcessingException e) {
            logger.error("序列化{}失败: {}", dataType, e.getMessage());
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
}
