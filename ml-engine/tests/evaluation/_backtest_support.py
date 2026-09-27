"""M6/6G test 지원 — 합성 스냅숏 행 조립기(비식별). 실 데이터를 쓰지 않는다.

여기서 만드는 값은 전부 지어낸 것이고 공고 식별자 자리에는 고정 해시 문자열을 넣는다
(`data-extract.md` §7 — 스냅숏은 저장소 밖, test fixture 는 합성)."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

_BASE_AMOUNT = 1_000_000_000
_FLOOR_RATE = 0.87745

SHIPPED_BACKTEST_POLICY_PATH = (
    Path(__file__).resolve().parents[2] / "policy" / "strategy-backtest-v1.yaml"
)


def notice_key_hash(label: str) -> str:
    return hashlib.sha256(label.encode("utf-8")).hexdigest()


def notice_payload(
    label: str = "n-1",
    *,
    category: str = "SERVICE",
    noticed_on: str = "2026-06-01",
    bid_close_at: str = "2026-06-10T10:00:00+09:00",
    base_amount: Any = _BASE_AMOUNT,
    floor_rate: float | None = _FLOOR_RATE,
    a_value: dict[str, Any] | None = None,
    successful_bid_method_code: str = "낙030001",
    successful_bid_method_name: str = "적격심사제-추정가격 2억원 미만인 용역",
    prearranged_price_decision_method: str = "복수예가",
    notice_ordinal: int = 1,
    progress_division: str | None = None,
    procurement_class_code: str | None = None,
    demand_agency_code: str | None = "A0001",
    bid_price_formula_a_applicable: bool | None = None,
    award_method_application_standard: str | None = "표준",
    application_basis_content: str | None = None,
    base_amount_disclosed_at: str | None = "2026-06-05T09:00:00+09:00",
    reserve_range_begin_rate: float | None = -0.02,
    reserve_range_end_rate: float | None = 0.02,
    pure_construction_cost: int | None = None,
) -> dict[str, Any]:
    return {
        "notice_key_hash": notice_key_hash(label),
        "category": category,
        "noticed_on": noticed_on,
        "bid_close_at": bid_close_at,
        "base_amount": base_amount,
        "base_amount_disclosed_at": base_amount_disclosed_at,
        "floor_rate": floor_rate,
        "reserve_range_begin_rate": reserve_range_begin_rate,
        "reserve_range_end_rate": reserve_range_end_rate,
        "a_value": a_value,
        "successful_bid_method_code": successful_bid_method_code,
        "successful_bid_method_name": successful_bid_method_name,
        "prearranged_price_decision_method": prearranged_price_decision_method,
        "notice_ordinal": notice_ordinal,
        "progress_division": progress_division,
        "procurement_class_code": procurement_class_code,
        "demand_agency_code": demand_agency_code,
        "bid_price_formula_a_applicable": bid_price_formula_a_applicable,
        "pure_construction_cost": pure_construction_cost,
        "award_method_application_standard": award_method_application_standard,
        "application_basis_content": application_basis_content,
    }


def reserve_prices(base_amount: float = _BASE_AMOUNT) -> list[float]:
    """±2% 를 15구간으로 나눈 구간 중앙값 — 합성이지만 제도 형태는 지킨다."""
    low = base_amount * 0.98
    step = base_amount * 0.04 / 15
    return [low + step * (index + 0.5) for index in range(15)]


def outcome_payload(
    *,
    opened_on: str = "2026-06-15",
    planned_price: float | None = None,
    prices: list[float] | None = None,
    prices_null: bool = False,
    opening_base_amount_null: bool = False,
    opening_base_amount: int | None = None,
    drawn: list[int] | None = None,
    participant_count: int = 12,
    bidder_amounts: list[float] | None = None,
) -> dict[str, Any]:
    values = reserve_prices() if prices is None else prices
    drawn_numbers = [1, 4, 8, 12] if drawn is None else drawn
    if planned_price is None:
        planned_price = sum(values[number - 1] for number in drawn_numbers) / len(
            drawn_numbers
        )
    amounts = (
        [planned_price * rate for rate in (0.879, 0.881, 0.884, 0.890)]
        if bidder_amounts is None
        else bidder_amounts
    )
    return {
        "opened_on": opened_on,
        "planned_price": int(planned_price),
        "opening_base_amount": (
            None
            if opening_base_amount_null
            else int(
                _BASE_AMOUNT if opening_base_amount is None else opening_base_amount
            )
        ),
        "reserve_prices": None if prices_null else [int(value) for value in values],
        "drawn_serial_numbers": drawn_numbers,
        "participant_count": participant_count,
        "bidder_rows": [
            {"ordinal": index + 1, "rank": index + 1, "amount": int(amount)}
            for index, amount in enumerate(sorted(amounts))
        ],
    }


def row_payload(label: str = "n-1", **kwargs: Any) -> dict[str, Any]:
    notice_kwargs = {
        key: value for key, value in kwargs.items() if key.startswith("notice_")
    }
    outcome_kwargs = {
        key: value for key, value in kwargs.items() if key.startswith("outcome_")
    }
    return {
        "notice": notice_payload(
            label, **{key[len("notice_") :]: v for key, v in notice_kwargs.items()}
        ),
        "outcome": outcome_payload(
            **{key[len("outcome_") :]: v for key, v in outcome_kwargs.items()}
        ),
    }


def rows_bytes(payloads: list[dict[str, Any]]) -> bytes:
    return (
        "\n".join(json.dumps(payload, sort_keys=True) for payload in payloads) + "\n"
    ).encode("utf-8")


def manifest_bytes(
    rows: bytes,
    *,
    snapshot_id: str = "snapshot-test-1",
    row_count: int | None = None,
    period_start: str = "2026-06-01",
    period_end: str = "2026-08-31",
    rows_sha256: str | None = None,
    sample_list_sha256: str = "0" * 64,
    schema_version: str = "snapshot-v1",
) -> bytes:
    payload = {
        "schema_version": schema_version,
        "snapshot_id": snapshot_id,
        "row_count": (
            len(rows.decode("utf-8").strip().splitlines())
            if row_count is None
            else row_count
        ),
        "period_start": period_start,
        "period_end": period_end,
        "rows_sha256": (
            hashlib.sha256(rows).hexdigest() if rows_sha256 is None else rows_sha256
        ),
        "sample_list_sha256": sample_list_sha256,
    }
    return json.dumps(payload, sort_keys=True).encode("utf-8")


def write_snapshot_dir(
    directory: Path, payloads: list[dict[str, Any]], **manifest_kwargs: Any
) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    rows = rows_bytes(payloads)
    (directory / "rows.jsonl").write_bytes(rows)
    (directory / "manifest.json").write_bytes(manifest_bytes(rows, **manifest_kwargs))
    return directory
