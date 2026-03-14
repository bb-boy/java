# 👀 TDMS 文件监控与补扫工具 — watch_tdms.py

## 🎯 作用

`data/watch_tdms.py` 基于 `watchdog` 进行文件系统监控，负责：

- 📂 监控 `data/TUBE` 新增/移动的 `.tdms` 文件
- 🧪 自动调用 `tools/tdms_preprocessor.py` 生成派生输出
- 📥 自动调用 `POST /api/ingest/shot` 触发入库
- 🔁 定时补扫目录，发现缺失炮号后补算

## 🧪 依赖安装

```bash
pip3 install watchdog nptdms
```

## 🚀 基本用法

```bash
python3 data/watch_tdms.py
python3 data/watch_tdms.py --scan-now
python3 data/watch_tdms.py --scan-only
python3 data/watch_tdms.py \
  --watch-root data/TUBE \
  --output-root data/derived \
  --api-url http://localhost:8080 \
  --scan-now \
  --scan-interval-seconds 600
```

## 🧾 参数说明

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--watch-root` | `TUBE` | TDMS 根目录（相对 data/ 或绝对路径） |
| `--output-root` | `derived` | 派生输出目录（相对 data/ 或绝对路径） |
| `--api-url` | `http://localhost:8080` | 入库 API 地址 |
| `--file-pattern` | `.*_Tube\.tdms$` | TDMS 文件名正则 |
| `--scan-interval-seconds` | `600` | 补扫周期（秒） |
| `--scan-now` | 关闭 | 启动后立即补扫 |
| `--scan-only` | 关闭 | 仅补扫一次后退出 |
| `--recursive` / `--no-recursive` | 开启 | 是否递归监控 |
| `--stability-checks` | `2` | 稳定性检查次数 |
| `--stability-wait-ms` | `100` | 稳定性检查间隔（毫秒） |
| `--python` | `python3` | Python 可执行文件路径 |
| `--watch-dir` | 无 | 旧参数兼容（等同 `--watch-root`） |

## 🧭 工作流程

👀 实时监控：

```
on_created / on_moved
  → 文件名匹配
  → 提取 shot_no
  → 入队去重
  → process_shot()
```

🔁 补扫机制：

1. 扫描 `watch_root` 下所有 `.tdms`
2. 对比 `output_root/{shot_no}/` 是否存在
3. 缺失炮号放入队列处理

📥 自动入库：

```
POST {api_url}/api/ingest/shot?shotNo={shot_no}
```

⏱️ 超时时间 30 秒。单个炮号失败会记录日志并继续处理其他炮号。

## 🧩 与 projectctl 集成

```bash
bash scripts/projectctl.sh start --watch
```

日志写入 `logs/watcher.log`，PID 记录到 `run/watcher.pid`。

## 🧾 启动输出示例

```
TDMS 文件监控与补扫
监控目录: /home/pl/java/data/TUBE
派生目录: /home/pl/java/data/derived
API 地址: http://localhost:8080
文件规则: .*_Tube\.tdms$
补扫周期: 600 秒
启动补扫: 是
稳定检查: 2 次 / 100 ms
递归监控: 是
[补扫] 发现 3 个缺失派生的炮号: [2, 3, 5]
[处理] shot=2: 开始派生...
[处理] shot=2: 派生完成, 正在入库...
[处理] shot=2: 入库成功
```

## ⚠️ 注意事项

- 🧪 文件稳定性检查用于避免读取尚未写完的 TDMS 文件
- 🧹 队列内置去重，同一炮号不会重复处理
