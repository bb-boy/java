# Kafka Schema 参考

## 概览

系统使用 12 个 Kafka Topic，所有消息均遵循 JSON Schema (draft-07) 定义。Schema 文件位于 `schemas/kafka/` 目录。

| Schema 文件 | 标题 | 说明 |
|-------------|------|------|
| `ecrh.shot.meta.v1.json` | Shot Meta | 炮元数据（时间线、状态） |
| `ecrh.tdms.artifact.v1.json` | TDMS Artifact | TDMS 文件登记信息 |
| `ecrh.signal.catalog.v1.json` | Signal Catalog | 信号/通道元数据目录 |
| `ecrh.event.operation.v1.json` | Operation Event | 运行事件（设定值变化、爬坡） |
| `ecrh.event.protection.v1.json` | Protection Event | 保护事件（异常、跳闸） |
| `ecrh.waveform.ingest.request.v1.json` | Waveform Ingest Request | 波形入库请求 |
| `ecrh.waveform.channel.batch.v1.json` | Waveform Channel Batch | 波形采样批次 |
| `ecrh.pipeline.dlq.v1.json` | Pipeline DLQ | 死信队列 |
| `ecrh.dict.operation_type.v1.json` | Operation Type Dict | 运行类型字典 |
| `ecrh.dict.operation_mode.v1.json` | Operation Mode Dict | 运行模式字典 |
| `ecrh.dict.operation_task.v1.json` | Operation Task Dict | 运行任务字典 |
| `ecrh.dict.protection_type.v1.json` | Protection Type Dict | 保护类型字典 |

---

## 核心数据 Schema

### ecrh.shot.meta.v1

炮元数据，包含实验时间线和状态。

**必填字段:** `shot_no`

| 字段 | 类型 | 说明 |
|------|------|------|
| `shot_no` | integer | 炮号（唯一标识） |
| `shot_start_time` | string (date-time) | 实验开始时间 |
| `shot_end_time` | string (date-time) | 实验结束时间 |
| `expected_duration` | number | 预期持续时间（秒） |
| `actual_duration` | number | 实际持续时间（秒） |
| `status_code` | string | 状态码（SUCCESS / FAILED） |
| `status_reason` | string | 状态原因说明 |
| `artifact_id` | string | 关联的主 Artifact ID |

---

### ecrh.tdms.artifact.v1

TDMS 文件登记信息，标识数据来源文件。

**必填字段:** `artifact_id`, `shot_no`, `data_type`, `file_path`, `sha256_hex`, `artifact_status`

| 字段 | 类型 | 说明 |
|------|------|------|
| `artifact_id` | string | 全局唯一 Artifact ID |
| `shot_no` | integer | 关联炮号 |
| `data_type` | string | 数据类型（TUBE / WATER） |
| `file_path` | string | 文件完整路径 |
| `file_name` | string | 原始文件名 |
| `file_size_bytes` | integer | 文件大小（字节） |
| `file_mtime` | string (date-time) | 文件最后修改时间 |
| `sha256_hex` | string | SHA-256 校验和（十六进制） |
| `artifact_status` | string | 处理状态（PARSED） |

---

### ecrh.signal.catalog.v1

信号通道元数据目录，描述每个物理通道的属性。

**必填字段:** `source_system`, `source_name`, `data_type`, `process_id`

| 字段 | 类型 | 说明 |
|------|------|------|
| `source_system` | string | 源系统标识 |
| `source_type` | string | 源类型 |
| `source_name` | string | 信号名称 |
| `data_type` | string | 数据分类（TUBE / WATER） |
| `process_id` | string | 过程标识（如 `ECRH:RF:IN_POWER`） |
| `display_name` | string | 显示名称 |
| `unit` | string | 计量单位 |
| `value_type` | string | 值类型分类 |
| `device_scope` | string | 产生信号的设备 |
| `is_primary_waveform` | boolean | 是否为主波形信号 |
| `is_primary_operation_signal` | boolean | 是否为主运行信号 |
| `is_primary_protection_signal` | boolean | 是否为主保护信号 |

---

## 事件 Schema

### ecrh.event.operation.v1

运行事件，记录设定值阶跃、功率爬坡等操作。

**必填字段:** `source_system`, `shot_no`, `event_family`, `event_time`, `event_code`

| 字段 | 类型 | 说明 |
|------|------|------|
| `source_system` | string | 源系统 |
| `authority_level` | string | 权限级别 |
| `correlation_key` | string | 事件关联键 |
| `source_entity_id` | string | 源实体引用 |
| `shot_no` | integer | 炮号 |
| `artifact_id` | string | Artifact 引用 |
| `event_family` | string | 固定值 `"OPERATION"` |
| `event_code` | string | 事件代码 |
| `event_name` | string | 事件名称 |
| `event_time` | string (date-time) | 事件时间戳 |
| `process_id` | string | 过程标识 |
| `channel_name` | string | 关联通道 |
| `message_text` | string | 事件描述 |
| `message_level` | string | 消息级别 |
| `severity` | string | 严重程度 |
| `dedup_key` | string | 去重键 |

**details 嵌套对象:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `operation_type_code` | string | 运行类型代码 |
| `operation_mode_code` | string | 运行模式代码 |
| `operation_task_code` | string | 任务代码 |
| `channel_name` | string | 关联通道 |
| `old_value` | number | 变更前值 |
| `new_value` | number | 变更后值 |
| `delta_value` | number | 变化量 |
| `confidence` | number | 置信度分数 |

**事件代码:**

| 代码 | 说明 |
|------|------|
| `OP_SETPOINT_STEP` | 设定值阶跃检测 |
| `OP_SETPOINT_RAMP` | 设定值爬坡检测 |

---

### ecrh.event.protection.v1

保护事件，记录异常检测和保护动作。

**必填字段:** 与 Operation Event 相同。

**details 嵌套对象:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `protection_type_code` | string | 保护类型代码 |
| `protection_scope` | string | 保护范围（TUBE / HVPS / RF / COOLING） |
| `trigger_condition` | string | 触发条件 |
| `measured_value` | number | 测量值 |
| `threshold_value` | number | 阈值 |
| `threshold_op` | string | 比较运算符 |
| `action_taken` | string | 采取动作（TRIP / SHUTDOWN / ALARM / NONE） |
| `window_start` | string (date-time) | 时间窗口起始 |
| `window_end` | string (date-time) | 时间窗口结束 |
| `related_channels` | array[string] | 关联通道列表 |
| `evidence_score` | number | 证据分数 |

**事件代码:**

| 代码 | 说明 |
|------|------|
| `PRX_NO_WAVE` | 无波形信号 |
| `PRX_PULSE_EARLY_TERMINATION` | 脉冲提前终止 |
| `PRX_PEAK_ABS_HIGH` | 绝对峰值过高 |
| `PRX_RAPID_DROPOUT` | 快速跌落 |
| `PRX_COOLING_ANOMALY` | 冷却异常 |

---

## 波形 Schema

### ecrh.waveform.ingest.request.v1

波形入库请求，描述一次入库操作包含的所有信号。

**必填字段:** `shot_no`, `request_id`, `measurement`, `source_system`, `authority_level`, `signals`

| 字段 | 类型 | 说明 |
|------|------|------|
| `shot_no` | integer | 炮号 |
| `request_id` | string | 请求唯一 ID |
| `measurement` | string | 测量类型 |
| `source_system` | string | 源系统 |
| `authority_level` | string | 权限级别 |
| `signals` | array[object] | 信号列表 |

**signals 每项字段:**

| 字段 | 类型 | 说明 |
|------|------|------|
| `shot_no` | integer | 炮号 |
| `artifact_id` | string | Artifact ID |
| `data_type` | string | 数据类型 |
| `channel_name` | string | 通道名称 |
| `process_id` | string | 过程标识 |
| `file_path` | string | 源文件路径 |
| `source_role` | string | 源角色（PRIMARY / AUXILIARY） |
| `sample_count` | integer | 采样点数 |
| `declared_sample_count` | integer | 声明采样点数 |
| `sample_interval_seconds` | number | 采样间隔（秒） |
| `start_time` | string (date-time) | 起始时间 |
| `end_time` | string (date-time) | 结束时间 |
| `shot_range` | array[integer, 2] | 有效采样范围 [start_idx, end_idx] |

---

### ecrh.waveform.channel.batch.v1

波形采样批次，将通道数据分块传输。每块最多 4096 个采样点。

**必填字段:** `artifact_id`, `shot_no`, `source_system`, `channel_name`, `process_id`, `sample_rate_hz`, `chunk_index`, `chunk_count`, `chunk_start_index`, `window_start`, `encoding`, `samples`

| 字段 | 类型 | 说明 |
|------|------|------|
| `artifact_id` | string | Artifact 引用 |
| `shot_no` | integer | 炮号 |
| `data_type` | string | 数据类型 |
| `source_system` | string | 源系统 |
| `channel_name` | string | 通道名称 |
| `process_id` | string | 过程标识 |
| `sample_rate_hz` | number | 采样频率（Hz） |
| `chunk_index` | integer | 分块序号（0 起始） |
| `chunk_count` | integer | 总分块数 |
| `chunk_start_index` | integer | 本块起始采样索引 |
| `window_start` | string (date-time) | 本批次时间窗口起始 |
| `window_end` | string (date-time) | 本批次时间窗口结束 |
| `encoding` | string | 编码格式（`raw`） |
| `samples` | array[number] 或 string | 采样数据 |

---

## 死信队列 Schema

### ecrh.pipeline.dlq.v1

消费失败的消息将路由到 DLQ Topic，用于故障排查。

**必填字段:** 全部

| 字段 | 类型 | 说明 |
|------|------|------|
| `failed_topic` | string | 失败的原始 Topic |
| `failed_partition` | integer | 原始分区号 |
| `failed_offset` | integer | 原始偏移量 |
| `error_type` | string | 错误类型 |
| `error_message` | string | 错误描述 |
| `raw_payload_json` | string | 原始消息 JSON 字符串 |
| `retryable` | boolean | 是否可重试 |

---

## 字典 Schema

### ecrh.dict.operation_type.v1

运行类型字典，定义系统中所有运行操作类型。

**必填字段:** `operation_type_code`, `requires_old_new`, `requires_task_code`, `requires_mode_code`, `is_active`

| 字段 | 类型 | 说明 |
|------|------|------|
| `operation_type_code` | string | 类型代码 |
| `display_name_zh` | string | 中文显示名 |
| `operation_group` | string | 分组 |
| `iter_control_function` | string | ITER 控制功能引用 |
| `requires_old_new` | boolean | 是否需要新旧值 |
| `requires_task_code` | boolean | 是否需要任务代码 |
| `requires_mode_code` | boolean | 是否需要模式代码 |
| `is_active` | boolean | 是否生效 |

---

### ecrh.dict.operation_mode.v1

**必填字段:** `operation_mode_code`, `is_active`

| 字段 | 类型 | 说明 |
|------|------|------|
| `operation_mode_code` | string | 模式代码（MANUAL / AUTO / REMOTE） |
| `display_name_zh` | string | 中文显示名 |
| `scope_note` | string | 适用范围说明 |
| `is_active` | boolean | 是否生效 |

---

### ecrh.dict.operation_task.v1

**必填字段:** `operation_task_code`, `is_active`

| 字段 | 类型 | 说明 |
|------|------|------|
| `operation_task_code` | string | 任务代码 |
| `iter_name` | string | ITER 系统名称 |
| `display_name_zh` | string | 中文显示名 |
| `task_group` | string | 任务分组 |
| `equipment_scope` | string | 涉及设备 |
| `allowed_mode_hint` | string | 允许模式提示 |
| `is_active` | boolean | 是否生效 |

---

### ecrh.dict.protection_type.v1

**必填字段:** `protection_type_code`, `is_active`

| 字段 | 类型 | 说明 |
|------|------|------|
| `protection_type_code` | string | 保护类型代码 |
| `iter_name` | string | ITER 系统名称 |
| `display_name_zh` | string | 中文显示名 |
| `protection_group` | string | 保护分组 |
| `equipment_scope` | string | 涉及设备 |
| `detection_mechanism` | string | 检测机制 |
| `default_severity` | string | 默认严重级别 |
| `default_action` | string | 默认动作 |
| `authority_default` | string | 默认权限级别 |
| `is_active` | boolean | 是否生效 |

---

## Topic 与 Schema 映射

| Kafka Topic | Schema | 消费策略 |
|-------------|--------|----------|
| `ecrh.shot.meta` | shot.meta.v1 | 写入 MySQL `shot_meta` 表 |
| `ecrh.tdms.artifact` | tdms.artifact.v1 | 写入 MySQL `tdms_artifact` 表 |
| `ecrh.signal.catalog` | signal.catalog.v1 | 更新 MySQL `signal_source_map` 表 |
| `ecrh.event.operation` | event.operation.v1 | 写入 MySQL `operation_event` 表 |
| `ecrh.event.protection` | event.protection.v1 | 写入 MySQL `protection_event` 表 |
| `ecrh.waveform.ingest.request` | waveform.ingest.request.v1 | 触发 InfluxDB 波形写入 |
| `ecrh.waveform.channel.batch` | waveform.channel.batch.v1 | 批量写入 InfluxDB |
| `ecrh.pipeline.dlq` | pipeline.dlq.v1 | 死信存储，人工排查 |
| `ecrh.dict.operation_type` | dict.operation_type.v1 | 写入 MySQL 字典表 |
| `ecrh.dict.operation_mode` | dict.operation_mode.v1 | 写入 MySQL 字典表 |
| `ecrh.dict.operation_task` | dict.operation_task.v1 | 写入 MySQL 字典表 |
| `ecrh.dict.protection_type` | dict.protection_type.v1 | 写入 MySQL 字典表 |
