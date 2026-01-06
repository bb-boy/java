package com.example.kafka.controller;

import com.example.kafka.ingest.FileDataReader;
import com.example.kafka.ingest.NetworkDataReceiver;
import com.example.kafka.model.*;
import com.example.kafka.service.DataQueryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 数据查询控制器 - 提供REST API访问 (已禁用，由DataController替代)
 * 
 * 注意：此控制器与DataController有重复的API映射
 * 为了避免启动时发生路由冲突，此控制器已禁用
 * 所有API功能已整合到 DataController 中
 */
@Deprecated  // 标记为已过时
// @RestController  // 已注释，禁用此控制器
// @RequestMapping("/api/data")
// @CrossOrigin(origins = "*")
public class DataQueryController {
    
    // @Autowired: Spring自动注入依赖，不需要手动new对象
    @Autowired
    private DataQueryService dataQueryService;  // 数据查询服务（业务逻辑层）
    
    @Autowired
    private FileDataReader fileDataReader;  // 文件数据读取器
    
    @Autowired
    private NetworkDataReceiver networkDataReceiver;  // 网络数据接收器
    
    // ==================== 查询接口 ====================
    
    /**
     * 获取所有炮号列表
     * 
     * 【HTTP请求示例】
     * GET http://localhost:8080/api/data/shots
     * 
     * 【返回示例】
     * [1, 2, 3, 10, 11, 12, ...]
     */
    @GetMapping("/shots")  // 映射到 GET /api/data/shots
    public ResponseEntity<List<Integer>> getAllShots() {
        return ResponseEntity.ok(dataQueryService.getAllShotNumbers());
    }
    
    /**
     * 获取指定炮号的元数据（基本信息）
     * 
     * 【HTTP请求示例】
     * GET http://localhost:8080/api/data/shots/1/metadata
     * 
     * 【返回示例】
     * {
     *   "shotNo": 1,
     *   "fileName": "1_Tube",
     *   "startTime": "2026-01-04T10:30:15.123",
     *   "status": "Success",
     *   "sampleRate": 1000000.0
     * }
     */
    @GetMapping("/shots/{shotNo}/metadata")
    public ResponseEntity<ShotMetadata> getShotMetadata(@PathVariable Integer shotNo) {
        // @PathVariable: 从URL路径中提取参数，{shotNo} -> shotNo变量
        ShotMetadata metadata = dataQueryService.getShotMetadata(shotNo);
        if (metadata != null) {
            return ResponseEntity.ok(metadata);  // HTTP 200 OK
        }
        return ResponseEntity.notFound().build();  // HTTP 404 Not Found
    }
    
    /**
     * 获取指定炮号的完整数据（一次性获取所有相关数据）
     * 
     * 【HTTP请求示例】
     * GET http://localhost:8080/api/data/shots/1/complete
     * 
     * 【返回数据包含】
     * - metadata: 元数据
     * - waveData: 所有通道的波形数据
     * - operationLogs: 操作日志
     * - plcInterlocks: PLC互锁日志
     */
    @GetMapping("/shots/{shotNo}/complete")
    public ResponseEntity<DataQueryService.ShotCompleteData> getCompleteData(
            @PathVariable Integer shotNo) {
        DataQueryService.ShotCompleteData data = dataQueryService.getCompleteData(shotNo);
        return ResponseEntity.ok(data);
    }
    
    /**
     * 获取指定炮号和通道的波形数据
     * 
     * 【HTTP请求示例】
     * GET http://localhost:8080/api/data/shots/1/wave?channel=NegVoltage&type=Tube
     * 
     * 【参数说明】
     * - channel: 通道名称（必填），如 NegVoltage, PosVoltage
     * - type: 数据类型（可选，默认Tube），Tube 或 Water
     * 
     * 【返回示例】
     * {
     *   "shotNo": 1,
     *   "channelName": "NegVoltage",
     *   "sampleRate": 1000000.0,
     *   "samples": 10000,
     *   "data": [0.1, 0.2, 0.3, ...]  // 波形数据点
     * }
     */
    @GetMapping("/shots/{shotNo}/wave")
    public ResponseEntity<WaveData> getWaveData(
            @PathVariable Integer shotNo,
            @RequestParam String channel,  // @RequestParam: 从URL查询参数中提取
            @RequestParam(defaultValue = "Tube") String type) {
        WaveData waveData = dataQueryService.getWaveData(shotNo, channel, type);
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
        return ResponseEntity.ok(dataQueryService.getChannelNames(shotNo, type));
    }
    
    /**
     * 获取指定炮号的操作日志
     * GET /api/data/shots/{shotNo}/logs/operation
     */
    @GetMapping("/shots/{shotNo}/logs/operation")
    public ResponseEntity<List<OperationLog>> getOperationLogs(
            @PathVariable Integer shotNo) {
        return ResponseEntity.ok(dataQueryService.getOperationLogs(shotNo));
    }
    
    /**
     * 获取指定炮号的PLC互锁日志
     * GET /api/data/shots/{shotNo}/logs/plc
     */
    @GetMapping("/shots/{shotNo}/logs/plc")
    public ResponseEntity<List<PlcInterlock>> getPlcInterlocks(
            @PathVariable Integer shotNo) {
        return ResponseEntity.ok(dataQueryService.getPlcInterlocks(shotNo));
    }
    
    /**
     * 获取数据库统计信息
     * GET /api/data/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(dataQueryService.getStatistics());
    }
    
    // ==================== 数据导入接口 ====================
    
    /**
     * 从文件导入指定炮号的数据到Kafka
     * POST /api/data/import/file/{shotNo}
     */
    @PostMapping("/import/file/{shotNo}")
    public ResponseEntity<Map<String, String>> importFromFile(@PathVariable Integer shotNo) {
        try {
            fileDataReader.readAndSendShot(shotNo);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "炮号 " + shotNo + " 数据已发送到Kafka"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 从文件导入所有炮号的数据到Kafka
     * POST /api/data/import/file/all
     */
    @PostMapping("/import/file/all")
    public ResponseEntity<Map<String, Object>> importAllFromFile() {
        try {
            List<Integer> shots = fileDataReader.getAllShotNumbers();
            fileDataReader.readAndSendAllShots();
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "所有炮号数据已发送到Kafka",
                "count", shots.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 获取文件系统中的炮号列表
     * GET /api/data/import/file/available
     */
    @GetMapping("/import/file/available")
    public ResponseEntity<List<Integer>> getAvailableFileShots() {
        return ResponseEntity.ok(fileDataReader.getAllShotNumbers());
    }
    
    // ==================== 网络接收接口 ====================
    
    /**
     * 通过API接收元数据并发送到Kafka
     * POST /api/data/receive/metadata
     */
    @PostMapping("/receive/metadata")
    public ResponseEntity<Map<String, String>> receiveMetadata(@RequestBody ShotMetadata metadata) {
        try {
            networkDataReceiver.receiveMetadata(metadata);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "元数据已发送到Kafka"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 通过API接收波形数据并发送到Kafka
     * POST /api/data/receive/wave
     */
    @PostMapping("/receive/wave")
    public ResponseEntity<Map<String, String>> receiveWaveData(@RequestBody WaveData waveData) {
        try {
            networkDataReceiver.receiveWaveData(waveData);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "波形数据已发送到Kafka"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 通过API接收操作日志并发送到Kafka
     * POST /api/data/receive/operation
     */
    @PostMapping("/receive/operation")
    public ResponseEntity<Map<String, String>> receiveOperationLog(@RequestBody OperationLog log) {
        try {
            networkDataReceiver.receiveOperationLog(log);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "操作日志已发送到Kafka"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 通过API接收PLC互锁并发送到Kafka
     * POST /api/data/receive/plc
     */
    @PostMapping("/receive/plc")
    public ResponseEntity<Map<String, String>> receivePlcInterlock(@RequestBody PlcInterlock interlock) {
        try {
            networkDataReceiver.receivePlcInterlock(interlock);
            return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "PLC互锁已发送到Kafka"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "status", "error",
                "message", e.getMessage()
            ));
        }
    }
    
    /**
     * 网络接收器状态
     * GET /api/data/receive/status
     */
    @GetMapping("/receive/status")
    public ResponseEntity<Map<String, Object>> getReceiverStatus() {
        return ResponseEntity.ok(Map.of(
            "tcpServerRunning", networkDataReceiver.isRunning()
        ));
    }
}
