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
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from typing import Final

from ml_engine.evaluation.backtest.jsonrow import (
    RowReadError,
    SnapshotRejectionReason,
    decode_json,
    row_date,
    row_flag,
    row_integer,
    row_mapping,
    row_number,
    row_optional_date,
    row_optional_flag,
    row_optional_integer,
    row_optional_number,
    row_optional_text,
    row_optional_timestamp,
    row_text,
    row_timestamp,
    row_value,
)
from ml_engine.evaluation.backtest.observations import (
    AValue,
    BidderRow,
    BusinessCategory,
    LoadedSnapshot,
    NoticeObservation,
    OpeningOutcome,
    SnapshotRejected,
    SnapshotRow,
)
from ml_engine.evaluation.backtest.sample_list import SampleList, check_sample_list
from ml_engine.registry.artifact import JsonValue

SUPPORTED_SNAPSHOT_SCHEMA_VERSION: Final[str] = "snapshot-v4"


_ROW_KEYS: Final[frozenset[str]] = frozenset({"notice", "outcome"})
_NOTICE_KEYS: Final[frozenset[str]] = frozenset(
    {
        "notice_key_hash",
        "category",
        "noticed_on",
        "bid_close_at",
        "base_amount",
        "base_amount_disclosed_at",
        "floor_rate",
        "reserve_range_begin_rate",
        "reserve_range_end_rate",
        "a_value",
        "successful_bid_method_code",
        "successful_bid_method_name",
        "prearranged_price_decision_method",
        "notice_ordinal",
        "procurement_class_code",
        "demand_agency_code",
        "bid_price_formula_a_applicable",
        "pure_construction_cost",
        "has_award_method_application_standard",
        "has_application_basis_content",
    }
)
_OUTCOME_KEYS: Final[frozenset[str]] = frozenset(
    {
        "opened_on",
        "planned_price",
        "progress_division",
        "opening_base_amount",
        "reserve_prices",
        "drawn_serial_numbers",
        "participant_count",
        "bidder_rows",
    }
)
_BIDDER_KEYS: Final[frozenset[str]] = frozenset({"ordinal", "rank", "amount"})
_A_VALUE_KEYS: Final[frozenset[str]] = frozenset(
    {"total", "open_at", "standard_market_price_applicable"}
)
_MANIFEST_KEYS: Final[frozenset[str]] = frozenset(
    {
        "schema_version",
        "snapshot_id",
        "row_count",
        "period_start",
        "period_end",
        "rows_sha256",
        "sample_list_sha256",
        "sample_size",
        "sampled_without_detail",
        "sampled_without_notice",
    }
)


def _category(payload: dict[str, JsonValue]) -> BusinessCategory:
    raw = row_text(payload, "category")
    if raw not in tuple(BusinessCategory):
        raise RowReadError(
            SnapshotRejectionReason.UNKNOWN_CATEGORY, "category: 닫힌 셋 밖의 값"
        )
    return BusinessCategory(raw)


def _a_value(payload: dict[str, JsonValue]) -> AValue | None:
    raw = row_value(payload, "a_value")
    if raw is None:
        return None
    fields = row_mapping(raw, "a_value", _A_VALUE_KEYS)
    return AValue(
        total=row_number(fields, "total"),
        open_at=row_timestamp(fields, "open_at"),
        standard_market_price_applicable=row_optional_flag(
            fields, "standard_market_price_applicable"
        ),
    )


def _numbers(payload: dict[str, JsonValue], key: str) -> tuple[float, ...] | None:
    raw = row_value(payload, key)
    if raw is None:
        return None
    if not isinstance(raw, list):
        raise RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 배열 아님")
    return tuple(row_number({"item": item}, "item") for item in raw)


def _integers(payload: dict[str, JsonValue], key: str) -> tuple[int, ...] | None:
    raw = row_value(payload, key)
    if raw is None:
        return None
    if not isinstance(raw, list):
        raise RowReadError(SnapshotRejectionReason.INVALID_VALUE, f"{key}: 배열 아님")
    return tuple(row_integer({"item": item}, "item") for item in raw)


def _bidder_rows(payload: dict[str, JsonValue]) -> tuple[BidderRow, ...]:
    raw = row_value(payload, "bidder_rows")
    if not isinstance(raw, list):
        raise RowReadError(
            SnapshotRejectionReason.INVALID_VALUE, "bidder_rows: 배열 아님"
        )
    rows = [row_mapping(item, "bidder_rows[]", _BIDDER_KEYS) for item in raw]
    return tuple(
        BidderRow(
            ordinal=row_integer(item, "ordinal"),
            rank=row_optional_integer(item, "rank"),
            amount=row_optional_number(item, "amount"),
        )
        for item in rows
    )


def _parse_notice(payload: JsonValue) -> NoticeObservation:
    fields = row_mapping(payload, "notice", _NOTICE_KEYS)
    return NoticeObservation(
        notice_key_hash=row_text(fields, "notice_key_hash"),
        category=_category(fields),
        noticed_on=row_optional_date(fields, "noticed_on"),
        bid_close_at=row_optional_timestamp(fields, "bid_close_at"),
        base_amount=row_optional_number(fields, "base_amount"),
        base_amount_disclosed_at=row_optional_timestamp(
            fields, "base_amount_disclosed_at"
        ),
        floor_rate=row_optional_number(fields, "floor_rate"),
        reserve_range_begin_rate=row_optional_number(
            fields, "reserve_range_begin_rate"
        ),
        reserve_range_end_rate=row_optional_number(fields, "reserve_range_end_rate"),
        a_value=_a_value(fields),
        successful_bid_method_code=row_optional_text(
            fields, "successful_bid_method_code"
        ),
        successful_bid_method_name=row_optional_text(
            fields, "successful_bid_method_name"
        ),
        prearranged_price_decision_method=row_optional_text(
            fields, "prearranged_price_decision_method"
        ),
        notice_ordinal=row_integer(fields, "notice_ordinal"),
        procurement_class_code=row_optional_text(fields, "procurement_class_code"),
        demand_agency_code=row_optional_text(fields, "demand_agency_code"),
        bid_price_formula_a_applicable=row_optional_flag(
            fields, "bid_price_formula_a_applicable"
        ),
        pure_construction_cost=row_optional_number(fields, "pure_construction_cost"),
        has_award_method_application_standard=row_flag(
            fields, "has_award_method_application_standard"
        ),
        has_application_basis_content=row_flag(fields, "has_application_basis_content"),
    )


def _parse_outcome(payload: JsonValue) -> OpeningOutcome:
    fields = row_mapping(payload, "outcome", _OUTCOME_KEYS)
    return OpeningOutcome(
        opened_on=row_optional_date(fields, "opened_on"),
        planned_price=row_optional_number(fields, "planned_price"),
        progress_division=row_optional_text(fields, "progress_division"),
        opening_base_amount=row_optional_number(fields, "opening_base_amount"),
        reserve_prices=_numbers(fields, "reserve_prices"),
        drawn_serial_numbers=_integers(fields, "drawn_serial_numbers"),
        participant_count=row_optional_integer(fields, "participant_count"),
        bidder_rows=_bidder_rows(fields),
    )


@dataclass(frozen=True)
class _Manifest:
    snapshot_id: str
    row_count: int
    period_start: date
    period_end: date
    rows_sha256: str
    sample_list_sha256: str
    sample_size: int
    sampled_without_detail: int
    sampled_without_notice: int


def _parse_manifest(manifest_bytes: bytes) -> _Manifest:
    fields = row_mapping(
        decode_json(manifest_bytes, "manifest"), "manifest", _MANIFEST_KEYS
    )
    if row_text(fields, "schema_version") != SUPPORTED_SNAPSHOT_SCHEMA_VERSION:
        raise RowReadError(
            SnapshotRejectionReason.UNSUPPORTED_SCHEMA_VERSION, "schema_version"
        )
    return _Manifest(
        snapshot_id=row_text(fields, "snapshot_id"),
        row_count=row_integer(fields, "row_count"),
        period_start=row_date(fields, "period_start"),
        period_end=row_date(fields, "period_end"),
        rows_sha256=row_text(fields, "rows_sha256"),
        sample_list_sha256=row_text(fields, "sample_list_sha256"),
        sample_size=row_integer(fields, "sample_size"),
        sampled_without_detail=row_integer(fields, "sampled_without_detail"),
        sampled_without_notice=row_integer(fields, "sampled_without_notice"),
    )


def _parse_rows(rows_bytes: bytes) -> tuple[SnapshotRow, ...]:
    """깨진 바이트를 **치환하지 않는다**(code-review r1 L-1) — 같은 모듈이 「미지 키를
    조용히 무시하지 않고 거부한다」를 표방하면서 바이트는 조용히 바꾸면 일관되지 않다.
    `UnicodeDecodeError` 는 `MALFORMED_JSON` 으로 접힌다."""
    rows: list[SnapshotRow] = []
    try:
        text = rows_bytes.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise RowReadError(
            SnapshotRejectionReason.MALFORMED_JSON, "rows: UTF-8 아님"
        ) from exc
    for line in text.splitlines():
        if not line.strip():
            continue
        fields = row_mapping(decode_json(line.encode("utf-8"), "row"), "row", _ROW_KEYS)
        rows.append(
            SnapshotRow(
                notice=_parse_notice(row_value(fields, "notice")),
                outcome=_parse_outcome(row_value(fields, "outcome")),
            )
        )
    return tuple(sorted(rows, key=lambda row: row.notice.notice_key_hash))


def opening_date_range(rows: Sequence[SnapshotRow]) -> tuple[date, date] | None:
    """행들의 **개찰일 범위**. v3 에서 개찰일이 `| null` 이라 값 있는 행만 본다 —
    하나도 없으면 범위가 성립하지 않는다(`None`)."""
    days = [row.outcome.opened_on for row in rows if row.outcome.opened_on is not None]
    if not days:
        return None
    return min(days), max(days)


def _check_period(manifest: _Manifest, rows: tuple[SnapshotRow, ...]) -> None:
    """manifest 의 기간이 **그 파일의 행**과 맞는지(D-6G-32 「manifest 가 사실을
    말하는가」). 표본 목록 해시 재계산(verifier r1 M-4)과 같은 계열이다 — manifest 는
    생산 쪽이 적는 값이고, 행과 묶이는 자리가 없으면 아무 기간이나 적을 수 있다.

    생산 쪽 계약은 「기간 = 행들의 개찰일 범위(추출 설정의 관측 창이 아니다)」이므로
    **일치**를 요구한다(포함이 아니라). 어긋나면 두 레인이 다른 것을 보고 있다는
    신호라 구조 실패로 전체를 거부한다."""
    observed = opening_date_range(rows)
    if observed is None:
        raise RowReadError(
            SnapshotRejectionReason.PERIOD_MISMATCH,
            "개찰일이 있는 행이 없어 기간을 대조할 수 없다",
        )
    if (manifest.period_start, manifest.period_end) != observed:
        raise RowReadError(
            SnapshotRejectionReason.PERIOD_MISMATCH,
            f"manifest {manifest.period_start}~{manifest.period_end} != "
            f"행 {observed[0]}~{observed[1]}",
        )


def _validate_rows(manifest: _Manifest, rows: tuple[SnapshotRow, ...]) -> None:
    """행 자체의 정합 — **표본 목록 대조보다 먼저** 돈다. 순서가 뒤집히면 중복 공고가
    「표본 계수 불일치」로 보고되어 사유가 뿌리를 가리키지 못한다."""
    if len(rows) != manifest.row_count:
        raise RowReadError(
            SnapshotRejectionReason.ROW_COUNT_MISMATCH,
            f"manifest {manifest.row_count} != rows {len(rows)}",
        )
    if not rows:
        raise RowReadError(SnapshotRejectionReason.EMPTY, "행이 없다")
    keys = [row.notice.notice_key_hash for row in rows]
    if len(set(keys)) != len(keys):
        raise RowReadError(
            SnapshotRejectionReason.DUPLICATE_NOTICE,
            f"중복 공고 {len(keys) - len(set(keys))}건",
        )
    _check_period(manifest, rows)


def _assemble(
    manifest: _Manifest, rows: tuple[SnapshotRow, ...], listing: SampleList
) -> LoadedSnapshot:
    return LoadedSnapshot(
        snapshot_id=manifest.snapshot_id,
        period_start=manifest.period_start,
        period_end=manifest.period_end,
        rows_sha256=manifest.rows_sha256,
        sample_list_sha256=manifest.sample_list_sha256,
        sample_size=manifest.sample_size,
        sampled_without_detail=manifest.sampled_without_detail,
        sampled_without_notice=manifest.sampled_without_notice,
        sample_divisions=listing.divisions,
        rows=rows,
    )


def load_snapshot(
    manifest_bytes: bytes, rows_bytes: bytes, sample_list_bytes: bytes
) -> LoadedSnapshot | SnapshotRejected:
    """스냅숏 바이트 **셋**을 판독한다(v4 — 표본 목록이 파일이 됐다). 실패는 전부
    `SnapshotRejected` — 예외로 새지 않는다. 순서: manifest 형식 → schema version →
    rows checksum → 행 판독 → row 수 → 빈 스냅숏 → 중복 공고 → 기간 → 표본 대조 셋."""
    try:
        manifest = _parse_manifest(manifest_bytes)
        actual = hashlib.sha256(rows_bytes).hexdigest()
        if actual != manifest.rows_sha256:
            raise RowReadError(
                SnapshotRejectionReason.CHECKSUM_MISMATCH, "rows.jsonl sha256 불일치"
            )
        rows = _parse_rows(rows_bytes)
        _validate_rows(manifest, rows)
        listing = check_sample_list(
            sample_list_bytes,
            declared_sha256=manifest.sample_list_sha256,
            sample_size=manifest.sample_size,
            row_keys=[row.notice.notice_key_hash for row in rows],
            sampled_without_detail=manifest.sampled_without_detail,
            sampled_without_notice=manifest.sampled_without_notice,
        )
        return _assemble(manifest, rows, listing)
    except RowReadError as rejected:
        # `UnicodeDecodeError` 를 여기서 받지 않는다 — 파일마다 자기 판독기가 사유를
        # 붙인다(cr r3 L-7). 여기서 한꺼번에 접으면 어느 파일이 깨졌는지 잃는다.
        return SnapshotRejected(rejected.reason, rejected.detail)
