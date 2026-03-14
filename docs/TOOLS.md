# 🧰 Python 数据工具

## 🧭 概览

Python 工具链负责从 TDMS 原始文件派生结构化数据，包括炮元数据、信号目录、事件检测和波形批次。

| 工具 | 说明 |
|------|------|
| `tools/tdms_preprocessor.py` | 单次派生：解析指定炮号 |
| `tools/tdms_auto_derive.py` | 批量派生：轮询扫描目录 |
| `tools/validate_derived_output.py` | 派生输出校验 |
| `data/watch_tdms.py` | 文件监控与补扫（见 data/WATCH_README.md） |
| `tools/tdms_derive/` | 核心派生引擎 |

🧪 依赖安装：

```bash
pip3 install nptdms numpy watchdog
```

## 📦 输出结构

派生输出位于 `{output-root}/{shot}/`：

| 文件 | 格式 | 说明 |
|------|------|------|
| `artifact.json` | JSON | TDMS 文件元数据与校验信息 |
| `shot_meta.json` | JSON | 炮时间线、状态、参考通道 |
| `signal_catalog.jsonl` | JSONL | 信号目录与统计信息 |
| `operation_events.jsonl` | JSONL | 运行事件 |
| `protection_events.jsonl` | JSONL | 保护事件 |
| `waveform_ingest_request.json` | JSON | 波形入库请求 |
| `waveform_channel_batch.jsonl` | JSONL | 波形采样批次（每批 ≤ 4096 点） |

## 🧪 tdms_preprocessor.py

单次派生入口，解析指定炮号的 TDMS 文件并生成全部派生输出。

```bash
python3 tools/tdms_preprocessor.py --shot <炮号> [选项]
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--shot` | 必填 | 炮号 |
| `--input-root` | `data/TUBE` | TDMS 根目录 |
| `--output-root` | `data/derived` | 派生输出目录 |
| `--stability-checks` | `2` | 文件稳定性检查次数 |
| `--stability-wait-ms` | `100` | 检查间隔（毫秒） |

示例：

```bash
python3 tools/tdms_preprocessor.py --shot 176
python3 tools/tdms_preprocessor.py --shot 176 --input-root /data/tube --output-root /data/derived
```

## 🔁 tdms_auto_derive.py

批量自动派生工具，周期性扫描目录发现新 TDMS 文件。

```bash
python3 tools/tdms_auto_derive.py [选项]
```

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--watch-root` | `/data/tube` | TDMS 根目录 |
| `--output-root` | `/data/derived` | 派生输出目录 |
| `--file-pattern` | `.*_Tube\.tdms$` | 文件名正则 |
| `--scan-interval-ms` | `2000` | 扫描周期（毫秒） |
| `--stability-checks` | `2` | 文件稳定性检查次数 |
| `--stability-wait-ms` | `100` | 检查间隔（毫秒） |
| `--sync-url` | 无 | 派生后调用的入库 API |
| `--once` | 关闭 | 单次扫描后退出 |

🧭 工作流：

```
scan_once()
  → discover_shots()
  → derive_shot() 或跳过已存在目录
  → sync_shot()（可选）
```

示例：

```bash
python3 tools/tdms_auto_derive.py \
  --watch-root data/TUBE \
  --output-root data/derived \
  --sync-url http://localhost:8080/api/ingest/shot \
  --once
```

## ✅ validate_derived_output.py

验证派生输出的完整性与关键字段。

```bash
python3 tools/validate_derived_output.py --shot <炮号> [--output-root data/derived]
```

| 文件 | 校验项 |
|------|--------|
| `artifact.json` | shot_no 正确，至少一个 PRIMARY artifact |
| `shot_meta.json` | shot_no、source_system、authority_level、window_source 存在 |
| `signal_catalog.jsonl` | source_system、authority_level 标记正确 |
| `operation_events.jsonl` | event_family = OPERATION |
| `protection_events.jsonl` | event_family = PROTECTION |
| `waveform_ingest_request.json` | shot_no、source_system、signals 非空 |
| `waveform_channel_batch.jsonl` | shot_no、encoding=raw、samples 非空 |

成功输出 `validation-ok`，失败抛出异常并显示具体错误。

## 🧩 tdms_derive 核心包

`tools/tdms_derive/` 是派生引擎核心实现，所有工具脚本调用 `run_preprocessor()`。

🧭 模块结构：

| 模块 | 职责 |
|------|------|
| `pipeline.py` | 主编排：加载 TDMS → 统计计算 → 事件检测 → 输出 |
| `parser.py` | TDMS 解析、稳定性检查 |
| `models.py` | 不可变数据模型 |
| `constants.py` | 参数与阈值常量 |
| `shot.py` | 炮元数据与时间线 |
| `operations.py` | 运行事件检测 |
| `protection.py` | 保护事件检测 |
| `waveform.py` | 波形分块与编码 |
| `output.py` | JSON/JSONL 输出 |
| `naming.py` | 命名与路径规则 |
| `utils.py` | 通用工具 |
| `random_fields.py` | 辅助字段生成 |

🔬 派生流程：

```
run_preprocessor(shot_no, input_root, output_root)
  → 读取 TDMS
  → 计算统计量
  → 构建 ShotMeta 与 Signal Catalog
  → 检测运行事件与保护事件
  → 构建波形入库请求与通道批次
  → 写入 7 个输出文件
```

🧪 派生流程细节：

1. 读取 `{shot_no}_Tube.tdms`（必需），`{shot_no}_Water.tdms`（可选）
2. 提取通道并计算统计量（基线、噪声、峰值、RMS）
3. 基于参考通道（NegVoltage、PosVoltage、InPower）确定活跃时间窗口
4. 构建 ShotMeta 与 Signal Catalog
5. 运行事件检测：STEP（阶跃）与 RAMP（爬坡）
6. 保护事件检测：NO_WAVE、PEAK_HIGH、DROPOUT、COOLING、EARLY_TERMINATION
7. 波形分块并写入批次（4096 点/块）

🧩 核心数据模型示例：

```python
@dataclass(frozen=True, slots=True)
class ArtifactRecord:
    shot_no: int
    data_type: str
    artifact_id: str
    sha256_hex: str
```

## 🧭 通道 → Process ID 映射

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

## 🧪 事件检测参数

### 🪜 阶跃检测（STEP）

| 参数 | 值 | 说明 |
|------|-----|------|
| smooth_window | 21 | 平滑窗口大小 |
| level_window | 200 | 水平分割窗口 |
| prepost_window | 100 | 阶跃前后比较窗口 |
| merge_gap | 40 | 合并间距 |
| quantile | 0.99 | 分位数阈值 |
| sigma_factor | 1.5 | 噪声倍数系数 |

最小变化量：NegVoltage/PosVoltage=0.001，FilaVoltage/FilaCurrent=0.003，InPower=0.03，RefPower=0.02。

### 📈 爬坡检测（RAMP）

| 参数 | 值 | 说明 |
|------|-----|------|
| smooth_window | 21 | 平滑窗口大小 |
| slope_window | 250 | 斜率计算窗口 |
| merge_gap | 120 | 合并间距 |
| min_duration_ms | 150 | 最短爬坡时长 |
| sigma_factor | 2.0 | 噪声倍数系数 |

### 🛡️ 保护检测阈值

| 类型 | 关键参数 |
|------|----------|
| NO_WAVE | min_signal_level=0.05, min_dynamic_range=0.02 |
| PEAK_HIGH | sigma_threshold=10.0, filter_window=9 |
| DROPOUT | high_ratio=0.85, low_ratio=0.25, window_ms=100 |
| COOLING | sigma_factor=6.0, min_delta=0.04, baseline_window_ms=500 |
| EARLY_TERMINATION | tolerance_ratio=0.02, tolerance_seconds=0.05 |
