#!/usr/bin/env python3
"""
TDMS 文件监控与补扫：
- 监控 TUBE 新文件，立即调用 tools/tdms_preprocessor.py 解析。
- 定时扫描 TUBE 与 derived，发现未解析则触发解析。
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from queue import Empty, Queue
from threading import Lock

from watchdog.events import FileSystemEventHandler
from watchdog.observers import Observer

TDMS_EXTENSION = ".tdms"
TDMS_INDEX_EXTENSION = ".tdms_index"
INDEX_MARKER = "_index"
SHOT_PATTERN = re.compile(r"^(\d+)")

DEFAULT_FILE_PATTERN = r".*_Tube\.tdms$"
DEFAULT_SCAN_INTERVAL_SECONDS = 600
DEFAULT_STABILITY_CHECKS = 2
DEFAULT_STABILITY_WAIT_MS = 100
DEFAULT_PYTHON_BIN = "python3"
DEFAULT_API_URL = "http://localhost:8080"
INGEST_TIMEOUT_SECONDS = 30
QUEUE_POLL_INTERVAL_SECONDS = 0.5

def resolve_repo_root() -> Path:
    return Path(__file__).resolve().parents[1]

def resolve_dir(base_dir: Path, raw_path: str) -> Path:
    path = Path(raw_path)
    if not path.is_absolute():
        path = (base_dir / path).resolve()
    return path

@dataclass(frozen=True)
class WatchConfig:
    watch_root: Path
    output_root: Path
    file_pattern: re.Pattern[str]
    scan_interval_seconds: int
    scan_now: bool
    scan_only: bool
    stability_checks: int
    stability_wait_ms: int
    recursive: bool
    python_bin: str
    preprocessor_path: Path
    api_url: str

class ShotQueue:
    def __init__(self) -> None:
        self._queue: Queue[int] = Queue()
        self._pending: set[int] = set()
        self._lock = Lock()

    def add(self, shot_no: int) -> None:
        with self._lock:
            if shot_no in self._pending:
                return
            self._pending.add(shot_no)
            self._queue.put(shot_no)

    def pop(self, timeout_seconds: float) -> int | None:
        try:
            shot_no = self._queue.get(timeout=timeout_seconds)
        except Empty:
            return None
        with self._lock:
            self._pending.discard(shot_no)
        return shot_no

class TdmsFileHandler(FileSystemEventHandler):
    def __init__(self, config: WatchConfig, queue: ShotQueue) -> None:
        self._config = config
        self._queue = queue

    def on_created(self, event) -> None:
        if event.is_directory:
            return
        self._handle_path(Path(event.src_path))

    def on_moved(self, event) -> None:
        if event.is_directory:
            return
        self._handle_path(Path(event.dest_path))

    def _handle_path(self, path: Path) -> None:
        if not is_candidate(path, self._config.file_pattern):
            return
        shot_no = extract_shot_no(path)
        if shot_no is None:
            print(f"[监控] 无法提取炮号: {path}")
            return
        print(f"[监控] 新文件: {path} -> shot {shot_no}")
        self._queue.add(shot_no)

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="TDMS 文件监控与补扫")
    parser.add_argument("--watch-root", default="TUBE", help="TUBE 根目录（相对 data/ 或绝对路径）")
    parser.add_argument("--output-root", default="derived", help="derived 根目录（相对 data/ 或绝对路径）")
    parser.add_argument("--file-pattern", default=DEFAULT_FILE_PATTERN, help="TDMS 文件名正则")
    parser.add_argument("--scan-interval-seconds", type=int, default=DEFAULT_SCAN_INTERVAL_SECONDS,
                        help="补扫周期（秒）")
    parser.add_argument("--scan-now", action="store_true", help="启动后立即补扫一次")
    parser.add_argument("--scan-only", action="store_true",
                        help="扫描一次缺失炮号并处理完毕后退出（不启动永久监控）")
    parser.add_argument("--stability-checks", type=int, default=DEFAULT_STABILITY_CHECKS,
                        help="稳定性检查次数")
    parser.add_argument("--stability-wait-ms", type=int, default=DEFAULT_STABILITY_WAIT_MS,
                        help="稳定性检查间隔（毫秒）")
    parser.add_argument("--recursive", action=argparse.BooleanOptionalAction, default=True,
                        help="递归监控子目录（默认开启，--no-recursive 关闭）")
    parser.add_argument("--python", default=DEFAULT_PYTHON_BIN, help="Python 可执行文件")
    parser.add_argument("--api-url", default=DEFAULT_API_URL, help="后端 API 地址")
    parser.add_argument("--watch-dir", default=None, help="兼容旧参数：等同 --watch-root")
    return parser

def build_config(args: argparse.Namespace) -> WatchConfig:
    base_dir = Path(__file__).resolve().parent
    watch_raw = args.watch_dir if args.watch_dir else args.watch_root
    watch_root = resolve_dir(base_dir, watch_raw)
    output_root = resolve_dir(base_dir, args.output_root)
    if not watch_root.is_dir():
        raise ValueError(f"watch root missing: {watch_root}")
    if args.scan_interval_seconds <= 0:
        raise ValueError("scan interval must be positive")
    if args.stability_checks <= 0:
        raise ValueError("stability checks must be positive")
    if args.stability_wait_ms <= 0:
        raise ValueError("stability wait must be positive")
    output_root.mkdir(parents=True, exist_ok=True)
    file_pattern = re.compile(args.file_pattern)
    repo_root = resolve_repo_root()
    preprocessor_path = repo_root / "tools" / "tdms_preprocessor.py"
    if not preprocessor_path.is_file():
        raise ValueError(f"preprocessor missing: {preprocessor_path}")
    return WatchConfig(
        watch_root=watch_root,
        output_root=output_root,
        file_pattern=file_pattern,
        scan_interval_seconds=args.scan_interval_seconds,
        scan_now=bool(args.scan_now),
        scan_only=bool(args.scan_only),
        stability_checks=args.stability_checks,
        stability_wait_ms=args.stability_wait_ms,
        recursive=bool(args.recursive),
        python_bin=args.python,
        preprocessor_path=preprocessor_path,
        api_url=args.api_url.rstrip("/"),
    )

def is_candidate(path: Path, pattern: re.Pattern[str]) -> bool:
    name = path.name
    lower = name.lower()
    if not lower.endswith(TDMS_EXTENSION):
        return False
    if lower.endswith(TDMS_INDEX_EXTENSION) or INDEX_MARKER in lower:
        return False
    return bool(pattern.match(name))

def extract_shot_no(path: Path) -> int | None:
    name_match = match_shot_no(path.name)
    if name_match is not None:
        return name_match
    parent = path.parent.name if path.parent else ""
    return match_shot_no(parent)

def match_shot_no(text: str) -> int | None:
    match = SHOT_PATTERN.match(text)
    if not match:
        return None
    return int(match.group(1))

def derived_exists(output_root: Path, shot_no: int) -> bool:
    from tdms_derive.constants import ARTIFACT_FILE

    return (output_root / str(shot_no) / ARTIFACT_FILE).is_file()

def discover_tube_shots(config: WatchConfig) -> set[int]:
    shots: set[int] = set()
    for path in config.watch_root.rglob(f"*{TDMS_EXTENSION}"):
        if not is_candidate(path, config.file_pattern):
            continue
        shot_no = extract_shot_no(path)
        if shot_no is None:
            continue
        shots.add(shot_no)
    return shots

def discover_derived_shots(output_root: Path) -> set[int]:
    from tdms_derive.constants import ARTIFACT_FILE

    if not output_root.is_dir():
        return set()
    shots: set[int] = set()
    for child in output_root.iterdir():
        if not child.is_dir():
            continue
        if not child.name.isdigit():
            continue
        if (child / ARTIFACT_FILE).is_file():
            shots.add(int(child.name))
    return shots

def enqueue_missing_shots(config: WatchConfig, queue: ShotQueue) -> None:
    tube_shots = discover_tube_shots(config)
    derived_shots = discover_derived_shots(config.output_root)
    missing = sorted(tube_shots - derived_shots)
    for shot_no in missing:
        print(f"[补扫] 发现未解析炮号: {shot_no}")
        queue.add(shot_no)

def run_preprocessor_cli(config: WatchConfig, shot_no: int) -> None:
    cmd = [
        config.python_bin,
        str(config.preprocessor_path),
        "--shot",
        str(shot_no),
        "--input-root",
        str(config.watch_root),
        "--output-root",
        str(config.output_root),
        "--stability-checks",
        str(config.stability_checks),
        "--stability-wait-ms",
        str(config.stability_wait_ms),
    ]
    print(f"[解析] 运行: {' '.join(cmd)}")
    subprocess.run(cmd, check=True)

def ingest_shot(config: WatchConfig, shot_no: int) -> bool:
    url = f"{config.api_url}/api/ingest/shot?shotNo={shot_no}"
    print(f"[同步] POST {url}")
    try:
        req = urllib.request.Request(url, method="POST", data=b"")
        with urllib.request.urlopen(req, timeout=INGEST_TIMEOUT_SECONDS) as resp:
            body = json.loads(resp.read().decode())
            print(f"[同步] 炮号 {shot_no} -> {body.get('status', 'ok')}")
            return True
    except urllib.error.HTTPError as exc:
        print(f"[同步失败] 炮号 {shot_no}: HTTP {exc.code}")
        return False
    except Exception as exc:  # noqa: BLE001
        print(f"[同步失败] 炮号 {shot_no}: {exc}")
        return False

def process_shot(config: WatchConfig, shot_no: int) -> None:
    if derived_exists(config.output_root, shot_no):
        print(f"[跳过] 已解析: {shot_no}，直接同步")
        ingest_shot(config, shot_no)
        return
    try:
        run_preprocessor_cli(config, shot_no)
        print(f"[完成] 解析完成: {shot_no}")
        ingest_shot(config, shot_no)
    except subprocess.CalledProcessError as exc:
        print(f"[错误] 炮号 {shot_no} 解析失败 (exit {exc.returncode})，跳过继续")
    except Exception as exc:  # noqa: BLE001
        print(f"[错误] 炮号 {shot_no} 意外错误: {exc}，跳过继续")

def run_loop(config: WatchConfig, queue: ShotQueue) -> None:
    next_scan = time.monotonic() + config.scan_interval_seconds
    while True:
        now = time.monotonic()
        if now >= next_scan:
            enqueue_missing_shots(config, queue)
            next_scan = now + config.scan_interval_seconds
        shot_no = queue.pop(QUEUE_POLL_INTERVAL_SECONDS)
        if shot_no is None:
            continue
        process_shot(config, shot_no)

def attach_import_path() -> None:
    repo_root = resolve_repo_root()
    tools_dir = repo_root / "tools"
    tools_path = str(tools_dir)
    if tools_path not in sys.path:
        sys.path.insert(0, tools_path)

def main() -> None:
    attach_import_path()
    parser = build_parser()
    args = parser.parse_args()
    config = build_config(args)

    print("=" * 60)
    print("TDMS 文件监控与补扫")
    print("=" * 60)
    print(f"监控目录: {config.watch_root}")
    print(f"派生目录: {config.output_root}")
    print(f"文件规则: {config.file_pattern.pattern}")
    print(f"补扫周期: {config.scan_interval_seconds} 秒")
    print(f"启动补扫: {'是' if config.scan_now else '否'}")
    print(f"一次性扫: {'是' if config.scan_only else '否'}")
    print(f"稳定检查: {config.stability_checks} 次 / {config.stability_wait_ms} ms")
    print(f"递归监控: {'是' if config.recursive else '否'}")
    print(f"API 地址: {config.api_url}")
    print("=" * 60)

    queue = ShotQueue()
    event_handler = TdmsFileHandler(config, queue)
    observer = Observer()
    observer.schedule(event_handler, str(config.watch_root), recursive=config.recursive)
    observer.start()
    print(f"[启动] 正在监控 {config.watch_root}")

    if config.scan_now:
        enqueue_missing_shots(config, queue)

    # scan-only 模式：处理完当前队列后直接退出，不做持久监控
    if args.scan_only:
        observer.stop()
        observer.join()
        print("[补扫] 开始一次性扫描...")
        enqueue_missing_shots(config, queue)
        while True:
            shot_no = queue.pop(timeout_seconds=0.1)
            if shot_no is None:
                break
            process_shot(config, shot_no)
        print("[补扫] 扫描完成，退出")
        return

    try:
        run_loop(config, queue)
    except KeyboardInterrupt:
        print("\n[停止] 收到中断信号，停止监控")
    finally:
        observer.stop()
        observer.join()

if __name__ == "__main__":
    main()
