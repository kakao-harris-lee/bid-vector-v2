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
from dataclasses import replace
from datetime import date
from typing import Any

import numpy as np
import pytest
from _backtest_support import (
    SHIPPED_BACKTEST_POLICY_PATH,
    manifest_bytes,
    row_payload,
    rows_bytes,
    sample_list_bytes,
)

from ml_engine.evaluation.backtest.exclusions import AdmittedNotice, admit_rows
from ml_engine.evaluation.backtest.floor import floor_price
from ml_engine.evaluation.backtest.observations import (
    LoadedSnapshot,
)
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.backtest.snapshot import (
    load_snapshot,
)
from ml_engine.evaluation.backtest.strategies import (
    Abstained,
    AbstentionReason,
    BidAmount,
    InstitutionalMonteCarloStrategy,
    RuleAnchorStrategy,
    StrategyInput,
    UniformBandStrategy,
    _candidate_rates,
    _simulated_floors,
    _win_probabilities,
    build_competitor_pool,
    build_strategy_input,
)


def _policy() -> StrategyBacktestPolicy:
    loaded = load_strategy_backtest_policy(SHIPPED_BACKTEST_POLICY_PATH)
    assert isinstance(loaded, StrategyBacktestPolicy)
    return loaded


def _admitted(payloads: list[dict[str, Any]]) -> tuple[AdmittedNotice, ...]:
    rows = rows_bytes(payloads)
    snapshot = load_snapshot(manifest_bytes(rows), rows, sample_list_bytes(rows))
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
                notice_bid_price_formula_a_applicable=True,
                notice_noticed_on="2026-03-01",
                notice_successful_bid_method_name="적격심사제-추정가격 100억원 미만 공사",
                notice_a_value={
                    "total": 60_000_000,
                    "open_at": "2026-06-05T09:00:00+09:00",
                    "standard_market_price_applicable": False,
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


def _construction_admitted() -> AdmittedNotice:
    """S4 가 두 실격선을 다 보는지 재려고 쓰는 공사 공고. A 값 공고가 아니라 A = 0 이고,
    순공사원가선만 하한가 위로 올라간다."""
    return _admitted(
        [
            row_payload(
                "s4-c",
                notice_category="CONSTRUCTION",
                notice_bid_price_formula_a_applicable=False,
                notice_successful_bid_method_name="적격심사제-추정가격 100억원 미만 공사",
                notice_noticed_on="2026-03-01",
                notice_pure_construction_cost=900_000_000,
                outcome_opened_on="2026-06-15",
                outcome_bidder_amounts=[940_000_000.0, 960_000_000.0, 980_000_000.0],
            )
        ]
    )[0]


def test_s4_simulation_respects_the_pure_construction_cost_floor() -> None:
    """**code-review r2 H-2** — `_simulated_floors` 가 정의만 되고 **호출되지 않았다**.
    그래서 S4 는 채점이 쓰는 두 실격선 중 하나(순공사원가선)를 보지 못한 채 투찰률을
    골랐고, 대조표에는 이행으로 적혀 있었다.

    **승률 곡선으로 잰다.** `bid` 의 argmax 하나만 보면 최적점이 우연히 같은 자리에
    남아 변이를 놓친다(실측: 두 판 모두 같은 격자점을 골랐지만 그 점의 승률은 0.491 대
    0.387 로 달랐다).

    곡선이 **일률적으로 낮아지지는 않는다** — 순공사원가선은 나와 경쟁자에게 똑같이
    걸려서, 높은 투찰률에서는 경쟁자를 더 많이 떨어뜨려 승률을 **올린다**(실측으로
    확인했다). 잠금은 그래서 「낮아진다」가 아니라 **「낮은 쪽이 더 많이 죽는다」**다:
    그 선 아래 투찰률은 어떤 사정률에서도 부적격이라 승률이 정확히 0 이 된다."""
    policy = _policy()
    history = _history(10)
    admitted = _construction_admitted()
    assert admitted.pure_cost_floor is not None
    assert admitted.pure_cost_floor > admitted.actual_floor_price

    guarded_input = build_strategy_input(admitted, history, seed=7)
    # 한 변수만 다르게 — 순공사원가만 지운 같은 입력(그 공고는 제외 ⑨ 로 승인되지
    # 않으므로 판독을 거쳐 만들 수 없다).
    unguarded_input = replace(
        guarded_input, notice=replace(guarded_input.notice, pure_construction_cost=None)
    )
    rates = _candidate_rates(guarded_input, policy)
    guarded = _win_probabilities(guarded_input, policy, rates)
    unguarded = _win_probabilities(unguarded_input, policy, rates)

    assert (guarded != unguarded).any(), (
        "순공사원가선이 승률을 한 자리도 바꾸지 않았다 — 시뮬레이션이 그 선을 빼고 "
        "돈다(code-review r2 H-2)"
    )
    dead_with = int((guarded == 0.0).sum())
    dead_without = int((unguarded == 0.0).sum())
    assert dead_with > dead_without, (
        "순공사원가선 아래 투찰률이 더 죽지 않았다 — 그 선이 내 적격 판정에 "
        f"들어가지 않는다(승률 0 인 격자: {dead_with} vs {dead_without})"
    )


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


def test_strategy_uses_the_bid_time_base_amount_not_the_opening_one() -> None:
    """D-6G-19 — 두 기초금액을 **다르게** 두고, 전략이 투찰 시점 칸을 쓰는지 본다.
    둘이 같은 fixture 에서는 어느 쪽을 써도 test 가 통과해 provenance 분리가 잠기지
    않는다(변이 실측에서 드러난 구멍)."""
    admitted = _admitted(
        [
            row_payload(
                "t-split",
                outcome_opened_on="2026-06-15",
                outcome_opening_base_amount=900_000_000,
                outcome_bidder_amounts=[885_000_000.0, 890_000_000.0, 900_000_000.0],
            )
        ]
    )[0]
    assert admitted.base_amount != admitted.opening_base_amount
    assert not admitted.base_amount_matches
    request = build_strategy_input(admitted, (), seed=7)
    assert request.base_amount == admitted.base_amount
    outcome = UniformBandStrategy().bid(request, _policy())
    assert isinstance(outcome, BidAmount)
    rate = outcome.amount / admitted.base_amount
    assert 0.87745 * 0.98 <= rate <= 0.87745 * 1.02


def test_strategy_input_does_not_expose_the_admitted_notice() -> None:
    """`AdmittedNotice` 를 그대로 넘기면 개찰 결과가 전략에 닿는다 — 넘기지 않는다."""
    request = build_strategy_input(_target(), _history(2), seed=1)
    assert not any(
        isinstance(value, AdmittedNotice) for value in vars(request).values()
    )
    assert isinstance(request, StrategyInput)


# ── 비대칭 예가 범위의 가격 앵커 (계획 2026-10-28 P1) ────────────────────────
# 기존 test 는 **대칭 예가 + A = 0** 공고만 썼다. 그 판에서는 옛 앵커
# `하한율 x (1 +- 반폭)` 과 제도상 평균 하한율이 **같은 수**라, 앵커가 어긋나 있어도
# 전부 초록이었다(계획 §2 P1 실측: 예가 `[-2%, +4%]` 에서 중심이 0.87745 인데 제도
# 평균은 0.88622450). 아래는 그 두 수가 **갈리는** 판을 짓는다.


def _ranged_target(
    begin_rate: float,
    end_rate: float,
    *,
    a_total: int | None = None,
) -> AdmittedNotice:
    """예가 범위(그리고 필요하면 A)를 지정한 승인 공고 하나.

    A 를 주면 공사로 짓는다 — A 는 공사 산식에만 있다(`rules.resolve_a_value`)."""
    if a_total is None:
        return _admitted(
            [
                row_payload(
                    "range-1",
                    notice_reserve_range_begin_rate=begin_rate,
                    notice_reserve_range_end_rate=end_rate,
                    outcome_bidder_amounts=_TARGET_BIDDER_AMOUNTS,
                )
            ]
        )[0]
    return _admitted(
        [
            row_payload(
                "range-a-1",
                notice_category="CONSTRUCTION",
                notice_bid_price_formula_a_applicable=True,
                notice_noticed_on="2026-03-01",
                notice_successful_bid_method_name="적격심사제-시설공사",
                notice_a_value={
                    "total": a_total,
                    "open_at": "2026-06-05T09:00:00+09:00",
                    "standard_market_price_applicable": False,
                },
                notice_pure_construction_cost=700_000_000,
                notice_reserve_range_begin_rate=begin_rate,
                notice_reserve_range_end_rate=end_rate,
                outcome_bidder_amounts=_TARGET_BIDDER_AMOUNTS,
            )
        ]
    )[0]


def _formula_anchor(request: StrategyInput) -> tuple[float, float]:
    """계획 §2 P1 의 수정식을 **test 안에서 따로** 계산한다 — 구현의 속성을 불러
    쓰면 식이 아니라 자기 자신을 재게 된다.

    `expected_floor_rate = 하한율 x E[R] + (1 - 하한율) x A / 기초금액`,
    반폭 `= 하한율 x 예가 반폭`."""
    expected_ratio = (
        1.0 + (request.reserve_range_begin_rate + request.reserve_range_end_rate) / 2.0
    )
    anchor = request.floor_rate * expected_ratio + (1.0 - request.floor_rate) * (
        request.a_value_total / request.base_amount
    )
    half_width = (
        request.floor_rate
        * (request.reserve_range_end_rate - request.reserve_range_begin_rate)
        / 2.0
    )
    return (anchor, half_width)


class _EndpointRecorder:
    """`uniform(low, high)` 의 인자를 포획하는 결정적 가짜 난수원(계획 §3.1 ③).

    S0 는 밴드 안에서 **난수 하나**를 뽑으므로 금액만 보면 끝점을 되돌릴 수 없다 —
    끝점을 직접 포획해야 「밴드가 수정식의 끝점인가」를 잴 수 있다."""

    def __init__(self) -> None:
        self.calls: list[tuple[float, float]] = []

    def uniform(self, low: float, high: float) -> float:
        self.calls.append((low, high))
        return low


def _record_s0_band(
    monkeypatch: pytest.MonkeyPatch, request: StrategyInput
) -> tuple[tuple[float, float], BidAmount]:
    recorder = _EndpointRecorder()
    monkeypatch.setattr(
        "ml_engine.evaluation.backtest.strategies.notice_rng",
        lambda request, *, stream: recorder,
    )
    outcome = UniformBandStrategy().bid(request, _policy())
    assert isinstance(outcome, BidAmount), outcome
    assert len(recorder.calls) == 1, recorder.calls
    return (recorder.calls[0], outcome)


@pytest.mark.parametrize(
    ("begin_rate", "end_rate", "expected_ratio"),
    [(-0.02, 0.04, 1.01), (-0.04, 0.02, 0.99)],
)
def test_expected_assessment_ratio_is_the_midpoint_of_an_asymmetric_range(
    begin_rate: float, end_rate: float, expected_ratio: float
) -> None:
    """계획 §3.1 ① — 비대칭 범위의 이론 평균 사정률(20만 회 직접 시뮬레이션이 `[-2%,
    +4%]` 에서 1.01000898 을 냈다, 계획 §2 P1 실측)."""
    request = build_strategy_input(_ranged_target(begin_rate, end_rate), (), seed=1)
    assert request.expected_assessment_ratio == pytest.approx(expected_ratio)


def test_expected_floor_rate_shares_one_formula_for_a_zero_and_a_positive() -> None:
    """계획 §3.1 ② · §3.2 — 평균 하한율과 반폭은 `StrategyInput` 의 **한 자리**에서
    나오고, A = 0 과 A > 0 이 같은 식을 쓴다(A = 0 은 그 식의 특수경우다)."""
    plain = build_strategy_input(_ranged_target(-0.02, 0.04), (), seed=1)
    assert plain.a_value_total == 0.0
    anchor, half_width = _formula_anchor(plain)
    assert plain.expected_floor_rate == pytest.approx(anchor)
    assert plain.floor_rate_half_width == pytest.approx(half_width)

    with_a = build_strategy_input(
        _ranged_target(-0.02, 0.04, a_total=60_000_000), (), seed=1
    )
    assert with_a.a_value_total == 60_000_000.0
    a_anchor, a_half_width = _formula_anchor(with_a)
    assert with_a.expected_floor_rate == pytest.approx(a_anchor)
    assert with_a.floor_rate_half_width == pytest.approx(a_half_width)
    # A 보정이 실제로 **위로** 민다 — A 가 하한가의 고정분이기 때문이다.
    assert a_anchor > anchor


def test_s0_band_endpoints_are_the_corrected_anchor(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """계획 §3.1 ③ — S0 의 `low/high` 가 수정식의 끝점이다(A > 0 · 비대칭 범위)."""
    request = build_strategy_input(
        _ranged_target(-0.02, 0.04, a_total=60_000_000), (), seed=7
    )
    anchor, half_width = _formula_anchor(request)
    (low, high), outcome = _record_s0_band(monkeypatch, request)
    assert low == pytest.approx(anchor - half_width)
    assert high == pytest.approx(anchor + half_width)
    # 옛 앵커(`하한율 x (1 +- 반폭)`)와 **다른 수**라야 이 판이 무언가를 잰다.
    assert low != pytest.approx(request.floor_rate * (1.0 - request.reserve_half_width))
    assert outcome.amount == pytest.approx(math.ceil(request.base_amount * low))


def test_symmetric_range_without_a_value_keeps_the_previous_s0_band(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """계획 §3.1 ⑥ — 대칭 범위 · A = 0 에서는 수정식이 **옛 기준으로 접힌다**.

    E[R] = 1 이고 A = 0 이면 `하한율 x 1 + 0` 이라 중심이 하한율이고, 반폭도
    `하한율 x 반폭` 이다 — 기존 실험의 S0 밴드와 같은 두 수다. 이 자리가 깨지면
    수정이 대칭 공고의 거동까지 바꾼 것이다."""
    request = build_strategy_input(_target(), (), seed=7)
    assert request.expected_assessment_ratio == pytest.approx(1.0)
    assert request.a_value_total == 0.0
    (low, high), _ = _record_s0_band(monkeypatch, request)
    assert low == pytest.approx(request.floor_rate * (1.0 - request.reserve_half_width))
    assert high == pytest.approx(
        request.floor_rate * (1.0 + request.reserve_half_width)
    )


def test_s4_grid_runs_from_the_corrected_anchor_lower_edge() -> None:
    """계획 §3.1 ④ — S4 격자의 첫 점이 수정된 하단이고 마지막 점은 `하단 + 정책 폭`
    이다. 격자 수·폭은 정책 그대로다(이 slice 는 그 둘을 바꾸지 않는다)."""
    policy = _policy()
    request = build_strategy_input(
        _ranged_target(-0.02, 0.04, a_total=60_000_000), (), seed=7
    )
    anchor, half_width = _formula_anchor(request)
    rates = _candidate_rates(request, policy)
    span = policy.strategies.s4_grid_span_bp / 10_000.0
    assert len(rates) == policy.strategies.s4_grid_size
    assert rates[0] == pytest.approx(anchor - half_width)
    assert rates[-1] == pytest.approx(anchor - half_width + span)


# ── 순공사원가 98% 실격선의 적용 범위 (계획 2026-10-28 P2) ───────────────────


def test_s4_pure_cost_line_follows_the_planned_price_cap() -> None:
    """계획 §3.1 ⑤ — S4 는 **시뮬레이션 표본마다** 예정가격으로 적용을 판정한다.

    100억원을 가로지르는 공고에서 표본별로 적용 여부가 갈린다: 상한 **아래** 표본은
    순공사원가선이 서고, 상한과 그 **위** 표본은 하한가만 남는다(정확히 상한은
    미적용 — 배타 경계)."""
    policy = _policy()
    cap = policy.floor.pure_construction_cost_max_planned_price_exclusive
    base = cap
    cost = cap * 0.95
    source = build_strategy_input(_construction_admitted(), (), seed=7)
    request = replace(
        source,
        base_amount=base,
        a_value_total=0.0,
        notice=replace(source.notice, pure_construction_cost=cost),
    )
    ratios = np.array([(cap - 1.0) / base, cap / base, (cap + 1.0) / base])
    floors = _simulated_floors(request, policy, ratios)
    plain = np.array(
        [
            floor_price(
                planned_price=ratio * base,
                floor_rate=request.floor_rate,
                a_value_total=0.0,
            )
            for ratio in ratios
        ]
    )
    # 이 공고에서는 순공사원가선이 하한가보다 **위**다 — 아니면 적용 여부가 보이지 않는다.
    assert cost * policy.floor.pure_construction_cost_ratio > plain[0]
    assert floors[0] > plain[0], "상한 아래 표본에서 순공사원가선이 서지 않았다"
    assert floors[1] == pytest.approx(plain[1]), "정확히 상한인데 규칙이 적용됐다"
    assert floors[2] == pytest.approx(plain[2]), "상한 위인데 규칙이 적용됐다"
