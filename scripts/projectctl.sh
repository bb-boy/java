#!/usr/bin/env bash
# projectctl.sh - 一键启动/停止/重启/查看项目状态
# 位置: scripts/projectctl.sh
# 用法: ./scripts/projectctl.sh start|stop|restart|status [--mode network|file]

set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DOCKER_DIR="$ROOT_DIR/docker"
JAR_FILE="$ROOT_DIR/target/kafka-demo-1.0.0.jar"
LOG_DIR="$ROOT_DIR/logs"
RUN_DIR="$ROOT_DIR/run"
APP_PID_FILE="$RUN_DIR/app.pid"
APP_LOG_FILE="$LOG_DIR/app.log"
WATCHER_PID_FILE="$RUN_DIR/watcher.pid"
MODE="file" # default (changed to use local file source by default)

mkdir -p "$LOG_DIR" "$RUN_DIR"

usage() {
  cat <<EOF
Usage: $0 <start|stop|restart|status> [--mode network|file]

Commands:
  start    Start the project (docker, app, watcher if applicable)
  stop     Stop the project (app, watcher, docker)
  restart  Restart (stop then start)
  status   Show status of services

Options:
  --mode  network (default) or file - sets app data source mode

EOF
}

parse_args() {
  if [ $# -lt 1 ]; then
    usage; exit 1
  fi
  CMD="$1"; shift
  while [ $# -gt 0 ]; do
    case "$1" in
      --mode)
        MODE="$2"; shift 2;;
      --mode=*) MODE="${1#*=}"; shift;;
      -h|--help) usage; exit 0;;
      *) echo "Unknown arg: $1"; usage; exit 1;;
    esac
  done
}

start_docker() {
  if ! command -v docker &> /dev/null || ! command -v docker-compose &> /dev/null; then
    echo "Docker or docker-compose not available: skipping docker start"
    return
  fi
  echo "Starting Docker services (docker-compose up -d)..."
  (cd "$DOCKER_DIR" && docker-compose up -d)
}

stop_docker() {
  if ! command -v docker &> /dev/null || ! command -v docker-compose &> /dev/null; then
    echo "Docker or docker-compose not available: skipping docker stop"
    return
  fi
  echo "Stopping Docker services (docker-compose down)..."
  (cd "$DOCKER_DIR" && docker-compose down)
}

build_jar() {
  if [ ! -f "$JAR_FILE" ]; then
    echo "JAR not found, building with Maven..."
    (cd "$ROOT_DIR" && mvn clean package -DskipTests)
  fi
}

start_app() {
  if [ -f "$APP_PID_FILE" ] && kill -0 "$(cat "$APP_PID_FILE")" 2>/dev/null; then
    echo "App already running with PID $(cat "$APP_PID_FILE")"
    return
  fi
  build_jar
  echo "Starting Java application (mode=$MODE)..."
  nohup java -jar "$JAR_FILE" --app.data.source.primary="$MODE" --spring.profiles.active=dev > "$APP_LOG_FILE" 2>&1 &
  echo $! > "$APP_PID_FILE"
  echo "App started (PID $(cat "$APP_PID_FILE")), logs: $APP_LOG_FILE"
}

stop_app() {
  if [ -f "$APP_PID_FILE" ]; then
    PID=$(cat "$APP_PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "Stopping app PID $PID"
      kill "$PID"
      # wait up to 10s
      for i in {1..10}; do
        if kill -0 "$PID" 2>/dev/null; then
          sleep 1
        else
          break
        fi
      done
      if kill -0 "$PID" 2>/dev/null; then
        echo "Force killing $PID"
        kill -9 "$PID" || true
      fi
    else
      echo "No process $PID, removing stale pid file"
    fi
    rm -f "$APP_PID_FILE"
  else
    echo "No app pid file ($APP_PID_FILE) found"
  fi
}

start_watcher() {
  # If there is a watch_tdms.py script, start it in background for 'file' mode
  if [ "$MODE" = "file" ]; then
    if [ -f "$ROOT_DIR/data/watch_tdms.py" ]; then
      if [ -f "$WATCHER_PID_FILE" ] && kill -0 "$(cat "$WATCHER_PID_FILE")" 2>/dev/null; then
        echo "Watcher already running (PID $(cat "$WATCHER_PID_FILE"))"
      else
        echo "Starting TDMS watcher..."
        nohup python3 "$ROOT_DIR/data/watch_tdms.py" > "$LOG_DIR/watcher.log" 2>&1 &
        echo $! > "$WATCHER_PID_FILE"
        echo "Watcher started (PID $(cat "$WATCHER_PID_FILE"))"
      fi
    else
      echo "Watcher script not found, skipping"
    fi
  fi
}

stop_watcher() {
  if [ -f "$WATCHER_PID_FILE" ]; then
    PID=$(cat "$WATCHER_PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "Stopping watcher PID $PID"
      kill "$PID"
      sleep 1
    fi
    rm -f "$WATCHER_PID_FILE"
  fi
}

status() {
  echo "==== Docker Compose Services (if available) ===="
  if command -v docker-compose &> /dev/null; then
    (cd "$DOCKER_DIR" && docker-compose ps)
  else
    echo "docker-compose not found"
  fi
  echo "\n==== Java App ===="
  if [ -f "$APP_PID_FILE" ] && kill -0 "$(cat "$APP_PID_FILE")" 2>/dev/null; then
    echo "App running: PID $(cat "$APP_PID_FILE"), tail of log:"
    tail -n 20 "$APP_LOG_FILE" || true
  else
    echo "App not running"
  fi
  echo "\n==== Watcher (file mode) ===="
  if [ -f "$WATCHER_PID_FILE" ] && kill -0 "$(cat "$WATCHER_PID_FILE")" 2>/dev/null; then
    echo "Watcher running: PID $(cat "$WATCHER_PID_FILE")"
  else
    echo "Watcher not running"
  fi
}

# Main
parse_args "$@"
case "$CMD" in
  start)
    echo "Starting project (mode=$MODE)"
    start_docker
    if [ "$MODE" = "network" ]; then
      start_app
    else
      start_watcher
      start_app
    fi
    ;;
  stop)
    echo "Stopping project"
    stop_app
    stop_watcher
    stop_docker
    ;;
  restart)
    "$0" stop --mode="$MODE"
    sleep 1
    "$0" start --mode="$MODE"
    ;;
  status)
    status
    ;;
  *)
    usage; exit 1
    ;;
esac
