"""RED — `ml_engine.inference.results`(scope.md ③, 설계 검토 (5) 구현 지시 2). 필드 타입
전수(float 없음), 후보 순서 고정, `bid_rate > 0` 불변식.
"""

from __future__ import annotations

import dataclasses
from decimal import Decimal

import pytest

from ml_engine.inference.results import (
    Candidate,
    CandidateLabel,
    Diagnostics,
    IntervalSource,
    PriceFitness,
    SegmentSupport,
    Success,
    Uncertainty,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)


def _candidate(label: CandidateLabel, rate: str) -> Candidate:
    return Candidate(
        label=label,
        bid_rate=Decimal(rate),
        weight=Decimal("0.24"),
        weight_policy_version="inference-v1",
    )


def test_candidate_bid_rate_must_be_decimal() -> None:
    with pytest.raises(TypeError):
        Candidate(
            label=CandidateLabel.BASE,
            bid_rate=0.9,  # type: ignore[arg-type]
            weight=Decimal("0.52"),
            weight_policy_version="inference-v1",
        )


def test_candidate_weight_must_be_decimal() -> None:
    with pytest.raises(TypeError):
        Candidate(
            label=CandidateLabel.BASE,
            bid_rate=Decimal("0.9"),
            weight=0.52,  # type: ignore[arg-type]
            weight_policy_version="inference-v1",
        )


@pytest.mark.parametrize("rate", ["0", "-0.1"])
def test_candidate_bid_rate_must_be_positive(rate: str) -> None:
    with pytest.raises(ValueError, match="bid_rate"):
        _candidate(CandidateLabel.BASE, rate)


def test_success_candidates_must_be_exactly_three() -> None:
    with pytest.raises(ValueError, match="3"):
        Success(
            candidates=(
                _candidate(CandidateLabel.CONSERVATIVE, "0.8"),
                _candidate(CandidateLabel.BASE, "0.9"),
            ),  # type: ignore[arg-type]
            fitness=PriceFitness(Decimal("0.9")),
            uncertainty=Uncertainty(
                sample_size=10,
                dispersion=Decimal("0.02"),
                estimate_margin=Decimal("0.03"),
                interval_source=IntervalSource.CROSS_VALIDATION_RESIDUAL,
            ),
            diagnostics=Diagnostics(
                training_row_count=100,
                segment_support=SegmentSupport.DIRECT,
                shrinkage_weight=Decimal("0"),
                excluded_observations=0,
            ),
        )


def test_success_candidate_order_is_fixed() -> None:
    with pytest.raises(ValueError, match="순서"):
        Success(
            candidates=(
                _candidate(CandidateLabel.BASE, "0.9"),
                _candidate(CandidateLabel.CONSERVATIVE, "0.8"),
                _candidate(CandidateLabel.AGGRESSIVE, "1.0"),
            ),
            fitness=PriceFitness(Decimal("0.9")),
            uncertainty=Uncertainty(
                sample_size=10,
                dispersion=Decimal("0.02"),
                estimate_margin=Decimal("0.03"),
                interval_source=IntervalSource.CROSS_VALIDATION_RESIDUAL,
            ),
            diagnostics=Diagnostics(
                training_row_count=100,
                segment_support=SegmentSupport.DIRECT,
                shrinkage_weight=Decimal("0"),
                excluded_observations=0,
            ),
        )


def test_success_with_correct_order_constructs() -> None:
    success = Success(
        candidates=(
            _candidate(CandidateLabel.CONSERVATIVE, "0.8"),
            _candidate(CandidateLabel.BASE, "0.9"),
            _candidate(CandidateLabel.AGGRESSIVE, "1.0"),
        ),
        fitness=PriceFitness(Decimal("0.9")),
        uncertainty=Uncertainty(
            sample_size=10,
            dispersion=Decimal("0.02"),
            estimate_margin=Decimal("0.03"),
            interval_source=IntervalSource.CROSS_VALIDATION_RESIDUAL,
        ),
        diagnostics=Diagnostics(
            training_row_count=100,
            segment_support=SegmentSupport.DIRECT,
            shrinkage_weight=Decimal("0"),
            excluded_observations=0,
        ),
    )
    assert success.candidates[1].label is CandidateLabel.BASE


def test_uncertainty_dispersion_and_margin_must_be_decimal() -> None:
    with pytest.raises(TypeError):
        Uncertainty(
            sample_size=1,
            dispersion=0.02,  # type: ignore[arg-type]
            estimate_margin=Decimal("0.03"),
            interval_source=IntervalSource.CROSS_VALIDATION_RESIDUAL,
        )


def test_diagnostics_shrinkage_weight_must_be_decimal() -> None:
    with pytest.raises(TypeError):
        Diagnostics(
            training_row_count=1,
            segment_support=SegmentSupport.DIRECT,
            shrinkage_weight=0.0,  # type: ignore[arg-type]
            excluded_observations=0,
        )


def test_all_result_dataclasses_are_frozen() -> None:
    unmeasurable = Unmeasurable(
        UnmeasurableReason.INSUFFICIENT_SAMPLES, UnmeasurableDetail.TOO_FEW_DRAWS
    )
    with pytest.raises(dataclasses.FrozenInstanceError):
        unmeasurable.reason = UnmeasurableReason.FEATURE_ABSENT  # type: ignore[misc]


def test_no_float_fields_in_candidate_or_uncertainty_or_diagnostics() -> None:
    """결과 타입에 float 필드가 없다(설계 검토 (1) 「Decimal 경계」 행) — 타입 애너테이션
    전수로 확인한다."""
    for cls, fields in (
        (Candidate, ("bid_rate", "weight")),
        (Uncertainty, ("dispersion", "estimate_margin")),
        (Diagnostics, ("shrinkage_weight",)),
    ):
        type_hints = {field.name: field.type for field in dataclasses.fields(cls)}
        for field_name in fields:
            assert type_hints[field_name] in ("Decimal", Decimal), (
                f"{cls.__name__}.{field_name} 는 Decimal 이어야 한다: {type_hints[field_name]!r}"
            )
