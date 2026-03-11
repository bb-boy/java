"""Channel naming and process ID mapping."""

from __future__ import annotations

import re

from .constants import TUBE_PROCESS_IDS
from .utils import extract_ascii_tokens, hash_key

WATER_LABEL_MAP = (
    ("金刚石窗", "DIAMOND_WINDOW"),
    ("主窗口", "MAIN_WINDOW"),
    ("收集极", "COLLECTOR"),
    ("小负载", "SMALL_LOAD"),
    ("过渡段", "TRANSITION"),
    ("长负载", "LONG_LOAD"),
    ("极化器", "POLARIZER"),
    ("滤波器", "FILTER"),
    ("波导", "WAVEGUIDE"),
    ("阳极", "ANODE"),
    ("镜子", "MIRROR"),
    ("管体", "TUBE_BODY"),
    ("弯头", "ELBOW"),
    ("器件", "DEVICE"),
    ("负载", "LOAD"),
    ("光栅", "GRATING"),
    ("备用", "SPARE"),
    ("俄大", "RUS"),
    ("大负载", "LOAD"),
    ("金刚石", "DIAMOND"),
)


def resolve_process_id(channel_name: str, data_type: str) -> str:
    if data_type == "TUBE":
        return TUBE_PROCESS_IDS.get(channel_name, f"ECRH:TUBE:{fallback_token(channel_name)}")
    return build_water_process_id(channel_name)


def build_water_process_id(channel_name: str) -> str:
    prefix = extract_prefix_number(channel_name)
    temp_token = extract_temp_token(channel_name)
    direction = extract_direction(channel_name)
    label = normalize_water_label(channel_name)
    tokens = [token for token in (prefix, label, temp_token, direction) if token]
    return f"ECRH:COOLING:TEMP_{'_'.join(tokens)}"


def extract_prefix_number(channel_name: str) -> str | None:
    match = re.match(r"\s*(\d+)", channel_name)
    if match is None:
        return None
    return match.group(1)


def extract_temp_token(channel_name: str) -> str | None:
    match = re.search(r"T\s*(\d+)", channel_name, re.IGNORECASE)
    if match is None:
        return None
    return f"T{match.group(1)}"


def extract_direction(channel_name: str) -> str | None:
    if "进" in channel_name:
        return "INLET"
    if "回" in channel_name:
        return "RETURN"
    return None


def normalize_water_label(channel_name: str) -> str:
    cleaned = re.sub(r"\s+", "", channel_name)
    cleaned = re.sub(r"^\d+", "", cleaned)
    cleaned = re.sub(r"T\s*\d+", "", cleaned, flags=re.IGNORECASE)
    cleaned = cleaned.replace("进", "").replace("回", "")
    translated = replace_known_labels(cleaned)
    tokens = extract_ascii_tokens(translated)
    if tokens:
        return "_".join(tokens)
    return f"RAW_{hash_key([channel_name])[:8].upper()}"


def replace_known_labels(value: str) -> str:
    translated = value
    for source, target in WATER_LABEL_MAP:
        translated = translated.replace(source, f" {target} ")
    translated = translated.replace("MOU", " MOU ")
    translated = translated.replace("tankload", " TANKLOAD ")
    translated = translated.replace("TAPER", " TAPER ")
    return translated


def fallback_token(channel_name: str) -> str:
    tokens = extract_ascii_tokens(channel_name)
    if tokens:
        return "_".join(tokens)
    return f"RAW_{hash_key([channel_name])[:10].upper()}"
