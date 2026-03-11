"""TDMS parsing helpers."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

import numpy as np
from nptdms import TdmsFile

from .constants import (
    ARTIFACT_STATUS,
    AUTHORITY_LEVEL,
    AUXILIARY_SOURCE_ROLE,
    PRIMARY_SOURCE_ROLE,
    SOURCE_SYSTEM,
)
from .models import ArtifactRecord, ChannelSeries, ParsedArtifact
from .naming import resolve_process_id
from .utils import isoformat_utc, sanitize_values, sha256_file, wait_for_stable_file


def read_tdms_artifact(
    shot_no: int,
    path: Path,
    data_type: str,
    stable_checks: int,
    stable_wait_ms: int,
) -> ParsedArtifact:
    size, mtime = wait_for_stable_file(path, stable_checks, stable_wait_ms)
    tdms = TdmsFile.read(path)
    groups = tdms.groups()
    if not groups:
        raise ValueError(f"TDMS file has no groups: {path}")
    group = groups[0]
    record = build_artifact_record(shot_no, path, data_type, tdms, group.name, size, mtime, stable_checks)
    channels = tuple(
        build_channel_series(
            shot_no=shot_no,
            path=path,
            data_type=data_type,
            group_name=group.name,
            artifact_id=record.artifact_id,
            source_role=record.source_role,
            channel=channel,
        )
        for channel in group.channels()
    )
    return ParsedArtifact(record=record, channels=channels)


def build_artifact_record(
    shot_no: int,
    path: Path,
    data_type: str,
    tdms: TdmsFile,
    group_name: str,
    file_size: int,
    file_mtime: float,
    stable_checks: int,
) -> ArtifactRecord:
    sha256_hex = sha256_file(path)
    artifact_id = f"tdms:{shot_no}:{data_type.lower()}:{sha256_hex[:12]}"
    file_time = datetime.fromtimestamp(file_mtime, tz=timezone.utc)
    return ArtifactRecord(
        shot_no=shot_no,
        data_type=data_type,
        artifact_id=artifact_id,
        file_path=str(path),
        file_name=path.name,
        sha256_hex=sha256_hex,
        file_size=file_size,
        file_mtime=isoformat_utc(file_time) or "",
        stable_checks=stable_checks,
        group_name=group_name,
        properties=stringify_properties(tdms.properties),
        channel_count=len(groupsafe_channels(tdms)),
        source_role=source_role_for(data_type),
        source_system=SOURCE_SYSTEM,
        authority_level=AUTHORITY_LEVEL,
        artifact_status=ARTIFACT_STATUS,
    )


def build_channel_series(
    shot_no: int,
    path: Path,
    data_type: str,
    group_name: str,
    artifact_id: str,
    source_role: str,
    channel: Any,
) -> ChannelSeries:
    props = channel.properties
    raw_values = sanitize_values(channel[:])
    declared_sample_count = optional_int(props.get("wf_samples"))
    return ChannelSeries(
        shot_no=shot_no,
        data_type=data_type,
        artifact_id=artifact_id,
        source_role=source_role,
        group_name=group_name,
        channel_name=channel.name,
        process_id=resolve_process_id(channel.name, data_type),
        unit=string_or_none(props.get("unit_string")),
        ni_channel_name=string_or_none(props.get("NI_ChannelName")),
        sample_interval_seconds=float(props["wf_increment"]),
        sample_count=int(raw_values.size),
        declared_sample_count=declared_sample_count,
        start_time=normalize_time(props["wf_start_time"]),
        start_offset_seconds=optional_float(props.get("wf_start_offset")),
        values=raw_values,
        file_path=str(path),
        raw_properties=stringify_properties(props),
    )


def source_role_for(data_type: str) -> str:
    return PRIMARY_SOURCE_ROLE if data_type == "TUBE" else AUXILIARY_SOURCE_ROLE


def groupsafe_channels(tdms: TdmsFile) -> list[Any]:
    channels: list[Any] = []
    for group in tdms.groups():
        channels.extend(group.channels())
    return channels


def normalize_time(value: Any) -> datetime:
    if isinstance(value, datetime):
        if value.tzinfo is None:
            return value.replace(tzinfo=timezone.utc)
        return value.astimezone(timezone.utc)
    if isinstance(value, np.datetime64):
        seconds = value.astype("datetime64[us]").astype("int64") / 1_000_000
        return datetime.fromtimestamp(seconds, tz=timezone.utc)
    if hasattr(value, "isoformat"):
        parsed = datetime.fromisoformat(str(value.isoformat()).replace("Z", "+00:00"))
        if parsed.tzinfo is None:
            return parsed.replace(tzinfo=timezone.utc)
        return parsed.astimezone(timezone.utc)
    origin = datetime(1904, 1, 1, tzinfo=timezone.utc)
    return origin + timedelta(seconds=float(value))


def stringify_properties(properties: dict[str, Any]) -> dict[str, Any]:
    return {str(key): stringify_value(value) for key, value in properties.items()}


def stringify_value(value: Any) -> Any:
    if isinstance(value, datetime):
        return isoformat_utc(normalize_time(value))
    if isinstance(value, (str, int, float, bool)) or value is None:
        return value
    return str(value)


def string_or_none(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def optional_float(value: Any) -> float | None:
    if value is None:
        return None
    return float(value)


def optional_int(value: Any) -> int | None:
    if value is None:
        return None
    return int(value)
