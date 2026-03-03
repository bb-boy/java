package com.example.kafka.service;

import com.example.kafka.datasource.FileDataSource;
import com.example.kafka.model.*;
import com.example.kafka.producer.DataProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据管道服务 - 协调完整的数据流
 * 
 * 【核心职责】
 * 实现"文件 → Kafka → 数据库 → 前端"的完整数据链路：
 * 
 * 1. 从FileDataSource读取TDMS文件和日志
 * 2. 通过DataProducer发送到Kafka主题
 * 3. DataConsumer自动监听Kafka并存入数据库
 * 4. 前端通过 /api/hybrid 或 /api/database 从数据库查询
 * 
 * 【使用场景】
 * - 定时任务：每隔一段时间自动同步新数据
 * - API触发：管理员手动点击按钮同步
 * - 启动初始化：应用启动时预加载关键数据
 */
@Service
public class DataPipelineService {
    
    private static final Logger logger = LoggerFactory.getLogger(DataPipelineService.class);
    private static final String STAGE_READ_METADATA = "READ_METADATA";
    private static final String STAGE_PIPELINE_EXCEPTION = "PIPELINE_EXCEPTION";
    
    @Autowired
    private FileDataSource fileDataSource;  // 文件数据源
    
    @Autowired
    private DataProducer dataProducer;      // Kafka生产者

    @Autowired
    private TdmsEventGeneratorService tdmsEventGeneratorService;
    
    /**
     * 同步单个炮号的所有数据到Kafka（会自动触发DataConsumer存入数据库）
     * 
     * 【数据流详解】
     * 1. 从磁盘读取炮号1的数据（TDMS文件、日志）
     * 2. 调用 dataProducer.sendMetadata() 发送元数据到 "shot-metadata" 主题
     * 3. 调用 dataProducer.sendWaveData() 发送波形数据到 "wave-data" 主题
     * 4. Kafka收到消息，分发到各分区（多副本容错）
     * 5. DataConsumer.java 中的 @KafkaListener 监听到消息
     * 6. DataConsumer 将消息反序列化，调用 metadataRepository.save() 存入H2数据库
     * 7. 前端调用 /api/hybrid/shots 查询数据库
     * 
     * @param shotNo 炮号
     * @return 同步结果统计
     */
    public SyncResult syncShotToKafka(Integer shotNo) {
        logger.info("========================================");
        logger.info("开始同步炮号 {} 的数据到Kafka", shotNo);
        logger.info("========================================");
        
        SyncResult result = new SyncResult(shotNo);
        
        try {
            // ==================================================
            // 步骤1: 读取元数据（从磁盘上的文件解析）
            // ==================================================
            logger.info("【步骤1/4】读取炮号 {} 的元数据...", shotNo);
            ShotMetadata metadata = fileDataSource.getShotMetadata(shotNo);
            
            if (metadata == null) {
                logger.warn("炮号 {} 的元数据不存在，跳过", shotNo);
                result.setSuccess(false);
                result.setErrorMessage("元数据不存在");
                dataProducer.sendSyncFailure(shotNo, STAGE_READ_METADATA, "元数据不存在", null);
                return result;
            }
            
            logger.info("  ✓ 元数据读取成功: 状态={}, 采样率={} Hz, 时长={} s", 
                       metadata.getStatus(), 
                       metadata.getSampleRate(), 
                       metadata.getActualDuration());
            
            // ==================================================
            // 步骤2: 发送元数据到Kafka
            // ==================================================
            logger.info("【步骤2/4】发送元数据到Kafka主题: shot-metadata");
            dataProducer.sendMetadata(metadata)
                .thenRun(() -> {
                    result.incrementMetadata();
                    logger.info("  ✓ 元数据已发送到Kafka，等待DataConsumer消费...");
                })
                .exceptionally(ex -> {
                    logger.error("  ✗ 元数据发送失败: {}", ex.getMessage());
                    return null;
                })
                .join();  // 阻塞等待完成
            
            // ==================================================
            // 步骤3: 读取并发送Tube波形数据
            // ==================================================
            logger.info("【步骤3/4】读取并发送Tube波形数据...");
            List<String> tubeChannels = fileDataSource.getChannelNames(shotNo, "Tube");
            logger.info("  发现 {} 个Tube通道: {}", tubeChannels.size(), tubeChannels);

            WaveData primaryWaveData = null;
            
            for (String channel : tubeChannels) {
                WaveData waveData = fileDataSource.getWaveData(shotNo, channel, "Tube");
                applyMetadataToWaveData(metadata, waveData);
                
                if (waveData != null && waveData.getData() != null && !waveData.getData().isEmpty()) {
                    dataProducer.sendWaveData(waveData)
                        .thenRun(() -> {
                            result.incrementWaveData();
                            logger.debug("    ✓ 通道 {} 波形数据已发送 ({} 个样本点)", 
                                        channel, waveData.getData().size());
                        })
                        .exceptionally(ex -> {
                            logger.error("    ✗ 通道 {} 发送失败: {}", channel, ex.getMessage());
                            return null;
                        })
                        .join();
                    if (primaryWaveData == null) {
                        primaryWaveData = waveData;
                    }
                } else {
                    logger.debug("    - 通道 {} 无数据，跳过", channel);
                }
            }
            
            // ==================================================
            // 步骤4: 读取并发送Water波形数据
            // ==================================================
            logger.info("【步骤4/4】读取并发送Water波形数据...");
            List<String> waterChannels = fileDataSource.getChannelNames(shotNo, "Water");
            logger.info("  发现 {} 个Water通道", waterChannels.size());
            
            for (String channel : waterChannels) {
                WaveData waveData = fileDataSource.getWaveData(shotNo, channel, "Water");
                applyMetadataToWaveData(metadata, waveData);
                
                if (waveData != null && waveData.getData() != null && !waveData.getData().isEmpty()) {
                    dataProducer.sendWaveData(waveData)
                        .thenRun(() -> {
                            result.incrementWaveData();
                            logger.debug("    ✓ 通道 {} 波形数据已发送 ({} 个样本点)", 
                                        channel, waveData.getData().size());
                        })
                        .exceptionally(ex -> {
                            logger.error("    ✗ 通道 {} 发送失败: {}", channel, ex.getMessage());
                            return null;
                        })
                        .join();
                    if (primaryWaveData == null) {
                        primaryWaveData = waveData;
                    }
                } else {
                    logger.debug("    - 通道 {} 无数据，跳过", channel);
                }
            }
            
            // ==================================================
            // 步骤5: 从TDMS波形生成操作日志与保护事件
            // ==================================================
            if (primaryWaveData != null && primaryWaveData.getData() != null
                && !primaryWaveData.getData().isEmpty()) {
                TdmsEventGeneratorService.GenerationResult generated =
                    tdmsEventGeneratorService.generateFromWaveData(metadata, primaryWaveData, true);
                result.addOperationLog(generated.operationCount());
                result.addProtectionEvent(generated.protectionCount());
                logger.info("【TDMS派生】操作日志 {} 条, 保护事件 {} 条", 
                    generated.operationCount(), generated.protectionCount());
            } else {
                logger.warn("【TDMS派生】未找到可用波形，跳过操作日志/保护事件生成: shotNo={}", shotNo);
            }
            
            // ==================================================
            // 步骤6: 发送PLC互锁日志（如果存在）
            // ==================================================
            List<PlcInterlock> plcLogs = fileDataSource.getPlcInterlocks(shotNo);
            if (plcLogs != null && !plcLogs.isEmpty()) {
                logger.info("【额外】发送PLC互锁日志: {} 条", plcLogs.size());
                for (PlcInterlock plc : plcLogs) {
                    dataProducer.sendPlcInterlock(plc)
                        .thenRun(() -> result.incrementPlcInterlock())
                        .join();
                }
            }
            
            result.setSuccess(true);
            logger.info("========================================");
            logger.info("✓ 炮号 {} 同步完成! 统计信息:", shotNo);
            logger.info("  - 元数据: {} 条", result.getMetadataCount());
            logger.info("  - 波形数据: {} 条", result.getWaveDataCount());
            logger.info("  - 操作日志: {} 条", result.getOperationLogCount());
            logger.info("  - 保护事件: {} 条", result.getProtectionEventCount());
            logger.info("  - PLC互锁: {} 条", result.getPlcInterlockCount());
            logger.info("========================================");
            
        } catch (Exception e) {
            logger.error("同步炮号 {} 时发生异常: {}", shotNo, e.getMessage(), e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
            dataProducer.sendSyncFailure(shotNo, STAGE_PIPELINE_EXCEPTION, e.getMessage(), e);
        }
        
        return result;
    }

    private void applyMetadataToWaveData(ShotMetadata metadata, WaveData waveData) {
        if (metadata == null || waveData == null) {
            return;
        }
        if (waveData.getSampleRate() == null || waveData.getSampleRate() <= 0) {
            waveData.setSampleRate(metadata.getSampleRate());
        }
        if (waveData.getStartTime() == null && metadata.getStartTime() != null) {
            waveData.setStartTime(metadata.getStartTime());
        }
        if (waveData.getEndTime() == null && metadata.getEndTime() != null) {
            waveData.setEndTime(metadata.getEndTime());
        }
    }
    
    /**
     * 批量同步多个炮号
     * 
     * @param shotNumbers 炮号列表
     * @return 总体同步结果
     */
    public BatchSyncResult syncMultipleShotsToKafka(List<Integer> shotNumbers) {
        logger.info("========================================");
        logger.info("开始批量同步 {} 个炮号到Kafka", shotNumbers.size());
        logger.info("炮号列表: {}", shotNumbers);
        logger.info("========================================");
        
        BatchSyncResult batchResult = new BatchSyncResult();
        
        for (Integer shotNo : shotNumbers) {
            SyncResult result = syncShotToKafka(shotNo);
            batchResult.addResult(result);
            
            // 每个炮号之间稍微延迟，避免Kafka压力过大
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        logger.info("========================================");
        logger.info("✓ 批量同步完成!");
        logger.info("  - 成功: {} 个", batchResult.getSuccessCount());
        logger.info("  - 失败: {} 个", batchResult.getFailureCount());
        logger.info("  - 总元数据: {} 条", batchResult.getTotalMetadata());
        logger.info("  - 总波形数据: {} 条", batchResult.getTotalWaveData());
        logger.info("========================================");
        
        return batchResult;
    }
    
    /**
     * 同步所有可用炮号
     */
    public BatchSyncResult syncAllShotsToKafka() {
        List<Integer> allShots = fileDataSource.getAllShotNumbers();
        logger.info("发现 {} 个炮号，准备全量同步", allShots.size());
        return syncMultipleShotsToKafka(allShots);
    }
    
    // ========================================================================
    // 定时任务（可选）- 自动同步新数据
    // ========================================================================
    
    /**
     * 定时任务：每隔5分钟检查并同步新数据
     * 
     * 【使用方式】
     * 1. 取消下面的注释即可启用
     * 2. @Scheduled注解配置：
     *    - fixedDelay: 上次执行完成后等待N毫秒再执行
     *    - fixedRate: 每隔N毫秒执行一次（不管上次是否完成）
     *    - cron: 使用cron表达式定时（如 "0 0 * * * ?" 每小时）
     * 
     * 【当前配置】
     * - fixedDelay = 300000 (5分钟)
     * - initialDelay = 60000 (启动1分钟后首次执行)
     */
    // @Scheduled(fixedDelay = 300000, initialDelay = 60000)
    public void autoSyncTask() {
        logger.info("========================================");
        logger.info("定时任务：开始自动同步数据");
        logger.info("========================================");
        
        try {
            // 这里可以实现增量同步逻辑
            // 例如：只同步最近24小时的炮号
            // 或者：检查数据库，只同步不存在的炮号
            
            List<Integer> allShots = fileDataSource.getAllShotNumbers();
            logger.info("发现 {} 个炮号", allShots.size());
            
            // 示例：只同步前3个炮号（避免定时任务压力过大）
            List<Integer> shotsToSync = allShots.stream()
                .limit(3)
                .toList();
            
            BatchSyncResult result = syncMultipleShotsToKafka(shotsToSync);
            
            logger.info("定时同步完成：成功 {} 个，失败 {} 个", 
                       result.getSuccessCount(), 
                       result.getFailureCount());
            
        } catch (Exception e) {
            logger.error("定时同步异常", e);
        }
    }
    
    // ========================================================================
    // 内部类：同步结果统计
    // ========================================================================
    
    /**
     * 单个炮号的同步结果
     */
    public static class SyncResult {
        private Integer shotNo;
        private boolean success;
        private String errorMessage;
        private AtomicInteger metadataCount = new AtomicInteger(0);
        private AtomicInteger waveDataCount = new AtomicInteger(0);
        private AtomicInteger operationLogCount = new AtomicInteger(0);
        private AtomicInteger protectionEventCount = new AtomicInteger(0);
        private AtomicInteger plcInterlockCount = new AtomicInteger(0);
        
        public SyncResult(Integer shotNo) {
            this.shotNo = shotNo;
        }
        
        public void incrementMetadata() { metadataCount.incrementAndGet(); }
        public void incrementWaveData() { waveDataCount.incrementAndGet(); }
        public void incrementOperationLog() { operationLogCount.incrementAndGet(); }
        public void addOperationLog(int count) { operationLogCount.addAndGet(Math.max(0, count)); }
        public void addProtectionEvent(int count) { protectionEventCount.addAndGet(Math.max(0, count)); }
        public void incrementPlcInterlock() { plcInterlockCount.incrementAndGet(); }
        
        // Getters and setters
        public Integer getShotNo() { return shotNo; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public int getMetadataCount() { return metadataCount.get(); }
        public int getWaveDataCount() { return waveDataCount.get(); }
        public int getOperationLogCount() { return operationLogCount.get(); }
        public int getProtectionEventCount() { return protectionEventCount.get(); }
        public int getPlcInterlockCount() { return plcInterlockCount.get(); }
    }
    
    /**
     * 批量同步结果
     */
    public static class BatchSyncResult {
        private List<SyncResult> results = new ArrayList<>();
        
        public void addResult(SyncResult result) {
            results.add(result);
        }
        
        public int getSuccessCount() {
            return (int) results.stream().filter(SyncResult::isSuccess).count();
        }
        
        public int getFailureCount() {
            return (int) results.stream().filter(r -> !r.isSuccess()).count();
        }
        
        public int getTotalMetadata() {
            return results.stream().mapToInt(SyncResult::getMetadataCount).sum();
        }
        
        public int getTotalWaveData() {
            return results.stream().mapToInt(SyncResult::getWaveDataCount).sum();
        }
        
        public List<SyncResult> getResults() { return results; }
    }
}
