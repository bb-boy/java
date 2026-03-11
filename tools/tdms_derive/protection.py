"""Protection event derivation."""

from __future__ import annotations

import numpy as np

from .constants import (
    COOLING_BASELINE_WINDOW_MS,
    COOLING_MIN_DELTA,
    COOLING_SIGMA_FACTOR,
    COOLING_SLOPE_WINDOW,
    COOLING_TOP_EVENTS,
    DROPOUT_FILTER_WINDOW,
    DROPOUT_HIGH_RATIO,
    DROPOUT_LOW_RATIO,
    DROPOUT_MIN_PLATEAU_MS,
    DROPOUT_WINDOW_MS,
    EDGE_GUARD_MS,
    EARLY_TERMINATION_TOLERANCE_RATIO,
    EARLY_TERMINATION_TOLERANCE_SECONDS,
    EVENT_CODE_COOLING,
    EVENT_CODE_DROPOUT,
    EVENT_CODE_EARLY_TERMINATION,
    EVENT_CODE_NO_WAVE,
    EVENT_CODE_PEAK_HIGH,
    NO_WAVE_MIN_DYNAMIC_RANGE,
    NO_WAVE_MIN_SIGNAL_LEVEL,
    PEAK_FILTER_WINDOW,
    PEAK_HIGH_SIGMA_THRESHOLD,
    PRIMARY_PROTECTION_CHANNELS,
    SEVERITY_TRIP,
    SEVERITY_WARN,
    SHOT_REFERENCE_CHANNELS,
)
from .models import ChannelSeries, DerivedEvent, ShotMeta, SignalStats
from .protection_common import create_protection_event, create_shot_event, parse_event_time
from .utils import clamp, isoformat_utc, median_filter, millis_to_samples, moving_average, robust_sigma


def derive_protection_events(
    channels: list[ChannelSeries],
    shot_meta: ShotMeta,
    active_ranges: dict[str, tuple[int, int]],
    stats_by_channel: dict[str, SignalStats],
) -> list[DerivedEvent]:
    tube_channels = [channel for channel in channels if channel.data_type == "TUBE"]
    channel_by_name = {channel.channel_name: channel for channel in tube_channels}
    events: list[DerivedEvent] = []
    no_wave = maybe_no_wave(tube_channels, shot_meta, stats_by_channel)
    if no_wave is not None:
        events.append(no_wave)
    early = maybe_early_termination(shot_meta)
    if early is not None:
        events.append(early)
    peak = maybe_peak_high(tube_channels, stats_by_channel)
    if peak is not None:
        events.append(peak)
    events.extend(dropout_events(channel_by_name, stats_by_channel))
    events.extend(cooling_events(channels, active_ranges, stats_by_channel))
    return sorted(events, key=lambda item: (item.event_time, item.channel_name, item.event_code))


def maybe_no_wave(
    tube_channels: list[ChannelSeries],
    shot_meta: ShotMeta,
    stats_by_channel: dict[str, SignalStats],
) -> DerivedEvent | None:
    candidates = [channel for channel in tube_channels if channel.channel_name in SHOT_REFERENCE_CHANNELS]
    if not candidates:
        return None
    levels = []
    for channel in candidates:
        stats = stats_by_channel[channel.channel_name]
        levels.append(max(abs(stats.level_median), stats.dynamic_range, stats.raw_rms))
    if max(levels) >= max(NO_WAVE_MIN_SIGNAL_LEVEL, NO_WAVE_MIN_DYNAMIC_RANGE):
        return None
    reference = candidates[0]
    details = {
        "protection_type_code": EVENT_CODE_NO_WAVE,
        "trigger_condition": "all reference channels remain below minimum signal level",
        "reference_levels": dict(zip([channel.channel_name for channel in candidates], levels, strict=False)),
        "minimum_signal_level": NO_WAVE_MIN_SIGNAL_LEVEL,
        "minimum_dynamic_range": NO_WAVE_MIN_DYNAMIC_RANGE,
    }
    event_time_value = parse_event_time(shot_meta.shot_start_time)
    if event_time_value is None:
        event_time_value = reference.start_time
    return create_protection_event(reference, EVENT_CODE_NO_WAVE, event_time_value, SEVERITY_TRIP, details)


def maybe_early_termination(shot_meta: ShotMeta) -> DerivedEvent | None:
    expected = shot_meta.expected_duration_seconds
    actual = shot_meta.actual_duration_seconds
    if expected is None or actual is None:
        return None
    tolerance = max(expected * EARLY_TERMINATION_TOLERANCE_RATIO, EARLY_TERMINATION_TOLERANCE_SECONDS)
    if actual >= expected - tolerance:
        return None
    details = {
        "protection_type_code": EVENT_CODE_EARLY_TERMINATION,
        "expected_duration_seconds": expected,
        "actual_duration_seconds": actual,
        "tolerance_seconds": tolerance,
        "window_source": shot_meta.window_source,
    }
    return create_shot_event(
        shot_meta.shot_no,
        EVENT_CODE_EARLY_TERMINATION,
        parse_event_time(shot_meta.shot_end_time),
        SEVERITY_TRIP,
        details,
    )


def maybe_peak_high(
    tube_channels: list[ChannelSeries],
    stats_by_channel: dict[str, SignalStats],
) -> DerivedEvent | None:
    scored: list[tuple[float, ChannelSeries, int, float]] = []
    for channel in tube_channels:
        if channel.channel_name not in PRIMARY_PROTECTION_CHANNELS:
            continue
        filtered = median_filter(channel.values, PEAK_FILTER_WINDOW)
        centered = np.abs(filtered - stats_by_channel[channel.channel_name].baseline)
        guard = millis_to_samples(EDGE_GUARD_MS, channel.sample_interval_seconds)
        if guard > 0 and centered.size > guard * 2:
            centered[:guard] = 0.0
            centered[-guard:] = 0.0
        score = float(np.nanmax(centered) / max(stats_by_channel[channel.channel_name].noise_sigma, 1e-9))
        index = int(np.nanargmax(centered))
        scored.append((score, channel, index, float(filtered[index])))
    if not scored:
        return None
    score, channel, index, measured_value = max(scored, key=lambda item: item[0])
    if score < PEAK_HIGH_SIGMA_THRESHOLD:
        return None
    details = {
        "protection_type_code": EVENT_CODE_PEAK_HIGH,
        "trigger_condition": f"abs(value-baseline) > {PEAK_HIGH_SIGMA_THRESHOLD:.1f} sigma",
        "measured_value": measured_value,
        "threshold_value": PEAK_HIGH_SIGMA_THRESHOLD,
        "threshold_op": ">",
        "evidence_score": clamp(score / (PEAK_HIGH_SIGMA_THRESHOLD * 2.0), 0.55, 0.99),
        "baseline_value": stats_by_channel[channel.channel_name].baseline,
        "sample_index": index,
    }
    return create_protection_event(channel, EVENT_CODE_PEAK_HIGH, channel.time_at(index), SEVERITY_WARN, details)


def dropout_events(
    channel_by_name: dict[str, ChannelSeries],
    stats_by_channel: dict[str, SignalStats],
) -> list[DerivedEvent]:
    events: list[DerivedEvent] = []
    for name in ("NegVoltage", "PosVoltage", "InPower"):
        channel = channel_by_name.get(name)
        if channel is None:
            continue
        event = maybe_dropout(channel, stats_by_channel[name])
        if event is not None:
            events.append(event)
    return events


def maybe_dropout(channel: ChannelSeries, stats: SignalStats) -> DerivedEvent | None:
    filtered = moving_average(median_filter(channel.values, DROPOUT_FILTER_WINDOW), DROPOUT_FILTER_WINDOW)
    centered = np.abs(filtered - stats.level_median)
    peak = float(np.nanmax(centered))
    if peak < max(stats.noise_sigma * PEAK_HIGH_SIGMA_THRESHOLD, NO_WAVE_MIN_SIGNAL_LEVEL):
        return None
    plateau_groups = group_plateaus(channel, centered, peak * DROPOUT_HIGH_RATIO)
    if not plateau_groups:
        return None
    last_plateau = plateau_groups[-1]
    search_end = min(channel.sample_count, last_plateau[1] + millis_to_samples(DROPOUT_WINDOW_MS, channel.sample_interval_seconds))
    low_slice = centered[last_plateau[1]:search_end]
    low_candidates = np.where(low_slice <= peak * DROPOUT_LOW_RATIO)[0]
    if low_candidates.size == 0:
        return None
    low_index = last_plateau[1] + int(low_candidates[0])
    details = {
        "protection_type_code": EVENT_CODE_DROPOUT,
        "trigger_condition": f"drop_ratio>{DROPOUT_HIGH_RATIO:.2f} within {DROPOUT_WINDOW_MS:.1f}ms",
        "window_start": isoformat_utc(channel.time_at(last_plateau[0])),
        "window_end": isoformat_utc(channel.time_at(low_index)),
        "plateau_level": float(np.nanmean(filtered[last_plateau[0]:last_plateau[1] + 1])),
        "low_level": float(filtered[low_index]),
        "evidence_score": clamp(peak / max(stats.noise_sigma * PEAK_HIGH_SIGMA_THRESHOLD, 1e-9), 0.55, 0.99),
        "related_channels": [channel.channel_name],
    }
    return create_protection_event(channel, EVENT_CODE_DROPOUT, channel.time_at(low_index), SEVERITY_TRIP, details)


def group_plateaus(channel: ChannelSeries, centered: np.ndarray, threshold: float) -> list[tuple[int, int]]:
    plateau_samples = millis_to_samples(DROPOUT_MIN_PLATEAU_MS, channel.sample_interval_seconds)
    groups: list[tuple[int, int]] = []
    active = np.where(centered >= threshold)[0]
    if active.size == 0:
        return groups
    start = prev = int(active[0])
    for value in active[1:]:
        current = int(value)
        if current - prev > 1:
            if prev - start + 1 >= plateau_samples:
                groups.append((start, prev))
            start = current
        prev = current
    if prev - start + 1 >= plateau_samples:
        groups.append((start, prev))
    return groups


def cooling_events(
    channels: list[ChannelSeries],
    active_ranges: dict[str, tuple[int, int]],
    stats_by_channel: dict[str, SignalStats],
) -> list[DerivedEvent]:
    events: list[tuple[float, DerivedEvent]] = []
    for channel in channels:
        if channel.data_type != "WATER":
            continue
        event = maybe_cooling_anomaly(channel, active_ranges.get(channel.channel_name), stats_by_channel[channel.channel_name])
        if event is not None:
            score = float(event.details.get("evidence_score", 0.0))
            events.append((score, event))
    events.sort(key=lambda item: item[0], reverse=True)
    return [event for _, event in events[:COOLING_TOP_EVENTS]]


def maybe_cooling_anomaly(
    channel: ChannelSeries,
    active_range: tuple[int, int] | None,
    stats: SignalStats,
) -> DerivedEvent | None:
    if active_range is None:
        return None
    baseline_count = min(channel.sample_count, millis_to_samples(COOLING_BASELINE_WINDOW_MS, channel.sample_interval_seconds))
    filtered = moving_average(median_filter(channel.values, 5), min(channel.sample_count, COOLING_SLOPE_WINDOW))
    baseline = float(np.nanmedian(filtered[:baseline_count])) if baseline_count else stats.level_median
    search_start = max(active_range[0], baseline_count)
    window = filtered[search_start:active_range[1] + 1]
    if window.size == 0:
        return None
    delta = float(np.nanmax(window) - baseline)
    baseline_noise = robust_sigma(np.diff(filtered[:baseline_count], prepend=filtered[0])) if baseline_count else stats.noise_sigma
    threshold = max(baseline_noise * COOLING_SIGMA_FACTOR, COOLING_MIN_DELTA)
    if delta <= threshold:
        return None
    peak_offset = int(np.nanargmax(window))
    peak_index = search_start + peak_offset
    slope_series = np.diff(moving_average(window, min(window.size, COOLING_SLOPE_WINDOW)), prepend=window[0])
    max_slope = float(np.nanmax(slope_series) / max(channel.sample_interval_seconds, 1e-9))
    evidence_score = clamp(delta / max(threshold * 2.0, 1e-9), 0.50, 0.99)
    details = {
        "protection_type_code": EVENT_CODE_COOLING,
        "trigger_condition": f"delta_temp>{threshold:.4f}",
        "baseline_value": baseline,
        "measured_value": float(filtered[peak_index]),
        "delta_value": delta,
        "max_slope_per_second": max_slope,
        "threshold_value": threshold,
        "threshold_op": ">",
        "window_start": isoformat_utc(channel.time_at(active_range[0])),
        "window_end": isoformat_utc(channel.time_at(active_range[1])),
        "evidence_score": evidence_score,
    }
    return create_protection_event(channel, EVENT_CODE_COOLING, channel.time_at(peak_index), SEVERITY_WARN, details)
