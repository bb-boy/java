# TDMS 文件实时监控工具使用文档

## 概述

`watch_tdms.py` 是一个基于 watchdog 的文件监控工具，能够实时检测 TDMS 文件的创建并自动触发 Kafka 同步，将数据写入 MySQL 和 InfluxDB。

## 功能特性

- ✅ **实时监控**：自动检测目录中新创建的 .tdms 文件
- ✅ **智能延迟**：文件创建后等待指定时间，确保文件完全写入
- ✅ **自动日志生成**：检测到操作日志缺失时自动运行 scan.py 生成日志
- ✅ **智能重试机制**：同步失败时自动生成日志并重试一次
- ✅ **自动同步**：检测到文件后自动调用 `/api/kafka/sync/shot` API
- ✅ **关键字过滤**：只处理文件名包含特定关键字的文件（如 "Tube"）
- ✅ **递归监控**：支持监控所有子目录
- ✅ **去重机制**：防止同一文件被重复处理
- ✅ **详细日志**：显示检测、同步状态和结果

## 安装依赖

```bash
pip3 install watchdog requests
```

## 使用方法

### 基本用法

```bash
# 监控默认目录（从 scan_config.json 读取）
python3 watch_tdms.py

# 监控指定目录
python3 watch_tdms.py --watch-dir /path/to/TUBE

# 只监控包含 "Tube" 的文件
python3 watch_tdms.py --only "Tube"

# 自定义同步延迟（秒）
python3 watch_tdms.py --sync-delay 3

# 指定 API 地址
python3 watch_tdms.py --api-url http://192.168.1.100:8080
```

### 后台运行

```bash
# 方式1：使用 nohup
nohup python3 watch_tdms.py --only "Tube" > watch.log 2>&1 &

# 方式2：使用 screen
screen -dmS tdms-watch python3 watch_tdms.py --only "Tube"

# 方式3：使用 systemd（推荐生产环境）
# 见下文 systemd 配置
```

### 停止监控

```bash
# 查找进程
ps aux | grep watch_tdms.py

# 停止进程
pkill -f watch_tdms.py
```

## 命令行参数

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--watch-dir` | 监控目录路径 | 从配置文件读取 `tdms_root_dir` |
| `--only` | 文件名关键字过滤 | 从配置文件读取 `only_keyword` |
| `--api-url` | API 服务器地址 | `http://localhost:8080` |
| `--sync-delay` | 文件创建后延迟时间（秒） | `2` |
| `--recursive` | 递归监控子目录 | `True`（默认开启） |

## 工作流程

### 标准流程（操作日志已存在）

```
1. 检测到新的 .tdms 文件
   ↓
2. 提取炮号（从文件名或目录名）
   ↓
3. 等待 N 秒（确保文件写入完成）
   ↓
4. 检查操作日志是否存在（TUBE_logs/{shot}/{shot}_Tube_operation_log.txt）
   ↓
5. 调用 API: GET /api/kafka/sync/shot?shotNo=N
   ↓
6. DataPipelineService 读取文件并发送到 Kafka
   ↓
7. DataConsumer 自动消费并写入 MySQL + InfluxDB
   ↓
8. 显示同步结果（元数据、波形、日志数量）
```

### 自动日志生成流程（NEW!）

当检测到操作日志不存在时，watcher 会自动生成：

```
1. 检测到新的 .tdms 文件
   ↓
2. 提取炮号
   ↓
3. 等待 N 秒
   ↓
4. 检查操作日志 → 不存在
   ↓
5. 自动运行: python3 scan.py --root TUBE --out TUBE_logs --only "{shot}_"
   ↓
6. 验证日志文件已生成
   ↓
7. 调用同步 API
   ↓
8. 数据写入 Kafka → MySQL + InfluxDB
```

### 智能重试流程（NEW!）

当首次同步失败（元数据不存在）时，自动重试：

```
1. 调用同步 API
   ↓
2. 检测到错误："元数据不存在"
   ↓
3. 自动运行 scan.py 生成操作日志
   ↓
4. 等待 1 秒
   ↓
5. 重新调用同步 API（最多重试 1 次）
   ↓
6. 同步成功 ✓
```

## 输出示例

### 标准同步输出

```
============================================================
TDMS 文件实时监控工具
============================================================
监控目录: /home/igusa/java/data/TUBE
关键字过滤: Tube
API 地址: http://localhost:8080
同步延迟: 2 秒
递归监控: 是
============================================================

按 Ctrl+C 停止监控

[连接] API 服务器正常运行 ✓

[启动] 正在监控 /home/igusa/java/data/TUBE ...


[检测到新文件] /home/igusa/java/data/TUBE/3/3_Tube.tdms
  等待 2 秒以确保文件写入完成...
  [同步] 调用 API: http://localhost:8080/api/kafka/sync/shot?shotNo=3
  [成功] 炮号 3 同步完成
    - 元数据: 1
    - 波形数据: 32 通道
    - 操作日志: 15 条
    - PLC 互锁: 0 条
```

### 自动生成日志输出（NEW!）

当检测到操作日志缺失时：

```
[检测到新文件] /home/igusa/java/data/TUBE/77/77_Tube.tdms
  等待 3 秒以确保文件写入完成...
  [日志] 操作日志不存在，正在生成...
    执行命令: python3 scan.py --root TUBE --out TUBE_logs --only 77_
    [成功] 操作日志已生成: TUBE_logs/77/77_Tube_operation_log.txt
  [同步] 调用 API: http://localhost:8080/api/kafka/sync/shot?shotNo=77
  [成功] 炮号 77 同步完成
    - 元数据: 1
    - 波形数据: 9 通道
    - 操作日志: 15 条
```

### 智能重试输出（NEW!）

当首次同步失败时自动重试：

```
[检测到新文件] /home/igusa/java/data/TUBE/88/88_Tube.tdms
  等待 3 秒以确保文件写入完成...
  [同步] 调用 API: http://localhost:8080/api/kafka/sync/shot?shotNo=88
  [失败] 炮号 88 同步失败: 元数据不存在
  [重试] 检测到元数据问题，尝试重新生成日志...
  [日志] 操作日志不存在，正在生成...
    执行命令: python3 scan.py --root TUBE --out TUBE_logs --only 88_
    [成功] 操作日志已生成: TUBE_logs/88/88_Tube_operation_log.txt
  [重试] 日志已生成，重新调用同步 API...
  [成功] 炮号 88 重试同步成功
    - 元数据: 1
    - 波形数据: 9 通道
    - 操作日志: 15 条
```

## 自动日志生成功能详解（NEW!）

### 功能说明

从版本 2.0 开始，`watch_tdms.py` 内置了智能日志生成和重试机制，无需手动运行 `scan.py`。

### 两层保护机制

**1. 预检查机制（推荐）**

在调用同步 API 之前，watcher 会检查操作日志是否存在：

- ✅ 日志存在 → 直接同步
- ❌ 日志不存在 → 自动运行 `scan.py --only "{shot}_"` → 验证生成成功 → 同步

**2. 重试机制（兜底保护）**

如果预检查未发现问题，但同步时检测到"元数据不存在"错误：

- 自动运行 `scan.py` 生成日志
- 等待 1 秒
- 重新调用同步 API（最多重试 1 次）

### 执行的命令

```bash
python3 scan.py --root TUBE --out TUBE_logs --only "{shot_number}_"
```

示例：
- 炮号 77 → `--only "77_"`
- 炮号 100 → `--only "100_"`

### 优点

- ✅ **零干预**：无需手动运行 scan.py 批处理
- ✅ **按需生成**：只处理当前炮号，节省时间
- ✅ **故障自愈**：同步失败时自动修复并重试
- ✅ **可靠性高**：两层保护机制，确保数据完整

### 注意事项

1. **超时限制**：scan.py 执行超时时间为 60 秒
2. **重试次数**：失败后最多重试 1 次（避免无限循环）
3. **日志验证**：生成后会验证日志文件是否真正创建
4. **路径要求**：scan.py 必须在 watcher 同目录下

## 配置文件集成

工具会自动读取 `scan_config.json` 中的配置：

```json
{
  "paths": {
    "tdms_root_dir": "TUBE",
    "only_keyword": "Tube"
  }
}
```

命令行参数会覆盖配置文件中的设置。

## Systemd 服务配置（推荐生产环境）

创建服务文件 `/etc/systemd/system/tdms-watcher.service`：

```ini
[Unit]
Description=TDMS File Watcher
After=network.target

[Service]
Type=simple
User=igusa
WorkingDirectory=/home/igusa/java/data
ExecStart=/usr/bin/python3 /home/igusa/java/data/watch_tdms.py --only "Tube"
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

启动服务：

```bash
sudo systemctl daemon-reload
sudo systemctl enable tdms-watcher
sudo systemctl start tdms-watcher

# 查看状态
sudo systemctl status tdms-watcher

# 查看日志
sudo journalctl -u tdms-watcher -f
```

## 故障排除

### 问题：API 连接失败

```
[错误] 无法连接到 API 服务器: http://localhost:8080
```

**解决方案**：
1. 确认 Java 应用正在运行：`ps aux | grep java`
2. 检查端口：`netstat -tulpn | grep 8080`
3. 测试连接：`curl http://localhost:8080/actuator/health`

### 问题：文件未被检测

**可能原因**：
1. 文件名不匹配关键字过滤
2. 文件是 `.tdms_index` 索引文件（会被自动跳过）
3. 监控目录配置错误

**解决方案**：
- 检查日志确认监控目录
- 移除 `--only` 参数测试全量监控
- 使用 `--watch-dir` 指定正确路径

### 问题：重复处理同一文件

工具内置了去重机制（`processed_files` 集合），同一文件路径不会被重复处理。如果重启进程，集合会清空，可能触发重新同步。

## 性能与资源

- **CPU 使用率**：空闲时 <1%，处理文件时 5-10%
- **内存占用**：~25-30 MB
- **网络带宽**：取决于 TDMS 文件大小（典型：10-50 MB/文件）
- **延迟**：检测 + 延迟 + 同步 = 通常 3-10 秒

## 最佳实践

1. **生产环境**：使用 systemd 服务而非手动后台运行
2. **网络稳定性**：如果 API 在远程服务器，考虑增加重试逻辑
3. **磁盘监控**：确保目标目录有足够空间
4. **日志轮转**：配置 logrotate 防止日志文件过大
5. **监控告警**：集成 Prometheus/Grafana 监控同步成功率

## 扩展功能

可以通过修改 `TdmsFileHandler` 类实现：

- 支持修改事件（取消注释 `on_modified` 方法）
- 添加文件 hash 校验（防止重复导入）
- 实现重试机制（API 失败时自动重试）
- 发送告警通知（邮件/钉钉/企业微信）
- 记录统计数据（处理文件数、成功/失败率）

## 与其他工具配合

- **scan.py**：离线批量处理历史文件
- **watch_tdms.py**：实时监控新增文件（本工具）
- **DataPipelineService**：核心同步逻辑
- **DataConsumer**：自动消费 Kafka 并写入数据库

## 测试结果

测试环境：
- 监控目录：`/home/igusa/java/data/TUBE`
- 关键字：`Tube`
- 测试文件：炮号 3 的 TDMS 文件

测试结果：
```
[检测到新文件] /home/igusa/java/data/TUBE/3/3_Tube.tdms
  等待 1 秒以确保文件写入完成...
  [同步] 调用 API: http://localhost:8080/api/kafka/sync/shot?shotNo=3
  [成功] 炮号 3 同步完成
    - 元数据: 1
    - 波形数据: 32 通道
    - 操作日志: 15 条
    - PLC 互锁: 0 条
```

验证数据：
```bash
curl "http://localhost:8080/api/hybrid/waveform?shotNo=3&channelName=InPower"
# 返回: 400 个采样点 ✓
```

## 更新日志

- **v1.0** (2026-01-08)
  - 初始版本
  - 支持实时文件监控
  - 自动调用同步 API
  - 去重与延迟机制
  - 关键字过滤
