# 系统架构 — Kafka‑First TDMS Pipeline

## 设计原则

1. **TDMS 为唯一事实来源**：当前所有数据（元数据、操作事件、保护事件、波形）均从 TDMS 文件派生；未来可替换为真实系统直接产生的事件流
2. **Kafka‑First**：任何写入 MySQL 或 InfluxDB 的数据，都必须先作为消息进入 Kafka；消费者从 Kafka 拉取后投影落库
3. **幂等与可追溯**：每条 Kafka 消息记录到 `source_records` 表（topic + partition + offset 唯一约束）；事件通过 `dedup_key` 避免重复
4. **DLQ 兜底**：任何消费失败自动转发到 `ecrh.pipeline.dlq.v1`，可重试或人工排查

## 总体数据流

```
┌─────────────────────────────────────────────────────────────────┐
│  数据源层                                                        │
│  TDMS 文件 (data/TUBE/{shotNo}/)                                 │
└─────────────────┬───────────────────────────────────────────────┘
                  │
        Python 派生 (tdms_preprocessor.py)
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│  派生层                                                          │
│  data/derived/{shotNo}/                                          │
│    artifact.json / shot_meta.json / signal_catalog.jsonl         │
│    operation_events.jsonl / protection_events.jsonl              │
│    waveform_ingest_request.json / waveform_channel_batch.jsonl   │
└─────────────────┬───────────────────────────────────────────────┘
                  │
        POST /api/ingest/shot?shotNo=N
        DerivedOutputReader → DerivedKafkaPublisher
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│  消息层 — Kafka (12 个主题)                                       │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────┐   │
│  │ 字典主题 (4)  │  │ 目录/资产 (3) │  │ 事件 (2)             │   │
│  │ dict.*        │  │ artifact     │  │ event.operation      │   │
│  │               │  │ shot.meta    │  │ event.protection     │   │
│  │               │  │ signal.cat.. │  │                      │   │
│  └──────┬───────┘  └──────┬───────┘  └──────────┬───────────┘   │
│         │                 │                      │               │
│  ┌──────────────────────┐  ┌─────────────────────────────┐      │
│  │ 波形 (2)              │  │ DLQ (1)                     │      │
│  │ waveform.ingest.req  │  │ pipeline.dlq.v1             │      │
│  │ waveform.channel.bat │  │                             │      │
│  └──────┬───────────────┘  └─────────────────────────────┘      │
└─────────┼───────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│  投影层（消费者）                                                  │
│                                                                   │
│  ArtifactConsumer ──→ ArtifactProjectionService ──→ MySQL        │
│    • tdms_artifacts (文件资产, SHA256 幂等)                        │
│    • shots (炮号元数据, UPSERT)                                   │
│    • signal_catalog + signal_source_map (信号目录与通道映射)       │
│                                                                   │
│  EventConsumer ──→ EventProjectionService ──→ MySQL              │
│    • events (统一事件表, dedup_key 幂等)                          │
│    • event_operation_detail / event_protection_detail             │
│                                                                   │
│  DictConsumer ──→ DictProjectionService ──→ MySQL                │
│    • protection_type_dict / operation_mode_dict                   │
│    • operation_task_dict / operation_type_dict                    │
│                                                                   │
│  WaveformConsumer ──→ WaveformProjectionService ──→ InfluxDB     │
│    • 波形导入请求: 更新 artifact 的 waveform_ingest_status        │
│    • 波形批次: WaveformInfluxWriter 按 5000 点 flush 写入        │
│                                                                   │
│  KafkaRecordProcessor (通用):                                     │
│    • 创建 source_records (幂等追踪)                               │
│    • 失败时发 DLQ                                                 │
└─────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────┐
│  查询层                                                          │
│                                                                   │
│  REST API (Spring Boot 8080)                                     │
│    /api/shots           — 炮号列表                                │
│    /api/events/*        — 操作/保护事件查询                        │
│    /api/waveform        — InfluxDB 波形查询                       │
│    /api/waveform/channels — 炮号可用通道                          │
│    /api/ingest/shot     — 触发导入                                │
│                                                                   │
│  前端 (React 18 + ECharts 5)                                     │
│    • 炮号选择器 + 元数据卡片                                      │
│    • Tube / Water 通道切换                                        │
│    • 波形图 + FFT 频谱 + DataZoom                                 │
│    • 保护事件表 + 操作日志表 + 详情面板                            │
└─────────────────────────────────────────────────────────────────┘
```

## Kafka 主题详细设计

系统定义了 12 个 Kafka 主题，全部 3 分区、3 副本、min.insync.replicas=2。

### 字典与目录（Compact 策略）

保留策略为 `compact`，保存每个 key 的最新值。

| 主题 | 说明 | 消息 Key | 消费者 |
|------|------|----------|--------|
| `ecrh.dict.protection_type.v1` | 保护类型字典 | `protection_type_code` | DictConsumer |
| `ecrh.dict.operation_mode.v1` | 操作模式字典 | `operation_mode_code` | DictConsumer |
| `ecrh.dict.operation_task.v1` | 操作任务字典 | `operation_task_code` | DictConsumer |
| `ecrh.dict.operation_type.v1` | 操作类型字典 | `operation_type_code` | DictConsumer |
| `ecrh.tdms.artifact.v1` | TDMS 文件资产 | `sha256_hex` | ArtifactConsumer |
| `ecrh.shot.meta.v1` | 炮号元数据 | `shot_no` | ArtifactConsumer |
| `ecrh.signal.catalog.v1` | 信号目录与映射 | `source_system:source_name` | ArtifactConsumer |

### 事件（Delete 策略，7 天保留）

| 主题 | 说明 | 消息 Key | 消费者 |
|------|------|----------|--------|
| `ecrh.event.operation.v1` | 操作事件 | `shot_no` | EventConsumer |
| `ecrh.event.protection.v1` | 保护事件 | `shot_no` | EventConsumer |

### 波形（Delete 策略，7 天保留）

| 主题 | 说明 | 消息 Key | 消费者 |
|------|------|----------|--------|
| `ecrh.waveform.ingest.request.v1` | 波形导入请求 | `sha256_hex` | WaveformConsumer |
| `ecrh.waveform.channel.batch.v1` | 波形通道批次 | `artifact_id:channel_name` | WaveformConsumer |

### DLQ

| 主题 | 说明 | 消息 Key |
|------|------|----------|
| `ecrh.pipeline.dlq.v1` | 消费失败转储 | 原消息 key |

DLQ 消息包含：`failed_topic`、`failed_partition`、`failed_offset`、`error_type`、`error_message`、`raw_payload_json`、`retryable` 标志。

## 消费者配置

| 参数 | 值 | 说明 |
|------|-----|------|
| `group.id` | `ecrh-projection-group` | 全部消费者共享 |
| `auto.offset.reset` | `earliest` | 新消费者组从头消费 |
| ACK 模式 | manual | 确保处理完成后才提交 offset |
| Producer `acks` | `all` | 确保消息写入所有同步副本 |
| Producer 压缩 | `snappy` | 减少网络传输 |
| Producer 重试 | `3` 次 | 自动重试 |

## InfluxDB 数据模型

存储在 `waveforms` bucket 中，measurement 为 `waveform`。

### Tags

| Tag | 类型 | 说明 |
|-----|------|------|
| `shot_no` | string | 炮号 |
| `channel_name` | string | 通道名（原始中文名）|
| `data_type` | string | `TUBE` 或 `WATER` |
| `source_system` | string | 来源系统（`ECRH`）|
| `process_id` | string | 信号处理流 ID |
| `artifact_id` | string | 源文件资产 ID |

### Fields

| Field | 类型 | 说明 |
|-------|------|------|
| `value` | float | 采样值 |
| `sample_index` | int | 采样点索引 |

### 写入策略

- 按通道分批，每 chunk 4096 个采样点
- `WaveformInfluxWriter` 每 5000 个 Point 执行一次 flush
- 时间精度：nanosecond
- 写入模式：批量非阻塞

## Java 源码模块

### 包结构（com.example.kafka）

| 包 | 文件数 | 职责 |
|---|--------|------|
| `config` | 3 | Kafka 主题定义（`KafkaConfig`）、InfluxDB 连接（`InfluxDBConfig`）、主题名常量（`TopicNames`）|
| `controller` | 4 | REST 端点：导入、事件查询、炮号查询、波形查询 |
| `consumer` | 5 | Kafka 消费者 + 通用处理器 `KafkaRecordProcessor` |
| `service` | 10 | 核心业务：导入编排、文件读取、Kafka 发布、投影写库、查询服务 |
| `entity` | 12 | JPA 实体定义，映射 11 张 MySQL 表 |
| `repository` | 12 | Spring Data JPA Repository 接口 |
| `model` | 9 | 请求/响应/内部数据模型 |
| `producer` | 1 | `KafkaMessagePublisher`（同步发送 + JSON 序列化）|
| `util` | 8 | 工具类：payload 解析、校验、去重 key、哈希、压缩 |

### 关键服务

| 服务 | 职责 |
|------|------|
| `TdmsIngestService` | 导入编排：验证 shotNo → 读取派生文件 → 发布到 Kafka |
| `DerivedOutputReader` | 从 `data/derived/{shotNo}/` 读取 7 个 JSON/JSONL 文件，组装 `DerivedPayload` |
| `DerivedKafkaPublisher` | 将 `DerivedPayload` 拆分发送到相应 Kafka 主题（含主通道判断逻辑）|
| `ArtifactProjectionService` | 从 Kafka 投影：资产 → `tdms_artifacts`；炮号 → `shots`；信号 → `signal_catalog` + `signal_source_map` |
| `EventProjectionService` | 从 Kafka 投影：事件 → `events` + 操作/保护明细表（`dedup_key` 幂等）|
| `WaveformProjectionService` | 从 Kafka 投影：波形请求更新 artifact 状态；波形批次解码后写 InfluxDB |
| `WaveformInfluxWriter` | 构建 InfluxDB Point 并批量写入（5000 点 / 次 flush）|
| `WaveformQueryService` | 构建 Flux 查询从 InfluxDB 读取波形序列和可用通道列表 |
| `EventQueryService` | 从 MySQL 查询事件（支持 shotNo / 时间范围 / severity / processId / eventCode 过滤）|

## 前端架构

- **技术**：React 18（CDN，无构建步骤）+ Babel standalone + ECharts 5
- **文件**：`src/main/resources/static/` 下的 `index.html`、`app.js`、`style.css`
- **功能**：
  - 炮号选择与元数据展示
  - Tube / Water 通道切换（按炮号从 InfluxDB 动态加载通道列表）
  - ECharts 波形绘图（支持 DataZoom 缩放）
  - 客户端 FFT 频谱分析（Cooley-Tukey radix-2）
  - 保护事件表 + 操作日志表 + 事件详情面板
  - 事件点击跳转：自动定位事件前后 ±2s 的波形窗口