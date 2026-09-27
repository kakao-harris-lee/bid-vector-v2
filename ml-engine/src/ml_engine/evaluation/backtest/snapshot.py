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
from datetime import date, datetime
from enum import StrEnum
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
from ml_engine.registry.artifact import JsonValue

SUPPORTED_SNAPSHOT_SCHEMA_VERSION: Final[str] = "snapshot-v3"


class BusinessCategory(StrEnum):
    """업무 대분류 — 닫힌 셋. 하한가 산식이 이 축으로 갈린다(D-6G-12)."""

    CONSTRUCTION = "CONSTRUCTION"
    SERVICE = "SERVICE"
    GOODS = "GOODS"


@dataclass(frozen=True)
class SnapshotRejected:
    """판독 거부 — `detail` 은 필드 이름·개수만 나른다(식별자 금지)."""

    reason: SnapshotRejectionReason
    detail: str


@dataclass(frozen=True)
class AValue:
    """입찰가격산식 A — 합산액과 **그 자신의 공개일시**. 공개가 입찰 마감 뒤인 공고는
    투찰 시점에 알 수 없는 값이라 제외된다(D-6G-12·D-6G-13 ⑥).

    `standard_market_price_applicable`(`snapshot-v2`, D-6G-23)는 표준시장단가금액
    (`smkpAmt`)의 적용 여부 술어다. **`total` 은 그 금액을 더하지 않는다**(스키마 §3.3 —
    예규의 A 일곱 항목 열거에 없고 근거 문면을 확보하지 못했다). 이 술어가 있어야
    그 배제의 **영향 범위**를 셀 수 있다 — 없으면 판정문이 「몇 건이 걸리는가」를 말하지
    못한다. `Y`/`N` 밖의 값이나 부재는 `None`(판정 불가)."""

    total: float
    open_at: datetime
    standard_market_price_applicable: bool | None


@dataclass(frozen=True)
class NoticeObservation:
    """투찰 시점에 알 수 있는 것만. 개찰 결과 이름은 여기 없다(누출 금지의 타입 경계).

    **기초금액이 두 칸으로 갈린다**(스키마 §3.4, D-6G-19). 여기 `base_amount` 는 **기초금액
    조회 출처**이고 `base_amount_disclosed_at < bid_close_at` 인 행만 값을 갖는다 — 공개가
    마감보다 늦으면 투찰 시점에 없던 값이라 `null` 이다. 개찰결과 출처의 기초금액은
    `OpeningOutcome.opening_base_amount` 로 따로 있고 **채점만** 쓴다. 한 칸에 접으면
    전략이 투찰 시점에 몰랐던 값을 입력으로 쓰게 된다.

    **없는 값을 상수로 메우지 않는다** — 없으면 제외 사유가 되고 계수된다(D-6G-16).
    예가 범위율은 기초금액 조회에서 오고 원문이 percent 라 추출이 fraction 으로 넘긴다.
    시작률이 종료율의 반대수라는 보장은 없다(비대칭 범위 지원)."""

    notice_key_hash: str
    category: BusinessCategory
    noticed_on: date | None
    bid_close_at: datetime | None
    base_amount: float | None
    base_amount_disclosed_at: datetime | None
    floor_rate: float | None
    reserve_range_begin_rate: float | None
    reserve_range_end_rate: float | None
    a_value: AValue | None
    successful_bid_method_code: str | None
    successful_bid_method_name: str | None
    prearranged_price_decision_method: str | None
    notice_ordinal: int
    procurement_class_code: str | None
    demand_agency_code: str | None
    bid_price_formula_a_applicable: bool | None
    pure_construction_cost: float | None
    has_award_method_application_standard: bool
    has_application_basis_content: bool
    """자유텍스트 두 칸의 **존재 여부만**(v3, D-6G-33). 원문은 무엇이 실릴지 모르는
    자유텍스트라 담당자명이 들어올 수 있는 유일한 비통제 경로였다(privacy r1) — 스냅숏이
    원문을 나르지 않으므로 그 경로가 사라진다. D-6G-22 채움률은 이 불리언으로 낸다."""


@dataclass(frozen=True)
class BidderRow:
    """투찰자 한 행 — 공고 안 순번과 금액뿐. 상호·사업자번호를 싣지 않는다(D-6G-10).

    `rank`(원문 `opengRank`)는 **결측·중복이 흔하다**(스키마 §3.2 실측: 표본 15건 중
    전 행 유일은 4건뿐) — 경쟁자 분포는 `rank` 가 아니라 `amount` 로 만든다. `ordinal`
    은 금액 오름차순으로 추출이 붙인 순번이고 항상 있다."""

    ordinal: int
    rank: int | None
    amount: float | None


@dataclass(frozen=True)
class OpeningOutcome:
    """개찰로 드러나는 것 — 채점만 읽는다. `opening_base_amount` 는 개찰결과 출처의
    기초금액이고 `NoticeObservation.base_amount` 와 **다른 칸**이다(스키마 §3.4)."""

    opened_on: date | None
    planned_price: float | None
    progress_division: str | None
    """진행구분 — **개찰로 드러나는 값**이라 이쪽이다(v3, verifier r1 M-5). v2 는 투찰
    시점 타입에 있었고, 그러면 「타입으로 닫았다」가 이 칸에서 깨진다."""

    opening_base_amount: float | None
    reserve_prices: tuple[float, ...] | None
    drawn_serial_numbers: tuple[int, ...] | None
    participant_count: int | None
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


def sample_list_checksum(notice_key_hashes: Sequence[str]) -> str:
    """스키마 §5 정의 그대로 — 뽑힌 `notice_key_hash` 를 **오름차순 정렬**해 `\n` 으로
    이은 문자열(끝 개행 없음)의 sha256 hex.

    이 값을 manifest 가 싣는 값과 대조하는 것이 우회 ⑦(표본 쇼핑)의 잠금이다
    (verifier r1 M-4). 앞 판은 manifest 의 해시를 **그대로 판정 JSON 에 옮겨 실었고**,
    그 해시가 추출된 행 집합과 묶이는 자리가 없었다 — seed 를 바꿔 여러 번 수집하고
    합집합을 추출해도 그럴듯한 해시가 실린다. 행에서 다시 계산해야 술어가 선다."""
    joined = "\n".join(sorted(notice_key_hashes))
    return hashlib.sha256(joined.encode("utf-8")).hexdigest()


def _assemble(manifest: _Manifest, rows: tuple[SnapshotRow, ...]) -> LoadedSnapshot:
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
    if sample_list_checksum(keys) != manifest.sample_list_sha256:
        raise RowReadError(
            SnapshotRejectionReason.SAMPLE_LIST_MISMATCH,
            "표본 목록 sha256 이 행 집합과 맞지 않는다",
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
            raise RowReadError(
                SnapshotRejectionReason.CHECKSUM_MISMATCH, "rows.jsonl sha256 불일치"
            )
        return _assemble(manifest, _parse_rows(rows_bytes))
    except RowReadError as rejected:
        return SnapshotRejected(rejected.reason, rejected.detail)
