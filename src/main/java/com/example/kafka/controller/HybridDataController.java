package com.example.kafka.controller;

import com.example.kafka.entity.OperationLogEntity;
import com.example.kafka.entity.ShotMetadataEntity;
import com.example.kafka.entity.WaveDataEntity;
import com.example.kafka.repository.OperationLogRepository;
import com.example.kafka.repository.ShotMetadataRepository;
import com.example.kafka.repository.WaveDataRepository;
import com.example.kafka.service.InfluxDBService;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
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
    
    private static final Logger logger = LoggerFactory.getLogger(HybridDataController.class);
    
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
     * GET /api/hybrid/shots/1
     */
    @GetMapping("/shots/{shotNo}")
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
            // 简化查询：只取value字段，按时间排序后限制点数（避免超时）
            String flux = String.format(
                "from(bucket: \"%s\") " +
                "|> range(start: -30d) " +
                "|> filter(fn: (r) => r._measurement == \"waveform\") " +
                "|> filter(fn: (r) => r.shot_no == \"%d\") " +
                "|> filter(fn: (r) => r.channel_name == \"%s\") " +
                "|> filter(fn: (r) => r._field == \"value\") " +
                "|> sort(columns: [\"_time\"]) " +
                "|> limit(n: 50000)",
                bucket, shotNo, channelName
            );
            
            List<FluxTable> tables = influxDBClient.getQueryApi().query(flux, org);
            
            // 使用LinkedHashMap进行时间戳去重(保持顺序,相同时间戳只保留最后一个值)
            Map<Long, Double> dedupMap = new LinkedHashMap<>();
            int rawCount = 0;
            
            for (FluxTable table : tables) {
                for (FluxRecord record : table.getRecords()) {
                    rawCount++;
                    Object value = record.getValue();
                    Instant timestamp = record.getTime();
                    if (value instanceof Number && timestamp != null) {
                        // 使用纳秒时间戳作为key实现精确去重
                        long nanos = timestamp.getEpochSecond() * 1_000_000_000 + timestamp.getNano();
                        dedupMap.put(nanos, ((Number) value).doubleValue());
                    }
                }
            }
            
            // 转换为列表(已去重并保持时间顺序)
            List<Double> values = new ArrayList<>(dedupMap.values());
            double duplicationRate = rawCount > 0 ? (double) rawCount / values.size() : 1.0;
            
            if (duplicationRate > 1.05) {  // 重复率超过5%时告警
                logger.warn("检测到数据重复: shot_no={}, channel_name={}, 原始点数={}, 去重后={}, 重复率={}x", 
                    shotNo, channelName, rawCount, values.size(), 
                    Math.round(duplicationRate * 100.0) / 100.0);
            }
            
            result.put("shotNo", shotNo);
            result.put("channelName", channelName);
            result.put("data", values);
            result.put("samples", values.size());          // 去重后的点数
            result.put("rawSamples", rawCount);            // 原始点数
            result.put("duplicationRate", Math.round(duplicationRate * 100.0) / 100.0);
            
            logger.debug("InfluxDB查询完成: shot_no={}, channel_name={}, 原始点数={}, 去重后={}", 
                shotNo, channelName, rawCount, values.size());
            
            // 保留原有的timestamps字段(用于调试)
            List<String> timestamps = new ArrayList<>();
            tables.stream()
                .flatMap(table -> table.getRecords().stream())
                .forEach(record -> {
                    if (record.getTime() != null) {
                        timestamps.add(record.getTime().toString());
                    }
                });
            result.put("timestamps", timestamps);
            result.put("source", "influxdb");
            
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    /**     * 获取指定炮号的通道列表
     * GET /api/hybrid/shots/1/channels?type=Tube
     */
    @GetMapping("/shots/{shotNo}/channels")
    public ResponseEntity<List<String>> getChannels(
            @PathVariable Integer shotNo,
            @RequestParam(defaultValue = "Tube") String type) {
        // 从MySQL获取通道列表
        List<WaveDataEntity> channels = waveDataRepository.findByShotNo(shotNo);
        List<String> channelNames = channels.stream()
            .filter(ch -> type.equals(ch.getDataType()))
            .map(WaveDataEntity::getChannelName)
            .distinct()
            .collect(Collectors.toList());
        return ResponseEntity.ok(channelNames);
    }
    
    /**     * 混合查询：元数据从MySQL，波形从InfluxDB
     * GET /api/hybrid/shots/1/complete
     */
    @GetMapping("/shots/{shotNo}/complete")
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
