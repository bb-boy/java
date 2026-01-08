# 波形数据展示系统

## 系统简介
支持文件和网络两种数据源的波形数据展示系统，读取 TDMS 波形、操作日志、PLC 互锁信息，并通过 REST API 和 WebSocket 提供给前端。内置 Kafka 数据管道，可将本地文件同步到 Kafka，再落库到 MySQL/InfluxDB。

## 核心能力
- 文件/网络双数据源，运行时可切换，支持主备回退
- Kafka 数据管道：文件 → Kafka → MySQL/InfluxDB → 前端
- REST API（炮号列表、元数据、波形、日志、数据源状态）+ WebSocket 推送
- 辅助脚本：通道提取、波形读取、Kafka 数据发布、冒烟测试

## 运行模式与数据流
- `/api/data/*`：直接从主数据源读取（`app.data.source.primary=file|network`），`fallback=true` 时会合并备用数据源。
- `/api/kafka/sync/*`：文件 → Kafka → DataConsumer → MySQL/InfluxDB，全链路同步后再由 `/api/database` / `/api/hybrid` 查询。
- Kafka 网络模式需要 Kafka 集群；InfluxDB 默认开启写入（`app.influxdb.enabled=true`），未部署时请禁用或改 token。

## 目录速览
```
src/main/java/com/example/kafka/   # 核心后端代码
src/main/resources/                # 配置与静态页面
data/                              # 本地文件数据(TDMS/日志)
channels/                          # 通道元数据(JSON，extract_channels.py生成)
docker/                            # Kafka/InfluxDB/MySQL 的 compose 模板
start-file-source.sh               # 文件源启动脚本
start-network-source.sh            # 网络(Kafka)源启动脚本
run_tests.sh                       # 冒烟测试脚本
data_publisher.py                  # 示例数据发布到Kafka
extract_channels.py                # 从TDMS提取通道元数据
read_wave_data.py                  # 读取TDMS波形供Java调用
```

## 运行依赖
- JDK 17+、Maven 3.6+
- MySQL 8（默认 `jdbc:mysql://localhost:3306/wavedb`，用户 `root/devroot`，可用环境变量 `SPRING_DATASOURCE_*` 覆盖）
- InfluxDB 2.x（默认开启写入，可通过 `app.influxdb.enabled=false` 关闭或替换 token）
- （可选）Docker & Docker Compose：`docker/docker-compose.yml` 可同时启动 Kafka、InfluxDB、MySQL
- （可选）Python 3.8+：`pip install nptdms kafka-python`，用于 TDMS 读取和 Kafka 数据发布

### 快速启动依赖（可选，推荐）
```bash
cd docker
export CLUSTER_ID=$(uuidgen)
export MYSQL_ROOT_PASSWORD=devroot
export MYSQL_DATABASE=wavedb
export MYSQL_USER=wavedb
export MYSQL_PASSWORD=wavedb123
docker compose up -d   # 启动 Kafka + InfluxDB + MySQL
```

## 启动应用
```bash
mvn clean package -DskipTests
```

- **文件数据源(默认)**
  ```bash
  ./start-file-source.sh
  # 或者
  java -jar target/kafka-demo-1.0.0.jar \
    --app.data.source.primary=file \
    --spring.profiles.active=dev
  ```

- **网络数据源(Kafka)**
  确保 Kafka 已启动（可用上面的 docker compose）。
  ```bash
  ./start-network-source.sh

# 一键管理脚本
- 新增：`scripts/projectctl.sh` 可一键启动/停止/重启/查看状态整个项目（包含 Docker services、Java 应用以及文件模式的 watcher）。

用法示例：

- 启动（网络模式 - 默认）：

  ```bash
  ./scripts/projectctl.sh start --mode network
  ```

- 启动（文件模式，使用本地文件源）：

  ```bash
  ./scripts/projectctl.sh start --mode file
  ```

- 停止：

  ```bash
  ./scripts/projectctl.sh stop
  ```

- 查看状态：

  ```bash
  ./scripts/projectctl.sh status
  ```

注：脚本会把应用日志写入 `logs/app.log`，并在 `run/` 中存放 pid 文件。
  # 或者
  java -jar target/kafka-demo-1.0.0.jar \
    --app.data.source.primary=network \
    --spring.profiles.active=dev
  ```

- **前端示例**
  打开 `frontend-example.html` 或 `src/main/resources/static/index.html`。

## 数据准备与辅助脚本
- `extract_channels.py`：扫描 TDMS，生成 `channels/channels_*.json`，提供完整通道列表给后端。  
  **注：** 现在 `DataConsumer` 会在消费 `wave-data` 时将通道信息写入数据库 `channels` 表，`FileDataSource.getChannelNames()` 会优先读取该表（若存在），然后回退到 `channels/*.json`，最后使用默认通道。
- `read_wave_data.py`：被 `FileDataSource` 调用，读取 TDMS 波形。
- `data_publisher.py`：将本地文件数据发送到 Kafka，用于测试网络数据源。
- `run_tests.sh`：编译、打包、启动应用并做 API 冒烟测试（使用默认端点 `/api/data/...`）。

## 主要配置
`src/main/resources/application.yml`：
- 数据源路径：`app.data.tube.path`、`app.data.logs.path`、`app.data.plc.path`（通道元数据默认 `channels/`，由 `extract_channels.py` 生成）
- 数据源切换：`app.data.source.primary` (`file`/`network`)，`app.data.source.fallback=true` 时自动合并备用数据源
- Kafka：`spring.kafka.bootstrap-servers`，主题在 `app.kafka.topic.*`，消费者组 `app.kafka.group-id`
- 网络接收器：`app.network.enabled=false`、`app.network.port=9999`
- 存储：默认 MySQL，可用环境变量或 `application-mysql.yml` 覆盖
- InfluxDB：`app.influxdb.*`（默认开启写入，记得配置 token/URL，未部署可设 `enabled=false`）

## API 快速参考
- **数据源直读 `/api/data`**：`shots`、`shots/{shotNo}/metadata|complete|wave|channels|logs/*`、`status`、`source/switch`、`shots/batch-metadata`
- **数据管道 `/api/kafka`**：`health`、`sync/shot`、`sync/batch`、`sync/all`（文件→Kafka→DB/Influx）
- **数据库查询 `/api/database`**：`stats`、`metadata?shotNo`、`wavedata?shotNo`、`logs?shotNo`
- **混合查询 `/api/hybrid`**：`shots`、`stats`、`shot/{shotNo}`、`shot/{shotNo}/complete`、`waveform?shotNo&channelName`、`timeline/{shotNo}`
- **WebSocket**：`ws://localhost:8080/ws`，订阅 `/topic/shots`、`/topic/shot/{shotNo}` 等

## 测试
- 自动：`./run_tests.sh`（会构建 JAR、启动应用并调用核心 API）
- 手动示例：
  ```bash
  curl http://localhost:8080/api/data/shots
  curl http://localhost:8080/api/data/shots/1/metadata | python3 -m json.tool
  ```

## 常见问题
- **TDMS 通道不完整？** 先运行 `python extract_channels.py --all` 生成 `channels/*.json`，后端会返回完整通道列表。
- **数据库/消息组件未就绪？** 检查 MySQL、Kafka、InfluxDB 是否运行；如使用自建实例，调整 `application.yml` 或环境变量。
- **切换数据源失败？** 确认 `app.data.source.primary` 值和对应数据源可用性，必要时重启应用。
