"""Reuse: bid-vector/app/ai/predictors/award_rate_gbm.py@ed4b06c

`ml_engine.inference.predict` — LightGBM predict adapter + 가용성 게이트(scope.md ②).
`BoosterLike` Protocol 뒤에 실제 `lgb.Booster`를 둔다((2b) 표 고정 항목 — 커널이 세야 하는
유일한 주입 자리, 판정 함수 자체는 주입하지 않는다). `segment_availability`는 가용성
게이트와 예측 경로 **둘 다**가 호출하는 단일 판정 함수다(ML-02 ②, legacy
`unlearned_category_reason` 「판정 단일 지점」 계승) — 임계 하한은 `max(1, ·)`로 클램프해
정책이 0 을 줘도 가드가 꺼지지 않는다(D-5D-3).
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from decimal import Decimal
from math import isfinite
from typing import Protocol

from ml_engine.features import AwardRateFeatureSpace, FeatureFacts, RowRejected
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.results import (
    Diagnostics,
    IntervalSource,
    PriceFitness,
    SegmentSupport,
    Success,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)
from ml_engine.inference.scenario import build_scenario_candidates, resolve_uncertainty
from ml_engine.registry.artifact import LoadedArtifact


class BoosterLike(Protocol):
    """실제 LightGBM `Booster`가 만족해야 하는 최소 계약 — test 는 fake 를 주입한다."""

    def predict(self, rows: Sequence[Sequence[float]]) -> Sequence[float]: ...


@dataclass(frozen=True)
class Available:
    """가용성 게이트 통과 — 값을 나르지 않는 마커."""


def segment_availability(
    rows: int, policy: InferencePolicy
) -> Available | Unmeasurable:
    """미학습 공종 가드 — `rows == 0` → `UNTRAINED_SEGMENT`(`NEVER_TRAINED`),
    `0 < rows < max(1, policy.gbm_min_category_rows)` → `INSUFFICIENT_SAMPLES`
    (`SHALLOW_SEGMENT`). 임계 1 클램프는 코드 불변식(D-5D-3) — 정책이 0 을 줘도 가드는
    켜져 있다."""
    if rows <= 0:
        return Unmeasurable(
            UnmeasurableReason.UNTRAINED_SEGMENT, UnmeasurableDetail.NEVER_TRAINED
        )
    minimum = max(1, policy.gbm_min_category_rows)
    if rows < minimum:
        return Unmeasurable(
            UnmeasurableReason.INSUFFICIENT_SAMPLES, UnmeasurableDetail.SHALLOW_SEGMENT
        )
    return Available()


def _predict_center(
    *,
    facts: FeatureFacts,
    feature_space: AwardRateFeatureSpace,
    booster: BoosterLike,
) -> float | Unmeasurable:
    """5B `build_row` → booster 호출 → 유한성 확인. 행 조립·예측 실패는 전부
    `Unmeasurable`(결과 타입, `BLE001`이 예외 폴백을 거부한다)."""
    row = feature_space.build_row(facts)
    if isinstance(row, RowRejected):
        return Unmeasurable(
            UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.ROW_REJECTED
        )
    predictions = list(booster.predict([row.values]))
    if len(predictions) != 1:
        return Unmeasurable(
            UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.ROW_REJECTED
        )
    center = float(predictions[0])
    if not isfinite(center):
        return Unmeasurable(
            UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.NON_FINITE_INPUT
        )
    return center


def predict_bid_rates(
    *,
    facts: FeatureFacts,
    feature_space: AwardRateFeatureSpace,
    artifact: LoadedArtifact,
    policy: InferencePolicy,
    booster: BoosterLike,
    segment_rows: int,
    sample_size: int,
) -> Success | Unmeasurable:
    """가용성 게이트(`segment_availability`) → 5B `build_row` → booster 호출 → 시나리오·
    불확실성·진단 조립. `segment_rows`는 미학습 공종 가드가 보는 공종 학습 행 수이고,
    `sample_size`는 이 특정 추정을 뒷받침하는 표본 수(§6.5 `Uncertainty.sample_size` —
    `segment_rows`와 다른 축, agency 표본처럼 공종 가드를 통과한 뒤에도 얕을 수 있다)."""
    availability = segment_availability(segment_rows, policy)
    if isinstance(availability, Unmeasurable):
        return availability

    center = _predict_center(facts=facts, feature_space=feature_space, booster=booster)
    if isinstance(center, Unmeasurable):
        return center

    residual_std = artifact.manifest.residual_std
    candidates = build_scenario_candidates(
        center=center, std=residual_std, policy=policy
    )
    if isinstance(candidates, Unmeasurable):
        return candidates

    uncertainty = resolve_uncertainty(
        sample_size=sample_size,
        dispersion=residual_std,
        estimate_margin=residual_std * float(policy.scenario_z),
        interval_source=IntervalSource.CROSS_VALIDATION_RESIDUAL,
        policy=policy,
    )
    if isinstance(uncertainty, Unmeasurable):
        return uncertainty

    return Success(
        candidates=candidates,
        fitness=PriceFitness(Decimal(str(center))),
        uncertainty=uncertainty,
        diagnostics=_direct_diagnostics(artifact),
    )


def _direct_diagnostics(artifact: LoadedArtifact) -> Diagnostics:
    """5D 는 direct(공종 자체 표본)만 계산한다 — parent/global 폴백 판정은 5C 인수
    (알려진 제한, checklist.md)."""
    return Diagnostics(
        training_row_count=artifact.manifest.training_row_count,
        segment_support=SegmentSupport.DIRECT,
        shrinkage_weight=Decimal("0"),
        excluded_observations=0,
    )
