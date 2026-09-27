"""RED — M6/6G D-6G-12·D-6G-13. 하한가 산식(업무별)과 제외 열다섯.

이 test 가 잠그는 것:
1. **공사 산식이 A 를 넣는다** — `예정가격 * r` 로 쓰면 A값 공고에서 하한가를 과소평가해
   적격률이 부풀고(02 §5.1 실측 약 77 bp), bp 지표의 눈금(±200~300 bp)을 덮는다.
2. **A 공개일시 절단** — A 가 입찰 마감 뒤에 공개되는 공고는 투찰 시점에 알 수 없다.
3. **제외는 사유 열다섯 전부를 계수한다** — 0건도 공시한다(제외의 정직성).
4. **제외 판정에 전략이 들어가지 않는다** — `admit_rows` 의 시그니처에 전략이 없다.
"""

from __future__ import annotations

import inspect
from typing import Any

import pytest
from _backtest_support import (
    SHIPPED_BACKTEST_POLICY_PATH,
    manifest_bytes,
    row_payload,
    rows_bytes,
)

from ml_engine.evaluation.backtest.exclusions import (
    EXCLUSION_RULE_ORDER,
    AdmittedNotice,
    ExclusionReason,
    UndecidableAxis,
    admit_rows,
    exclusion_counts,
    resolve_a_value,
)
from ml_engine.evaluation.backtest.floor import (
    floor_price,
    is_eligible,
    pure_construction_floor,
)
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.backtest.snapshot import (
    BusinessCategory,
    LoadedSnapshot,
    load_snapshot,
)


def _policy() -> StrategyBacktestPolicy:
    loaded = load_strategy_backtest_policy(SHIPPED_BACKTEST_POLICY_PATH)
    assert isinstance(loaded, StrategyBacktestPolicy)
    return loaded


def _snapshot(payloads: list[dict[str, Any]]) -> LoadedSnapshot:
    rows = rows_bytes(payloads)
    loaded = load_snapshot(manifest_bytes(rows), rows)
    assert isinstance(loaded, LoadedSnapshot), loaded
    return loaded


def _reason(payload: dict[str, Any]) -> ExclusionReason | None:
    result = admit_rows(_snapshot([payload]).rows, _policy())
    if result.admitted:
        return None
    return result.excluded[0].reason


def test_floor_price_for_a_value_notice_is_higher_than_the_naive_product() -> None:
    """(예정가격 - A) * r + A 는 예정가격 * r 보다 `A * (1 - r)` 만큼 크다."""
    planned, rate, a_total = 1_000_000_000.0, 0.87745, 63_100_000.0
    correct = floor_price(planned_price=planned, floor_rate=rate, a_value_total=a_total)
    naive = planned * rate
    assert correct > naive
    assert correct - naive == pytest.approx(a_total * (1 - rate))
    # 02 §5.1 실측 — A/기초금액 6.31% 공고에서 하한가 차이는 기초금액의 약 77 bp 다.
    assert (correct - naive) / planned * 10_000.0 == pytest.approx(77.0, abs=1.0)


def test_floor_price_without_a_value_collapses_to_the_product() -> None:
    planned, rate = 1_000_000_000.0, 0.87995
    assert floor_price(
        planned_price=planned, floor_rate=rate, a_value_total=0.0
    ) == pytest.approx(planned * rate)


def test_pure_construction_floor_converts_the_base_amount_basis() -> None:
    value = pure_construction_floor(
        pure_construction_cost=800_000_000.0,
        base_amount=1_000_000_000.0,
        planned_price=990_000_000.0,
        ratio=0.98,
    )
    assert value == pytest.approx(800_000_000.0 * 0.99 * 0.98)


def test_is_eligible_requires_every_floor() -> None:
    assert is_eligible(100.0, (90.0, 99.0))
    assert not is_eligible(95.0, (90.0, 99.0))


def test_service_notice_resolves_a_value_as_zero() -> None:
    notice = _snapshot([row_payload("n-1")]).rows[0].notice
    assert notice.category is BusinessCategory.SERVICE
    assert resolve_a_value(notice) == 0.0


def test_construction_a_value_opened_after_bid_close_is_unusable() -> None:
    late = row_payload(
        "n-1",
        notice_category="CONSTRUCTION",
        notice_bid_price_formula_a_applicable=True,
        notice_bid_close_at="2026-06-10T10:00:00+09:00",
        notice_a_value={"total": 1.0, "open_at": "2026-06-11T10:00:00+09:00"},
        notice_pure_construction_cost=800_000_000.0,
        notice_noticed_on="2026-06-01",
    )
    assert _reason(late) is ExclusionReason.A_VALUE_ABSENT_OR_LATE


def test_construction_without_a_value_is_excluded() -> None:
    payload = row_payload(
        "n-1",
        notice_category="CONSTRUCTION",
        notice_bid_price_formula_a_applicable=True,
        notice_pure_construction_cost=800_000_000.0,
        notice_noticed_on="2026-06-01",
    )
    assert _reason(payload) is ExclusionReason.A_VALUE_ABSENT_OR_LATE


def test_construction_that_is_not_an_a_value_notice_resolves_a_as_zero() -> None:
    """`bidPrceCalclAYn` 이 거짓이면 A값 공고가 아니라 산식에 A 가 없다 — `a_value`
    부재만 보고 제외하면 A 를 쓰지 않는 공사가 통째로 빠진다(D-6G-19 수집 뒤)."""
    payload = row_payload(
        "c-0",
        notice_category="CONSTRUCTION",
        notice_bid_price_formula_a_applicable=False,
        notice_successful_bid_method_name="적격심사제-추정가격 100억원 미만 공사",
        notice_noticed_on="2026-03-01",
        notice_pure_construction_cost=700_000_000,
    )
    result = admit_rows(_snapshot([payload]).rows, _policy())
    assert result.admitted, result.excluded
    assert result.admitted[0].a_value_total == 0.0


def test_construction_without_the_a_applicability_flag_is_excluded() -> None:
    """판정 입력이 없으면 조용히 통과시키지 않는다(fail-closed)."""
    payload = row_payload(
        "c-0",
        notice_category="CONSTRUCTION",
        notice_successful_bid_method_name="적격심사제-추정가격 100억원 미만 공사",
        notice_noticed_on="2026-03-01",
        notice_pure_construction_cost=700_000_000,
    )
    assert _reason(payload) is ExclusionReason.A_VALUE_ABSENT_OR_LATE


@pytest.mark.parametrize(
    ("mutation", "reason"),
    [
        ({"notice_base_amount": None}, ExclusionReason.BASE_AMOUNT_ABSENT_OR_LATE),
        (
            {"outcome_opening_base_amount_null": True},
            ExclusionReason.OPENING_BASE_AMOUNT_ABSENT,
        ),
    ],
)
def test_base_amount_provenance_split_is_fail_closed(
    mutation: dict[str, Any], reason: ExclusionReason
) -> None:
    """D-6G-19 — 두 칸은 서로의 대용이 아니다. 한쪽이 비면 다른 쪽으로 메우지 않고
    제외한다(투찰 시점 칸이 비었는데 개찰 칸으로 메우면 그게 곧 누출이다)."""
    assert _reason(row_payload("n-1", **mutation)) is reason


def test_construction_without_pure_cost_is_excluded() -> None:
    payload = row_payload(
        "n-1",
        notice_category="CONSTRUCTION",
        notice_bid_price_formula_a_applicable=True,
        notice_a_value={"total": 1.0, "open_at": "2026-06-05T10:00:00+09:00"},
        notice_noticed_on="2026-06-01",
    )
    assert _reason(payload) is ExclusionReason.PURE_CONSTRUCTION_COST_ABSENT


@pytest.mark.parametrize(
    ("mutation", "reason"),
    [
        (
            {"notice_successful_bid_method_name": None},
            ExclusionReason.BID_METHOD_ABSENT,
        ),
        (
            {"notice_reserve_range_begin_rate": None},
            ExclusionReason.RESERVE_PRICE_RANGE_ABSENT,
        ),
        (
            {"notice_reserve_range_end_rate": None},
            ExclusionReason.RESERVE_PRICE_RANGE_ABSENT,
        ),
        ({"notice_progress_division": "유찰"}, ExclusionReason.REBID_OR_AMENDED),
        (
            {
                "notice_successful_bid_method_name": (
                    "소액수의견적(2인 이상 견적 제출)-국민연금보험료 등 합산액 감액 적용"
                )
            },
            ExclusionReason.SMALL_SUM_QUOTE,
        ),
        (
            {
                "notice_successful_bid_method_name": (
                    "조달청 중소기업자간 경쟁물품에 대한 계약이행능력심사 세부기준"
                )
            },
            ExclusionReason.SME_COMPETITION_SCREENING,
        ),
        (
            {"notice_successful_bid_method_name": "협상에의한계약"},
            ExclusionReason.NOT_QUALIFICATION_SCREENING,
        ),
        (
            {"notice_noticed_on": "2026-05-01"},
            ExclusionReason.FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY,
        ),
        ({"notice_notice_ordinal": 2}, ExclusionReason.REBID_OR_AMENDED),
        (
            {"notice_prearranged_price_decision_method": "단일예가"},
            ExclusionReason.SINGLE_PREARRANGED_PRICE,
        ),
        (
            {"notice_floor_rate": None},
            ExclusionReason.FLOOR_RATE_ABSENT_OR_OUT_OF_BAND,
        ),
        (
            {"notice_floor_rate": 0.2},
            ExclusionReason.FLOOR_RATE_ABSENT_OR_OUT_OF_BAND,
        ),
        (
            {"notice_floor_rate": 0.999},
            ExclusionReason.FLOOR_RATE_ABSENT_OR_OUT_OF_BAND,
        ),
        ({"outcome_prices_null": True}, ExclusionReason.RESERVE_DRAW_INCOMPLETE),
        ({"outcome_drawn": [1, 2, 3]}, ExclusionReason.RESERVE_DRAW_INCOMPLETE),
    ],
)
def test_exclusion_rules_fire_with_their_own_reason(
    mutation: dict[str, Any], reason: ExclusionReason
) -> None:
    assert _reason(row_payload("n-1", **mutation)) is reason


def test_ship_manufacturing_is_excluded_only_for_goods() -> None:
    goods = row_payload(
        "n-1",
        notice_category="GOODS",
        notice_noticed_on="2026-06-01",
        notice_successful_bid_method_name="적격심사 - 선박 제조(주요장비 가격 공개)",
    )
    assert _reason(goods) is ExclusionReason.SHIP_MANUFACTURING


def test_all_bidders_below_the_floor_yields_no_eligible_bidder() -> None:
    payload = row_payload("n-1", outcome_bidder_amounts=[1.0, 2.0, 3.0])
    assert _reason(payload) is ExclusionReason.NO_ELIGIBLE_BIDDER


def test_tied_lowest_eligible_bidder_is_excluded() -> None:
    snapshot = _snapshot([row_payload("n-1")])
    planned = snapshot.rows[0].outcome.planned_price
    tied = planned * 0.885
    payload = row_payload("n-1", outcome_bidder_amounts=[tied, tied, planned * 0.9])
    assert _reason(payload) is ExclusionReason.TIED_LOWEST


def test_admitted_notice_carries_the_actual_floor_and_lowest_eligible_amount() -> None:
    snapshot = _snapshot([row_payload("n-1")])
    result = admit_rows(snapshot.rows, _policy())
    assert len(result.admitted) == 1
    admitted = result.admitted[0]
    assert isinstance(admitted, AdmittedNotice)
    expected = floor_price(
        planned_price=admitted.row.outcome.planned_price,
        floor_rate=0.87745,
        a_value_total=0.0,
    )
    assert admitted.actual_floor_price == pytest.approx(expected)
    assert admitted.pure_cost_floor is None
    assert admitted.eligibility_floors == (admitted.actual_floor_price,)
    eligible = [
        row.amount
        for row in admitted.row.outcome.bidder_rows
        if row.amount >= admitted.actual_floor_price
    ]
    assert admitted.lowest_eligible_amount == pytest.approx(min(eligible))


def test_exclusion_counts_report_every_reason_including_zero() -> None:
    result = admit_rows(
        _snapshot(
            [
                row_payload("n-1"),
                row_payload("n-2", notice_progress_division="유찰"),
                row_payload("n-3", notice_progress_division="재입찰"),
            ]
        ).rows,
        _policy(),
    )
    counts = dict(exclusion_counts(result.excluded))
    assert len(counts) == len(ExclusionReason)
    assert counts[ExclusionReason.REBID_OR_AMENDED] == 2
    assert counts[ExclusionReason.TIED_LOWEST] == 0
    # ⑪⑫ 는 **한 번도 발화하지 않는다** — 0 은 「없었다」가 아니라 「가르지 못했다」·
    # 「들어오지 않았다」이고, 그 사실은 `undecidable` 이 따로 공시한다(D-6G-21).
    assert counts[ExclusionReason.LOCAL_GOVERNMENT] == 0
    assert counts[ExclusionReason.FOREIGN_CAPITAL] == 0
    assert dict(result.undecidable)[UndecidableAxis.LOCAL_GOVERNMENT] == len(
        result.admitted
    )


def test_exclusion_rule_order_covers_every_reason_exactly_once() -> None:
    assert sorted(EXCLUSION_RULE_ORDER) == sorted(ExclusionReason)
    assert len(set(EXCLUSION_RULE_ORDER)) == len(EXCLUSION_RULE_ORDER)


def test_admission_signature_has_no_strategy_parameter() -> None:
    """제외는 전략을 보기 전 입력 단계에서만 — 시그니처가 그것을 강제한다(우회 ④)."""
    parameters = set(inspect.signature(admit_rows).parameters)
    assert parameters == {"rows", "policy"}
