"""RED — `ml_engine.inference.distribution`(scope.md ②, 설계 검토 구현 지시 5). legacy
`_estimate_distribution` 조립의 재현: 가용성 게이트 공유(D-5D2-7)·CLEAN 필터(ML-04 ①)·
투찰율 축 환산(D-5D2-6)·진단(agency/category 는 항상 `None`, 알려진 제한).

Reuse: bid-vector/app/ai/predictors/distribution.py@ed4b06c
"""

from __future__ import annotations

from decimal import Decimal
from statistics import fmean, pstdev

import pytest
from _policy_support import shipped_inference_policy_for_test
from _sample_support import VALID_RATIOS, competition_sample

from ml_engine.contracts import common_pb2, features_pb2, prediction_pb2
from ml_engine.inference.assessment import LevelObservation
from ml_engine.inference.distribution import (
    DistributionRequest,
    SegmentedSample,
    SegmentMissing,
    _resolve_diagnostics,
    predict_distribution,
)
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.results import (
    IntervalSource,
    SegmentSupport,
    Success,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)

_EXPECTED_CENTER = fmean(VALID_RATIOS)
_BID_RATES = ("0.90", "0.91", "0.92", "0.93", "0.94", "0.95", "0.96", "0.97")


@pytest.fixture(scope="module")
def policy() -> InferencePolicy:
    return shipped_inference_policy_for_test()


def _segmented(
    samples: list[features_pb2.CompetitionSample],
) -> tuple[SegmentedSample, ...]:
    """`OPEN-5D2-SAMPLE-SEGMENT` — 이 slice 에서는 항상 `SegmentMissing()`."""
    return tuple(
        SegmentedSample(sample=sample, segment=SegmentMissing()) for sample in samples
    )


def _valid_request(
    *,
    count: int = 8,
    bid_rates: tuple[str, ...] = _BID_RATES,
    provenance: int = common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN,
) -> DistributionRequest:
    samples = [
        competition_sample(
            observed_bid_rate=bid_rates[i % len(bid_rates)], provenance=provenance
        )
        for i in range(count)
    ]
    return DistributionRequest(
        samples=_segmented(samples),
        base_amount=None,  # type: ignore[arg-type]  # 조립기가 소비하지 않는다(알려진 제한)
        agency=None,  # type: ignore[arg-type]
        category=None,  # type: ignore[arg-type]
    )


def _valid_features_inputs() -> features_pb2.FeatureInputs:
    inputs = features_pb2.FeatureInputs()
    inputs.base_amount.value.CopyFrom(
        common_pb2.Money(
            amount_won=1_000_000_000,
            currency=common_pb2.CURRENCY_KRW,
            basis=common_pb2.BASIS_BASE_AMOUNT,
            vat_treatment=common_pb2.VAT_TREATMENT_INCLUSIVE,
            provenance=common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED,
        )
    )
    inputs.category_code.value = "civil-works"
    inputs.agency_id.value = "agency-1"
    inputs.base_amount_provenance_label.value = (
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN
    )
    return inputs


class TestDistributionRequestFromProto:
    def test_valid_request_builds(self) -> None:
        request = prediction_pb2.CalculateOptimalBidRequest(
            features=_valid_features_inputs(),
            competition_samples=[competition_sample() for _ in range(3)],
        )
        result = DistributionRequest.from_proto(request)
        assert isinstance(result, DistributionRequest)
        assert len(result.samples) == 3

    def test_malformed_features_is_feature_absent(self) -> None:
        request = prediction_pb2.CalculateOptimalBidRequest(
            features=features_pb2.FeatureInputs(),  # oneof 전부 미설정
        )
        result = DistributionRequest.from_proto(request)
        assert result == Unmeasurable(
            UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.ROW_REJECTED
        )

    def test_every_sample_segment_is_missing(self) -> None:
        """`OPEN-5D2-SAMPLE-SEGMENT` — wire 에 표본별 기관·공종 축이 없어 `from_proto`
        는 표본 수와 무관하게 전부 `SegmentMissing()`을 낸다."""
        request = prediction_pb2.CalculateOptimalBidRequest(
            features=_valid_features_inputs(),
            competition_samples=[competition_sample() for _ in range(3)],
        )
        result = DistributionRequest.from_proto(request)
        assert isinstance(result, DistributionRequest)
        assert all(
            isinstance(segmented.segment, SegmentMissing)
            for segmented in result.samples
        )


class TestPredictDistribution:
    def test_success_with_eight_clean_samples(self, policy: InferencePolicy) -> None:
        request = _valid_request()
        result = predict_distribution(request, policy)
        assert isinstance(result, Success)
        assert len(result.candidates) == 3
        assert result.fitness == Decimal(str(_EXPECTED_CENTER))
        assert result.uncertainty.sample_size == 8
        assert result.uncertainty.interval_source is IntervalSource.POSTERIOR_PREDICTIVE
        expected_ratios = [float(rate) / _EXPECTED_CENTER for rate in _BID_RATES]
        assert result.uncertainty.dispersion == Decimal(str(pstdev(expected_ratios)))
        assert result.diagnostics.segment_support is SegmentSupport.GLOBAL
        assert result.diagnostics.agency_sample_count == 0
        assert result.diagnostics.excluded_observations == 0
        assert result.diagnostics.training_row_count == 0

    def test_below_min_reserve_records_is_unmeasurable(
        self, policy: InferencePolicy
    ) -> None:
        request = _valid_request(count=policy.reserve_min_reserve_records - 1)
        result = predict_distribution(request, policy)
        assert isinstance(result, Unmeasurable)
        assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
        assert result.detail is UnmeasurableDetail.TOO_FEW_OBSERVATIONS

    def test_rejected_samples_are_excluded_and_counted(
        self, policy: InferencePolicy
    ) -> None:
        valid = [
            competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
            for i in range(8)
        ]
        invalid = [competition_sample(base_amount_won=0) for _ in range(2)]
        request = DistributionRequest(
            samples=_segmented(valid + invalid),
            base_amount=None,  # type: ignore[arg-type]
            agency=None,  # type: ignore[arg-type]
            category=None,  # type: ignore[arg-type]
        )
        result = predict_distribution(request, policy)
        assert isinstance(result, Success)
        assert result.diagnostics.excluded_observations == 2

    def test_non_clean_provenance_is_excluded_and_counted(
        self, policy: InferencePolicy
    ) -> None:
        clean = [
            competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
            for i in range(8)
        ]
        non_clean = [
            competition_sample(
                provenance=common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_SUSPECT_RATIO
            )
            for _ in range(2)
        ]
        request = DistributionRequest(
            samples=_segmented(clean + non_clean),
            base_amount=None,  # type: ignore[arg-type]
            agency=None,  # type: ignore[arg-type]
            category=None,  # type: ignore[arg-type]
        )
        result = predict_distribution(request, policy)
        assert isinstance(result, Success)
        assert result.diagnostics.excluded_observations == 2
        # 비-CLEAN 두 표본이 관측 수(따라서 sample_size)에 들어가지 않는다(ML-04 ①).
        assert result.uncertainty.sample_size == 8


def test_resolve_diagnostics_matches_golden_ml_kernel_011_shape(
    policy: InferencePolicy,
) -> None:
    """golden 011 이 직접 쓰는 함수 — 계약 대조는 golden test 쪽에서, 여기서는 갈래
    (DIRECT/PARENT_CATEGORY/GLOBAL) 셋을 고정한다."""
    agency = LevelObservation(sample_count=5, mean=1.04, variance=0.0016)
    category = LevelObservation(sample_count=10, mean=1.02, variance=0.0016)

    direct = _resolve_diagnostics(
        agency=agency,
        category=category,
        posterior_shrinkage_weight=0.25,
        excluded_observations=0,
        policy=policy,
    )
    assert direct.segment_support is SegmentSupport.DIRECT
    assert direct.agency_sample_count == 5

    parent = _resolve_diagnostics(
        agency=None,
        category=category,
        posterior_shrinkage_weight=0.0,
        excluded_observations=0,
        policy=policy,
    )
    assert parent.segment_support is SegmentSupport.PARENT_CATEGORY
    assert parent.agency_sample_count == 0

    global_only = _resolve_diagnostics(
        agency=None,
        category=None,
        posterior_shrinkage_weight=0.0,
        excluded_observations=0,
        policy=policy,
    )
    assert global_only.segment_support is SegmentSupport.GLOBAL
