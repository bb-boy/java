package com.example.kafka.service;

// 导入SLF4J日志相关类
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// 导入Spring Kafka的核心类：KafkaTemplate用于发送消息
import org.springframework.kafka.core.KafkaTemplate;
// 导入发送结果类
import org.springframework.kafka.support.SendResult;
// 导入Spring的Service注解
import org.springframework.stereotype.Service;

// 导入Java的异步编程类
import java.util.concurrent.CompletableFuture;

/**
 * Kafka消息生产者服务类
 * 
 * 生产者的职责：
 * 1. 接收要发送的消息
 * 2. 将消息发送到Kafka集群的指定Topic
 * 3. 处理发送结果（成功或失败）
 * 
 * 为什么叫"生产者"？
 * - 因为它"生产"消息并发送到Kafka
 * - 对应的，"消费者"负责从Kafka"消费"（接收）消息
 */
@Service  // @Service注解标识这是一个服务层组件
          // Spring会自动创建这个类的实例，并管理它的生命周期
          // 其他类可以通过依赖注入使用这个服务
public class MessageProducer {
    
    // 创建日志记录器，用于输出日志信息
    // getLogger(MessageProducer.class)：日志会标记为来自MessageProducer类
    private static final Logger logger = LoggerFactory.getLogger(MessageProducer.class);
    
    // 定义要发送消息的目标Topic名称
    // 这个Topic在KafkaConfig中已经配置好了
    private static final String TOPIC = "demo-topic";
    
    // KafkaTemplate是Spring Kafka提供的核心类，用于发送消息到Kafka
    // <String, String>表示：Key的类型是String，Value（消息内容）的类型也是String
    // final关键字表示这个字段在构造函数中赋值后不能再修改
    private final KafkaTemplate<String, String> kafkaTemplate;
    
    /**
     * 构造函数：Spring会自动注入KafkaTemplate
     * 
     * 什么是依赖注入（DI）？
     * - Spring会自动创建KafkaTemplate对象
     * - 在创建MessageProducer对象时，自动将KafkaTemplate传入
     * - 我们不需要手动new KafkaTemplate()
     * 
     * @param kafkaTemplate Spring自动注入的Kafka模板对象
     */
    public MessageProducer(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;  // 将注入的对象保存到成员变量
    }
    
    /**
     * 发送简单消息（不带Key）
     * 
     * 消息发送流程：
     * 1. 调用kafkaTemplate.send()发送消息
     * 2. Kafka会根据哈希算法选择一个分区
     * 3. 消息被写入该分区的日志文件
     * 4. 返回发送结果（包含分区号和偏移量）
     * 
     * @param message 要发送的消息内容
     */
    public void sendMessage(String message) {
        // 记录日志：准备发送消息
        // {}是占位符，会被message的值替换
        logger.info("准备发送消息: {}", message);
        
        // 发送消息到Kafka
        // send()方法是异步的，立即返回一个CompletableFuture对象
        // 不会阻塞等待发送完成
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(TOPIC, message);
        
        // whenComplete()：当异步操作完成时执行这个回调函数
        // (result, ex)是Lambda表达式的参数：
        // - result：发送成功时的结果对象
        // - ex：发送失败时的异常对象
        future.whenComplete((result, ex) -> {
            // 判断是否有异常
            if (ex == null) {
                // 发送成功，记录详细信息
                logger.info("✓ 消息发送成功 - Partition: {}, Offset: {}, Message: {}",
                        result.getRecordMetadata().partition(),   // 消息被写入的分区号（0、1或2）
                        result.getRecordMetadata().offset(),      // 消息在分区中的偏移量（位置）
                        message);                                  // 消息内容
                
                // 什么是Offset（偏移量）？
                // - 每个分区中的消息都有一个唯一的序号，从0开始递增
                // - 消费者通过offset知道自己消费到哪条消息了
                // - offset保证了消息的顺序性和不重复消费
            } else {
                // 发送失败，记录错误日志
                logger.error("✗ 消息发送失败: {}", message, ex);
            }
        });
        
        // 注意：这个方法会立即返回，不等待消息发送完成
        // 实际的发送和回调处理是异步进行的
    }
    
    /**
     * 发送带Key的消息
     * 
     * 为什么要带Key？
     * 1. 保证顺序性：相同Key的消息会发送到同一个分区，保证这些消息的顺序
     * 2. 业务分组：可以按用户ID、订单ID等作为Key，方便后续处理
     * 
     * 例如：
     * - Key="user123"的所有消息都会发送到同一个分区
     * - 这保证了user123的操作按发送顺序被处理
     * 
     * @param key 消息的键，用于决定消息发送到哪个分区
     * @param message 消息内容
     */
    public void sendMessageWithKey(String key, String message) {
        // 记录日志
        logger.info("准备发送消息 (Key: {}): {}", key, message);
        
        // 发送带Key的消息
        // Kafka会对Key进行哈希计算：hash(key) % partitions = 分区号
        // 这保证了相同Key的消息总是发送到同一个分区
        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(TOPIC, key, message);
        
        // 处理发送结果的回调
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // 发送成功
                logger.info("✓ 消息发送成功 - Key: {}, Partition: {}, Offset: {}",
                        key,                                       // 消息的Key
                        result.getRecordMetadata().partition(),   // 分区号
                        result.getRecordMetadata().offset());     // 偏移量
            } else {
                // 发送失败
                logger.error("✗ 消息发送失败 - Key: {}", key, ex);
            }
        });
    }
}

/*
 * 总结：生产者的工作流程
 * 
 * 1. Controller调用MessageProducer.sendMessage("Hello")
 * 2. MessageProducer调用kafkaTemplate.send()
 * 3. KafkaTemplate将消息序列化为字节数组
 * 4. 根据Key（或默认策略）计算目标分区
 * 5. 将消息发送到Kafka集群的对应分区
 * 6. Kafka Leader副本写入消息并返回结果
 * 7. 触发whenComplete回调，记录日志
 * 
 * 异步 vs 同步：
 * - 我们使用的是异步发送（推荐）
 * - 不会阻塞主线程，性能更好
 * - 如果需要同步发送：future.get()会阻塞等待结果
 */
