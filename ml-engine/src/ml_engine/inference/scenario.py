"""Reuse: bid-vector/app/ai/predictors/scenario_spec.py@ed4b06c

`ml_engine.inference.scenario` — 시나리오 후보 3 산출(scope.md ⑥) + `Uncertainty` 조립
(scope.md ④, §6.5). legacy 산식 `clamp(scale·(center + sign·z·std))`을 **괄호 위치까지
그대로** 보존한다(분배법칙으로 펴면 마지막 비트가 달라져 반올림된 값이 바뀔 수 있다,
legacy docstring 그대로). `Decimal` 변환은 경계에서 한 번뿐이다(D-5D-1) — `bid_rate`만
정책 `scenario.bid_rate_digits`로 quantize 하고, `weight`는 `Decimal(str(x))` 그대로
(quantize 없음, scale 보존).

§6.5 분기: 합성 `confidence`는 이식하지 않고, `sample_size < policy.
assessment_min_samples_for_variance`이면 `Unmeasurable(INSUFFICIENT_SAMPLES,
DEGENERATE_VARIANCE)`다(margin 을 지어내지 않는다) — 이 판단은 `resolve_uncertainty`가
한다.
"""

from __future__ import annotations

from decimal import Decimal
from math import isfinite

from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.results import (
    Candidate,
    CandidateLabel,
    IntervalSource,
    Uncertainty,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)
from ml_engine.inference.rounding import quantize_bid_rate

_LABELS: tuple[CandidateLabel, CandidateLabel, CandidateLabel] = (
    CandidateLabel.CONSERVATIVE,
    CandidateLabel.BASE,
    CandidateLabel.AGGRESSIVE,
)


def build_scenario_candidates(
    *,
    center: float,
    std: float,
    policy: InferencePolicy,
    scale: float = 1.0,
) -> tuple[Candidate, Candidate, Candidate] | Unmeasurable:
    """legacy `scenario_bid_rates` — `clamp(scale·(center + sign·z·std))` 셋(라벨 순서
    고정). `std == 0`은 거부하지 않는다(설계 검토 (14) — 후보 3 이 전부 같은 값이 되는 것은
    허용된 결과다, `MIN_PREDICTIVE_STD`/`MIN_RESIDUAL_STD` 바닥이 상류에서 이미 막는다)."""
    if not (isfinite(center) and isfinite(std) and isfinite(scale)):
        return Unmeasurable(
            UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.NON_FINITE_INPUT
        )

    z = float(policy.scenario_z)
    clamp_min = float(policy.scenario_clamp_min)
    clamp_max = float(policy.scenario_clamp_max)
    digits = policy.scenario_bid_rate_digits

    candidates: list[Candidate] = []
    for label, sign, weight in zip(
        _LABELS, policy.scenario_z_signs, policy.scenario_weights, strict=True
    ):
        raw = scale * (center + (sign * z * std))
        clamped = max(clamp_min, min(clamp_max, raw))
        bid_rate = quantize_bid_rate(clamped, digits)
        candidates.append(
            Candidate(
                label=label,
                bid_rate=bid_rate,
                weight=weight,
                weight_policy_version=policy.version,
            )
        )
    conservative, base, aggressive = candidates
    return conservative, base, aggressive


def resolve_uncertainty(
    *,
    sample_size: int,
    dispersion: float,
    estimate_margin: float,
    interval_source: IntervalSource,
    policy: InferencePolicy,
) -> Uncertainty | Unmeasurable:
    """`Uncertainty` 조립 — §6.5 분기: 표본이 정책 최소치 미만이면 margin 을 지어내지
    않고 `Unmeasurable`을 낸다(합성 `confidence` 이식 금지와 같은 갈래)."""
    if sample_size < policy.assessment_min_samples_for_variance:
        return Unmeasurable(
            UnmeasurableReason.INSUFFICIENT_SAMPLES,
            UnmeasurableDetail.DEGENERATE_VARIANCE,
        )
    if not (isfinite(dispersion) and isfinite(estimate_margin)):
        return Unmeasurable(
            UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.NON_FINITE_INPUT
        )
    return Uncertainty(
        sample_size=sample_size,
        dispersion=Decimal(str(dispersion)),
        estimate_margin=Decimal(str(estimate_margin)),
        interval_source=interval_source,
    )
