"""`ml_engine.evaluation.verdict` — 판정식과 3값 결과(scope ⑥). 신규 — legacy는 판정을
불리언 셋(`gate_passed`·`gate_evaluable`·`gate_passed_at_latest_window`·
`gate_passed_at_all_origins`)의 조합으로 표현했다(조사 02 §1-5(b)). 여기서는 그 조합을
`GateOutcome = Passed | Failed | NotEvaluable(reason)` sealed 타입으로 승격한다 —
`UNDERPOWERED`는 legacy 가 콘솔 요약에서만 하던 판정을 결과 타입으로 올린 것(D-5C2-4).

판정식은 한 줄이다: ``model_rmse < baseline_rmse and paired_t < -threshold``(legacy
`award_rate_diagnostics.py:367`). 순서는 창 없음(n<2) → seed 불안정 → underpowered →
판정식 — seed 가 불안정하면 개별 시행의 형식적 통과·실패보다 "이 창이 애초에 방향조차
재지 못했다"는 사실이 우선한다.

`trial_outcome`은 seed 안정성 없이 단일 시행을 낸다(안정성 sweep 의 각 seed 가 부르는
자리, legacy `StabilityTrial.passed`와 같은 순수 불리언 판정식을 필요로 한다).
`gate_outcome`은 `trial_outcome` 위에 안정성 요약을 얹어 최종 판정을 낸다.

**`UNDERPOWERED`의 경계** — legacy 콘솔 `_underpowered_windows`는 부호를 보지 않고
`improvement_ratio < mde` 만 본다(진단 전용이라 무해했다). 이 결과 타입은 **판정**이므로
그 조건을 그대로 승격하면 "모델이 확실히 더 나쁘다"(개선률이 크게 음수, `paired_t`가
유의하게 양수)까지 `UNDERPOWERED`로 가려 `Failed`가 나와야 할 자리를 흐린다 — 그래서
`improvement_ratio >= 0`(점추정이 개선 방향)일 때만 MDE 미달을 `UNDERPOWERED`로 본다.
개선률이 음수(모델이 더 나쁨)면 검정력과 무관하게 판정식으로 직행해 `Failed`를 낸다.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

import numpy as np

from ml_engine.evaluation.diagnostics import (
    StabilitySummary,
    minimum_detectable_improvement,
    required_row_count,
)
from ml_engine.evaluation.policy import EvaluationPolicy
from ml_engine.evaluation.scoring import improvement_ratio, paired_t


class NotEvaluableReason(StrEnum):
    NO_EVALUABLE_WINDOW = "NO_EVALUABLE_WINDOW"
    UNDERPOWERED = "UNDERPOWERED"
    SEED_UNSTABLE = "SEED_UNSTABLE"


@dataclass(frozen=True)
class _Scored:
    baseline_rmse: float
    model_rmse: float
    improvement_ratio: float
    paired_t: float
    min_detectable_improvement: float
    required_row_count: int | None


@dataclass(frozen=True)
class Passed(_Scored):
    """판정식을 통과했고 검정력·seed 안정성도 충족했다."""


@dataclass(frozen=True)
class Failed(_Scored):
    """판정식을 통과하지 못했다(검정력·안정성은 충족 — "못 이겼다")."""


@dataclass(frozen=True)
class NotEvaluable:
    """"못 쟀다" — `Passed`가 될 수 없다(타입, bool 쌍 아님)."""

    reason: NotEvaluableReason
    required_row_count: int | None = None


type GateOutcome = Passed | Failed | NotEvaluable


def _passes(baseline_rmse: float, model_rmse: float, statistic: float, policy: EvaluationPolicy) -> bool:
    return model_rmse < baseline_rmse and statistic < -policy.paired_t_threshold


def trial_outcome(
    *,
    baseline_rmse: float,
    model_rmse: float,
    model_predictions: np.ndarray,
    baseline_predictions: np.ndarray,
    targets: np.ndarray,
    policy: EvaluationPolicy,
) -> GateOutcome:
    """seed 안정성을 보지 않는 단일 시행 판정 — 순서: 창 없음(n<2) → underpowered →
    판정식. 안정성 sweep 의 각 trial(`StabilityTrial.passed`)이 이 함수의 판정식
    부분(`_passes`)만 쓰고, 헤드라인 최종 판정은 `gate_outcome`이 낸다."""
    n = int(targets.size)
    if n < 2:
        return NotEvaluable(NotEvaluableReason.NO_EVALUABLE_WINDOW)
    statistic = paired_t(model_predictions, baseline_predictions, targets)
    improvement = improvement_ratio(baseline_rmse, model_rmse)
    mde = minimum_detectable_improvement(
        model_predictions, baseline_predictions, targets, threshold=policy.paired_t_threshold
    )
    required = required_row_count(statistic, n, threshold=policy.paired_t_threshold)
    if improvement >= 0.0 and improvement < mde:
        return NotEvaluable(NotEvaluableReason.UNDERPOWERED, required)
    scored = dict(
        baseline_rmse=baseline_rmse,
        model_rmse=model_rmse,
        improvement_ratio=improvement,
        paired_t=statistic,
        min_detectable_improvement=mde,
        required_row_count=required,
    )
    return Passed(**scored) if _passes(baseline_rmse, model_rmse, statistic, policy) else Failed(**scored)


def gate_outcome(
    *,
    baseline_rmse: float,
    model_rmse: float,
    model_predictions: np.ndarray,
    baseline_predictions: np.ndarray,
    targets: np.ndarray,
    policy: EvaluationPolicy,
    stability: StabilitySummary,
) -> GateOutcome:
    """최종 판정 — 순서: 창 없음(n<2) → seed 불안정 → underpowered → 판정식(D-5C2-4).
    임계·시드·창 수를 낱개 인자로 받지 않는다 — 전부 `policy`에서만 온다."""
    n = int(targets.size)
    if n < 2:
        return NotEvaluable(NotEvaluableReason.NO_EVALUABLE_WINDOW)
    statistic = paired_t(model_predictions, baseline_predictions, targets)
    improvement = improvement_ratio(baseline_rmse, model_rmse)
    mde = minimum_detectable_improvement(
        model_predictions, baseline_predictions, targets, threshold=policy.paired_t_threshold
    )
    required = required_row_count(statistic, n, threshold=policy.paired_t_threshold)
    if not (stability.sign_consistent and stability.verdict_consistent):
        return NotEvaluable(NotEvaluableReason.SEED_UNSTABLE, required)
    if improvement >= 0.0 and improvement < mde:
        return NotEvaluable(NotEvaluableReason.UNDERPOWERED, required)
    scored = dict(
        baseline_rmse=baseline_rmse,
        model_rmse=model_rmse,
        improvement_ratio=improvement,
        paired_t=statistic,
        min_detectable_improvement=mde,
        required_row_count=required,
    )
    return Passed(**scored) if _passes(baseline_rmse, model_rmse, statistic, policy) else Failed(**scored)
