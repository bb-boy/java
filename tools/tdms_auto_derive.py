#!/usr/bin/env python3
"""Watch TDMS arrivals and generate derived payloads using tools/tdms_derive."""

from __future__ import annotations

import argparse
import re
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

from tdms_derive.constants import ARTIFACT_FILE
from tdms_derive.pipeline import run_preprocessor

TDMS_EXTENSION = ".tdms"
TDMS_INDEX_EXTENSION = ".tdms_index"
INDEX_MARKER = "_index"
SHOT_PATTERN = re.compile(r"^(\d+)")
DEFAULT_WATCH_ROOT = "/data/tube"
DEFAULT_OUTPUT_ROOT = "/data/derived"
DEFAULT_FILE_PATTERN = r".*_Tube\.tdms$"
DEFAULT_SCAN_INTERVAL_MS = 2000
DEFAULT_STABILITY_CHECKS = 2
DEFAULT_STABILITY_WAIT_MS = 100
REQUEST_TIMEOUT_SECONDS = 60


@dataclass(frozen=True)
class WatchConfig:
    watch_root: Path
    output_root: Path
    file_pattern: re.Pattern[str]
    scan_interval_ms: int
    stability_checks: int
    stability_wait_ms: int
    sync_url: str | None
    run_once: bool


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Auto-derive TDMS files when they arrive.")
    parser.add_argument("--watch-root", default=DEFAULT_WATCH_ROOT, help="TDMS root directory")
    parser.add_argument("--output-root", default=DEFAULT_OUTPUT_ROOT, help="Derived output root")
    parser.add_argument("--file-pattern", default=DEFAULT_FILE_PATTERN, help="Regex for TDMS filenames")
    parser.add_argument("--scan-interval-ms", type=int, default=DEFAULT_SCAN_INTERVAL_MS, help="Scan interval in ms")
    parser.add_argument("--stability-checks", type=int, default=DEFAULT_STABILITY_CHECKS, help="Stable checks")
    parser.add_argument("--stability-wait-ms", type=int, default=DEFAULT_STABILITY_WAIT_MS, help="Stable wait in ms")
    parser.add_argument("--sync-url", default=None, help="Optional sync API endpoint")
    parser.add_argument("--once", action="store_true", help="Run a single scan and exit")
    return parser


def build_config(args: argparse.Namespace) -> WatchConfig:
    watch_root = Path(args.watch_root)
    if not watch_root.is_dir():
        raise ValueError(f"watch root missing: {watch_root}")
    if args.scan_interval_ms <= 0:
        raise ValueError("scan interval must be positive")
    if args.stability_checks <= 0:
        raise ValueError("stability checks must be positive")
    if args.stability_wait_ms <= 0:
        raise ValueError("stability wait must be positive")
    file_pattern = re.compile(args.file_pattern)
    sync_url = args.sync_url.strip() if isinstance(args.sync_url, str) and args.sync_url.strip() else None
    return WatchConfig(
        watch_root=watch_root,
        output_root=Path(args.output_root),
        file_pattern=file_pattern,
        scan_interval_ms=args.scan_interval_ms,
        stability_checks=args.stability_checks,
        stability_wait_ms=args.stability_wait_ms,
        sync_url=sync_url,
        run_once=bool(args.once),
    )


def scan_loop(config: WatchConfig) -> None:
    while True:
        scan_once(config)
        if config.run_once:
            return
        time.sleep(config.scan_interval_ms / 1000.0)


def scan_once(config: WatchConfig) -> None:
    shots = discover_shots(config.watch_root, config.file_pattern)
    for shot_no, tdms_path in shots.items():
        if derived_exists(config.output_root, shot_no):
            continue
        derive_shot(config, shot_no)
        if config.sync_url:
            sync_shot(config.sync_url, shot_no)


def discover_shots(root: Path, pattern: re.Pattern[str]) -> dict[int, Path]:
    shots: dict[int, Path] = {}
    for path in root.rglob(f"*{TDMS_EXTENSION}"):
        if not is_candidate(path, pattern):
            continue
        shot_no = extract_shot_no(path)
        if shot_no is None:
            continue
        shots.setdefault(shot_no, path)
    return shots


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
    return (output_root / str(shot_no) / ARTIFACT_FILE).is_file()


def derive_shot(config: WatchConfig, shot_no: int) -> None:
    run_preprocessor(
        shot_no=shot_no,
        input_root=config.watch_root,
        output_root=config.output_root,
        stable_checks=config.stability_checks,
        stable_wait_ms=config.stability_wait_ms,
    )


def sync_shot(sync_url: str, shot_no: int) -> None:
    url = f"{sync_url}?shotNo={shot_no}"
    request = urllib.request.Request(url, method="POST")
    try:
        with urllib.request.urlopen(request, timeout=REQUEST_TIMEOUT_SECONDS) as response:
            if response.status != 200:
                raise RuntimeError(f"sync failed: HTTP {response.status}")
    except urllib.error.HTTPError as exc:
        raise RuntimeError(f"sync failed: HTTP {exc.code}") from exc
    except urllib.error.URLError as exc:
        raise RuntimeError(f"sync failed: {exc.reason}") from exc


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    config = build_config(args)
    scan_loop(config)


if __name__ == "__main__":
    main()
