# 快速测试指南

## 前置条件
- Java 17+、Maven 3.6+
- MySQL 可用（默认 `jdbc:mysql://localhost:3306/wavedb`，用户 `root/devroot`，可用环境变量覆盖）
- 文件数据集已放在 `data/`，通道元数据可用 `python extract_channels.py --all` 生成到 `channels/`
- 如需网络模式：Kafka 已运行（`docker/docker-compose.yml` 可一键启动）

## 立即冒烟（3分钟）
```bash
cd /home/igusa/java
./run_tests.sh   # 构建 → 启动 → 调用核心 API
```

## 手动测试
```bash
cd /home/igusa/java
mvn clean package -DskipTests

# 文件源模式启动
java -jar target/kafka-demo-1.0.0.jar \
  --app.data.source.primary=file \
  --spring.profiles.active=dev

# 另一个终端手工验证
curl http://localhost:8080/api/data/shots
curl http://localhost:8080/api/data/shots/1/metadata | python3 -m json.tool
curl "http://localhost:8080/api/data/shots/1/wave?channel=NegVoltage&type=Tube" | head
```

## 网络数据源测试
```bash
# 启动依赖 (Kafka/InfluxDB/MySQL)
cd /home/igusa/java/docker
export CLUSTER_ID=$(uuidgen)
export MYSQL_ROOT_PASSWORD=devroot
export MYSQL_DATABASE=wavedb
export MYSQL_USER=wavedb
export MYSQL_PASSWORD=wavedb123
docker compose up -d

# 回到项目根目录后启动网络模式
cd ..
java -jar target/kafka-demo-1.0.0.jar --app.data.source.primary=network

# 发布示例数据到 Kafka
python data_publisher.py --shot 1 --kafka localhost:19092
curl http://localhost:8080/api/data/status
```

## WebSocket 验证
```bash
node -e "const ws=new(require('ws'))('ws://localhost:8080/ws');ws.on('message',m=>console.log(m.toString()));"
# 或在浏览器打开 src/main/resources/static/test.html
```

## 调试工具
- 日志：`tail -f /tmp/app.log`
- MySQL：默认库 `wavedb`，可用 `mysql -uroot -pdevroot` 登录
- JSON 美化：`curl ... | python3 -m json.tool`

## 性能/压力示例
```bash
ab -n 500 -c 20 http://localhost:8080/api/data/shots
```

## 常见问题
- **启动失败/连不上库**：确认 MySQL 端口、用户名密码，或用环境变量覆盖 `SPRING_DATASOURCE_*`。
- **通道列表不全**：先运行 `python extract_channels.py --all` 生成 `channels/*.json`。
- **端口占用**：`lsof -i :8080` 查占用后终止，或用 `--server.port=8081` 启动。

### 问题3：API返回404
```bash
# 检查应用是否正常启动
curl http://localhost:8080

# 查看日志
tail -50 /tmp/app.log | grep -i error
```

### 问题4：数据库错误
```bash
# 访问H2控制台验证数据库连接
curl http://localhost:8080/h2-console

# 查看数据库初始化日志
grep -i "hibernate\|jpa\|h2" /tmp/app.log | head -20
```

---

## 📋 测试检查清单

使用此清单确保所有功能都已测试：

- [ ] 环境检查（Java 17+, Maven 3.6+）
- [ ] 编译成功（mvn compile）
- [ ] 打包成功（69MB JAR文件）
- [ ] 应用启动（端口8080）
- [ ] 获取炮号列表（20个炮号）
- [ ] 获取元数据（时间、采样率等）
- [ ] 获取完整数据（元数据+日志）
- [ ] 获取通道列表（4个通道）
- [ ] 获取操作日志（15条）
- [ ] 系统状态接口（数据源信息）
- [ ] H2控制台（数据库查询）
- [ ] 日志输出（无ERROR）

---

## 📈 测试结果记录

**测试日期**: ____________  
**测试人员**: ____________  
**环境**: ____________  

| 测试项 | 结果 | 备注 |
|------|------|------|
| 编译 | ☐ 通过 | |
| 打包 | ☐ 通过 | |
| 启动 | ☐ 通过 | |
| API测试 | ☐ 通过 | |
| 数据库 | ☐ 通过 | |
| 性能 | ☐ 可接受 | |
| 日志 | ☐ 正常 | |

**总体评分**: ☐ 优秀  ☐ 良好  ☐ 及格  ☐ 失败

---

## 📞 获取帮助

- 📖 详细文档：[README.md](README.md)
- 🏗️ 架构说明：[ARCHITECTURE.md](ARCHITECTURE.md)
- 📊 测试报告：[TEST_REPORT.md](TEST_REPORT.md)
- 🔧 运行脚本：[run_tests.sh](run_tests.sh)

---

**祝测试顺利！** 🚀
