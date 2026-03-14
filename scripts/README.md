# 🛠️ projectctl.sh — 项目管理脚本

## 🎯 作用

`scripts/projectctl.sh` 用于一键管理 Docker 服务、Java 应用，以及可选的 TDMS 文件监控进程。

## ✅ 前置条件

| 依赖 | 最低版本 | 说明 |
|------|---------|------|
| Docker | 20+ | 需支持 `docker compose` v2 |
| Java | 17 | JDK 运行时 |
| Maven | 3.8+ | JAR 不存在时自动构建 |
| Python 3 | 3.10+ | 仅 `--watch` 模式需要 |

## 📌 用法

```bash
bash scripts/projectctl.sh <command> [options]
```

## 🧾 命令

| 命令 | 说明 |
|------|------|
| `start` | 启动 Docker + Java（可选 watcher） |
| `stop` | 停止 Java + Docker（可选 watcher） |
| `restart` | 停止后重启 Java（默认跳过 Docker） |
| `rebuild` | 强制构建 JAR 后重启 Java（默认跳过 Docker） |
| `status` | 查看 Docker、Java、Watcher 运行状态 |

## ⚙️ 选项

| 选项 | 说明 |
|------|------|
| `--watch` | 同时管理 TDMS watcher（`data/watch_tdms.py`） |
| `--skip-docker` | 跳过 Docker Compose 操作 |

## 🧭 运行逻辑

- 🐳 `start` 会确保 Docker 运行并启动应用
- 🧱 JAR 不存在时自动执行 `mvn clean package -DskipTests`
- 🧾 Java 进程以后台方式启动，PID 记录在 `run/app.pid`
- 🔁 `restart` 与 `rebuild` 默认跳过 Docker（脚本内置行为）
- 👀 `--watch` 会启动 `python3 data/watch_tdms.py --scan-now --api-url http://localhost:8080`

## 🗂️ 日志与 PID

| 文件 | 说明 |
|------|------|
| `run/app.pid` | Java 应用 PID |
| `run/watcher.pid` | Watcher PID |
| `run/cluster_id` | Kafka KRaft Cluster ID 缓存 |
| `logs/app.log` | Java 应用日志 |
| `logs/watcher.log` | Watcher 日志 |

## 🔐 环境变量与默认值

脚本会自动处理 `CLUSTER_ID`：

- 🧭 若 Kafka 数据目录已有 `meta.properties`，使用其 `cluster.id`
- 🧪 否则优先使用 `run/cluster_id`，不存在时自动生成并写入

MySQL 默认值（可通过环境变量或 `docker/.env` 覆盖）：

| 变量 | 默认值 |
|------|--------|
| `MYSQL_ROOT_PASSWORD` | `devroot` |
| `MYSQL_DATABASE` | `wavedb` |
| `MYSQL_USER` | `wavedb` |
| `MYSQL_PASSWORD` | `wavedb123` |

## 🧪 使用示例

```bash
bash scripts/projectctl.sh start --watch
bash scripts/projectctl.sh start --skip-docker
bash scripts/projectctl.sh stop --watch
bash scripts/projectctl.sh rebuild
bash scripts/projectctl.sh status
```

## ❓ 常见问题

🐍 ModuleNotFoundError: No module named 'watchdog'

```bash
pip3 install watchdog
```

🚫 端口 8080 已被占用

```bash
fuser -n tcp 8080
bash scripts/projectctl.sh stop
```

🧩 Kafka Cluster ID 不匹配

```bash
rm -rf docker/data/kafka* run/cluster_id
bash scripts/projectctl.sh start
```

⚠️ Docker Compose 版本不兼容

仅支持 `docker compose` v2。
