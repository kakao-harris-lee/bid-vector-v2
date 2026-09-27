"""`ml_engine.evaluation.backtest.snapshot` — 불변 스냅숏 판독(D-6G-2). 바이트 둘
(`manifest.json`·`rows.jsonl`)만 본다 — 파일 읽기는 `ml_engine.adapters` 의 몫이다(5C
`dataset_files` ↔ `training.dataset` 과 같은 갈래).

**한 행이 두 반쪽이다.** `NoticeObservation` 은 투찰 시점에 알 수 있는 것,
`OpeningOutcome` 은 개찰로 드러나는 것. 전략에 넘어가는 것은 앞쪽뿐이고 뒤쪽은 채점만
읽는다 — 누출 금지(위협 모델 ①)를 문서가 아니라 **타입**으로 닫는다. 개찰 결과 이름이
`NoticeObservation` 에 없으므로, 전략이 그 값을 보려면 이 dataclass 를 고쳐야 하고 그
편집은 diff 에 드러난다.

판독은 fail-closed 다 — **미지 키를 조용히 무시하지 않고 스냅숏 전체를 거부한다.** 이것이
상호·사업자번호가 스냅숏에 실려 오는 경로를 구조로 막는 자리다(D-6G-10, 금지 목록
열거가 아니라 허용 키 전수 대조). 거부 사유는 필드 이름만 나르고 공고 식별자를 담지
않는다(D-6G-9 「보고에 식별자가 없다」).
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from datetime import date, datetime
from enum import StrEnum
from math import isfinite
from typing import Final

from ml_engine.registry.artifact import JsonValue

SUPPORTED_SNAPSHOT_SCHEMA_VERSION: Final[str] = "snapshot-v1"


class BusinessCategory(StrEnum):
    """업무 대분류 — 닫힌 셋. 하한가 산식이 이 축으로 갈린다(D-6G-12)."""

    CONSTRUCTION = "CONSTRUCTION"
    SERVICE = "SERVICE"
    GOODS = "GOODS"


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


@dataclass(frozen=True)
class SnapshotRejected:
    """판독 거부 — `detail` 은 필드 이름·개수만 나른다(식별자 금지)."""

    reason: SnapshotRejectionReason
    detail: str


@dataclass(frozen=True)
class AValue:
    """입찰가격산식 A — 합산액과 **그 자신의 공개일시**. 공개가 입찰 마감 뒤인 공고는
    투찰 시점에 알 수 없는 값이라 제외된다(D-6G-12·D-6G-13 ⑥)."""

    total: float
    open_at: datetime


@dataclass(frozen=True)
class NoticeObservation:
    """투찰 시점에 알 수 있는 것만. 개찰 결과 이름은 여기 없다(누출 금지의 타입 경계)."""

    notice_key_hash: str
    category: BusinessCategory
    noticed_on: date
    bid_close_at: datetime
    base_amount: float
    floor_rate: float | None
    reserve_range_begin_rate: float
    reserve_range_end_rate: float
    a_value: AValue | None
    successful_bid_method_code: str
    successful_bid_method_name: str
    prearranged_price_decision_method: str
    notice_ordinal: int
    is_local_government: bool
    is_foreign_capital: bool
    pure_construction_cost: float | None


@dataclass(frozen=True)
class BidderRow:
    """투찰자 한 행 — 공고 안 순위와 금액뿐. 상호·사업자번호를 싣지 않는다(D-6G-10)."""

    rank: int
    amount: float


@dataclass(frozen=True)
class OpeningOutcome:
    """개찰로 드러나는 것 — 채점만 읽는다."""

    opened_on: date
    planned_price: float
    reserve_prices: tuple[float, ...] | None
    drawn_serial_numbers: tuple[int, ...] | None
    participant_count: int
    bidder_rows: tuple[BidderRow, ...]


@dataclass(frozen=True)
class SnapshotRow:
    notice: NoticeObservation
    outcome: OpeningOutcome


@dataclass(frozen=True)
class LoadedSnapshot:
    """판독된 스냅숏 — 행은 `notice_key_hash` 오름차순으로 고정한다(입력 파일의 줄
    순서가 판정에 새지 않게)."""

    snapshot_id: str
    period_start: date
    period_end: date
    rows_sha256: str
    sample_list_sha256: str
    rows: tuple[SnapshotRow, ...]


_ROW_KEYS: Final[frozenset[str]] = frozenset({"notice", "outcome"})
_NOTICE_KEYS: Final[frozenset[str]] = frozenset(
    {
        "notice_key_hash",
        "category",
        "noticed_on",
        "bid_close_at",
        "base_amount",
        "floor_rate",
        "reserve_range_begin_rate",
        "reserve_range_end_rate",
        "a_value",
        "successful_bid_method_code",
        "successful_bid_method_name",
        "prearranged_price_decision_method",
        "notice_ordinal",
        "is_local_government",
        "is_foreign_capital",
        "pure_construction_cost",
    }
)
_OUTCOME_KEYS: Final[frozenset[str]] = frozenset(
    {
        "opened_on",
        "planned_price",
        "reserve_prices",
        "drawn_serial_numbers",
        "participant_count",
        "bidder_rows",
    }
)
_BIDDER_KEYS: Final[frozenset[str]] = frozenset({"rank", "amount"})
_A_VALUE_KEYS: Final[frozenset[str]] = frozenset({"total", "open_at"})
_MANIFEST_KEYS: Final[frozenset[str]] = frozenset(
    {
        "schema_version",
        "snapshot_id",
        "row_count",
        "period_start",
        "period_end",
        "rows_sha256",
        "sample_list_sha256",
    }
)


class _RowReadError(Exception):
    """판독 실패를 호출 경계까지 나르는 내부 신호 — `load_snapshot` 이 결과 타입으로
    바꾼다(예외가 public 표면으로 새지 않는다, v2-지침서.md §5)."""

    def __init__(self, reason: SnapshotRejectionReason, detail: str) -> None:
        super().__init__(detail)
        self.reason = reason
        self.detail = detail


def _mapping(
    value: JsonValue, name: str, allowed: frozenset[str]
) -> dict[str, JsonValue]:
    if not isinstance(value, dict):
        raise _RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, f"{name}: 매핑이 아님"
        )
    unknown = sorted(set(value) - allowed)
    if unknown:
        raise _RowReadError(
            SnapshotRejectionReason.UNKNOWN_FIELD, f"{name}: 미지 키 {unknown}"
        )
    return value


def _present(payload: dict[str, JsonValue], key: str) -> JsonValue:
    if key not in payload:
        raise _RowReadError(SnapshotRejectionReason.MISSING_FIELD, key)
    return payload[key]


def _number(payload: dict[str, JsonValue], key: str) -> float:
    value = _present(payload, key)
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        raise _RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 수가 아님")
    if not isfinite(value):
        raise _RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 비유한값")
    return float(value)


def _optional_number(payload: dict[str, JsonValue], key: str) -> float | None:
    return None if _present(payload, key) is None else _number(payload, key)


def _integer(payload: dict[str, JsonValue], key: str) -> int:
    value = _present(payload, key)
    if isinstance(value, bool) or not isinstance(value, int):
        raise _RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, f"{key}: 정수가 아님"
        )
    return value


def _text(payload: dict[str, JsonValue], key: str) -> str:
    value = _present(payload, key)
    if not isinstance(value, str):
        raise _RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, f"{key}: 문자열 아님"
        )
    return value


def _flag(payload: dict[str, JsonValue], key: str) -> bool:
    value = _present(payload, key)
    if not isinstance(value, bool):
        raise _RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, f"{key}: 불리언 아님"
        )
    return value


def _day(payload: dict[str, JsonValue], key: str) -> date:
    try:
        return date.fromisoformat(_text(payload, key))
    except ValueError as exc:
        raise _RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, f"{key}: 날짜 형식 아님"
        ) from exc


def _timestamp(payload: dict[str, JsonValue], key: str) -> datetime:
    try:
        return datetime.fromisoformat(_text(payload, key))
    except ValueError as exc:
        raise _RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, f"{key}: 시각 형식 아님"
        ) from exc


def _category(payload: dict[str, JsonValue]) -> BusinessCategory:
    raw = _text(payload, "category")
    if raw not in tuple(BusinessCategory):
        raise _RowReadError(
            SnapshotRejectionReason.UNKNOWN_CATEGORY, "category: 닫힌 셋 밖의 값"
        )
    return BusinessCategory(raw)


def _a_value(payload: dict[str, JsonValue]) -> AValue | None:
    raw = _present(payload, "a_value")
    if raw is None:
        return None
    fields = _mapping(raw, "a_value", _A_VALUE_KEYS)
    return AValue(total=_number(fields, "total"), open_at=_timestamp(fields, "open_at"))


def _numbers(payload: dict[str, JsonValue], key: str) -> tuple[float, ...] | None:
    raw = _present(payload, key)
    if raw is None:
        return None
    if not isinstance(raw, list):
        raise _RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 배열 아님")
    return tuple(_number({"item": item}, "item") for item in raw)


def _integers(payload: dict[str, JsonValue], key: str) -> tuple[int, ...] | None:
    raw = _present(payload, key)
    if raw is None:
        return None
    if not isinstance(raw, list):
        raise _RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 배열 아님")
    return tuple(_integer({"item": item}, "item") for item in raw)


def _bidder_rows(payload: dict[str, JsonValue]) -> tuple[BidderRow, ...]:
    raw = _present(payload, "bidder_rows")
    if not isinstance(raw, list):
        raise _RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, "bidder_rows: 배열 아님"
        )
    rows = [_mapping(item, "bidder_rows[]", _BIDDER_KEYS) for item in raw]
    return tuple(
        BidderRow(rank=_integer(item, "rank"), amount=_number(item, "amount"))
        for item in rows
    )


def _parse_notice(payload: JsonValue) -> NoticeObservation:
    fields = _mapping(payload, "notice", _NOTICE_KEYS)
    return NoticeObservation(
        notice_key_hash=_text(fields, "notice_key_hash"),
        category=_category(fields),
        noticed_on=_day(fields, "noticed_on"),
        bid_close_at=_timestamp(fields, "bid_close_at"),
        base_amount=_number(fields, "base_amount"),
        floor_rate=_optional_number(fields, "floor_rate"),
        reserve_range_begin_rate=_number(fields, "reserve_range_begin_rate"),
        reserve_range_end_rate=_number(fields, "reserve_range_end_rate"),
        a_value=_a_value(fields),
        successful_bid_method_code=_text(fields, "successful_bid_method_code"),
        successful_bid_method_name=_text(fields, "successful_bid_method_name"),
        prearranged_price_decision_method=_text(
            fields, "prearranged_price_decision_method"
        ),
        notice_ordinal=_integer(fields, "notice_ordinal"),
        is_local_government=_flag(fields, "is_local_government"),
        is_foreign_capital=_flag(fields, "is_foreign_capital"),
        pure_construction_cost=_optional_number(fields, "pure_construction_cost"),
    )


def _parse_outcome(payload: JsonValue) -> OpeningOutcome:
    fields = _mapping(payload, "outcome", _OUTCOME_KEYS)
    return OpeningOutcome(
        opened_on=_day(fields, "opened_on"),
        planned_price=_number(fields, "planned_price"),
        reserve_prices=_numbers(fields, "reserve_prices"),
        drawn_serial_numbers=_integers(fields, "drawn_serial_numbers"),
        participant_count=_integer(fields, "participant_count"),
        bidder_rows=_bidder_rows(fields),
    )


def _reject_constant(literal: str) -> float:
    """`NaN`/`Infinity` 는 JSON 이 아니다 — `json.loads` 기본값은 받아들이므로 막는다."""
    raise _RowReadError(
        SnapshotRejectionReason.INVALID_VALUE, f"비유한 상수: {literal}"
    )


def _decode(raw: bytes, name: str) -> JsonValue:
    try:
        decoded: JsonValue = json.loads(raw, parse_constant=_reject_constant)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        raise _RowReadError(
            SnapshotRejectionReason.MALFORMED_JSON, f"{name}: JSON 아님"
        ) from exc
    return decoded


@dataclass(frozen=True)
class _Manifest:
    snapshot_id: str
    row_count: int
    period_start: date
    period_end: date
    rows_sha256: str
    sample_list_sha256: str


def _parse_manifest(manifest_bytes: bytes) -> _Manifest:
    fields = _mapping(_decode(manifest_bytes, "manifest"), "manifest", _MANIFEST_KEYS)
    if _text(fields, "schema_version") != SUPPORTED_SNAPSHOT_SCHEMA_VERSION:
        raise _RowReadError(
            SnapshotRejectionReason.UNSUPPORTED_SCHEMA_VERSION, "schema_version"
        )
    return _Manifest(
        snapshot_id=_text(fields, "snapshot_id"),
        row_count=_integer(fields, "row_count"),
        period_start=_day(fields, "period_start"),
        period_end=_day(fields, "period_end"),
        rows_sha256=_text(fields, "rows_sha256"),
        sample_list_sha256=_text(fields, "sample_list_sha256"),
    )


def _parse_rows(rows_bytes: bytes) -> tuple[SnapshotRow, ...]:
    rows: list[SnapshotRow] = []
    for line in rows_bytes.decode("utf-8", errors="replace").splitlines():
        if not line.strip():
            continue
        fields = _mapping(_decode(line.encode("utf-8"), "row"), "row", _ROW_KEYS)
        rows.append(
            SnapshotRow(
                notice=_parse_notice(_present(fields, "notice")),
                outcome=_parse_outcome(_present(fields, "outcome")),
            )
        )
    return tuple(sorted(rows, key=lambda row: row.notice.notice_key_hash))


def _assemble(manifest: _Manifest, rows: tuple[SnapshotRow, ...]) -> LoadedSnapshot:
    if len(rows) != manifest.row_count:
        raise _RowReadError(
            SnapshotRejectionReason.ROW_COUNT_MISMATCH,
            f"manifest {manifest.row_count} != rows {len(rows)}",
        )
    if not rows:
        raise _RowReadError(SnapshotRejectionReason.EMPTY, "행이 없다")
    keys = [row.notice.notice_key_hash for row in rows]
    if len(set(keys)) != len(keys):
        raise _RowReadError(
            SnapshotRejectionReason.DUPLICATE_NOTICE,
            f"중복 공고 {len(keys) - len(set(keys))}건",
        )
    return LoadedSnapshot(
        snapshot_id=manifest.snapshot_id,
        period_start=manifest.period_start,
        period_end=manifest.period_end,
        rows_sha256=manifest.rows_sha256,
        sample_list_sha256=manifest.sample_list_sha256,
        rows=rows,
    )


def load_snapshot(
    manifest_bytes: bytes, rows_bytes: bytes
) -> LoadedSnapshot | SnapshotRejected:
    """스냅숏 바이트 둘을 판독한다. 실패는 전부 `SnapshotRejected` — 예외로 새지
    않는다. 순서: manifest 형식 → schema version → rows checksum → 행 판독 → row 수
    → 빈 스냅숏 → 중복 공고."""
    try:
        manifest = _parse_manifest(manifest_bytes)
        actual = hashlib.sha256(rows_bytes).hexdigest()
        if actual != manifest.rows_sha256:
            raise _RowReadError(
                SnapshotRejectionReason.CHECKSUM_MISMATCH, "rows.jsonl sha256 불일치"
            )
        return _assemble(manifest, _parse_rows(rows_bytes))
    except _RowReadError as rejected:
        return SnapshotRejected(rejected.reason, rejected.detail)
