package com.example.kafka.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * InfluxDB波形数据去重与清理服务
 * 
 * 功能:
 * 1. 检测并删除重复的波形数据点
 * 2. 验证数据完整性
 * 3. 提供清理报告
 */
@Service
public class InfluxDBCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(InfluxDBCleanupService.class);
    
    private final InfluxDBClient influxDBClient;
    private final String bucket;
    private final String org;
    
    public InfluxDBCleanupService(
            InfluxDBClient influxDBClient,
            @Value("${app.influxdb.bucket:waveforms}") String bucket,
            @Value("${app.influxdb.org:wavedata}") String org) {
        this.influxDBClient = influxDBClient;
        this.bucket = bucket;
        this.org = org;
    }
    
    /**
     * 清理指定shot和channel的重复数据
     * 
     * @param shotNo 炮号
     * @param channelName 通道名
     * @param expectedSamples 期望采样点数(来自MySQL元数据)
     * @return 清理报告
     */
    public CleanupReport cleanupDuplicates(Integer shotNo, String channelName, Integer expectedSamples) {
        CleanupReport report = new CleanupReport(shotNo, channelName, expectedSamples);
        
        try {
            // 1. 查询当前实际记录数
            long actualCount = countRecords(shotNo, channelName);
            report.setBeforeCount(actualCount);
            logger.info("炮{} 通道[{}] 数据点: 期望={}, 实际={}, 重复={}", 
                shotNo, channelName, expectedSamples, actualCount, actualCount - expectedSamples);
            
            if (actualCount <= expectedSamples) {
                report.setStatus("CLEAN");
                report.setMessage("No duplicates found");
                return report;
            }
            
            // 2. 使用delete API删除所有数据
            deleteAllRecords(shotNo, channelName);
            
            // 3. 验证删除成功
            long afterDeleteCount = countRecords(shotNo, channelName);
            report.setAfterDeleteCount(afterDeleteCount);
            
            if (afterDeleteCount > 0) {
                report.setStatus("DELETE_FAILED");
                report.setMessage(String.format("Delete failed, %d records remain", afterDeleteCount));
                logger.warn("删除失败: 炮{} 通道[{}] 仍有{}条记录", shotNo, channelName, afterDeleteCount);
                return report;
            }
            
            // 4. 标记需要重新写入(由调用方处理)
            report.setStatus("DELETED");
            report.setMessage("All records deleted successfully. Re-write required.");
            logger.info("清理成功: 炮{} 通道[{}] 已删除{}条重复记录", shotNo, channelName, actualCount);
            
        } catch (Exception e) {
            report.setStatus("ERROR");
            report.setMessage("Cleanup failed: " + e.getMessage());
            logger.error("清理失败: 炮{} 通道[{}]", shotNo, channelName, e);
        }
        
        return report;
    }
    
    /**
     * 统计指定shot和channel的记录数
     */
    private long countRecords(Integer shotNo, String channelName) {
        String flux = String.format(
            "from(bucket: \"%s\") " +
            "|> range(start: -30d) " +
            "|> filter(fn: (r) => r._measurement == \"waveform\") " +
            "|> filter(fn: (r) => r.shot_no == \"%d\") " +
            "|> filter(fn: (r) => r.channel_name == \"%s\") " +
            "|> filter(fn: (r) => r._field == \"value\") " +
            "|> count()",
            bucket, shotNo, channelName
        );
        
        List<FluxTable> tables = influxDBClient.getQueryApi().query(flux, org);
        if (tables.isEmpty() || tables.get(0).getRecords().isEmpty()) {
            return 0;
        }
        
        FluxRecord record = tables.get(0).getRecords().get(0);
        return ((Number) record.getValue()).longValue();
    }
    
    /**
     * 删除指定shot和channel的所有记录
     */
    private void deleteAllRecords(Integer shotNo, String channelName) {
        OffsetDateTime start = OffsetDateTime.now().minusDays(30);
        OffsetDateTime end = OffsetDateTime.now().plusDays(1);
        
        String predicate = String.format(
            "_measurement=\"waveform\" AND shot_no=\"%d\" AND channel_name=\"%s\"",
            shotNo, channelName
        );
        
        influxDBClient.getDeleteApi().delete(start, end, predicate, bucket, org);
        
        // Wait for deletion to propagate
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 清理报告
     */
    public static class CleanupReport {
        private final Integer shotNo;
        private final String channelName;
        private final Integer expectedSamples;
        private long beforeCount;
        private long afterDeleteCount;
        private String status;
        private String message;
        
        public CleanupReport(Integer shotNo, String channelName, Integer expectedSamples) {
            this.shotNo = shotNo;
            this.channelName = channelName;
            this.expectedSamples = expectedSamples;
        }
        
        public Integer getShotNo() { return shotNo; }
        public String getChannelName() { return channelName; }
        public Integer getExpectedSamples() { return expectedSamples; }
        public long getBeforeCount() { return beforeCount; }
        public void setBeforeCount(long beforeCount) { this.beforeCount = beforeCount; }
        public long getAfterDeleteCount() { return afterDeleteCount; }
        public void setAfterDeleteCount(long afterDeleteCount) { this.afterDeleteCount = afterDeleteCount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        
        public long getDuplicateCount() { 
            return beforeCount - expectedSamples; 
        }
        
        @Override
        public String toString() {
            return String.format("CleanupReport[shot=%d, channel=%s, expected=%d, actual=%d, duplicates=%d, status=%s]",
                shotNo, channelName, expectedSamples, beforeCount, getDuplicateCount(), status);
        }
    }
}
