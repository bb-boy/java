# Python 数据工具

## 概览

Python 工具链负责从 TDMS 原始文件派生结构化数据，包括炮元数据、信号目录、事件检测和波形批次。

| 工具 | 说明 |
|------|------|
| `tools/tdms_preprocessor.py` | 单次派生：解析指定炮号的 TDMS 文件 |
| `tools/tdms_auto_derive.py` | 批量派生：扫描目录自动发现并处理新文件 |
| `tools/validate_derived_output.py` | 验证派生输出完整性和正确性 |
| `data/watch_tdms.py` | 监控守护进程（参见 data/WATCH_README.md） |
| `tools/tdms_derive/` | 核心派生引擎（Python 包） |

### 依赖安装

```bash
pip3 install nptdms numpy watchdog
```

---

## tdms_preprocessor.py

单次派生入口，解析指定炮号的 TDMS 文件并生成全部派生输出。

### 用法

```bash
python3 tools/tdms_preprocessor.py --shot <炮号> [选项]
```

### 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--shot` | （必填） | 炮号 |
| `--input-root` | `data/TUBE` | TDMS 原始文件根目录 |
| `--output-root` | `data/derived` | 派生输出目录 |
| `--stability-checks` | `2` | 文件稳定性检查次数 |
| `--stability-wait-ms` | `100` | 检查间隔（毫秒） |

### 输出

在 `{output-root}/{shot}/` 下生成 7 个文件：

| 文件 | 格式 | 说明 |
|------|------|------|
| `artifact.json` | JSON | TDMS 文件元数据和校验信息 |
| `shot_meta.json` | JSON | 炮时间线、状态、参考通道 |
| `signal_catalog.jsonl` | JSONL | 每个通道一行，含统计信息 |
| `operation_events.jsonl` | JSONL | 检测到的运行事件 |
| `protection_events.jsonl` | JSONL | 检测到的保护事件 |
| `waveform_ingest_request.json` | JSON | 波形入库请求元数据 |
| `waveform_channel_batch.jsonl` | JSONL | 波形采样批次（每批 ≤ 4096 点） |

### 示例

```bash
# 派生炮号 176
python3 tools/tdms_preprocessor.py --shot 176

# 指定输入输出目录
python3 tools/tdms_preprocessor.py --shot 176 \
  --input-root /data/tube --output-root /data/derived
```

---

## tdms_auto_derive.py

批量自动派生工具，周期性扫描目录发现新 TDMS 文件。

### 用法

```bash
python3 tools/tdms_auto_derive.py [选项]
```

### 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--watch-root` | `/data/tube` | TDMS 根目录 |
| `--output-root` | `/data/derived` | 派生输出目录 |
| `--file-pattern` | `.*_Tube\.tdms$` | 文件名匹配正则 |
| `--scan-interval-ms` | `2000` | 扫描周期（毫秒） |
| `--stability-checks` | `2` | 文件稳定性检查次数 |
| `--stability-wait-ms` | `100` | 检查间隔（毫秒） |
| `--sync-url` | （无） | 派生后调用的入库 API 地址 |
| `--once` | 关闭 | 单次扫描后退出 |

### 工作流

```
scan_once()
   │
   ├── discover_shots(): 扫描 .tdms 文件，提取炮号
   │
   ├── 检查 {output-root}/{shot_no}/ 是否已存在
   │
   ├── derive_shot(): 调用 run_preprocessor() 派生
   │
   └── sync_shot(): POST {sync-url}?shotNo={shot_no}（可选）
```

持续模式下，`scan_loop()` 按 `scan-interval-ms` 周期重复调用 `scan_once()`。

### 示例

```bash
# 一次性扫描并入库
python3 tools/tdms_auto_derive.py \
  --watch-root data/TUBE \
  --output-root data/derived \
  --sync-url http://localhost:8080/api/ingest/shot \
  --once

# 持续轮询
python3 tools/tdms_auto_derive.py \
  --watch-root data/TUBE \
  --output-root data/derived \
  --sync-url http://localhost:8080/api/ingest/shot \
  --scan-interval-ms 5000
```

---

## validate_derived_output.py

验证派生输出文件的完整性和数据正确性。

### 用法

```bash
python3 tools/validate_derived_output.py --shot <炮号> [--output-root data/derived]
```

### 参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--shot` | （必填） | 炮号 |
| `--output-root` | `data/derived` | 派生输出目录 |

### 校验内容

| 文件 | 校验项 |
|------|--------|
| `artifact.json` | shot_no 正确，至少一个 PRIMARY artifact |
| `shot_meta.json` | shot_no、source_system、authority_level、window_source 存在 |
| `signal_catalog.jsonl` | source_system、authority_level 标记正确 |
| `operation_events.jsonl` | event_family = OPERATION，标记正确 |
| `protection_events.jsonl` | event_family = PROTECTION，标记正确 |
| `waveform_ingest_request.json` | shot_no、source_system、signals 数组非空 |
| `waveform_channel_batch.jsonl` | shot_no、encoding=raw、samples 非空 |

成功时输出 `validation-ok`，失败时抛出异常并显示具体错误。

### 示例

```bash
# 验证炮号 176 的输出
python3 tools/validate_derived_output.py --shot 176

# 验证所有已派生的炮号
for d in data/derived/*/; do
  shot=$(basename "$d")
  echo "验证 shot=$shot"
  python3 tools/validate_derived_output.py --shot "$shot"
done
```

---

## tdms_derive 核心包

`tools/tdms_derive/` 是派生引擎的核心实现，所有工具脚本调用此包的 `run_preprocessor()` 函数。

### 模块结构

| 模块 | 职责 |
|------|------|
| `pipeline.py` | 主编排：加载 TDMS → 统计计算 → 事件检测 → 输出 |
| `parser.py` | TDMS 文件解析：读取通道、提取元数据、稳定性检查 |
| `models.py` | 数据模型（不可变 dataclass）：ArtifactRecord、ChannelSeries、ShotMeta 等 |
| `constants.py` | 配置常量：事件检测参数、通道映射、阈值 |
| `shot.py` | 炮元数据构建：活跃窗口检测、时间线计算 |
| `operations.py` | 运行事件检测：阶跃（STEP）和爬坡（RAMP）算法 |
| `protection.py` | 保护事件检测：异常峰值、跌落、冷却异常 |
| `protection_common.py` | 保护检测通用工具函数 |
| `waveform.py` | 波形批次生成：分块（4096 点/块）和编码 |
| `output.py` | 文件输出：JSON/JSONL 序列化和写入 |
| `naming.py` | 命名规则：Artifact ID、文件路径生成 |
| `utils.py` | 通用工具：平滑滤波、统计计算 |
| `random_fields.py` | 随机字段生成：运行模式、任务代码等补充字段 |

### 核心数据模型

#### ArtifactRecord

TDMS 文件的元数据记录，包含文件路径、SHA-256 校验和、通道数等。

```python
@dataclass(frozen=True, slots=True)
class ArtifactRecord:
    shot_no: int
    data_type: str          # TUBE / WATER
    artifact_id: str        # 全局唯一 ID
    sha256_hex: str         # SHA-256 校验和
    source_role: str        # PRIMARY / AUXILIARY
    channel_count: int      # 通道数
    ...
```

#### ChannelSeries

单通道波形数据，包含采样值的 NumPy 数组。

```python
@dataclass(frozen=True, slots=True)
class ChannelSeries:
    channel_name: str
    process_id: str          # 如 ECRH:RF:IN_POWER
    sample_interval_seconds: float
    values: np.ndarray       # 采样值数组
    start_time: datetime
    ...

    @property
    def sample_rate_hz(self) -> float:
        return 1.0 / self.sample_interval_seconds
```

#### DerivedEvent

检测到的事件（运行或保护），包含时间、通道、严重程度和详情。

```python
@dataclass(frozen=True, slots=True)
class DerivedEvent:
    event_family: str   # OPERATION / PROTECTION
    event_code: str     # OP_SETPOINT_STEP / PRX_PEAK_ABS_HIGH / ...
    channel_name: str
    severity: str       # TRIP / WARN
    details: dict       # 事件特有数据
    ...
```

### 派生流程

```
run_preprocessor(shot_no, input_root, output_root)
   │
   ├── 1. load_artifacts(): 读取 TDMS 文件
   │       ├── {shot_no}_Tube.tdms（必须）
   │       └── {shot_no}_Water.tdms（可选）
   │
   ├── 2. 提取通道，计算 SignalStats（基线、噪声、峰值、RMS）
   │
   ├── 3. 确定活跃时间窗口（基于参考通道）
   │       参考通道: NegVoltage, PosVoltage, InPower
   │
   ├── 4. 构建 ShotMeta（时间线、状态、质量标记）
   │
   ├── 5. 构建 Signal Catalog（通道元数据 + 统计）
   │
   ├── 6. 检测运行事件
   │       ├── STEP: 平滑 → 水平分割 → 阶跃检测 → 合并
   │       └── RAMP: 平滑 → 斜率计算 → 爬坡区间检测 → 合并
   │
   ├── 7. 检测保护事件
   │       ├── NO_WAVE: 信号幅度低于阈值
   │       ├── PEAK_HIGH: 绝对峰值超阈值（σ > 10）
   │       ├── DROPOUT: 快速跌落检测
   │       ├── COOLING: 冷却异常（基线漂移）
   │       └── EARLY_TERMINATION: 脉冲提前终止
   │
   ├── 8. 构建波形入库请求和批次（4096 点/块）
   │
   └── 9. 写入 7 个输出文件 → 返回 DerivedBundle
```

### 通道 → Process ID 映射

| TDMS 通道名 | Process ID | 说明 |
|-------------|------------|------|
| InPower | `ECRH:RF:IN_POWER` | 入射功率 |
| RefPower | `ECRH:RF:REF_POWER` | 反射功率 |
| NegVoltage | `ECRH:HVPS:NEG_VOLT` | 负高压 |
| NegCurrent | `ECRH:HVPS:NEG_CURRENT` | 负高压电流 |
| PosVoltage | `ECRH:HVPS:POS_VOLT` | 正高压 |
| PosCurrent | `ECRH:HVPS:POS_CURRENT` | 正高压电流 |
| FilaVoltage | `ECRH:GYROTRON:FILA_VOLT` | 灯丝电压 |
| FilaCurrent | `ECRH:GYROTRON:FILA_CURRENT` | 灯丝电流 |
| TiPumpCurrent | `ECRH:VACUUM:TI_PUMP_CURRENT` | 钛泵电流 |

### 事件检测参数

#### 阶跃检测 (STEP)

| 参数 | 值 | 说明 |
|------|-----|------|
| smooth_window | 21 | 平滑窗口大小 |
| level_window | 200 | 水平分割窗口 |
| prepost_window | 100 | 阶跃前后比较窗口 |
| merge_gap | 40 | 合并间距（采样点） |
| quantile | 0.99 | 分位数阈值 |
| sigma_factor | 1.5 | 噪声倍数系数 |

通道最小变化量要求：NegVoltage/PosVoltage=0.001，FilaVoltage/FilaCurrent=0.003，InPower=0.03，RefPower=0.02。

#### 爬坡检测 (RAMP)

| 参数 | 值 | 说明 |
|------|-----|------|
| smooth_window | 21 | 平滑窗口大小 |
| slope_window | 250 | 斜率计算窗口 |
| merge_gap | 120 | 合并间距 |
| min_duration_ms | 150 | 最短爬坡时长 |
| sigma_factor | 2.0 | 噪声倍数系数 |

#### 保护检测阈值

| 类型 | 关键参数 |
|------|----------|
| NO_WAVE | min_signal_level=0.05, min_dynamic_range=0.02 |
| PEAK_HIGH | sigma_threshold=10.0, filter_window=9 |
| DROPOUT | high_ratio=0.85, low_ratio=0.25, window_ms=100 |
| COOLING | sigma_factor=6.0, min_delta=0.04, baseline_window_ms=500 |
| EARLY_TERMINATION | tolerance_ratio=0.02, tolerance_seconds=0.05 |
