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

import java.util.ArrayList;
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

    @Autowired
    private javax.sql.DataSource dataSource;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;
    
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

    /**
     * 列出当前数据库中的表名
     * GET /api/database/tables
     */
    @GetMapping("/tables")
    public ResponseEntity<List<String>> listTables() {
        String sql = "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()";
        List<String> tables = jdbcTemplate.queryForList(sql, String.class);
        return ResponseEntity.ok(tables);
    }

    /**
     * 获取表的前几行数据（分页）
     * GET /api/database/tables/{table}?limit=100&offset=0
     */
    @GetMapping("/tables/{table}")
    public ResponseEntity<Object> getTableRows(
            @PathVariable String table,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        // 基本校验：表名只允许字母、数字和下划线
        if (!table.matches("[A-Za-z0-9_]+")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid table name"));
        }
        // 验证表名确实存在
        String checkSql = "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?";
        Integer exists = jdbcTemplate.queryForObject(checkSql, new Object[]{table}, Integer.class);
        if (exists == null || exists == 0) {
            return ResponseEntity.status(404).body(Map.of("error", "Table not found"));
        }

        // 使用参数化查询（注意：表名不能作为参数，已通过白名单校验）
        String sql = String.format("SELECT * FROM `%s` LIMIT %d OFFSET %d", table, limit, offset);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql);

        // 返回列名和行数据
        List<String> columns = rows.isEmpty() ? List.of() : new ArrayList<>(rows.get(0).keySet());
        Map<String, Object> result = new HashMap<>();
        result.put("columns", columns);
        result.put("rows", rows);
        result.put("limit", limit);
        result.put("offset", offset);
        return ResponseEntity.ok(result);
    }
}
