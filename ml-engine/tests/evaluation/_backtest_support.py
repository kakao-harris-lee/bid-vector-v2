"""M6/6G test 지원 — 합성 스냅숏 행 조립기(비식별). 실 데이터를 쓰지 않는다.

여기서 만드는 값은 전부 지어낸 것이고 공고 식별자 자리에는 고정 해시 문자열을 넣는다
(`data-extract.md` §7 — 스냅숏은 저장소 밖, test fixture 는 합성)."""

from __future__ import annotations

import hashlib
import json
import math
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.backtest.sample_list import BUSINESS_DIVISIONS
from ml_engine.evaluation.backtest.strategies import (
    BidAmount,
    StrategyInput,
    StrategyOutcome,
)
from tests.evaluation._backtest_fixture import (
    INELIGIBLE_BID_RATE,
    LOSING_BID_RATE,
    WINNING_BID_RATE,
)

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
    sample_scope_divisions: tuple[str, ...] | None = None,
    schema_version: str = "snapshot-v5",
) -> bytes:
    """`sample_scope_divisions` 를 주지 않으면 **표본 목록에 나타난 업무**로 채운다 —
    범위와 표본이 같은 판이 기본이고, 둘을 **다르게** 두는 것은 그것을 재는 test 의
    몫이다(D-6G-66: 문턱이 어느 쪽에서 오는지는 둘이 다를 때만 갈린다)."""
    listing = sample_list_bytes(rows) if sample_list is None else sample_list
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
            hashlib.sha256(listing).hexdigest()
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
        "sample_scope_divisions": (
            sorted(_default_scope(listing, rows))
            if sample_scope_divisions is None
            else list(sample_scope_divisions)
        ),
    }
    return json.dumps(payload, sort_keys=True).encode("utf-8")


def _default_scope(listing: bytes, rows: bytes) -> set[str]:
    """기본 확정 범위 — **표본 목록의 층 축과 행의 업무를 합친 집합**.

    둘은 다른 축이지만(스키마 §2.1) 어휘가 같고, 판독은 **둘 다** 범위 안이기를
    요구한다(범위 밖 행은 채점에 들어가면서 업무 대표 공시에서 사라진다). 그러니
    기본값은 그 데이터를 실제로 덮는 집합이어야 한다 — 둘을 **다르게** 두는 것은
    그것을 재는 test 의 몫이다.

    일부러 깨뜨린 목록(어휘 밖 값·칸 수 위반)을 쓰는 test 가 있다 — 그 값을 범위 칸에
    옮겨 적으면 manifest 판독이 먼저 걸려 test 가 겨눈 자리를 못 본다. 어휘에 맞는
    값만 모으고, 하나도 없으면 판이 서는 기본값을 쓴다."""
    divisions = {
        columns[1]
        for columns in (
            line.split("\t")
            for line in listing.decode("utf-8", errors="replace").splitlines()
        )
        if len(columns) == len(("key", "division", "week"))
        and columns[1] in BUSINESS_DIVISIONS
    }
    for line in rows.decode("utf-8", errors="replace").splitlines():
        if not line.strip():
            continue
        try:
            category = json.loads(line)["notice"]["category"]
        except (json.JSONDecodeError, KeyError, TypeError):
            continue
        if category in BUSINESS_DIVISIONS:
            divisions.add(category)
    return divisions or {"SERVICE"}


def write_snapshot_dir(
    directory: Path, payloads: list[dict[str, Any]], **manifest_kwargs: Any
) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    rows = rows_bytes(payloads)
    (directory / "rows.jsonl").write_bytes(rows)
    (directory / "sample-list.tsv").write_bytes(sample_list_bytes(rows))
    (directory / "manifest.json").write_bytes(manifest_bytes(rows, **manifest_kwargs))
    return directory


@dataclass(frozen=True)
class PlannedBidStrategy:
    """**계획대로 투찰하는** test 전략(M6/6G-2a, D-6G2a-4) — 공고마다 투찰률 하나.

    출하 전략 다섯(S0·S1·S2 셋·S4)은 난수와 분포 엔진에 달려 있어 「이 창에서 후보가
    기준선을 몇 번 이긴다」를 지정할 수 없다. 그래서 **판정 입력의 경계를 올라타는 판**에는
    이 전략을 주입한다 — `StrategyLike` 는 Protocol 이고 전략은 조립 근이 넣는 것이라
    (`run` 의 층 경계) 출하 코드를 건드리지 않는다.

    **판정 입력을 읽는 자리는 전략이 아니다**: 일곱 값은 `verdict`·`run`·`windows`·
    `policy_values` 에서 읽히고 전략 모듈에서는 읽히지 않는다(착수 AST 전수). 그래서 이
    전략을 쓰는 것이 측정 범위를 줄이지 않는다. 전략 모듈에서만 읽히는 값
    (`strategy.*` · `institution.*` · `floor.pure_construction_cost_ratio`)은 출하 전략을
    그대로 돌리는 판에서 잰다.

    `seed_sensitive` 가 참이면 `seeds` 밖의 seed 로 불릴 때 승패를 뒤집는다 — 정책의
    seed 다섯이 **전부** 전략에 닿는지를 거동으로 잰다(하나만 닿으면 seed 안정성 판정이
    흔들리지 않아 그 자리의 상수가 보이지 않는다)."""

    name: str
    plan: Mapping[str, float]
    seeds: tuple[int, ...] = ()
    seed_sensitive: bool = False

    def bid(
        self, request: StrategyInput, policy: StrategyBacktestPolicy
    ) -> StrategyOutcome:
        del policy  # 이 전략은 정책을 읽지 않는다 — 계획이 투찰률을 정한다.
        rate = self.plan[request.notice.notice_key_hash]
        if self.seed_sensitive and request.seed not in self.seeds:
            rate = _FLIPPED_RATES[rate]
        return BidAmount(float(math.ceil(request.base_amount * rate)))


_FLIPPED_RATES: dict[float, float] = {
    WINNING_BID_RATE: LOSING_BID_RATE,
    LOSING_BID_RATE: WINNING_BID_RATE,
    INELIGIBLE_BID_RATE: WINNING_BID_RATE,
}
"""seed 민감 전략이 계획 밖 seed 에서 쓰는 반대쪽 투찰률 — 승패가 뒤집히므로 seed 안정성
판정이 그 사실을 본다."""
