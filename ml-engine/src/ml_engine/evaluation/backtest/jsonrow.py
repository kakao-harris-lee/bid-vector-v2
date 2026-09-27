"""`ml_engine.evaluation.backtest.jsonrow` — 스냅숏 JSONL 한 줄을 읽는 원시 판독기.
`snapshot` 에서 분리한 이유는 파일 크기다(설계 래칫 500줄) — 타입 선언과 조립은 그쪽에
두고, 「키가 있는가·형이 맞는가」만 여기 모았다.

판독 규율은 하나다: **미지 키를 조용히 무시하지 않고 거부한다**(스키마 §7). 상호·
사업자번호가 스냅숏에 실려 오는 경로를 금지 목록 열거가 아니라 **허용 키 전수 대조**로
막는 자리이고, 키를 더하려면 두 레인이 같이 고쳐야 한다.

거부 사유는 **필드 이름만** 나른다 — 값도, 공고 식별자도 담지 않는다(D-6G-9).
"""

from __future__ import annotations

import json
from datetime import date, datetime
from enum import StrEnum
from math import isfinite

from ml_engine.registry.artifact import JsonValue


class SnapshotRejectionReason(StrEnum):
    MALFORMED_JSON = "MALFORMED_JSON"
    UNSUPPORTED_SCHEMA_VERSION = "UNSUPPORTED_SCHEMA_VERSION"
    MISSING_FIELD = "MISSING_FIELD"
    UNKNOWN_FIELD = "UNKNOWN_FIELD"
    INVALID_VALUE = "INVALID_VALUE"
    UNKNOWN_CATEGORY = "UNKNOWN_CATEGORY"
    DUPLICATE_NOTICE = "DUPLICATE_NOTICE"
    ROW_COUNT_MISMATCH = "ROW_COUNT_MISMATCH"
    CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH"
    EMPTY = "EMPTY"


class RowReadError(Exception):
    """판독 실패를 호출 경계까지 나르는 내부 신호 — `load_snapshot` 이 결과 타입으로
    바꾼다(예외가 public 표면으로 새지 않는다, v2-지침서.md §5)."""

    def __init__(self, reason: SnapshotRejectionReason, detail: str) -> None:
        super().__init__(detail)
        self.reason = reason
        self.detail = detail


def row_mapping(
    value: JsonValue, name: str, allowed: frozenset[str]
) -> dict[str, JsonValue]:
    if not isinstance(value, dict):
        raise RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, f"{name}: 매핑이 아님"
        )
    unknown = sorted(set(value) - allowed)
    if unknown:
        raise RowReadError(
            SnapshotRejectionReason.UNKNOWN_FIELD, f"{name}: 미지 키 {unknown}"
        )
    return value


def row_value(payload: dict[str, JsonValue], key: str) -> JsonValue:
    if key not in payload:
        raise RowReadError(SnapshotRejectionReason.MISSING_FIELD, key)
    return payload[key]


def row_number(payload: dict[str, JsonValue], key: str) -> float:
    value = row_value(payload, key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 수가 아님")
    if not isfinite(value):
        raise RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 비유한값")
    return float(value)


def row_optional_number(payload: dict[str, JsonValue], key: str) -> float | None:
    return None if row_value(payload, key) is None else row_number(payload, key)


def row_integer(payload: dict[str, JsonValue], key: str) -> int:
    value = row_value(payload, key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 정수가 아님")
    return value


def row_text(payload: dict[str, JsonValue], key: str) -> str:
    value = row_value(payload, key)
    if not isinstance(value, str):
        raise RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 문자열 아님")
    return value


def row_flag(payload: dict[str, JsonValue], key: str) -> bool:
    value = row_value(payload, key)
    if not isinstance(value, bool):
        raise RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 불리언 아님")
    return value


def row_optional_integer(payload: dict[str, JsonValue], key: str) -> int | None:
    return None if row_value(payload, key) is None else row_integer(payload, key)


def row_optional_text(payload: dict[str, JsonValue], key: str) -> str | None:
    return None if row_value(payload, key) is None else row_text(payload, key)


def row_optional_flag(payload: dict[str, JsonValue], key: str) -> bool | None:
    value = row_value(payload, key)
    if value is None:
        return None
    if not isinstance(value, bool):
        raise RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 불리언 아님")
    return value


def row_optional_timestamp(payload: dict[str, JsonValue], key: str) -> datetime | None:
    return None if row_value(payload, key) is None else row_timestamp(payload, key)


def row_date(payload: dict[str, JsonValue], key: str) -> date:
    try:
        return date.fromisoformat(row_text(payload, key))
    except ValueError as exc:
        raise RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, f"{key}: 날짜 형식 아님"
        ) from exc


def row_timestamp(payload: dict[str, JsonValue], key: str) -> datetime:
    try:
        return datetime.fromisoformat(row_text(payload, key))
    except ValueError as exc:
        raise RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, f"{key}: 시각 형식 아님"
        ) from exc


def reject_non_finite_constant(literal: str) -> float:
    """`NaN`/`Infinity` 는 JSON 이 아니다 — `json.loads` 기본값은 받아들이므로 막는다."""
    raise RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"비유한 상수: {literal}")


def decode_json(raw: bytes, name: str) -> JsonValue:
    try:
        decoded: JsonValue = json.loads(raw, parse_constant=reject_non_finite_constant)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise RowReadError(
            SnapshotRejectionReason.MALFORMED_JSON, f"{name}: JSON 아님"
        ) from exc
    return decoded
