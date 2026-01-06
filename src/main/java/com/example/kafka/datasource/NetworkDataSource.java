package com.example.kafka.datasource;

import com.example.kafka.model.*;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网络数据源实现 - 通过Kafka接收实时数据
 */
@Component
public class NetworkDataSource implements DataSource {
    
    private static final Logger logger = LoggerFactory.getLogger(NetworkDataSource.class);
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 内存缓存,存储接收到的实时数据
    private final Map<Integer, ShotMetadata> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, WaveData> waveDataCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<OperationLog>> operationLogCache = new ConcurrentHashMap<>();
    private final Map<Integer, List<PlcInterlock>> plcInterlockCache = new ConcurrentHashMap<>();
    
    private volatile boolean initialized = false;
    
    @Override
    public WaveData.DataSourceType getSourceType() {
        return WaveData.DataSourceType.NETWORK;
    }
    
    @Override
    public void initialize() {
        logger.info("初始化网络数据源 (Kafka)");
        initialized = true;
    }
    
    @Override
    public boolean isAvailable() {
        return initialized;
    }
    
    @Override
    public List<Integer> getAllShotNumbers() {
        return new ArrayList<>(metadataCache.keySet());
    }
    
    @Override
    public ShotMetadata getShotMetadata(Integer shotNo) {
        return metadataCache.get(shotNo);
    }
    
    @Override
    public WaveData getWaveData(Integer shotNo, String channelName, String dataType) {
        String key = generateWaveDataKey(shotNo, channelName, dataType);
        return waveDataCache.get(key);
    }
    
    @Override
    public List<String> getChannelNames(Integer shotNo, String dataType) {
        // 从缓存中提取该炮号的所有通道
        Set<String> channels = new HashSet<>();
        String prefix = shotNo + "_";
        
        for (String key : waveDataCache.keySet()) {
            if (key.startsWith(prefix)) {
                WaveData data = waveDataCache.get(key);
                if (data != null) {
                    channels.add(data.getChannelName());
                }
            }
        }
        
        return new ArrayList<>(channels);
    }
    
    @Override
    public List<OperationLog> getOperationLogs(Integer shotNo) {
        return operationLogCache.getOrDefault(shotNo, Collections.emptyList());
    }
    
    @Override
    public List<PlcInterlock> getPlcInterlocks(Integer shotNo) {
        return plcInterlockCache.getOrDefault(shotNo, Collections.emptyList());
    }
    
    @Override
    public void close() {
        logger.info("关闭网络数据源");
        metadataCache.clear();
        waveDataCache.clear();
        operationLogCache.clear();
        plcInterlockCache.clear();
    }
    
    // ==================== Kafka消费者 ====================
    
    /**
     * 接收元数据
     */
    @KafkaListener(topics = "${app.kafka.topic.metadata:shot-metadata}", 
                   groupId = "network-viewer-group")
    public void consumeMetadata(String message) {
        try {
            ShotMetadata metadata = objectMapper.readValue(message, ShotMetadata.class);
            metadataCache.put(metadata.getShotNo(), metadata);
            logger.info("接收到元数据: ShotNo={}", metadata.getShotNo());
        } catch (Exception e) {
            logger.error("解析元数据消息失败: {}", message, e);
        }
    }
    
    /**
     * 接收波形数据
     */
    @KafkaListener(topics = "${app.kafka.topic.wavedata:wave-data}", 
                   groupId = "network-viewer-group")
    public void consumeWaveData(String message) {
        try {
            WaveData waveData = objectMapper.readValue(message, WaveData.class);
            waveData.setSourceType(WaveData.DataSourceType.NETWORK);
            
            String key = generateWaveDataKey(
                waveData.getShotNo(), 
                waveData.getChannelName(),
                extractDataType(waveData.getFileSource())
            );
            
            waveDataCache.put(key, waveData);
            logger.debug("接收到波形数据: ShotNo={}, Channel={}", 
                        waveData.getShotNo(), waveData.getChannelName());
        } catch (Exception e) {
            logger.error("解析波形数据消息失败: {}", message, e);
        }
    }
    
    /**
     * 接收操作日志
     */
    @KafkaListener(topics = "${app.kafka.topic.operation:operation-log}", 
                   groupId = "network-viewer-group")
    public void consumeOperationLog(String message) {
        try {
            OperationLog log = objectMapper.readValue(message, OperationLog.class);
            log.setSourceType(WaveData.DataSourceType.NETWORK);
            
            operationLogCache.computeIfAbsent(log.getShotNo(), k -> new ArrayList<>())
                            .add(log);
            logger.debug("接收到操作日志: ShotNo={}, Type={}", 
                        log.getShotNo(), log.getOperationType());
        } catch (Exception e) {
            logger.error("解析操作日志消息失败: {}", message, e);
        }
    }
    
    /**
     * 接收PLC互锁数据
     */
    @KafkaListener(topics = "${app.kafka.topic.plc:plc-interlock}", 
                   groupId = "network-viewer-group")
    public void consumePlcInterlock(String message) {
        try {
            PlcInterlock interlock = objectMapper.readValue(message, PlcInterlock.class);
            interlock.setSourceType(WaveData.DataSourceType.NETWORK);
            
            plcInterlockCache.computeIfAbsent(interlock.getShotNo(), k -> new ArrayList<>())
                            .add(interlock);
            logger.debug("接收到PLC互锁: ShotNo={}, Name={}", 
                        interlock.getShotNo(), interlock.getInterlockName());
        } catch (Exception e) {
            logger.error("解析PLC互锁消息失败: {}", message, e);
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private String generateWaveDataKey(Integer shotNo, String channelName, String dataType) {
        return String.format("%d_%s_%s", shotNo, channelName, dataType);
    }
    
    private String extractDataType(String fileSource) {
        if (fileSource != null && fileSource.contains("_")) {
            String[] parts = fileSource.split("_");
            if (parts.length > 1) {
                return parts[1].replace(".tdms", "");
            }
        }
        return "Tube"; // 默认
    }
    
    /**
     * 清除指定炮号的缓存数据 (可用于内存管理)
     */
    public void clearCache(Integer shotNo) {
        metadataCache.remove(shotNo);
        operationLogCache.remove(shotNo);
        plcInterlockCache.remove(shotNo);
        
        // 清除波形数据
        String prefix = shotNo + "_";
        waveDataCache.keySet().removeIf(key -> key.startsWith(prefix));
        
        logger.info("清除炮号 {} 的缓存数据", shotNo);
    }
    
    /**
     * 获取缓存统计信息
     */
    public Map<String, Object> getCacheStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("metadataCount", metadataCache.size());
        stats.put("waveDataCount", waveDataCache.size());
        stats.put("operationLogCount", operationLogCache.values().stream()
                                        .mapToInt(List::size).sum());
        stats.put("plcInterlockCount", plcInterlockCache.values().stream()
                                        .mapToInt(List::size).sum());
        return stats;
    }
}
