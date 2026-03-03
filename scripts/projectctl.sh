#!/usr/bin/env bash
# projectctl.sh - 一键启动/停止/重启/查看项目状态
# 位置: scripts/projectctl.sh
# 用法: ./scripts/projectctl.sh start|stop|restart|status [--watch]

set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DOCKER_DIR="$ROOT_DIR/docker"
JAR_FILE="$ROOT_DIR/target/kafka-demo-1.0.0.jar"
LOG_DIR="$ROOT_DIR/logs"
RUN_DIR="$ROOT_DIR/run"
APP_PID_FILE="$RUN_DIR/app.pid"
APP_LOG_FILE="$LOG_DIR/app.log"
WATCHER_PID_FILE="$RUN_DIR/watcher.pid"
WATCHER_ENABLED=false
COMPOSE_CMD=()
KAFKA_IMAGE="confluentinc/cp-kafka:8.1.1"
CLUSTER_ID_FILE="$RUN_DIR/cluster_id"
DEFAULT_MYSQL_ROOT_PASSWORD="devroot"
DEFAULT_MYSQL_DATABASE="wavedb"
DEFAULT_MYSQL_USER="wavedb"
DEFAULT_MYSQL_PASSWORD="wavedb123"
KAFKA_DATA_DIRS=(
  "$DOCKER_DIR/data/kafka1"
  "$DOCKER_DIR/data/kafka2"
  "$DOCKER_DIR/data/kafka3"
)

APP_START_GRACE_SECONDS=2
WATCHER_START_GRACE_SECONDS=2
STOP_WAIT_SECONDS=10
TAIL_LINES=50

mkdir -p "$LOG_DIR" "$RUN_DIR"

usage() {
  cat <<EOF
Usage: $0 <start|stop|restart|status> [--watch]

Commands:
  start    Start the project (docker, app, optional watcher)
  stop     Stop the project (app, watcher, docker)
  restart  Restart (stop then start)
  status   Show status of services

Options:
  --watch  Start TDMS watcher to auto-trigger Kafka sync

EOF
}

parse_args() {
  if [ $# -lt 1 ]; then
    usage; exit 1
  fi
  CMD="$1"; shift
  while [ $# -gt 0 ]; do
    case "$1" in
      --watch)
        WATCHER_ENABLED=true; shift;;
      -h|--help) usage; exit 0;;
      *) echo "Unknown arg: $1"; usage; exit 1;;
    esac
  done
}

detect_compose_cmd() {
  if command -v docker &> /dev/null && docker compose version &> /dev/null; then
    COMPOSE_CMD=("docker" "compose")
    return 0
  fi
  if command -v docker-compose &> /dev/null; then
    echo "Unsupported docker-compose v1 detected. Please install Docker Compose v2 (docker compose)." >&2
    return 1
  fi
  COMPOSE_CMD=()
  return 1
}

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" &> /dev/null; then
    echo "Required command not found: $cmd"
    return 1
  fi
}

generate_cluster_id() {
  require_cmd docker
  echo "Generating Kafka KRaft CLUSTER_ID using $KAFKA_IMAGE..." >&2
  docker run --rm "$KAFKA_IMAGE" kafka-storage random-uuid
}

read_existing_cluster_id() {
  local found_id=""
  local dir
  for dir in "${KAFKA_DATA_DIRS[@]}"; do
    if [ -f "$dir/meta.properties" ]; then
      local id
      id="$(grep -m 1 "^cluster.id=" "$dir/meta.properties" | cut -d= -f2)"
      if [ -z "$id" ]; then
        continue
      fi
      if [ -z "$found_id" ]; then
        found_id="$id"
      elif [ "$found_id" != "$id" ]; then
        echo "Detected inconsistent Kafka cluster IDs in $dir (expected $found_id, got $id)." >&2
        return 1
      fi
    fi
  done
  if [ -n "$found_id" ]; then
    echo "$found_id"
  fi
}

ensure_compose_env() {
  local existing_id
  existing_id="$(read_existing_cluster_id)" || return 1

  if [ -z "${CLUSTER_ID:-}" ]; then
    if [ -n "$existing_id" ]; then
      CLUSTER_ID="$existing_id"
      if [ ! -s "$CLUSTER_ID_FILE" ] || [ "$(cat "$CLUSTER_ID_FILE")" != "$existing_id" ]; then
        echo "$existing_id" > "$CLUSTER_ID_FILE"
        echo "Detected existing Kafka data; stored cluster id to $CLUSTER_ID_FILE"
      else
        echo "CLUSTER_ID not set; using stored value in $CLUSTER_ID_FILE"
      fi
    else
      if [ -s "$CLUSTER_ID_FILE" ]; then
        CLUSTER_ID="$(cat "$CLUSTER_ID_FILE")"
        echo "CLUSTER_ID not set; using stored value in $CLUSTER_ID_FILE"
      else
        CLUSTER_ID="$(generate_cluster_id)"
        echo "$CLUSTER_ID" > "$CLUSTER_ID_FILE"
        echo "CLUSTER_ID not set; generated and stored in $CLUSTER_ID_FILE"
      fi
    fi
    export CLUSTER_ID
  else
    if [ -n "$existing_id" ] && [ "$existing_id" != "$CLUSTER_ID" ]; then
      echo "CLUSTER_ID ($CLUSTER_ID) does not match existing Kafka data ($existing_id)." >&2
      echo "Delete $DOCKER_DIR/data/kafka* to reinitialize, or set CLUSTER_ID to $existing_id." >&2
      return 1
    fi
  fi

  if [ -z "${MYSQL_ROOT_PASSWORD:-}" ]; then
    export MYSQL_ROOT_PASSWORD="$DEFAULT_MYSQL_ROOT_PASSWORD"
    echo "MYSQL_ROOT_PASSWORD not set; using default (see README)"
  fi
  if [ -z "${MYSQL_DATABASE:-}" ]; then
    export MYSQL_DATABASE="$DEFAULT_MYSQL_DATABASE"
    echo "MYSQL_DATABASE not set; using default (see README)"
  fi
  if [ -z "${MYSQL_USER:-}" ]; then
    export MYSQL_USER="$DEFAULT_MYSQL_USER"
    echo "MYSQL_USER not set; using default (see README)"
  fi
  if [ -z "${MYSQL_PASSWORD:-}" ]; then
    export MYSQL_PASSWORD="$DEFAULT_MYSQL_PASSWORD"
    echo "MYSQL_PASSWORD not set; using default (see README)"
  fi
}

is_pid_running() {
  local pid="$1"
  kill -0 "$pid" 2>/dev/null
}

tail_log() {
  local file="$1"
  if [ -f "$file" ]; then
    tail -n "$TAIL_LINES" "$file"
  else
    echo "Log file not found: $file"
  fi
}

start_docker() {
  if ! detect_compose_cmd; then
    echo "Docker Compose v2 is required to start docker services." >&2
    return 1
  fi
  if [ ! -f "$DOCKER_DIR/docker-compose.yml" ]; then
    echo "docker-compose.yml not found in $DOCKER_DIR" >&2
    return 1
  fi
  ensure_compose_env
  echo "Starting Docker services (${COMPOSE_CMD[*]} up -d)..."
  (cd "$DOCKER_DIR" && "${COMPOSE_CMD[@]}" up -d)
}

stop_docker() {
  if ! detect_compose_cmd; then
    echo "Docker Compose v2 is required to stop docker services." >&2
    return 1
  fi
  if [ ! -f "$DOCKER_DIR/docker-compose.yml" ]; then
    echo "docker-compose.yml not found in $DOCKER_DIR" >&2
    return 1
  fi
  ensure_compose_env
  echo "Stopping Docker services (${COMPOSE_CMD[*]} down)..."
  (cd "$DOCKER_DIR" && "${COMPOSE_CMD[@]}" down)
}

build_jar() {
  if [ ! -f "$JAR_FILE" ]; then
    echo "JAR not found, building with Maven..."
    require_cmd mvn
    (cd "$ROOT_DIR" && mvn clean package -DskipTests)
  fi
}

start_app() {
  require_cmd java
  if [ -f "$APP_PID_FILE" ] && kill -0 "$(cat "$APP_PID_FILE")" 2>/dev/null; then
    echo "App already running with PID $(cat "$APP_PID_FILE")"
    return
  fi
  build_jar
  echo "Starting Java application..."
  nohup java -jar "$JAR_FILE" --spring.profiles.active=dev > "$APP_LOG_FILE" 2>&1 &
  local pid=$!
  echo "$pid" > "$APP_PID_FILE"
  sleep "$APP_START_GRACE_SECONDS"
  if ! is_pid_running "$pid"; then
    echo "App failed to start (PID $pid exited). Tail of log:"
    tail_log "$APP_LOG_FILE"
    rm -f "$APP_PID_FILE"
    return 1
  fi
  echo "App started (PID $pid), logs: $APP_LOG_FILE"
}

stop_app() {
  if [ -f "$APP_PID_FILE" ]; then
    PID=$(cat "$APP_PID_FILE")
    if kill -0 "$PID" 2>/dev/null; then
      echo "Stopping app PID $PID"
      kill "$PID"
      # wait up to STOP_WAIT_SECONDS
      for ((i=0; i<STOP_WAIT_SECONDS; i++)); do
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
  if [ -f "$ROOT_DIR/data/watch_tdms.py" ]; then
    require_cmd python3
    if [ -f "$WATCHER_PID_FILE" ] && kill -0 "$(cat "$WATCHER_PID_FILE")" 2>/dev/null; then
      echo "Watcher already running (PID $(cat "$WATCHER_PID_FILE"))"
    else
      echo "Starting TDMS watcher..."
      nohup python3 "$ROOT_DIR/data/watch_tdms.py" > "$LOG_DIR/watcher.log" 2>&1 &
      local pid=$!
      echo "$pid" > "$WATCHER_PID_FILE"
      sleep "$WATCHER_START_GRACE_SECONDS"
      if ! is_pid_running "$pid"; then
        echo "Watcher failed to start (PID $pid exited). Tail of log:"
        tail_log "$LOG_DIR/watcher.log"
        rm -f "$WATCHER_PID_FILE"
        return 1
      fi
      echo "Watcher started (PID $pid)"
    fi
  else
    echo "Watcher script not found, skipping"
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
  if [ ! -f "$DOCKER_DIR/docker-compose.yml" ]; then
    echo "docker-compose.yml not found in $DOCKER_DIR"
    return 1
  fi
  if ! detect_compose_cmd; then
    echo "Docker Compose v2 is required to show docker status." >&2
    return 1
  fi
  ensure_compose_env
  (cd "$DOCKER_DIR" && "${COMPOSE_CMD[@]}" ps)
  echo ""
  echo "==== Java App ===="
  if [ -f "$APP_PID_FILE" ]; then
    PID=$(cat "$APP_PID_FILE")
    if is_pid_running "$PID"; then
      echo "App running: PID $PID, tail of log:"
      tail -n "$TAIL_LINES" "$APP_LOG_FILE" || true
    else
      echo "App not running (stale pid $PID)"
    fi
  else
    echo "App not running"
  fi
  echo ""
  echo "==== Watcher ===="
  if [ -f "$WATCHER_PID_FILE" ]; then
    PID=$(cat "$WATCHER_PID_FILE")
    if is_pid_running "$PID"; then
      echo "Watcher running: PID $PID"
    else
      echo "Watcher not running (stale pid $PID)"
    fi
  else
    echo "Watcher not running"
  fi
}

# Main
parse_args "$@"
case "$CMD" in
  start)
    echo "Starting project"
    start_docker
    if [ "$WATCHER_ENABLED" = true ]; then
      start_watcher
    fi
    start_app
    ;;
  stop)
    echo "Stopping project"
    stop_app
    stop_watcher
    stop_docker
    ;;
  restart)
    "$0" stop
    sleep 1
    if [ "$WATCHER_ENABLED" = true ]; then
      "$0" start --watch
    else
      "$0" start
    fi
    ;;
  status)
    status
    ;;
  *)
    usage; exit 1
    ;;
esac
