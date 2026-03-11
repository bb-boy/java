"""Waveform batch derivation."""

from __future__ import annotations

import math
from typing import Iterable

from .constants import SOURCE_SYSTEM, WAVEFORM_BATCH_ENCODING, WAVEFORM_BATCH_SAMPLE_SIZE
from .models import ChannelSeries, WaveformBatch
from .utils import isoformat_utc


def build_waveform_batches(
    channels: Iterable[ChannelSeries],
    active_ranges: dict[str, tuple[int, int]],
) -> list[WaveformBatch]:
    batches: list[WaveformBatch] = []
    for channel in channels:
        start, end = resolve_range(channel, active_ranges)
        batches.extend(build_channel_batches(channel, start, end))
    return batches


def resolve_range(channel: ChannelSeries, active_ranges: dict[str, tuple[int, int]]) -> tuple[int, int]:
    if channel.channel_name in active_ranges:
        start, end = active_ranges[channel.channel_name]
    else:
        start, end = 0, max(channel.sample_count - 1, 0)
    if end < start:
        raise ValueError(f"invalid range for {channel.channel_name}: {start}-{end}")
    return start, end


def build_channel_batches(channel: ChannelSeries, start: int, end: int) -> list[WaveformBatch]:
    count = end - start + 1
    if count <= 0:
        raise ValueError(f"empty waveform for {channel.channel_name}")
    chunk_size = WAVEFORM_BATCH_SAMPLE_SIZE
    chunk_count = math.ceil(count / chunk_size)
    batches: list[WaveformBatch] = []
    for chunk_index in range(chunk_count):
        slice_start = start + chunk_index * chunk_size
        slice_end = min(start + (chunk_index + 1) * chunk_size - 1, end)
        samples = channel.values[slice_start:slice_end + 1].astype(float).tolist()
        batches.append(WaveformBatch(
            artifact_id=channel.artifact_id,
            shot_no=channel.shot_no,
            data_type=channel.data_type,
            source_system=SOURCE_SYSTEM,
            channel_name=channel.channel_name,
            process_id=channel.process_id,
            sample_rate_hz=channel.sample_rate_hz,
            chunk_index=chunk_index,
            chunk_count=chunk_count,
            chunk_start_index=slice_start,
            window_start=isoformat_utc(channel.time_at(slice_start)) or "",
            window_end=isoformat_utc(channel.time_at(slice_end)) or "",
            encoding=WAVEFORM_BATCH_ENCODING,
            samples=samples,
        ))
    return batches
