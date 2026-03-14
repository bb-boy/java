# 🧩 系统架构 — Kafka‑First TDMS Pipeline

## 🎯 架构目标

- ✅ 保证数据可追溯、可回放、可验证
- 📊 让结构化事件与高频波形同时可查询
- 🔗 通过 Kafka 形成可靠的数据事实链

## 🧱 关键约束

- 📁 TDMS 是当前唯一事实来源
- 🛰️ 所有写库动作必须经过 Kafka
- 🧾 投影必须幂等且可追溯
- 🚨 消费失败必须进入 DLQ

## 🧪 端到端数据流

```
数据源
TDMS 文件 (data/TUBE/{shotNo}/)
    │
    ▼  Python 派生 (tools/tdms_preprocessor.py / data/watch_tdms.py)
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
Java 导入编排 → 发布到 Kafka
    │
    ▼  Kafka (12 主题)
    │
    ├── ArtifactConsumer → MySQL (资产/炮号/信号目录)
    ├── EventConsumer    → MySQL (事件/明细)
    ├── DictConsumer     → MySQL (4 张字典表)
    └── WaveformConsumer → InfluxDB (波形)
    │
    ▼
REST API → 前端可视化
```

## 🧩 组件职责

### 🐍 Python 派生层

- 🧪 解析 TDMS 文件，生成 7 类派生输出
- 🔍 负责事件检测、波形分块与元数据构建
- 📦 输出格式统一为 JSON/JSONL

### 🧾 Ingest API 与发布层

- 📤 `POST /api/ingest/shot` 读取派生目录并发布到 Kafka
- 🧩 `DerivedOutputReader` 负责读取与组装
- 🛰️ `DerivedKafkaPublisher` 负责按主题拆分与发送

### 🛰️ Kafka 消息层

- 🧭 12 个主题覆盖字典、目录、事件、波形与 DLQ
- 🧱 主题默认 3 分区、3 副本、`min.insync.replicas=2`
- 🧹 字典与目录使用 compact，事件/波形使用 delete

### 🧱 投影层（消费者）

- 🧾 `KafkaRecordProcessor` 统一处理幂等与 DLQ
- 🗂️ `ArtifactConsumer` 写入 `tdms_artifacts`、`shots`、`signal_catalog`、`signal_source_map`
- 📍 `EventConsumer` 写入 `events` 与事件明细表
- 🧭 `DictConsumer` 写入 4 张字典表
- 🌊 `WaveformConsumer` 写入 InfluxDB，并更新波形导入状态

### 🔍 查询层与前端

- 📡 REST API：查询炮号、事件、波形、通道
- 🎨 前端：波形绘图、事件表、FFT 频谱

## 📦 Kafka 主题设计

### 🗂️ 主题总览

| 主题 | 类型 | 保留策略 | Key | 消费者 |
|------|------|---------|-----|--------|
| `ecrh.dict.*.v1` | 字典 | compact | 各类 code | DictConsumer |
| `ecrh.tdms.artifact.v1` | 资产 | compact | `sha256_hex` | ArtifactConsumer |
| `ecrh.shot.meta.v1` | 炮号 | compact | `shot_no` | ArtifactConsumer |
| `ecrh.signal.catalog.v1` | 信号目录 | compact | `source_system:source_name` | ArtifactConsumer |
| `ecrh.event.operation.v1` | 运行事件 | delete | `shot_no` | EventConsumer |
| `ecrh.event.protection.v1` | 保护事件 | delete | `shot_no` | EventConsumer |
| `ecrh.waveform.ingest.request.v1` | 波形导入请求 | delete | `sha256_hex` | WaveformConsumer |
| `ecrh.waveform.channel.batch.v1` | 波形通道批次 | delete | `artifact_id:channel_name` | WaveformConsumer |
| `ecrh.pipeline.dlq.v1` | DLQ | delete | 原消息 key | 无 |

DLQ 消息包含 `failed_topic`、`failed_partition`、`failed_offset`、`error_type`、`error_message`、`raw_payload_json`、`retryable` 等字段。

## 🔁 幂等与一致性

- 🧾 `source_records` 使用 `(topic_name, partition_id, offset_value)` 唯一约束
- 🧷 `events` 使用 `dedup_key` 唯一约束
- 🔗 通过 Kafka 强制“先消息、后落库”的一致性顺序

## ⚙️ 消费者与生产者关键配置

| 项目 | 值 | 说明 |
|------|-----|------|
| `group.id` | `ecrh-projection-group` | 所有投影消费者共享 |
| `auto.offset.reset` | `earliest` | 新组从头消费 |
| `ack-mode` | `manual` | 处理完成后再提交 offset |
| Producer `acks` | `all` | 写入所有同步副本 |
| Producer `compression` | `snappy` | 降低网络传输 |
| Producer `retries` | `3` | 自动重试 |

## 📈 InfluxDB 数据模型

存储在 `waveforms` bucket 中，measurement 为 `waveform`。

### 🏷️ Tags 与 Fields

| 类型 | 名称 | 说明 |
|------|------|------|
| Tag | `shot_no` | 炮号 |
| Tag | `channel_name` | 通道名 |
| Tag | `data_type` | `TUBE` / `WATER` |
| Tag | `source_system` | 来源系统 |
| Tag | `process_id` | 处理流 ID |
| Tag | `artifact_id` | 资产 ID |
| Field | `value` | 采样值 |
| Field | `sample_index` | 采样点索引 |

### ✍️ 写入策略

- 📦 波形按通道分块，每块 4096 点
- ⏱️ `WaveformInfluxWriter` 5000 点触发一次 flush
- ⚡ 时间精度为纳秒，批量非阻塞写入

## 🧭 代码模块与职责

| 包 | 职责 |
|---|------|
| `config` | Kafka 主题配置、InfluxDB 配置、主题名常量 |
| `controller` | REST API |
| `consumer` | Kafka 消费与通用处理器 |
| `service` | 导入编排、投影写库、查询服务 |
| `entity` | JPA 实体 |
| `repository` | Spring Data JPA |
| `model` | 请求/响应/内部模型 |
| `producer` | Kafka 发布 |
| `util` | 解析、校验、哈希、压缩等工具 |

## 🎨 前端架构

- 🧰 技术栈：React 18 + Babel standalone + ECharts 5
- 📁 入口目录：`src/main/resources/static/`
- 🧩 主要能力：炮号选择、波形绘图、FFT 频谱、事件表与详情面板
