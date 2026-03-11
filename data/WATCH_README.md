# TDMS 文件监控与补扫工具

## 摘要
`watch_tdms.py` 负责监控 `TUBE` 新文件并触发派生，同时周期性扫描 `TUBE` 与 `derived`，发现未解析炮号时补扫。

## 依赖
```bash
pip3 install watchdog
```

## 功能
- 监听 `TUBE` 新增 `.tdms` 文件
- 自动触发 `tools/tdms_preprocessor.py` 解析
- 定时补扫：对比 `TUBE` 与 `derived`，发现缺失即补算
- 支持一次性补扫（启动即执行）

## 基本用法
```bash
# 监控默认目录（data/TUBE 与 data/derived）
python3 data/watch_tdms.py

# 启动后立即补扫一次
python3 data/watch_tdms.py --scan-now

# 自定义目录与周期
python3 data/watch_tdms.py --watch-root /data/tube --output-root /data/derived --scan-interval-seconds 900
```

## 参数说明
- `--watch-root`：TUBE 根目录（相对 `data/` 或绝对路径）
- `--output-root`：派生输出目录（相对 `data/` 或绝对路径）
- `--file-pattern`：TDMS 文件名正则（默认只匹配 `*_Tube.tdms`）
- `--scan-interval-seconds`：补扫周期（秒）
- `--scan-now`：启动后立刻执行一次补扫
- `--stability-checks` / `--stability-wait-ms`：文件稳定性检查
- `--python`：Python 可执行文件
- `--watch-dir`：旧参数兼容（等同 `--watch-root`）

## 输出示例
```
============================================================
TDMS 文件监控与补扫
============================================================
监控目录: /home/xxx/java/data/TUBE
派生目录: /home/xxx/java/data/derived
文件规则: .*_Tube\.tdms$
补扫周期: 600 秒
启动补扫: 否
稳定检查: 2 次 / 100 ms
递归监控: 是
============================================================
[启动] 正在监控 /home/xxx/java/data/TUBE
```

## 说明
- 该工具仅负责派生（生成 `data/derived`），不触发 `/api/ingest/shot`。
- 如需入库，请手动调用 API 或使用独立的导入流程。
