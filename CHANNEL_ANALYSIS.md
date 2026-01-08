# 通道数据分析报告

**问题**: 测试发现 API 返回的通道列表不完整  
**发现时间**: 2026-01-04  
**分析工具**: Python nptdms库 + 自定义提取脚本  

---

## 🔍 问题描述

API 端点 `GET /api/data/shots/{shotNo}/channels` 只返回 **4 个硬编码通道**：
```json
["PosVoltage", "NegVoltage", "Current", "Pressure"]
```

但实际 TDMS 文件中包含的通道数据：
- **Tube文件**: 最多 **14 个通道** ❌
- **Water文件**: **32 个温度传感器通道** ❌

---

## 📊 真实数据统计

### Tube文件通道概览

| 通道名 | 功能 | 数据点 |
|-------|------|--------|
| InPower | 输入功率 | 10400 |
| RefPower | 参考功率 | 10400 |
| **NegVoltage** | 负电压 ✅ | 10400 |
| NegCurrent | 负电流 | 10400 |
| **PosVoltage** | 正电压 ✅ | 10400 |
| PosCurrent | 正电流 | 10400 |
| FilaVoltage | 灯丝电压 | 10400 |
| FilaCurrent | 灯丝电流 | 10400 |
| TiPumpCurrent | 钛泵电流 | 10400 |

**当前返回**: 4个  
**实际应有**: 9-14个  
**缺失率**: 50-75%  

### Water文件通道概览

**32个温度传感器通道** (所有炮号):
```
1. 140阳极T1进          17. 140俄大负载A T3回
2. 140阳极T1回          18. 140俄大负载M T4回
3. 140主窗口T2回        19-24. 140滤波器/弯头/TAPER/金刚石窗
4. 140收集极T5进        25-28. 140波导/光栅(1-3)
5. 140收集极回          29-31. 140负载(1-3)
6. 140镜子T6回          32. 140备用T30
7. 140管体T7回
8. 140MOU T8回
... (更多温度传感器)
```

**当前返回**: 0个  
**实际应有**: 32个  
**缺失率**: 100%  

---

## 🔧 根本原因

在 `FileDataSource.java` 中：

```java
@Override
public List<String> getChannelNames(Integer shotNo, String dataType) {
    // TODO: 实现从TDMS文件获取通道名称
    // 临时返回常见通道
    return Arrays.asList("PosVoltage", "NegVoltage", "Current", "Pressure");
}
```

**问题**:
1. Java 无法直接读取 TDMS 文件（NI LabVIEW 专有格式）
2. 使用了硬编码的占位实现
3. 完全忽略了 Water 文件的 32 个通道

---

## ✅ 解决方案

### 方案实施：自动通道提取工具

已创建 `extract_channels.py` 脚本，用于：
1. 读取所有 TDMS 文件
2. 提取所有通道的完整信息
3. 生成 JSON 格式的通道元数据
4. 供 Java 应用加载和返回

**使用方式**:
```bash
# 提取单个炮号
python extract_channels.py --shot 1

# 提取多个炮号
python extract_channels.py --shots 1 2 3 10

# 提取所有炮号
python extract_channels.py --all
```

**输出示例** (`channels/channels_1.json`):

> 说明：系统现在支持两种通道元数据来源（优先级：1. DB channels 表 2. channels JSON 文件 3. 默认通道列表）。
> - `DataConsumer` 会在消费 `wave-data` 时把通道信息写入 `channels` 表（shotNo+channelName+dataType 唯一），作为长期查询来源。
> - `extract_channels.py` 依旧用于快速生成 JSON 作为补丁或离线工具。

```json
{
  "shotNo": 1,
  "extractTime": "2026-01-04T...",
  "dataTypes": {
    "Tube": {
      "count": 9,
      "channels": [
        {
          "name": "InPower",
          "dataPoints": 10400,
          "dataType": "float64",
          "startTime": "2023-03-29T04:43:39.995073",
          "sampleInterval": 0.001,
          "unit": "Volts"
        },
        ...
      ]
    },
    "Water": {
      "count": 32,
      "channels": [...]
    }
  }
}
```

---

## 📈 通道分布统计

### 所有 20 个炮号的通道数量

```
炮号    1: Tube= 9, Water=32  ✅
炮号    2: Tube= 9, Water=32  ✅
炮号    3: Tube=14, Water=32  ✅ (多5个通道)
炮号    4: Tube=14, Water=32  ✅ (多5个通道)
炮号    5: Tube= 9, Water=32  ✅
...
炮号 1001: Tube= 9, Water=32  ✅
```

**统计**:
- 大部分炮号: 9个Tube通道 + 32个Water通道 = **41个通道**
- 部分炮号(3, 4): 14个Tube通道 + 32个Water通道 = **46个通道**
- **当前实现**: 仅返回 4 个通道 ❌
- **完整度**: 4/41 = **9.8%** (严重缺失)

---

## 🎯 对应用的影响

### 功能受损
- ❌ 用户无法看到完整的 Tube 通道列表
- ❌ 用户完全看不到 Water 温度传感器数据
- ❌ 前端波形绘制缺少 75-90% 的数据来源

### 用户体验
- ⚠️ 前端下拉菜单只显示 4 个选项，用户不知道有其他数据
- ⚠️ Water 数据完全不可访问
- ⚠️ 实验数据展示不完整

### 数据分析
- ⚠️ 无法进行完整的多通道对比分析
- ⚠️ 缺失温度监测数据（Water 32个温度传感器）
- ⚠️ 缺失电源参数（InPower, RefPower）

---

## 💡 建议修复

### 短期修复 (立即可用)

1. **使用JSON 通道文件**:
   ```bash
   # 一次性运行脚本生成所有通道数据
   python extract_channels.py --all
   
   # Java 应用启动时加载 channels/*.json
   # 返回完整的通道列表
   ```

2. **修改 FileDataSource.getChannelNames()**:
   ```java
   @Override
   public List<String> getChannelNames(Integer shotNo, String dataType) {
       // 从 channels/channels_{shotNo}.json 读取
       // 按 dataType 过滤返回
       String jsonFile = String.format("channels/channels_%d.json", shotNo);
       return loadChannelsFromJson(jsonFile, dataType);
   }
   ```

### 中期改进 (优雅方案)

1. **Python 微服务**:
   ```python
   # 创建 Flask REST API
   # 提供 /api/channels/{shotNo} 端点
   # Java 应用调用此服务获取通道数据
   ```

2. **Kafka 消息队列**:
   ```
   Python → Kafka: channel-info topic
   Java → Kafka: subscribe and consume
   ```

### 长期方案 (标准化)

1. **TDMS → 标准格式转换**:
   ```bash
   # 定期运行 Python 脚本
   # 将 TDMS 转换为 HDF5 / Parquet / NetCDF
   # Java 可以更容易地读取这些格式
   ```

2. **数据库存储**:
   ```sql
   -- 创建 CHANNELS 表
   CREATE TABLE channels (
       id INT PRIMARY KEY,
       shot_no INT,
       data_type VARCHAR(10),  -- 'Tube' or 'Water'
       channel_name VARCHAR(100),
       data_points INT,
       sample_interval DOUBLE,
       unit VARCHAR(50)
   );
   ```

---

## 📋 已生成的文件

| 文件 | 大小 | 说明 |
|-----|------|------|
| `extract_channels.py` | 5KB | 通道提取脚本 |
| `channels/channels_*.json` | 13KB×20 | 各炮号的通道元数据 |
| `CHANNEL_ANALYSIS.md` | 本文件 | 分析报告 |

---

## ✅ 验证清单

- [x] 发现问题根本原因
- [x] 分析实际数据完整度
- [x] 统计所有炮号的通道分布
- [x] 创建自动提取工具
- [x] 验证提取结果正确性
- [x] 生成通道元数据文件
- [ ] 修改 Java 代码集成 JSON 数据
- [ ] 更新 API 返回完整通道列表
- [ ] 测试前端展示所有通道
- [ ] 更新文档说明支持的通道

---

## 🎓 小白知识点

**TDMS 文件结构** (NI LabVIEW 格式):
```
TDMS File
├── Group 1
│   ├── Channel 1: InPower (9000+ 数据点)
│   ├── Channel 2: RefPower (9000+ 数据点)
│   └── ...
└── Group 2: (可能有多个组)
```

**通道数据特性**:
- 每个通道是一个时间序列（时间戳 + 数值）
- 采样频率固定 (本例为 1KHz, 采样间隔 0.001s)
- 数据类型统一为 float64（64位浮点数）
- 包含属性信息（单位、采样率、开始时间等）

---

## 总结

| 指标 | 值 |
|-----|-----|
| 发现的通道缺失 | 37/41 (90%) |
| 缺失的 Water 通道 | 32/32 (100%) |
| 缺失的 Tube 通道 | 5-10/9-14 (50-75%) |
| 提取的通道元数据文件 | 20个 |
| 可用的解决方案 | 3种 |
| 建议实施时间 | 立即 |

**评价**: ⚠️ **高优先级问题** - 影响应用完整功能，建议立即修复。

