"""RED — M6/6G D-6G-6·31·32 판정식 잠금.

verifier r1 H-4 가 변이 다섯(V3·V5·V7·V8·V9)에 **초록**을 냈다 — 판정식의 구성요소와
누출 울타리가 잠겨 있지 않았다. 이 파일은 그 다섯을 각각 RED 로 만드는 자리다.

| 변이 | 잠그는 test |
|---|---|
| V3 McNemar `P(X >= k+1)` | `one_sided_p_value` 를 **손 계산 값**과 대조 |
| V5 seed 안정성 `return True` | 부호가 갈리는 판 둘로 `_seed_sign_consistent` 가 False |
| V7 창 이력 `< window.end` | embargo 구간 공고가 이력에 들어오지 않는다 |
| V8 `pooled.passed` 제거 | 창 과반은 통과하지만 합동이 실패하는 판은 `Failed` |
| V9 표본 동일성 `return True` | 표본이 다른 판을 `_samples_match` 가 False |

그리고 **UNDERPOWERED 는 창 단위**다(D-6G-31) — 판정 가능한 창이 과반에 못 미치면
전체가 `NotEvaluable` 이고, 그래야 D-6G-7 의 「미배선」이 아니라 「수집 연장」으로 간다.

`_seed_sign_consistent`·`_samples_match` 는 비공개지만 여기서 직접 부른다 — 둘은 전체
실행에서 **참으로만** 나오는 방어 단언이라(오늘 코드에서는 거짓이 될 입력이 없다)
공개 경로로는 변이를 RED 로 만들 수 없다. 잠금의 대상이 그 술어 자체다.
"""

from __future__ import annotations

from datetime import date, timedelta
from fractions import Fraction
from math import comb
from pathlib import Path
from typing import Any

import pytest
from _backtest_support import (
    SHIPPED_BACKTEST_POLICY_PATH,
    manifest_bytes,
    row_payload,
    rows_bytes,
    sample_list_bytes,
)

from ml_engine.evaluation.backtest.exclusions import admit_rows
from ml_engine.evaluation.backtest.mcnemar import DiscordantCounts, one_sided_p_value
from ml_engine.evaluation.backtest.metrics import NoticeScore, StrategyScores
from ml_engine.evaluation.backtest.observations import (
    LoadedSnapshot,
)
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.backtest.records import BacktestRequest, SampleVariant
from ml_engine.evaluation.backtest.run import _samples_match, _seed_sign_consistent
from ml_engine.evaluation.backtest.snapshot import (
    load_snapshot,
)
from ml_engine.evaluation.backtest.strategies import UniformBandStrategy
from ml_engine.evaluation.backtest.verdict import (
    StrategyFailed,
    StrategyNotEvaluable,
    StrategyPassed,
    StrategyVerdict,
    WindowOutcome,
    evaluable_window_count,
    passes_window,
    relative_gain,
    strategy_verdict,
)
from ml_engine.evaluation.backtest.windows import plan_backtest_windows
from ml_engine.evaluation.verdict import NotEvaluableReason


def _policy() -> StrategyBacktestPolicy:
    loaded = load_strategy_backtest_policy(SHIPPED_BACKTEST_POLICY_PATH)
    assert isinstance(loaded, StrategyBacktestPolicy)
    return loaded


def _snapshot(payloads: list[dict[str, Any]]) -> LoadedSnapshot:
    rows = rows_bytes(payloads)
    loaded = load_snapshot(manifest_bytes(rows), rows, sample_list_bytes(rows))
    assert isinstance(loaded, LoadedSnapshot), loaded
    return loaded


def _outcome(
    *,
    index: int | None,
    gain: float,
    p_value: float,
    pairs: int = 100,
    required: int | None = 10,
    baseline_rate: float = 0.10,
    primary: bool = True,
) -> WindowOutcome:
    rules = _policy()
    return WindowOutcome(
        window_index=index,
        row_count=500,
        baseline_win_rate=baseline_rate,
        strategy_win_rate=baseline_rate * (1.0 + gain),
        relative_gain=gain,
        discordant=DiscordantCounts(strategy_only=pairs, baseline_only=0),
        p_value=p_value,
        alpha_used=rules.verdict.alpha_for(primary=primary),
        required_discordant_pairs=required,
        passed=passes_window(gain, p_value, rules, primary=primary),
    )


def _verdict(
    windows: list[WindowOutcome],
    pooled: WindowOutcome,
    *,
    ineligibility_delta: float = 0.0,
    seed_stable: bool = True,
) -> StrategyVerdict:
    return strategy_verdict(
        strategy_name="S2b",
        primary=True,
        windows=windows,
        pooled=pooled,
        ineligibility_delta=ineligibility_delta,
        seed_sign_consistent=seed_stable,
        policy=_policy(),
    )


def _scores(name: str, wins: list[bool]) -> StrategyScores:
    return StrategyScores(
        name=name,
        scores=tuple(
            NoticeScore(f"k{index}", True, 0.0, win, None)
            for index, win in enumerate(wins)
        ),
    )


# ── V3: McNemar 정확값 ────────────────────────────────────────────────────────


def _exact_one_sided(strategy_only: int, baseline_only: int) -> float:
    """유리수 산술로 낸 정확한 단측 p — 구현과 **다른 경로**다(`comb` + `Fraction`,
    로그 공간 합이 아니다). 구현이 `P(X >= k)` 를 `P(X >= k+1)` 로 밀면 갈린다."""
    n = strategy_only + baseline_only
    tail = sum(comb(n, k) for k in range(strategy_only, n + 1))
    return float(Fraction(tail, 2**n))


@pytest.mark.parametrize(
    ("strategy_only", "baseline_only"),
    [(3, 1), (8, 2), (9, 1), (5, 5), (12, 6), (1, 9), (4, 0), (7, 3)],
)
def test_one_sided_p_value_matches_hand_computed_exact_values(
    strategy_only: int, baseline_only: int
) -> None:
    counts = DiscordantCounts(strategy_only, baseline_only)
    assert one_sided_p_value(counts) == pytest.approx(
        _exact_one_sided(strategy_only, baseline_only), rel=1e-12
    )


def test_one_sided_p_value_is_the_inclusive_upper_tail() -> None:
    """경계를 축어로 — `P(X >= k)` 이지 `P(X > k)` 가 아니다. 10쌍 중 8 이면
    `(45 + 10 + 1) / 1024`."""
    assert one_sided_p_value(DiscordantCounts(8, 2)) == pytest.approx(56 / 1024)
    assert one_sided_p_value(DiscordantCounts(0, 0)) == pytest.approx(1.0)


# ── V5: seed 안정성 ───────────────────────────────────────────────────────────


def test_seed_instability_is_detected_when_the_sign_flips() -> None:
    """seed 마다 부호가 갈리면 「이 창이 방향조차 재지 못했다」는 뜻이다. 계산이
    `return True` 로 바뀌면 이 test 가 붉어진다(변이 V5)."""
    request = BacktestRequest(
        snapshot=_snapshot([row_payload("n-1")]),
        baseline=UniformBandStrategy(),
        candidates=(),
        primary_names=("S2b",),
        policy=_policy(),
        policy_checksum="",
        variant=SampleVariant.MAIN,
    )
    plan = plan_backtest_windows((), _policy())
    # 기준선 승률이 두 판 모두 0 이 아니어야 부호가 정의된다(0 이면 `NO_BASELINE_WIN`
    # 축이지 안정성 축이 아니다).
    up, down = [True, True, True, False], [True, False, False, False]
    winning = {"S0": (_scores("S0", down),), "S2b": (_scores("S2b", up),)}
    losing = {"S0": (_scores("S0", up),), "S2b": (_scores("S2b", down),)}
    assert not _seed_sign_consistent(request, plan, "S2b", [winning, losing])
    assert _seed_sign_consistent(request, plan, "S2b", [winning, winning])


# ── V7: embargo ───────────────────────────────────────────────────────────────


def _relaxed_window_policy(tmp_path: Path) -> StrategyBacktestPolicy:
    """창 규칙만 재는 단위 test 라 **창당 표본 하한만** 낮춘 파생 정책을 쓴다 —
    embargo·창 폭은 출하값 그대로다(D-6G-25 와 같은 갈래, 판정식 축 무변경)."""
    lines = [
        "verdict.min_window_rows: 2"
        if line.startswith("verdict.min_window_rows:")
        else line
        for line in SHIPPED_BACKTEST_POLICY_PATH.read_text(
            encoding="utf-8"
        ).splitlines()
    ]
    path = tmp_path / "windows.yaml"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    loaded = load_strategy_backtest_policy(path)
    assert isinstance(loaded, StrategyBacktestPolicy)
    return loaded


def test_window_history_stops_one_embargo_before_the_window_starts(
    tmp_path: Path,
) -> None:
    """이력 절단이 `< start - embargo` 에서 `< window.end` 로 밀리면(변이 V7) embargo
    가 사라지고 **창 안** 공고까지 이력이 된다 — 그 공고의 개찰 결과는 같은 창 다른
    공고의 투찰 시점에 아직 없다."""
    policy = _relaxed_window_policy(tmp_path)
    # 창은 **가장 이른 개찰일**에서 시작하는 달력 블록이다 — 세 날을 그 블록 구조에
    # 맞춰 놓는다. 이력(d0) · embargo 구간(d0+10) · 채점 창(d0+16).
    anchor = date(2026, 6, 22)
    gap_day = anchor + timedelta(days=10)
    scored_day = anchor + timedelta(days=16)
    payloads = [
        *(
            row_payload(f"old-{index}", outcome_opened_on=anchor.isoformat())
            for index in range(3)
        ),
        *(
            row_payload(f"gap-{index}", outcome_opened_on=gap_day.isoformat())
            for index in range(3)
        ),
        *(
            row_payload(f"win-{index}", outcome_opened_on=scored_day.isoformat())
            for index in range(3)
        ),
    ]
    admitted = admit_rows(_snapshot(payloads).rows, policy).admitted
    assert len(admitted) == len(payloads)
    plan = plan_backtest_windows(admitted, policy)
    target = [item for item in plan.selected if item.window.contains(scored_day)]
    assert target, [(item.window.start, len(item.notices)) for item in plan.selected]
    embargo = timedelta(days=policy.windows.embargo_days)
    history_days = {item.row.outcome.opened_on for item in target[0].history}
    assert history_days == {anchor}
    assert gap_day not in history_days
    assert scored_day not in history_days
    assert all(day < target[0].window.start - embargo for day in history_days)


# ── V8: 합동 통과 조건 ────────────────────────────────────────────────────────


def test_windows_majority_alone_does_not_pass_without_the_pooled_test() -> None:
    """창 과반이 통과해도 **합동**이 실패하면 Passed 가 아니다. `pooled.passed` 를
    빼면(변이 V8) 이 판이 Passed 가 된다."""
    windows = [
        _outcome(index=0, gain=0.5, p_value=0.0),
        _outcome(index=1, gain=0.5, p_value=0.0),
        _outcome(index=2, gain=0.0, p_value=0.4),
    ]
    pooled_fails = _outcome(index=None, gain=0.5, p_value=0.4)
    assert not pooled_fails.passed
    assert isinstance(_verdict(windows, pooled_fails), StrategyFailed)
    assert isinstance(
        _verdict(windows, _outcome(index=None, gain=0.5, p_value=0.0)), StrategyPassed
    )


# ── V9: 전략 간 표본 동일성 ───────────────────────────────────────────────────


def test_strategy_sample_mismatch_is_detected() -> None:
    """전략마다 채점한 공고가 다르면 쌍대 검정이 깨진다. 단언이 `return True` 로
    바뀌면(변이 V9) 이 test 가 붉어진다."""
    same = {
        "S0": (_scores("S0", [True, False]),),
        "S2b": (_scores("S2b", [False, True]),),
    }
    assert _samples_match(same)
    different = {
        "S0": (_scores("S0", [True, False]),),
        "S2b": (
            StrategyScores(
                name="S2b", scores=(NoticeScore("other", True, 0.0, True, None),)
            ),
        ),
    }
    assert not _samples_match(different)


# ── UNDERPOWERED 는 창 단위 ───────────────────────────────────────────────────


def test_underpowered_is_decided_per_window_not_only_pooled() -> None:
    """판정 가능한 창이 과반에 못 미치면 전체가 NotEvaluable 이다(D-6G-31) — 합동만
    보면 검정력 미달이 `Failed` 가 되어 「미배선」 귀결로 샌다."""
    weak = _outcome(index=0, gain=0.5, p_value=0.9, pairs=3, required=500)
    assert weak.underpowered
    windows = [
        weak,
        _outcome(index=1, gain=0.5, p_value=0.9, pairs=3, required=500),
        _outcome(index=2, gain=0.5, p_value=0.0),
    ]
    assert evaluable_window_count(windows) == 1
    outcome = _verdict(windows, _outcome(index=None, gain=0.5, p_value=0.0))
    assert isinstance(outcome, StrategyNotEvaluable)
    assert outcome.reason is NotEvaluableReason.UNDERPOWERED


def test_exactly_half_the_windows_evaluable_is_not_evaluable() -> None:
    """**경계**(code-review r2 N6) — 「판정 가능한 창이 **과반 미만**이면 전체
    NotEvaluable」에서 정확히 절반은 과반이 **아니다**. 창 넷 중 둘만 잴 수 있으면
    못 잰 것이다. 술어가 `<=` 에서 `<` 로 밀리면 이 test 가 붉어진다."""
    weak = [
        _outcome(index=index, gain=0.5, p_value=0.9, pairs=3, required=500)
        for index in range(2)
    ]
    strong = [_outcome(index=index + 2, gain=0.5, p_value=0.0) for index in range(2)]
    windows = [*weak, *strong]
    assert evaluable_window_count(windows) * 2 == len(windows)
    outcome = _verdict(windows, _outcome(index=None, gain=0.5, p_value=0.0))
    assert isinstance(outcome, StrategyNotEvaluable)
    assert outcome.reason is NotEvaluableReason.UNDERPOWERED


def test_one_more_than_half_the_windows_evaluable_reaches_the_judgement() -> None:
    """경계의 반대편 — 다섯 중 셋이면 과반이라 판정으로 간다."""
    windows = [
        *(
            _outcome(index=index, gain=0.5, p_value=0.9, pairs=3, required=500)
            for index in range(2)
        ),
        *(_outcome(index=index + 2, gain=0.5, p_value=0.0) for index in range(3)),
    ]
    assert evaluable_window_count(windows) * 2 > len(windows)
    assert isinstance(
        _verdict(windows, _outcome(index=None, gain=0.5, p_value=0.0)), StrategyPassed
    )


def test_enough_evaluable_windows_reach_the_judgement() -> None:
    windows = [
        _outcome(index=0, gain=0.5, p_value=0.0),
        _outcome(index=1, gain=0.5, p_value=0.0),
        _outcome(index=2, gain=0.5, p_value=0.9, pairs=3, required=500),
    ]
    assert evaluable_window_count(windows) == 2
    assert isinstance(
        _verdict(windows, _outcome(index=None, gain=0.5, p_value=0.0)), StrategyPassed
    )


def test_underpowered_only_when_the_point_estimate_is_improving() -> None:
    improving = _outcome(index=None, gain=0.5, p_value=0.9, pairs=5, required=500)
    worse = _outcome(index=None, gain=-0.5, p_value=0.9, pairs=5, required=500)
    assert improving.underpowered
    assert not worse.underpowered


def test_baseline_that_never_won_gets_its_own_reason() -> None:
    """기준선 승률 0 은 「검정력이 모자랐다」가 아니라 「잴 기준선이 없었다」다
    (code-review r1 L-3)."""
    windows = [
        _outcome(index=index, gain=0.0, p_value=0.0, baseline_rate=0.0)
        for index in range(3)
    ]
    outcome = _verdict(
        windows, _outcome(index=None, gain=0.0, p_value=0.0, baseline_rate=0.0)
    )
    assert isinstance(outcome, StrategyNotEvaluable)
    assert outcome.reason is NotEvaluableReason.NO_BASELINE_WIN


# ── Bonferroni 는 주 가설만 ───────────────────────────────────────────────────


def test_bonferroni_applies_to_primary_hypotheses_only() -> None:
    """D-6G-32 — 주 가설(S2 셋)만 /3 이고 보조(S1·S4)는 보정 없는 유의수준이다.
    보조가 **느슨한** 쪽이라 그 완화가 `alpha_used` 로 문면에 보여야 한다."""
    policy = _policy()
    between = (policy.verdict.primary_alpha + policy.verdict.alpha) / 2.0
    gain = policy.verdict.min_relative_improvement
    assert not passes_window(gain, between, policy, primary=True)
    assert passes_window(gain, between, policy, primary=False)
    assert policy.verdict.alpha_for(primary=True) == policy.verdict.primary_alpha
    assert policy.verdict.alpha_for(primary=False) == policy.verdict.alpha


def test_alpha_used_is_carried_on_every_window_outcome() -> None:
    policy = _policy()
    assert _outcome(index=0, gain=0.5, p_value=0.0, primary=True).alpha_used == (
        policy.verdict.primary_alpha
    )
    assert _outcome(index=0, gain=0.5, p_value=0.0, primary=False).alpha_used == (
        policy.verdict.alpha
    )


# ── 그 밖의 판정 순서·경계 ────────────────────────────────────────────────────


def test_relative_gain_is_positive_when_the_strategy_wins_more() -> None:
    assert relative_gain(0.10, 0.12) == pytest.approx(0.20)
    assert relative_gain(0.10, 0.08) == pytest.approx(-0.20)


def test_verdict_requires_the_effect_size_not_just_significance() -> None:
    policy = _policy()
    tiny = policy.verdict.min_relative_improvement / 2.0
    assert not passes_window(tiny, 0.0, policy, primary=True)


def test_too_few_windows_is_not_evaluable_and_hides_the_pooled_result() -> None:
    outcome = _verdict(
        [_outcome(index=0, gain=0.5, p_value=0.0)],
        _outcome(index=None, gain=0.5, p_value=0.0),
    )
    assert isinstance(outcome, StrategyNotEvaluable)
    assert outcome.reason is NotEvaluableReason.NO_EVALUABLE_WINDOW
    assert outcome.pooled is None


def test_seed_instability_outranks_the_judgement() -> None:
    windows = [_outcome(index=index, gain=0.5, p_value=0.0) for index in range(3)]
    outcome = _verdict(
        windows, _outcome(index=None, gain=0.5, p_value=0.0), seed_stable=False
    )
    assert isinstance(outcome, StrategyNotEvaluable)
    assert outcome.reason is NotEvaluableReason.SEED_UNSTABLE


def test_ineligibility_non_inferiority_blocks_a_pass() -> None:
    policy = _policy()
    windows = [_outcome(index=index, gain=0.5, p_value=0.0) for index in range(3)]
    outcome = _verdict(
        windows,
        _outcome(index=None, gain=0.5, p_value=0.0),
        ineligibility_delta=policy.verdict.ineligibility_noninferiority_margin * 2.0,
    )
    assert isinstance(outcome, StrategyFailed)


def test_a_tie_of_windows_is_not_a_majority() -> None:
    windows = [
        _outcome(index=0, gain=0.5, p_value=0.0),
        _outcome(index=1, gain=0.5, p_value=0.0),
        _outcome(index=2, gain=0.0, p_value=0.4),
        _outcome(index=3, gain=0.0, p_value=0.4),
    ]
    assert isinstance(
        _verdict(windows, _outcome(index=None, gain=0.5, p_value=0.0)), StrategyFailed
    )
