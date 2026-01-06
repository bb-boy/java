#!/bin/bash
# 启动脚本 - 文件数据源模式

echo "=========================================="
echo "  波形数据展示系统 - 启动中..."
echo "=========================================="

# 检查Java版本
if ! command -v java &> /dev/null; then
    echo "错误: 未找到Java,请先安装JDK 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "错误: Java版本过低,需要JDK 17+,当前版本: $JAVA_VERSION"
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
echo "数据源模式: 文件"
echo "访问地址: http://localhost:8080"
echo ""

java -jar "$JAR_FILE" \
  --app.data.source.primary=file \
  --spring.profiles.active=dev

echo ""
echo "应用已停止"
