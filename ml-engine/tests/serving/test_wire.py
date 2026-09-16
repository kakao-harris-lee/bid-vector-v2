"""RED — `ml_engine.serving.wire`(scope.md ②, D-5E2-5·6). `map_kernel_result`가
`Success`·`Unmeasurable`을 각각 어떻게 옮기는지, 매핑 불변식 위반이 결과가 아니라
`MappingRejected`가 되는지 확인한다."""

from __future__ import annotations

from decimal import Decimal

import pytest

from ml_engine.contracts import common_pb2, error_pb2, prediction_pb2
from ml_engine.inference.results import (
    Candidate,
    CandidateLabel,
    Diagnostics,
    IntervalSource,
    SegmentSupport,
    Success,
    Uncertainty,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)
from ml_engine.serving.wire import MappingRejected, _format_fraction, map_kernel_result


def _candidate(label: CandidateLabel, bid_rate: str, weight: str) -> Candidate:
    return Candidate(
        label=label,
        bid_rate=Decimal(bid_rate),
        weight=Decimal(weight),
        weight_policy_version="candidate-weight-v1",
    )


def _success(
    *,
    sample_size: int = 15,
    training_row_count: int = 0,
    bid_rate: str = "0.8500",
    dispersion: str = "0.0380",
) -> Success:
    return Success(
        candidates=(
            _candidate(CandidateLabel.CONSERVATIVE, bid_rate, "0.2400"),
            _candidate(CandidateLabel.BASE, "0.8750", "0.5200"),
            _candidate(CandidateLabel.AGGRESSIVE, "0.9000", "0.2400"),
        ),
        fitness=Decimal("0.6900"),  # type: ignore[arg-type]
        uncertainty=Uncertainty(
            sample_size=sample_size,
            dispersion=Decimal(dispersion),
            estimate_margin=Decimal("0.0210"),
            interval_source=IntervalSource.POSTERIOR_PREDICTIVE,
        ),
        diagnostics=Diagnostics(
            training_row_count=training_row_count,
            segment_support=SegmentSupport.GLOBAL,
            shrinkage_weight=Decimal("0.0000"),
            excluded_observations=0,
            agency_sample_count=0,
            agency_sample_below_threshold=False,
        ),
    )


def _release() -> prediction_pb2.ModelRelease:
    release = prediction_pb2.ModelRelease()
    release.release_id = "distribution/inference-v1"
    release.artifact_checksum = "sha256:" + "a" * 64
    release.feature_schema_version = "award-rate-features-v2"
    release.code_version = "ml-engine-test"
    release.dataset_id = ""
    release.release_kind = prediction_pb2.RELEASE_KIND_DERIVED
    return release


# ---- Success 매핑 ----


def test_maps_success_candidates_in_order_with_recommended_origin() -> None:
    mapped = map_kernel_result(_success(), _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    labels = [c.label for c in mapped.success.candidates]
    assert labels == [
        prediction_pb2.CANDIDATE_LABEL_CONSERVATIVE,
        prediction_pb2.CANDIDATE_LABEL_BASE,
        prediction_pb2.CANDIDATE_LABEL_AGGRESSIVE,
    ]
    assert all(
        c.origin == common_pb2.BID_RATE_ORIGIN_RECOMMENDED
        for c in mapped.success.candidates
    )


def test_maps_decimal_fields_to_normalized_fraction_strings() -> None:
    mapped = map_kernel_result(_success(), _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert mapped.success.candidates[0].bid_rate.fraction == "0.8500"
    assert mapped.success.uncertainty.dispersion == "0.0380"
    assert mapped.success.fitness.score == "0.6900"


def test_maps_uncertainty_interval_source() -> None:
    mapped = map_kernel_result(_success(), _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert (
        mapped.success.uncertainty.interval_source
        == prediction_pb2.INTERVAL_SOURCE_POSTERIOR_PREDICTIVE
    )


def test_release_is_copied_from_runtime_release_with_schema_echo() -> None:
    release = _release()
    mapped = map_kernel_result(_success(), release, "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert mapped.success.release.release_id == release.release_id
    assert mapped.success.release.artifact_checksum == release.artifact_checksum
    assert mapped.success.release.code_version == release.code_version
    assert mapped.success.release.release_kind == prediction_pb2.RELEASE_KIND_DERIVED
    assert mapped.success.release.feature_schema_version == "award-rate-features-v2"


def test_diagnostics_are_copied_through() -> None:
    mapped = map_kernel_result(_success(), _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert mapped.success.diagnostics.training_row_count == 0
    assert (
        mapped.success.diagnostics.segment_support
        == prediction_pb2.SEGMENT_SUPPORT_GLOBAL
    )
    assert mapped.success.diagnostics.shrinkage_weight.fraction == "0.0000"


# ---- Unmeasurable 매핑 ----


def test_maps_unmeasurable_reason_and_detail_code() -> None:
    unmeasurable = Unmeasurable(
        reason=UnmeasurableReason.UNTRAINED_SEGMENT,
        detail=UnmeasurableDetail.NEVER_TRAINED,
    )
    mapped = map_kernel_result(unmeasurable, _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert mapped.WhichOneof("result") == "unmeasurable"
    assert mapped.unmeasurable.reason == error_pb2.UNMEASURABLE_REASON_UNTRAINED_SEGMENT
    assert mapped.unmeasurable.detail_code == "NEVER_TRAINED"


# ---- 매핑 불변식(D-5E2-6) ----


def test_sample_size_zero_success_is_mapping_rejected() -> None:
    mapped = map_kernel_result(
        _success(sample_size=0), _release(), "award-rate-features-v2"
    )
    assert isinstance(mapped, MappingRejected)


def test_nonzero_training_row_count_is_mapping_rejected() -> None:
    """DERIVED release 는 아티팩트가 없어 학습 행 수가 항상 0 이어야 한다."""
    mapped = map_kernel_result(
        _success(training_row_count=4120), _release(), "award-rate-features-v2"
    )
    assert isinstance(mapped, MappingRejected)


def test_non_finite_decimal_is_mapping_rejected() -> None:
    success = _success()
    # frozen dataclass 라도 `object.__setattr__`로 사후 변조할 수 있다 — 엔진 결함을
    # 흉내낸다(구성 시점 `__post_init__`은 통과했지만 이후 값이 오염된 경우).
    object.__setattr__(success.uncertainty, "dispersion", Decimal("Infinity"))
    mapped = map_kernel_result(success, _release(), "award-rate-features-v2")
    assert isinstance(mapped, MappingRejected)


def test_candidate_count_not_three_is_mapping_rejected() -> None:
    success = _success()
    object.__setattr__(success, "candidates", success.candidates[:2])
    mapped = map_kernel_result(success, _release(), "award-rate-features-v2")
    assert isinstance(mapped, MappingRejected)


@pytest.mark.parametrize(
    "value",
    ["0.0000001", "0.24", "1", "0.8700"],
)
def test_format_fraction_round_trips_kotlin_normalized_forms(value: str) -> None:
    """D-5E2-5 — Kotlin `FractionRules.isNormalizedFraction`가 받아들이는 형태와
    `format(d, "f")`가 낸 형태가 같음을 왕복으로 확인한다(exponent 없음, scale 보존)."""
    assert _format_fraction(Decimal(value)) == value
