# TDMS 文件监控与补扫工具 — watch_tdms.py

## 概述

`data/watch_tdms.py` 是基于 `watchdog` 库的文件系统监控工具，负责：

1. **实时监听** `TUBE` 目录下新增/移动的 `.tdms` 文件
2. **自动派生** 调用 `tools/tdms_preprocessor.py` 生成结构化 JSON/JSONL
3. **自动入库** 调用 `POST /api/ingest/shot` 将派生数据发送到 Kafka
4. **周期补扫** 定时对比 `TUBE` 与 `derived` 目录，发现缺失则补算

## 依赖安装

```bash
pip3 install watchdog nptdms
```

## 基本用法

```bash
# 持续监控（默认目录）
python3 data/watch_tdms.py

# 启动后立即补扫一次 + 持续监控
python3 data/watch_tdms.py --scan-now

# 一次性补扫后退出（不启动监控）
python3 data/watch_tdms.py --scan-only

# 完整参数示例
python3 data/watch_tdms.py \
  --watch-root data/TUBE \
  --output-root data/derived \
  --api-url http://localhost:8080 \
  --scan-now \
  --scan-interval-seconds 600
```

## 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--watch-root` | `data/TUBE` | TDMS 原始文件根目录 |
| `--output-root` | `data/derived` | 派生输出目录 |
| `--api-url` | `http://localhost:8080` | Java 应用 API 地址（用于自动入库）|
| `--file-pattern` | `.*_Tube\.tdms$` | TDMS 文件名正则匹配 |
| `--scan-interval-seconds` | `600` | 补扫周期（秒）|
| `--scan-now` | 关闭 | 启动后立即执行一次补扫 |
| `--scan-only` | 关闭 | 仅执行一次补扫后退出 |
| `--recursive` / `--no-recursive` | 开启 | 是否递归监控子目录 |
| `--stability-checks` | `2` | 文件稳定性检查次数 |
| `--stability-wait-ms` | `100` | 每次检查间隔（毫秒）|
| `--python` | `python3` | Python 可执行文件路径 |

## 工作流程

### 实时监控

```
文件系统事件 (on_created / on_moved)
   │
   ▼ 文件名匹配 --file-pattern?
   │
   ▼ 提取 shot_no (从目录结构)
   │
   ▼ 放入 ShotQueue (线程安全去重队列)
   │
   ▼ process_shot(config, shot_no)
       ├── 若 derived/{shot_no}/ 已存在 → 直接调用 ingest API
       └── 否则 → run_preprocessor 派生 → 调用 ingest API
```

### 补扫机制

按 `--scan-interval-seconds` 周期执行：

1. 扫描 `--watch-root` 下所有子目录，提取炮号
2. 检查 `--output-root/{shot_no}/` 是否存在
3. 缺失的炮号放入 ShotQueue 处理

### 自动入库

`ingest_shot(config, shot_no)` 向 Java 应用发送 HTTP POST：

```
POST {api_url}/api/ingest/shot?shotNo={shot_no}
```

超时时间 30 秒。失败仅记录日志，不中断后续处理。

## 启动输出示例

```
============================================================
TDMS 文件监控与补扫
============================================================
监控目录: /home/pl/java/data/TUBE
派生目录: /home/pl/java/data/derived
API 地址: http://localhost:8080
文件规则: .*_Tube\.tdms$
补扫周期: 600 秒
启动补扫: 是
稳定检查: 2 次 / 100 ms
递归监控: 是
============================================================
[补扫] 发现 3 个缺失派生的炮号: [2, 3, 5]
[处理] shot=2: 开始派生...
[处理] shot=2: 派生完成, 正在入库...
[处理] shot=2: 入库成功
...
[启动] 正在监控 /home/pl/java/data/TUBE
```

## 与 projectctl 集成

通过 `scripts/projectctl.sh start --watch` 可自动启动本工具：

```bash
bash scripts/projectctl.sh start --watch
```

此时日志写入 `logs/watcher.log`，PID 记录到 `run/watcher.pid`。

## 注意事项

- 文件稳定性检查：监测到新文件后，会等 `--stability-checks` × `--stability-wait-ms` 确认文件写入完成
- 去重机制：`ShotQueue` 内部使用 set 去重，同一炮号不会重复处理
- 错误恢复：单个炮号处理失败不影响其他炮号，异常记录日志后继续