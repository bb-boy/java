package com.example.kafka.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * InfluxDB配置类
 * 用于存储时序波形数据
 */
@Configuration
@ConditionalOnProperty(name = "app.influxdb.enabled", havingValue = "true")
public class InfluxDBConfig {

    @Value("${app.influxdb.url}")
    private String url;

    @Value("${app.influxdb.token}")
    private String token;

    @Value("${app.influxdb.org}")
    private String org;

    @Value("${app.influxdb.bucket}")
    private String bucket;

    @Bean
    public InfluxDBClient influxDBClient() {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }

    @Bean
    public String influxDBOrg() {
        return org;
    }

    @Bean
    public String influxDBBucket() {
        return bucket;
    }
}
