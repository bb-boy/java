# projectctl.sh — 项目启动控制脚本

## 摘要
用于一键管理 Docker 服务、Java 应用与可选的 TDMS watcher。

## 前提条件
- Linux 环境
- Docker（支持 `docker compose` v2）
- Java 17
- Maven（当 JAR 不存在时自动构建）
- Python3（用于 `data/watch_tdms.py`，需安装 `watchdog`）

## 主要功能
- 启动/停止 Docker 服务（`docker compose up -d` / `docker compose down`）
- 构建并启动 Java 应用（PID 与日志管理）
- 可选启动/停止 TDMS watcher（`--watch`）
- 状态检查（Docker、应用、watcher）

## 使用方法
```bash
# 启动
./scripts/projectctl.sh start

# 启动并启用 watcher（监控 TUBE 并自动派生）
./scripts/projectctl.sh start --watch

# 停止
./scripts/projectctl.sh stop

# 重启
./scripts/projectctl.sh restart

# 查看状态
./scripts/projectctl.sh status
```

## 说明
- `--watch` 会启动 `data/watch_tdms.py`，当 TDMS 文件到达或补扫发现缺失派生时执行解析。
- Kafka KRaft 需要 `CLUSTER_ID`，脚本会自动生成并写入 `run/cluster_id`。
- MySQL 默认值：`devroot / wavedb / wavedb / wavedb123`（可用环境变量或 `docker/.env` 覆盖）。

## 日志与 PID
- 日志目录：`logs/`（`logs/app.log`、`logs/watcher.log`）
- PID 文件：`run/app.pid`、`run/watcher.pid`

## 常见问题
- `ModuleNotFoundError: No module named 'watchdog'`
  ```bash
  pip3 install watchdog
  ```
- 端口占用或启动失败：查看 `logs/app.log`
- Compose 版本：仅支持 `docker compose` v2
