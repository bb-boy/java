# Database Design (Kafka‑First)

## 摘要
该数据库用于 Kafka 投影落库，MySQL 存放元数据/事件/目录/字典，波形点存放在 InfluxDB。

## MySQL 表结构
DDL 位于：`sql/ecrh_schema.sql`

### 字典层
- `protection_type_dict`
- `operation_mode_dict`
- `operation_task_dict`
- `operation_type_dict`

### 来源层
- `tdms_artifacts`
  - `UNIQUE(sha256_hex)`
- `source_records`
  - `UNIQUE(topic_name, partition_id, offset_value)`

### 目录层
- `signal_catalog`
- `signal_source_map`

### 事实层
- `shots`
- `events`
  - `UNIQUE(dedup_key)`
- `event_operation_detail`
- `event_protection_detail`

## InfluxDB
- Measurement: `waveform`
- Tags: `shot_no`, `channel_name`, `data_type`, `source_system`, `process_id`, `artifact_id`
- Fields: `value`, `sample_index`

## 说明
- `source_records` 作为 Kafka 消费幂等与追踪表
- `events` 为统一事件总表，细节拆分到明细表
- 波形点不入 MySQL，仅在 InfluxDB 存储
