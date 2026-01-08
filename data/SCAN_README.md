# TDMS 扫描和操作检测工具

## 概述

`scan.py` 是一个用于批量处理 TDMS 文件、检测参数变化（阶跃/斜坡）的 Python 脚本。所有可配置参数均已提取到独立的 JSON 配置文件中，便于调整和维护。

## 文件结构

```
data/
├── scan.py                  # 主扫描脚本
├── scan_config.json         # 配置文件
├── SCAN_README.md           # 本文档
├── TUBE/                    # TDMS 文件存储目录
│   ├── 1/
│   │   ├── 1_Tube.tdms
│   │   └── 1_Water.tdms
│   ├── 2/
│   └── ...
└── TUBE_logs/               # 操作日志输出目录（自动生成）
    ├── 1/
    │   ├── 1_Tube_operation_log.txt
    │   └── 1_Water_operation_log.txt
    ├── 2/
    └── ALL_INDEX.txt        # 批处理摘要索引
```

## 配置文件详解

`scan_config.json` 包含以下可配置参数：

### paths 配置

```json
{
  "paths": {
    "tdms_root_dir": "TUBE",           // TDMS 文件根目录（相对路径）
    "output_dir": "TUBE_logs",         // 操作日志输出目录（相对路径）
    "only_keyword": "",                // 文件名过滤关键字（空表示无过滤）
    "iplen_unit": "auto"               // LpLen 单位：auto/samples/seconds
  }
}
```

### detection_params 配置

#### step（阶跃检测）

```json
{
  "detection_params": {
    "step": {
      "smooth_window": 21,             // 平滑窗口大小
      "level_window": 200,             // 水平线提取窗口
      "prepost_window": 100,           // 前后对比窗口
      "quantile": 0.99,                // 分位数阈值（0-1）
      "merge_gap": 40,                 // 事件合并间隔
      "min_delta_abs": 0.0,            // 最小绝对变化量
      "min_sigma": 1.5                 // 最小 sigma 倍数
    }
  }
}
```

#### ramp（斜坡检测）

```json
{
  "detection_params": {
    "ramp": {
      "smooth_window": 21,             // 平滑窗口
      "slope_window": 250,             // 斜率计算窗口
      "quantile": 0.98,                // 分位数阈值
      "merge_gap": 120,                // 事件合并间隔
      "min_duration_ms": 150,          // 最小持续时间（毫秒）
      "min_delta_abs": 0.0,            // 最小绝对变化量
      "min_sigma": 2.0,                // 最小 sigma 倍数
      "dt_seconds": 0.001              // 采样间隔（秒）
    }
  }
}
```

## 使用方法

### 方式 1：使用配置文件（推荐）

在 `data/` 目录下运行：

```bash
cd /home/igusa/java/data
python3 scan.py
```

脚本会自动加载同级目录的 `scan_config.json` 配置文件。

**输出：**
- 每个 TDMS 文件生成 `*_operation_log.txt`
- 生成 `TUBE_logs/ALL_INDEX.txt` 摘要索引

### 方式 2：使用配置文件 + 命令行参数覆盖

命令行参数会覆盖配置文件中的对应设置：

```bash
# 只处理包含 "Tube" 的文件
python3 scan.py --only "Tube"

# 自定义输出目录
python3 scan.py --out custom_output

# 自定义配置文件位置
python3 scan.py --config /path/to/custom_config.json

# 修改 IpLen 单位
python3 scan.py --iplen-unit samples
```

### 方式 3：命令行方式（完全不依赖配置文件）

```bash
python3 scan.py --root TUBE --out TUBE_logs --only "Tube"
```

## 快速示例

### 示例 1：处理所有 Tube 文件

```bash
python3 scan.py --only "Tube"
```

### 示例 2：处理特定炮号

```bash
python3 scan.py --root TUBE --out TUBE_logs --only "1_Tube"
```

### 示例 3：调整检测参数

修改 `scan_config.json` 中的 `detection_params` 段，例如提高阶跃检测灵敏度：

```json
{
  "detection_params": {
    "step": {
      "min_sigma": 1.0,    // 降低此值以检测更微弱的变化
      ...
    }
  }
}
```

## 输出文件说明

### 操作日志文件（如 `1_Tube_operation_log.txt`）

```
=== TDMS Operation Log ===
File      : TUBE/1/1_Tube.tdms
Name      : 1_Tube
ShotNo    : 1
...
--- Operations (absolute timestamps) ---
[2023-03-29 04:43:41.268] 操作(调参) 阴极电压(NegVoltage) step 旧=0.0258502 新=0.0276476 Δ=+0.0018 置信度=4.41σ
```

**字段说明：**
- `mode`: `step`（阶跃） 或 `ramp`（斜坡）
- `旧/新值`: 变化前后的参数值
- `Δ`: 变化量（带符号）
- `置信度`: 变化幅度相对于噪声的倍数（σ）

### 摘要索引（`ALL_INDEX.txt`）

```
=== TDMS Batch Index ===
[OK] ShotNo=1 | 正常完成 | actual=10.400s expected=10.000s | TUBE_logs/1/1_Tube_operation_log.txt
[OK] ShotNo=2 | 正常完成 | actual=5.400s expected=5.000s | TUBE_logs/2/2_Tube_operation_log.txt
```

## 修改建议

### 调整检测灵敏度

1. **更敏感的检测**：
   - 降低 `min_sigma`（例如 1.0 代替 1.5）
   - 增加 `quantile`（例如 0.995 代替 0.99）

2. **更稳定的检测**：
   - 提高 `min_sigma`（例如 2.0 代替 1.5）
   - 减小 `quantile`（例如 0.95 代替 0.99）

### 自定义参数通道

修改 `infer_operations()` 函数中的检测调用，例如添加新通道的阶跃检测：

```python
ops += detect_steps("新通道", t_abs, new_channel_data, step_cfg, min_delta_abs=0.001)
```

## 故障排除

| 问题 | 原因 | 解决方案 |
|------|------|---------|
| 找不到配置文件 | 配置文件路径错误 | 确保 `scan_config.json` 与脚本在同一目录 |
| 检测到太多操作事件 | 检测参数过敏感 | 提高 `min_sigma`，减小 `quantile` |
| 检测不到预期的操作 | 检测参数不够敏感 | 降低 `min_sigma`，增加 `quantile` |
| 缺少必要的通道 | TDMS 文件不完整 | 检查 TDMS 文件是否包含 NegVoltage, PosVoltage 等必要通道 |

## 技术细节

### 支持的通道

- `NegVoltage`（阴极电压）
- `PosVoltage`（阳极电压）
- `FilaCurrent`（灯丝电流）
- `FilaVoltage`（灯丝电压）

### 算法概述

1. **阶跃检测**：平滑数据 → 提取水平线 → 比较前后 → 检测突跃
2. **斜坡检测**：平滑数据 → 计算斜率 → 寻找高斜率区间 → 检测持续变化

### 性能

- 单个 TDMS 文件处理时间：~1-2 秒（取决于采样率和数据量）
- 全批处理时间（20 炮）：~30-40 秒

## 更新日志

- **v1.0** (2026-01-08)：初版，配置文件支持，支持命令行参数覆盖
