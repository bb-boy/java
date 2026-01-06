package com.example.kafka.controller;

import com.example.kafka.entity.ShotMetadataEntity;
import com.example.kafka.entity.WaveDataEntity;
import com.example.kafka.entity.OperationLogEntity;
import com.example.kafka.repository.ShotMetadataRepository;
import com.example.kafka.repository.WaveDataRepository;
import com.example.kafka.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库查询控制器
 * 
 * 直接查询H2数据库中的数据（通过Kafka写入的）
 */
@RestController
@RequestMapping("/api/database")
@CrossOrigin(origins = "*")
public class DatabaseController {
    
    @Autowired
    private ShotMetadataRepository metadataRepository;
    
    @Autowired
    private WaveDataRepository waveDataRepository;
    
    @Autowired
    private OperationLogRepository operationLogRepository;
    
    /**
     * 查询数据库统计信息
     * GET /api/database/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getDatabaseStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalMetadata", metadataRepository.count());
        stats.put("totalWaveData", waveDataRepository.count());
        stats.put("totalOperationLog", operationLogRepository.count());
        
        // 查询所有炮号
        List<Integer> shotNumbers = metadataRepository.findAll()
            .stream()
            .map(ShotMetadataEntity::getShotNo)
            .sorted()
            .distinct()
            .toList();
        
        stats.put("shotNumbers", shotNumbers);
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 查询指定炮号的元数据（从数据库）
     * GET /api/database/metadata?shotNo=1
     */
    @GetMapping("/metadata")
    public ResponseEntity<List<ShotMetadataEntity>> getMetadata(
            @RequestParam Integer shotNo) {
        // 查询所有匹配的记录（可能有多条）
        List<ShotMetadataEntity> metadata = metadataRepository.findAll()
            .stream()
            .filter(m -> m.getShotNo().equals(shotNo))
            .toList();
        return ResponseEntity.ok(metadata);
    }
    
    /**
     * 查询指定炮号的所有波形数据（从数据库）
     * GET /api/database/wavedata?shotNo=1
     */
    @GetMapping("/wavedata")
    public ResponseEntity<List<WaveDataEntity>> getWaveData(
            @RequestParam Integer shotNo) {
        List<WaveDataEntity> waveData = waveDataRepository.findByShotNo(shotNo);
        return ResponseEntity.ok(waveData);
    }
    
    /**
     * 查询指定炮号的操作日志（从数据库）
     * GET /api/database/logs?shotNo=1
     */
    @GetMapping("/logs")
    public ResponseEntity<List<OperationLogEntity>> getOperationLogs(
            @RequestParam Integer shotNo) {
        List<OperationLogEntity> logs = operationLogRepository.findByShotNoOrderByTimestampAsc(shotNo);
        return ResponseEntity.ok(logs);
    }
}
