"""`ml_engine.serving.wire` — `KernelResult` → `CalculateOptimalBidResponse` 순수 매핑
(scope.md ②, D-5E2-5·6). 값을 지어내지 않는다 — 엔진(`ml_engine.inference.results`)이
낸 값만 옮긴다. `release`는 호출자가 준다(런타임 상수, D-5E2-1) — 이 모듈은 release 를
만들지 않는다(`serving.runtime` 소관).

매핑 불변식(D-5E2-6) — `sample_size == 0`인 `Success`·후보 수 ≠ 3·`training_row_count
≠ 0`(이 slice 는 DERIVED release 만 생산해 아티팩트 학습 행이 있을 수 없다)·비유한
Decimal 은 엔진 결함이다. 결과가 아니라 `MappingRejected`(결과 타입)로 나른다 — servicer
가 이를 삼키지 않고 예외로 올린다(`prediction.py`, BLE 규율)."""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from decimal import Decimal

from ml_engine.contracts import common_pb2, error_pb2, prediction_pb2
from ml_engine.inference.results import (
    Candidate,
    CandidateLabel,
    Diagnostics,
    IntervalSource,
    KernelResult,
    SegmentSupport,
    Success,
    Uncertainty,
    Unmeasurable,
    UnmeasurableReason,
)

_CANDIDATE_LABEL_TO_WIRE: dict[CandidateLabel, int] = {
    CandidateLabel.CONSERVATIVE: prediction_pb2.CANDIDATE_LABEL_CONSERVATIVE,
    CandidateLabel.BASE: prediction_pb2.CANDIDATE_LABEL_BASE,
    CandidateLabel.AGGRESSIVE: prediction_pb2.CANDIDATE_LABEL_AGGRESSIVE,
}

_INTERVAL_SOURCE_TO_WIRE: dict[IntervalSource, int] = {
    IntervalSource.CROSS_VALIDATION_RESIDUAL: (
        prediction_pb2.INTERVAL_SOURCE_CROSS_VALIDATION_RESIDUAL
    ),
    IntervalSource.TIME_HOLDOUT_RESIDUAL: (
        prediction_pb2.INTERVAL_SOURCE_TIME_HOLDOUT_RESIDUAL
    ),
    IntervalSource.POSTERIOR_PREDICTIVE: (
        prediction_pb2.INTERVAL_SOURCE_POSTERIOR_PREDICTIVE
    ),
}

_SEGMENT_SUPPORT_TO_WIRE: dict[SegmentSupport, int] = {
    SegmentSupport.DIRECT: prediction_pb2.SEGMENT_SUPPORT_DIRECT,
    SegmentSupport.PARENT_CATEGORY: prediction_pb2.SEGMENT_SUPPORT_PARENT_CATEGORY,
    SegmentSupport.GLOBAL: prediction_pb2.SEGMENT_SUPPORT_GLOBAL,
}

_UNMEASURABLE_REASON_TO_WIRE: dict[UnmeasurableReason, int] = {
    UnmeasurableReason.INSUFFICIENT_SAMPLES: (
        error_pb2.UNMEASURABLE_REASON_INSUFFICIENT_SAMPLES
    ),
    UnmeasurableReason.UNTRAINED_SEGMENT: error_pb2.UNMEASURABLE_REASON_UNTRAINED_SEGMENT,
    UnmeasurableReason.FEATURE_ABSENT: error_pb2.UNMEASURABLE_REASON_FEATURE_ABSENT,
}


@dataclass(frozen=True)
class MappingRejected:
    """매핑 불변식 위반(D-5E2-6) — 엔진 결함이지 `Unmeasurable`이 아니다. servicer 가
    예외로 올린다(위장하지 않는다)."""

    reason: str


def _format_fraction(value: Decimal) -> str:
    """D-5E2-5 — decimal 정규형(`format(d, "f")`, 지수 표기 없음·scale 보존). 호출 전
    `value.is_finite()`를 확인하는 것은 호출부(`_check_invariants`) 책임이다 — 이 함수는
    유한값만 받는다고 가정한다(비유한 값은 매핑 불변식 위반으로 그 전에 걸러진다)."""
    return format(value, "f")


def _check_invariants(success: Success) -> MappingRejected | None:
    """D-5E2-6 넷: 후보 정확히 3(구조적으로 `Success.__post_init__`이 이미 강제하지만,
    frozen dataclass 도 `object.__setattr__`로 사후 변조될 수 있어 여기서도 확인한다)·
    `sample_size > 0`·`training_row_count == 0`(이 slice 는 DERIVED release 만 생산해
    학습 아티팩트가 없다)·모든 Decimal 필드가 유한."""
    # `Sequence`로 국소적으로 넓혀 확인한다 — `Success.candidates`의 정적 타입
    # (`tuple[Candidate, Candidate, Candidate]`)은 길이 3을 이미 보장하므로, 그 타입
    # 그대로 `len(...) != 3`을 검사하면 mypy `warn_unreachable`이 "항상 거짓"이라며
    # 막는다. 이 검사가 지키는 것은 **그 타입 보장이 사후에 깨진 경우**(frozen
    # dataclass 라도 `object.__setattr__`로 변조 가능, test 가 실측)다 — 타입 밖 방어라
    # 타입을 좁혀 mypy 를 속이지 않고 넓혀서 실행 시점 검사를 유지한다.
    candidates: Sequence[Candidate] = success.candidates
    if len(candidates) != 3:
        return MappingRejected(
            f"candidates 는 정확히 3건이어야 한다: {len(candidates)}건"
        )
    if success.uncertainty.sample_size == 0:
        return MappingRejected(
            "sample_size 는 0일 수 없다(Success 가 아니라 Unmeasurable)"
        )
    if success.diagnostics.training_row_count != 0:
        return MappingRejected(
            "training_row_count 는 DERIVED release 에서 0이어야 한다: "
            f"{success.diagnostics.training_row_count}"
        )
    non_finite = _first_non_finite_decimal(success)
    if non_finite is not None:
        return MappingRejected(f"비유한 Decimal 값: {non_finite!r}")
    return None


def _first_non_finite_decimal(success: Success) -> Decimal | None:
    candidates_values = [
        value
        for candidate in success.candidates
        for value in (candidate.bid_rate, candidate.weight)
    ]
    uncertainty_values = [
        success.uncertainty.dispersion,
        success.uncertainty.estimate_margin,
    ]
    diagnostics_values = [success.diagnostics.shrinkage_weight]
    for value in (
        success.fitness,
        *candidates_values,
        *uncertainty_values,
        *diagnostics_values,
    ):
        if not value.is_finite():
            return value
    return None


def _map_candidate(candidate: Candidate) -> prediction_pb2.Candidate:
    wire_candidate = prediction_pb2.Candidate()
    wire_candidate.label = _CANDIDATE_LABEL_TO_WIRE[candidate.label]
    wire_candidate.bid_rate.fraction = _format_fraction(candidate.bid_rate)
    wire_candidate.origin = common_pb2.BID_RATE_ORIGIN_RECOMMENDED
    wire_candidate.weight.fraction = _format_fraction(candidate.weight)
    wire_candidate.weight_policy_version = candidate.weight_policy_version
    return wire_candidate


def _map_uncertainty(uncertainty: Uncertainty) -> prediction_pb2.Uncertainty:
    wire_uncertainty = prediction_pb2.Uncertainty()
    wire_uncertainty.sample_size = uncertainty.sample_size
    wire_uncertainty.dispersion = _format_fraction(uncertainty.dispersion)
    wire_uncertainty.estimate_margin = _format_fraction(uncertainty.estimate_margin)
    wire_uncertainty.interval_source = _INTERVAL_SOURCE_TO_WIRE[
        uncertainty.interval_source
    ]
    return wire_uncertainty


def _map_diagnostics(diagnostics: Diagnostics) -> prediction_pb2.Diagnostics:
    wire_diagnostics = prediction_pb2.Diagnostics()
    wire_diagnostics.training_row_count = diagnostics.training_row_count
    wire_diagnostics.segment_support = _SEGMENT_SUPPORT_TO_WIRE[
        diagnostics.segment_support
    ]
    wire_diagnostics.shrinkage_weight.fraction = _format_fraction(
        diagnostics.shrinkage_weight
    )
    wire_diagnostics.excluded_observations = diagnostics.excluded_observations
    wire_diagnostics.agency_sample_count = diagnostics.agency_sample_count
    wire_diagnostics.agency_sample_below_threshold = (
        diagnostics.agency_sample_below_threshold
    )
    return wire_diagnostics


def _map_success(
    success: Success,
    release: prediction_pb2.ModelRelease,
    feature_schema_version: str,
) -> prediction_pb2.CalculateOptimalBidResponse | MappingRejected:
    invariant_violation = _check_invariants(success)
    if invariant_violation is not None:
        return invariant_violation

    response = prediction_pb2.CalculateOptimalBidResponse()
    wire_success = response.success
    wire_success.candidates.extend(
        _map_candidate(candidate) for candidate in success.candidates
    )
    wire_success.fitness.score = _format_fraction(success.fitness)
    wire_success.uncertainty.CopyFrom(_map_uncertainty(success.uncertainty))
    # D-5E2-1 — release 는 런타임 상수의 복사본 + 요청 값 에코(feature_schema_version
    # 하나만). 다른 네 성분(release_id·artifact_checksum·code_version·dataset_id·
    # release_kind)은 `runtime.release`(`build_derived_release`의 유일 산출)를 그대로
    # 옮긴다 — `promoted`(prediction.py::GetModelMetadata)와 같은 객체의 복사본이라
    # 두 자리가 다른 코드 경로로 벌어질 수 없다.
    wire_success.release.CopyFrom(release)
    wire_success.release.feature_schema_version = feature_schema_version
    wire_success.diagnostics.CopyFrom(_map_diagnostics(success.diagnostics))
    return response


def _map_unmeasurable(
    unmeasurable: Unmeasurable,
) -> prediction_pb2.CalculateOptimalBidResponse:
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.unmeasurable.reason = _UNMEASURABLE_REASON_TO_WIRE[unmeasurable.reason]
    response.unmeasurable.detail_code = unmeasurable.detail.value
    return response


def map_kernel_result(
    result: KernelResult,
    release: prediction_pb2.ModelRelease,
    feature_schema_version: str,
) -> prediction_pb2.CalculateOptimalBidResponse | MappingRejected:
    """유일 진입점(scope.md ②) — `Success`는 release·feature_schema_version 을 함께
    받아 매핑하고(불변식 위반은 `MappingRejected`), `Unmeasurable`은 값을 그대로 옮긴다
    (release 축이 없다 — `Unmeasurable`은 release 를 나르지 않는다, 위협 모델 (a))."""
    if isinstance(result, Unmeasurable):
        return _map_unmeasurable(result)
    return _map_success(result, release, feature_schema_version)
