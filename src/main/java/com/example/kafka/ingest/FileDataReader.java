package com.example.kafka.ingest;

import com.example.kafka.model.*;
import com.example.kafka.producer.DataProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文件数据读取器 - 从本地文件读取数据并发送到Kafka
 */
@Service
public class FileDataReader {
    
    private static final Logger logger = LoggerFactory.getLogger(FileDataReader.class);
    
    @Autowired
    private DataProducer dataProducer;
    
    @Value("${app.data.tube.path:data/TUBE}")
    private String tubePath;
    
    @Value("${app.data.logs.path:data/TUBE_logs}")
    private String logsPath;
    
    @Value("${app.data.plc.path:data/PLC_logs}")
    private String plcPath;
    
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    /**
     * 读取并发送指定炮号的所有数据
     */
    public void readAndSendShot(Integer shotNo) {
        logger.info("开始读取炮号 {} 的数据", shotNo);
        
        // 1. 发送元数据
        ShotMetadata metadata = readMetadata(shotNo);
        if (metadata != null) {
            dataProducer.sendMetadata(metadata);
        }
        
        // 2. 发送操作日志
        List<OperationLog> logs = readOperationLogs(shotNo);
        if (!logs.isEmpty()) {
            dataProducer.sendOperationLogBatch(logs);
        }
        
        // 3. 发送PLC互锁
        List<PlcInterlock> interlocks = readPlcInterlocks(shotNo);
        if (!interlocks.isEmpty()) {
            dataProducer.sendPlcInterlockBatch(interlocks);
        }
        
        // 4. 发送波形数据 (需要Python处理TDMS,这里发送文件路径信息)
        sendWaveDataInfo(shotNo);
        
        logger.info("炮号 {} 数据读取完成", shotNo);
    }
    
    /**
     * 读取并发送所有炮号的数据
     */
    public void readAndSendAllShots() {
        List<Integer> shotNumbers = getAllShotNumbers();
        logger.info("开始批量读取 {} 个炮号的数据", shotNumbers.size());
        
        for (Integer shotNo : shotNumbers) {
            readAndSendShot(shotNo);
        }
        
        logger.info("批量读取完成");
    }
    
    /**
     * 获取所有炮号
     */
    public List<Integer> getAllShotNumbers() {
        Path basePath = Paths.get(tubePath);
        List<Integer> shotNumbers = new ArrayList<>();
        
        try {
            if (Files.exists(basePath)) {
                shotNumbers = Files.list(basePath)
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> name.matches("\\d+"))
                    .map(Integer::parseInt)
                    .sorted()
                    .collect(Collectors.toList());
            }
        } catch (IOException e) {
            logger.error("读取炮号列表失败", e);
        }
        
        return shotNumbers;
    }
    
    /**
     * 读取元数据
     */
    public ShotMetadata readMetadata(Integer shotNo) {
        Path logPath = Paths.get(logsPath, shotNo.toString(), 
                                 shotNo + "_Tube_operation_log.txt");
        
        if (!Files.exists(logPath)) {
            logger.warn("炮号 {} 的日志文件不存在: {}", shotNo, logPath);
            return null;
        }
        
        ShotMetadata metadata = new ShotMetadata(shotNo);
        metadata.setFilePath(logPath.toString());
        
        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                parseMetadataLine(line, metadata);
            }
        } catch (IOException e) {
            logger.error("读取元数据失败: {}", logPath, e);
            return null;
        }
        
        return metadata;
    }
    
    /**
     * 读取操作日志
     */
    public List<OperationLog> readOperationLogs(Integer shotNo) {
        Path logPath = Paths.get(logsPath, shotNo.toString(), 
                                 shotNo + "_Tube_operation_log.txt");
        
        if (!Files.exists(logPath)) {
            return Collections.emptyList();
        }
        
        List<OperationLog> logs = new ArrayList<>();
        Pattern logPattern = Pattern.compile(
            "\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]\\s+" +
            "操作\\((.+?)\\)\\s+(.+?)\\s+(\\w+)\\s+" +
            "旧=([\\d.]+)\\s+新=([\\d.]+)\\s+Δ=([+-][\\d.]+)\\s+置信度=([\\d.]+)σ"
        );
        
        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            boolean inOperationsSection = false;
            
            while ((line = reader.readLine()) != null) {
                if (line.contains("--- Operations")) {
                    inOperationsSection = true;
                    continue;
                }
                
                if (!inOperationsSection) continue;
                
                Matcher matcher = logPattern.matcher(line);
                if (matcher.find()) {
                    OperationLog log = new OperationLog();
                    log.setShotNo(shotNo);
                    log.setTimestamp(parseDateTime(matcher.group(1)));
                    log.setOperationType(matcher.group(2));
                    log.setChannelName(matcher.group(3));
                    log.setStepType(matcher.group(4));
                    log.setOldValue(Double.parseDouble(matcher.group(5)));
                    log.setNewValue(Double.parseDouble(matcher.group(6)));
                    log.setDelta(Double.parseDouble(matcher.group(7)));
                    log.setConfidence(Double.parseDouble(matcher.group(8)));
                    log.setSourceType(WaveData.DataSourceType.FILE);
                    log.setFileSource(logPath.toString());
                    
                    logs.add(log);
                }
            }
        } catch (IOException e) {
            logger.error("读取操作日志失败: {}", logPath, e);
        }
        
        return logs;
    }
    
    /**
     * 读取PLC互锁日志
     */
    public List<PlcInterlock> readPlcInterlocks(Integer shotNo) {
        Path plcLogPath = Paths.get(plcPath, shotNo.toString(), 
                                    shotNo + "_plc_interlock.txt");
        
        if (!Files.exists(plcLogPath)) {
            return Collections.emptyList();
        }
        
        List<PlcInterlock> interlocks = new ArrayList<>();
        // TODO: 根据实际PLC日志格式实现解析
        
        return interlocks;
    }
    
    /**
     * 发送波形数据信息 (TDMS文件需要Python处理)
     */
    private void sendWaveDataInfo(Integer shotNo) {
        // TDMS文件路径
        Path tubeTdmsPath = Paths.get(tubePath, shotNo.toString(), 
                                      shotNo + "_Tube.tdms");
        Path waterTdmsPath = Paths.get(tubePath, shotNo.toString(), 
                                       shotNo + "_Water.tdms");
        
        // 发送Tube波形信息
        if (Files.exists(tubeTdmsPath)) {
            WaveData tubeInfo = new WaveData(shotNo, "TDMS_FILE_INFO");
            tubeInfo.setFileSource(tubeTdmsPath.toString());
            tubeInfo.setSourceType(WaveData.DataSourceType.FILE);
            dataProducer.sendWaveData(tubeInfo);
        }
        
        // 发送Water波形信息
        if (Files.exists(waterTdmsPath)) {
            WaveData waterInfo = new WaveData(shotNo, "TDMS_FILE_INFO");
            waterInfo.setFileSource(waterTdmsPath.toString());
            waterInfo.setSourceType(WaveData.DataSourceType.FILE);
            dataProducer.sendWaveData(waterInfo);
        }
    }
    
    // ==================== 辅助方法 ====================
    
    private void parseMetadataLine(String line, ShotMetadata metadata) {
        if (line.startsWith("File")) {
            metadata.setFilePath(extractValue(line));
        } else if (line.startsWith("Name")) {
            metadata.setFileName(extractValue(line));
        } else if (line.startsWith("Expected")) {
            String value = extractValue(line).replace(" s", "");
            if (!value.equals("nans")) {
                metadata.setExpectedDuration(Double.parseDouble(value));
            }
        } else if (line.startsWith("Actual")) {
            String value = extractValue(line).replace(" s", "");
            metadata.setActualDuration(Double.parseDouble(value));
        } else if (line.startsWith("Status")) {
            metadata.setStatus(extractValue(line));
        } else if (line.startsWith("Reason")) {
            metadata.setReason(extractValue(line));
        } else if (line.startsWith("StartTime")) {
            metadata.setStartTime(parseDateTime(extractValue(line)));
        } else if (line.startsWith("EndTime")) {
            metadata.setEndTime(parseDateTime(extractValue(line)));
        } else if (line.startsWith("Fs")) {
            String value = extractValue(line).replace(" Hz", "");
            metadata.setSampleRate(Double.parseDouble(value));
        } else if (line.startsWith("N ") && line.contains("samples")) {
            String value = extractValue(line).replace(" samples", "");
            metadata.setTotalSamples(Integer.parseInt(value));
        } else if (line.startsWith("Tolerance")) {
            String value = extractValue(line).replace(" s", "");
            metadata.setTolerance(Double.parseDouble(value));
        }
    }
    
    private String extractValue(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex >= 0 && colonIndex < line.length() - 1) {
            return line.substring(colonIndex + 1).trim();
        }
        return "";
    }
    
    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            logger.warn("解析时间失败: {}", dateTimeStr);
            return null;
        }
    }
}
