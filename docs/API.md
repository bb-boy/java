# REST API 参考

## 概览

| 方法 | 路径 | 控制器 | 说明 |
|------|------|--------|------|
| GET | `/api/shots` | ShotController | 获取炮号列表 |
| GET | `/api/events/operation` | EventController | 查询运行事件 |
| GET | `/api/events/protection` | EventController | 查询保护事件 |
| POST | `/api/ingest/shot` | IngestController | 触发炮号数据入库 |
| GET | `/api/waveform` | WaveformController | 查询波形数据 |
| GET | `/api/waveform/channels` | WaveformController | 查询可用通道 |

基础 URL: `http://localhost:8080`

---

## 1. 炮号列表

### GET /api/shots

返回所有已入库的炮号，按炮号降序排列。

**参数:** 无

**响应示例:**

```json
[
  {
    "shotNo": 176,
    "shotStartTime": "2024-12-01T10:00:00Z",
    "shotEndTime": "2024-12-01T10:05:00Z",
    "statusCode": "SUCCESS",
    "actualDuration": 300.0
  },
  {
    "shotNo": 6,
    "shotStartTime": "2024-11-15T08:30:00Z",
    "shotEndTime": "2024-11-15T08:32:00Z",
    "statusCode": "SUCCESS",
    "actualDuration": 120.0
  }
]
```

---

## 2. 运行事件查询

### GET /api/events/operation

查询指定炮号的运行事件（设定值阶跃、功率爬坡等）。

**参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `shotNo` | Integer | 是 | 炮号 |
| `start` | String | 否 | 起始时间（ISO-8601） |
| `end` | String | 否 | 结束时间（ISO-8601） |
| `severity` | String | 否 | 严重级别过滤 |
| `processId` | String | 否 | 过程 ID 过滤 |
| `eventCode` | String | 否 | 事件代码过滤 |

**时间格式:** 支持 `yyyy-MM-dd HH:mm:ss.SSS` 和 `yyyy-MM-dd HH:mm:ss`。

**约束:** 若同时提供 `start` 和 `end`，`end` 必须晚于 `start`。

**响应示例:**

```json
[
  {
    "shotNo": 176,
    "eventFamily": "OPERATION",
    "eventCode": "OP_SETPOINT_STEP",
    "eventTime": "2024-12-01T10:01:23.456Z",
    "channelName": "NegVoltage",
    "processId": "ECRH:HVPS:NEG_VOLT",
    "severity": "WARN",
    "messageText": "NegVoltage step detected: 0.50 → 1.20",
    "details": {
      "operation_type_code": "SETPOINT_CHANGE",
      "old_value": 0.50,
      "new_value": 1.20,
      "delta_value": 0.70,
      "confidence": 0.95
    }
  }
]
```

**错误响应:**

```json
{
  "error": "shotNo is required"
}
```

---

## 3. 保护事件查询

### GET /api/events/protection

查询指定炮号的保护事件（异常峰值、快速跌落、冷却异常等）。

**参数:** 与 `/api/events/operation` 相同。

**响应示例:**

```json
[
  {
    "shotNo": 176,
    "eventFamily": "PROTECTION",
    "eventCode": "PRX_PEAK_ABS_HIGH",
    "eventTime": "2024-12-01T10:02:15.789Z",
    "channelName": "InPower",
    "processId": "ECRH:RF:IN_POWER",
    "severity": "TRIP",
    "messageText": "InPower peak absolute value exceeded threshold",
    "details": {
      "protection_type_code": "PEAK_HIGH",
      "protection_scope": "RF",
      "measured_value": 15.8,
      "threshold_value": 10.0,
      "threshold_op": ">",
      "action_taken": "TRIP"
    }
  }
]
```

---

## 4. 数据入库

### POST /api/ingest/shot

触发指定炮号的数据入库流程。系统从 `data/derived/{shotNo}/` 读取派生文件并发送到 Kafka。

**参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `shotNo` | Integer (Query) | 是 | 炮号 |

**请求示例:**

```bash
curl -X POST "http://localhost:8080/api/ingest/shot?shotNo=176"
```

**响应:**

```json
{
  "status": "accepted",
  "shotNo": "176"
}
```

---

## 5. 波形数据查询

### GET /api/waveform

从 InfluxDB 查询指定炮号和通道的波形时序数据。

**参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `shotNo` | Integer | 是 | 炮号 |
| `channelName` | String | 是 | 通道名称 |
| `dataType` | String | 否 | 数据类型（Tube / Water）|
| `start` | String | 是 | 起始时间（带时区偏移的 ISO-8601）|
| `end` | String | 是 | 结束时间（带时区偏移的 ISO-8601）|
| `maxPoints` | Integer | 否 | 最大采样点数 |

**时间格式要求:** 必须包含时区偏移，例如 `2024-12-01T10:00:00+08:00`。不含时区偏移会返回错误。

**响应结构 `WaveformSeries`:**

```json
{
  "shotNo": 176,
  "channelName": "InPower",
  "dataType": "Tube",
  "points": [
    { "time": "2024-12-01T10:00:00.000Z", "value": 1.234 },
    { "time": "2024-12-01T10:00:00.001Z", "value": 1.256 }
  ],
  "totalPoints": 4096,
  "sampleRate": 1000.0
}
```

**错误响应:**

```json
{
  "error": "Timestamp must include timezone offset, e.g. +08:00"
}
```

---

## 6. 可用通道查询

### GET /api/waveform/channels

查询指定炮号在 InfluxDB 中实际存在的通道列表。该接口直接从 InfluxDB 查询，返回真实可用通道（非全局配置表）。

**参数:**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `shotNo` | Integer (Query) | 是 | 炮号 |
| `dataType` | String (Query) | 否 | 数据类型过滤（Tube / Water）|

**请求示例:**

```bash
# 查询炮号 176 的所有通道
curl "http://localhost:8080/api/waveform/channels?shotNo=176"

# 仅查询 Water 类型通道
curl "http://localhost:8080/api/waveform/channels?shotNo=176&dataType=Water"
```

**响应示例:**

```json
[
  { "channelName": "InPower", "dataType": "Tube" },
  { "channelName": "RefPower", "dataType": "Tube" },
  { "channelName": "NegVoltage", "dataType": "Tube" },
  { "channelName": "传输线配水测温1-1", "dataType": "Water" },
  { "channelName": "收集极回水温1-1", "dataType": "Water" }
]
```

---

## 通用错误格式

所有接口在参数校验失败时返回统一的错误格式：

```json
{
  "error": "错误描述"
}
```

HTTP 状态码：

| 状态码 | 含义 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 500 | 服务端异常 |
