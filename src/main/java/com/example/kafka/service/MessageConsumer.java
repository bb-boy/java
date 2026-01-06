package com.example.kafka.service;

// 导入Kafka消费者记录类
import org.apache.kafka.clients.consumer.ConsumerRecord;
// 导入日志相关类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 导入Spring Kafka的监听器注解
import org.springframework.kafka.annotation.KafkaListener;
// 导入Spring的Service注解
import org.springframework.stereotype.Service;

/**
 * Kafka消息消费者服务类
 * 
 * 消费者的职责：
 * 1. 订阅指定的Topic
 * 2. 自动从Kafka拉取消息
 * 3. 处理接收到的消息
 * 4. 提交offset（告诉Kafka已经消费到哪里了）
 * 
 * 消费者 vs 生产者的区别：
 * - 生产者：主动发送消息到Kafka
 * - 消费者：被动接收来自Kafka的消息
 */
@Service  // 标识这是一个Spring服务组件
public class MessageConsumer {
    
    // 创建日志记录器
    private static final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);
    
    /**
     * 监听并消费demo-topic的消息
     * 
     * @KafkaListener注解的工作原理：
     * 1. Spring启动时扫描到这个注解
     * 2. 创建一个Kafka消费者，连接到Kafka集群
     * 3. 订阅指定的Topic（demo-topic）
     * 4. 在后台线程中不断拉取消息
     * 5. 每收到一条消息，就调用这个方法
     * 
     * 什么是消费者组（Consumer Group）？
     * - groupId标识了消费者所属的组
     * - 同一个组内的多个消费者会分担消息（负载均衡）
     * - 不同组的消费者会各自接收全部消息（广播）
     * 
     * 例如：
     * - Consumer1和Consumer2都属于"demo-consumer-group"
     * - 如果demo-topic有3个分区，它们可能分别消费不同的分区
     * - 如果只有1个消费者，它会消费所有3个分区的消息
     * 
     * @param record 消费到的消息记录，包含完整的消息信息
     */
    @KafkaListener(
            topics = "demo-topic",              // 要监听的Topic名称
            groupId = "demo-consumer-group"     // 消费者组ID
    )
    // 这个方法会被Spring Kafka自动调用，每收到一条消息就调用一次
    public void consumeMessage(ConsumerRecord<String, String> record) {
        // ConsumerRecord包含了消息的所有信息：
        // - topic: 消息来自哪个Topic
        // - partition: 消息来自哪个分区
        // - offset: 消息在分区中的位置
        // - key: 消息的Key（可能为null）
        // - value: 消息的内容
        // - timestamp: 消息的时间戳
        
        // 打印分隔线，方便查看
        logger.info("========================================");
        logger.info("收到消息:");
        
        // 打印消息的详细信息
        logger.info("  Topic: {}", record.topic());         // demo-topic
        logger.info("  Partition: {}", record.partition()); // 0、1或2（因为配置了3个分区）
        logger.info("  Offset: {}", record.offset());       // 在该分区中的偏移量
        logger.info("  Key: {}", record.key());             // 消息的Key，没有则为null
        logger.info("  Value: {}", record.value());         // 消息的实际内容
        logger.info("  Timestamp: {}", record.timestamp()); // 消息产生的时间戳（毫秒）
        
        logger.info("========================================");
        
        // 调用业务处理方法
        processMessage(record.value());
        
        // 注意：方法执行完毕后，Spring Kafka会自动提交offset
        // 这告诉Kafka：这条消息已经被成功处理了
        // 下次启动时，会从下一条消息开始消费
    }
    
    /**
     * 处理消息的业务逻辑
     * 
     * 在实际项目中，这里可以做各种业务处理：
     * 1. 解析消息内容（如JSON字符串）
     * 2. 保存到数据库
     * 3. 调用其他微服务的API
     * 4. 发送通知或邮件
     * 5. 更新缓存
     * 6. 触发其他业务流程
     * 
     * 错误处理：
     * - 如果这个方法抛出异常，Spring Kafka会重试
     * - 可以配置重试次数和策略
     * - 也可以配置死信队列（DLQ）来处理失败的消息
     * 
     * @param message 消息内容
     */
    private void processMessage(String message) {
        // 记录调试日志
        logger.debug("正在处理消息: {}", message);
        
        // 这里是你的业务逻辑
        // 例如：
        
        // 1. 保存到数据库
        // userRepository.save(parseUser(message));
        
        // 2. 调用其他服务
        // orderService.createOrder(message);
        
        // 3. 发送通知
        // notificationService.send(message);
        
        // 4. 数据转换
        // String processedData = transform(message);
        
        // 当前示例：只是简单地记录日志
        // 在实际项目中，你需要在这里添加真正的业务逻辑
    }
}

/*
 * 消费者的完整工作流程：
 * 
 * 1. Spring Boot启动
 *    ↓
 * 2. 扫描到@KafkaListener注解
 *    ↓
 * 3. 创建Kafka消费者，连接到集群（localhost:19092,29092,39092）
 *    ↓
 * 4. 加入消费者组"demo-consumer-group"
 *    ↓
 * 5. 订阅"demo-topic"（3个分区）
 *    ↓
 * 6. Kafka分配分区给消费者（如果只有1个消费者，它负责所有3个分区）
 *    ↓
 * 7. 从上次提交的offset开始拉取消息（如果是第一次，从earliest开始）
 *    ↓
 * 8. 每拉取到一条消息，调用consumeMessage()方法
 *    ↓
 * 9. 处理消息（调用processMessage）
 *    ↓
 * 10. 自动提交offset（告诉Kafka这条消息已处理）
 *    ↓
 * 11. 继续拉取下一条消息，重复步骤8-10
 * 
 * 
 * Offset管理（重要！）：
 * 
 * - offset记录了消费者消费到哪条消息了
 * - 存储在Kafka内部的__consumer_offsets主题中
 * - 自动提交（enable-auto-commit: true）：
 *   - Spring Kafka定期自动提交offset
 *   - 简单但可能丢失消息（处理失败也会提交）
 * - 手动提交：
 *   - 处理成功后再提交offset
 *   - 更可靠，但需要更多代码
 * 
 * 
 * 消费者组的作用：
 * 
 * 场景1：单个消费者
 * - 1个消费者订阅demo-topic
 * - 它会消费所有3个分区的消息
 * 
 * 场景2：多个消费者（同组）
 * - 3个消费者都属于"demo-consumer-group"
 * - Kafka会分配：
 *   - Consumer1 → 分区0
 *   - Consumer2 → 分区1
 *   - Consumer3 → 分区2
 * - 每条消息只会被一个消费者处理（负载均衡）
 * 
 * 场景3：多个消费者组
 * - Group A的消费者
 * - Group B的消费者
 * - 它们都能收到全部消息（广播）
 */
