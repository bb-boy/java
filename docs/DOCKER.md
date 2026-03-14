# 🐳 Docker 部署指南

## 🧭 服务概览

`docker/docker-compose.yml` 定义了以下服务：

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| kafka1 | confluentinc/cp-kafka:8.1.1 | 19092 | Kafka Broker 1 |
| kafka2 | confluentinc/cp-kafka:8.1.1 | 29092 | Kafka Broker 2 |
| kafka3 | confluentinc/cp-kafka:8.1.1 | 39092 | Kafka Broker 3 |
| kafka-ui | provectuslabs/kafka-ui:latest | 18080 | Kafka 管理界面 |
| influxdb | influxdb:2.7 | 8086 | 时序数据库 |
| mysql | mysql:8.0 | 3306 | 关系型数据库 |

🧩 所有服务共享 `kafka-network` 桥接网络。

## ✅ 前置条件

- 🐳 Docker
- 🧰 Docker Compose v2（命令为 `docker compose`）

## 🔐 环境变量

Kafka KRaft 需要 `CLUSTER_ID`，MySQL 需要账号配置。推荐在 `docker/.env` 中维护：

```env
CLUSTER_ID=REPLACE_WITH_UUID
MYSQL_ROOT_PASSWORD=devroot
MYSQL_DATABASE=wavedb
MYSQL_USER=wavedb
MYSQL_PASSWORD=wavedb123
```

`CLUSTER_ID` 可通过 `kafka-storage random-uuid` 生成。使用 `scripts/projectctl.sh` 启动时，会自动生成并缓存到 `run/cluster_id`。

## ▶️ 启动与停止

```bash
cd docker
docker compose up -d
docker compose ps
docker compose logs -f kafka1
docker compose logs -f mysql
docker compose down
docker compose down -v
```

也可使用 `scripts/projectctl.sh` 统一管理（见 scripts/README.md）。

## 🛰️ Kafka 集群（3 节点 KRaft）

| 节点 | Node ID | 内部通信 | 宿主机端口 | Controller 端口 |
|------|---------|----------|-----------|----------------|
| kafka1 | 1 | kafka1:29092 | localhost:19092 | kafka1:29093 |
| kafka2 | 2 | kafka2:29092 | localhost:29092 | kafka2:29093 |
| kafka3 | 3 | kafka3:29092 | localhost:39092 | kafka3:29093 |

🎧 监听器协议：

| 协议 | 用途 |
|------|------|
| `PLAINTEXT_HOST` | 宿主机连接（`0.0.0.0:9092`） |
| `PLAINTEXT` | 容器内通信（`0.0.0.0:29092`） |
| `CONTROLLER` | KRaft 控制器通信（`0.0.0.0:29093`） |

⚙️ 核心参数：

| 参数 | 值 |
|------|-----|
| `KAFKA_DEFAULT_REPLICATION_FACTOR` | 3 |
| `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` | 3 |
| `KAFKA_MIN_INSYNC_REPLICAS` | 2 |
| `KAFKA_NUM_PARTITIONS` | 3 |
| `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | 3 |
| `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR` | 2 |
| `KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS` | 0 |

## 🧭 Kafka UI

| 项目 | 值 |
|------|-----|
| 访问地址 | http://localhost:18080 |
| 集群名称 | `local-kraft-3nodes` |
| Bootstrap Servers | `kafka1:29092,kafka2:29092,kafka3:29092` |

## 📈 InfluxDB

| 项目 | 值 |
|------|-----|
| 访问地址 | http://localhost:8086 |
| 管理员 | admin / admin123456 |
| 组织 | wavedata |
| Bucket | waveforms |
| API Token | `my-super-secret-token` |

数据持久化卷：`influxdb-data` → `/var/lib/influxdb2`。

## 🗄️ MySQL

| 项目 | 值 |
|------|-----|
| 访问地址 | localhost:3306 |
| Root 密码 | `MYSQL_ROOT_PASSWORD` |
| 默认数据库 | `MYSQL_DATABASE` |
| 应用用户 | `MYSQL_USER` / `MYSQL_PASSWORD` |

🧱 初始化 Schema：

```bash
mysql -h 127.0.0.1 -u root -p wavedb < sql/ecrh_schema.sql
```

## 💾 数据持久化

| 服务 | 卷 | 容器路径 |
|------|----|----------|
| kafka1 | ./data/kafka1 | /var/lib/kafka/data |
| kafka2 | ./data/kafka2 | /var/lib/kafka/data |
| kafka3 | ./data/kafka3 | /var/lib/kafka/data |
| influxdb | influxdb-data | /var/lib/influxdb2 |
| mysql | mysql-data | /var/lib/mysql |

## 🛠️ 常见操作

```bash
docker exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --list
docker exec kafka1 kafka-topics --bootstrap-server kafka1:29092 \
  --create --topic ecrh.shot.meta.v1 --partitions 3 --replication-factor 3
docker exec kafka1 kafka-consumer-groups --bootstrap-server kafka1:29092 --list
docker exec kafka1 kafka-consumer-groups --bootstrap-server kafka1:29092 \
  --group ecrh-projection-group --reset-offsets --to-earliest --all-topics --execute
```
