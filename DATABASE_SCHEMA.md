# 🗄️ 数据库设计 — MySQL + InfluxDB

## 🎯 目标与边界

- 🧾 MySQL 负责结构化元数据、事件与字典
- 🌊 InfluxDB 负责高频波形
- 🛰️ 所有数据由 Kafka 消费者投影写入

DDL 位于 `sql/ecrh_schema.sql`，旧表清理 DDL 位于 `sql/ecrh_drop_legacy.sql`。

## 🧱 MySQL 结构总览

### 🧭 分层模型

```
字典层 (Dict)
  └── protection_type_dict / operation_mode_dict / operation_task_dict / operation_type_dict

来源层 (Source)
  └── tdms_artifacts / source_records

目录层 (Catalog)
  └── signal_catalog / signal_source_map

事实层 (Fact)
  └── shots / events / event_operation_detail / event_protection_detail
```

### 📚 字典层（4 张表）

#### 🛡️ protection_type_dict

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `protection_type_code` | VARCHAR(64) | PK | 保护类型编码 |
| `iter_name` | VARCHAR(128) | | ITER 标准名称 |
| `display_name_zh` | VARCHAR(255) | | 中文显示名 |
| `protection_group` | VARCHAR(128) | | 保护组 |
| `equipment_scope` | VARCHAR(128) | | 设备范围 |
| `detection_mechanism` | VARCHAR(128) | | 检测机制 |
| `default_severity` | VARCHAR(32) | | 默认严重级别 |
| `default_action` | VARCHAR(128) | | 默认动作 |
| `authority_default` | VARCHAR(32) | | 默认权限级别 |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用 |

#### 🧭 operation_mode_dict

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `operation_mode_code` | VARCHAR(64) | PK | 模式编码 |
| `display_name_zh` | VARCHAR(255) | | 中文显示名 |
| `scope_note` | VARCHAR(255) | | 范围说明 |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用 |

#### 🧩 operation_task_dict

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `operation_task_code` | VARCHAR(64) | PK | 任务编码 |
| `iter_name` | VARCHAR(128) | | ITER 标准名称 |
| `display_name_zh` | VARCHAR(255) | | 中文显示名 |
| `task_group` | VARCHAR(128) | | 任务组 |
| `equipment_scope` | VARCHAR(128) | | 设备范围 |
| `allowed_mode_hint` | VARCHAR(255) | | 允许的模式提示 |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用 |

#### 🧪 operation_type_dict

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `operation_type_code` | VARCHAR(64) | PK | 类型编码 |
| `display_name_zh` | VARCHAR(255) | | 中文显示名 |
| `operation_group` | VARCHAR(128) | | 分组 |
| `iter_control_function` | VARCHAR(128) | | ITER 控制功能 |
| `requires_old_new` | BOOLEAN | NOT NULL, DEFAULT FALSE | 是否需要旧值/新值 |
| `requires_task_code` | BOOLEAN | NOT NULL, DEFAULT FALSE | 是否需要任务编码 |
| `requires_mode_code` | BOOLEAN | NOT NULL, DEFAULT FALSE | 是否需要模式编码 |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用 |

### 🧾 来源层（2 张表）

#### 📦 tdms_artifacts

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `artifact_id` | VARCHAR(64) | PK | 资产 ID |
| `shot_no` | INT | NOT NULL | 炮号 |
| `data_type` | VARCHAR(32) | NOT NULL | 数据类型 |
| `file_path` | VARCHAR(512) | NOT NULL | 文件路径 |
| `file_name` | VARCHAR(255) | | 文件名 |
| `file_size_bytes` | BIGINT | | 文件大小 |
| `file_mtime` | DATETIME(3) | | 文件修改时间 |
| `sha256_hex` | VARCHAR(64) | NOT NULL, UNIQUE | 文件 SHA256 哈希 |
| `artifact_status` | VARCHAR(32) | NOT NULL | 资产状态 |
| `waveform_ingest_status` | VARCHAR(32) | | 波形导入状态 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

#### 🧾 source_records

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `source_record_id` | BIGINT | PK, AUTO_INCREMENT | 记录 ID |
| `source_system` | VARCHAR(64) | NOT NULL | 来源系统 |
| `source_entity_type` | VARCHAR(64) | | 实体类型 |
| `source_entity_id` | VARCHAR(128) | | 实体 ID |
| `topic_name` | VARCHAR(128) | NOT NULL | Kafka 主题 |
| `partition_id` | INT | NOT NULL | 分区号 |
| `offset_value` | BIGINT | NOT NULL | 消息偏移量 |
| `message_key` | VARCHAR(256) | | 消息 Key |
| `payload_hash` | VARCHAR(64) | | 载荷哈希 |
| `produced_at` | DATETIME(3) | | 生产时间 |
| `consumed_at` | DATETIME(3) | NOT NULL | 消费时间 |
| `ingest_status` | VARCHAR(32) | NOT NULL | 处理状态 |
| `raw_payload_json` | JSON | | 原始载荷 |

唯一约束：`(topic_name, partition_id, offset_value)`。

### 🗂️ 目录层（2 张表）

#### 📒 signal_catalog

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `process_id` | VARCHAR(128) | PK | 处理流 ID |
| `display_name` | VARCHAR(255) | | 显示名 |
| `signal_class` | VARCHAR(64) | | 信号类别 |
| `unit` | VARCHAR(32) | | 单位 |
| `value_type` | VARCHAR(32) | | 值类型 |
| `device_scope` | VARCHAR(64) | | 设备范围 |
| `is_key_signal` | BOOLEAN | NOT NULL, DEFAULT FALSE | 是否关键信号 |

#### 🧭 signal_source_map

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `map_id` | BIGINT | PK, AUTO_INCREMENT | 映射 ID |
| `source_system` | VARCHAR(64) | NOT NULL | 来源系统 |
| `source_type` | VARCHAR(64) | | 来源类型 |
| `source_name` | VARCHAR(128) | NOT NULL | 通道名 |
| `process_id` | VARCHAR(128) | NOT NULL | 处理流 ID |
| `data_type` | VARCHAR(32) | | 数据类型 |
| `is_primary_waveform` | BOOLEAN | NOT NULL, DEFAULT FALSE | 是否主波形 |
| `is_primary_operation_signal` | BOOLEAN | NOT NULL, DEFAULT FALSE | 是否主操作 |
| `is_primary_protection_signal` | BOOLEAN | NOT NULL, DEFAULT FALSE | 是否主保护 |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | 是否启用 |

唯一约束：`(source_system, source_type, source_name, data_type)`。

### 📌 事实层（3 张表）

#### 🎯 shots

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `shot_no` | INT | PK | 炮号 |
| `shot_start_time` | DATETIME(3) | | 起始时间 |
| `shot_end_time` | DATETIME(3) | | 结束时间 |
| `expected_duration` | DOUBLE | | 预期时长（秒） |
| `actual_duration` | DOUBLE | | 实际时长（秒） |
| `status_code` | VARCHAR(32) | | 状态码 |
| `status_reason` | VARCHAR(255) | | 状态原因 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |
| `updated_at` | DATETIME(3) | NOT NULL | 更新时间 |

#### 🧨 events

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `event_id` | BIGINT | PK, AUTO_INCREMENT | 事件 ID |
| `shot_no` | INT | NOT NULL | 炮号 |
| `source_record_id` | BIGINT | | 关联 source_records |
| `artifact_id` | VARCHAR(64) | | 关联资产 ID |
| `event_family` | VARCHAR(32) | NOT NULL | `OPERATION` / `PROTECTION` |
| `event_code` | VARCHAR(64) | | 事件编码 |
| `event_name` | VARCHAR(255) | | 事件名称 |
| `event_time` | DATETIME(3) | NOT NULL | 事件时间 |
| `process_id` | VARCHAR(128) | | 处理流 ID |
| `message_text` | TEXT | | 事件消息 |
| `message_level` | VARCHAR(32) | | 消息级别 |
| `severity` | VARCHAR(32) | | 严重级别 |
| `source_system` | VARCHAR(64) | NOT NULL | 来源系统 |
| `authority_level` | VARCHAR(32) | | 权限级别 |
| `event_status` | VARCHAR(32) | | 事件状态 |
| `correlation_key` | VARCHAR(128) | | 关联键 |
| `dedup_key` | VARCHAR(256) | NOT NULL, UNIQUE | 去重键 |
| `raw_payload_json` | JSON | | 原始载荷 |
| `created_at` | DATETIME(3) | NOT NULL | 创建时间 |

索引：

- `idx_events_shot_time(shot_no, event_time)`
- `idx_events_family_time(event_family, event_time)`

去重键格式：`sourceSystem|eventFamily|eventCode|shotNo|eventTime|processId|entityId`。

#### 🧰 event_operation_detail

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `event_id` | BIGINT | PK, FK → events | 事件 ID |
| `operation_type_code` | VARCHAR(64) | | 操作类型编码 |
| `operation_mode_code` | VARCHAR(64) | | 操作模式编码 |
| `operation_task_code` | VARCHAR(64) | | 操作任务编码 |
| `channel_name` | VARCHAR(128) | | 通道名 |
| `old_value` | DOUBLE | | 旧值 |
| `new_value` | DOUBLE | | 新值 |
| `delta_value` | DOUBLE | | 变化量 |
| `command_name` | VARCHAR(128) | | 命令名 |
| `command_params_json` | JSON | | 命令参数 |
| `operator_id` | VARCHAR(64) | | 操作员 ID |
| `execution_status` | VARCHAR(32) | | 执行状态 |
| `confidence` | DOUBLE | | 置信度 |

#### 🛡️ event_protection_detail

| 列 | 类型 | 约束 | 说明 |
|----|------|------|------|
| `event_id` | BIGINT | PK, FK → events | 事件 ID |
| `protection_type_code` | VARCHAR(64) | | 保护类型编码 |
| `protection_scope` | VARCHAR(64) | | 保护范围 |
| `trigger_condition` | VARCHAR(255) | | 触发条件 |
| `measured_value` | DOUBLE | | 测量值 |
| `threshold_value` | DOUBLE | | 阈值 |
| `threshold_op` | VARCHAR(16) | | 阈值运算符 |
| `action_taken` | VARCHAR(128) | | 采取动作 |
| `action_latency_us` | BIGINT | | 动作延迟（微秒） |
| `window_start` | DATETIME(3) | | 事件窗口起始 |
| `window_end` | DATETIME(3) | | 事件窗口结束 |
| `related_channels` | JSON | | 关联通道 |
| `evidence_score` | DOUBLE | | 证据分数 |
| `ack_state` | VARCHAR(32) | | 确认状态 |
| `ack_user_id` | VARCHAR(64) | | 确认用户 |
| `ack_time` | DATETIME(3) | | 确认时间 |

## 📈 InfluxDB 数据模型

### 🔐 默认连接信息（见 application.yml）

| 参数 | 值 |
|------|-----|
| URL | http://localhost:8086 |
| Organization | `wavedata` |
| Bucket | `waveforms` |
| Token | `my-super-secret-token` |

### 📌 Measurement: `waveform`

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

- 📦 每通道按 4096 点分块
- ⏱️ 5000 个 Point 触发一次 flush
- ⚡ 时间精度为纳秒

### 🔎 查询示例 (Flux)

```flux
from(bucket: "waveforms")
  |> range(start: 2024-11-27T08:05:31Z, stop: 2024-11-27T08:05:32Z)
  |> filter(fn: (r) => r._measurement == "waveform" and r._field == "value")
  |> filter(fn: (r) => r.shot_no == "176" and r.channel_name == "InPower1")
  |> limit(n: 50000)

from(bucket: "waveforms")
  |> range(start: 0)
  |> filter(fn: (r) => r._measurement == "waveform" and r.shot_no == "176" and r.data_type == "WATER")
  |> keep(columns: ["channel_name"])
  |> distinct(column: "channel_name")
```
