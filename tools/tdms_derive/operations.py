"""Operation event derivation."""

from __future__ import annotations

from datetime import datetime

import numpy as np

from .constants import (
    AUTHORITY_LEVEL,
    EDGE_GUARD_MS,
    EVENT_CODE_RAMP,
    EVENT_CODE_STEP,
    EVENT_FAMILY_OPERATION,
    OPERATION_RAMP_MIN_DELTA,
    OPERATION_STEP_MIN_DELTA,
    PRIMARY_OPERATION_CHANNELS,
    RAMP_MERGE_GAP,
    RAMP_MIN_DURATION_MS,
    RAMP_QUANTILE,
    RAMP_SIGMA_FACTOR,
    RAMP_SLOPE_WINDOW,
    SOURCE_SYSTEM,
    STEP_LEVEL_WINDOW,
    STEP_MERGE_GAP,
    STEP_PREPOST_WINDOW,
    STEP_QUANTILE,
    STEP_SIGMA_FACTOR,
    STEP_SMOOTH_WINDOW,
)
from .models import ChannelSeries, DerivedEvent, SignalStats
from .random_fields import build_operation_fields
from .utils import (
    clamp,
    group_sorted_indices,
    hash_key,
    isoformat_utc,
    median_filter,
    millis_to_samples,
    moving_average,
    quantile_threshold,
    robust_sigma,
    safe_mean,
)


def derive_operation_events(
    channels: list[ChannelSeries],
    stats_by_channel: dict[str, SignalStats],
) -> list[DerivedEvent]:
    events: list[DerivedEvent] = []
    for channel in channels:
        if channel.data_type != "TUBE" or channel.channel_name not in PRIMARY_OPERATION_CHANNELS:
            continue
        stats = stats_by_channel[channel.channel_name]
        filtered = median_filter(channel.values, STEP_SMOOTH_WINDOW)
        level = moving_average(filtered, min(channel.sample_count, STEP_LEVEL_WINDOW))
        events.extend(detect_step_events(channel, stats, level))
        events.extend(detect_ramp_events(channel, stats, filtered))
    return deduplicate(sort_events(events))


def detect_step_events(channel: ChannelSeries, stats: SignalStats, level: np.ndarray) -> list[DerivedEvent]:
    delta_level = np.diff(level, prepend=level[0])
    threshold = quantile_threshold(delta_level, STEP_QUANTILE)
    candidate_indices = np.where(np.abs(delta_level - np.nanmedian(delta_level)) > threshold)[0]
    level_sigma = robust_sigma(level)
    min_delta = max(OPERATION_STEP_MIN_DELTA.get(channel.channel_name, 0.0), level_sigma * STEP_SIGMA_FACTOR)
    events: list[DerivedEvent] = []
    for group in group_sorted_indices(candidate_indices, STEP_MERGE_GAP):
        event = build_step_event(channel, stats, level, group, min_delta, threshold, level_sigma)
        if event is not None:
            events.append(event)
    return events


def detect_ramp_events(channel: ChannelSeries, stats: SignalStats, filtered: np.ndarray) -> list[DerivedEvent]:
    slope = np.diff(filtered, prepend=filtered[0]) / max(channel.sample_interval_seconds, 1e-9)
    slope_smoothed = moving_average(slope, min(channel.sample_count, RAMP_SLOPE_WINDOW))
    threshold = quantile_threshold(slope_smoothed, RAMP_QUANTILE)
    candidate_indices = np.where(np.abs(slope_smoothed - np.nanmedian(slope_smoothed)) > threshold)[0]
    filtered_sigma = robust_sigma(filtered)
    min_delta = max(OPERATION_RAMP_MIN_DELTA.get(channel.channel_name, 0.0), filtered_sigma * RAMP_SIGMA_FACTOR)
    events: list[DerivedEvent] = []
    for group in group_sorted_indices(candidate_indices, RAMP_MERGE_GAP):
        event = build_ramp_event(channel, stats, filtered, group, min_delta, filtered_sigma)
        if event is not None:
            events.append(event)
    return events


def build_step_event(
    channel: ChannelSeries,
    stats: SignalStats,
    level: np.ndarray,
    group: np.ndarray,
    min_delta: float,
    threshold: float,
    level_sigma: float,
) -> DerivedEvent | None:
    center = int(group[len(group) // 2])
    if in_edge_guard(channel, center):
        return None
    event_time_value = channel.time_at(center)
    event_time_text = isoformat_utc(event_time_value) or ""
    before = level[max(0, center - STEP_PREPOST_WINDOW):center]
    after = level[center + 1:min(level.size, center + STEP_PREPOST_WINDOW + 1)]
    old_value = safe_mean(before)
    new_value = safe_mean(after)
    delta = new_value - old_value
    if abs(delta) < min_delta:
        return None
    confidence_sigma = abs(delta) / max(level_sigma, 1e-9)
    confidence = clamp(abs(delta) / max(threshold, min_delta, 1e-9), 0.55, 0.99)
    details = {
        "operation_type_code": EVENT_CODE_STEP,
        "channel_name": channel.channel_name,
        "old_value": old_value,
        "new_value": new_value,
        "delta_value": delta,
        "confidence": confidence,
        "confidence_sigma": confidence_sigma,
        "window_start": isoformat_utc(channel.time_at(max(0, center - STEP_PREPOST_WINDOW))),
        "window_end": isoformat_utc(channel.time_at(min(channel.sample_count - 1, center + STEP_PREPOST_WINDOW))),
        "sample_index": center,
    }
    seeded = build_operation_fields([str(channel.shot_no), channel.channel_name, EVENT_CODE_STEP, event_time_text])
    return create_event(channel, EVENT_CODE_STEP, event_time_value, old_value, new_value, {**details, **seeded})


def build_ramp_event(
    channel: ChannelSeries,
    stats: SignalStats,
    filtered: np.ndarray,
    group: np.ndarray,
    min_delta: float,
    filtered_sigma: float,
) -> DerivedEvent | None:
    start_index = int(group[0])
    end_index = int(group[-1])
    if in_edge_guard(channel, start_index) or in_edge_guard(channel, end_index):
        return None
    event_time_value = channel.time_at(start_index)
    event_time_text = isoformat_utc(event_time_value) or ""
    duration_ms = (end_index - start_index) * channel.sample_interval_seconds * 1000.0
    if duration_ms < RAMP_MIN_DURATION_MS:
        return None
    old_value = safe_mean(filtered[max(0, start_index - 1):start_index + 1])
    new_value = safe_mean(filtered[end_index:min(filtered.size, end_index + 2)])
    delta = new_value - old_value
    if abs(delta) < min_delta:
        return None
    confidence_sigma = abs(delta) / max(filtered_sigma, 1e-9)
    confidence = clamp(confidence_sigma / max(RAMP_SIGMA_FACTOR, 1e-9), 0.50, 0.98)
    details = {
        "operation_type_code": EVENT_CODE_RAMP,
        "channel_name": channel.channel_name,
        "old_value": old_value,
        "new_value": new_value,
        "delta_value": delta,
        "duration_ms": duration_ms,
        "confidence": confidence,
        "confidence_sigma": confidence_sigma,
        "window_start": isoformat_utc(channel.time_at(start_index)),
        "window_end": isoformat_utc(channel.time_at(end_index)),
        "start_index": start_index,
        "end_index": end_index,
    }
    seeded = build_operation_fields([str(channel.shot_no), channel.channel_name, EVENT_CODE_RAMP, event_time_text])
    return create_event(channel, EVENT_CODE_RAMP, event_time_value, old_value, new_value, {**details, **seeded})


def in_edge_guard(channel: ChannelSeries, index: int) -> bool:
    guard = millis_to_samples(EDGE_GUARD_MS, channel.sample_interval_seconds)
    return index < guard or index >= max(channel.sample_count - guard, 0)


def create_event(
    channel: ChannelSeries,
    event_code: str,
    event_time_value: datetime,
    old_value: float,
    new_value: float,
    details: dict[str, object],
) -> DerivedEvent:
    event_time_text = isoformat_utc(event_time_value) or ""
    dedup_key = hash_key([str(channel.shot_no), channel.channel_name, event_code, event_time_text])
    event_label = "step" if event_code == EVENT_CODE_STEP else "ramp"
    message = f"{channel.channel_name} {event_label} from {old_value:.6g} to {new_value:.6g}"
    return DerivedEvent(
        event_family=EVENT_FAMILY_OPERATION,
        event_code=event_code,
        source_system=SOURCE_SYSTEM,
        authority_level=AUTHORITY_LEVEL,
        event_time=event_time_text,
        shot_no=channel.shot_no,
        artifact_id=channel.artifact_id,
        process_id=channel.process_id,
        channel_name=channel.channel_name,
        message_text=message,
        severity=None,
        details=details,
        dedup_key=dedup_key,
    )


def sort_events(events: list[DerivedEvent]) -> list[DerivedEvent]:
    return sorted(events, key=lambda item: (item.event_time, item.channel_name, item.event_code))


def deduplicate(events: list[DerivedEvent]) -> list[DerivedEvent]:
    unique: dict[str, DerivedEvent] = {}
    for event in events:
        unique.setdefault(event.dedup_key, event)
    return list(unique.values())
