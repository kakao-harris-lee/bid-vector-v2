"""RED — M6/6G D-6G-4·D-6G-10 과 판정 산술(McNemar 정확 검정).

지표 쪽에서 잠그는 것: ① 적격은 실격선 **전부**를 넘어야 한다(공사는 둘) ② bp 는
적격일 때만 나온다 ③ would-have-won 은 1위가 아니라 **적격 투찰자 중 최저**와 겨룬다
④ 기권은 표본에서 빠지지 않고 「부적격·패」로 센다.

검정 쪽에서 잠그는 것: 이항 꼬리가 알려진 값과 맞고(손으로 셀 수 있는 작은 n),
단측 p 가 대칭을 깨며, 필요 표본 수가 「모른다」를 큰 수로 위장하지 않는다.
"""

from __future__ import annotations

import math
from typing import Any

import pytest
from _backtest_support import (
    SHIPPED_BACKTEST_POLICY_PATH,
    manifest_bytes,
    row_payload,
    rows_bytes,
)

from ml_engine.evaluation.backtest.exclusions import AdmittedNotice, admit_rows
from ml_engine.evaluation.backtest.mcnemar import (
    DiscordantCounts,
    alternative_success_probability,
    binomial_upper_tail,
    critical_count,
    discordant_counts,
    one_sided_p_value,
    power_at,
    required_discordant_pairs,
)
from ml_engine.evaluation.backtest.metrics import (
    score_notice,
    score_strategy,
    scored_notice_keys,
)
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.backtest.snapshot import LoadedSnapshot, load_snapshot
from ml_engine.evaluation.backtest.strategies import (
    Abstained,
    AbstentionReason,
    BidAmount,
)


def _policy() -> StrategyBacktestPolicy:
    loaded = load_strategy_backtest_policy(SHIPPED_BACKTEST_POLICY_PATH)
    assert isinstance(loaded, StrategyBacktestPolicy)
    return loaded


def _admitted(payload: dict[str, Any]) -> AdmittedNotice:
    rows = rows_bytes([payload])
    snapshot = load_snapshot(manifest_bytes(rows), rows)
    assert isinstance(snapshot, LoadedSnapshot), snapshot
    result = admit_rows(snapshot.rows, _policy())
    assert result.admitted, result.excluded
    return result.admitted[0]


def test_bid_below_the_floor_is_ineligible_and_carries_no_basis_points() -> None:
    admitted = _admitted(row_payload("n-1"))
    score = score_notice(admitted, BidAmount(admitted.actual_floor_price - 1.0))
    assert not score.eligible
    assert score.basis_points_above_floor is None
    assert not score.would_have_won


def test_bid_at_the_floor_is_eligible_with_zero_basis_points() -> None:
    admitted = _admitted(row_payload("n-1"))
    score = score_notice(admitted, BidAmount(admitted.actual_floor_price))
    assert score.eligible
    assert score.basis_points_above_floor == pytest.approx(0.0)


def test_would_have_won_compares_against_the_lowest_eligible_bidder() -> None:
    admitted = _admitted(row_payload("n-1"))
    lowest = admitted.lowest_eligible_amount
    assert score_notice(admitted, BidAmount(lowest - 1.0)).would_have_won
    assert not score_notice(admitted, BidAmount(lowest)).would_have_won
    assert not score_notice(admitted, BidAmount(lowest + 1.0)).would_have_won


def test_abstention_is_counted_as_ineligible_loss_not_dropped() -> None:
    admitted = _admitted(row_payload("n-1"))
    score = score_notice(
        admitted, Abstained(AbstentionReason.INSUFFICIENT_COMPETITOR_SAMPLES)
    )
    assert not score.eligible
    assert not score.would_have_won
    assert score.abstention is AbstentionReason.INSUFFICIENT_COMPETITOR_SAMPLES
    assert score.notice_key_hash == admitted.row.notice.notice_key_hash


def test_construction_second_floor_makes_an_otherwise_eligible_bid_ineligible() -> None:
    """순공사원가 98% 선이 하한가보다 높으면 그 사이 금액은 실격이다(P-3 §4.3)."""
    admitted = _admitted(
        row_payload(
            "c-1",
            notice_category="CONSTRUCTION",
            notice_bid_price_formula_a_applicable=True,
            notice_noticed_on="2026-03-01",
            notice_a_value={
                "total": 60_000_000.0,
                "open_at": "2026-06-05T09:00:00+09:00",
            },
            notice_pure_construction_cost=930_000_000.0,
            outcome_bidder_amounts=[960_000_000.0, 980_000_000.0],
        )
    )
    assert admitted.pure_cost_floor is not None
    assert admitted.pure_cost_floor > admitted.actual_floor_price
    between = (admitted.actual_floor_price + admitted.pure_cost_floor) / 2.0
    assert not score_notice(admitted, BidAmount(between)).eligible
    assert score_notice(admitted, BidAmount(admitted.pure_cost_floor)).eligible


def test_score_strategy_keeps_the_notice_order_and_rejects_length_mismatch() -> None:
    admitted = [_admitted(row_payload("n-1")), _admitted(row_payload("n-2"))]
    scores = score_strategy(
        "S0", admitted, [BidAmount(1.0), Abstained(AbstentionReason.NON_FINITE_RESULT)]
    )
    assert scored_notice_keys(scores) == tuple(
        item.row.notice.notice_key_hash for item in admitted
    )
    assert scores.abstention_count == 1
    assert scores.ineligible_rate == pytest.approx(1.0)
    with pytest.raises(ValueError, match="개수가 같아야"):
        score_strategy("S0", admitted, [BidAmount(1.0)])


def test_basis_point_quartiles_are_none_without_any_eligible_notice() -> None:
    admitted = [_admitted(row_payload("n-1"))]
    scores = score_strategy("S0", admitted, [BidAmount(1.0)])
    assert scores.basis_point_quartiles is None


def test_binomial_upper_tail_matches_hand_counted_values() -> None:
    assert binomial_upper_tail(4, 0, 0.5) == pytest.approx(1.0)
    assert binomial_upper_tail(4, 5, 0.5) == pytest.approx(0.0)
    # P(X >= 3), X ~ Bin(4, 0.5) = (4 + 1) / 16
    assert binomial_upper_tail(4, 3, 0.5) == pytest.approx(5 / 16)
    # P(X >= 8), X ~ Bin(10, 0.5) = (45 + 10 + 1) / 1024
    assert binomial_upper_tail(10, 8, 0.5) == pytest.approx(56 / 1024)


def test_binomial_upper_tail_does_not_underflow_for_large_n() -> None:
    value = binomial_upper_tail(20_000, 10_400, 0.5)
    assert 0.0 < value < 1.0
    assert math.isfinite(value)


def test_discordant_counts_ignore_agreeing_pairs() -> None:
    counts = discordant_counts(
        [True, True, False, False, True], [True, False, True, False, False]
    )
    assert counts == DiscordantCounts(strategy_only=2, baseline_only=1)
    assert counts.total == 3


def test_discordant_counts_reject_unpaired_lengths() -> None:
    with pytest.raises(ValueError, match="같은 길이"):
        discordant_counts([True], [True, False])


def test_one_sided_p_value_is_asymmetric() -> None:
    winning = one_sided_p_value(DiscordantCounts(strategy_only=9, baseline_only=1))
    losing = one_sided_p_value(DiscordantCounts(strategy_only=1, baseline_only=9))
    assert winning < 0.05 < losing
    assert one_sided_p_value(DiscordantCounts(0, 0)) == pytest.approx(1.0)


def test_critical_count_is_none_when_no_outcome_can_reject() -> None:
    assert critical_count(3, 0.05 / 3) is None
    assert critical_count(20, 0.05 / 3) is not None


def test_power_rises_with_the_effect_and_the_sample() -> None:
    small = power_at(50, 0.65, 0.05 / 3)
    large = power_at(200, 0.65, 0.05 / 3)
    stronger = power_at(50, 0.80, 0.05 / 3)
    assert small < large
    assert small < stronger


def test_required_pairs_is_none_when_the_alternative_is_not_better() -> None:
    assert (
        required_discordant_pairs(success_probability=0.5, alpha=0.05, target_power=0.8)
        is None
    )
    assert (
        required_discordant_pairs(success_probability=0.4, alpha=0.05, target_power=0.8)
        is None
    )


def test_required_pairs_reaches_the_target_power() -> None:
    pairs = required_discordant_pairs(
        success_probability=0.7, alpha=0.05 / 3, target_power=0.8
    )
    assert pairs is not None
    assert power_at(pairs, 0.7, 0.05 / 3) >= 0.8


def test_alternative_success_probability_maps_delta_onto_discordant_pairs() -> None:
    probability = alternative_success_probability(
        window_rows=500, baseline_win_rate=0.10, relative_improvement=0.20, pairs=50
    )
    # n*w0*Δ = 10 건의 승수 차이가 불일치 쌍 50 개에 실린다 -> 0.5 + 10/100
    assert probability == pytest.approx(0.6)
    assert alternative_success_probability(
        window_rows=500, baseline_win_rate=0.10, relative_improvement=0.20, pairs=0
    ) == pytest.approx(0.5)
