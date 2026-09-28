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
    noticed_on: str | None = "2026-06-01",
    bid_close_at: str | None = "2026-06-10T10:00:00+09:00",
    base_amount: Any = _BASE_AMOUNT,
    floor_rate: float | None = _FLOOR_RATE,
    a_value: dict[str, Any] | None = None,
    successful_bid_method_code: str = "낙030001",
    successful_bid_method_name: str = "적격심사제-추정가격 2억원 미만인 용역",
    prearranged_price_decision_method: str = "복수예가",
    notice_ordinal: int = 0,
    procurement_class_code: str | None = None,
    demand_agency_code: str | None = "A0001",
    bid_price_formula_a_applicable: bool | None = None,
    has_award_method_application_standard: bool = True,
    has_application_basis_content: bool = False,
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
        "procurement_class_code": procurement_class_code,
        "demand_agency_code": demand_agency_code,
        "bid_price_formula_a_applicable": bid_price_formula_a_applicable,
        "pure_construction_cost": pure_construction_cost,
        "has_award_method_application_standard": (
            has_award_method_application_standard
        ),
        "has_application_basis_content": has_application_basis_content,
    }


def reserve_prices(base_amount: float = _BASE_AMOUNT) -> list[float]:
    """±2% 를 15구간으로 나눈 구간 중앙값 — 합성이지만 제도 형태는 지킨다."""
    low = base_amount * 0.98
    step = base_amount * 0.04 / 15
    return [low + step * (index + 0.5) for index in range(15)]


def outcome_payload(
    *,
    opened_on: str | None = "2026-06-15",
    progress_division: str | None = None,
    planned_price: float | None = None,
    planned_price_null: bool = False,
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
        "planned_price": None if planned_price_null else int(planned_price),
        "progress_division": progress_division,
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


def sample_list_bytes(
    rows: bytes,
    *,
    extra_keys: tuple[str, ...] = (),
    division: str = "SERVICE",
    divisions: tuple[str, ...] = (),
) -> bytes:
    """`sample-list.tsv` 바이트(v4, 스키마 §2.1) — 헤더 없는 TSV, 해시 오름차순,
    끝 줄 개행 포함. `extra_keys` 로 **행이 없는 표본**(상세를 못 받았거나 공고
    canonical 이 없는 공고)을 넣어 진부분집합 상태를 만든다.

    `divisions` 를 주면 키 순서대로 **돌려 가며** 붙인다 — 업무 축이 여럿인 목록을
    만들어 최소 표본 결정식이 그 수를 어디서 세는지 가른다."""
    keys = sorted({*_row_keys(rows), *extra_keys})
    if divisions:
        lines = [
            f"{key}\t{divisions[index % len(divisions)]}\t2026-W25"
            for index, key in enumerate(keys)
        ]
    else:
        lines = [f"{key}\t{division}\t2026-W25" for key in keys]
    return ("\n".join(lines) + "\n").encode("utf-8") if lines else b""


def _row_keys(rows: bytes) -> list[str]:
    keys: list[str] = []
    for line in rows.decode("utf-8", errors="replace").splitlines():
        if not line.strip():
            continue
        try:
            keys.append(json.loads(line)["notice"]["notice_key_hash"])
        except (json.JSONDecodeError, KeyError, TypeError):
            # 일부러 깨뜨린 입력을 쓰는 test 가 있다 — 그 줄은 건너뛴다. 그런 입력은
            # 판독기가 표본 목록 대조보다 먼저 `MALFORMED_JSON` 으로 거부한다.
            continue
    return keys


def _opening_range(rows: bytes) -> tuple[str, str]:
    """행에서 계산한 개찰일 범위 — 판독기가 같은 식으로 재계산해 대조한다."""
    days: list[str] = []
    for line in rows.decode("utf-8", errors="replace").splitlines():
        if not line.strip():
            continue
        try:
            day = json.loads(line)["outcome"]["opened_on"]
        except (json.JSONDecodeError, KeyError, TypeError):
            continue
        if day:
            days.append(day)
    if not days:
        return ("2026-06-01", "2026-06-01")
    return (min(days), max(days))


def manifest_bytes(
    rows: bytes,
    *,
    snapshot_id: str = "snapshot-test-1",
    row_count: int | None = None,
    period_start: str | None = None,
    period_end: str | None = None,
    rows_sha256: str | None = None,
    sample_list_sha256: str | None = None,
    sample_list: bytes | None = None,
    sample_size: int | None = None,
    sampled_without_detail: int = 0,
    sampled_without_notice: int = 0,
    incomplete_axis: int = 0,
    schema_version: str = "snapshot-v5",
) -> bytes:
    payload = {
        "schema_version": schema_version,
        "snapshot_id": snapshot_id,
        "row_count": (
            len(rows.decode("utf-8").strip().splitlines())
            if row_count is None
            else row_count
        ),
        "period_start": (
            _opening_range(rows)[0] if period_start is None else period_start
        ),
        "period_end": _opening_range(rows)[1] if period_end is None else period_end,
        "rows_sha256": (
            hashlib.sha256(rows).hexdigest() if rows_sha256 is None else rows_sha256
        ),
        "sample_list_sha256": (
            hashlib.sha256(
                sample_list_bytes(rows) if sample_list is None else sample_list
            ).hexdigest()
            if sample_list_sha256 is None
            else sample_list_sha256
        ),
        "sample_size": (
            len(_row_keys(rows))
            + sampled_without_detail
            + sampled_without_notice
            + incomplete_axis
            if sample_size is None
            else sample_size
        ),
        "sampled_without_detail": sampled_without_detail,
        "sampled_without_notice": sampled_without_notice,
        "incomplete_axis": incomplete_axis,
    }
    return json.dumps(payload, sort_keys=True).encode("utf-8")


def write_snapshot_dir(
    directory: Path, payloads: list[dict[str, Any]], **manifest_kwargs: Any
) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    rows = rows_bytes(payloads)
    (directory / "rows.jsonl").write_bytes(rows)
    (directory / "sample-list.tsv").write_bytes(sample_list_bytes(rows))
    (directory / "manifest.json").write_bytes(manifest_bytes(rows, **manifest_kwargs))
    return directory
