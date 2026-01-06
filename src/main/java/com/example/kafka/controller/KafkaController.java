package com.example.kafka.controller;

// 导入消息生产者服务
import com.example.kafka.service.MessageProducer;
import com.example.kafka.service.DataPipelineService;
// 导入Spring的HTTP响应类
import org.springframework.http.ResponseEntity;
// 导入Spring Web的相关注解
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

// 导入Java集合类
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Arrays;

/**
 * Kafka测试控制器
 * 
 * Controller的作用：
 * 1. 接收HTTP请求（来自浏览器、Postman、curl等）
 * 2. 调用Service层的业务逻辑
 * 3. 返回HTTP响应（通常是JSON格式）
 * 
 * 这是典型的MVC架构中的C（Controller）层
 * - Model（模型）：数据
 * - View（视图）：前端页面（本例中返回JSON，由前端渲染）
 * - Controller（控制器）：处理请求，调用业务逻辑
 */
@RestController  // @RestController = @Controller + @ResponseBody
                 // @Controller: 标识这是一个控制器类
                 // @ResponseBody: 方法返回值自动转换为JSON格式
@RequestMapping("/api/kafka")  // 设置这个Controller的基础路径
                               // 所有方法的URL都会以/api/kafka开头
public class KafkaController {
    
    // 注入消息生产者服务
    // final表示这个字段在构造函数赋值后不能修改
    private final MessageProducer messageProducer;
    
    // 注入数据管道服务（新增）
    @Autowired
    private DataPipelineService dataPipelineService;
    
    /**
     * 构造函数：Spring自动注入MessageProducer
     * 
     * 依赖注入的好处：
     * 1. 解耦：Controller不需要知道MessageProducer如何创建
     * 2. 可测试：可以注入Mock对象进行单元测试
     * 3. 灵活：可以轻松替换实现类
     * 
     * @param messageProducer Spring自动注入的生产者服务
     */
    public KafkaController(MessageProducer messageProducer) {
        this.messageProducer = messageProducer;
    }
    
    /**
     * 发送简单消息的API接口
     * 
     * 访问方式：
     * GET http://localhost:8080/api/kafka/send?message=Hello
     * 
     * 执行流程：
     * 1. 用户访问上述URL
     * 2. Spring接收到HTTP GET请求
     * 3. 根据路径匹配到这个方法
     * 4. 从URL参数中提取message的值
     * 5. 调用messageProducer.sendMessage()发送消息
     * 6. 构造响应JSON并返回
     * 
     * @param message URL参数，例如：?message=Hello 中的"Hello"
     * @return ResponseEntity包装的JSON响应
     */
    @GetMapping("/send")  // 映射GET请求到 /api/kafka/send
                          // 完整路径 = @RequestMapping + @GetMapping
    public ResponseEntity<Map<String, Object>> sendMessage(
            @RequestParam String message) {  // @RequestParam从URL参数中提取值
                                             // 如果参数名是message，可以省略value属性
        
        // 调用生产者服务发送消息到Kafka
        // 这是异步操作，不会阻塞
        messageProducer.sendMessage(message);
        
        // 构造响应JSON
        // HashMap用于存储键值对，会被自动转换为JSON
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");      // 状态
        response.put("message", "消息已发送");   // 提示信息
        response.put("data", message);          // 返回发送的消息内容
        
        // ResponseEntity.ok() 创建HTTP 200响应
        // 返回的JSON格式：
        // {
        //   "status": "success",
        //   "message": "消息已发送",
        //   "data": "Hello"
        // }
        return ResponseEntity.ok(response);
    }
    
    /**
     * 发送带Key的消息的API接口
     * 
     * 访问方式：
     * GET http://localhost:8080/api/kafka/send-with-key?key=user1&message=Hello
     * 
     * 为什么需要Key？
     * - 保证相同Key的消息发送到同一分区
     * - 保证这些消息的处理顺序
     * 
     * 使用场景：
     * - key=用户ID：保证同一用户的操作按顺序处理
     * - key=订单ID：保证同一订单的更新按顺序处理
     * 
     * @param key 消息的键
     * @param message 消息内容
     * @return JSON响应
     */
    @GetMapping("/send-with-key")  // 映射到 /api/kafka/send-with-key
    public ResponseEntity<Map<String, Object>> sendMessageWithKey(
            @RequestParam String key,      // 第一个URL参数：key
            @RequestParam String message) { // 第二个URL参数：message
        
        // 调用生产者服务发送带Key的消息
        messageProducer.sendMessageWithKey(key, message);
        
        // 构造响应
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "消息已发送");
        response.put("key", key);          // 返回Key
        response.put("data", message);     // 返回消息内容
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 批量发送消息的API接口
     * 
     * 访问方式：
     * GET http://localhost:8080/api/kafka/batch?count=10
     * 
     * 如果不提供count参数，默认发送10条消息
     * 例如：GET http://localhost:8080/api/kafka/batch
     * 
     * 使用场景：
     * - 性能测试
     * - 批量数据导入
     * - 模拟高并发场景
     * 
     * @param count 要发送的消息数量，默认值为10
     * @return JSON响应
     */
    @GetMapping("/batch")  // 映射到 /api/kafka/batch
    public ResponseEntity<Map<String, Object>> sendBatch(
            @RequestParam(defaultValue = "10") int count) {  
            // defaultValue: 如果URL中没有count参数，使用默认值10
        
        // 循环发送多条消息
        for (int i = 1; i <= count; i++) {
            // String.format()格式化字符串
            // %d：整数占位符
            // System.currentTimeMillis()：获取当前时间戳（毫秒）
            String message = String.format("批量消息 #%d - %d", i, System.currentTimeMillis());
            
            // 发送消息
            messageProducer.sendMessage(message);
        }
        
        // 构造响应
        Map<String, Object> response = new HashMap<>();
        response.put("status", "success");
        response.put("message", "批量消息已发送");
        response.put("count", count);  // 返回实际发送的数量
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 健康检查接口
     * 
     * 访问方式：
     * GET http://localhost:8080/api/kafka/health
     * 
     * 作用：
     * 1. 检查应用是否正常运行
     * 2. 用于监控系统（如Prometheus）
     * 3. 用于负载均衡器（如Nginx）的健康检查
     * 
     * @return JSON响应
     */
    @GetMapping("/health")  // 映射到 /api/kafka/health
    public ResponseEntity<Map<String, String>> health() {
        // 创建响应
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");           // 状态：UP表示正常运行
        response.put("service", "Kafka Demo");  // 服务名称
        
        // 返回JSON：
        // {
        //   "status": "UP",
        //   "service": "Kafka Demo"
        // }
        return ResponseEntity.ok(response);
    }
    
    // ========================================================================
    // 数据管道API - 文件 → Kafka → 数据库完整流程
    // ========================================================================
    
    /**
     * 同步单个炮号数据到Kafka（会自动触发存入数据库）
     * 
     * GET /api/kafka/sync/shot?shotNo=1
     * 
     * 【完整数据流】
     * 1. HTTP请求到达此方法
     * 2. 调用 dataPipelineService.syncShotToKafka(1)
     * 3. DataPipelineService 从文件读取炮号1的数据
     * 4. 通过 DataProducer 发送到Kafka主题
     * 5. DataConsumer 监听Kafka并自动存入H2数据库
     * 6. 返回同步结果给前端
     * 
     * @param shotNo 炮号
     * @return 同步结果统计
     */
    @GetMapping("/sync/shot")
    public ResponseEntity<Map<String, Object>> syncSingleShot(
            @RequestParam Integer shotNo) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 调用管道服务同步数据
            DataPipelineService.SyncResult result = 
                dataPipelineService.syncShotToKafka(shotNo);
            
            if (result.isSuccess()) {
                response.put("status", "success");
                response.put("message", "炮号 " + shotNo + " 同步成功");
                response.put("data", Map.of(
                    "shotNo", result.getShotNo(),
                    "metadata", result.getMetadataCount(),
                    "waveData", result.getWaveDataCount(),
                    "operationLog", result.getOperationLogCount(),
                    "plcInterlock", result.getPlcInterlockCount()
                ));
            } else {
                response.put("status", "failed");
                response.put("message", "炮号 " + shotNo + " 同步失败: " + result.getErrorMessage());
            }
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "同步异常: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 批量同步多个炮号
     * 
     * GET /api/kafka/sync/batch?shotNos=1,2,3
     * 
     * @param shotNos 炮号列表（逗号分隔）
     * @return 批量同步结果
     */
    @GetMapping("/sync/batch")
    public ResponseEntity<Map<String, Object>> syncBatchShots(
            @RequestParam String shotNos) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 解析炮号列表
            List<Integer> shotNumbers = Arrays.stream(shotNos.split(","))
                .map(String::trim)
                .map(Integer::parseInt)
                .toList();
            
            // 批量同步
            DataPipelineService.BatchSyncResult result = 
                dataPipelineService.syncMultipleShotsToKafka(shotNumbers);
            
            response.put("status", "success");
            response.put("message", "批量同步完成");
            response.put("data", Map.of(
                "total", shotNumbers.size(),
                "success", result.getSuccessCount(),
                "failed", result.getFailureCount(),
                "totalMetadata", result.getTotalMetadata(),
                "totalWaveData", result.getTotalWaveData()
            ));
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "批量同步异常: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * 同步所有炮号数据到Kafka
     * 
     * POST /api/kafka/sync/all
     * 
     * 【警告】这会同步所有文件数据，可能耗时较长！
     * 
     * @return 全量同步结果
     */
    @PostMapping("/sync/all")
    public ResponseEntity<Map<String, Object>> syncAllShots() {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            // 全量同步
            DataPipelineService.BatchSyncResult result = 
                dataPipelineService.syncAllShotsToKafka();
            
            response.put("status", "success");
            response.put("message", "全量同步完成");
            response.put("data", Map.of(
                "success", result.getSuccessCount(),
                "failed", result.getFailureCount(),
                "totalMetadata", result.getTotalMetadata(),
                "totalWaveData", result.getTotalWaveData()
            ));
            
        } catch (Exception e) {
            response.put("status", "error");
            response.put("message", "全量同步异常: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
}

