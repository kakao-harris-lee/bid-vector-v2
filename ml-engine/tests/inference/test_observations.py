"""RED — `ml_engine.inference.observations`(scope.md ①, 설계 검토 구현 지시 3). wire
`CompetitionSample` → `ReserveDrawSample | SampleRejected` 관문 규칙표: 7 사유·정상·
`Missing` 실현 사정률.

Reuse: bid-vector/app/ai/predictors/distribution_extraction.py@ed4b06c
(`observe_reserve_draw`·`realized_assessment_ratio` — 관문·흐름의 산술 출처).
"""

from __future__ import annotations

import statistics
from decimal import Decimal

import pytest
from _policy_support import shipped_inference_policy_for_test
from _sample_support import VALID_RATIOS as _VALID_RATIOS
from _sample_support import competition_sample as _sample

from ml_engine.contracts import common_pb2
from ml_engine.inference.assessment import AssessmentProvenance
from ml_engine.inference.observations import (
    ReserveDrawSample,
    SampleRejected,
    SampleRejectionReason,
    observe_sample,
)
from ml_engine.inference.policy import InferencePolicy

# center 는 밴드 안(0.9933)이지만 두 outlier(0.3)를 고르면 실현 사정률만 밴드 밖.
_OUTLIER_RATIOS = (0.3, 0.3) + (1.10,) * 13


@pytest.fixture(scope="module")
def policy() -> InferencePolicy:
    return shipped_inference_policy_for_test()


def _expected_center_and_std(
    ratios: tuple[float, ...], draw_count: int
) -> tuple[float, float]:
    n = len(ratios)
    mean = statistics.fmean(ratios)
    variance = statistics.pvariance(ratios)
    draw_variance = (variance / draw_count) * ((n - draw_count) / (n - 1))
    return mean, draw_variance**0.5


def test_valid_sample_is_admitted(policy: InferencePolicy) -> None:
    sample = _sample(selected_numbers=(1, 2))
    result = observe_sample(sample, policy)
    assert isinstance(result, ReserveDrawSample)
    expected_center, expected_std = _expected_center_and_std(
        _VALID_RATIOS, policy.reserve_draw_count
    )
    assert result.center == pytest.approx(expected_center)
    assert result.draw_std == pytest.approx(expected_std)
    assert result.observed_bid_rate == pytest.approx(0.95)
    assert result.provenance is AssessmentProvenance.CLEAN
    # selected_numbers (1, 2) → ratios[0], ratios[1] = 0.85, 0.86.
    assert result.realized_assessment_ratio == pytest.approx(
        statistics.fmean([_VALID_RATIOS[0], _VALID_RATIOS[1]])
    )


def test_base_amount_non_positive_is_rejected(policy: InferencePolicy) -> None:
    sample = _sample(base_amount_won=0)
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.BASE_AMOUNT_INVALID)


def test_base_amount_wrong_basis_is_rejected(policy: InferencePolicy) -> None:
    sample = _sample(base_amount_overrides={"basis": common_pb2.BASIS_ESTIMATED})
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.BASE_AMOUNT_INVALID)


def test_base_amount_wrong_currency_is_rejected(policy: InferencePolicy) -> None:
    sample = _sample(
        base_amount_overrides={"currency": common_pb2.CURRENCY_UNSPECIFIED}
    )
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.BASE_AMOUNT_INVALID)


def test_base_amount_unspecified_provenance_is_rejected(
    policy: InferencePolicy,
) -> None:
    sample = _sample(
        base_amount_overrides={
            "provenance": common_pb2.AMOUNT_PROVENANCE_KIND_UNSPECIFIED
        }
    )
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.BASE_AMOUNT_INVALID)


def test_no_reserve_draw_is_rejected(policy: InferencePolicy) -> None:
    sample = _sample(with_reserve_draw=False)
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.NO_RESERVE_DRAW)


def test_price_count_mismatch_is_rejected(policy: InferencePolicy) -> None:
    sample = _sample(reserve_price_ratios=_VALID_RATIOS[:10])
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.PRICE_COUNT_MISMATCH)


def test_non_positive_price_is_rejected(policy: InferencePolicy) -> None:
    sample = _sample(reserve_price_overrides={0: 0})
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.NON_POSITIVE_PRICE)


def test_degenerate_variance_is_reserve_draw_unmeasurable(
    policy: InferencePolicy,
) -> None:
    """K6(`draw_mean_moments`)가 표본 전부 동일값(모분산 0, n != draw_count)을 만나면
    `Unmeasurable`을 낸다 — 이 관문은 그것을 조용히 삼키지 않고 사유로 나른다."""
    sample = _sample(reserve_price_ratios=(1.0,) * 15)
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.RESERVE_DRAW_UNMEASURABLE)


def test_center_out_of_band_is_rejected(policy: InferencePolicy) -> None:
    sample = _sample(reserve_price_ratios=tuple(2.0 + i * 0.01 for i in range(15)))
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.CENTER_OUT_OF_BAND)


def test_bid_rate_out_of_band_is_rejected(policy: InferencePolicy) -> None:
    sample = _sample(observed_bid_rate="3.0")
    result = observe_sample(sample, policy)
    assert result == SampleRejected(SampleRejectionReason.BID_RATE_OUT_OF_BAND)


def test_too_few_selected_numbers_is_missing_not_rejected(
    policy: InferencePolicy,
) -> None:
    sample = _sample(selected_numbers=(1,))
    result = observe_sample(sample, policy)
    assert isinstance(result, ReserveDrawSample)
    assert result.realized_assessment_ratio is None


def test_no_selected_numbers_is_missing_not_rejected(policy: InferencePolicy) -> None:
    sample = _sample(selected_numbers=())
    result = observe_sample(sample, policy)
    assert isinstance(result, ReserveDrawSample)
    assert result.realized_assessment_ratio is None


def test_realized_ratio_out_of_band_is_missing_not_rejected(
    policy: InferencePolicy,
) -> None:
    """center 는 밴드 안이지만(0.9933), 고른 두 표본(0.3, 0.3)의 평균은 밴드 밖 — 행
    자체는 거부되지 않고 `realized_assessment_ratio`만 `None`이다(legacy `observe_
    reserve_draw` — 같은 술어를 실현값에도 적용, 둘 다 None 접힘)."""
    sample = _sample(reserve_price_ratios=_OUTLIER_RATIOS, selected_numbers=(1, 2))
    result = observe_sample(sample, policy)
    assert isinstance(result, ReserveDrawSample)
    assert result.realized_assessment_ratio is None


def test_out_of_range_selected_number_is_ignored_not_rejected(
    policy: InferencePolicy,
) -> None:
    """`selected_numbers`의 범위 밖(0 또는 len+1 이상) 값은 legacy 처럼 조용히
    걸러진다(1-기반 관례 위반이지 관측 자체의 결함이 아니다) — 남은 유효 개수만
    본다."""
    sample = _sample(selected_numbers=(0, 1, 2, 999))
    result = observe_sample(sample, policy)
    assert isinstance(result, ReserveDrawSample)
    assert result.realized_assessment_ratio == pytest.approx(
        statistics.fmean([_VALID_RATIOS[0], _VALID_RATIOS[1]])
    )


def test_provenance_labels_mirror_assessment_provenance(
    policy: InferencePolicy,
) -> None:
    mapping = {
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN: AssessmentProvenance.CLEAN,
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_DERIVED_YEGA: (
            AssessmentProvenance.DERIVED_YEGA
        ),
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_DERIVED_VAT: (
            AssessmentProvenance.DERIVED_VAT
        ),
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_SUSPECT_RATIO: (
            AssessmentProvenance.SUSPECT_RATIO
        ),
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_UNKNOWN: AssessmentProvenance.UNKNOWN,
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_UNSPECIFIED: (
            AssessmentProvenance.UNKNOWN
        ),
    }
    for label, expected in mapping.items():
        sample = _sample(provenance=label)
        result = observe_sample(sample, policy)
        assert isinstance(result, ReserveDrawSample), label
        assert result.provenance is expected, label


def test_observed_bid_rate_is_read_via_decimal_not_bare_float(
    policy: InferencePolicy,
) -> None:
    """D-5D-1 대칭 — `Rate.fraction` 은 `Decimal` 로 읽은 뒤 커널 직전 float 한 번
    (설계 검토 (10))."""
    sample = _sample(observed_bid_rate="0.9500")
    result = observe_sample(sample, policy)
    assert isinstance(result, ReserveDrawSample)
    assert result.observed_bid_rate == float(Decimal("0.9500"))


def test_rejection_reasons_are_seven() -> None:
    """설계 검토 (5) 구현 지시 3 — 「7 사유」."""
    assert len(list(SampleRejectionReason)) == 7
