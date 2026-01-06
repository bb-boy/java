package com.example.kafka.controller;

import com.example.kafka.model.*;
import com.example.kafka.service.DataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据查询控制器 - 提供REST API访问
 */
@RestController
@RequestMapping("/api/data")
@CrossOrigin(origins = "*") // 允许前端跨域访问
public class DataController {
    
    @Autowired
    private DataService dataService;
    
    /**
     * 获取所有炮号列表
     * GET /api/data/shots
     */
    @GetMapping("/shots")
    public ResponseEntity<List<Integer>> getAllShots() {
        return ResponseEntity.ok(dataService.getAllShotNumbers());
    }
    
    /**
     * 获取指定炮号的元数据
     * GET /api/data/shots/{shotNo}/metadata
     */
    @GetMapping("/shots/{shotNo}/metadata")
    public ResponseEntity<ShotMetadata> getShotMetadata(@PathVariable Integer shotNo) {
        ShotMetadata metadata = dataService.getShotMetadata(shotNo);
        if (metadata != null) {
            return ResponseEntity.ok(metadata);
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 获取指定炮号的完整数据
     * GET /api/data/shots/{shotNo}/complete
     */
    @GetMapping("/shots/{shotNo}/complete")
    public ResponseEntity<DataService.ShotCompleteData> getCompleteData(
            @PathVariable Integer shotNo) {
        DataService.ShotCompleteData data = dataService.getCompleteData(shotNo);
        return ResponseEntity.ok(data);
    }
    
    /**
     * 获取指定炮号和通道的波形数据
     * GET /api/data/shots/{shotNo}/wave?channel=NegVoltage&type=Tube
     */
    @GetMapping("/shots/{shotNo}/wave")
    public ResponseEntity<WaveData> getWaveData(
            @PathVariable Integer shotNo,
            @RequestParam String channel,
            @RequestParam(defaultValue = "Tube") String type) {
        WaveData waveData = dataService.getWaveData(shotNo, channel, type);
        if (waveData != null) {
            return ResponseEntity.ok(waveData);
        }
        return ResponseEntity.notFound().build();
    }
    
    /**
     * 获取指定炮号的所有通道名称
     * GET /api/data/shots/{shotNo}/channels?type=Tube
     */
    @GetMapping("/shots/{shotNo}/channels")
    public ResponseEntity<List<String>> getChannels(
            @PathVariable Integer shotNo,
            @RequestParam(defaultValue = "Tube") String type) {
        return ResponseEntity.ok(dataService.getChannelNames(shotNo, type));
    }
    
    /**
     * 获取指定炮号的操作日志
     * GET /api/data/shots/{shotNo}/logs/operation
     */
    @GetMapping("/shots/{shotNo}/logs/operation")
    public ResponseEntity<List<OperationLog>> getOperationLogs(
            @PathVariable Integer shotNo) {
        return ResponseEntity.ok(dataService.getOperationLogs(shotNo));
    }
    
    /**
     * 获取指定炮号的PLC互锁日志
     * GET /api/data/shots/{shotNo}/logs/plc
     */
    @GetMapping("/shots/{shotNo}/logs/plc")
    public ResponseEntity<List<PlcInterlock>> getPlcInterlocks(
            @PathVariable Integer shotNo) {
        return ResponseEntity.ok(dataService.getPlcInterlocks(shotNo));
    }
    
    /**
     * 获取数据源状态
     * GET /api/data/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDataSourceStatus() {
        return ResponseEntity.ok(dataService.getDataSourceStatus());
    }
    
    /**
     * 切换主数据源
     * POST /api/data/source/switch?source=file
     */
    @PostMapping("/source/switch")
    public ResponseEntity<Map<String, String>> switchDataSource(
            @RequestParam String source) {
        try {
            dataService.switchPrimarySource(source);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "数据源已切换到: " + source
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 批量查询炮号元数据
     * POST /api/data/shots/batch-metadata
     * Body: [1, 2, 3, 4, 5]
     */
    @PostMapping("/shots/batch-metadata")
    public ResponseEntity<Map<Integer, ShotMetadata>> getBatchMetadata(
            @RequestBody List<Integer> shotNumbers) {
        Map<Integer, ShotMetadata> result = new java.util.HashMap<>();
        for (Integer shotNo : shotNumbers) {
            ShotMetadata metadata = dataService.getShotMetadata(shotNo);
            if (metadata != null) {
                result.put(shotNo, metadata);
            }
        }
        return ResponseEntity.ok(result);
    }
}
