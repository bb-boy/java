package com.example.kafka.service;

import com.example.kafka.datasource.DataSource;
import com.example.kafka.datasource.FileDataSource;
import com.example.kafka.datasource.NetworkDataSource;
import com.example.kafka.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 数据服务 - 统一管理文件和网络两种数据源
 */
@Service
public class DataService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataService.class);
    
    @Autowired
    private FileDataSource fileDataSource;
    
    @Autowired
    private NetworkDataSource networkDataSource;
    
    @Value("${app.data.source.primary:file}")
    private String primarySource;
    
    @Value("${app.data.source.fallback:true}")
    private boolean enableFallback;
    
    @PostConstruct
    public void init() {
        fileDataSource.initialize();
        networkDataSource.initialize();
        logger.info("数据服务初始化完成 - 主数据源: {}, 启用备用: {}", 
                    primarySource, enableFallback);
    }
    
    /**
     * 获取主数据源
     */
    private DataSource getPrimaryDataSource() {
        return "network".equalsIgnoreCase(primarySource) 
               ? networkDataSource 
               : fileDataSource;
    }
    
    /**
     * 获取备用数据源
     */
    private DataSource getFallbackDataSource() {
        return "network".equalsIgnoreCase(primarySource) 
               ? fileDataSource 
               : networkDataSource;
    }
    
    /**
     * 获取所有炮号 (合并两个数据源)
     */
    public List<Integer> getAllShotNumbers() {
        Set<Integer> shotNumbers = new HashSet<>();
        
        // 从主数据源获取
        DataSource primary = getPrimaryDataSource();
        if (primary.isAvailable()) {
            shotNumbers.addAll(primary.getAllShotNumbers());
        }
        
        // 如果启用备用,也从备用数据源获取
        if (enableFallback) {
            DataSource fallback = getFallbackDataSource();
            if (fallback.isAvailable()) {
                shotNumbers.addAll(fallback.getAllShotNumbers());
            }
        }
        
        return shotNumbers.stream().sorted().collect(Collectors.toList());
    }
    
    /**
     * 获取炮号元数据 (优先从主数据源获取)
     */
    public ShotMetadata getShotMetadata(Integer shotNo) {
        ShotMetadata metadata = getPrimaryDataSource().getShotMetadata(shotNo);
        
        // 如果主数据源没有,尝试从备用数据源获取
        if (metadata == null && enableFallback) {
            metadata = getFallbackDataSource().getShotMetadata(shotNo);
        }
        
        return metadata;
    }
    
    /**
     * 获取波形数据
     */
    public WaveData getWaveData(Integer shotNo, String channelName, String dataType) {
        WaveData waveData = getPrimaryDataSource().getWaveData(shotNo, channelName, dataType);
        
        if (waveData == null && enableFallback) {
            waveData = getFallbackDataSource().getWaveData(shotNo, channelName, dataType);
        }
        
        return waveData;
    }
    
    /**
     * 获取通道列表
     */
    public List<String> getChannelNames(Integer shotNo, String dataType) {
        Set<String> channels = new HashSet<>();
        
        channels.addAll(getPrimaryDataSource().getChannelNames(shotNo, dataType));
        
        if (enableFallback) {
            channels.addAll(getFallbackDataSource().getChannelNames(shotNo, dataType));
        }
        
        return new ArrayList<>(channels);
    }
    
    /**
     * 获取操作日志
     */
    public List<OperationLog> getOperationLogs(Integer shotNo) {
        List<OperationLog> logs = getPrimaryDataSource().getOperationLogs(shotNo);
        
        if ((logs == null || logs.isEmpty()) && enableFallback) {
            logs = getFallbackDataSource().getOperationLogs(shotNo);
        }
        
        return logs != null ? logs : Collections.emptyList();
    }
    
    /**
     * 获取PLC互锁日志
     */
    public List<PlcInterlock> getPlcInterlocks(Integer shotNo) {
        List<PlcInterlock> interlocks = getPrimaryDataSource().getPlcInterlocks(shotNo);
        
        if ((interlocks == null || interlocks.isEmpty()) && enableFallback) {
            interlocks = getFallbackDataSource().getPlcInterlocks(shotNo);
        }
        
        return interlocks != null ? interlocks : Collections.emptyList();
    }
    
    /**
     * 获取完整的炮号数据 (元数据+波形+日志)
     */
    public ShotCompleteData getCompleteData(Integer shotNo) {
        ShotCompleteData data = new ShotCompleteData();
        data.setMetadata(getShotMetadata(shotNo));
        data.setOperationLogs(getOperationLogs(shotNo));
        data.setPlcInterlocks(getPlcInterlocks(shotNo));
        
        // 获取所有通道的波形数据
        Map<String, WaveData> tubeWaves = new HashMap<>();
        Map<String, WaveData> waterWaves = new HashMap<>();
        
        // 分别获取 Tube 和 Water 的通道列表
        List<String> tubeChannels = getChannelNames(shotNo, "Tube");
        for (String channel : tubeChannels) {
            WaveData tubeData = getWaveData(shotNo, channel, "Tube");
            if (tubeData != null) {
                tubeWaves.put(channel, tubeData);
            }
        }
        
        // 获取 Water 通道的波形数据
        List<String> waterChannels = getChannelNames(shotNo, "Water");
        for (String channel : waterChannels) {
            WaveData waterData = getWaveData(shotNo, channel, "Water");
            if (waterData != null) {
                waterWaves.put(channel, waterData);
            }
        }
        
        data.setTubeWaveData(tubeWaves);
        data.setWaterWaveData(waterWaves);
        
        return data;
    }
    
    /**
     * 切换主数据源
     */
    public void switchPrimarySource(String source) {
        if ("file".equalsIgnoreCase(source) || "network".equalsIgnoreCase(source)) {
            this.primarySource = source;
            logger.info("切换主数据源为: {}", source);
        } else {
            throw new IllegalArgumentException("无效的数据源类型: " + source);
        }
    }
    
    /**
     * 获取数据源状态
     */
    public Map<String, Object> getDataSourceStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("primarySource", primarySource);
        status.put("fallbackEnabled", enableFallback);
        status.put("fileSourceAvailable", fileDataSource.isAvailable());
        status.put("networkSourceAvailable", networkDataSource.isAvailable());
        status.put("fileSourceShotCount", fileDataSource.getAllShotNumbers().size());
        status.put("networkSourceShotCount", networkDataSource.getAllShotNumbers().size());
        
        // 网络数据源缓存统计
        status.put("networkCacheStats", networkDataSource.getCacheStats());
        
        return status;
    }
    
    /**
     * 完整数据包装类
     */
    public static class ShotCompleteData {
        private ShotMetadata metadata;
        private Map<String, WaveData> tubeWaveData;
        private Map<String, WaveData> waterWaveData;
        private List<OperationLog> operationLogs;
        private List<PlcInterlock> plcInterlocks;
        
        // Getters and Setters
        public ShotMetadata getMetadata() { return metadata; }
        public void setMetadata(ShotMetadata metadata) { this.metadata = metadata; }
        
        public Map<String, WaveData> getTubeWaveData() { return tubeWaveData; }
        public void setTubeWaveData(Map<String, WaveData> tubeWaveData) { 
            this.tubeWaveData = tubeWaveData; 
        }
        
        public Map<String, WaveData> getWaterWaveData() { return waterWaveData; }
        public void setWaterWaveData(Map<String, WaveData> waterWaveData) { 
            this.waterWaveData = waterWaveData; 
        }
        
        public List<OperationLog> getOperationLogs() { return operationLogs; }
        public void setOperationLogs(List<OperationLog> operationLogs) { 
            this.operationLogs = operationLogs; 
        }
        
        public List<PlcInterlock> getPlcInterlocks() { return plcInterlocks; }
        public void setPlcInterlocks(List<PlcInterlock> plcInterlocks) { 
            this.plcInterlocks = plcInterlocks; 
        }
    }
}
