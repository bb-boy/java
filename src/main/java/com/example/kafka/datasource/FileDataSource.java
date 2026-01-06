package com.example.kafka.datasource;

import com.example.kafka.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文件数据源实现 - 从本地文件系统读取TDMS和日志文件
 * 
 * 【功能说明】
 * 这个类负责从本地磁盘读取实验数据文件，主要包括：
 * 1. TDMS文件 (Technical Data Management Streaming)：存储波形数据
 * 2. 操作日志文件：记录实验操作过程
 * 3. PLC互锁日志：记录安全检查信息
 * 
 * 【目录结构示例】
 * data/TUBE/1/            <- 炮号1的数据
 *   ├── 1_Tube.tdms       <- 管道波形数据
 *   ├── 1_Water.tdms      <- 水波形数据
 * data/TUBE_logs/1/       <- 炮号1的日志
 *   └── 1_Tube_operation_log.txt
 */
@Component  // Spring组件，会被自动扫描和注册
public class FileDataSource implements DataSource {
    
    // 日志记录器，用于输出调试和错误信息
    private static final Logger logger = LoggerFactory.getLogger(FileDataSource.class);
    
    // 从配置文件(application.yml)读取路径配置
    @Value("${app.data.tube.path:data/TUBE}")  // 冒号后面是默认值
    private String tubePath;  // TDMS文件存放目录
    
    @Value("${app.data.logs.path:data/TUBE_logs}")
    private String logsPath;  // 日志文件存放目录
    
    @Value("${app.data.plc.path:data/PLC_logs}")
    private String plcPath;   // PLC日志存放目录
    
    // Java NIO的Path对象，提供更好的文件操作API
    private Path tubeBasePath;
    private Path logsBasePath;
    private Path plcBasePath;
    
    // 通道元数据目录
    @Value("${app.data.channels.path:channels}")
    private String channelsPath;
    private Path channelsBasePath;
    
    // JSON解析器，用于读取通道元数据
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // 日期时间格式化器，用于解析日志中的时间戳
    private static final DateTimeFormatter DATE_TIME_FORMATTER = 
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    @Override
    public WaveData.DataSourceType getSourceType() {
        return WaveData.DataSourceType.FILE;  // 标识这是文件数据源
    }
    
    /**
     * 初始化方法：检查目录是否存在，准备文件路径
     */
    @Override
    public void initialize() {
        // 将字符串路径转换为Path对象
        tubeBasePath = Paths.get(tubePath);
        logsBasePath = Paths.get(logsPath);
        plcBasePath = Paths.get(plcPath);
        channelsBasePath = Paths.get(channelsPath);
        
        logger.info("初始化文件数据源:");
        logger.info("  TUBE路径: {}", tubeBasePath.toAbsolutePath());
        logger.info("  日志路径: {}", logsBasePath.toAbsolutePath());
        logger.info("  PLC路径: {}", plcBasePath.toAbsolutePath());
        logger.info("  通道元数据: {}", channelsBasePath.toAbsolutePath());
    }
    
    /**
     * 检查数据源是否可用（目录是否存在）
     */
    @Override
    public boolean isAvailable() {
        return Files.exists(tubeBasePath) && Files.isDirectory(tubeBasePath);
    }
    
    /**
     * 获取所有炮号列表
     * 
     * 【实现原理】
     * 1. 扫描 data/TUBE/ 目录
     * 2. 每个子目录名就是炮号，如：1, 2, 10, 100
     * 3. 过滤出纯数字目录名
     * 4. 转换为整数并排序
     */
    @Override
    public List<Integer> getAllShotNumbers() {
        List<Integer> shotNumbers = new ArrayList<>();
        try {
            if (Files.exists(tubeBasePath)) {
                shotNumbers = Files.list(tubeBasePath)         // 列出所有子项
                    .filter(Files::isDirectory)                // 只要目录
                    .map(path -> path.getFileName().toString())// 获取目录名
                    .filter(name -> name.matches("\\d+"))      // 正则表达式：只要纯数字
                    .map(Integer::parseInt)                    // 转换为整数
                    .sorted()                                  // 升序排序
                    .collect(Collectors.toList());             // 收集为List
            }
        } catch (IOException e) {
            logger.error("读取炮号列表失败", e);
        }
        return shotNumbers;
    }
    
    /**
     * 获取元数据：从操作日志文件中解析基本信息
     * 
     * @param shotNo 炮号，例如 1
     * @return 元数据对象，包含文件名、时间、采样率等
     */
    @Override
    public ShotMetadata getShotMetadata(Integer shotNo) {
        // 构建日志文件路径: data/TUBE_logs/1/1_Tube_operation_log.txt
        Path logPath = logsBasePath.resolve(shotNo.toString())
                                   .resolve(shotNo + "_Tube_operation_log.txt");
        
        if (!Files.exists(logPath)) {
            logger.warn("炮号 {} 的元数据文件不存在: {}", shotNo, logPath);
            return null;
        }
        
        return parseMetadataFromLog(shotNo, logPath);
    }
    
    /**
     * 获取波形数据（占位实现）
     * 
     * 【重要提示】
     * Java不能直接读取TDMS文件（这是NI LabVIEW的专有格式）
     * 实际项目中需要：
     * 1. 使用Python的nptdms库读取
     * 2. 或使用JNI调用C库
     * 3. 或预先转换为CSV/JSON格式
     */
    @Override
    public WaveData getWaveData(Integer shotNo, String channelName, String dataType) {
        WaveData waveData = new WaveData(shotNo, channelName);
        waveData.setSourceType(WaveData.DataSourceType.FILE);
        
        // 构建TDMS文件路径: data/TUBE/1/1_Tube.tdms
        String fileName = String.format("%d_%s.tdms", shotNo, dataType);
        Path tdmsPath = tubeBasePath.resolve(shotNo.toString()).resolve(fileName);
        waveData.setFileSource(tdmsPath.toString());
        
        // 调用Python脚本读取TDMS波形数据
        try {
            List<String> command = Arrays.asList(
                "python3", "read_wave_data.py",
                shotNo.toString(),
                channelName,
                dataType
            );
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(new File(tubeBasePath.resolve("../..").toString()));
            Process process = pb.start();
            
            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder output = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
            }
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && output.length() > 0) {
                // 解析JSON响应
                JsonNode jsonResponse = objectMapper.readTree(output.toString());
                
                if (jsonResponse.has("data") && !jsonResponse.get("data").isNull()) {
                    // 提取波形数据
                    List<Double> dataList = new ArrayList<>();
                    JsonNode dataNode = jsonResponse.get("data");
                    if (dataNode.isArray()) {
                        for (JsonNode value : dataNode) {
                            dataList.add(value.asDouble());
                        }
                    }
                    
                    waveData.setData(dataList);
                    waveData.setSamples(dataList.size());
                    
                    if (jsonResponse.has("sampleRate")) {
                        waveData.setSampleRate(jsonResponse.get("sampleRate").asDouble());
                    }
                    
                    logger.info("成功读取TDMS文件: 炮号={}, 通道={}, 类型={}, 样本数={}", 
                                shotNo, channelName, dataType, dataList.size());
                } else {
                    logger.warn("TDMS文件中无数据: 炮号={}, 通道={}, 类型={}, JSON响应: {}", 
                                shotNo, channelName, dataType, jsonResponse.toString());
                }
            } else {
                logger.error("Python脚本执行失败: 炮号={}, 通道={}, 类型={}, 退出码={}, 输出: {}", 
                            shotNo, channelName, dataType, exitCode, output.toString());
            }
        } catch (Exception e) {
            logger.error("读取TDMS文件异常: {}, 通道: {}", tdmsPath, channelName, e);
        }
        
        return waveData;
    }
    
    /**
     * 获取通道名称列表
     * 
     * 【实现方式】
     * 1. 尝试从channels/channels_{shotNo}.json读取（推荐）
     * 2. 如果JSON不存在，返回备用的常见通道
     * 
     * 【JSON文件生成】
     * 运行命令: python extract_channels.py --shot {shotNo}
     * 
     * @param shotNo 炮号
     * @param dataType 数据类型 (Tube 或 Water)
     * @return 通道名称列表
     */
    @Override
    public List<String> getChannelNames(Integer shotNo, String dataType) {
        // 尝试从JSON文件读取
        Path channelJsonFile = channelsBasePath.resolve("channels_" + shotNo + ".json");
        
        if (Files.exists(channelJsonFile)) {
            try {
                // 读取并解析JSON文件
                JsonNode root = objectMapper.readTree(channelJsonFile.toFile());
                JsonNode dataTypesNode = root.get("dataTypes");
                
                if (dataTypesNode != null && dataTypesNode.has(dataType)) {
                    JsonNode typeNode = dataTypesNode.get(dataType);
                    JsonNode channelsNode = typeNode.get("channels");
                    
                    if (channelsNode != null && channelsNode.isArray()) {
                        List<String> channelNames = new ArrayList<>();
                        for (JsonNode channelNode : channelsNode) {
                            String name = channelNode.get("name").asText();
                            channelNames.add(name);
                        }
                        
                        logger.info("从JSON加载炮号{}的{}通道: {} 个，通道列表: {}", shotNo, dataType, channelNames.size(), channelNames);
                        return channelNames;
                    }
                }
            } catch (IOException e) {
                logger.warn("读取通道JSON文件失败: {}, 使用默认通道", channelJsonFile, e);
            }
        } else {
            logger.debug("通道JSON文件不存在: {}, 使用默认通道", channelJsonFile);
        }
        
        // 如果JSON不存在或读取失败，返回备用通道列表
        if ("Tube".equals(dataType)) {
            return Arrays.asList("InPower", "RefPower", "NegVoltage", "NegCurrent", 
                               "PosVoltage", "PosCurrent", "FilaVoltage", "FilaCurrent", "TiPumpCurrent");
        } else if ("Water".equals(dataType)) {
            // Water的常见温度传感器通道
            return Arrays.asList("140阳极T1进", "140阳极T1回", "140主窗口T2回", "140收集极T5进");
        }
        
        // 默认返回基础通道
        return Arrays.asList("NegVoltage", "PosVoltage", "Current", "Pressure");
    }
    
    /**
     * 获取操作日志：解析日志文件
     */
    @Override
    public List<OperationLog> getOperationLogs(Integer shotNo) {
        Path logPath = logsBasePath.resolve(shotNo.toString())
                                   .resolve(shotNo + "_Tube_operation_log.txt");
        
        if (!Files.exists(logPath)) {
            logger.warn("炮号 {} 的操作日志不存在: {}", shotNo, logPath);
            return Collections.emptyList();  // 返回空列表而不是null
        }
        
        return parseOperationLogs(shotNo, logPath);
    }
    
    @Override
    public List<PlcInterlock> getPlcInterlocks(Integer shotNo) {
        Path plcLogPath = plcBasePath.resolve(shotNo.toString())
                                     .resolve(shotNo + "_plc_interlock.txt");
        
        if (!Files.exists(plcLogPath)) {
            logger.debug("炮号 {} 的PLC互锁日志不存在: {}", shotNo, plcLogPath);
            return Collections.emptyList();
        }
        
        return parsePlcInterlocks(shotNo, plcLogPath);
    }
    
    @Override
    public void close() {
        logger.info("关闭文件数据源");
        // 文件数据源不需要特殊清理工作
    }
    
    // ==================== 私有解析方法 ====================
    
    /**
     * 从日志文件解析元数据
     * 
     * 【日志格式示例】
     * File: /path/to/1_Tube.tdms
     * ShotNo : 1
     * Expected duration: 1.5 s
     * Actual duration: 1.48 s
     * Status: Success
     * StartTime: 2026-01-04 10:30:15.123
     * ...
     */
    private ShotMetadata parseMetadataFromLog(Integer shotNo, Path logPath) {
        ShotMetadata metadata = new ShotMetadata(shotNo);
        
        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                
                // 使用startsWith判断行开头，提取冒号后的值
                if (line.startsWith("File")) {
                    metadata.setFilePath(extractValue(line));
                } else if (line.startsWith("ShotNo ")) {
                    metadata.setShotNo(Integer.parseInt(extractValue(line)));
                } else if (line.startsWith("Expected")) {
                    String value = extractValue(line).replace(" s", "").trim();
                    // 处理 "nan" 或空值的情况（某些炮号没有预设脉宽）
                    if (!value.isEmpty() && !value.equalsIgnoreCase("nan")) {
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
                } else if (line.startsWith("N ")) {
                    String value = extractValue(line).replace(" samples", "");
                    metadata.setTotalSamples(Integer.parseInt(value));
                }
            }
        } catch (IOException e) {
            logger.error("解析元数据文件失败: {}", logPath, e);
        }
        
        return metadata;
    }
    
    /**
     * 解析操作日志
     * 
     * 【日志格式示例】
     * [2026-01-04 10:30:15.123] 操作(Start) NegVoltage Step 旧=0.0 新=100.0 Δ=+100.0 置信度=3.5σ
     */
    private List<OperationLog> parseOperationLogs(Integer shotNo, Path logPath) {
        List<OperationLog> logs = new ArrayList<>();
        
        // 正则表达式：匹配日志行格式
        Pattern logPattern = Pattern.compile(
            "\\[(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]\\s+操作\\((.+?)\\)\\s+(.+?)\\s+(\\w+)\\s+旧=([\\d.]+)\\s+新=([\\d.]+)\\s+Δ=([+-][\\d.]+)\\s+置信度=([\\d.]+)σ"
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
                    log.setTimestamp(parseDateTime(matcher.group(1)));  // 时间戳
                    log.setOperationType(matcher.group(2));  // 操作类型
                    log.setChannelName(matcher.group(3));    // 通道名
                    log.setStepType(matcher.group(4));       // 步骤类型
                    log.setOldValue(Double.parseDouble(matcher.group(5)));  // 旧值
                    log.setNewValue(Double.parseDouble(matcher.group(6)));  // 新值
                    log.setDelta(Double.parseDouble(matcher.group(7)));     // 变化量
                    log.setConfidence(Double.parseDouble(matcher.group(8))); // 置信度
                    log.setSourceType(WaveData.DataSourceType.FILE);
                    log.setFileSource(logPath.toString());
                    
                    logs.add(log);
                }
            }
        } catch (IOException e) {
            logger.error("解析操作日志失败: {}", logPath, e);
        }
        
        return logs;
    }
    
    private List<PlcInterlock> parsePlcInterlocks(Integer shotNo, Path plcLogPath) {
        List<PlcInterlock> interlocks = new ArrayList<>();
        
        try (BufferedReader reader = Files.newBufferedReader(plcLogPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                // TODO: 根据实际PLC日志格式解析
                // 这里是示例实现
                PlcInterlock interlock = new PlcInterlock(shotNo, LocalDateTime.now());
                interlock.setSourceType(WaveData.DataSourceType.FILE);
                interlocks.add(interlock);
            }
        } catch (IOException e) {
            logger.error("解析PLC互锁日志失败: {}", plcLogPath, e);
        }
        
        return interlocks;
    }
    
    /**
     * 提取冒号后的值
     * 例如: "ShotNo : 1" -> "1"
     */
    private String extractValue(String line) {
        int colonIndex = line.indexOf(':');
        if (colonIndex >= 0 && colonIndex < line.length() - 1) {
            return line.substring(colonIndex + 1).trim();
        }
        return "";
    }
    
    /**
     * 解析日期时间字符串
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        try {
            return LocalDateTime.parse(dateTimeStr, DATE_TIME_FORMATTER);
        } catch (Exception e) {
            logger.warn("解析时间失败: {}", dateTimeStr);
            return null;
        }
    }
}
