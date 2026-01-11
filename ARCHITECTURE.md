# 波形数据展示系统 - 架构设计文档

**最后更新**: 2026年1月11日  
**系统版本**: 1.0.0

## 系统概述

本系统是一套完整的波形数据采集、处理、存储和展示解决方案，用于展示和分析TDMS波形文件、操作日志和PLC互锁日志。采用**数据管道架构**和**多源数据融合**方案：

```
数据源 (文件/网络) → Kafka → 数据库(MySQL/InfluxDB) → REST API / WebSocket → Web UI展示
```

**核心特性**：
- ✅ 双数据源支持 (本地文件/Kafka网络)，运行时动态切换
- ✅ Kafka 数据管道 (异步解耦、削峰填谷)
- ✅ MySQL + InfluxDB 混合存储 (结构化+时序数据)
- ✅ 完整的REST API和WebSocket实时推送
- ✅ 实时波形图表展示，含采样点数统计
- ✅ 支持主备数据源自动合并（fallback机制）

## 核心架构

### 分层架构设计

系统采用**四层分层架构**：

1. **数据采集层** (Ingest Layer)
   - FileDataReader: 本地TDMS文件读取
   - NetworkDataReceiver: 网络数据接收 (TCP/Kafka)

2. **消息队列层** (Message Queue Layer)
   - Kafka集群 (3节点高可用)
   - 4个主题: shot-metadata, wave-data, operation-log, plc-interlock

3. **数据持久化层** (Persistence Layer)
   - MySQL: 元数据、操作日志、PLC互锁 (结构化数据)
   - InfluxDB: 波形时序数据 (高效时序查询)

4. **服务层 + 表现层** (Service & Presentation Layer)
   - DataService: 数据源管理与切换
   - DataQueryService: 数据库查询服务
   - REST Controllers: 多个API端点
   - WebSocket: 实时推送
   - Web UI: ECharts图表展示 (含采样点数统计)

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                              数据采集层                                        │
│  ┌─────────────────┐           ┌─────────────────┐                           │
│  │  FileDataReader │           │NetworkDataReceiver│                          │
│  │  (文件读取器)    │           │  (网络接收器)     │                          │
│  │  - TDMS文件     │           │  - TCP Server    │                          │
│  │  - 操作日志     │           │  - HTTP API      │                          │
│  │  - PLC日志      │           │  - 外部系统推送   │                          │
│  └────────┬────────┘           └────────┬────────┘                           │
│           │                              │                                    │
│           └──────────┬──────────────────┘                                    │
│                      ▼                                                        │
│            ┌─────────────────┐                                               │
│            │  DataProducer   │                                               │
│            │  (Kafka生产者)   │                                               │
│            └────────┬────────┘                                               │
│└─────────────────────┼────────────────────────────────────────────────────────┘
│                      │
│                      ▼
│┌─────────────────────────────────────────────────────────────────────────────┐
│                           消息队列层 (Kafka)                                  │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────────┐            │
│  │shot-metadata│ │  wave-data  │ │operation-log│ │plc-interlock│            │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────────┘            │
│└─────────────────────┼────────────────────────────────────────────────────────┘
│                      │
│                      ▼
│┌─────────────────────────────────────────────────────────────────────────────┐
│                           数据持久化层                                        │
│            ┌─────────────────┐                                               │
│            │  DataConsumer   │                                               │
│            │  (Kafka消费者)   │                                               │
│            └────────┬────────┘                                               │
│                     │                                                        │
│         ┌──────────┴──────────┐                                             │
│         ▼                     ▼                                              │
│  ┌─────────────────┐   ┌─────────────────┐                                  │
│  │ 关系型数据库     │   │ InfluxDB        │                                  │
│  │ (MySQL/H2)      │   │ (时序数据库)     │                                  │
│  │ - shot_metadata │   │ - waveform      │                                  │
│  │ - wave_data     │   │   (波形时序数据) │                                  │
│  │ - operation_log │   │                 │                                  │
│  │ - plc_interlock │   │                 │                                  │
│  └────────┬────────┘   └────────┬────────┘                                  │
│           └──────────┬──────────┘                                           │
│└──────────────────────┼──────────────────────────────────────────────────────┘
│                      │
│                      ▼
│┌─────────────────────────────────────────────────────────────────────────────┐
│                           数据服务层                                          │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────┐          │
│  │ DataQueryService│    │   Repository    │    │WebSocketController│         │
│  │  (数据查询服务)  │◄──│  (数据访问层)    │    │  (实时推送)       │         │
│  └────────┬────────┘    └─────────────────┘    └────────┬────────┘          │
│           │                                              │                   │
│           └──────────────────┬───────────────────────────┘                   │
│                              ▼                                               │
│                    ┌─────────────────┐                                       │
│                    │DataQueryController│                                      │
│                    │   (REST API)     │                                      │
│                    └────────┬────────┘                                       │
│└─────────────────────────────┼────────────────────────────────────────────────┘
│                              │
│                              ▼
│┌─────────────────────────────────────────────────────────────────────────────┐
│                            前端展示层                                         │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                      Web UI (React/Vue)                              │    │
│  │  - 波形图表展示 (ECharts)    - 实时数据监控 (WebSocket)              │    │
│  │  - 操作日志表格              - PLC互锁状态                           │    │
│  │  - 炮号列表/搜索             - 数据导入控制                          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│└─────────────────────────────────────────────────────────────────────────────┘
```

- 数据源选择：`DataService` 基于 `app.data.source.primary` 选择 file 或 network 作为主源，`fallback=true` 时自动合并备用源结果（炮号列表/通道/波形/日志）。  
- 数据管道：`DataPipelineService` 负责文件 → Kafka → DataConsumer → MySQL/Influx 的同步，`KafkaController` 提供触发接口。  

## 项目结构

```
src/main/java/com/example/kafka/
├── entity/                     # 数据库实体
│   ├── ShotMetadataEntity.java    # 炮号元数据表
│   ├── WaveDataEntity.java        # 波形数据表
│   ├── OperationLogEntity.java    # 操作日志表
│   └── PlcInterlockEntity.java    # PLC互锁表
│
├── repository/                 # 数据访问层 (Spring Data JPA)
│   ├── ShotMetadataRepository.java
│   ├── WaveDataRepository.java
│   ├── OperationLogRepository.java
│   └── PlcInterlockRepository.java
│
├── model/                      # 数据传输模型 (DTO)
│   ├── WaveData.java
│   ├── OperationLog.java
│   ├── PlcInterlock.java
│   └── ShotMetadata.java
│
├── ingest/                     # 数据采集层
│   ├── FileDataReader.java        # 文件读取器
│   └── NetworkDataReceiver.java   # 网络接收器
│
├── producer/                   # Kafka生产者
│   └── DataProducer.java          # 统一数据生产者
│
├── consumer/                   # Kafka消费者
│   └── DataConsumer.java          # 消费并存入数据库
│
├── datasource/                # 数据源实现
│   ├── FileDataSource.java       # 本地文件源 (主/备之一)
│   └── NetworkDataSource.java    # Kafka 网络源 (主/备之一)
│
├── service/                    # 服务层
│   ├── DataService.java           # 主/备数据源切换与合并
│   ├── DataPipelineService.java   # 文件 → Kafka → DB/Influx 同步
│   ├── DataQueryService.java      # 数据库查询服务
│   └── InfluxDBService.java       # InfluxDB时序数据服务
│
├── controller/                 # 控制器层
│   ├── DataController.java        # 直接文件/网络数据源的REST API
│   ├── KafkaController.java       # 数据管道同步与Kafka演示接口
│   ├── DatabaseController.java    # MySQL中的数据查询
│   ├── HybridDataController.java  # MySQL+Influx混合查询
│   └── WebSocketController.java   # WebSocket推送
│
├── config/                     # 配置类
│   ├── KafkaConfig.java
│   ├── WebSocketConfig.java
│   └── InfluxDBConfig.java        # InfluxDB配置
```

## 数据库设计

### 表结构

#### shot_metadata (炮号元数据)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| shot_no | INT (PK) | 炮号 |
| file_name | VARCHAR | 文件名 |
| file_path | VARCHAR | 文件路径 |
| tolerance | DOUBLE | 允许误差(秒) |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| expected_duration | DOUBLE | 预期时长(秒) |
| actual_duration | DOUBLE | 实际时长(秒) |
| status | VARCHAR | 状态 |
| reason | VARCHAR | 原因 |
| sample_rate | DOUBLE | 采样率(Hz) |
| total_samples | INT | 总采样数 |
| source_type | ENUM | 数据源类型 |
| created_at | DATETIME | 创建时间 |
| updated_at | DATETIME | 更新时间 |

#### wave_data (波形数据)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT (PK) | 主键 |
| shot_no | INT | 炮号 |
| channel_name | VARCHAR | 通道名 |
| data_type | VARCHAR | 数据类型(Tube/Water) |
| start_time | DATETIME | 开始时间 |
| end_time | DATETIME | 结束时间 |
| sample_rate | DOUBLE | 采样率 |
| samples | INT | 采样点数 |
| data | LONGBLOB | 压缩波形数据 |
| file_source | VARCHAR | 数据来源文件 |
| source_type | ENUM | 数据源类型 |
| created_at | DATETIME | 创建时间 |

#### operation_log (操作日志)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT (PK) | 主键 |
| shot_no | INT | 炮号 |
| timestamp | DATETIME | 操作时间 |
| operation_type | VARCHAR | 操作类型 |
| channel_name | VARCHAR | 通道名 |
| old_value | DOUBLE | 旧值 |
| new_value | DOUBLE | 新值 |
| delta | DOUBLE | 变化量 |
| confidence | DOUBLE | 置信度(σ) |
| step_type | VARCHAR | 步骤类型 |
| file_source | VARCHAR | 数据来源文件 |
| source_type | ENUM | 数据源类型 |
| created_at | DATETIME | 创建时间 |

#### plc_interlock (PLC互锁)
| 字段名 | 类型 | 说明 |
|--------|------|------|
| id | BIGINT (PK) | 主键 |
| shot_no | INT | 炮号 |
| timestamp | DATETIME | 时间戳 |
| interlock_name | VARCHAR | 互锁名称 |
| status | BOOLEAN | 状态 |
| current_value | DOUBLE | 当前值 |
| threshold | DOUBLE | 阈值 |
| threshold_operation | VARCHAR | 阈值比较操作 |
| description | VARCHAR | 描述 |
| additional_data | TEXT | 额外数据(JSON) |
| source_type | ENUM | 数据源类型 |
| created_at | DATETIME | 创建时间 |

### InfluxDB时序数据结构

#### waveform (波形时序数据)
| 类型 | 名称 | 说明 |
|------|------|------|
| Measurement | waveform | 波形数据测量名 |
| Tag | shot_no | 炮号 |
| Tag | channel_name | 通道名称 |
| Tag | data_type | 数据类型(Tube/Water) |
| Tag | file_source | 数据来源文件 |
| Field | value | 波形采样值 |
| Field | sample_index | 采样索引 |
| Timestamp | - | 根据start_time和采样率计算的纳秒级时间戳 |

**说明**: 波形数据同时存储在关系型数据库(压缩二进制)和InfluxDB(时序点)中:
- 关系型数据库: 用于元数据查询和完整波形快速获取
- InfluxDB: 用于时序分析、范围查询和降采样

## Kafka主题设计

| 主题名 | 用途 | Key格式 |
|--------|------|---------|
| shot-metadata | 炮号元数据 | shotNo |
| wave-data | 波形数据 | shotNo_channelName_dataType |
| operation-log | 操作日志 | shotNo_timestamp |
| plc-interlock | PLC互锁 | shotNo_timestamp |

## API接口

### 数据源直读 (`/api/data`)
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/data/shots | 获取所有炮号（主/备数据源合并） |
| GET | /api/data/shots/{shotNo}/metadata | 获取元数据 |
| GET | /api/data/shots/{shotNo}/complete | 元数据+波形+日志 |
| GET | /api/data/shots/{shotNo}/wave | 单通道波形（参数 channel/type） |
| GET | /api/data/shots/{shotNo}/channels | 通道列表（Tube/Water） |
| GET | /api/data/shots/{shotNo}/logs/operation | 操作日志 |
| GET | /api/data/shots/{shotNo}/logs/plc | PLC互锁 |
| GET | /api/data/status | 数据源状态（主数据源/备用/可用性等） |
| POST | /api/data/source/switch?source=file\|network | 切换主数据源 |
| POST | /api/data/shots/batch-metadata | 批量查询元数据 |

### 数据管道同步与健康检查 (`/api/kafka`)
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/kafka/health | 应用健康检查 |
| GET | /api/kafka/sync/shot?shotNo=1 | 将单炮号文件同步到 Kafka→DB/Influx |
| GET | /api/kafka/sync/batch?shotNos=1,2 | 批量同步指定炮号 |
| POST | /api/kafka/sync/all | 全量同步所有炮号（耗时） |

### 数据库查询 (`/api/database`)
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/database/stats | 数据量统计 |
| GET | /api/database/metadata?shotNo=1 | 查询元数据（MySQL） |
| GET | /api/database/wavedata?shotNo=1 | 查询波形元数据与压缩数据 |
| GET | /api/database/logs?shotNo=1 | 查询操作日志 |

### 混合查询 (MySQL + InfluxDB) (`/api/hybrid`)
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/hybrid/shots | 从wave_data表统计炮号列表 |
| GET | /api/hybrid/stats | MySQL/Influx 指标统计 |
| GET | /api/hybrid/shot/{shotNo} | 元数据+通道概览 |
| GET | /api/hybrid/shot/{shotNo}/complete | 元数据+示例波形 |
| GET | /api/hybrid/waveform?shotNo=1&channelName=InPower | InfluxDB 波形数据 |
| GET | /api/hybrid/timeline/{shotNo} | 操作日志时间线 |

### WebSocket端点
| 端点 | 说明 |
|------|------|
| /ws | WebSocket连接端点 |
| /topic/shots | 炮号列表更新 |
| /topic/shot/{shotNo} | 指定炮号数据更新 |

## 配置说明

### application.yml 核心配置

```yaml
spring:
  datasource:                    # 默认使用 MySQL
    url: jdbc:mysql://localhost:3306/wavedb?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: devroot
  jpa:
    hibernate:
      ddl-auto: update
  kafka:
    bootstrap-servers: localhost:19092,localhost:29092,localhost:39092
    consumer:
      group-id: data-consumer-group-v2
    producer:
      acks: all
      compression-type: snappy

app:
  data:
    tube.path: data/TUBE
    logs.path: data/TUBE_logs
    plc.path: data/PLC_logs
    source:
      primary: file               # 主数据源 file / network
      fallback: true              # 启用备用数据源自动合并
  kafka:
    topic:
      metadata: shot-metadata
      wavedata: wave-data
      operation: operation-log
      plc: plc-interlock
    group-id: data-consumer-group-v2
  network:
    enabled: false                # TCP/Kafka 网络接收器开关
    port: 9999
  influxdb:
    enabled: true                 # 默认开启，需正确 token/URL
    url: http://localhost:8086
    token: my-super-secret-token
    org: wavedata
    bucket: waveforms

server:
  port: 8080
```

- 文件数据源通道元数据路径：`app.data.channels.path`（默认为 `channels/`，`FileDataSource` 使用），需运行 `python extract_channels.py --all` 生成。  
- H2 仅作为依赖存在，默认关闭控制台；如需改用 H2，可通过 profiles 覆盖 `spring.datasource`。  
- InfluxDB 默认开启，如未部署可将 `app.influxdb.enabled=false`。  
- 主/备数据源：`DataService` 根据 `app.data.source.primary` 选择 file 或 network，并在 `fallback=true` 时合并备用源。  

## 部署运行

### 1. 启动基础设施

```bash
# 启动Kafka
cd docker
docker-compose up -d

# 检查状态
docker-compose ps
```

### 2. 编译运行

```bash
# 编译
mvn clean package -DskipTests

# 运行
java -jar target/kafka-demo-1.0.0.jar
```

### 3. 导入数据

```bash
# 从文件导入所有炮号到Kafka->数据库
curl -X POST http://localhost:8080/api/data/import/file/all

# 导入单个炮号
curl -X POST http://localhost:8080/api/data/import/file/1
```

### 4. 查询数据

```bash
# 获取所有炮号
curl http://localhost:8080/api/data/shots

# 获取完整数据
curl http://localhost:8080/api/data/shots/1/complete

# 查看数据库 (H2控制台)
# 访问 http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:wavedb
```

## 扩展建议

### 1. 生产环境数据库
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/wavedb
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: your_password
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQL8Dialect
```

### 2. TDMS文件处理
由于Java无法直接读取TDMS文件，建议：
- 使用Python预处理转JSON
- 通过HTTP API接收处理后的数据
- 使用Python脚本 `data_publisher.py` 发送

### 3. 性能优化
- Redis缓存热点数据
- 数据库分表分库
- Kafka分区优化
- 前端数据采样

### 4. 监控告警
- Prometheus + Grafana
- Kafka监控
- 数据库监控
- 应用日志收集
