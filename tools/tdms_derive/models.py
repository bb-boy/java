"""Data models for TDMS derived payload generation."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path
from typing import Any

import numpy as np


@dataclass(frozen=True, slots=True)
class ArtifactRecord:
    shot_no: int
    data_type: str
    artifact_id: str
    file_path: str
    file_name: str
    sha256_hex: str
    file_size: int
    file_mtime: str
    stable_checks: int
    group_name: str
    properties: dict[str, Any]
    channel_count: int
    source_role: str
    source_system: str
    authority_level: str
    artifact_status: str


@dataclass(frozen=True, slots=True)
class ChannelSeries:
    shot_no: int
    data_type: str
    artifact_id: str
    source_role: str
    group_name: str
    channel_name: str
    process_id: str
    unit: str | None
    ni_channel_name: str | None
    sample_interval_seconds: float
    sample_count: int
    declared_sample_count: int | None
    start_time: datetime
    start_offset_seconds: float | None
    values: np.ndarray
    file_path: str
    raw_properties: dict[str, Any]

    @property
    def sample_rate_hz(self) -> float:
        return 1.0 / self.sample_interval_seconds if self.sample_interval_seconds else 0.0

    @property
    def end_time(self) -> datetime:
        if self.sample_count <= 1:
            return self.start_time
        duration = self.sample_interval_seconds * (self.sample_count - 1)
        return self.start_time + timedelta(seconds=duration)

    def time_at(self, index: int) -> datetime:
        safe_index = min(max(index, 0), max(self.sample_count - 1, 0))
        duration = safe_index * self.sample_interval_seconds
        return self.start_time + timedelta(seconds=duration)


@dataclass(frozen=True, slots=True)
class SignalStats:
    baseline: float
    noise_sigma: float
    peak_abs: float
    rms: float
    raw_rms: float
    level_median: float
    start_mean: float
    end_mean: float
    dynamic_range: float


@dataclass(frozen=True, slots=True)
class ShotMeta:
    shot_no: int
    shot_start_time: str | None
    shot_end_time: str | None
    expected_duration_seconds: float | None
    actual_duration_seconds: float | None
    status_code: str
    status_reason: str
    reference_channels: list[str]
    window_source: str
    quality_flags: list[str]
    source_system: str
    authority_level: str


@dataclass(frozen=True, slots=True)
class DerivedEvent:
    event_family: str
    event_code: str
    source_system: str
    authority_level: str
    event_time: str
    shot_no: int
    artifact_id: str | None
    process_id: str
    channel_name: str
    message_text: str
    severity: str | None
    details: dict[str, Any]
    dedup_key: str


@dataclass(frozen=True, slots=True)
class ParsedArtifact:
    record: ArtifactRecord
    channels: tuple[ChannelSeries, ...]


@dataclass(frozen=True, slots=True)
class WaveformBatch:
    artifact_id: str
    shot_no: int
    data_type: str
    source_system: str
    channel_name: str
    process_id: str
    sample_rate_hz: float
    chunk_index: int
    chunk_count: int
    chunk_start_index: int
    window_start: str
    window_end: str
    encoding: str
    samples: list[float]


@dataclass(slots=True)
class DerivedBundle:
    shot_no: int
    output_dir: Path
    artifacts: list[ArtifactRecord] = field(default_factory=list)
    shot_meta: ShotMeta | None = None
    signal_catalog: list[dict[str, Any]] = field(default_factory=list)
    operation_events: list[DerivedEvent] = field(default_factory=list)
    protection_events: list[DerivedEvent] = field(default_factory=list)
    waveform_request: dict[str, Any] | None = None
    waveform_batches: list[WaveformBatch] = field(default_factory=list)
