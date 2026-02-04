# projectctl.sh — 项目启动控制脚本 📋

简短说明
- 脚本位置: `scripts/projectctl.sh`
- 目的: 一键管理项目（启动/停止/重启/查看状态），包含 Docker 服务、Java 应用和可选的 TDMS watcher（文件监控）。

前提 ✅
- Linux 环境
- 已安装 Docker（或 Docker CLI 支持 `docker compose`）
- 已安装 Java（应用需要）和 Maven（脚本会在没有 JAR 时自动打包）
- Python3（用于 `watch_tdms.py`）及其依赖（如 `watchdog`、`pandas` 等）

主要功能 🔧
- 启动/停止 Docker 服务（`docker compose up -d` / `docker compose down`）
- 构建并启动 Java 应用（记录 PID、输出到 `logs/app.log`）
- 启动/停止 TDMS watcher（仅在 `file` 模式下，记录 PID）
- 状态检查，显示 Docker 服务、APP 与 watcher 的状态

使用方法 🧭

基本命令：
```bash
# 启动（文件模式，默认会启动 watcher）
./scripts/projectctl.sh start --mode file

# 启动（网络模式，不启用本地 watcher，使用 Kafka/数据库）
./scripts/projectctl.sh start --mode network

# 停止
./scripts/projectctl.sh stop

# 重启
./scripts/projectctl.sh restart --mode file

# 查看状态
./scripts/projectctl.sh status
```

说明：
- `--mode` 支持 `file`（默认）和 `network`。
  - `file`: 使用本地 TDMS 文件作为数据源并启动 watcher。
  - `network`: 不启动 watcher，假定 Kafka/MySQL/InfluxDB 等服务可用。

日志与 PID 📁
- 日志目录: `logs/`（例如 `logs/app.log`、`logs/watcher.log`）
- 运行时 PID: `run/app.pid`, `run/watcher.pid`
- 若脚本提示找不到 `run` 或 `logs` 目录，会自动创建

注意事项 / 故障排除 ⚠️
- 如果 `docker` 命令不支持 `docker compose`，脚本会尝试 `docker-compose`，两种都支持最好。
- 若 watcher 报错 `ModuleNotFoundError: No module named 'watchdog'`，请安装依赖：
  ```bash
  pip3 install watchdog pandas scipy matplotlib
  ```
- 应用无法绑定端口或启动失败：查看 `logs/app.log` 以获取详细错误信息。
- 如果脚本报告权限或 PID 文件问题，检查目录权限并确认脚本以合适的用户运行。

把脚本作为 systemd 服务（建议，示例） 🔁
- 可创建 `systemd` 单元文件将 watcher 或整个脚本作为服务管理（省略具体实现，按需可帮你生成）。

常见命令示例 ✨
- 仅启动应用（不启动 Docker）：
  ```bash
  ./scripts/projectctl.sh start --mode network
  ```
- 在文件模式下（自动监控新 TDMS 文件）：
  ```bash
  ./scripts/projectctl.sh start --mode file
  ```

如果需要，我可以：
- 帮你把这个 README 添加到仓库并提交（`git add && git commit`）。 ✅
- 为 `systemd` 生成服务示例。⚙️

---
*作者提示*: 如果需要更详细的使用样例或想把 `projectctl.sh` 注册为 systemd 服务，告诉我你偏好的用户/服务配置，我会生成示例单元文件。