"""Reuse: bid-vector/app/domain/assessment_shrinkage.py@ed4b06c

`ml_engine.inference.assessment` — K5 이식(계층 수축 사후분포, scope.md ①) +
**provenance 게이트 신설**(D-5D-5). legacy 는 집계된 수치만 받고 필터링은 호출부
책임이었다(provenance 게이트 없음) — ML-04 「clean 이외 provenance 의 행이 분포 추정
입력에 들어갈 수 없다(값이 정수라서 통과하는 경로가 없다)」를 만족하려면 그 게이트가
타입으로 서야 한다. `admit_clean`이 유일한 `CleanAssessmentSample` 생성 함수이고,
`aggregate_level_observation`은 그 타입만 받는다 — 비-`CLEAN` 표본이 값이 정수(개수)라서
집계를 통과하는 경로가 없다.

`level_weights: dict[str, float]`(legacy)는 `LevelWeights`(3필드 dataclass)로 승격한다
(설계 검토 (1) 「dict[str, Any] 재유입」 방어 — 래칫 0). κ 는 `InferencePolicy.assessment_*`
에서 받는다(5D 소유 사정률 축 — 5B `SHIPPED_ENCODING_POLICY`는 낙찰률 축이라 재사용하지
않는다, D-5D-8). `shrink_toward`는 5B `features/shrinkage.py`를 재사용한다(D-5B-6, 중복
금지) — `pseudo_count_weight`는 이 모듈에서 직접 쓰지 않는다(`shrink_toward`가 내부에서
호출).
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from enum import StrEnum
from math import sqrt
from statistics import fmean, pvariance

from ml_engine.features import shrink_toward
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.results import (
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)

_LEVEL_AGENCY = "agency"
_LEVEL_CATEGORY = "category"
_LEVEL_GLOBAL = "global"


class AssessmentProvenance(StrEnum):
    """사정률 관측의 판정 라벨 — wire `BaseAmountProvenanceLabel`과 같은 어휘(5값,
    UNSPECIFIED 제외). `CLEAN`만 집계에 들어간다(D-5D-5)."""

    CLEAN = "CLEAN"
    DERIVED_YEGA = "DERIVED_YEGA"
    DERIVED_VAT = "DERIVED_VAT"
    SUSPECT_RATIO = "SUSPECT_RATIO"
    UNKNOWN = "UNKNOWN"


@dataclass(frozen=True)
class AssessmentSample:
    """사정률 관측 원본 — provenance 검증 **전**. 집계 함수는 이 타입을 받지 않는다."""

    center: float
    provenance: AssessmentProvenance


@dataclass(frozen=True)
class CleanAssessmentSample:
    """provenance == CLEAN 인 표본만 — `admit_clean`이 유일 생성 지점(D-5D-5, 우회 후보
    (10) — Python 가시성 한계로 직접 생성 자체는 막지 못한다, (2b) 등재)."""

    center: float


def admit_clean(
    samples: Iterable[AssessmentSample],
) -> tuple[tuple[CleanAssessmentSample, ...], int]:
    """`(clean 표본들, 제외 건수)` — 제외된 표본의 값은 버려지고 개수만 남는다(조용한
    drop 금지, `Diagnostics.excluded_observations`가 이 개수를 나른다)."""
    clean: list[CleanAssessmentSample] = []
    excluded = 0
    for sample in samples:
        if sample.provenance is AssessmentProvenance.CLEAN:
            clean.append(CleanAssessmentSample(sample.center))
        else:
            excluded += 1
    return tuple(clean), excluded


@dataclass(frozen=True)
class LevelObservation:
    """한 계층에서 관측된 공고별 사정률 중심의 집계 통계(legacy 그대로)."""

    sample_count: int
    mean: float
    variance: float


def aggregate_level_observation(
    samples: Sequence[CleanAssessmentSample],
) -> LevelObservation | None:
    """`CleanAssessmentSample`만 받는다(D-5D-5) — provenance 필터를 통과하지 못한
    표본은 이 함수에 닿을 수 없다."""
    if not samples:
        return None
    centers = [sample.center for sample in samples]
    return LevelObservation(
        sample_count=len(centers),
        mean=fmean(centers),
        variance=pvariance(centers) if len(centers) >= 2 else 0.0,
    )


@dataclass(frozen=True)
class LevelWeights:
    """legacy `dict[str, float]` → 3필드 승격(설계 검토 (1) — 래칫 0, `dict[str,Any]`
    재유입 방지)."""

    agency: float
    category: float
    global_: float


@dataclass(frozen=True)
class AssessmentPosterior:
    """수축된 사정률 사전분포(legacy 그대로, `level_weights`만 타입 승격)."""

    mean: float
    std: float
    effective_sample_count: float
    level_weights: LevelWeights


def resolve_assessment_posterior(
    *,
    agency: LevelObservation | None,
    category: LevelObservation | None,
    global_level: LevelObservation | None,
    policy: InferencePolicy,
) -> AssessmentPosterior | Unmeasurable:
    """legacy 3계층 순차 수축(전역→공종→기관) 그대로 — `global_level.sample_count < 1`은
    legacy `ValueError`를 결과 타입으로 치환(위협 모델 (a))."""
    if global_level is None or global_level.sample_count < 1:
        return Unmeasurable(
            UnmeasurableReason.INSUFFICIENT_SAMPLES, UnmeasurableDetail.TOO_FEW_DRAWS
        )

    category_observed = (
        category if category is not None and category.sample_count > 0 else None
    )
    agency_observed = agency if agency is not None and agency.sample_count > 0 else None

    category_mean, category_self_weight = _shrink_optional_level(
        category_observed,
        prior_mean=global_level.mean,
        prior_strength=float(policy.assessment_category_prior_strength),
    )
    posterior_mean, agency_self_weight = _shrink_optional_level(
        agency_observed,
        prior_mean=category_mean,
        prior_strength=float(policy.assessment_agency_prior_strength),
    )
    level_weights = _compose_level_weights(agency_self_weight, category_self_weight)
    min_predictive_std = float(policy.assessment_min_predictive_std)
    predictive_variance = _blend_predictive_variance(
        level_weights=level_weights,
        agency=agency_observed,
        category=category_observed,
        global_level=global_level,
        min_predictive_std=min_predictive_std,
        min_samples_for_variance=policy.assessment_min_samples_for_variance,
    )

    return AssessmentPosterior(
        mean=posterior_mean,
        std=max(sqrt(predictive_variance), min_predictive_std),
        effective_sample_count=_effective_sample_count(
            level_weights, agency_observed, category_observed, global_level
        ),
        level_weights=level_weights,
    )


def _effective_sample_count(
    level_weights: LevelWeights,
    agency: LevelObservation | None,
    category: LevelObservation | None,
    global_level: LevelObservation,
) -> float:
    return (
        (level_weights.agency * _sample_count(agency))
        + (level_weights.category * _sample_count(category))
        + (level_weights.global_ * global_level.sample_count)
    )


def _compose_level_weights(
    agency_self_weight: float, category_self_weight: float
) -> LevelWeights:
    return LevelWeights(
        agency=agency_self_weight,
        category=(1.0 - agency_self_weight) * category_self_weight,
        global_=(1.0 - agency_self_weight) * (1.0 - category_self_weight),
    )


def _blend_predictive_variance(
    *,
    level_weights: LevelWeights,
    agency: LevelObservation | None,
    category: LevelObservation | None,
    global_level: LevelObservation,
    min_predictive_std: float,
    min_samples_for_variance: int,
) -> float:
    global_variance = _trusted_variance(
        global_level,
        fallback=min_predictive_std**2,
        min_samples=min_samples_for_variance,
    )
    category_variance = _trusted_variance(
        category, fallback=global_variance, min_samples=min_samples_for_variance
    )
    agency_variance = _trusted_variance(
        agency, fallback=category_variance, min_samples=min_samples_for_variance
    )
    return (
        (level_weights.agency * agency_variance)
        + (level_weights.category * category_variance)
        + (level_weights.global_ * global_variance)
    )


def _shrink_optional_level(
    observed: LevelObservation | None,
    *,
    prior_mean: float,
    prior_strength: float,
) -> tuple[float, float]:
    if observed is None:
        return prior_mean, 0.0
    return shrink_toward(
        observed.mean,
        observed.sample_count,
        prior_mean=prior_mean,
        prior_strength=prior_strength,
    )


def _trusted_variance(
    observed: LevelObservation | None, *, fallback: float, min_samples: int
) -> float:
    if observed is None or observed.sample_count < min_samples:
        return fallback
    return max(observed.variance, 0.0)


def _sample_count(observed: LevelObservation | None) -> int:
    return observed.sample_count if observed is not None else 0
