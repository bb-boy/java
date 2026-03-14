# 🚀 ECRH Kafka‑First TDMS Pipeline

## 🧭 一句话说明

这是一个面向 ECRH（电子回旋共振加热）实验的端到端数据管线：把 TDMS 原始文件派生成结构化数据，通过 Kafka 投影写入 MySQL 和 InfluxDB，并提供 REST API 与可视化前端用于查询与分析。

## 🎯 适用场景

- 📌 需要把 TDMS 原始数据标准化、入库，并形成可追溯的数据链路
- 📊 同时管理结构化事件/元数据和高频波形数据
- 🔁 需要 Kafka‑First 的一致性与可回放能力

## ✨ 关键特性

- ✅ Kafka‑First：任何写库数据都先进入 Kafka，保证溯源与一致性
- 🧾 幂等投影：`source_records` 按 topic/partition/offset 去重，`events` 按 `dedup_key` 去重
- 🧱 双存储分离：MySQL 保存结构化元数据与事件；InfluxDB 保存高频时序波形
- 🤖 全链路自动化：支持监控目录 → 派生 → 入库的无人值守流程

## 🧪 核心流程（端到端）

```
TDMS 原始文件 (data/TUBE/{shotNo}/)
    │
    ▼  Python 派生 (tools/tdms_preprocessor.py 或 data/watch_tdms.py)
派生输出 (data/derived/{shotNo}/)
    ├── artifact.json
    ├── shot_meta.json
    ├── signal_catalog.jsonl
    ├── operation_events.jsonl
    ├── protection_events.jsonl
    ├── waveform_ingest_request.json
    └── waveform_channel_batch.jsonl
    │
    ▼  POST /api/ingest/shot?shotNo=N
Java 应用 → DerivedOutputReader → DerivedKafkaPublisher
    │
    ▼  Kafka (12 个主题)
    ├── ArtifactConsumer  → MySQL
    ├── EventConsumer     → MySQL
    ├── DictConsumer      → MySQL
    └── WaveformConsumer  → InfluxDB
    │
    ▼  REST API + 前端
React SPA → 波形图 / 事件表 / FFT 频谱分析
```

## ⚡ 快速开始

### 🧰 前置条件

- 🐳 Docker + Docker Compose v2
- ☕ Java 17 + Maven 3.8+
- 🐍 Python 3.10+（`pip3 install nptdms watchdog`）
- 🧩 MySQL 客户端（用于初始化 schema）

### 1. 🧱 启动基础设施

```bash
cd docker
docker compose up -d
```

📍 服务端口速查：

| 服务 | 端口 | 说明 |
|------|------|------|
| Kafka (3 节点 KRaft) | 19092 / 29092 / 39092 | 消息集群 |
| Kafka UI | 18080 | 可视化管理 |
| InfluxDB | 8086 | 时序数据库 |
| MySQL | 3306 | 关系数据库 |

### 2. 🗄️ 初始化数据库

```bash
mysql -h 127.0.0.1 -uroot -pdevroot wavedb < sql/ecrh_schema.sql
```

### 3. 🚀 构建并启动应用

```bash
# 方式 A：projectctl 一键启动
bash scripts/projectctl.sh start

# 方式 B：手动启动
mvn clean package -DskipTests
java -jar target/kafka-demo-1.0.0.jar --spring.profiles.active=dev
```

🌐 应用启动后访问 `http://localhost:8080` 进入前端。

### 4. 🧪 派生与入库

```bash
# 派生单个炮号
python3 tools/tdms_preprocessor.py --shot 1 --input-root data/TUBE --output-root data/derived

# 触发 Kafka 入库
curl -X POST "http://localhost:8080/api/ingest/shot?shotNo=1"
```

### 5. 🤖 自动化（可选）

```bash
# 文件监控 + 自动派生 + 自动入库
python3 data/watch_tdms.py --scan-now --api-url http://localhost:8080

# 或通过 projectctl 启动监控
bash scripts/projectctl.sh start --watch
```

## 📡 REST API 速查

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/ingest/shot?shotNo=N` | 读取派生文件并发布到 Kafka |
| `GET` | `/api/shots` | 查询已入库炮号列表（按炮号降序） |
| `GET` | `/api/events/operation` | 查询运行事件 |
| `GET` | `/api/events/protection` | 查询保护事件 |
| `GET` | `/api/waveform` | 查询波形时序数据 |
| `GET` | `/api/waveform/channels` | 查询炮号可用通道 |

完整细节见 [docs/API.md](docs/API.md)。

## ⚙️ 关键配置

配置文件位于 `src/main/resources/application.yml`，常用项：

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
    output-root: /home/pl/java/data/derived
  waveform:
    max-points: 50000
  influxdb:
    enabled: true
    url: http://localhost:8086
    org: wavedata
    bucket: waveforms
    token: my-super-secret-token
```

## 🗂️ 目录结构

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
├── tools/               # Python 派生工具
├── data/                # TDMS 原始与派生数据
├── schemas/kafka/       # Kafka 主题 JSON Schema
├── sql/                 # MySQL DDL
├── docker/              # Docker Compose 编排
├── scripts/             # 运维脚本
└── docs/                # 详细文档
```

## 📚 文档索引

| 文档 | 说明 |
|------|------|
| [ARCHITECTURE.md](ARCHITECTURE.md) | 系统架构与主题设计 |
| [DATABASE_SCHEMA.md](DATABASE_SCHEMA.md) | MySQL 表结构与 InfluxDB 模型 |
| [docs/API.md](docs/API.md) | REST API 接口文档 |
| [docs/KAFKA_SCHEMAS.md](docs/KAFKA_SCHEMAS.md) | Kafka 消息 Schema 参考 |
| [docs/DOCKER.md](docs/DOCKER.md) | Docker 部署与环境配置 |
| [docs/TOOLS.md](docs/TOOLS.md) | Python 数据派生工具 |
| [scripts/README.md](scripts/README.md) | projectctl 运维脚本 |
| [data/WATCH_README.md](data/WATCH_README.md) | 文件监控与补扫工具 |

ℹ️ MySQL 连接密码可通过 `application-mysql.yml` profile 或环境变量覆盖。
