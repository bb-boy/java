package com.example.kafka.service;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * InfluxDB服务 - 用于将波形数据写入时序数据库
 * 
 * 数据模型:
 * - Measurement: waveform
 * - Tags: shot_no, channel_name, data_type, file_source
 * - Fields: value (波形值), sample_index (采样索引)
 * - Timestamp: 根据start_time和采样率计算的实际时间
 */
@Service
@ConditionalOnProperty(name = "app.influxdb.enabled", havingValue = "true")
public class InfluxDBService {

    private static final Logger logger = LoggerFactory.getLogger(InfluxDBService.class);
    private static final String MEASUREMENT_WAVEFORM = "waveform";
    private static final int BATCH_SIZE = 5000;

    @Autowired
    private InfluxDBClient influxDBClient;

    @Autowired
    @Qualifier("influxDBBucket")
    private String bucket;

    @Autowired
    @Qualifier("influxDBOrg")
    private String org;

    /**
     * 写入波形数据到InfluxDB
     * 
     * @param shotNo 炮号
     * @param channelName 通道名称
     * @param dataType 数据类型(Tube/Water)
     * @param fileSource 数据来源文件
     * @param sampleRate 采样率(Hz)
     * @param startTime 开始时间
     * @param waveData 波形数据列表
     */
    public void writeWaveData(Integer shotNo, String channelName, String dataType,
                               String fileSource, Double sampleRate, LocalDateTime startTime,
                               List<Double> waveData) {
        if (waveData == null || waveData.isEmpty()) {
            logger.warn("波形数据为空, 跳过写入: ShotNo={}, Channel={}", shotNo, channelName);
            return;
        }

        try {
            WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
            
            // 计算时间间隔(纳秒)
            long intervalNanos = (long) (1_000_000_000.0 / sampleRate);
            Instant baseTime = startTime != null 
                ? startTime.atZone(ZoneId.systemDefault()).toInstant()
                : Instant.now();

            // 删除已有同 shot_no + channel_name 的数据，保证幂等性（避免重复写入）
            // 使用足够宽的时间范围确保删除所有历史数据
            try {
                // 使用过去30天到未来1天的时间范围，覆盖所有可能的旧数据
                OffsetDateTime startOdt = OffsetDateTime.now().minusDays(30);
                OffsetDateTime endOdt = OffsetDateTime.now().plusDays(1);
                String predicate = String.format(
                    "_measurement=\"%s\" AND shot_no=\"%s\" AND channel_name=\"%s\"",
                    MEASUREMENT_WAVEFORM, shotNo, channelName
                );
                influxDBClient.getDeleteApi().delete(startOdt, endOdt, predicate, bucket, org);
                
                // 等待删除操作传播(InfluxDB异步删除)
                Thread.sleep(500);
                
                // 验证删除成功
                long remainingRecords = countRecords(shotNo, channelName);
                if (remainingRecords > 0) {
                    throw new IllegalStateException(
                        String.format("删除验证失败: 仍有%d条记录残留", remainingRecords)
                    );
                }
                
                logger.debug("已删除旧数据: Shot={}, Channel={}, 删除范围=过去30天", 
                            shotNo, channelName);
            } catch (Exception e) {
                logger.error("删除旧数据失败,终止写入: Shot={}, Channel={}, 错误={}", 
                           shotNo, channelName, e.getMessage());
                // 删除失败则抛出异常,防止重复数据
                throw new RuntimeException("InfluxDB删除失败,拒绝写入以防止数据重复", e);
            }

            List<Point> points = new ArrayList<>(Math.min(waveData.size(), BATCH_SIZE));
            
            for (int i = 0; i < waveData.size(); i++) {
                Double value = waveData.get(i);
                if (value == null || value.isNaN() || value.isInfinite()) {
                    continue;
                }

                Instant timestamp = baseTime.plusNanos(i * intervalNanos);
                
                Point point = Point.measurement(MEASUREMENT_WAVEFORM)
                    .addTag("shot_no", String.valueOf(shotNo))
                    .addTag("channel_name", channelName)
                    .addTag("data_type", dataType != null ? dataType : "unknown")
                    .addTag("file_source", fileSource != null ? fileSource : "unknown")
                    .addField("value", value)
                    .addField("sample_index", i)
                    .time(timestamp, WritePrecision.NS);
                
                points.add(point);

                // 批量写入
                if (points.size() >= BATCH_SIZE) {
                    writeApi.writePoints(points);
                    points.clear();
                }
            }

            // 写入剩余数据
            if (!points.isEmpty()) {
                writeApi.writePoints(points);
            }

            logger.info("波形数据已写入InfluxDB: ShotNo={}, Channel={}, Type={}, Points={}",
                       shotNo, channelName, dataType, waveData.size());

        } catch (Exception e) {
            logger.error("写入InfluxDB失败: ShotNo={}, Channel={}", shotNo, channelName, e);
        }
    }

    /**
     * 从Map数据写入波形
     * 用于Kafka消费者直接调用
     */
    @SuppressWarnings("unchecked")
    public void writeFromKafkaMessage(Map<String, Object> data) {
        Integer shotNo = (Integer) data.get("shotNo");
        String channelName = (String) data.get("channelName");
        String fileSource = (String) data.get("fileSource");
        String dataType = extractDataType(fileSource);
        Double sampleRate = toDouble(data.get("sampleRate"));
        LocalDateTime startTime = parseDateTime(data.get("startTime"));
        
        Object waveDataObj = data.get("data");
        if (waveDataObj instanceof List) {
            List<Double> waveData = convertToDoubleList((List<?>) waveDataObj);
            
            // 默认采样率1000Hz
            if (sampleRate == null || sampleRate <= 0) {
                sampleRate = 1000.0;
            }
            
            writeWaveData(shotNo, channelName, dataType, fileSource, sampleRate, startTime, waveData);
        }
    }

    private List<Double> convertToDoubleList(List<?> list) {
        List<Double> result = new ArrayList<>(list.size());
        for (Object obj : list) {
            if (obj instanceof Number) {
                result.add(((Number) obj).doubleValue());
            }
        }
        return result;
    }

    private String extractDataType(String fileSource) {
        if (fileSource != null) {
            if (fileSource.contains("Water")) return "Water";
            if (fileSource.contains("Tube")) return "Tube";
        }
        return "Tube";
    }

    private Double toDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Double) return (Double) value;
        if (value instanceof Number) return ((Number) value).doubleValue();
        return null;
    }
    
    /**
     * 统计指定shot_no和channel_name的记录数
     * 用于删除验证和数据完整性检查
     */
    private long countRecords(Integer shotNo, String channelName) {
        String flux = String.format(
            "from(bucket: \"%s\") " +
            "|> range(start: -30d) " +
            "|> filter(fn: (r) => r._measurement == \"waveform\") " +
            "|> filter(fn: (r) => r.shot_no == \"%d\") " +
            "|> filter(fn: (r) => r.channel_name == \"%s\") " +
            "|> filter(fn: (r) => r._field == \"value\") " +
            "|> count()",
            bucket, shotNo, channelName
        );
        
        try {
            List<FluxTable> tables = influxDBClient.getQueryApi().query(flux, org);
            if (tables.isEmpty() || tables.get(0).getRecords().isEmpty()) {
                return 0;
            }
            
            FluxRecord record = tables.get(0).getRecords().get(0);
            return ((Number) record.getValue()).longValue();
        } catch (Exception e) {
            logger.warn("记录计数失败: shot_no={}, channel_name={}, 错误={}", 
                shotNo, channelName, e.getMessage());
            return -1; // 返回-1表示计数失败
        }
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof String) {
            String str = (String) value;
            try {
                if (str.contains("T")) {
                    return LocalDateTime.parse(str.replace(" ", "T").split("\\.")[0]);
                } else {
                    return LocalDateTime.parse(str, 
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                }
            } catch (Exception e) {
                try {
                    return LocalDateTime.parse(str, 
                        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (Exception e2) {
                    return null;
                }
            }
        }
        return null;
    }
}
