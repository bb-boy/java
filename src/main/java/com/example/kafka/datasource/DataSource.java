package com.example.kafka.datasource;

import com.example.kafka.model.*;
import java.util.List;

/**
 * 数据源接口 - 统一抽象文件和网络数据源
 * 
 * 【设计模式】策略模式 (Strategy Pattern)
 * 这个接口定义了数据获取的统一规范，具体实现可以是：
 * 1. FileDataSource - 从本地文件系统读取数据
 * 2. NetworkDataSource - 从Kafka网络接收实时数据
 * 
 * 【核心概念】炮号(Shot Number)
 * 在物理实验中，每次实验称为一"炮"，每炮都有唯一编号(ShotNo)
 * 每炮会产生：波形数据、操作日志、PLC互锁日志等
 */
public interface DataSource {
    
    /**
     * 获取数据源类型
     * 
     * @return FILE(文件) 或 NETWORK(网络)
     */
    WaveData.DataSourceType getSourceType();
    
    /**
     * 获取所有炮号列表
     * 
     * @return 炮号列表，按升序排列，如 [1, 2, 3, 10, 11, ...]
     */
    List<Integer> getAllShotNumbers();
    
    /**
     * 获取指定炮号的元数据（基本信息）
     * 
     * @param shotNo 炮号，例如 1, 10, 100
     * @return 元数据对象，包含文件名、路径、时间等基本信息
     */
    ShotMetadata getShotMetadata(Integer shotNo);
    
    /**
     * 获取指定炮号的波形数据
     * 
     * 【波形数据说明】
     * - 每炮实验会产生多个通道(Channel)的波形数据
     * - 例如：NegVoltage(负电压)、PosVoltage(正电压)等
     * - 每个通道包含采样率和大量数据点（可能上万个点）
     * 
     * @param shotNo 炮号
     * @param channelName 通道名称，如 "NegVoltage", "PosVoltage"
     * @param dataType 数据类型：Tube(管道) 或 Water(水)
     * @return 波形数据对象，包含时间序列数据
     */
    WaveData getWaveData(Integer shotNo, String channelName, String dataType);
    
    /**
     * 获取指定炮号的所有通道名称
     * 
     * @param shotNo 炮号
     * @param dataType 数据类型 (Tube 或 Water)
     * @return 通道名称列表，如 ["NegVoltage", "PosVoltage", "Current"]
     */
    List<String> getChannelNames(Integer shotNo, String dataType);
    
    /**
     * 获取指定炮号的操作日志
     * 
     * 【操作日志说明】
     * 记录实验过程中的所有操作步骤和状态变化
     * 例如：启动时间、参数设置、状态切换等
     * 
     * @param shotNo 炮号
     * @return 操作日志列表，按时间顺序排列
     */
    List<OperationLog> getOperationLogs(Integer shotNo);
    
    /**
     * 获取指定炮号的PLC互锁日志
     * 
     * 【PLC互锁说明】
     * PLC(可编程逻辑控制器)用于设备安全控制
     * 互锁日志记录安全检查点和报警信息
     * 例如：门是否关闭、压力是否正常等
     * 
     * @param shotNo 炮号
     * @return PLC互锁日志列表
     */
    List<PlcInterlock> getPlcInterlocks(Integer shotNo);
    
    /**
     * 检查数据源是否可用
     * 
     * @return true=可用，false=不可用
     */
    boolean isAvailable();
    
    /**
     * 初始化数据源
     * 
     * 在首次使用前调用，执行必要的准备工作：
     * - 文件数据源：检查目录是否存在
     * - 网络数据源：建立Kafka连接
     */
    void initialize();
    
    /**
     * 关闭数据源，释放资源
     * 
     * 应用关闭时调用，清理缓存和网络连接
     */
    void close();
}
