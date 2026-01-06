package com.example.kafka.controller;

import com.example.kafka.entity.OperationLogEntity;
import com.example.kafka.entity.ShotMetadataEntity;
import com.example.kafka.entity.WaveDataEntity;
import com.example.kafka.repository.OperationLogRepository;
import com.example.kafka.repository.ShotMetadataRepository;
import com.example.kafka.repository.WaveDataRepository;
import com.example.kafka.service.InfluxDBService;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 混合数据查询控制器
 * 
 * 同时从MySQL和InfluxDB查询数据
 * - MySQL: 元数据、通道列表等结构化数据
 * - InfluxDB: 波形时序数据
 */
@RestController
@RequestMapping("/api/hybrid")
@CrossOrigin(origins = "*")
public class HybridDataController {
    
    @Autowired
    private ShotMetadataRepository metadataRepository;
    
    @Autowired
    private WaveDataRepository waveDataRepository;
    
    @Autowired
    private OperationLogRepository operationLogRepository;
    
    @Autowired(required = false)
    private InfluxDBClient influxDBClient;
    
    @Autowired(required = false)
    @Qualifier("influxDBBucket")
    private String bucket;
    
    @Autowired(required = false)
    @Qualifier("influxDBOrg")
    private String org;
    
    /**
     * 获取所有炮号列表（从wave_data表，确保包含所有炮号）
     * GET /api/hybrid/shots
     */
    @GetMapping("/shots")
    public ResponseEntity<List<Integer>> getAllShots() {
        // 从wave_data表获取所有炮号，避免metadata表主键冲突导致数据缺失
        List<Integer> shotNumbers = waveDataRepository.findAll()
            .stream()
            .map(WaveDataEntity::getShotNo)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(shotNumbers);
    }
    
    /**
     * 获取数据统计（从两个数据库）
     * GET /api/hybrid/stats
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // MySQL统计
        stats.put("mysql", Map.of(
            "metadata", metadataRepository.count(),
            "wavedata", waveDataRepository.count()
        ));
        
        // InfluxDB统计
        if (influxDBClient != null) {
            try {
                String flux = String.format(
                    "from(bucket: \"%s\") " +
                    "|> range(start: -30d) " +
                    "|> filter(fn: (r) => r._measurement == \"waveform\") " +
                    "|> group() " +
                    "|> count()",
                    bucket
                );
                
                List<FluxTable> tables = influxDBClient.getQueryApi().query(flux, org);
                long influxCount = tables.stream()
                    .flatMap(table -> table.getRecords().stream())
                    .mapToLong(record -> ((Number) record.getValue()).longValue())
                    .sum();
                
                stats.put("influxdb", Map.of(
                    "datapoints", influxCount,
                    "status", "connected"
                ));
            } catch (Exception e) {
                stats.put("influxdb", Map.of(
                    "status", "error",
                    "message", e.getMessage()
                ));
            }
        } else {
            stats.put("influxdb", Map.of("status", "disabled"));
        }
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * 获取指定炮号的完整数据
     * MySQL: 元数据、通道列表
     * InfluxDB: 波形数据
     * 
     * GET /api/hybrid/shot/1
     */
    @GetMapping("/shot/{shotNo}")
    public ResponseEntity<Map<String, Object>> getShotData(@PathVariable Integer shotNo) {
        Map<String, Object> result = new HashMap<>();
        
        // 从MySQL获取元数据
        List<ShotMetadataEntity> metadata = metadataRepository.findAll()
            .stream()
            .filter(m -> m.getShotNo().equals(shotNo))
            .collect(Collectors.toList());
        
        if (!metadata.isEmpty()) {
            result.put("metadata", metadata.get(0));
        }
        
        // 从MySQL获取通道列表
        List<WaveDataEntity> channels = waveDataRepository.findByShotNo(shotNo);
        List<Map<String, Object>> channelList = channels.stream()
            .map(ch -> {
                Map<String, Object> channelInfo = new HashMap<>();
                channelInfo.put("channelName", ch.getChannelName());
                channelInfo.put("dataType", ch.getDataType());
                channelInfo.put("samples", ch.getSamples());
                channelInfo.put("sampleRate", ch.getSampleRate());
                channelInfo.put("source", "mysql");
                return channelInfo;
            })
            .collect(Collectors.toList());
        
        result.put("channels", channelList);
        result.put("channelCount", channelList.size());
        
        // 从InfluxDB查询可用通道
        if (influxDBClient != null) {
            try {
                String flux = String.format(
                    "from(bucket: \"%s\") " +
                    "|> range(start: -30d) " +
                    "|> filter(fn: (r) => r._measurement == \"waveform\") " +
                    "|> filter(fn: (r) => r.shot_no == \"%d\") " +
                    "|> group(columns: [\"channel_name\", \"data_type\"]) " +
                    "|> count() " +
                    "|> keep(columns: [\"channel_name\", \"data_type\", \"_value\"])",
                    bucket, shotNo
                );
                
                List<FluxTable> tables = influxDBClient.getQueryApi().query(flux, org);
                List<Map<String, Object>> influxChannels = tables.stream()
                    .flatMap(table -> table.getRecords().stream())
                    .map(record -> {
                        Map<String, Object> channelInfo = new HashMap<>();
                        channelInfo.put("channelName", record.getValueByKey("channel_name"));
                        channelInfo.put("dataType", record.getValueByKey("data_type"));
                        channelInfo.put("datapoints", record.getValue());
                        channelInfo.put("source", "influxdb");
                        return channelInfo;
                    })
                    .collect(Collectors.toList());
                
                result.put("influxChannels", influxChannels);
            } catch (Exception e) {
                result.put("influxError", e.getMessage());
            }
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取指定通道的波形数据（从InfluxDB）
     * 
     * GET /api/hybrid/waveform?shotNo=1&channelName=InPower
     */
    @GetMapping("/waveform")
    public ResponseEntity<Map<String, Object>> getWaveform(
            @RequestParam Integer shotNo,
            @RequestParam String channelName) {
        
        Map<String, Object> result = new HashMap<>();
        
        if (influxDBClient == null) {
            result.put("error", "InfluxDB未启用");
            return ResponseEntity.ok(result);
        }
        
        try {
            String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -30d) " +
                "|> filter(fn: (r) => r._measurement == \"waveform\") " +
                "|> filter(fn: (r) => r.shot_no == \"%d\") " +
                "|> filter(fn: (r) => r.channel_name == \"%s\") " +
                "|> filter(fn: (r) => r._field == \"value\") " +
                "|> sort(columns: [\"_time\"]) " +
                "|> limit(n: 20000)",  // 限制返回点数
                bucket, shotNo, channelName
            );
            
            List<FluxTable> tables = influxDBClient.getQueryApi().query(flux, org);
            
            List<Double> values = new ArrayList<>();
            List<String> timestamps = new ArrayList<>();
            
            tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .forEach(record -> {
                    Object value = record.getValue();
                    if (value instanceof Number) {
                        values.add(((Number) value).doubleValue());
                        timestamps.add(record.getTime().toString());
                    }
                });
            
            result.put("shotNo", shotNo);
            result.put("channelName", channelName);
            result.put("data", values);
            result.put("timestamps", timestamps);
            result.put("samples", values.size());
            result.put("source", "influxdb");
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 混合查询：元数据从MySQL，波形从InfluxDB
     * GET /api/hybrid/shot/1/complete
     */
    @GetMapping("/shot/{shotNo}/complete")
    public ResponseEntity<Map<String, Object>> getCompleteData(@PathVariable Integer shotNo) {
        Map<String, Object> result = new HashMap<>();
        
        // MySQL: 元数据
        List<ShotMetadataEntity> metadata = metadataRepository.findAll()
            .stream()
            .filter(m -> m.getShotNo().equals(shotNo))
            .collect(Collectors.toList());
        
        if (!metadata.isEmpty()) {
            result.put("metadata", metadata.get(0));
        }
        
        // MySQL: 通道信息
        List<WaveDataEntity> channels = waveDataRepository.findByShotNo(shotNo);
        result.put("channels", channels.stream()
            .map(ch -> ch.getChannelName())
            .collect(Collectors.toList()));
        
        // InfluxDB: 获取第一个通道的波形数据作为示例
        if (!channels.isEmpty() && influxDBClient != null) {
            try {
                String firstChannel = channels.get(0).getChannelName();
                ResponseEntity<Map<String, Object>> waveResponse = getWaveform(shotNo, firstChannel);
                result.put("sampleWaveform", waveResponse.getBody());
            } catch (Exception e) {
                result.put("waveformError", e.getMessage());
            }
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * 获取指定炮号的操作日志时间线
     * GET /api/hybrid/timeline/{shotNo}
     */
    @GetMapping("/timeline/{shotNo}")
    public ResponseEntity<Map<String, Object>> getTimeline(@PathVariable Integer shotNo) {
        Map<String, Object> result = new HashMap<>();
        
        // 从MySQL获取操作日志
        List<OperationLogEntity> logs = operationLogRepository.findByShotNoOrderByTimestampAsc(shotNo);
        
        result.put("shotNo", shotNo);
        result.put("totalEvents", logs.size());
        result.put("events", logs.stream().map(log -> {
            Map<String, Object> event = new HashMap<>();
            event.put("timestamp", log.getTimestamp());
            event.put("operation", log.getOperationType());
            event.put("channelName", log.getChannelName());
            event.put("description", log.getStepType());
            event.put("status", log.getOldValue() != null ? "数据变更" : "操作记录");
            event.put("duration", log.getDelta());
            return event;
        }).collect(Collectors.toList()));
        
        return ResponseEntity.ok(result);
    }
}
