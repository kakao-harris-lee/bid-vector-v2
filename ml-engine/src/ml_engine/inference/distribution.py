"""Reuse: bid-vector/app/ai/predictors/distribution.py@ed4b06c

`ml_engine.inference.distribution` — 분포 엔진 조립기(scope.md ②). `_estimate_distribution`
흐름 — 조사 §5 흐름 그대로: 관측 정제 → K6 draw 모멘트 → 3계층 K5 수축 → 예측분산 합성 →
투찰율 축 환산 → 시나리오 조립. 산식 자체는 K5(`assessment.py`)·K6(`reserve_draw.py`)에
이미 이식돼 있다 — 이 모듈은 legacy `_estimate_distribution`·`_resolve_posterior`가 하던
**조립**만 새로 짠다(D-5D2-1 (b), 분포 단독 엔진).

M5/5D-3(scope.md, `_workspace/m5-5d3/02_design-review.md`) — `OPEN-5D2-SAMPLE-SEGMENT`
해소. M2/2F 가 `CompetitionSample`에 표본별 기관·공종 축(`agency_id`·`category_code`,
`AgencyIdFact`/`CategoryCodeFact`)을 추가했다(D-2F-1). 이 조립기는 그 축을 요청 축
(`DistributionRequest.agency`/`category`)과 **정규화 문자열 동일 매칭**(별칭 없음,
D-5D3-1)으로 이어 3계층(발주기관/공종/전역) 수축을 서빙 경로에서 만든다. 요청 축이
`Missing`이면 그 계층은 매칭 불가(`None`) — 표본 축이 있어도 요청을 같은 기관이라
가정하지 않는다(D-5D3-3). 표본 축 판독은 5B `resolve_text_fact`(허용 결측 사유를
`{NOT_COLLECTED_YET}`로 좁힘)를 그대로 쓴다 — 분포 엔진 안에 두 번째 판독기를 두지
않는다(D-5D3-6). 판독 거부는 표본 하나만 `SampleRejected(SEGMENT_REASON_NOT_ALLOWED)`로
접혀 요청 전체를 죽이지 않는다(D-5D3-2).
"""

from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from decimal import Decimal
from math import sqrt
from statistics import fmean, median, pstdev

from ml_engine.contracts import common_pb2, features_pb2, prediction_pb2
from ml_engine.features import (
    FactRejected,
    FactValue,
    FeatureFacts,
    Present,
    resolve_text_fact,
)
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
    SampleRejectionReason,
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


# 표본 축(agency_id/category_code)이 허용하는 유일한 결측 사유(D-5D3-2) — 요청 축(열린
# 집합, F-10)과 달리 닫힌 집합이다. 송신 어댑터가 다른 사유를 지어내면 거부다(2F
# `features.proto` 주석 「송신 어댑터가 다른 사유를 지어내지 않는다」).
def _is_segment_missing_reason_allowed(raw: int) -> bool:
    return bool(raw == common_pb2.MISSING_REASON_NOT_COLLECTED_YET)


@dataclass(frozen=True)
class SampleSegment:
    """표본 하나의 발주기관·공종 축(D-5D3-6) — 각 축은 5B `FactValue[str]`
    (`Present[str] | Missing`)다. 「값 없음」은 이 타입 자체의 `Missing` 상태가
    나른다 — 별도 `SegmentMissing` 마커를 두지 않는다(둘 다 `Missing`인 상태가
    구 마커의 자리를 대신한다, 불가능한 상태 하나 제거)."""

    agency: FactValue[str]
    category: FactValue[str]


@dataclass(frozen=True)
class SegmentedSample:
    """`DistributionRequest.samples`의 원소 — wire 표본 + 세그먼트. `segment`는
    `from_proto`가 표본마다 즉시 판독한다(`SampleSegment` 또는 판독 거부를 접은
    `SampleRejected(SEGMENT_REASON_NOT_ALLOWED)`) — 판정 자체는 정책이 필요 없는
    순수 매핑이라 관측 정제(`observe_sample`, policy 필요)보다 먼저 끝낼 수 있다."""

    sample: features_pb2.CompetitionSample
    segment: SampleSegment | SampleRejected


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
        predict.py 관례 재사용). 표본마다 `_resolve_segment`로 기관·공종 축을 판독한다
        (D-5D3-6) — **요청의 `FeatureInputs.agency_id`로 표본을 같은 기관이라
        가정하지 않는다**(scope.md 계약 갱신 이력, D-5D3-3)."""
        facts = FeatureFacts.from_proto(request.features)
        if isinstance(facts, FactRejected):
            return Unmeasurable(
                UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.ROW_REJECTED
            )
        return DistributionRequest(
            samples=tuple(
                SegmentedSample(sample=sample, segment=_resolve_segment(sample))
                for sample in request.competition_samples
            ),
            base_amount=facts.base_amount,
            agency=facts.agency_id,
            category=facts.category_code,
        )


def _resolve_segment(
    sample: features_pb2.CompetitionSample,
) -> SampleSegment | SampleRejected:
    """표본별 기관·공종 축 판독(D-5D3-6) — 5B `resolve_text_fact`를 요청 축과 같은
    코드로 쓰되 결측 사유 술어를 `{NOT_COLLECTED_YET}`만 참인 닫힌 집합으로 좁힌다
    (D-5D3-2). 어느 한 축이라도 거부되면(oneof 미설정·정규화 뒤 빈 키·그 밖 결측 사유)
    표본 전체가 `SampleRejected(SEGMENT_REASON_NOT_ALLOWED)`다 — 5B 의 구체적
    `FactRejectionReason`은 여기서 이 사유 하나로 접힌다."""
    agency = resolve_text_fact(
        sample.agency_id,
        field="agency_id",
        is_allowed_missing_reason=_is_segment_missing_reason_allowed,
    )
    if isinstance(agency, FactRejected):
        return SampleRejected(SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED)
    category = resolve_text_fact(
        sample.category_code,
        field="category_code",
        is_allowed_missing_reason=_is_segment_missing_reason_allowed,
    )
    if isinstance(category, FactRejected):
        return SampleRejected(SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED)
    return SampleSegment(agency=agency, category=category)


@dataclass(frozen=True)
class _ObservedRow:
    """관측 정제를 통과한 표본 하나 — `ReserveDrawSample`(K5/K6 입력)과 `SampleSegment`
    (계층 매칭 축)을 함께 나른다(D-5D3-1·3·4 매칭에 둘 다 필요). 분리는 내용 보존만
    (설계 래칫, 우회 경로 아님)."""

    reserve: ReserveDrawSample
    segment: SampleSegment


def _observe_all(
    samples: tuple[SegmentedSample, ...], policy: InferencePolicy
) -> tuple[list[_ObservedRow], int]:
    """행마다 `observe_sample`(관측 정제) + `segment`(이미 `from_proto`가 판독) 게이트
    둘을 본다 — 조용한 drop 금지, 어느 쪽이 거부해도 그 표본 하나만 `rejected_count`에
    센다(D-5D3-2, 이중 계수 금지)."""
    observed: list[_ObservedRow] = []
    rejected_count = 0
    for segmented in samples:
        reserve = observe_sample(segmented.sample, policy)
        if isinstance(reserve, SampleRejected):
            rejected_count += 1
            continue
        if isinstance(segmented.segment, SampleRejected):
            rejected_count += 1
            continue
        observed.append(_ObservedRow(reserve=reserve, segment=segmented.segment))
    return observed, rejected_count


def _clean_statistics(
    observed: list[_ObservedRow],
) -> tuple[tuple[CleanAssessmentSample, ...], list[_ObservedRow], int]:
    """`admit_clean` 재사용(scope.md ②) — CLEAN 표본만 K5 집계·투찰율 축 산출에 쓴다
    (ML-04 ① — 비-CLEAN 이 값으로 편입되는 경로를 만들지 않는다). `clean_levels`와
    `clean_observed`는 같은 술어(`provenance is CLEAN`)로 걸러 순서·길이가 일치한다 —
    `_resolve_levels`가 이 정합을 `zip(..., strict=True)`로 이용한다."""
    assessment_samples = [
        AssessmentSample(center=row.reserve.center, provenance=row.reserve.provenance)
        for row in observed
    ]
    clean_levels, excluded_by_provenance = admit_clean(assessment_samples)
    clean_observed = [
        row for row in observed if row.reserve.provenance is AssessmentProvenance.CLEAN
    ]
    return clean_levels, clean_observed, excluded_by_provenance


def _matches(sample_axis: FactValue[str], request_axis: FactValue[str]) -> bool:
    """D-5D3-1 — 정규화 문자열 동일만(별칭·부분 일치 없음, 양쪽 다 5B 판독기를 거친
    문자열이라 이미 같은 정규화를 탔다). 요청 축이 `Missing`이면 항상 불일치
    (D-5D3-3 — 요청 결측을 값으로 가정하지 않는다) — 표본 축이 `Missing`이어도 마찬가지
    (`Present`끼리만 비교, 우회 후보 (9))."""
    return (
        isinstance(sample_axis, Present)
        and isinstance(request_axis, Present)
        and sample_axis.value == request_axis.value
    )


def _matched_level(
    paired: list[tuple[CleanAssessmentSample, SampleSegment]],
    *,
    request_axis: FactValue[str],
    axis: Callable[[SampleSegment], FactValue[str]],
) -> LevelObservation | None:
    """CLEAN 집합에 매칭 술어를 독립 적용(D-5D3-4) — agency·category 호출은 서로의
    결과에 영향을 주지 않는다(agency 매칭 표본이 category 매칭 집합에도 들 수 있다,
    우회 후보 (7))."""
    matched = [
        clean for clean, segment in paired if _matches(axis(segment), request_axis)
    ]
    return aggregate_level_observation(matched)


def _resolve_levels(
    inputs: _EstimationInputs, request: DistributionRequest
) -> tuple[LevelObservation | None, LevelObservation | None, LevelObservation | None]:
    """(agency, category, global) — 요청 축 기준 매칭 술어 둘을 CLEAN 집합에 독립
    적용한다(D-5D3-1·3·4). `global`은 매칭과 무관하게 CLEAN 전체."""
    paired = list(
        zip(
            inputs.clean_levels,
            (row.segment for row in inputs.clean_observed),
            strict=True,
        )
    )
    agency_level = _matched_level(
        paired, request_axis=request.agency, axis=lambda segment: segment.agency
    )
    category_level = _matched_level(
        paired, request_axis=request.category, axis=lambda segment: segment.category
    )
    global_level = aggregate_level_observation(inputs.clean_levels)
    return agency_level, category_level, global_level


def _resolve_diagnostics(
    *,
    agency: LevelObservation | None,
    category: LevelObservation | None,
    posterior_shrinkage_weight: float,
    excluded_observations: int,
    policy: InferencePolicy,
) -> Diagnostics:
    """`segment_support` = agency 관측 ≥1 이면 DIRECT, 아니면 category 관측 ≥1 이면
    PARENT_CATEGORY, 아니면 GLOBAL(scope.md ②). M5/5D-3부터 `agency`·`category`는
    실제 매칭 결과다(`_resolve_levels`) — 요청 축이 `Missing`이거나 매칭 표본이 없으면
    여전히 `None`이라 GLOBAL로 접힌다(D-5D3-5, 5D-2 회귀와 같은 갈래)."""
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
    clean_observed: list[_ObservedRow]
    ratio_samples: list[float]
    excluded_observations: int


def _prepare_estimation_inputs(
    request: DistributionRequest, policy: InferencePolicy
) -> _EstimationInputs:
    observed, rejected_count = _observe_all(request.samples, policy)
    clean_levels, clean_observed, excluded_by_provenance = _clean_statistics(observed)
    ratio_samples = [
        row.reserve.observed_bid_rate / row.reserve.center for row in clean_observed
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
    agency: LevelObservation | None,
    category: LevelObservation | None,
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
        agency=agency,
        category=category,
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
    요청 축과 매칭된 실제 관측, D-5D3-1~4) → 예측분산 합성 → 투찰율 축 환산(D-5D2-6,
    축 혼재 없음) → 시나리오 → (불확실성·진단, `_assemble_success`)."""
    inputs = _prepare_estimation_inputs(request, policy)
    availability = distribution_availability(
        observation_count=len(inputs.clean_levels),
        ratio_sample_count=len(inputs.ratio_samples),
        policy=policy,
    )
    if isinstance(availability, Unmeasurable):
        return availability

    agency_level, category_level, global_level = _resolve_levels(inputs, request)
    posterior = resolve_assessment_posterior(
        agency=agency_level,
        category=category_level,
        global_level=global_level,
        policy=policy,
    )
    if isinstance(posterior, Unmeasurable):
        return posterior

    draw_variance_mean = fmean(row.reserve.draw_std**2 for row in inputs.clean_observed)
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
        agency=agency_level,
        category=category_level,
    )
