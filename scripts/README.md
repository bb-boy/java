# projectctl.sh — 项目启动控制脚本 📋

简短说明
- 脚本位置: `scripts/projectctl.sh`
- 目的: 一键管理项目（启动/停止/重启/查看状态），包含 Docker 服务、Java 应用和可选的 TDMS watcher（文件监控）。

前提 ✅
- Linux 环境
- 已安装 Docker（或 Docker CLI 支持 `docker compose`）
- 已安装 Java（应用需要）和 Maven（脚本会在没有 JAR 时自动打包）
- Python3（用于 `watch_tdms.py`）及其依赖（如 `watchdog`、`pandas` 等）
- 仅支持 Docker Compose v2（`docker compose`），`docker-compose` v1 会触发 `ContainerConfig` 报错
- Kafka KRaft 需要 `CLUSTER_ID`，脚本会通过 `confluentinc/cp-kafka:8.1.1` 生成并保存到 `run/cluster_id`（可用环境变量或 `docker/.env` 覆盖）
- MySQL 相关环境变量未设置时会使用默认值：`devroot / wavedb / wavedb / wavedb123`
- 如果 `docker/data/kafka*` 已存在，脚本会读取其中 `meta.properties` 的 `cluster.id` 并更新 `run/cluster_id`，避免集群 ID 不一致导致 Kafka 反复重启；如需重置，请删除 `docker/data/kafka*`。

主要功能 🔧
- 启动/停止 Docker 服务（`docker compose up -d` / `docker compose down`）
- 构建并启动 Java 应用（记录 PID、输出到 `logs/app.log`）
- 可选启动/停止 TDMS watcher（`--watch`，记录 PID）
- 状态检查，显示 Docker 服务、APP 与 watcher 的状态

使用方法 🧭

基本命令：
```bash
# 启动
./scripts/projectctl.sh start

# 启动并启用 watcher（自动触发 Kafka 同步）
./scripts/projectctl.sh start --watch

# 停止
./scripts/projectctl.sh stop

# 重启


# 查看状态
./scripts/projectctl.sh status
```

说明：
- `--watch` 可选启用 TDMS watcher，用于监控文件并触发 `/api/kafka/sync/*` 同步。
- 如需自定义 Kafka/MySQL 相关环境变量，请在运行前 `export` 或配置 `docker/.env`。

日志与 PID 📁
- 日志目录: `logs/`（例如 `logs/app.log`、`logs/watcher.log`）
- 运行时 PID: `run/app.pid`, `run/watcher.pid`
- 若脚本提示找不到 `run` 或 `logs` 目录，会自动创建

注意事项 / 故障排除 ⚠️
- 仅支持 `docker compose`（Compose v2）。若系统只有 `docker-compose` v1，会直接报错并退出。
- 若 watcher 报错 `ModuleNotFoundError: No module named 'watchdog'`，请安装依赖：
  ```bash
  pip3 install watchdog pandas scipy matplotlib
  ```
- 应用无法绑定端口或启动失败：查看 `logs/app.log` 以获取详细错误信息。
- 如果脚本报告权限或 PID 文件问题，检查目录权限并确认脚本以合适的用户运行。

把脚本作为 systemd 服务（建议，示例） 🔁
- 可创建 `systemd` 单元文件将 watcher 或整个脚本作为服务管理（省略具体实现，按需可帮你生成）。

常见命令示例 ✨
- 仅启动应用（不启用 watcher）：
  ```bash
  ./scripts/projectctl.sh start
  ```
- 启用 watcher（自动监控新 TDMS 文件并触发同步）：
  ```bash
  ./scripts/projectctl.sh start --watch
  ```

如果需要，我可以：
- 帮你把这个 README 添加到仓库并提交（`git add && git commit`）。 ✅
- 为 `systemd` 生成服务示例。⚙️

---
*作者提示*: 如果需要更详细的使用样例或想把 `projectctl.sh` 注册为 systemd 服务，告诉我你偏好的用户/服务配置，我会生成示例单元文件。
