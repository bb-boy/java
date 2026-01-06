package com.example.kafka.service;

import com.example.kafka.consumer.DataConsumer;
import com.example.kafka.entity.*;
import com.example.kafka.model.*;
import com.example.kafka.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据查询服务 - 从数据库查询数据供前端展示
 */
@Service
public class DataQueryService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataQueryService.class);
    
    @Autowired
    private ShotMetadataRepository metadataRepository;
    
    @Autowired
    private WaveDataRepository waveDataRepository;
    
    @Autowired
    private OperationLogRepository operationLogRepository;
    
    @Autowired
    private PlcInterlockRepository plcInterlockRepository;
    
    /**
     * 获取所有炮号列表
     */
    public List<Integer> getAllShotNumbers() {
        return metadataRepository.findAllShotNumbers();
    }
    
    /**
     * 获取炮号元数据
     */
    public ShotMetadata getShotMetadata(Integer shotNo) {
        return metadataRepository.findById(shotNo)
            .map(this::convertToModel)
            .orElse(null);
    }
    
    /**
     * 获取波形数据
     */
    public WaveData getWaveData(Integer shotNo, String channelName, String dataType) {
        return waveDataRepository.findByShotNoAndChannelNameAndDataType(shotNo, channelName, dataType)
            .map(this::convertToModel)
            .orElse(null);
    }
    
    /**
     * 获取指定炮号的所有通道名称
     */
    public List<String> getChannelNames(Integer shotNo, String dataType) {
        return waveDataRepository.findChannelNamesByShotNoAndDataType(shotNo, dataType);
    }
    
    /**
     * 获取操作日志
     */
    public List<OperationLog> getOperationLogs(Integer shotNo) {
        return operationLogRepository.findByShotNoOrderByTimestampAsc(shotNo)
            .stream()
            .map(this::convertToModel)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取PLC互锁日志
     */
    public List<PlcInterlock> getPlcInterlocks(Integer shotNo) {
        return plcInterlockRepository.findByShotNoOrderByTimestampAsc(shotNo)
            .stream()
            .map(this::convertToModel)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取完整的炮号数据
     */
    public ShotCompleteData getCompleteData(Integer shotNo) {
        ShotCompleteData data = new ShotCompleteData();
        
        // 元数据
        data.setMetadata(getShotMetadata(shotNo));
        
        // 操作日志
        data.setOperationLogs(getOperationLogs(shotNo));
        
        // PLC互锁
        data.setPlcInterlocks(getPlcInterlocks(shotNo));
        
        // 波形数据
        Map<String, WaveData> tubeWaves = new HashMap<>();
        Map<String, WaveData> waterWaves = new HashMap<>();
        
        List<String> tubeChannels = getChannelNames(shotNo, "Tube");
        for (String channel : tubeChannels) {
            WaveData wd = getWaveData(shotNo, channel, "Tube");
            if (wd != null) {
                tubeWaves.put(channel, wd);
            }
        }
        
        List<String> waterChannels = getChannelNames(shotNo, "Water");
        for (String channel : waterChannels) {
            WaveData wd = getWaveData(shotNo, channel, "Water");
            if (wd != null) {
                waterWaves.put(channel, wd);
            }
        }
        
        data.setTubeWaveData(tubeWaves);
        data.setWaterWaveData(waterWaves);
        
        return data;
    }
    
    /**
     * 获取数据库统计信息
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalShots", metadataRepository.count());
        stats.put("totalWaveData", waveDataRepository.count());
        stats.put("totalOperationLogs", operationLogRepository.count());
        stats.put("totalPlcInterlocks", plcInterlockRepository.count());
        return stats;
    }
    
    // ==================== Entity -> Model 转换 ====================
    
    private ShotMetadata convertToModel(ShotMetadataEntity entity) {
        ShotMetadata model = new ShotMetadata();
        model.setShotNo(entity.getShotNo());
        model.setFileName(entity.getFileName());
        model.setFilePath(entity.getFilePath());
        model.setStartTime(entity.getStartTime());
        model.setEndTime(entity.getEndTime());
        model.setExpectedDuration(entity.getExpectedDuration());
        model.setActualDuration(entity.getActualDuration());
        model.setStatus(entity.getStatus());
        model.setReason(entity.getReason());
        model.setTolerance(entity.getTolerance());
        model.setTotalSamples(entity.getTotalSamples());
        model.setSampleRate(entity.getSampleRate());
        return model;
    }
    
    private WaveData convertToModel(WaveDataEntity entity) {
        WaveData model = new WaveData();
        model.setShotNo(entity.getShotNo());
        model.setChannelName(entity.getChannelName());
        model.setStartTime(entity.getStartTime());
        model.setEndTime(entity.getEndTime());
        model.setSampleRate(entity.getSampleRate());
        model.setSamples(entity.getSamples());
        model.setFileSource(entity.getFileSource());
        
        // 解压波形数据
        if (entity.getData() != null) {
            List<Double> data = DataConsumer.decompressWaveData(entity.getData());
            model.setData(data);
        }
        
        if (entity.getSourceType() != null) {
            model.setSourceType(WaveData.DataSourceType.valueOf(entity.getSourceType().name()));
        }
        
        return model;
    }
    
    private OperationLog convertToModel(OperationLogEntity entity) {
        OperationLog model = new OperationLog();
        model.setShotNo(entity.getShotNo());
        model.setTimestamp(entity.getTimestamp());
        model.setOperationType(entity.getOperationType());
        model.setChannelName(entity.getChannelName());
        model.setStepType(entity.getStepType());
        model.setOldValue(entity.getOldValue());
        model.setNewValue(entity.getNewValue());
        model.setDelta(entity.getDelta());
        model.setConfidence(entity.getConfidence());
        model.setFileSource(entity.getFileSource());
        
        if (entity.getSourceType() != null) {
            model.setSourceType(WaveData.DataSourceType.valueOf(entity.getSourceType().name()));
        }
        
        return model;
    }
    
    private PlcInterlock convertToModel(PlcInterlockEntity entity) {
        PlcInterlock model = new PlcInterlock();
        model.setShotNo(entity.getShotNo());
        model.setTimestamp(entity.getTimestamp());
        model.setInterlockName(entity.getInterlockName());
        model.setStatus(entity.getStatus());
        model.setCurrentValue(entity.getCurrentValue());
        model.setThreshold(entity.getThreshold());
        model.setThresholdOperation(entity.getThresholdOperation());
        model.setDescription(entity.getDescription());
        
        if (entity.getSourceType() != null) {
            model.setSourceType(WaveData.DataSourceType.valueOf(entity.getSourceType().name()));
        }
        
        return model;
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
