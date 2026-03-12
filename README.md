# ECRH Kafka‑First TDMS Pipeline

## 项目简介

ECRH（电子回旋共振加热）数据管线，将 TDMS 实验数据文件经结构化派生后，通过 Kafka 投影写入 MySQL（元数据、事件、字典、信号目录）与 InfluxDB（时序波形），并提供 REST API 与 Web 前端供查询与可视化。

### 设计目标

- **Kafka‑First**：所有数据必须先进入 Kafka 再由消费者投影写库，保证事件溯源与一致性
- **幂等消费**：`source_records` 按 topic/partition/offset 去重，`events` 按 `dedup_key` 去重
- **双存储分离**：MySQL 存结构化元数据与事件；InfluxDB 存高频时序波形
- **自动化管线**：支持文件系统监控 → 自动派生 → 自动入库的全链路无人值守

### 技术栈

| 层 | 技术 | 版本 |
|---|---|---|
| 后端 | Java + Spring Boot | 17 / 3.2.0 |
| 消息 | Apache Kafka (KRaft) | Confluent 8.1.1 |
| 关系库 | MySQL | 8.0 |
| 时序库 | InfluxDB | 2.7 |
| 前端 | React + ECharts | 18 / 5 |
| 数据派生 | Python 3 + nptdms | 3.10+ |
| 容器 | Docker Compose | v2 |

## 系统架构

```
TDMS 原始文件 (data/TUBE/{shotNo}/)
    │
    ▼  Python 派生 (tools/tdms_preprocessor.py 或 data/watch_tdms.py)
派生输出 (data/derived/{shotNo}/)
    ├── artifact.json              # TDMS 文件资产信息
    ├── shot_meta.json             # 炮号元数据
    ├── signal_catalog.jsonl       # 信号目录与映射
    ├── operation_events.jsonl     # 操作事件
    ├── protection_events.jsonl    # 保护事件
    ├── waveform_ingest_request.json  # 波形导入请求
    └── waveform_channel_batch.jsonl  # 波形通道批次
    │
    ▼  POST /api/ingest/shot?shotNo=N
Java 应用 → DerivedOutputReader → DerivedKafkaPublisher
    │
    ▼  Kafka (12 个主题)
    ├── ArtifactConsumer  → MySQL (tdms_artifacts, shots, signal_catalog, signal_source_map)
    ├── EventConsumer     → MySQL (events + operation/protection detail)
    ├── DictConsumer      → MySQL (4 张字典表)
    └── WaveformConsumer  → InfluxDB (waveform measurement)
    │
    ▼  REST API + 前端
React SPA → ECharts 波形图 / 事件表格 / FFT 频谱分析
```

## 快速启动

### 前置条件

- Docker + Docker Compose v2
- Java 17 + Maven 3.8+
- Python 3.10+（`pip3 install nptdms watchdog`）
- MySQL 客户端（用于初始化 schema）

### 1. 启动基础设施

```bash
cd docker && docker compose up -d
```

该命令启动：

| 服务 | 端口 | 说明 |
|------|------|------|
| Kafka (3 节点 KRaft) | 19092 / 29092 / 39092 | 消息集群 |
| Kafka UI | 18080 | 可视化管理 |
| InfluxDB | 8086 | 时序数据库（org=wavedata, bucket=waveforms）|
| MySQL | 3306 | 关系数据库（root/devroot, db=wavedb）|

### 2. 初始化数据库

```bash
mysql -h 127.0.0.1 -uroot -pdevroot wavedb < sql/ecrh_schema.sql
```

### 3. 构建并启动应用

```bash
# 方式 A：projectctl 一键启动（推荐）
bash scripts/projectctl.sh start

# 方式 B：手动启动
mvn clean package -DskipTests
java -jar target/kafka-demo-1.0.0.jar --spring.profiles.active=dev
```

应用启动后访问 **http://localhost:8080** 打开前端。

### 4. 数据派生与导入

```bash
# 派生单个炮号
python3 tools/tdms_preprocessor.py --shot 1 --input-root data/TUBE --output-root data/derived

# 触发 Kafka 导入
curl -X POST "http://localhost:8080/api/ingest/shot?shotNo=1"
```

### 5. 自动化（可选）

```bash
# 文件监控 + 自动派生 + 自动入库
python3 data/watch_tdms.py --scan-now --api-url http://localhost:8080

# 或通过 projectctl 启动监控
bash scripts/projectctl.sh start --watch
```

## REST API 速查

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/ingest/shot?shotNo=N` | 读取派生文件发布到 Kafka |
| `GET` | `/api/shots` | 查询已入库炮号列表（按炮号降序）|
| `GET` | `/api/events/operation?shotNo=N` | 查询操作事件 |
| `GET` | `/api/events/protection?shotNo=N` | 查询保护事件 |
| `GET` | `/api/waveform?shotNo=N&channelName=X&start=T1&end=T2` | 查询波形时序数据 |
| `GET` | `/api/waveform/channels?shotNo=N&dataType=Tube` | 查询炮号实际可用通道 |

完整 API 文档见 [docs/API.md](docs/API.md)。

## 目录结构

```
├── src/main/java/com/example/kafka/   # Java 后端
│   ├── config/          # Kafka 主题定义、InfluxDB 连接
│   ├── consumer/        # Kafka 消费者（投影写库）
│   ├── controller/      # REST 控制器
│   ├── entity/          # JPA 实体
│   ├── model/           # 请求/响应/内部模型
│   ├── producer/        # Kafka 消息发布
│   ├── repository/      # Spring Data JPA Repository
│   ├── service/         # 业务服务
│   └── util/            # 工具类
├── src/main/resources/
│   ├── application.yml  # 主配置
│   └── static/          # 前端 SPA (React + ECharts)
├── tools/                # Python 派生工具
│   ├── tdms_preprocessor.py       # 派生 CLI 入口
│   ├── tdms_auto_derive.py        # 轮询自动派生
│   ├── validate_derived_output.py # 派生输出校验
│   └── tdms_derive/               # 派生核心包 (13 模块)
├── data/
│   ├── TUBE/            # TDMS 原始文件（按炮号子目录）
│   ├── derived/         # 派生 JSON/JSONL 输出
│   └── watch_tdms.py    # 文件监控 + 补扫 + 自动入库
├── schemas/kafka/       # 12 个 Kafka 主题的 JSON Schema
├── sql/                 # MySQL DDL (ecrh_schema.sql)
├── docker/              # Docker Compose 编排
├── scripts/             # 运维脚本 (projectctl.sh)
└── docs/                # 详细文档
```

## 文档索引

| 文档 | 说明 |
|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 系统架构与 Kafka 主题设计 |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | MySQL 表结构与 InfluxDB 模型详解 |
| [docs/API.md](docs/API.md) | REST API 接口详细文档 |
| [docs/KAFKA_SCHEMAS.md](docs/KAFKA_SCHEMAS.md) | Kafka 消息 Schema 参考 |
| [docs/DOCKER.md](docs/DOCKER.md) | Docker 部署与环境配置 |
| [docs/TOOLS.md](docs/TOOLS.md) | Python 数据派生工具文档 |
| [scripts/README.md](scripts/README.md) | projectctl 运维脚本文档 |
| [data/WATCH_README.md](data/WATCH_README.md) | 文件监控与补扫工具文档 |

## 关键配置

配置文件位于 `src/main/resources/application.yml`，核心项：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/wavedb
    username: root
    password: devroot
  kafka:
    bootstrap-servers: localhost:19092,localhost:29092,localhost:39092

app:
  derivation:
    output-root: /home/pl/java/data/derived   # 派生文件根目录
  influxdb:
    enabled: true
    url: http://localhost:8086
    org: wavedata
    bucket: waveforms
    token: my-super-secret-token
  waveform:
    max-points: 50000                          # 单次查询最大点数
```

MySQL 连接密码可通过 `application-mysql.yml` profile 或环境变量覆盖。