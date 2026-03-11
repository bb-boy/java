#!/usr/bin/env python3
"""Validate generated derived output files."""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from tdms_derive.constants import (
    ARTIFACT_FILE,
    AUTHORITY_LEVEL,
    OPERATION_EVENTS_FILE,
    PRIMARY_SOURCE_ROLE,
    PROTECTION_EVENTS_FILE,
    SHOT_META_FILE,
    SIGNAL_CATALOG_FILE,
    SOURCE_SYSTEM,
    WAVEFORM_BATCH_FILE,
    WAVEFORM_REQUEST_FILE,
)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Validate generated derived TDMS payloads.")
    parser.add_argument("--shot", type=int, required=True, help="Shot number to validate")
    parser.add_argument("--output-root", default="data/derived", help="Derived output root")
    return parser


def main() -> None:
    args = build_parser().parse_args()
    base_dir = Path(args.output_root) / str(args.shot)
    ensure_exists(base_dir / ARTIFACT_FILE)
    ensure_exists(base_dir / SHOT_META_FILE)
    ensure_exists(base_dir / SIGNAL_CATALOG_FILE)
    ensure_exists(base_dir / OPERATION_EVENTS_FILE)
    ensure_exists(base_dir / PROTECTION_EVENTS_FILE)
    ensure_exists(base_dir / WAVEFORM_REQUEST_FILE)
    ensure_exists(base_dir / WAVEFORM_BATCH_FILE)
    validate_artifact(base_dir / ARTIFACT_FILE, args.shot)
    validate_shot_meta(base_dir / SHOT_META_FILE, args.shot)
    validate_jsonl(base_dir / SIGNAL_CATALOG_FILE, require_markers=True)
    validate_jsonl(base_dir / OPERATION_EVENTS_FILE, require_markers=True, require_family="OPERATION")
    validate_jsonl(base_dir / PROTECTION_EVENTS_FILE, require_markers=True, require_family="PROTECTION")
    validate_waveform_request(base_dir / WAVEFORM_REQUEST_FILE, args.shot)
    validate_waveform_batches(base_dir / WAVEFORM_BATCH_FILE, args.shot)
    print("validation-ok")


def ensure_exists(path: Path) -> None:
    if not path.exists():
        raise FileNotFoundError(f"missing output file: {path}")


def validate_artifact(path: Path, shot_no: int) -> None:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("shot_no") != shot_no:
        raise ValueError("artifact shot_no mismatch")
    artifacts = payload.get("artifacts", [])
    if not artifacts:
        raise ValueError("artifact.json must contain at least one artifact")
    if not any(artifact.get("source_role") == PRIMARY_SOURCE_ROLE for artifact in artifacts):
        raise ValueError("artifact.json must contain a PRIMARY artifact")


def validate_shot_meta(path: Path, shot_no: int) -> None:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("shot_no") != shot_no:
        raise ValueError("shot_meta shot_no mismatch")
    if payload.get("source_system") != SOURCE_SYSTEM:
        raise ValueError("shot_meta source_system mismatch")
    if payload.get("authority_level") != AUTHORITY_LEVEL:
        raise ValueError("shot_meta authority_level mismatch")
    if not payload.get("window_source"):
        raise ValueError("shot_meta must include window_source")


def validate_waveform_request(path: Path, shot_no: int) -> None:
    payload = json.loads(path.read_text(encoding="utf-8"))
    if payload.get("shot_no") != shot_no:
        raise ValueError("waveform request shot_no mismatch")
    if payload.get("source_system") != SOURCE_SYSTEM:
        raise ValueError("waveform request source_system mismatch")
    signals = payload.get("signals")
    if not signals:
        raise ValueError("waveform request must contain signals")


def validate_waveform_batches(path: Path, shot_no: int) -> None:
    lines = [line for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not lines:
        raise ValueError("waveform batches must not be empty")
    for line in lines:
        payload = json.loads(line)
        if payload.get("shot_no") != shot_no:
            raise ValueError("waveform batch shot_no mismatch")
        if payload.get("source_system") != SOURCE_SYSTEM:
            raise ValueError("waveform batch source_system mismatch")
        if payload.get("chunk_start_index") is None:
            raise ValueError("waveform batch chunk_start_index missing")
        if payload.get("encoding") != "raw":
            raise ValueError("waveform batch encoding mismatch")
        samples = payload.get("samples")
        if not isinstance(samples, list) or not samples:
            raise ValueError("waveform batch samples missing")


def validate_jsonl(path: Path, require_markers: bool, require_family: str | None = None) -> None:
    lines = [line for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    if path.name == SIGNAL_CATALOG_FILE and not lines:
        raise ValueError("signal catalog must not be empty")
    for line in lines:
        payload = json.loads(line)
        if require_markers and payload.get("source_system") != SOURCE_SYSTEM:
            raise ValueError(f"marker mismatch in {path.name}")
        if require_markers and payload.get("authority_level") != AUTHORITY_LEVEL:
            raise ValueError(f"authority mismatch in {path.name}")
        if require_family and payload.get("event_family") != require_family:
            raise ValueError(f"event family mismatch in {path.name}")
        if path.name == SIGNAL_CATALOG_FILE and payload.get("sample_count", 0) <= 0:
            raise ValueError("signal catalog sample_count must be > 0")


if __name__ == "__main__":
    main()
