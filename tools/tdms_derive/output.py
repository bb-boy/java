"""Derived output writers."""

from __future__ import annotations

import json
from dataclasses import asdict
from pathlib import Path
from typing import Iterable

from .constants import (
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
from .models import DerivedBundle, DerivedEvent, WaveformBatch


def resolve_output_dir(output_root: Path, shot_no: int) -> Path:
    output_dir = output_root / str(shot_no)
    output_dir.mkdir(parents=True, exist_ok=True)
    return output_dir


def write_bundle(bundle: DerivedBundle) -> None:
    write_json(bundle.output_dir / ARTIFACT_FILE, artifact_payload(bundle))
    write_json(bundle.output_dir / SHOT_META_FILE, asdict(bundle.shot_meta) if bundle.shot_meta else {})
    write_jsonl(bundle.output_dir / SIGNAL_CATALOG_FILE, bundle.signal_catalog)
    write_events(bundle.output_dir / OPERATION_EVENTS_FILE, bundle.operation_events)
    write_events(bundle.output_dir / PROTECTION_EVENTS_FILE, bundle.protection_events)
    write_json(bundle.output_dir / WAVEFORM_REQUEST_FILE, bundle.waveform_request or {})
    write_waveform_batches(bundle.output_dir / WAVEFORM_BATCH_FILE, bundle.waveform_batches)


def artifact_payload(bundle: DerivedBundle) -> dict:
    primary_ids = [artifact.artifact_id for artifact in bundle.artifacts if artifact.source_role == PRIMARY_SOURCE_ROLE]
    auxiliary_ids = [artifact.artifact_id for artifact in bundle.artifacts if artifact.source_role != PRIMARY_SOURCE_ROLE]
    return {
        "shot_no": bundle.shot_no,
        "source_system": SOURCE_SYSTEM,
        "authority_level": AUTHORITY_LEVEL,
        "primary_artifact_ids": primary_ids,
        "auxiliary_artifact_ids": auxiliary_ids,
        "artifacts": [asdict(artifact) for artifact in bundle.artifacts],
    }


def write_events(path: Path, events: Iterable[DerivedEvent]) -> None:
    write_jsonl(path, [asdict(event) for event in events])


def write_waveform_batches(path: Path, batches: Iterable[WaveformBatch]) -> None:
    write_jsonl(path, [asdict(batch) for batch in batches])


def write_json(path: Path, payload: dict) -> None:
    path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")


def write_jsonl(path: Path, rows: Iterable[dict]) -> None:
    lines = [json.dumps(row, ensure_ascii=False) for row in rows]
    path.write_text("\n".join(lines) + ("\n" if lines else ""), encoding="utf-8")
