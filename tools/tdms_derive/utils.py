"""Utility helpers for TDMS derived payload generation."""

from __future__ import annotations

from datetime import datetime, timezone
from hashlib import sha256
from pathlib import Path
from time import sleep
from typing import Iterable

import numpy as np
from numpy.lib.stride_tricks import sliding_window_view


def isoformat_utc(value: datetime | None) -> str | None:
    if value is None:
        return None
    if value.tzinfo is None:
        value = value.replace(tzinfo=timezone.utc)
    return value.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def sha256_file(path: Path) -> str:
    digest = sha256()
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def wait_for_stable_file(path: Path, checks: int, wait_ms: int) -> tuple[int, float]:
    last_snapshot: tuple[int, float] | None = None
    stable_count = 0
    while stable_count < checks:
        stat = path.stat()
        snapshot = (stat.st_size, stat.st_mtime)
        stable_count = stable_count + 1 if snapshot == last_snapshot else 1
        last_snapshot = snapshot
        if stable_count < checks:
            sleep(wait_ms / 1000.0)
    return last_snapshot


def median_absolute_deviation(values: np.ndarray) -> float:
    if values.size == 0:
        return 0.0
    median = float(np.nanmedian(values))
    deviation = np.abs(values - median)
    return float(np.nanmedian(deviation))


def robust_sigma(values: np.ndarray) -> float:
    mad = median_absolute_deviation(values)
    if mad > 0.0:
        return 1.4826 * mad
    std = float(np.nanstd(values))
    return std if std > 0.0 else 1e-9


def sanitize_values(values: np.ndarray) -> np.ndarray:
    data = np.asarray(values, dtype=float)
    if np.isfinite(data).all():
        data.setflags(write=False)
        return data
    finite_mask = np.isfinite(data)
    if not finite_mask.any():
        raise ValueError("channel data has no finite samples")
    clean = data.copy()
    clean[~finite_mask] = float(np.nanmedian(clean[finite_mask]))
    clean.setflags(write=False)
    return clean


def median_filter(values: np.ndarray, window: int) -> np.ndarray:
    if window <= 1 or values.size < 3:
        return values.copy()
    size = min(window, values.size)
    if size % 2 == 0:
        size -= 1
    padded = np.pad(values, size // 2, mode="edge")
    windows = sliding_window_view(padded, size)
    return np.median(windows, axis=1)


def moving_average(values: np.ndarray, window: int) -> np.ndarray:
    if window <= 1 or values.size < 3:
        return values.copy()
    size = min(window, values.size)
    left = size // 2
    right = size - 1 - left
    padded = np.pad(values, (left, right), mode="edge")
    kernel = np.ones(size, dtype=float) / size
    return np.convolve(padded, kernel, mode="valid")


def group_sorted_indices(indices: np.ndarray, max_gap: int) -> list[np.ndarray]:
    if indices.size == 0:
        return []
    groups: list[list[int]] = [[int(indices[0])]]
    for value in indices[1:]:
        current = int(value)
        if current - groups[-1][-1] <= max_gap:
            groups[-1].append(current)
            continue
        groups.append([current])
    return [np.asarray(group, dtype=int) for group in groups]


def clamp(value: float, low: float, high: float) -> float:
    return max(low, min(high, value))


def millis_to_samples(window_ms: float, interval_seconds: float) -> int:
    if interval_seconds <= 0:
        return 1
    samples = int(round((window_ms / 1000.0) / interval_seconds))
    return max(1, samples)


def safe_mean(values: np.ndarray) -> float:
    if values.size == 0:
        return 0.0
    return float(np.nanmean(values))


def quantile_threshold(values: np.ndarray, quantile: float) -> float:
    centered = np.abs(values - np.nanmedian(values))
    if centered.size == 0:
        return 0.0
    return float(np.nanquantile(centered, quantile))


def extract_ascii_tokens(value: str) -> list[str]:
    tokens: list[str] = []
    current: list[str] = []
    for char in value:
        if char.isascii() and char.isalnum():
            current.append(char.upper())
            continue
        if current:
            tokens.append("".join(current))
            current.clear()
    if current:
        tokens.append("".join(current))
    return tokens


def hash_key(parts: Iterable[str]) -> str:
    joined = "|".join(parts)
    return sha256(joined.encode("utf-8")).hexdigest()
