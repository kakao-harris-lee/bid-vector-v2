"""RED — M6/6G D-6G-3 과 위협 모델 ① (누출 없음).

이 test 의 핵심은 **누출 잠금 둘**이다:
1. `build_competitor_pool` 이 대상 공고 자신의 개찰 결과를 표본에 넣지 않는다. 대상
   공고를 이력에 **심어** 놓고 확인한다 — 절단을 `<` 에서 `<=` 로 바꾸면 붉어진다.
2. `StrategyInput` 에 개찰 결과 이름이 없다. 전략이 예정가격·추첨 번호·투찰자 행·참가자
   수를 보려면 타입을 고쳐야 하고 그 편집은 diff 에 드러난다.

그 밖에 S0 의 밴드·결정성, S1 의 산식, S4 의 기권·결정성을 잠근다.
"""

from __future__ import annotations

import math
from datetime import date
from typing import Any

import pytest
from _backtest_support import (
    SHIPPED_BACKTEST_POLICY_PATH,
    manifest_bytes,
    row_payload,
    rows_bytes,
)

from ml_engine.evaluation.backtest.exclusions import AdmittedNotice, admit_rows
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.backtest.snapshot import LoadedSnapshot, load_snapshot
from ml_engine.evaluation.backtest.strategies import (
    Abstained,
    AbstentionReason,
    BidAmount,
    InstitutionalMonteCarloStrategy,
    RuleAnchorStrategy,
    StrategyInput,
    UniformBandStrategy,
    build_competitor_pool,
    build_strategy_input,
)


def _policy() -> StrategyBacktestPolicy:
    loaded = load_strategy_backtest_policy(SHIPPED_BACKTEST_POLICY_PATH)
    assert isinstance(loaded, StrategyBacktestPolicy)
    return loaded


def _admitted(payloads: list[dict[str, Any]]) -> tuple[AdmittedNotice, ...]:
    rows = rows_bytes(payloads)
    snapshot = load_snapshot(manifest_bytes(rows), rows)
    assert isinstance(snapshot, LoadedSnapshot), snapshot
    result = admit_rows(snapshot.rows, _policy())
    assert not result.excluded, result.excluded
    return result.admitted


def _history(
    count: int, *, opened_on: str = "2026-06-08"
) -> tuple[AdmittedNotice, ...]:
    return _admitted(
        [
            row_payload(f"h-{index}", outcome_opened_on=opened_on)
            for index in range(count)
        ]
    )


# 대상 공고의 투찰 금액은 이력과 **겹치지 않게** 지어낸다 — 겹치면 누출 test 가
# "절단이 동작했다"와 "우연히 같은 값이다"를 구별하지 못한다.
_TARGET_BIDDER_AMOUNTS = [871_111_111.0, 873_222_222.0, 877_333_333.0, 881_444_444.0]


def _construction_target() -> AdmittedNotice:
    """S1 이 채점하는 유일한 업무 — 공사. A 와 순공사원가가 모두 있어야 승인된다."""
    return _admitted(
        [
            row_payload(
                "c-1",
                notice_category="CONSTRUCTION",
                notice_noticed_on="2026-03-01",
                notice_successful_bid_method_name="적격심사제-추정가격 100억원 미만 공사",
                notice_a_value={
                    "total": 60_000_000,
                    "open_at": "2026-06-05T09:00:00+09:00",
                },
                notice_pure_construction_cost=700_000_000,
                outcome_opened_on="2026-06-15",
                outcome_bidder_amounts=_TARGET_BIDDER_AMOUNTS,
            )
        ]
    )[0]


def _target() -> AdmittedNotice:
    return _admitted(
        [
            row_payload(
                "t-1",
                outcome_opened_on="2026-06-15",
                outcome_bidder_amounts=_TARGET_BIDDER_AMOUNTS,
            )
        ]
    )[0]


def test_target_own_opening_result_never_enters_the_competitor_pool() -> None:
    """대상 공고를 이력에 **심는다** — 절단이 없으면 자기 개찰 결과가 표본에 섞인다."""
    target = _target()
    poisoned = (*_history(2), target)
    pool = build_competitor_pool(poisoned, before=target.row.outcome.opened_on)
    target_rates = {
        row.amount / target.row.notice.base_amount
        for row in target.row.outcome.bidder_rows
    }
    assert not target_rates & {item.bid_rate for item in pool}
    assert all(item.opened_on < target.row.outcome.opened_on for item in pool)


def test_same_day_opening_is_excluded_from_the_pool() -> None:
    """경계는 엄격한 `<` — 같은 날 개찰분의 결과는 투찰 시점에 알 수 없다."""
    target = _target()
    same_day = _history(2, opened_on="2026-06-15")
    assert build_competitor_pool(same_day, before=target.row.outcome.opened_on) == ()
    earlier = _history(2, opened_on="2026-06-14")
    assert build_competitor_pool(earlier, before=target.row.outcome.opened_on)


def test_strategy_input_has_no_opening_result_attribute() -> None:
    request = build_strategy_input(_target(), _history(2), seed=1)
    forbidden = {
        "planned_price",
        "reserve_prices",
        "drawn_serial_numbers",
        "bidder_rows",
        "participant_count",
        "outcome",
        "opened_on",
    }
    assert not forbidden & set(vars(request))
    assert not forbidden & set(vars(request.notice))


def test_expected_participant_count_comes_from_history_not_the_target() -> None:
    """대상 공고의 참가자 수는 개찰 결과다 — 쓰지 않는다. 지난 창 공고들의 투찰자
    수 중앙값으로 센다(목록 축 `participant_count` 는 부재 가능이라 쓰지 않는다)."""
    target = _admitted(
        [
            row_payload(
                "t-1",
                outcome_opened_on="2026-06-15",
                outcome_bidder_amounts=_TARGET_BIDDER_AMOUNTS,
            )
        ]
    )[0]
    history = _admitted(
        [
            row_payload(
                f"h-{index}",
                outcome_opened_on="2026-06-08",
                outcome_bidder_amounts=[880_000_000.0, 890_000_000.0, 900_000_000.0],
            )
            for index in range(3)
        ]
    )
    request = build_strategy_input(target, history, seed=1)
    assert request.expected_participant_count == 3
    assert target.participant_count == len(_TARGET_BIDDER_AMOUNTS)


def test_reserve_half_width_and_expected_ratio_come_from_the_notice() -> None:
    request = build_strategy_input(_target(), _history(2), seed=1)
    assert request.reserve_half_width == pytest.approx(0.02)
    assert request.expected_assessment_ratio == pytest.approx(1.0)


def test_s0_draws_inside_the_band_and_is_deterministic_per_seed() -> None:
    policy = _policy()
    target = _target()
    strategy = UniformBandStrategy()
    first = strategy.bid(build_strategy_input(target, (), seed=7), policy)
    again = strategy.bid(build_strategy_input(target, (), seed=7), policy)
    other = strategy.bid(build_strategy_input(target, (), seed=42), policy)
    assert isinstance(first, BidAmount)
    assert isinstance(again, BidAmount)
    assert isinstance(other, BidAmount)
    assert first.amount == again.amount
    assert first.amount != other.amount
    base = target.row.notice.base_amount
    rate = first.amount / base
    assert 0.87745 * 0.98 <= rate <= 0.87745 * 1.02


def test_s0_differs_between_notices_under_the_same_seed() -> None:
    """공고 키가 난수원에 들어간다 — 한 seed 아래 모든 공고가 같은 값을 내면 그
    자체가 결함이다."""
    policy = _policy()
    strategy = UniformBandStrategy()
    amounts = {
        strategy.bid(build_strategy_input(item, (), seed=7), policy)
        for item in _admitted([row_payload(f"t-{index}") for index in range(5)])
    }
    assert len(amounts) == 5


def test_s1_is_the_floor_plus_offset_and_does_not_multiply_expected_ratio() -> None:
    """D-6G-15 — legacy 앵커는 `floor + offset` 이고 `x E[예정가]` 변형은 기각됐다."""
    policy = _policy()
    target = _construction_target()
    request = build_strategy_input(target, (), seed=1)
    outcome = RuleAnchorStrategy().bid(request, policy)
    assert isinstance(outcome, BidAmount)
    expected_rate = target.floor_rate + policy.strategies.s1_offset_bp / 10_000.0
    assert outcome.amount == pytest.approx(
        math.ceil(target.row.notice.base_amount * expected_rate)
    )


def test_s1_abstains_outside_construction() -> None:
    """offset 은 legacy 가 공사 정착행에서 잰 값이라 용역·물품에 옮길 근거가 없다 —
    관측값으로 새 상수를 만들지 않고 기권으로 공시한다(D-6G-15)."""
    outcome = RuleAnchorStrategy().bid(
        build_strategy_input(_target(), (), seed=1), _policy()
    )
    assert isinstance(outcome, Abstained)
    assert outcome.reason is AbstentionReason.NOT_APPLICABLE_CATEGORY


def test_bid_amounts_are_whole_won_rounded_up() -> None:
    policy = _policy()
    for strategy, target in (
        (UniformBandStrategy(), _target()),
        (RuleAnchorStrategy(), _construction_target()),
    ):
        outcome = strategy.bid(build_strategy_input(target, (), seed=7), policy)
        assert isinstance(outcome, BidAmount)
        assert outcome.amount == float(int(outcome.amount))


def test_s4_abstains_without_enough_competitor_samples() -> None:
    policy = _policy()
    outcome = InstitutionalMonteCarloStrategy().bid(
        build_strategy_input(_target(), (), seed=1), policy
    )
    assert isinstance(outcome, Abstained)
    assert outcome.reason is AbstentionReason.INSUFFICIENT_COMPETITOR_SAMPLES


def test_s4_picks_a_grid_point_and_repeats_under_the_same_seed() -> None:
    policy = _policy()
    target = _target()
    history = _history(10)
    request = build_strategy_input(target, history, seed=7)
    assert len(request.competitors) >= policy.strategies.s4_min_competitor_samples
    strategy = InstitutionalMonteCarloStrategy()
    first = strategy.bid(request, policy)
    again = strategy.bid(build_strategy_input(target, history, seed=7), policy)
    assert isinstance(first, BidAmount)
    assert isinstance(again, BidAmount)
    assert first.amount == again.amount
    rate = first.amount / target.row.notice.base_amount
    low = target.floor_rate * (1.0 - request.reserve_half_width)
    assert low <= rate <= low + policy.strategies.s4_grid_span_bp / 10_000.0 + 1e-9


def test_strategy_names_are_the_preregistered_labels() -> None:
    assert UniformBandStrategy().name == "S0"
    assert RuleAnchorStrategy().name == "S1"
    assert InstitutionalMonteCarloStrategy().name == "S4"


def test_competitor_pool_carries_no_identifier() -> None:
    """공고 식별자·상호가 표본에 없다 — 필드 전수 대조로 잠근다(D-6G-10).
    예비가격·추첨 번호는 **지난** 공고의 것이라 누출이 아니고, S2 가 표본마다 요구한다."""
    pool = build_competitor_pool(_history(2), before=date(2026, 6, 15))
    assert pool
    for item in pool:
        assert set(vars(item)) == {
            "opened_on",
            "bid_rate",
            "participant_count",
            "base_amount",
            "category",
            "reserve_prices",
            "drawn_serial_numbers",
        }


def test_strategy_input_does_not_expose_the_admitted_notice() -> None:
    """`AdmittedNotice` 를 그대로 넘기면 개찰 결과가 전략에 닿는다 — 넘기지 않는다."""
    request = build_strategy_input(_target(), _history(2), seed=1)
    assert not any(
        isinstance(value, AdmittedNotice) for value in vars(request).values()
    )
    assert isinstance(request, StrategyInput)
