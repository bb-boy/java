"""Randomized placeholder fields for provisional events."""

from __future__ import annotations

import random
from typing import Iterable

from .constants import (
    ACK_STATES,
    ACTION_LATENCY_MAX_US,
    ACTION_LATENCY_MIN_US,
    ACTION_TAKEN_CODES,
    COMMAND_NAMES,
    COMMAND_TARGETS,
    COMMAND_GAIN_MAX,
    COMMAND_GAIN_MIN,
    EXECUTION_STATUS_CODES,
    OPERATION_MODE_CODES,
    OPERATION_TASK_CODES,
    OPERATOR_IDS,
    PROTECTION_SCOPES,
)
from .utils import hash_key


def build_operation_fields(seed_parts: Iterable[str]) -> dict[str, object]:
    rng = seeded_random(seed_parts)
    return {
        "operation_mode_code": pick(rng, OPERATION_MODE_CODES),
        "operation_task_code": pick(rng, OPERATION_TASK_CODES),
        "command_name": pick(rng, COMMAND_NAMES),
        "command_params_json": {
            "target": pick(rng, COMMAND_TARGETS),
            "gain": round(rng.uniform(COMMAND_GAIN_MIN, COMMAND_GAIN_MAX), 3),
        },
        "operator_id": pick(rng, OPERATOR_IDS),
        "execution_status": pick(rng, EXECUTION_STATUS_CODES),
    }


def build_protection_fields(seed_parts: Iterable[str], event_time: str | None) -> dict[str, object]:
    rng = seeded_random(seed_parts)
    ack_state = pick(rng, ACK_STATES)
    return {
        "protection_scope": pick(rng, PROTECTION_SCOPES),
        "action_taken": pick(rng, ACTION_TAKEN_CODES),
        "action_latency_us": rng.randint(ACTION_LATENCY_MIN_US, ACTION_LATENCY_MAX_US),
        "ack_state": ack_state,
        "ack_user_id": "operator" if ack_state == "ACKED" else None,
        "ack_time": event_time if ack_state == "ACKED" else None,
    }


def seeded_random(parts: Iterable[str]) -> random.Random:
    seed_hex = hash_key([str(part) for part in parts])
    seed_value = int(seed_hex[:16], 16)
    return random.Random(seed_value)


def pick(rng: random.Random, options: Iterable[str]) -> str:
    items = tuple(options)
    if not items:
        raise ValueError("empty choice list")
    return items[rng.randrange(len(items))]
