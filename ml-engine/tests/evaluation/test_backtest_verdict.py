"""RED — M6/6G D-6G-6 판정식. 5C-2 `test_verdict.py` 와 같은 갈래로, 「못 이겼다」와
「못 쟀다」가 갈리는 자리를 잠근다.

특히 잠그는 것 둘:
1. **Bonferroni 가 실제로 쓰인다** — 판정이 `alpha` 가 아니라 `primary_alpha` 를 본다.
   정책 객체가 보정을 낸다는 것만으로는 부족하다: 판정 코드가 그 값을 **쓰지 않으면**
   보정이 장식이 된다(우회 ⑥).
2. **실격률 비열등이 통과를 막는다** — 승률에서 이겨도 실격률이 한계를 넘으면 Passed 가
   아니다.
"""

from __future__ import annotations

import pytest
from _backtest_support import SHIPPED_BACKTEST_POLICY_PATH

from ml_engine.evaluation.backtest.mcnemar import DiscordantCounts, one_sided_p_value
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.backtest.verdict import (
    StrategyFailed,
    StrategyNotEvaluable,
    StrategyPassed,
    WindowOutcome,
    passes_window,
    relative_gain,
    strategy_verdict,
)
from ml_engine.evaluation.verdict import NotEvaluableReason


def _policy() -> StrategyBacktestPolicy:
    loaded = load_strategy_backtest_policy(SHIPPED_BACKTEST_POLICY_PATH)
    assert isinstance(loaded, StrategyBacktestPolicy)
    return loaded


def _outcome(
    *,
    index: int | None,
    gain: float,
    p_value: float,
    pairs: int = 100,
    required: int | None = 10,
    policy: StrategyBacktestPolicy | None = None,
) -> WindowOutcome:
    rules = policy or _policy()
    return WindowOutcome(
        window_index=index,
        row_count=500,
        baseline_win_rate=0.10,
        strategy_win_rate=0.10 * (1.0 + gain),
        relative_gain=gain,
        discordant=DiscordantCounts(strategy_only=pairs, baseline_only=0),
        p_value=p_value,
        required_discordant_pairs=required,
        passed=passes_window(gain, p_value, rules),
    )


def test_relative_gain_is_positive_when_the_strategy_wins_more() -> None:
    assert relative_gain(0.10, 0.12) == pytest.approx(0.20)
    assert relative_gain(0.10, 0.08) == pytest.approx(-0.20)
    assert relative_gain(0.0, 0.05) == pytest.approx(0.0)


def test_verdict_uses_the_bonferroni_corrected_alpha_not_the_raw_alpha() -> None:
    """보정 전후 **사이**의 p 값을 넣는다 — 보정을 쓰면 실패하고, 안 쓰면 통과한다."""
    policy = _policy()
    between = (policy.verdict.primary_alpha + policy.verdict.alpha) / 2.0
    assert policy.verdict.primary_alpha < between < policy.verdict.alpha
    gain = policy.verdict.min_relative_improvement
    assert not passes_window(gain, between, policy)
    assert passes_window(gain, policy.verdict.primary_alpha, policy)


def test_verdict_requires_the_effect_size_not_just_significance() -> None:
    policy = _policy()
    tiny = policy.verdict.min_relative_improvement / 2.0
    assert not passes_window(tiny, 0.0, policy)


def test_too_few_windows_is_not_evaluable_and_hides_the_pooled_result() -> None:
    policy = _policy()
    pooled = _outcome(index=None, gain=0.5, p_value=0.0, policy=policy)
    outcome = strategy_verdict(
        strategy_name="S2b",
        windows=[_outcome(index=0, gain=0.5, p_value=0.0, policy=policy)],
        pooled=pooled,
        ineligibility_delta=0.0,
        seed_sign_consistent=True,
        policy=policy,
    )
    assert isinstance(outcome, StrategyNotEvaluable)
    assert outcome.reason is NotEvaluableReason.NO_EVALUABLE_WINDOW
    assert outcome.pooled is None


def test_seed_instability_outranks_the_judgement() -> None:
    policy = _policy()
    windows = [
        _outcome(index=index, gain=0.5, p_value=0.0, policy=policy)
        for index in range(3)
    ]
    outcome = strategy_verdict(
        strategy_name="S2b",
        windows=windows,
        pooled=_outcome(index=None, gain=0.5, p_value=0.0, policy=policy),
        ineligibility_delta=0.0,
        seed_sign_consistent=False,
        policy=policy,
    )
    assert isinstance(outcome, StrategyNotEvaluable)
    assert outcome.reason is NotEvaluableReason.SEED_UNSTABLE


def test_underpowered_only_when_the_point_estimate_is_improving() -> None:
    policy = _policy()
    improving = _outcome(
        index=None, gain=0.5, p_value=0.9, pairs=5, required=500, policy=policy
    )
    assert improving.underpowered
    worse = _outcome(
        index=None, gain=-0.5, p_value=0.9, pairs=5, required=500, policy=policy
    )
    assert not worse.underpowered


def test_underpowered_pooled_result_is_not_evaluable() -> None:
    policy = _policy()
    windows = [
        _outcome(index=index, gain=0.5, p_value=0.0, policy=policy)
        for index in range(3)
    ]
    outcome = strategy_verdict(
        strategy_name="S2b",
        windows=windows,
        pooled=_outcome(
            index=None, gain=0.5, p_value=0.9, pairs=5, required=500, policy=policy
        ),
        ineligibility_delta=0.0,
        seed_sign_consistent=True,
        policy=policy,
    )
    assert isinstance(outcome, StrategyNotEvaluable)
    assert outcome.reason is NotEvaluableReason.UNDERPOWERED
    assert outcome.required_discordant_pairs == 500


def test_passing_requires_majority_windows_pooled_and_non_inferior_ineligibility() -> (
    None
):
    policy = _policy()
    strong = [
        _outcome(index=index, gain=0.5, p_value=0.0, policy=policy)
        for index in range(3)
    ]
    pooled = _outcome(index=None, gain=0.5, p_value=0.0, policy=policy)
    passed = strategy_verdict(
        strategy_name="S2b",
        windows=strong,
        pooled=pooled,
        ineligibility_delta=0.0,
        seed_sign_consistent=True,
        policy=policy,
    )
    assert isinstance(passed, StrategyPassed)

    worse_ineligibility = strategy_verdict(
        strategy_name="S2b",
        windows=strong,
        pooled=pooled,
        ineligibility_delta=(
            policy.verdict.ineligibility_noninferiority_margin * 2.0
        ),
        seed_sign_consistent=True,
        policy=policy,
    )
    assert isinstance(worse_ineligibility, StrategyFailed)


def test_a_tie_of_windows_is_not_a_majority() -> None:
    policy = _policy()
    windows = [
        _outcome(index=0, gain=0.5, p_value=0.0, policy=policy),
        _outcome(index=1, gain=0.5, p_value=0.0, policy=policy),
        _outcome(index=2, gain=0.0, p_value=0.9, policy=policy),
        _outcome(index=3, gain=0.0, p_value=0.9, policy=policy),
    ]
    outcome = strategy_verdict(
        strategy_name="S2b",
        windows=windows,
        pooled=_outcome(index=None, gain=0.5, p_value=0.0, policy=policy),
        ineligibility_delta=0.0,
        seed_sign_consistent=True,
        policy=policy,
    )
    assert isinstance(outcome, StrategyFailed)


def test_one_sided_p_value_feeds_the_judgement_consistently() -> None:
    """검정과 판정이 같은 값을 본다 — 판정이 다른 p 를 쓰면 이 대조가 깨진다."""
    policy = _policy()
    counts = DiscordantCounts(strategy_only=30, baseline_only=5)
    p_value = one_sided_p_value(counts)
    assert passes_window(policy.verdict.min_relative_improvement, p_value, policy)
