# 波形数据展示系统

**项目版本**: 1.0.0  
**最后更新**: 2026年3月2日  
**构建状态**: 以本地构建结果为准

## 系统简介

一套完整的**波形数据采集、处理、存储与展示**系统，支持TDMS文件和网络两种数据源。包含以下核心功能：

- 📊 **实时波形展示**：ECharts图表，动态显示采样点数量
- 🔄 **数据同步链路**：本地文件 → Kafka → 数据库/InfluxDB
- 📢 **Kafka数据管道**：文件 → Kafka → MySQL/InfluxDB → 前端，实现异步解耦
- 💾 **混合存储**：
  - MySQL: 元数据、操作日志、PLC互锁、保护事件、压缩波形
  - InfluxDB: 波形时序数据 (高效时序查询)
- 🧩 **波形分段存储**：`waveform_metadata`/`waveform_samples` 分段入库，支持窗口回放
- 🌐 **REST API + WebSocket**：完整的数据接口 + 实时推送
- 📋 **辅助工具**：Python脚本进行通道提取、数据发布、文件监控
- 🧪 **ECRH分析接口**：波形窗口、频谱计算、保护事件/操作日志检索
- 🧾 **TDMS派生日志/事件**：同步时基于TDMS波形生成操作日志与保护事件，不依赖外部扫描脚本

数据库设计详见 `DATABASE_SCHEMA.md`。

## 核心能力

- ✅ **Kafka→数据库链路**：文件 → Kafka → DataConsumer → MySQL/InfluxDB
- ✅ **Kafka数据管道**：文件 → Kafka → DataConsumer → MySQL/InfluxDB
- ✅ **REST API**：炮号列表、元数据、波形数据、操作日志、PLC互锁、保护事件、数据源状态
- ✅ **WebSocket推送**：实时数据更新推送
- ✅ **Web UI**：ECharts波形展示、炮号选择、通道列表、操作日志时间线
- ✅ **数据采样统计**：波形图表标题显示采样点数量
- ✅ **完整的辅助工具链**：通道提取、波形读取、Kafka数据发布、文件监控
- ✅ **波形窗口与频谱**：按时间窗抽取波形并计算FFT频谱
- ✅ **分段波形存储**：元数据+分段样本入库，支持窗口回放
- ✅ **ECRH生成器**：基于波形生成操作日志/保护事件，保证可重复
- ✅ **TDMS派生器**：同步时自动从TDMS生成操作日志/保护事件
- ✅ **InfluxDB维护接口**：重复数据清理与完整性报告

## 运行模式与数据流

### Kafka数据管道模式 (`/api/kafka/sync/*`)
- 文件 → Kafka → DataConsumer → MySQL/InfluxDB 全链路同步
- 提供 `/api/kafka/sync/shot?shotNo=1` 等端点触发导入
- 同步完成后通过 `/api/database` 或 `/api/hybrid` 查询数据库
- 同步过程中会从TDMS波形派生操作日志与保护事件，并通过Kafka主题投递后再由DataConsumer入库

### 混合查询模式 (`/api/hybrid/*`)
- 从MySQL查询元数据、操作日志、PLC互锁
- 从InfluxDB查询波形时序数据
- 不回退到文件系统；数据库或InfluxDB无数据时直接返回错误
- 适用于: 高性能波形展示、时序分析

### ECRH分析接口 (`/api/ecrh/*`)
- 基于MySQL压缩波形数据做时间窗提取与频谱计算
- 支持保护事件与操作日志的条件检索
- 支持基于波形生成操作日志与保护事件（可选覆盖现有数据）

#### `/api/ecrh` 端点清单
- `GET /api/ecrh/waveform/window`：时间窗波形（`shotNo/channelName/dataType`，`start/end` 可选成对）
- `GET /api/ecrh/waveform/spectrum`：FFT频谱（同上，支持 `maxPoints`）
- `GET /api/ecrh/protection-events`：保护事件（支持 `severity/deviceId/interlockName` 与时间范围）
- `GET /api/ecrh/operation-logs`：操作日志（支持 `userId/deviceId/command` 与时间范围）
- `POST /api/ecrh/generate`：生成操作日志与保护事件（`SyntheticGenerationRequest`）

**注意**：
- 查询前需先通过 `/api/kafka/sync/*` 同步，数据仅通过 Kafka→数据库链路提供
- InfluxDB默认开启写入 (`app.influxdb.enabled=true`)，未部署时请禁用或配置正确token

### 关键配置
- `app.waveform.segment.max-points`：分段样本点数（波形分段入库）
- `app.waveform.window.max-points`：窗口波形最大点数（下采样）
- `app.spectrum.max-points`：频谱计算最大点数
- `app.protection.window.before-ms` / `app.protection.window.after-ms`：事件回放窗口

## 项目结构

```
.
├── src/main/java/com/example/kafka/        # ✅ 核心后端代码
│   ├── KafkaDemoApplication.java            # Spring Boot 启动类
│   ├── config/                              # 配置层
│   │   ├── KafkaConfig.java                 # Kafka配置
│   │   ├── WebSocketConfig.java             # WebSocket配置
│   │   └── InfluxDBConfig.java              # InfluxDB配置
│   │
│   ├── controller/                          # 控制器层 (REST API)
│   │   ├── DataController.java              # 旧接口已移除（/api/data/* 返回 410）
│   │   ├── KafkaController.java             # Kafka管道 API (/api/kafka/*)
│   │   ├── DatabaseController.java          # 数据库查询 API (/api/database/*)
│   │   ├── HybridDataController.java        # 混合查询 API (/api/hybrid/*) - MySQL+InfluxDB
│   │   ├── InfluxDBMaintenanceController.java # InfluxDB维护接口
│   │   ├── EcrhController.java              # ECRH分析接口 (/api/ecrh/*)
│   │   └── WebSocketController.java         # WebSocket端点
│   │
│   ├── service/                             # 服务层
│   │   ├── DataPipelineService.java         # 文件→Kafka→DB同步管道
│   │   ├── InfluxDBService.java             # InfluxDB时序数据服务
│   │   ├── MessageProducer.java             # Kafka生产者
│   │   ├── MessageConsumer.java             # Kafka消费者
│   │   ├── InfluxDBCleanupService.java      # InfluxDB清理维护
│   │   ├── ProtectionEventService.java      # 保护事件处理
│   │   ├── WaveformWindowService.java       # 波形窗口计算
│   │   ├── SpectrumService.java             # 频谱计算
│   │   ├── WaveformSegmentService.java      # 波形分段写入
│   │   └── SyntheticDataGeneratorService.java # 日志/事件生成器
│   │
│   ├── datasource/                          # 数据源实现 (策略模式)
│   │   ├── DataSource.java                  # 接口定义
│   │   ├── FileDataSource.java              # 本地文件源 - 读取TDMS/日志/PLC
│   │   └── NetworkDataSource.java           # 网络源 - Kafka消费（不用于查询接口）
│   │
│   ├── ingest/                              # 数据采集层
│   │   ├── FileDataReader.java              # 文件读取器
│   │   └── NetworkDataReceiver.java         # 网络接收器
│   │
│   ├── producer/                            # Kafka生产者
│   │   └── DataProducer.java                # 数据生产者实现
│   │
│   ├── consumer/                            # Kafka消费者 (监听器)
│   │   └── DataConsumer.java                # 消费者 - 负责持久化到MySQL/InfluxDB
│   ├── entity/                              # 数据库实体 (JPA)
│   │   ├── ShotMetadataEntity.java          # 炮号元数据表
│   │   ├── WaveDataEntity.java              # 波形数据表
│   │   ├── OperationLogEntity.java          # 操作日志表
│   │   ├── PlcInterlockEntity.java          # PLC互锁表
│   │   ├── ProtectionEventEntity.java       # 保护事件表
│   │   ├── WaveformMetadataEntity.java      # 波形元数据表
│   │   ├── WaveformSampleEntity.java        # 波形分段样本表
│   │   ├── UserEntity.java                  # 用户审计表
│   │   ├── SystemConfigEntity.java          # 系统配置表
│   │   └── ChannelEntity.java               # 通道元数据表
│   │
│   ├── model/                               # 数据传输对象 (DTO)
│   │   ├── ShotMetadata.java                # 炮号元数据
│   │   ├── WaveData.java                    # 波形数据
│   │   ├── OperationLog.java                # 操作日志
│   │   ├── PlcInterlock.java                # PLC互锁
│   │   ├── WaveformWindowRequest.java       # 波形窗口请求
│   │   ├── WaveformWindowResult.java        # 波形窗口结果
│   │   ├── SpectrumRequest.java             # 频谱请求
│   │   ├── SpectrumResult.java              # 频谱结果
│   │   ├── WaveformSegmentRequest.java      # 波形分段请求
│   │   ├── SyntheticGenerationRequest.java  # 生成请求
│   │   └── SyntheticGenerationResult.java   # 生成结果
│   │
│   └── repository/                          # 数据访问层 (Spring Data JPA)
│       ├── ShotMetadataRepository.java
│       ├── WaveDataRepository.java
│       ├── OperationLogRepository.java
│       ├── PlcInterlockRepository.java
│       ├── ProtectionEventRepository.java
│       ├── ChannelRepository.java
│       ├── WaveformMetadataRepository.java
│       ├── WaveformSampleRepository.java
│       ├── UserRepository.java
│       └── SystemConfigRepository.java
│
├── src/main/resources/                      # ✅ 配置与静态资源
│   ├── application.yml                      # 应用主配置 (数据源/Kafka/InfluxDB)
│   ├── application-mysql.yml                # MySQL特定配置
│   ├── static/index.html                    # 前端Web UI - 波形展示、通道选择、时间线
│   └── static/css|js                        # 前端资源
│
├── data/                                    # ✅ 本地数据文件
│   ├── TUBE/                                # TDMS波形文件 (按炮号分组)
│   │   ├── 1/  (1_Tube.tdms, 1_Water.tdms)
│   │   ├── 2/  └─ ...
│   │   └── ...
│   ├── TUBE_logs/
│   │   ├── 1/  (1_operation.log)
│   │   ├── 2/   └─ ...  
│   │   └── ...                              # 可选操作日志文件 (不再作为必需依赖)
│   ├── PLC_logs/                            # PLC互锁日志文件
│   ├── channels/                            # 通道元数据 (JSON, extract_channels.py生成)
│   │   ├── channels_1.json
│   │   ├── channels_2.json
│   │   └── ...
│   ├── extract_channels.py                  # 通道元数据提取脚本
│   └── watch_tdms.py                        # TDMS文件监控脚本
│
├── docker/                                  # ✅ Docker配置
│   ├── docker-compose.yml                   # 一键启动 Kafka(3节点) + InfluxDB + MySQL
│   └── data/                                # Docker容器的持久化数据
│
├── scripts/                                 # ✅ 工具脚本
│   └── projectctl.sh                        # 项目全量管理脚本 (启动/停止/重启/状态)
│
├── data_publisher.py                        # Kafka数据发布脚本 (根目录)
├── read_wave_data.py                        # 波形数据读取脚本 (根目录)
├── pom.xml                                  # Maven配置
├── ARCHITECTURE.md                          # ✅ 架构设计文档
├── README.md                                # 本文件
└── ...
```


## 运行依赖

| 依赖 | 版本 | 说明 |
|------|------|------|
| **JDK** | 17+ | ✅ 已安装 (Java 17.0.17) |
| **Maven** | 3.6+ | ✅ 项目依赖管理 |
| **MySQL** | 8.0+ | ✅ 关系型数据库 (localhost:3306) |
| **InfluxDB** | 2.7+ | ✅ 时序数据库 (localhost:8086) |
| **Kafka** | 3.x (Confluent 8.1.1) | ✅ 消息队列 (3节点高可用) |
| **Docker** | 20.10+ | ✅ 容器化运行 |
| **Docker Compose** | 2.x (docker compose) | ✅ 快速启动依赖服务 |
| **Python** | 3.8+ | ✅ 数据处理脚本 |

### Python依赖包 (可选)
```bash
pip install nptdms kafka-python influxdb-client
```
用于运行：
- `extract_channels.py` - 从TDMS提取通道元数据
- `read_wave_data.py` - 读取TDMS波形
- `data_publisher.py` - 发送数据到Kafka

## 快速开始

### 1️⃣ 启动基础设施 (Kafka + MySQL + InfluxDB)

```bash
cd docker
export CLUSTER_ID=$(docker run --rm confluentinc/cp-kafka:8.1.1 kafka-storage random-uuid)  # 生成Kafka集群ID
export MYSQL_ROOT_PASSWORD=devroot
export MYSQL_DATABASE=wavedb
export MYSQL_USER=wavedb
export MYSQL_PASSWORD=wavedb123

docker compose up -d
```
提示：
- 使用 `./scripts/projectctl.sh start` 时会通过 `confluentinc/cp-kafka:8.1.1` 生成并持久化 `CLUSTER_ID`（保存到 `run/cluster_id`），并在未设置 `MYSQL_*` 时使用默认值（见上方示例）。
- 如需自定义，请在运行脚本前导出环境变量或在 `docker/.env` 中设置。
- 仅支持 Docker Compose v2（`docker compose`）。如系统只有 `docker-compose` v1，会出现 `ContainerConfig` 报错，需要升级到 v2。
- 若 `docker/data/kafka*` 已存在，脚本会读取其中 `meta.properties` 的 `cluster.id` 并更新 `run/cluster_id`，避免集群 ID 不一致导致 Kafka 反复重启；如需重置集群，请先删除 `docker/data/kafka*`。

**检查状态**：
```bash
docker compose ps
# 应看到: kafka1, kafka2, kafka3, kafka-ui, mysql, influxdb 均为 Up
```

### 2️⃣ 编译项目

```bash
mvn clean package -DskipTests
# 生成 target/kafka-demo-1.0.0.jar
```

### 3️⃣ 启动应用

```bash
java -jar target/kafka-demo-1.0.0.jar \
  --spring.profiles.active=dev
```

或使用后台运行脚本：
```bash
nohup java -jar target/kafka-demo-1.0.0.jar > logs/app.log 2>&1 &
```

### 4️⃣ 访问应用

| 服务 | URL | 说明 |
|------|-----|------|
| **Web UI** | http://localhost:8080 | 波形展示、数据查询 |
| **Kafka UI** | http://localhost:18080 | Kafka集群可视化管理 |
| **InfluxDB UI** | http://localhost:8086 | 时序数据库管理 |


## 📚 API接口速查

> 旧接口 `/api/data/*` 已移除，访问将返回 410，请使用 Kafka 同步后再通过 `/api/hybrid/*` 查询。

### 📢 Kafka数据管道 (`/api/kafka/*`)
触发文件→Kafka→数据库的全链路同步

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/kafka/health` | 健康检查 |
| GET | `/api/kafka/send?message=Hello` | 发送单条消息（测试） |
| GET | `/api/kafka/send-with-key?key=k1&message=Hello` | 发送带Key消息（测试） |
| GET | `/api/kafka/batch?count=10` | 批量发送消息（测试） |
| GET | `/api/kafka/sync/shot?shotNo=1` | 同步单个炮号 |
| GET | `/api/kafka/sync/batch?shotNos=1,2,3` | 批量同步 |
| POST | `/api/kafka/sync/all` | 全量同步所有炮号 |

> 失败事件：当同步失败（如元数据缺失或同步异常）以及 Kafka 发送失败时，会写入 `ingest-error` 主题，并由 DataConsumer 落库。

### 💾 数据库查询 (`/api/database/*`)
从MySQL查询各类数据

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/database/stats` | 数据统计 |
| GET | `/api/database/metadata?shotNo=1` | 元数据查询 |
| GET | `/api/database/wavedata?shotNo=1` | 波形数据查询 |
| GET | `/api/database/logs?shotNo=1` | 日志查询 |
| GET | `/api/database/tables` | 列出表 |
| GET | `/api/database/tables/{table}?limit=100&offset=0` | 查询表数据（分页） |

### 🌐 混合查询 (`/api/hybrid/*`)
MySQL + InfluxDB组合查询，高性能波形展示

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/hybrid/shots` | 炮号列表 |
| GET | `/api/hybrid/stats` | MySQL/Influx统计 |
| GET | `/api/hybrid/shots/{shotNo}` | 元数据+通道 |
| GET | `/api/hybrid/shots/{shotNo}/channels` | 通道列表 |
| GET | `/api/hybrid/shots/{shotNo}/complete` | 元数据+示例波形 |
| GET | `/api/hybrid/waveform?shotNo=1&channelName=InPower` | 波形数据 (InfluxDB) |
| GET | `/api/hybrid/timeline/{shotNo}` | 操作日志时间线 |

> 说明：`/api/hybrid/*` 不回退到文件系统，数据库或InfluxDB无数据时直接返回错误。

### 🧰 InfluxDB维护 (`/api/influxdb/*`)
| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/influxdb/cleanup?shotNo=1&channelName=FilaCurrent` | 清理指定通道重复数据 |
| POST | `/api/influxdb/fix-all` | 批量修复所有数据 |
| GET | `/api/influxdb/integrity-report` | 数据完整性报告 |

### 🧪 ECRH分析 (`/api/ecrh/*`)
| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/ecrh/protection-events?shotNo=1&start=...&end=...&severity=TRIP&deviceId=Tube` | 保护事件查询 |
| GET | `/api/ecrh/operation-logs?shotNo=1&start=...&end=...&command=SET_POWER` | 操作日志检索 |
| GET | `/api/ecrh/waveform/window?shotNo=1&channelName=InPower&dataType=Tube&start=...&end=...` | 波形窗口（MySQL压缩数据） |
| GET | `/api/ecrh/waveform/spectrum?shotNo=1&channelName=InPower&dataType=Tube&start=...&end=...` | 频谱计算（FFT） |
| POST | `/api/ecrh/generate` | 生成操作日志与保护事件 |

### 🔌 WebSocket实时推送
- STOMP端点：`ws://localhost:8080/ws`
- 发送：`/app/subscribe/{shotNo}`
- 订阅：`/topic/shots`、`/topic/shot/{shotNo}`、`/topic/shot/{shotNo}/wave`、`/topic/shot/{shotNo}/operation`、`/topic/shot/{shotNo}/plc`、`/topic/status`

### API使用示例

```bash
# 获取所有炮号
curl http://localhost:8080/api/hybrid/shots
# 输出: [1,2,3,4,5,6,176]

# 获取炮号1的元数据
curl http://localhost:8080/api/hybrid/shots/1 | python -m json.tool

# 获取炮号1、通道InPower的波形 (从InfluxDB)
curl 'http://localhost:8080/api/hybrid/waveform?shotNo=1&channelName=InPower'

# 同步炮号1到数据库
curl 'http://localhost:8080/api/kafka/sync/shot?shotNo=1'

# 获取炮号1的操作日志时间线
curl 'http://localhost:8080/api/hybrid/timeline/1'
```

## 🛠️ 数据准备与辅助脚本

| 脚本 | 位置 | 用途 | 说明 |
|------|------|------|------|
| `extract_channels.py` | data/ | 通道元数据提取 | 扫描TDMS生成 `channels/channels_*.json` |
| `watch_tdms.py` | data/ | TDMS文件监控 | 监控TDMS文件变化，自动触发处理 |
| `read_wave_data.py` | 根目录 | 波形读取 | 被FileDataSource调用，解析TDMS波形 |
| `data_publisher.py` | 根目录 | 数据发布 | 将文件数据发送到Kafka，测试网络源 |
| `projectctl.sh` | scripts/ | 项目管理 | 启动/停止/重启/状态检查 |

**通道元数据处理流程**：
1. 首先运行 `python data/extract_channels.py --all` 生成 `channels/*.json`
2. `DataConsumer` 消费 wave-data 时写入数据库 `channels` 表
3. `FileDataSource.getChannelNames()` 优先读数据库，回退到JSON，最后使用默认通道

**保护事件处理流程**：
1. `protection-event` 主题写入保护事件消息
2. `DataConsumer` 写入 `protection_events` 表
3. `/api/ecrh/protection-events` 提供查询接口

**ECRH生成器流程**：
1. 调用 `/api/ecrh/generate` 触发生成
2. 从 `wave_data` 读取波形并写入 `operation_log`/`protection_events`
3. 前端通过 `/api/ecrh/*` 联动展示波形与事件

## ⚙️ 主要配置说明

编辑 `src/main/resources/application.yml`：

```yaml
app:
  data:
    tube.path: data/TUBE                    # TDMS波形文件路径
    logs.path: data/TUBE_logs               # 操作日志路径
    plc.path: data/PLC_logs                 # PLC互锁路径
    channels.path: data/channels            # 通道元数据路径

  kafka:
    bootstrap-servers: localhost:19092,localhost:29092,localhost:39092
    topic:
      metadata: shot-metadata
      wavedata: wave-data
      operation: operation-log
      plc: plc-interlock
      protection: protection-event
    group-id: data-consumer-group-v2

  protection:
    window:
      before-ms: 100
      after-ms: 200

  waveform:
    segment:
      max-points: 10000
    window:
      max-points: 50000

  spectrum:
    max-points: 65536

  influxdb:
    enabled: true                           # 启用InfluxDB写入
    url: http://localhost:8086
    token: my-super-secret-token
    org: wavedata
    bucket: waveforms

  network:
    enabled: false                          # TCP接收器开关
    port: 9999

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/wavedb?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: root
    password: devroot

  jpa:
    hibernate:
      ddl-auto: update                      # 自动创建/更新表

server:
  port: 8080
```

## 🧪 测试

**使用项目管理脚本**：
```bash
# 启动应用
./scripts/projectctl.sh start

# 启动并启用 TDMS watcher（可选）
./scripts/projectctl.sh start --watch

# 检查状态
./scripts/projectctl.sh status

# 停止应用
./scripts/projectctl.sh stop

# 重启应用
./scripts/projectctl.sh restart
```
说明：
- 脚本会在首次启动时通过 `confluentinc/cp-kafka:8.1.1` 生成 `run/cluster_id` 供 Kafka KRaft 使用，并默认填充 `MYSQL_*` 环境变量。
- 若已有自定义配置，请在运行脚本前导出环境变量（或使用 `docker/.env`）。

**手动测试示例**：
```bash
# 检查应用是否运行
curl http://localhost:8080/api/hybrid/shots

# 验证Kafka管道
curl -X POST http://localhost:8080/api/kafka/sync/all

# 检查InfluxDB数据
curl http://localhost:8080/api/hybrid/shots
```

## ❓ 常见问题 & 解决方案

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| **通道列表为空** | 未生成通道元数据 | 运行 `python data/extract_channels.py --all` |
| **数据库连接失败** | MySQL未启动或凭证错误 | 检查 `docker compose ps` 或更新 `application.yml` |
| **波形数据为空** | InfluxDB未启用或未同步数据 | 运行 `/api/kafka/sync/all` 同步数据 |
| **混合查询返回404** | 数据库或InfluxDB无数据 | 先通过Kafka管道同步数据 |
| **采样点数显示错误** | 前端缓存 | 清除浏览器缓存或刷新页面 |

## 📞 获取帮助

- 查看应用日志：`tail -f logs/app.log`
- Kafka UI管理：http://localhost:18080
- InfluxDB查询：http://localhost:8086
- 架构文档：查看 [ARCHITECTURE.md](ARCHITECTURE.md)
---

## 附录：专利摘要与权利要求（摘录） 🔖

### 标题
**一种双数据源工业波形数据处理方法及系统**

### 摘要
本发明公开了一种双数据源工业波形数据处理方法及系统，应用于 TDMS 文件链路与消息队列链路并行场景。该方法将 `shotNo`、`fileName`、`filePath`、`expectedDuration`、`actualDuration`、`status` 统一建模，按 `shot-metadata`、`wave-data`、`operation-log`、`plc-interlock` 主题分别流式处理，结构化数据写入关系型数据库，波形数据压缩后写入时序数据库并在查询端解压；通过 REST 接口触发单/批量和全量同步，通过 WebSocket 推送处理状态，在主源不可用时切换备用源并在恢复后回退。本发明实现双源数据一致处理与连续服务。

### 权利要求书
1. 一种双数据源工业波形数据处理方法，其特征在于，包括：接收离线文件源与在线网络源的工业波形数据；基于统一字段模型对输入数据执行标准化建模；按数据消息主题和步骤路由并由对应消费者处理；将结构化数据与波形时序数据分层持久化；通过服务接口返回查询结果与处理状态。

2. 根据权利要求1所述的方法，其特征在于，所述离线文件源包括 TDMS 波形文件与日志文件，所述在线网络源包括消息队列中的消息作为输入源。

3. 根据权利要求1所述的方法，其特征在于，所述统一字段模型至少包括 `shotNo`、`fileName`、`filePath`、`expectedDuration`、`actualDuration` 和 `status`。

4. 根据权利要求1所述的方法，其特征在于，所述预设消息主题至少包括 `shot-metadata`、`wave-data`、`operation-log` 和 `plc-interlock`。

5. 根据权利要求1所述的方法，其特征在于，所述方法支持单任务同步、批量同步和全量同步模式，并通过 REST 接口触发、通过 WebSocket 推送处理状态。

6. 根据权利要求1所述的方法，其特征在于，元数据、操作日志和 PLC 互锁日志分别写入关系型数据库中的对应表，波形时序数据在压缩后写入时序数据库并在查询端解压。

````
