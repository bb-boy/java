# Kafka‑First 架构说明

## 摘要
系统以 TDMS 为唯一事实来源，所有入库数据必须先写入 Kafka，再由投影消费者落库/入库，保证一致性与可追溯。

## 总体流程
```
TDMS 文件
 -> tools/tdms_preprocessor.py 派生（落盘到 data/derived）
 -> Kafka（所有对象都先进入 Kafka）
 -> MySQL 投影消费者（元数据/事件/字典/目录）
 -> InfluxDB 写入消费者（波形 batch）
 -> 前端查询 MySQL + 回放 Influx 波形
```

## 关键原则
- TDMS 文件是当前唯一真实输入
- 操作/保护事件先由 TDMS 派生（未来可替换为真实系统）
- 所有入库必须先经过 Kafka
- 波形点先进入 Kafka，再写入 InfluxDB

## Kafka 主题
| 主题 | 说明 | Key |
| --- | --- | --- |
| `ecrh.tdms.artifact.v1` | TDMS 文件资产 | `sha256_hex` |
| `ecrh.shot.meta.v1` | 炮号元数据 | `shot_no` |
| `ecrh.signal.catalog.v1` | 信号目录与映射 | `source_system:source_name` |
| `ecrh.dict.protection_type.v1` | 保护类型字典 | `protection_type_code` |
| `ecrh.dict.operation_mode.v1` | 模式字典 | `operation_mode_code` |
| `ecrh.dict.operation_task.v1` | 任务字典 | `operation_task_code` |
| `ecrh.dict.operation_type.v1` | 操作类型字典 | `operation_type_code` |
| `ecrh.event.operation.v1` | 操作事件 | `shot_no` |
| `ecrh.event.protection.v1` | 保护事件 | `shot_no` |
| `ecrh.waveform.ingest.request.v1` | 波形导入请求 | `sha256_hex` |
| `ecrh.waveform.channel.batch.v1` | 波形批次 | `artifact_id:channel_name` |
| `ecrh.pipeline.dlq.v1` | 失败事件 | 原消息 key |

## MySQL 投影结构（核心表）
- `tdms_artifacts`：文件资产（SHA256 幂等）
- `source_records`：Kafka 消费收据（topic/partition/offset 幂等）
- 字典表：`protection_type_dict`, `operation_mode_dict`, `operation_task_dict`, `operation_type_dict`
- `shots`：炮号元数据
- `events`：统一事件总表
- `event_operation_detail` / `event_protection_detail`：事件明细
- `signal_catalog` / `signal_source_map`：信号目录与映射

## InfluxDB
- Measurement: `waveform`
- Tags: `shot_no`, `channel_name`, `data_type`, `source_system`, `process_id`, `artifact_id`
- Fields: `value`, `sample_index`

## API 与职责
- `/api/ingest/shot`：读取派生文件 → Kafka
- `/api/events/*`：查询 MySQL 事件投影
- `/api/shots`：查询炮号元数据
- `/api/waveform/*`：查询 Influx 波形
