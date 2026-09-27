"""`ml_engine.evaluation.backtest.verdict` — 판정식과 3값 결과(D-6G-6).

5C-2 `evaluation.verdict` 의 **구조를 그대로 잇는다**: `Passed | Failed |
NotEvaluable(reason)` sealed 타입, 「못 이겼다」와 「못 쟀다」를 가르는 것, 그리고 판정
순서(창 없음 -> seed 불안정 -> underpowered -> 판정식). 사유 어휘도 그 모듈의
`NotEvaluableReason` 을 **재사용**한다 — 같은 뜻의 두 번째 enum 을 만들지 않는다.

**다른 것은 판정식뿐이다.** 5C-2 는 연속 지표의 RMSE 와 대응 t 로 판정했고, 여기는
이진 지표(would-have-won)의 쌍대 비율과 McNemar 정확 검정으로 판정한다.

판정식은 한 자리(`passes_window`)에만 있고 창 판정·합동 판정이 같은 함수를 쓴다
(이중 구현 금지, 5C-2 verifier r1 H-2 와 같은 규율). 유의수준은 **정책 객체가**
고른다(`VerdictThresholds.alpha_for`) — 주 가설은 Bonferroni /3, 보조는 보정 없음(D-6G-32).
여기서 나누지도, 어느 쪽인지 고르지도 않는다.

**UNDERPOWERED 는 창 단위다**(D-6G-31, verifier r1 M-2). 계약 문면이 「**창의** 불일치
쌍 수가 모자람」이고, 창이 검정력 미달인데 `Failed` 로 접으면 D-6G-7 의 「미배선」
갈래로 가 버린다 — 원래는 「수집 연장」 갈래다. 판정 가능한 창이 과반에 못 미치면
전체가 `NotEvaluable` 이다.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from ml_engine.evaluation.backtest.mcnemar import (
    DiscordantCounts,
    alternative_success_probability,
    discordant_counts,
    one_sided_p_value,
    required_discordant_pairs,
)
from ml_engine.evaluation.backtest.metrics import StrategyScores
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.scoring import improvement_ratio
from ml_engine.evaluation.verdict import NotEvaluableReason


def relative_gain(baseline_rate: float, strategy_rate: float) -> float:
    """S0 대비 상대 개선. 5C-2 `scoring.improvement_ratio` 는 **오차** 지표용이라
    「작을수록 좋다」인데 승률은 「클수록 좋다」다 — 부호만 뒤집어 재사용한다(0 나눗셈
    가드도 함께 온다). 두 번째 산식을 만들지 않는다."""
    return -improvement_ratio(baseline_rate, strategy_rate)


@dataclass(frozen=True)
class WindowOutcome:
    """창 하나(또는 합동)에 대한 한 전략의 결과 — 전부 공시한다(창 쇼핑 차단)."""

    window_index: int | None
    row_count: int
    baseline_win_rate: float
    strategy_win_rate: float
    relative_gain: float
    discordant: DiscordantCounts
    p_value: float
    alpha_used: float
    """이 창에 실제로 쓴 유의수준 — 주 가설은 Bonferroni 보정값, 보조는 유의수준. 보조가
    느슨한 것이 판정 JSON 문면에 보이게 싣는다(D-6G-32)."""

    required_discordant_pairs: int | None
    passed: bool

    @property
    def underpowered(self) -> bool:
        """점추정이 개선 방향인데 불일치 쌍이 Δ 를 검출할 만큼 모이지 않은 상태.
        개선률이 음수면(전략이 더 나쁘다) 검정력과 무관하게 판정식으로 간다 — 5C-2
        `verdict` 의 `UNDERPOWERED` 경계와 같은 판단."""
        if self.relative_gain < 0.0:
            return False
        required = self.required_discordant_pairs
        return required is None or self.discordant.total < required


def passes_window(
    outcome_gain: float,
    p_value: float,
    policy: StrategyBacktestPolicy,
    *,
    primary: bool,
) -> bool:
    """판정식 그 자체 — 유일 정의. 창 판정도 합동 판정도 이 함수만 쓴다. 유의수준은
    정책이 고르고(`alpha_for`) 여기서는 그 값을 쓰기만 한다."""
    return (
        outcome_gain >= policy.verdict.min_relative_improvement
        and p_value <= policy.verdict.alpha_for(primary=primary)
    )


def evaluate_window(
    *,
    window_index: int | None,
    baseline: StrategyScores,
    strategy: StrategyScores,
    policy: StrategyBacktestPolicy,
    primary: bool,
) -> WindowOutcome:
    """창 하나의 결과 — 쌍은 공고 순서로 맞춘다(`score_strategy` 가 순서를 지킨다)."""
    counts = discordant_counts(strategy.wins, baseline.wins)
    p_value = one_sided_p_value(counts)
    gain = relative_gain(baseline.win_rate, strategy.win_rate)
    rows = len(baseline.scores)
    alpha = policy.verdict.alpha_for(primary=primary)
    required = required_discordant_pairs(
        success_probability=alternative_success_probability(
            window_rows=rows,
            baseline_win_rate=baseline.win_rate,
            relative_improvement=policy.verdict.min_relative_improvement,
            pairs=counts.total,
        ),
        alpha=alpha,
        target_power=policy.verdict.target_power,
    )
    return WindowOutcome(
        window_index=window_index,
        row_count=rows,
        baseline_win_rate=baseline.win_rate,
        strategy_win_rate=strategy.win_rate,
        relative_gain=gain,
        discordant=counts,
        p_value=p_value,
        alpha_used=alpha,
        required_discordant_pairs=required,
        passed=passes_window(gain, p_value, policy, primary=primary),
    )


@dataclass(frozen=True)
class _Summary:
    strategy_name: str
    primary: bool
    windows: tuple[WindowOutcome, ...]
    pooled: WindowOutcome
    ineligibility_delta: float
    seed_sign_consistent: bool


@dataclass(frozen=True)
class StrategyPassed(_Summary):
    """창 과반 통과 + 합동 통과 + 실격률 비열등 + seed 안정."""


@dataclass(frozen=True)
class StrategyFailed(_Summary):
    """못 이겼다(검정력·안정성은 충족)."""


@dataclass(frozen=True)
class StrategyNotEvaluable:
    """못 쟀다 — `Passed` 가 될 수 없다(타입, 불리언 쌍 아님)."""

    strategy_name: str
    primary: bool
    reason: NotEvaluableReason
    windows: tuple[WindowOutcome, ...]
    pooled: WindowOutcome | None
    required_discordant_pairs: int | None


type StrategyVerdict = StrategyPassed | StrategyFailed | StrategyNotEvaluable


def _majority_passed(windows: Sequence[WindowOutcome]) -> bool:
    """창 과반 — 동수는 과반이 아니다."""
    return sum(1 for window in windows if window.passed) * 2 > len(windows)


def evaluable_window_count(windows: Sequence[WindowOutcome]) -> int:
    """검정력이 서는 창의 수 — 「잴 수 있었던 창」이다(D-6G-31)."""
    return sum(1 for window in windows if not window.underpowered)


def _not_evaluable(
    *,
    strategy_name: str,
    primary: bool,
    windows: tuple[WindowOutcome, ...],
    pooled: WindowOutcome,
    seed_sign_consistent: bool,
    policy: StrategyBacktestPolicy,
) -> StrategyNotEvaluable | None:
    """「못 쟀다」 넷 — 순서: 창 부족 -> seed 불안정 -> 기준선 무승 -> 창 단위
    underpowered. 창이 모자라면 합동 결과 자체를 공시하지 않는다(`pooled=None`) —
    잴 창이 없었다는 사실이 값보다 앞선다."""

    def rejected(
        reason: NotEvaluableReason, *, show_pooled: bool = True
    ) -> StrategyNotEvaluable:
        return StrategyNotEvaluable(
            strategy_name,
            primary,
            reason,
            windows,
            pooled if show_pooled else None,
            pooled.required_discordant_pairs,
        )

    if len(windows) < policy.verdict.min_window_count:
        return rejected(NotEvaluableReason.NO_EVALUABLE_WINDOW, show_pooled=False)
    if not seed_sign_consistent:
        return rejected(NotEvaluableReason.SEED_UNSTABLE)
    if pooled.baseline_win_rate <= 0.0:
        # 기준선이 한 번도 이기지 못했다 — 상대 개선의 분모가 0 이라 개선률이 서지
        # 않는다. `UNDERPOWERED` 로 접으면 다른 이름이 붙는다(code-review r1 L-3).
        return rejected(NotEvaluableReason.NO_BASELINE_WIN)
    if evaluable_window_count(windows) * 2 <= len(windows) or pooled.underpowered:
        return rejected(NotEvaluableReason.UNDERPOWERED)
    return None


def strategy_verdict(
    *,
    strategy_name: str,
    primary: bool,
    windows: Sequence[WindowOutcome],
    pooled: WindowOutcome,
    ineligibility_delta: float,
    seed_sign_consistent: bool,
    policy: StrategyBacktestPolicy,
) -> StrategyVerdict:
    """최종 판정 — 순서: 창 부족 -> seed 불안정 -> underpowered -> 판정식.

    `ineligibility_delta` 는 「전략 실격률 - S0 실격률」이다. 비열등 한계를 넘으면
    승률에서 이겼어도 `Passed` 가 아니다(D-6G-6)."""
    ordered = tuple(windows)
    unmeasured = _not_evaluable(
        strategy_name=strategy_name,
        primary=primary,
        windows=ordered,
        pooled=pooled,
        seed_sign_consistent=seed_sign_consistent,
        policy=policy,
    )
    if unmeasured is not None:
        return unmeasured
    summary = (
        strategy_name,
        primary,
        ordered,
        pooled,
        ineligibility_delta,
        seed_sign_consistent,
    )
    if (
        _majority_passed(ordered)
        and pooled.passed
        and ineligibility_delta <= policy.verdict.ineligibility_noninferiority_margin
    ):
        return StrategyPassed(*summary)
    return StrategyFailed(*summary)
