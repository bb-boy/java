# 📡 REST API 参考

## 🧭 基础信息

- 🌐 基础 URL：`http://localhost:8080`
- 📦 返回格式：JSON
- ⚠️ 错误格式：`{ "error": "..." }`

## 📐 通用规则

- 🔍 查询类接口使用 `GET`，导入使用 `POST`
- 🕒 事件查询：`start`/`end` 必须成对出现，格式支持 `yyyy-MM-dd HH:mm:ss.SSS`、`yyyy-MM-dd HH:mm:ss`，或带 `T` 的等价形式
- ⏱️ 波形查询：`start`/`end` 必须带时区偏移（例如 `2024-12-01T10:00:00+08:00`）
- 🧾 `shotNo` 为整数，必填时未提供会返回 400

## 🧾 接口清单

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/shots` | 获取炮号列表 |
| GET | `/api/events/operation` | 查询运行事件 |
| GET | `/api/events/protection` | 查询保护事件 |
| POST | `/api/ingest/shot` | 触发炮号数据入库 |
| GET | `/api/waveform` | 查询波形数据 |
| GET | `/api/waveform/channels` | 查询可用通道 |

---

## 1. 🎯 炮号列表

### GET /api/shots

返回所有已入库的炮号，按炮号降序排列。

参数：无

响应示例：

```json
[
  {
    "shotNo": 176,
    "shotStartTime": "2024-12-01T10:00:00Z",
    "shotEndTime": "2024-12-01T10:05:00Z",
    "statusCode": "SUCCESS",
    "actualDuration": 300.0
  }
]
```

---

## 2. 🧪 运行事件查询

### GET /api/events/operation

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `shotNo` | Integer | 是 | 炮号 |
| `start` | String | 否 | 起始时间 |
| `end` | String | 否 | 结束时间 |
| `severity` | String | 否 | 严重级别过滤 |
| `processId` | String | 否 | 过程 ID 过滤 |
| `eventCode` | String | 否 | 事件代码过滤 |

约束：

- 🔗 `start` 和 `end` 必须成对提供
- ⏭️ `end` 必须晚于 `start`

响应示例：

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

错误示例：

```json
{ "error": "shotNo is required" }
```

---

## 3. 🛡️ 保护事件查询

### GET /api/events/protection

参数：与 `/api/events/operation` 相同。

响应示例：

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

## 4. 📥 数据入库

### POST /api/ingest/shot

触发指定炮号的数据入库流程，读取 `data/derived/{shotNo}/` 并发布到 Kafka。

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `shotNo` | Integer (Query) | 是 | 炮号 |

请求示例：

```bash
curl -X POST "http://localhost:8080/api/ingest/shot?shotNo=176"
```

响应示例：

```json
{ "status": "accepted", "shotNo": "176" }
```

---

## 5. 🌊 波形数据查询

### GET /api/waveform

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `shotNo` | Integer | 是 | 炮号 |
| `channelName` | String | 是 | 通道名称 |
| `dataType` | String | 否 | 数据类型（Tube / Water）|
| `start` | String | 是 | 起始时间（含时区偏移） |
| `end` | String | 是 | 结束时间（含时区偏移） |
| `maxPoints` | Integer | 否 | 最大采样点数 |

时间格式要求：必须包含时区偏移，否则返回 400。

响应示例：

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

错误示例：

```json
{ "error": "start must include timezone offset" }
```

---

## 6. 🧭 可用通道查询

### GET /api/waveform/channels

参数：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `shotNo` | Integer (Query) | 是 | 炮号 |
| `dataType` | String (Query) | 否 | 数据类型过滤（Tube / Water）|

返回值说明：

- 🏷️ `dataType` 字段为入参回显（不提供时为空字符串）

请求示例：

```bash
curl "http://localhost:8080/api/waveform/channels?shotNo=176"
curl "http://localhost:8080/api/waveform/channels?shotNo=176&dataType=Water"
```

响应示例：

```json
[
  { "channelName": "InPower", "dataType": "" },
  { "channelName": "RefPower", "dataType": "" }
]
```

---

## ⚠️ 通用错误格式

```json
{ "error": "错误描述" }
```

HTTP 状态码：

| 状态码 | 含义 |
|--------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 500 | 服务端异常 |
