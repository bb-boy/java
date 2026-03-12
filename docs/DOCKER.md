# Docker 部署指南

## 服务概览

`docker/docker-compose.yml` 定义了以下 5 个服务：

| 服务 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| kafka1 | confluentinc/cp-kafka:8.1.1 | 19092 | Kafka Broker 1 |
| kafka2 | confluentinc/cp-kafka:8.1.1 | 29092 | Kafka Broker 2 |
| kafka3 | confluentinc/cp-kafka:8.1.1 | 39092 | Kafka Broker 3 |
| kafka-ui | provectuslabs/kafka-ui:latest | 18080 | Kafka 管理界面 |
| influxdb | influxdb:2.7 | 8086 | 时序数据库 |
| mysql | mysql:8.0 | 3306 | 关系型数据库 |

所有服务共享 `kafka-network` 桥接网络。

---

## 环境配置

### 必需环境变量

在 `docker/` 目录创建 `.env` 文件：

```env
CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk
MYSQL_ROOT_PASSWORD=devroot
MYSQL_DATABASE=wavedb
MYSQL_USER=waveuser
MYSQL_PASSWORD=wavepass
```

> `CLUSTER_ID` 是 Kafka KRaft 集群 ID，可通过 `kafka-storage random-uuid` 生成。

---

## 启动与停止

```bash
cd docker

# 启动所有服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f kafka1
docker compose logs -f mysql

# 停止所有服务
docker compose down

# 停止并清除数据卷
docker compose down -v
```

也可通过 `projectctl.sh` 统一管理（参见 scripts/README.md）。

---

## Kafka 集群（3 节点 KRaft）

采用 KRaft 模式（无 ZooKeeper），3 节点同时担任 broker + controller。

### 网络配置

| 节点 | Node ID | 内部通信 | 宿主机端口 | Controller 端口 |
|------|---------|----------|-----------|----------------|
| kafka1 | 1 | kafka1:29092 | localhost:19092 | kafka1:29093 |
| kafka2 | 2 | kafka2:29092 | localhost:29092 | kafka2:29093 |
| kafka3 | 3 | kafka3:29092 | localhost:39092 | kafka3:29093 |

监听器协议：

- `PLAINTEXT_HOST`: 宿主机连接（`0.0.0.0:N9092`）
- `PLAINTEXT`: 容器内部通信（`0.0.0.0:29092`）
- `CONTROLLER`: KRaft 控制器通信（`0.0.0.0:29093`）

### 核心参数

| 参数 | 值 | 说明 |
|------|-----|------|
| `KAFKA_DEFAULT_REPLICATION_FACTOR` | 3 | 默认副本数 |
| `KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR` | 3 | 偏移量 Topic 副本数 |
| `KAFKA_MIN_INSYNC_REPLICAS` | 2 | 最少同步副本 |
| `KAFKA_NUM_PARTITIONS` | 3 | 默认分区数 |
| `KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR` | 3 | 事务日志副本数 |
| `KAFKA_TRANSACTION_STATE_LOG_MIN_ISR` | 2 | 事务日志最少 ISR |
| `KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS` | 0 | 消费者组初始再平衡延迟 |

### 健康检查

```yaml
healthcheck:
  test: kafka-broker-api-versions --bootstrap-server localhost:9092
  interval: 30s
  timeout: 10s
  retries: 5
  start_period: 60s
```

### 数据持久化

| 卷 | 容器路径 | 说明 |
|----|----------|------|
| `./data/kafka1` | `/var/lib/kafka/data` | Broker 1 数据 |
| `./data/kafka2` | `/var/lib/kafka/data` | Broker 2 数据 |
| `./data/kafka3` | `/var/lib/kafka/data` | Broker 3 数据 |

---

## Kafka UI

| 项目 | 值 |
|------|-----|
| 访问地址 | http://localhost:18080 |
| 集群名称 | `local-kraft-3nodes` |
| Bootstrap Servers | `kafka1:29092,kafka2:29092,kafka3:29092` |
| 动态配置 | 启用 (`DYNAMIC_CONFIG_ENABLED=true`) |

依赖 kafka1、kafka2、kafka3 启动后才加载。

---

## InfluxDB

| 项目 | 值 |
|------|-----|
| 访问地址 | http://localhost:8086 |
| 管理员 | admin / admin123456 |
| 组织 | wavedata |
| Bucket | waveforms |
| API Token | `my-super-secret-token` |

### 数据持久化

| 卷 | 容器路径 |
|----|----------|
| `influxdb-data` (Docker named volume) | `/var/lib/influxdb2` |

### 初始化

首次启动时自动执行 setup 模式，创建 admin 用户、组织、bucket 和 token。

---

## MySQL

| 项目 | 值 |
|------|-----|
| 访问地址 | localhost:3306 |
| Root 密码 | 来自 `MYSQL_ROOT_PASSWORD` 环境变量 |
| 默认数据库 | 来自 `MYSQL_DATABASE` 环境变量 |
| 应用用户 | 来自 `MYSQL_USER` / `MYSQL_PASSWORD` |

### 健康检查

```yaml
healthcheck:
  test: mysqladmin ping -h localhost
  interval: 10s
  timeout: 5s
  retries: 10
```

### 数据持久化

| 卷 | 容器路径 |
|----|----------|
| `mysql-data` (Docker named volume) | `/var/lib/mysql` |

### 初始化 Schema

启动 MySQL 后，执行 SQL 初始化脚本：

```bash
mysql -h 127.0.0.1 -u root -p wavedb < sql/ecrh_schema.sql
```

---

## 网络拓扑

```
┌─────────────────────────────────────────────────┐
│                kafka-network (bridge)            │
│                                                  │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐         │
│  │ kafka1  │  │ kafka2  │  │ kafka3  │         │
│  │ :19092  │  │ :29092  │  │ :39092  │         │
│  └────┬────┘  └────┬────┘  └────┬────┘         │
│       │            │            │                │
│       └────────┬───┘────────────┘                │
│                │                                  │
│  ┌─────────┐  │  ┌──────────┐  ┌─────────┐     │
│  │kafka-ui │  │  │ influxdb │  │  mysql  │     │
│  │ :18080  │  │  │  :8086   │  │  :3306  │     │
│  └─────────┘  │  └──────────┘  └─────────┘     │
│                │                                  │
└────────────────┼──────────────────────────────────┘
                 │
        ┌────────┴────────┐
        │ Java 应用 :8080 │
        │ (宿主机运行)     │
        └─────────────────┘
```

---

## 常见操作

### 查看 Topic 列表

```bash
docker exec kafka1 kafka-topics --bootstrap-server kafka1:29092 --list
```

### 创建 Topic

```bash
docker exec kafka1 kafka-topics --bootstrap-server kafka1:29092 \
  --create --topic ecrh.shot.meta \
  --partitions 3 --replication-factor 3
```

### 查看消费者组

```bash
docker exec kafka1 kafka-consumer-groups --bootstrap-server kafka1:29092 --list
```

### 重置消费偏移量

```bash
docker exec kafka1 kafka-consumer-groups --bootstrap-server kafka1:29092 \
  --group ecrh-pipeline --reset-offsets --to-earliest --all-topics --execute
```
