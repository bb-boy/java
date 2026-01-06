#!/bin/bash
# 启动脚本 - 网络数据源模式

echo "=========================================="
echo "  波形数据展示系统 - 网络模式启动"
echo "=========================================="

# 检查Docker
if ! command -v docker &> /dev/null; then
    echo "警告: 未找到Docker,Kafka集群无法启动"
    echo "如果Kafka已在其他地方运行,请忽略此警告"
else
    echo "检查Kafka集群状态..."
    cd docker
    if docker-compose ps | grep -q "kafka"; then
        echo "Kafka集群已运行"
    else
        echo "启动Kafka集群..."
        docker-compose up -d
        sleep 10
    fi
    cd ..
fi

# 检查Java版本
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java,请先安装JDK 17+"
    exit 1
fi

# 检查JAR文件
JAR_FILE="target/kafka-demo-1.0.0.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "未找到JAR文件,开始编译..."
    mvn clean package -DskipTests
    if [ $? -ne 0 ]; then
        echo "编译失败!"
        exit 1
    fi
fi

# 启动应用
echo ""
echo "启动应用..."
echo "数据源模式: 网络(Kafka)"
echo "Kafka地址: localhost:19092,localhost:29092,localhost:39092"
echo "访问地址: http://localhost:8080"
echo ""
echo "提示: 使用 data_publisher.py 发送测试数据到Kafka"
echo ""

java -jar "$JAR_FILE" \
  --app.data.source.primary=network \
  --spring.profiles.active=dev

echo ""
echo "应用已停止"
