package com.example.kafka.controller;

import com.example.kafka.consumer.DataConsumer;
import com.example.kafka.entity.WaveDataEntity;
import com.example.kafka.repository.WaveDataRepository;
import com.example.kafka.service.InfluxDBCleanupService;
import com.example.kafka.service.InfluxDBService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * InfluxDB数据清理与维护API
 * 
 * 功能:
 * 1. 检测并清理重复数据
 * 2. 批量修复所有炮数据
 * 3. 提供数据完整性报告
 */
@RestController
@RequestMapping("/api/influxdb")
public class InfluxDBMaintenanceController {
    
    private static final Logger logger = LoggerFactory.getLogger(InfluxDBMaintenanceController.class);
    
    @Autowired(required = false)
    private InfluxDBCleanupService cleanupService;
    
    @Autowired(required = false)
    private InfluxDBService influxDBService;
    
    @Autowired
    private WaveDataRepository waveDataRepository;
    
    /**
     * 清理指定炮的重复数据
     * 
     * GET /api/influxdb/cleanup?shotNo=1&channelName=FilaCurrent
     */
    @GetMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanup(
            @RequestParam Integer shotNo,
            @RequestParam String channelName) {
        
        Map<String, Object> response = new HashMap<>();
        
        if (cleanupService == null) {
            response.put("error", "InfluxDB未启用");
            return ResponseEntity.ok(response);
        }
        
        try {
            // 从MySQL获取元数据(期望采样点数)
            Optional<WaveDataEntity> waveDataOpt = waveDataRepository
                .findByShotNoAndChannelNameAndDataType(shotNo, channelName, "Water");
            
            if (!waveDataOpt.isPresent()) {
                response.put("error", "MySQL中未找到元数据");
                return ResponseEntity.ok(response);
            }
            
            WaveDataEntity waveData = waveDataOpt.get();
            Integer expectedSamples = waveData.getSamples();
            
            // 执行清理
            InfluxDBCleanupService.CleanupReport report = cleanupService.cleanupDuplicates(
                shotNo, channelName, expectedSamples
            );
            
            response.put("report", report);
            response.put("needsRewrite", "DELETED".equals(report.getStatus()));
            
            // 如果清理成功,重新写入数据
            if ("DELETED".equals(report.getStatus())) {
                logger.info("正在重新写入数据: shot_no={}, channel_name={}", shotNo, channelName);
                influxDBService.writeWaveData(
                    shotNo,
                    channelName,
                    waveData.getDataType(),
                    waveData.getFileSource(),
                    waveData.getSampleRate(),
                    waveData.getStartTime(),
                    DataConsumer.decompressWaveData(waveData.getData())
                );
                response.put("rewritten", true);
            }
            
        } catch (Exception e) {
            logger.error("清理失败: shot_no={}, channel_name={}", shotNo, channelName, e);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 批量修复所有炮数据
     * 
     * POST /api/influxdb/fix-all
     */
    @PostMapping("/fix-all")
    public ResponseEntity<Map<String, Object>> fixAll() {
        
        Map<String, Object> response = new HashMap<>();
        
        if (cleanupService == null || influxDBService == null) {
            response.put("error", "InfluxDB未启用");
            return ResponseEntity.ok(response);
        }
        
        try {
            // 获取所有Water类型数据
            List<WaveDataEntity> allWaveData = waveDataRepository.findByDataType("Water");
            logger.info("开始批量修复, 总记录数: {}", allWaveData.size());
            
            List<InfluxDBCleanupService.CleanupReport> reports = new ArrayList<>();
            int fixedCount = 0;
            int errorCount = 0;
            
            for (WaveDataEntity waveData : allWaveData) {
                try {
                    // 检查并清理
                    InfluxDBCleanupService.CleanupReport report = cleanupService.cleanupDuplicates(
                        waveData.getShotNo(),
                        waveData.getChannelName(),
                        waveData.getSamples()
                    );
                    
                    reports.add(report);
                    
                    // 如果有重复,重新写入
                    if ("DELETED".equals(report.getStatus())) {
                        influxDBService.writeWaveData(
                            waveData.getShotNo(),
                            waveData.getChannelName(),
                            waveData.getDataType(),
                            waveData.getFileSource(),
                            waveData.getSampleRate(),
                            waveData.getStartTime(),
                            DataConsumer.decompressWaveData(waveData.getData())
                        );
                        fixedCount++;
                        logger.info("已修复: shot_no={}, channel_name={}", 
                            waveData.getShotNo(), waveData.getChannelName());
                    }
                    
                } catch (Exception e) {
                    errorCount++;
                    logger.error("修复失败: shot_no={}, channel_name={}", 
                        waveData.getShotNo(), waveData.getChannelName(), e);
                }
            }
            
            response.put("total", allWaveData.size());
            response.put("fixed", fixedCount);
            response.put("errors", errorCount);
            response.put("reports", reports);
            
            logger.info("批量修复完成: 总数={}, 已修复={}, 错误={}", 
                allWaveData.size(), fixedCount, errorCount);
            
        } catch (Exception e) {
            logger.error("批量修复失败", e);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 获取数据完整性报告
     * 
     * GET /api/influxdb/integrity-report
     */
    @GetMapping("/integrity-report")
    public ResponseEntity<Map<String, Object>> integrityReport() {
        
        Map<String, Object> response = new HashMap<>();
        
        if (cleanupService == null) {
            response.put("error", "InfluxDB未启用");
            return ResponseEntity.ok(response);
        }
        
        try {
            List<WaveDataEntity> allWaveData = waveDataRepository.findByDataType("Water");
            
            List<Map<String, Object>> issues = new ArrayList<>();
            int totalDuplicates = 0;
            
            for (WaveDataEntity waveData : allWaveData) {
                InfluxDBCleanupService.CleanupReport report = cleanupService.cleanupDuplicates(
                    waveData.getShotNo(),
                    waveData.getChannelName(),
                    waveData.getSamples()
                );
                
                if (report.getDuplicateCount() > 0) {
                    Map<String, Object> issue = new HashMap<>();
                    issue.put("shotNo", report.getShotNo());
                    issue.put("channelName", report.getChannelName());
                    issue.put("expectedSamples", report.getExpectedSamples());
                    issue.put("actualSamples", report.getBeforeCount());
                    issue.put("duplicates", report.getDuplicateCount());
                    issue.put("duplicationRate", 
                        Math.round((double) report.getBeforeCount() / report.getExpectedSamples() * 100.0) / 100.0);
                    issues.add(issue);
                    
                    totalDuplicates += report.getDuplicateCount();
                }
            }
            
            response.put("total", allWaveData.size());
            response.put("issuesFound", issues.size());
            response.put("totalDuplicates", totalDuplicates);
            response.put("issues", issues);
            
        } catch (Exception e) {
            logger.error("完整性报告生成失败", e);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}
