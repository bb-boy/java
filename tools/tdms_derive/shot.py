"""Shot metadata and signal catalog generation."""

from __future__ import annotations

from datetime import datetime
from math import isfinite

import numpy as np

from .constants import (
    ACTIVE_PEAK_RATIO,
    ACTIVE_SIGMA_FACTOR,
    ACTIVE_WINDOW_COVERAGE_RATIO,
    AUTHORITY_LEVEL,
    DEFAULT_VALUE_TYPE,
    EARLY_TERMINATION_TOLERANCE_RATIO,
    EARLY_TERMINATION_TOLERANCE_SECONDS,
    SHOT_REFERENCE_CHANNELS,
    SOURCE_SYSTEM,
)
from .models import ChannelSeries, ShotMeta, SignalStats
from .utils import isoformat_utc, median_filter, millis_to_samples, moving_average, robust_sigma, safe_mean


def build_shot_meta(
    shot_no: int,
    artifacts: list,
    channels: list[ChannelSeries],
) -> tuple[ShotMeta, dict[str, tuple[int, int]], dict[str, SignalStats]]:
    stats_by_channel = {channel.channel_name: compute_signal_stats(channel) for channel in channels}
    tube_channels = [channel for channel in channels if channel.data_type == "TUBE"]
    start_time, end_time, references, window_source, quality_flags = resolve_shot_bounds(tube_channels, stats_by_channel)
    ranges = build_channel_ranges(channels, start_time, end_time)
    expected_duration = infer_expected_duration(artifacts, resolve_reference_rate(tube_channels), duration_seconds(start_time, end_time))
    actual_duration = duration_seconds(start_time, end_time)
    status_code, status_reason = resolve_status(expected_duration, actual_duration)
    meta = ShotMeta(
        shot_no=shot_no,
        shot_start_time=isoformat_utc(start_time),
        shot_end_time=isoformat_utc(end_time),
        expected_duration_seconds=expected_duration,
        actual_duration_seconds=actual_duration,
        status_code=status_code,
        status_reason=status_reason,
        reference_channels=references,
        window_source=window_source,
        quality_flags=quality_flags,
        source_system=SOURCE_SYSTEM,
        authority_level=AUTHORITY_LEVEL,
    )
    return meta, ranges, stats_by_channel


def build_signal_catalog(channels: list[ChannelSeries], stats_by_channel: dict[str, SignalStats]) -> list[dict]:
    catalog: list[dict] = []
    for channel in channels:
        stats = stats_by_channel[channel.channel_name]
        catalog.append({
            "shot_no": channel.shot_no,
            "data_type": channel.data_type,
            "artifact_id": channel.artifact_id,
            "source_role": channel.source_role,
            "group_name": channel.group_name,
            "channel_name": channel.channel_name,
            "process_id": channel.process_id,
            "unit": channel.unit,
            "value_type": DEFAULT_VALUE_TYPE,
            "ni_channel_name": channel.ni_channel_name,
            "sample_interval_seconds": channel.sample_interval_seconds,
            "sample_rate_hz": channel.sample_rate_hz,
            "sample_count": channel.sample_count,
            "declared_sample_count": channel.declared_sample_count,
            "start_offset_seconds": channel.start_offset_seconds,
            "start_time": isoformat_utc(channel.start_time),
            "end_time": isoformat_utc(channel.end_time),
            "baseline": stats.baseline,
            "noise_sigma": stats.noise_sigma,
            "peak_abs": stats.peak_abs,
            "rms": stats.rms,
            "raw_rms": stats.raw_rms,
            "level_median": stats.level_median,
            "start_mean": stats.start_mean,
            "end_mean": stats.end_mean,
            "dynamic_range": stats.dynamic_range,
            "source_system": SOURCE_SYSTEM,
            "authority_level": AUTHORITY_LEVEL,
            "file_path": channel.file_path,
        })
    return catalog


def compute_signal_stats(channel: ChannelSeries) -> SignalStats:
    window = max(64, min(channel.sample_count // 20, 1_000))
    filtered = median_filter(channel.values, 5)
    trend = moving_average(filtered, max(5, min(channel.sample_count, 201)))
    residual = filtered - trend
    baseline = float(np.nanmedian(filtered[:window])) if window else 0.0
    centered = filtered - baseline
    dynamic_range = quantile_span(filtered)
    return SignalStats(
        baseline=baseline,
        noise_sigma=robust_sigma(residual),
        peak_abs=float(np.nanmax(np.abs(centered))) if centered.size else 0.0,
        rms=float(np.sqrt(np.nanmean(np.square(centered)))) if centered.size else 0.0,
        raw_rms=float(np.sqrt(np.nanmean(np.square(channel.values)))) if channel.values.size else 0.0,
        level_median=float(np.nanmedian(filtered)) if filtered.size else 0.0,
        start_mean=safe_mean(filtered[:window]),
        end_mean=safe_mean(filtered[-window:]),
        dynamic_range=dynamic_range,
    )


def quantile_span(values: np.ndarray) -> float:
    if values.size == 0:
        return 0.0
    high = float(np.nanquantile(values, 0.95))
    low = float(np.nanquantile(values, 0.05))
    return high - low


def resolve_shot_bounds(
    tube_channels: list[ChannelSeries],
    stats_by_channel: dict[str, SignalStats],
) -> tuple[datetime | None, datetime | None, list[str], str, list[str]]:
    references = [channel for channel in tube_channels if channel.channel_name in SHOT_REFERENCE_CHANNELS]
    if not references:
        start_time, end_time = file_span(tube_channels)
        return start_time, end_time, [], "tube_file_span", ["missing_reference_channels"]
    windows = collect_reference_windows(references, stats_by_channel)
    if should_use_file_span(references, windows):
        start_time, end_time = file_span(references)
        quality_flags = ["shot_window_from_file_span", "reference_window_hits_boundary"]
        names = [channel.channel_name for channel in references]
        return start_time, end_time, names, "tube_file_span", quality_flags
    start_time = min(reference.time_at(window[0]) for reference, window in windows)
    end_time = max(reference.time_at(window[1]) for reference, window in windows)
    names = [reference.channel_name for reference, _ in windows]
    return start_time, end_time, names, "tube_active_window", []


def file_span(channels: list[ChannelSeries]) -> tuple[datetime | None, datetime | None]:
    if not channels:
        return None, None
    return min(channel.start_time for channel in channels), max(channel.end_time for channel in channels)


def collect_reference_windows(
    references: list[ChannelSeries],
    stats_by_channel: dict[str, SignalStats],
) -> list[tuple[ChannelSeries, tuple[int, int]]]:
    windows: list[tuple[ChannelSeries, tuple[int, int]]] = []
    for channel in references:
        active = detect_active_window(channel, stats_by_channel[channel.channel_name])
        if active is not None:
            windows.append((channel, active))
    return windows


def detect_active_window(channel: ChannelSeries, stats: SignalStats) -> tuple[int, int] | None:
    filtered = moving_average(median_filter(channel.values, 5), max(5, millis_to_samples(20.0, channel.sample_interval_seconds)))
    centered = np.abs(filtered - stats.level_median)
    threshold = max(stats.noise_sigma * ACTIVE_SIGMA_FACTOR, stats.dynamic_range * ACTIVE_PEAK_RATIO)
    indices = np.where(centered > threshold)[0]
    if indices.size == 0:
        return None
    return int(indices[0]), int(indices[-1])


def should_use_file_span(
    references: list[ChannelSeries],
    windows: list[tuple[ChannelSeries, tuple[int, int]]],
) -> bool:
    if not references or len(windows) != len(references):
        return True
    for channel, window in windows:
        coverage = (window[1] - window[0] + 1) / max(channel.sample_count, 1)
        if coverage >= ACTIVE_WINDOW_COVERAGE_RATIO:
            return True
    return False


def build_channel_ranges(
    channels: list[ChannelSeries],
    start_time: datetime | None,
    end_time: datetime | None,
) -> dict[str, tuple[int, int]]:
    if start_time is None or end_time is None:
        return {}
    ranges: dict[str, tuple[int, int]] = {}
    for channel in channels:
        start_index = time_index(channel, start_time)
        end_index = time_index(channel, end_time)
        ranges[channel.channel_name] = (start_index, max(start_index, end_index))
    return ranges


def time_index(channel: ChannelSeries, value: datetime) -> int:
    offset = max(0.0, (value - channel.start_time).total_seconds())
    index = int(round(offset / channel.sample_interval_seconds))
    return min(max(index, 0), max(channel.sample_count - 1, 0))


def duration_seconds(start_time: datetime | None, end_time: datetime | None) -> float | None:
    if start_time is None or end_time is None:
        return None
    return max(0.0, (end_time - start_time).total_seconds())


def resolve_reference_rate(channels: list[ChannelSeries]) -> float:
    for name in SHOT_REFERENCE_CHANNELS:
        for channel in channels:
            if channel.channel_name == name:
                return channel.sample_rate_hz
    return channels[0].sample_rate_hz if channels else 1.0


def infer_expected_duration(artifacts: list, sample_rate_hz: float, actual_duration: float | None) -> float | None:
    values = [artifact.record.properties.get("IpLen") for artifact in artifacts if artifact.record.data_type == "TUBE"]
    if not values:
        return None
    raw = float(values[0])
    if not isfinite(raw) or raw <= 0.0:
        return None
    if actual_duration is not None and raw > actual_duration * 5.0:
        return raw / sample_rate_hz
    if raw > sample_rate_hz:
        return raw / sample_rate_hz
    return raw


def resolve_status(expected_duration: float | None, actual_duration: float | None) -> tuple[str, str]:
    if actual_duration is None:
        return "INCOMPLETE", "unable to resolve shot time window"
    if expected_duration is None:
        return "COMPLETE", "expected duration unavailable"
    tolerance = max(expected_duration * EARLY_TERMINATION_TOLERANCE_RATIO, EARLY_TERMINATION_TOLERANCE_SECONDS)
    if actual_duration < expected_duration - tolerance:
        return "INCOMPLETE", "actual duration is shorter than expected duration"
    return "COMPLETE", "actual duration is within expected tolerance"
