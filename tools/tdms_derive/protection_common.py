"""Shared helpers for provisional protection events."""

from __future__ import annotations

from datetime import datetime

from .constants import AUTHORITY_LEVEL, EVENT_FAMILY_PROTECTION, SOURCE_SYSTEM
from .models import ChannelSeries, DerivedEvent
from .random_fields import build_protection_fields
from .utils import hash_key, isoformat_utc


def create_protection_event(
    channel: ChannelSeries,
    event_code: str,
    event_time_value: datetime,
    severity: str,
    details: dict[str, object],
) -> DerivedEvent:
    event_time_text = isoformat_utc(event_time_value) or ""
    dedup_key = hash_key([str(channel.shot_no), channel.channel_name, event_code, event_time_text])
    extras = build_protection_fields([str(channel.shot_no), channel.channel_name, event_code], event_time_text)
    return DerivedEvent(
        event_family=EVENT_FAMILY_PROTECTION,
        event_code=event_code,
        source_system=SOURCE_SYSTEM,
        authority_level=AUTHORITY_LEVEL,
        event_time=event_time_text,
        shot_no=channel.shot_no,
        artifact_id=channel.artifact_id,
        process_id=channel.process_id,
        channel_name=channel.channel_name,
        message_text=f"{channel.channel_name} triggered {event_code}",
        severity=severity,
        details={**details, **extras},
        dedup_key=dedup_key,
    )


def create_shot_event(
    shot_no: int,
    event_code: str,
    event_time_value: datetime | None,
    severity: str,
    details: dict[str, object],
) -> DerivedEvent:
    event_time_text = isoformat_utc(event_time_value) or ""
    dedup_key = hash_key([str(shot_no), "SHOT", event_code, event_time_text])
    extras = build_protection_fields([str(shot_no), "SHOT", event_code], event_time_text)
    return DerivedEvent(
        event_family=EVENT_FAMILY_PROTECTION,
        event_code=event_code,
        source_system=SOURCE_SYSTEM,
        authority_level=AUTHORITY_LEVEL,
        event_time=event_time_text,
        shot_no=shot_no,
        artifact_id=None,
        process_id="ECRH:SHOT:DURATION",
        channel_name="SHOT",
        message_text=f"{event_code} detected for shot {shot_no}",
        severity=severity,
        details={**details, **extras},
        dedup_key=dedup_key,
    )


def parse_event_time(value: str | None) -> datetime | None:
    if value is None:
        return None
    return datetime.fromisoformat(value.replace("Z", "+00:00"))
