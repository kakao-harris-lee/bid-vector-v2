"""Reuse: bid-vector/app/ai/predictors/distribution.py@ed4b06c

`ml_engine.inference.distribution` — 분포 엔진 조립기(scope.md ②). `_estimate_distribution`
흐름 — 조사 §5 흐름 그대로: 관측 정제 → K6 draw 모멘트 → 3계층 K5 수축 → 예측분산 합성 →
투찰율 축 환산 → 시나리오 조립. 산식 자체는 K5(`assessment.py`)·K6(`reserve_draw.py`)에
이미 이식돼 있다 — 이 모듈은 legacy `_estimate_distribution`·`_resolve_posterior`가 하던
**조립**만 새로 짠다(D-5D2-1 (b), 분포 단독 엔진).

**알려진 제한(agency/category 계층 — checklist.md 근거)**: M2 wire `CompetitionSample`은
행 단위 agency/category 를 나르지 않는다(D-2B-3 인용이 `reserve_prices`·`selected_numbers`
·`base_amount_basis` 셋만 근거로 든다 — agency/category 는 없음). legacy `_resolve_posterior`
는 이력 행마다 `agency_name`/`category`를 읽어 3계층(발주기관/공종/전역)을 나눴지만, 이
wire 형태로는 그 분리가 **불가능**하다. 이 조립기는 그래서 **global 레벨만** 채우고
agency/category 레벨은 항상 `None`(→ `segment_support`는 항상 `GLOBAL`) — ML-04 ②(기관
표본 임계 미만 시 수축 가중치 노출)는 golden `ml-kernel-011`이 K5(`resolve_assessment_
posterior`)를 직접 호출해 검증하고, 이 조립기(wire 구동 경로)에서는 구조적으로 도달하지
않는다. `DistributionRequest.agency`/`category`는 향후 계약이 그 축을 열면 쓰일 자리로
남겨둔다(값은 채우되 소비하지 않음).
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from math import sqrt
from statistics import fmean, median, pstdev

from ml_engine.contracts import features_pb2, prediction_pb2
from ml_engine.features import FactRejected, FactValue, FeatureFacts
from ml_engine.inference.assessment import (
    AssessmentPosterior,
    AssessmentProvenance,
    AssessmentSample,
    CleanAssessmentSample,
    LevelObservation,
    admit_clean,
    aggregate_level_observation,
    resolve_assessment_posterior,
)
from ml_engine.inference.availability import distribution_availability
from ml_engine.inference.observations import (
    ReserveDrawSample,
    SampleRejected,
    observe_sample,
)
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.results import (
    Candidate,
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


@dataclass(frozen=True)
class SampleSegment:
    """`OPEN-5D2-SAMPLE-SEGMENT`(scope.md 계약 갱신 이력 2026-09-15) — M2 2F 가
    `CompetitionSample`에 추가할 표본별 기관·공종 축(`agency_id`·`category_code`,
    `optional` 불투명 문자열). 지금은 wire 에 이 필드가 없어 **어떤 표본도** 이 값을
    갖지 않는다(`DistributionRequest.from_proto`가 항상 `SegmentMissing()`을 낸다)."""

    agency: str
    category: str


@dataclass(frozen=True)
class SegmentMissing:
    """`SampleSegment`가 아직 없다는 표지 — `OPEN-5D2-SAMPLE-SEGMENT`가 닫히기 전까지
    유일하게 관측되는 값(값 없는 마커, 팀장 계약 문구의 `Missing`을 5B `features.
    Missing(reason: int)`과 이름이 겹치지 않도록 이 이름으로 구현한다 — checklist.md)."""


@dataclass(frozen=True)
class SegmentedSample:
    """`DistributionRequest.samples`의 원소 — wire 표본 + 세그먼트 슬롯. 조립기
    (`predict_distribution`)의 시그니처는 이 슬롯 신설로 바뀌지 않는다(`segment`는
    아직 소비되지 않는다, OPEN-5D2-SAMPLE-SEGMENT)."""

    sample: features_pb2.CompetitionSample
    segment: SampleSegment | SegmentMissing


@dataclass(frozen=True)
class DistributionRequest:
    """분포 엔진 진입점 입력 — wire `CalculateOptimalBidRequest`에서 `from_proto`로만
    만든다((2b) 「연다(입력)」, 설계 검토 (2b) 표)."""

    samples: tuple[SegmentedSample, ...]
    base_amount: FactValue[float]
    agency: FactValue[str]
    category: FactValue[str]

    @staticmethod
    def from_proto(
        request: prediction_pb2.CalculateOptimalBidRequest,
    ) -> DistributionRequest | Unmeasurable:
        """`request.features`를 5B `FeatureFacts.from_proto`로 검증한다(base_amount·
        agency·category — `denominator_source`는 분포 엔진이 쓰지 않아 버린다). 실패는
        `Unmeasurable(FEATURE_ABSENT, ROW_REJECTED)`(GBM 의 `RowRejected` 과 같은 사유,
        predict.py 관례 재사용). 표본마다 `SegmentMissing()`을 붙인다(wire 에 표본별
        기관·공종 축이 없다, `OPEN-5D2-SAMPLE-SEGMENT`) — **요청의 `FeatureInputs.
        agency_id`로 표본을 같은 기관이라 가정하지 않는다**(scope.md 계약 갱신 이력)."""
        facts = FeatureFacts.from_proto(request.features)
        if isinstance(facts, FactRejected):
            return Unmeasurable(
                UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.ROW_REJECTED
            )
        return DistributionRequest(
            samples=tuple(
                SegmentedSample(sample=sample, segment=SegmentMissing())
                for sample in request.competition_samples
            ),
            base_amount=facts.base_amount,
            agency=facts.agency_id,
            category=facts.category_code,
        )


def _observe_all(
    samples: tuple[SegmentedSample, ...], policy: InferencePolicy
) -> tuple[list[ReserveDrawSample], int]:
    """행마다 `observe_sample` — 관측·거부 카운트로 분리(조용한 drop 금지). `segment`
    슬롯은 아직 읽지 않는다(OPEN-5D2-SAMPLE-SEGMENT — 후속이 wire 에서 채우면 여기서
    소비를 시작한다, 이 함수 시그니처는 그때도 바뀌지 않는다)."""
    observed: list[ReserveDrawSample] = []
    rejected_count = 0
    for segmented in samples:
        result = observe_sample(segmented.sample, policy)
        if isinstance(result, SampleRejected):
            rejected_count += 1
        else:
            observed.append(result)
    return observed, rejected_count


def _clean_statistics(
    observed: list[ReserveDrawSample],
) -> tuple[tuple[CleanAssessmentSample, ...], list[ReserveDrawSample], int]:
    """`admit_clean` 재사용(scope.md ②) — CLEAN 표본만 K5 집계·투찰율 축 산출에 쓴다
    (ML-04 ① — 비-CLEAN 이 값으로 편입되는 경로를 만들지 않는다)."""
    assessment_samples = [
        AssessmentSample(center=sample.center, provenance=sample.provenance)
        for sample in observed
    ]
    clean_levels, excluded_by_provenance = admit_clean(assessment_samples)
    clean_observed = [
        sample for sample in observed if sample.provenance is AssessmentProvenance.CLEAN
    ]
    return clean_levels, clean_observed, excluded_by_provenance


def _resolve_diagnostics(
    *,
    agency: LevelObservation | None,
    category: LevelObservation | None,
    posterior_shrinkage_weight: float,
    excluded_observations: int,
    policy: InferencePolicy,
) -> Diagnostics:
    """`segment_support` = agency 관측 ≥1 이면 DIRECT, 아니면 category 관측 ≥1 이면
    PARENT_CATEGORY, 아니면 GLOBAL(scope.md ②). 이 조립기에서 `agency`·`category`는
    항상 `None`이라(알려진 제한, 모듈 docstring) 실제로는 항상 GLOBAL 이 나온다 —
    golden `ml-kernel-011`은 이 함수를 agency/category 를 채워 직접 호출해 그 갈래를
    검증한다."""
    agency_sample_count = agency.sample_count if agency is not None else 0
    if agency is not None and agency.sample_count > 0:
        segment_support = SegmentSupport.DIRECT
    elif category is not None and category.sample_count > 0:
        segment_support = SegmentSupport.PARENT_CATEGORY
    else:
        segment_support = SegmentSupport.GLOBAL
    return Diagnostics(
        training_row_count=0,
        segment_support=segment_support,
        shrinkage_weight=Decimal(str(posterior_shrinkage_weight)),
        excluded_observations=excluded_observations,
        agency_sample_count=agency_sample_count,
        agency_sample_below_threshold=(
            agency_sample_count < policy.assessment_agency_sample_threshold
        ),
    )


@dataclass(frozen=True)
class _EstimationInputs:
    """`predict_distribution`의 함수 길이를 설계 래칫(50줄) 안으로 유지하려는 분리
    (내용은 그대로, 우회 경로 아님) — 관측·CLEAN 필터·투찰율 축 산출까지."""

    clean_levels: tuple[CleanAssessmentSample, ...]
    clean_observed: list[ReserveDrawSample]
    ratio_samples: list[float]
    excluded_observations: int


def _prepare_estimation_inputs(
    request: DistributionRequest, policy: InferencePolicy
) -> _EstimationInputs:
    observed, rejected_count = _observe_all(request.samples, policy)
    clean_levels, clean_observed, excluded_by_provenance = _clean_statistics(observed)
    ratio_samples = [
        sample.observed_bid_rate / sample.center for sample in clean_observed
    ]
    return _EstimationInputs(
        clean_levels=clean_levels,
        clean_observed=clean_observed,
        ratio_samples=ratio_samples,
        excluded_observations=rejected_count + excluded_by_provenance,
    )


def _assemble_success(
    *,
    candidates: tuple[Candidate, Candidate, Candidate],
    posterior: AssessmentPosterior,
    predictive_std: float,
    inputs: _EstimationInputs,
    policy: InferencePolicy,
) -> Success | Unmeasurable:
    """`predict_distribution`의 함수 길이를 설계 래칫(50줄) 안으로 유지하려는 분리
    (내용은 그대로) — 후보 3 이 나온 뒤 불확실성·진단 조립부터 `Success` 반환까지."""
    sample_size = len(inputs.clean_levels)
    uncertainty = resolve_uncertainty(
        sample_size=sample_size,
        dispersion=pstdev(inputs.ratio_samples),
        estimate_margin=float(policy.scenario_z) * predictive_std / sqrt(sample_size),
        interval_source=IntervalSource.POSTERIOR_PREDICTIVE,
        policy=policy,
    )
    if isinstance(uncertainty, Unmeasurable):
        return uncertainty

    diagnostics = _resolve_diagnostics(
        agency=None,
        category=None,
        posterior_shrinkage_weight=posterior.level_weights.agency,
        excluded_observations=inputs.excluded_observations,
        policy=policy,
    )

    return Success(
        candidates=candidates,
        fitness=PriceFitness(Decimal(str(posterior.mean))),
        uncertainty=uncertainty,
        diagnostics=diagnostics,
    )


def predict_distribution(
    request: DistributionRequest, policy: InferencePolicy
) -> Success | Unmeasurable:
    """legacy `_estimate_distribution` 조립(scope.md ②): 관측 정제 → CLEAN 필터 →
    가용성 게이트(조립기·진입점 공유, D-5D2-7) → K5 3계층 수축(agency/category 는
    항상 `None`, 알려진 제한) → 예측분산 합성 → 투찰율 축 환산(D-5D2-6, 축 혼재 없음)
    → 시나리오 → (불확실성·진단, `_assemble_success`)."""
    inputs = _prepare_estimation_inputs(request, policy)
    availability = distribution_availability(
        observation_count=len(inputs.clean_levels),
        ratio_sample_count=len(inputs.ratio_samples),
        policy=policy,
    )
    if isinstance(availability, Unmeasurable):
        return availability

    global_level = aggregate_level_observation(inputs.clean_levels)
    posterior = resolve_assessment_posterior(
        agency=None, category=None, global_level=global_level, policy=policy
    )
    if isinstance(posterior, Unmeasurable):
        return posterior

    draw_variance_mean = fmean(sample.draw_std**2 for sample in inputs.clean_observed)
    predictive_std = sqrt(posterior.std**2 + draw_variance_mean)
    bid_ratio = median(inputs.ratio_samples)

    candidates = build_scenario_candidates(
        center=posterior.mean, std=predictive_std, policy=policy, scale=bid_ratio
    )
    if isinstance(candidates, Unmeasurable):
        return candidates

    return _assemble_success(
        candidates=candidates,
        posterior=posterior,
        predictive_std=predictive_std,
        inputs=inputs,
        policy=policy,
    )
