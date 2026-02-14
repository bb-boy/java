# 波形数据展示系统

**项目版本**: 1.0.0  
**最后更新**: 2026年1月11日  
**构建状态**: ✅ 构建成功 | 📦 运行中

## 系统简介

一套完整的**波形数据采集、处理、存储与展示**系统，支持TDMS文件和网络两种数据源。包含以下核心功能：

- 📊 **实时波形展示**：ECharts图表，动态显示采样点数量
- 🔄 **双数据源**：本地文件 (FileDataSource) / Kafka网络 (NetworkDataSource)，支持运行时切换与主备回退
- 📢 **Kafka数据管道**：文件 → Kafka → MySQL/InfluxDB → 前端，实现异步解耦
- 💾 **混合存储**：
  - MySQL: 元数据、操作日志、PLC互锁 (结构化数据)
  - InfluxDB: 波形时序数据 (高效时序查询)
- 🌐 **REST API + WebSocket**：完整的数据接口 + 实时推送
- 📋 **辅助工具**：Python脚本进行通道提取、数据发布、冒烟测试

## 核心能力

- ✅ **文件/网络双数据源**：运行时可切换，支持主备回退 (fallback)
- ✅ **Kafka数据管道**：文件 → Kafka → DataConsumer → MySQL/InfluxDB
- ✅ **REST API**：炮号列表、元数据、波形数据、操作日志、PLC互锁、数据源状态
- ✅ **WebSocket推送**：实时数据更新推送
- ✅ **Web UI**：ECharts波形展示、炮号选择、通道列表、操作日志时间线
- ✅ **数据采样统计**：波形图表标题显示采样点数量
- ✅ **完整的辅助工具链**：通道提取、波形读取、Kafka数据发布、冒烟测试

## 运行模式与数据流

### 数据源直读模式 (`/api/data/*`)
- 直接从主数据源读取（`app.data.source.primary=file|network`）
- 当 `app.data.source.fallback=true` 时，自动合并备用数据源结果
- 适用于: 炮号列表、元数据、波形、通道、日志

### Kafka数据管道模式 (`/api/kafka/sync/*`)
- 文件 → Kafka → DataConsumer → MySQL/InfluxDB 全链路同步
- 提供 `/api/kafka/sync/shot?shotNo=1` 等端点触发导入
- 同步完成后通过 `/api/database` 或 `/api/hybrid` 查询数据库

### 混合查询模式 (`/api/hybrid/*`)
- 从MySQL查询元数据、操作日志、PLC互锁
- 从InfluxDB查询波形时序数据
- 适用于: 高性能波形展示、时序分析

**注意**：
- Kafka网络模式需要Kafka集群运行，可用 `docker-compose` 快速启动
- InfluxDB默认开启写入 (`app.influxdb.enabled=true`)，未部署时请禁用或配置正确token

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
│   │   ├── DataController.java              # 数据源直读 API (/api/data/*)
│   │   ├── KafkaController.java             # Kafka管道 API (/api/kafka/*)
│   │   ├── DatabaseController.java          # 数据库查询 API (/api/database/*)
│   │   ├── HybridDataController.java        # 混合查询 API (/api/hybrid/*) - MySQL+InfluxDB
│   │   ├── InfluxDBMaintenanceController.java # InfluxDB维护接口
│   │   └── WebSocketController.java         # WebSocket端点
│   │
│   ├── service/                             # 服务层
│   │   ├── DataService.java                 # 主/备数据源管理与切换
│   │   ├── DataPipelineService.java         # 文件→Kafka→DB同步管道
│   │   ├── DataQueryService.java            # 数据库通用查询服务
│   │   ├── InfluxDBService.java             # InfluxDB时序数据服务
│   │   ├── MessageProducer.java             # Kafka生产者
│   │   ├── MessageConsumer.java             # Kafka消费者
│   │   └── InfluxDBCleanupService.java      # InfluxDB清理维护
│   │
│   ├── datasource/                          # 数据源实现 (策略模式)
│   │   ├── DataSource.java                  # 接口定义
│   │   ├── FileDataSource.java              # 本地文件源 - 读取TDMS/日志/PLC
│   │   └── NetworkDataSource.java           # 网络源 - Kafka消费
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
│   │
│   ├── entity/                              # 数据库实体 (JPA)
│   │   ├── ShotMetadataEntity.java          # 炮号元数据表
│   │   ├── WaveDataEntity.java              # 波形数据表
│   │   ├── OperationLogEntity.java          # 操作日志表
│   │   ├── PlcInterlockEntity.java          # PLC互锁表
│   │   └── ChannelEntity.java               # 通道元数据表
│   │
│   ├── model/                               # 数据传输对象 (DTO)
│   │   ├── ShotMetadata.java                # 炮号元数据
│   │   ├── WaveData.java                    # 波形数据
│   │   ├── OperationLog.java                # 操作日志
│   │   └── PlcInterlock.java                # PLC互锁
│   │
│   └── repository/                          # 数据访问层 (Spring Data JPA)
│       ├── ShotMetadataRepository.java
│       ├── WaveDataRepository.java
│       ├── OperationLogRepository.java
│       ├── PlcInterlockRepository.java
│       └── ChannelRepository.java
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
│   ├── TUBE_logs/                           # 操作日志文件 (按炮号分组)
│   ├── PLC_logs/                            # PLC互锁日志文件
│   └── channels/                            # 通道元数据 (JSON, extract_channels.py生成)
│       ├── channels_1.json
│       ├── channels_2.json
│       └── ...
│
├── docker/                                  # ✅ Docker配置
│   ├── docker-compose.yml                   # 一键启动 Kafka(3节点) + InfluxDB + MySQL
│   └── data/                                # Docker容器的持久化数据
│
├── scripts/                                 # ✅ 工具脚本
│   └── projectctl.sh                        # 项目全量管理脚本 (启动/停止/重启/状态)
│
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
| **Docker Compose** | 1.29+ | ✅ 快速启动依赖服务 |
| **Python** | 3.8+ | ⚠️ 可选 (数据处理脚本) |

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
export CLUSTER_ID=$(uuidgen)  # 生成Kafka集群ID
export MYSQL_ROOT_PASSWORD=devroot
export MYSQL_DATABASE=wavedb
export MYSQL_USER=wavedb
export MYSQL_PASSWORD=wavedb123

docker compose up -d
```

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

**文件数据源模式 (默认)**：
```bash
java -jar target/kafka-demo-1.0.0.jar \
  --app.data.source.primary=file \
  --spring.profiles.active=dev
```

**网络数据源模式 (Kafka)**：
```bash
java -jar target/kafka-demo-1.0.0.jar \
  --app.data.source.primary=network \
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

### 📊 数据源直读 (`/api/data/*`)
直接从配置的主数据源读取，支持fallback合并备用源结果

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/data/shots` | 获取所有炮号 |
| GET | `/api/data/shots/{shotNo}/metadata` | 炮号元数据 |
| GET | `/api/data/shots/{shotNo}/complete` | 元数据+波形+日志 |
| GET | `/api/data/shots/{shotNo}/channels` | 通道列表 |
| GET | `/api/data/shots/{shotNo}/logs/operation` | 操作日志 |
| GET | `/api/data/shots/{shotNo}/logs/plc` | PLC互锁 |

### 📢 Kafka数据管道 (`/api/kafka/*`)
触发文件→Kafka→数据库的全链路同步

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/kafka/health` | 健康检查 |
| GET | `/api/kafka/sync/shot?shotNo=1` | 同步单个炮号 |
| POST | `/api/kafka/sync/batch?shotNos=1,2,3` | 批量同步 |
| POST | `/api/kafka/sync/all` | 全量同步所有炮号 |

### 💾 数据库查询 (`/api/database/*`)
从MySQL查询各类数据

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/database/stats` | 数据统计 |
| GET | `/api/database/metadata?shotNo=1` | 元数据查询 |
| GET | `/api/database/logs?shotNo=1` | 日志查询 |

### 🌐 混合查询 (`/api/hybrid/*`)
MySQL + InfluxDB组合查询，高性能波形展示

| 方法 | 端点 | 说明 |
|------|------|------|
| GET | `/api/hybrid/shots` | 炮号列表 |
| GET | `/api/hybrid/shot/{shotNo}` | 元数据+通道 |
| GET | `/api/hybrid/waveform?shotNo=1&channelName=InPower` | 波形数据 (InfluxDB) |
| GET | `/api/hybrid/timeline/{shotNo}` | 操作日志时间线 |

### 🔌 WebSocket实时推送
- 端点：`ws://localhost:8080/ws`
- 订阅：`/topic/shots`、`/topic/shot/{shotNo}` 等

### API使用示例

```bash
# 获取所有炮号
curl http://localhost:8080/api/data/shots
# 输出: [1,2,3,4,5,6,176]

# 获取炮号1的元数据
curl http://localhost:8080/api/data/shots/1/metadata | python -m json.tool

# 获取炮号1、通道InPower的波形 (从InfluxDB)
curl 'http://localhost:8080/api/hybrid/waveform?shotNo=1&channelName=InPower'

# 同步炮号1到数据库
curl 'http://localhost:8080/api/kafka/sync/shot?shotNo=1'

# 获取炮号1的操作日志时间线
curl 'http://localhost:8080/api/hybrid/timeline/1'
```

## 🛠️ 数据准备与辅助脚本

| 脚本 | 用途 | 说明 |
|------|------|------|
| `extract_channels.py` | 通道元数据提取 | 扫描TDMS生成 `channels/channels_*.json` |
| `read_wave_data.py` | 波形读取 | 被FileDataSource调用，解析TDMS波形 |
| `data_publisher.py` | 数据发布 | 将文件数据发送到Kafka，测试网络源 |
| `run_tests.sh` | 冒烟测试 | 编译→启动→API测试 |

**通道元数据处理流程**：
1. 首先运行 `python extract_channels.py --all` 生成 `channels/*.json`
2. `DataConsumer` 消费 wave-data 时写入数据库 `channels` 表
3. `FileDataSource.getChannelNames()` 优先读数据库，回退到JSON，最后使用默认通道

## ⚙️ 主要配置说明

编辑 `src/main/resources/application.yml`：

```yaml
app:
  data:
    tube.path: data/TUBE                    # TDMS波形文件路径
    logs.path: data/TUBE_logs               # 操作日志路径
    plc.path: data/PLC_logs                 # PLC互锁路径
    channels.path: channels/                # 通道元数据路径
    source:
      primary: file                         # 主数据源: file / network
      fallback: true                        # 启用备用数据源自动合并

  kafka:
    bootstrap-servers: localhost:19092,localhost:29092,localhost:39092
    topic:
      metadata: shot-metadata
      wavedata: wave-data
      operation: operation-log
      plc: plc-interlock
    group-id: data-consumer-group-v2

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

**自动化测试**：
```bash
./run_tests.sh
```
会自动：
1. 清理编译产物
2. 编译项目
3. 启动应用
4. 调用核心API进行冒烟测试
5. 查看日志验证功能

**手动测试示例**：
```bash
# 检查应用是否运行
curl http://localhost:8080/api/data/shots

# 验证Kafka管道
curl -X POST http://localhost:8080/api/kafka/sync/all

# 检查InfluxDB数据
curl http://localhost:8080/api/hybrid/shots
```

## ❓ 常见问题 & 解决方案

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| **通道列表为空** | 未生成通道元数据 | 运行 `python extract_channels.py --all` |
| **数据库连接失败** | MySQL未启动或凭证错误 | 检查 `docker compose ps` 或更新 `application.yml` |
| **波形数据为空** | InfluxDB未启用或未同步数据 | 运行 `/api/kafka/sync/all` 同步数据 |
| **切换数据源失败** | 备用数据源不可用 | 确保Kafka集群运行 (docker compose) |
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
