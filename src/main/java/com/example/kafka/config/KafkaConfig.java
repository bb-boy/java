package com.example.kafka.config;

// 导入Kafka的主题类
import org.apache.kafka.clients.admin.NewTopic;
// 导入Spring的Bean注解
import org.springframework.context.annotation.Bean;
// 导入Spring的配置类注解
import org.springframework.context.annotation.Configuration;
// 导入Spring Kafka的主题构建器
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka配置类
 * 
 * 这个类负责配置Kafka相关的Bean，主要是创建Topic（主题）
 * Spring Boot启动时会自动执行这个配置，如果主题不存在则自动创建
 */
@Configuration  // 标识这是一个Spring配置类，Spring会扫描并加载这个类中的@Bean方法
public class KafkaConfig {
    
    /**
     * 创建并配置demo主题
     * 
     * 什么是Topic（主题）？
     * - Topic是Kafka中消息的分类，类似于数据库中的表
     * - 生产者发送消息到指定的Topic
     * - 消费者订阅Topic来接收消息
     * 
     * 什么是分区（Partition）？
     * - 一个Topic可以分为多个分区，提高并发处理能力
     * - 每个分区是一个有序的消息队列
     * - 相同Key的消息会发送到同一个分区，保证顺序性
     * 
     * 什么是副本（Replica）？
     * - 每个分区可以有多个副本，提高数据可靠性
     * - 副本分布在不同的Kafka节点上
     * - 如果一个节点故障，其他副本仍然可以提供服务
     * 
     * @return NewTopic对象，Spring会自动在Kafka中创建这个主题
     */
    @Bean  // @Bean注解告诉Spring：这个方法返回的对象需要被Spring管理
           // Spring启动时会调用这个方法，并将返回的NewTopic注册到Kafka
    public NewTopic demoTopic() {
        // 使用TopicBuilder构建一个新的主题
        return TopicBuilder
                .name("demo-topic")      // 主题名称：demo-topic
                .partitions(3)           // 分区数：3个分区
                                         // 为什么是3个？因为我们有3个Kafka节点，
                                         // 每个节点可以处理一个分区，提高并发能力
                .replicas(3)             // 副本数：每个分区有3个副本
                                         // 副本会分布在3个不同的Kafka节点上
                                         // 即使2个节点挂掉，数据也不会丢失
                .build();                // 构建并返回NewTopic对象
    }
    
    /*
     * 执行流程：
     * 1. Spring Boot启动时扫描到这个配置类
     * 2. 执行demoTopic()方法，获取NewTopic对象
     * 3. Spring Kafka自动连接到Kafka集群（使用application.yml中的配置）
     * 4. 检查"demo-topic"是否存在
     * 5. 如果不存在，则在Kafka中创建这个主题
     * 6. 如果已存在，则跳过创建步骤
     */
}
