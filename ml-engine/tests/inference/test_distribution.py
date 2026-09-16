"""RED — `ml_engine.inference.distribution`(scope.md ②, 설계 검토 구현 지시 5). legacy
`_estimate_distribution` 조립의 재현: 가용성 게이트 공유(D-5D2-7)·CLEAN 필터(ML-04 ①)·
투찰율 축 환산(D-5D2-6)·진단.

M5/5D-3(`OPEN-5D2-SAMPLE-SEGMENT` 해소) — 표본 축(agency/category)과 요청 축의 정규화
문자열 동일 매칭으로 3계층(D-5D3-1~5) + 우회 후보 (7)(8)(9)(10) 회귀.

Reuse: bid-vector/app/ai/predictors/distribution.py@ed4b06c
"""

from __future__ import annotations

from decimal import Decimal
from statistics import fmean, pstdev

import pytest
from _policy_support import shipped_inference_policy_for_test
from _sample_support import VALID_RATIOS, competition_sample

from ml_engine.contracts import common_pb2, features_pb2, prediction_pb2
from ml_engine.features import FactValue, Missing, Present
from ml_engine.inference.assessment import LevelObservation
from ml_engine.inference.distribution import (
    DistributionRequest,
    SampleSegment,
    SegmentedSample,
    _resolve_diagnostics,
    predict_distribution,
)
from ml_engine.inference.observations import SampleRejected, SampleRejectionReason
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
_NOT_COLLECTED_YET = Missing(common_pb2.MISSING_REASON_NOT_COLLECTED_YET)
_MISSING_SEGMENT = SampleSegment(agency=_NOT_COLLECTED_YET, category=_NOT_COLLECTED_YET)


@pytest.fixture(scope="module")
def policy() -> InferencePolicy:
    return shipped_inference_policy_for_test()


def _segmented(
    samples: list[features_pb2.CompetitionSample],
    segments: list[SampleSegment | SampleRejected] | None = None,
) -> tuple[SegmentedSample, ...]:
    """`OPEN-5D2-SAMPLE-SEGMENT` 해소(D-5D3-6) — 기본은 표본 축 전부 `Missing(
    NOT_COLLECTED_YET)`(D-5D3-5 회귀와 같은 상태, `_sample_support.competition_sample`
    의 기본값과 정합). `segments`를 주면 표본과 순서대로 짝지어 매칭 규칙을 직접
    검증한다(from_proto 판독을 거치지 않는 단위 test 용)."""
    resolved = segments if segments is not None else [_MISSING_SEGMENT] * len(samples)
    return tuple(
        SegmentedSample(sample=sample, segment=segment)
        for sample, segment in zip(samples, resolved, strict=True)
    )


def _valid_request(
    *,
    count: int = 8,
    bid_rates: tuple[str, ...] = _BID_RATES,
    provenance: int = common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN,
    segments: list[SampleSegment | SampleRejected] | None = None,
    agency: FactValue[str] = _NOT_COLLECTED_YET,
    category: FactValue[str] = _NOT_COLLECTED_YET,
) -> DistributionRequest:
    samples = [
        competition_sample(
            observed_bid_rate=bid_rates[i % len(bid_rates)], provenance=provenance
        )
        for i in range(count)
    ]
    return DistributionRequest(
        samples=_segmented(samples, segments),
        base_amount=_NOT_COLLECTED_YET,
        agency=agency,
        category=category,
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

    def test_default_sample_segment_is_missing_not_collected_yet(self) -> None:
        """`_sample_support.competition_sample`의 기본값(D-5D3-5) — 명시적으로
        기관·공종을 채우지 않은 표본은 두 축 모두 `Missing(NOT_COLLECTED_YET)`다."""
        request = prediction_pb2.CalculateOptimalBidRequest(
            features=_valid_features_inputs(),
            competition_samples=[competition_sample() for _ in range(3)],
        )
        result = DistributionRequest.from_proto(request)
        assert isinstance(result, DistributionRequest)
        for segmented in result.samples:
            assert isinstance(segmented.segment, SampleSegment)
            assert segmented.segment == _MISSING_SEGMENT

    def test_sample_with_agency_and_category_values_resolves_present(self) -> None:
        sample = competition_sample(
            agency_id="Agency-Opaque-771", category_code="CAT-0821"
        )
        request = prediction_pb2.CalculateOptimalBidRequest(
            features=_valid_features_inputs(), competition_samples=[sample]
        )
        result = DistributionRequest.from_proto(request)
        assert isinstance(result, DistributionRequest)
        segment = result.samples[0].segment
        assert isinstance(segment, SampleSegment)
        assert segment.agency == Present("agency-opaque-771")
        assert segment.category == Present("cat-0821")

    def test_sample_with_disallowed_missing_reason_is_rejected(self) -> None:
        """D-5D3-2 — 표본 축이 허용하는 유일한 결측 사유는 `NOT_COLLECTED_YET`이다."""
        sample = competition_sample(agency_id=common_pb2.MISSING_REASON_UNKNOWN)
        request = prediction_pb2.CalculateOptimalBidRequest(
            features=_valid_features_inputs(), competition_samples=[sample]
        )
        result = DistributionRequest.from_proto(request)
        assert isinstance(result, DistributionRequest)
        assert result.samples[0].segment == SampleRejected(
            SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED
        )

    def test_sample_with_unset_agency_oneof_is_rejected(self) -> None:
        sample = competition_sample(agency_id=None)
        request = prediction_pb2.CalculateOptimalBidRequest(
            features=_valid_features_inputs(), competition_samples=[sample]
        )
        result = DistributionRequest.from_proto(request)
        assert isinstance(result, DistributionRequest)
        assert result.samples[0].segment == SampleRejected(
            SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED
        )

    def test_sample_with_blank_category_value_is_rejected(self) -> None:
        """정규화 뒤 빈 키(우회 후보 (8)) — 요청 축과 「빈 = 빈」 일치를 만들지 않는다."""
        sample = competition_sample(category_code="   ")
        request = prediction_pb2.CalculateOptimalBidRequest(
            features=_valid_features_inputs(), competition_samples=[sample]
        )
        result = DistributionRequest.from_proto(request)
        assert isinstance(result, DistributionRequest)
        assert result.samples[0].segment == SampleRejected(
            SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED
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
            base_amount=_NOT_COLLECTED_YET,
            agency=_NOT_COLLECTED_YET,
            category=_NOT_COLLECTED_YET,
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
            base_amount=_NOT_COLLECTED_YET,
            agency=_NOT_COLLECTED_YET,
            category=_NOT_COLLECTED_YET,
        )
        result = predict_distribution(request, policy)
        assert isinstance(result, Success)
        assert result.diagnostics.excluded_observations == 2
        # 비-CLEAN 두 표본이 관측 수(따라서 sample_size)에 들어가지 않는다(ML-04 ①).
        assert result.uncertainty.sample_size == 8

    def test_segment_axis_rejection_is_counted_even_if_reserve_draw_is_valid(
        self, policy: InferencePolicy
    ) -> None:
        """우회 후보 (2)(11) — 세그먼트 게이트 거부가 관측 게이트 성공과 독립으로
        `excluded_observations`에 들어간다(이중 계수 없이 한 표본당 한 번)."""
        valid = [
            competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
            for i in range(8)
        ]
        segments: list[SampleSegment | SampleRejected] = [_MISSING_SEGMENT] * 8
        segments[0] = SampleRejected(SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED)
        segments[1] = SampleRejected(SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED)
        request = DistributionRequest(
            samples=_segmented(valid, segments),
            base_amount=_NOT_COLLECTED_YET,
            agency=_NOT_COLLECTED_YET,
            category=_NOT_COLLECTED_YET,
        )
        result = predict_distribution(request, policy)
        assert isinstance(result, Unmeasurable)
        assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES

    def test_segment_axis_rejection_count_is_exact_not_double_counted(
        self, policy: InferencePolicy
    ) -> None:
        """verifier r1 F-6 — 위 test는 표본 부족으로 `Unmeasurable`이 나와 `Diagnostics`
        자체가 없다(계수를 실제로 재지 않는다). 이 test는 `Success`가 나오는 표본 수에서
        세그먼트 거부 1건이 `excluded_observations`에 정확히 1로 실리는지 잰다 — 관측
        게이트와 세그먼트 게이트가 같은 표본을 두 번 세면(회귀) 이 단언이 깨진다."""
        valid = [
            competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
            for i in range(9)
        ]
        segments: list[SampleSegment | SampleRejected] = [_MISSING_SEGMENT] * 9
        segments[0] = SampleRejected(SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED)
        request = DistributionRequest(
            samples=_segmented(valid, segments),
            base_amount=_NOT_COLLECTED_YET,
            agency=_NOT_COLLECTED_YET,
            category=_NOT_COLLECTED_YET,
        )
        result = predict_distribution(request, policy)
        assert isinstance(result, Success)
        assert result.diagnostics.excluded_observations == 1
        assert result.uncertainty.sample_size == 8

    def test_segment_axis_enum_outside_missing_reason_rejects_only_that_sample(
        self, policy: InferencePolicy
    ) -> None:
        """F-10 — 표본 축은 요청 축과 달리 여전히 닫힌 집합이다: enum 밖 정수(`99`)를
        결측 사유로 실은 표본은 `SEGMENT_REASON_NOT_ALLOWED`로 거부되지만, 그 표본
        하나만 빠지고 요청은 `Success`로 남는다(wire 경유, `DistributionRequest.
        from_proto` 실제 판독 확인)."""
        samples = [
            competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
            for i in range(9)
        ]
        samples[0] = competition_sample(observed_bid_rate=_BID_RATES[0], agency_id=99)
        request = prediction_pb2.CalculateOptimalBidRequest(
            features=_valid_features_inputs(), competition_samples=samples
        )
        distribution_request = DistributionRequest.from_proto(request)
        assert isinstance(distribution_request, DistributionRequest)
        assert distribution_request.samples[0].segment == SampleRejected(
            SampleRejectionReason.SEGMENT_REASON_NOT_ALLOWED
        )

        result = predict_distribution(distribution_request, policy)
        assert isinstance(result, Success)
        assert result.diagnostics.excluded_observations == 1

    def test_agency_and_category_match_builds_direct_segment_support(
        self, policy: InferencePolicy
    ) -> None:
        """D-5D3-1·D-5D3-4 — 요청 축과 정규화 문자열이 같은 표본만 계층에 든다."""
        samples = [
            competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
            for i in range(8)
        ]
        segments: list[SampleSegment | SampleRejected] = [
            SampleSegment(agency=Present("agency-x"), category=Present("cat-y"))
            for _ in range(5)
        ] + [_MISSING_SEGMENT] * 3
        request = DistributionRequest(
            samples=_segmented(samples, segments),
            base_amount=_NOT_COLLECTED_YET,
            agency=Present("agency-x"),
            category=Present("cat-y"),
        )
        result = predict_distribution(request, policy)
        assert isinstance(result, Success)
        assert result.diagnostics.segment_support is SegmentSupport.DIRECT
        assert result.diagnostics.agency_sample_count == 5

    def test_category_match_without_agency_match_is_parent_category(
        self, policy: InferencePolicy
    ) -> None:
        """D-5D3-4 — category 집합은 agency 불일치 표본도 포함한다(우회 후보 (7))."""
        samples = [
            competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
            for i in range(8)
        ]
        segments: list[SampleSegment | SampleRejected] = [
            SampleSegment(agency=Present("agency-other"), category=Present("cat-y"))
            for _ in range(4)
        ] + [_MISSING_SEGMENT] * 4
        request = DistributionRequest(
            samples=_segmented(samples, segments),
            base_amount=_NOT_COLLECTED_YET,
            agency=Present("agency-x"),
            category=Present("cat-y"),
        )
        result = predict_distribution(request, policy)
        assert isinstance(result, Success)
        assert result.diagnostics.segment_support is SegmentSupport.PARENT_CATEGORY
        assert result.diagnostics.agency_sample_count == 0

    def test_missing_request_agency_never_matches_even_if_samples_share_a_value(
        self, policy: InferencePolicy
    ) -> None:
        """D-5D3-3, 우회 후보 (9) — 요청 축이 `Missing`이면 표본이 전부 같은 값이라도
        「그 기관이겠지」로 추론하지 않는다."""
        samples = [
            competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
            for i in range(8)
        ]
        segments: list[SampleSegment | SampleRejected] = [
            SampleSegment(agency=Present("agency-x"), category=Present("cat-y"))
            for _ in range(8)
        ]
        request = DistributionRequest(
            samples=_segmented(samples, segments),
            base_amount=_NOT_COLLECTED_YET,
            agency=_NOT_COLLECTED_YET,
            category=_NOT_COLLECTED_YET,
        )
        result = predict_distribution(request, policy)
        assert isinstance(result, Success)
        assert result.diagnostics.segment_support is SegmentSupport.GLOBAL
        assert result.diagnostics.agency_sample_count == 0

    def test_non_clean_matching_samples_do_not_enter_the_agency_set(
        self, policy: InferencePolicy
    ) -> None:
        """우회 후보 (10) — 계층 집합은 CLEAN 이후에만 구성한다(D-5D3-4)."""
        clean = [
            competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
            for i in range(8)
        ]
        matching_non_clean = [
            competition_sample(
                observed_bid_rate=_BID_RATES[0],
                provenance=common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_SUSPECT_RATIO,
            )
            for _ in range(3)
        ]
        segments: list[SampleSegment | SampleRejected] = [_MISSING_SEGMENT] * 8 + [
            SampleSegment(agency=Present("agency-x"), category=Present("cat-y"))
            for _ in range(3)
        ]
        request = DistributionRequest(
            samples=_segmented(clean + matching_non_clean, segments),
            base_amount=_NOT_COLLECTED_YET,
            agency=Present("agency-x"),
            category=Present("cat-y"),
        )
        result = predict_distribution(request, policy)
        assert isinstance(result, Success)
        assert result.diagnostics.segment_support is SegmentSupport.GLOBAL
        assert result.diagnostics.agency_sample_count == 0
        assert result.diagnostics.excluded_observations == 3


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
