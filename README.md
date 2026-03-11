# Kafka‑First TDMS Pipeline

## 摘要
本项目将 TDMS 文件派生为结构化消息，经 Kafka 投影到 MySQL（元数据/事件/目录）与 InfluxDB（波形）。核心目标是“数据必须先过 Kafka”，确保一致性与可追溯。

## 你会得到什么
- TDMS 到 Kafka 的派生链路（`tools/tdms_preprocessor.py`）
- MySQL 投影表（元数据/事件/目录/字典）
- InfluxDB 波形写入与查询
- 可直接使用的 REST API（事件、波形、炮号）

## 系统流程（概览）
```
TDMS 文件
 -> tools/tdms_preprocessor.py 派生（落盘到 data/derived）
 -> Kafka（所有对象都先进入 Kafka）
 -> MySQL 投影消费者（元数据/事件/字典/目录）
 -> InfluxDB 写入消费者（波形 batch）
 -> 前端查询 MySQL + 回放 Influx 波形
```

## API 速查
- `POST /api/ingest/shot?shotNo=1`
  - 读取派生文件并发送到 Kafka
- `GET /api/events/operation`
  - 查询操作事件
  - 必填：`shotNo`；可选：`start/end`、`severity`、`processId`、`eventCode`
- `GET /api/events/protection`
  - 查询保护事件（参数同上）
- `GET /api/shots`
  - 查询已入库炮号列表
- `GET /api/waveform`
  - 查询波形
  - 必填：`shotNo`、`channelName`、`start`、`end`（带时区）
  - 可选：`dataType`、`maxPoints`
- `GET /api/waveform/channels`
  - 查询某炮号可用通道
  - 参数：`shotNo`，可选 `dataType`

## 快速启动
1. 启动基础服务（Kafka / MySQL / InfluxDB）：
   ```bash
   (cd docker && docker compose up -d)
   ```
2. 初始化数据库：
   ```bash
   mysql -uroot -pdevroot < sql/ecrh_schema.sql
   ```
3. 启动应用：
   ```bash
   ./mvnw spring-boot:run
   ```
4. 预处理 TDMS：
   ```bash
   python3 tools/tdms_preprocessor.py --shot 1 --input-root data/TUBE --output-root data/derived
   ```
5. 触发导入：
   ```bash
   curl -X POST "http://localhost:8080/api/ingest/shot?shotNo=1"
   ```

## 自动派生
持续监听目录并派生：
```bash
python3 tools/tdms_auto_derive.py \
  --watch-root /data/tube \
  --output-root /data/derived
```
如需单次派生可加 `--once`。

## 关键配置（`application.yml`）
```yaml
app:
  derivation:
    script: tools/tdms_preprocessor.py
    input-root: data/TUBE
    output-root: data/derived
  influxdb:
    enabled: true
    url: http://localhost:8086
```

## 数据库与存储
- MySQL：`tdms_artifacts`、`source_records`、`shots`、`events`、事件明细表、信号目录与映射表
- InfluxDB：`waveform` measurement（按 `shot_no/channel_name/data_type` 等维度）

## 目录结构
- `src/main/java/com/example/kafka/`：后端核心
- `tools/`：TDMS 解析与派生
- `data/`：原始 TDMS 与派生输出
- `schemas/kafka/`：Kafka Schema
- `sql/`：数据库 Schema
