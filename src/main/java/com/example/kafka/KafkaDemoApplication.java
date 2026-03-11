package com.example.kafka;

// 导入Spring Boot的启动类
import org.springframework.boot.SpringApplication;
// 导入Spring Boot自动配置注解
import org.springframework.boot.autoconfigure.SpringBootApplication;
// 导入定时任务注解

/**
 * Spring Boot Kafka Demo 主应用类
 * 
 * 这是整个应用的入口点，负责：
 * 1. 启动Spring Boot应用
 * 2. 自动扫描并加载所有的Spring组件（Controller、Service、Configuration等）
 * 3. 初始化Spring容器和Kafka相关的Bean
 */
@SpringBootApplication  // 这是一个组合注解，包含了：
                        // @Configuration: 标识这是一个配置类
                        // @EnableAutoConfiguration: 启用Spring Boot的自动配置机制
                        // @ComponentScan: 自动扫描当前包及子包下的所有Spring组件
public class KafkaDemoApplication {
    
    /**
     * 应用程序的主入口方法
     * 
     * @param args 命令行参数（本例中未使用）
     */
    public static void main(String[] args) {
        // SpringApplication.run() 做了以下几件事：
        // 1. 创建Spring应用上下文（ApplicationContext）
        // 2. 扫描并注册所有的Bean（如Controller、Service等）
        // 3. 初始化内嵌的Tomcat服务器（默认8080端口）
        // 4. 连接到Kafka集群（根据application.yml配置）
        // 5. 启动Kafka消费者监听器
        SpringApplication.run(KafkaDemoApplication.class, args);
        
        // 应用启动成功后，打印提示信息到控制台
        System.out.println("========================================");
        System.out.println("Kafka Demo 应用已启动！");
        System.out.println("访问: http://localhost:8080/api/ingest/shot?shotNo=1");
        System.out.println("========================================");
    }
}
