"""Pipeline orchestration for TDMS derived payload generation."""

from __future__ import annotations

from pathlib import Path

from .constants import AUTHORITY_LEVEL, DEFAULT_CHANNEL_SCOPE, DEFAULT_INGEST_MODE, PRIMARY_SOURCE_ROLE, SOURCE_SYSTEM
from .models import DerivedBundle
from .operations import derive_operation_events
from .output import resolve_output_dir, write_bundle
from .parser import read_tdms_artifact
from .protection import derive_protection_events
from .shot import build_shot_meta, build_signal_catalog
from .waveform import build_waveform_batches


def run_preprocessor(
    shot_no: int,
    input_root: Path,
    output_root: Path,
    stable_checks: int,
    stable_wait_ms: int,
) -> DerivedBundle:
    artifacts = load_artifacts(shot_no, input_root, stable_checks, stable_wait_ms)
    channels = [channel for artifact in artifacts for channel in artifact.channels]
    output_dir = resolve_output_dir(output_root, shot_no)
    shot_meta, active_ranges, stats_by_channel = build_shot_meta(shot_no, artifacts, channels)
    bundle = DerivedBundle(shot_no=shot_no, output_dir=output_dir)
    bundle.artifacts = [artifact.record for artifact in artifacts]
    bundle.shot_meta = shot_meta
    bundle.signal_catalog = build_signal_catalog(channels, stats_by_channel)
    bundle.operation_events = derive_operation_events(channels, stats_by_channel)
    bundle.protection_events = derive_protection_events(channels, shot_meta, active_ranges, stats_by_channel)
    artifact_sha_by_id = {artifact.record.artifact_id: artifact.record.sha256_hex for artifact in artifacts}
    primary_sha = resolve_primary_sha256(artifacts)
    bundle.waveform_request = build_waveform_request(shot_no, channels, active_ranges, artifact_sha_by_id, primary_sha)
    bundle.waveform_batches = build_waveform_batches(channels, active_ranges)
    write_bundle(bundle)
    return bundle


def load_artifacts(shot_no: int, input_root: Path, stable_checks: int, stable_wait_ms: int):
    base_dir = input_root / str(shot_no)
    tube_path = base_dir / f"{shot_no}_Tube.tdms"
    water_path = base_dir / f"{shot_no}_Water.tdms"
    if not tube_path.exists():
        raise FileNotFoundError(f"missing tube file: {tube_path}")
    artifacts = [read_tdms_artifact(shot_no, tube_path, "TUBE", stable_checks, stable_wait_ms)]
    if water_path.exists():
        artifacts.append(read_tdms_artifact(shot_no, water_path, "WATER", stable_checks, stable_wait_ms))
    return artifacts


def build_waveform_request(
    shot_no: int,
    channels,
    active_ranges: dict[str, tuple[int, int]],
    artifact_sha_by_id: dict[str, str],
    primary_sha256: str | None,
) -> dict:
    signals = []
    for channel in channels:
        signals.append({
            "shot_no": shot_no,
            "artifact_id": channel.artifact_id,
            "source_role": channel.source_role,
            "data_type": channel.data_type,
            "channel_name": channel.channel_name,
            "process_id": channel.process_id,
            "file_path": channel.file_path,
            "sha256_hex": artifact_sha_by_id.get(channel.artifact_id),
            "sample_count": channel.sample_count,
            "declared_sample_count": channel.declared_sample_count,
            "sample_interval_seconds": channel.sample_interval_seconds,
            "start_time": channel.start_time.isoformat().replace("+00:00", "Z"),
            "end_time": channel.end_time.isoformat().replace("+00:00", "Z"),
            "shot_range": active_ranges.get(channel.channel_name),
        })
    return {
        "shot_no": shot_no,
        "request_id": f"waveform-ingest:{shot_no}",
        "measurement": "waveform",
        "source_system": SOURCE_SYSTEM,
        "authority_level": AUTHORITY_LEVEL,
        "sha256_hex": primary_sha256,
        "channel_scope": DEFAULT_CHANNEL_SCOPE,
        "ingest_mode": DEFAULT_INGEST_MODE,
        "signals": signals,
    }


def resolve_primary_sha256(artifacts) -> str | None:
    for artifact in artifacts:
        if artifact.record.source_role == PRIMARY_SOURCE_ROLE:
            return artifact.record.sha256_hex
    return artifacts[0].record.sha256_hex if artifacts else None
