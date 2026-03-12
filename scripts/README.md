# projectctl.sh — 项目管理脚本

## 概述

`scripts/projectctl.sh` 是一键管理脚本，负责 Docker 服务编排、Java 应用生命周期管理、以及可选的 TDMS 文件监控进程。

## 前置条件

| 依赖 | 最低版本 | 说明 |
|------|---------|------|
| Docker | 20+ | 需支持 `docker compose` v2 子命令 |
| Java | 17 | JDK 运行时 |
| Maven | 3.8+ | 仅当 JAR 不存在时自动触发构建 |
| Python 3 | 3.10+ | 仅 `--watch` 模式需要，依赖 `watchdog` + `nptdms` |

## 命令

```bash
bash scripts/projectctl.sh <command> [options]
```

### 可用命令

| 命令 | 说明 |
|------|------|
| `start` | 启动 Docker 服务 + Java 应用（自动检测 JAR 是否存在，缺失时自动构建）|
| `stop` | 停止 Java 应用 + Docker 服务 |
| `restart` | 停止后重新启动（JAR 存在则跳过构建）|
| `rebuild` | 强制 `mvn clean package` 后启动 |
| `status` | 显示 Docker、Java 应用、Watcher 的运行状态 |

### 可用选项

| 选项 | 说明 |
|------|------|
| `--watch` | 启动 / 停止时同时管理 TDMS 文件监控进程（`data/watch_tdms.py`）|
| `--skip-docker` | 跳过 Docker Compose 操作（仅管理 Java 应用）|

## 使用示例

```bash
# 完整启动：Docker + Java + TDMS 监控
bash scripts/projectctl.sh start --watch

# 仅启动 Java 应用（Docker 已在运行）
bash scripts/projectctl.sh start --skip-docker

# 完整停止
bash scripts/projectctl.sh stop --watch

# 重新构建并启动
bash scripts/projectctl.sh rebuild

# 查看所有服务状态
bash scripts/projectctl.sh status
```

## 启动流程细节

### 1. Docker 服务

- 自动检测 `docker/docker-compose.yml` 中的 `CLUSTER_ID` 环境变量
- 首次启动时生成 Kafka KRaft Cluster ID 并缓存到 `run/cluster_id`
- 后续启动复用已缓存的 Cluster ID
- 执行 `docker compose up -d` 且等待 MySQL 健康检查通过

### 2. Java 应用

- 检查 `target/kafka-demo-1.0.0.jar` 是否存在
  - 不存在 → 自动执行 `mvn clean package -DskipTests`
  - 存在 → 跳过构建（使用 `rebuild` 强制重新构建）
- 以后台方式启动：`java -jar ... --spring.profiles.active=dev`
- PID 记录到 `run/app.pid`
- 日志输出到 `logs/app.log`

### 3. TDMS Watcher（`--watch`）

- 启动 `python3 data/watch_tdms.py --scan-now --api-url http://localhost:8080`
- PID 记录到 `run/watcher.pid`
- 日志输出到 `logs/watcher.log`

## 停止流程

1. 读取 `run/app.pid`，发送 SIGTERM 停止 Java 进程
2. 若 `--watch`，读取 `run/watcher.pid` 停止 Python 进程
3. 若非 `--skip-docker`，执行 `docker compose down`
4. 清理残留进程（`fuser` 兜底清理端口）

## 文件说明

| 文件 | 说明 |
|------|------|
| `run/app.pid` | Java 应用 PID |
| `run/watcher.pid` | Watcher 进程 PID |
| `run/cluster_id` | Kafka KRaft Cluster ID 缓存 |
| `logs/app.log` | Java 应用日志 |
| `logs/watcher.log` | Watcher 日志 |

## MySQL 默认配置

脚本内置的 MySQL 默认值（可通过环境变量或 `docker/.env` 覆盖）：

| 变量 | 默认值 |
|------|--------|
| `MYSQL_ROOT_PASSWORD` | `devroot` |
| `MYSQL_DATABASE` | `wavedb` |
| `MYSQL_USER` | `wavedb` |
| `MYSQL_PASSWORD` | `wavedb123` |

## 常见问题

### ModuleNotFoundError: No module named 'watchdog'

```bash
pip3 install watchdog
```

### 端口 8080 已被占用

```bash
# 查找占用进程
fuser -n tcp 8080
# 或使用 projectctl 停止
bash scripts/projectctl.sh stop
```

### Kafka 启动失败 (Cluster ID mismatch)

删除旧数据重新生成：

```bash
rm -rf docker/data/kafka* run/cluster_id
bash scripts/projectctl.sh start
```

### Docker Compose 版本不兼容

仅支持 `docker compose` v2（不支持 `docker-compose` v1 连字号形式）。