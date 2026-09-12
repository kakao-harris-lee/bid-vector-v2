"""RED — `ml_engine.inference.reserve_draw`(K6 이식, scope.md ①⑤, 설계 검토 구현 지시 3).
legacy `ValueError` 셋(빈 입력·`len < draw_count`·NaN/Inf/≤0 값·`draw_count < 1`)이
`Unmeasurable`로 바뀌는지, 닫힌식과 완전열거가 동치인지 확인한다.
"""

from __future__ import annotations

import math

import pytest
from hypothesis import given
from hypothesis import strategies as st

from ml_engine.inference.reserve_draw import (
    DrawMeanDistribution,
    draw_mean_moments,
    exact_draw_mean_distribution,
)
from ml_engine.inference.results import (
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)


def test_draw_count_below_one_is_unmeasurable() -> None:
    result = draw_mean_moments([1.0, 2.0, 3.0], draw_count=0)
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
    assert result.detail is UnmeasurableDetail.TOO_FEW_DRAWS


def test_empty_values_is_unmeasurable() -> None:
    result = draw_mean_moments([], draw_count=4)
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
    assert result.detail is UnmeasurableDetail.TOO_FEW_DRAWS


def test_fewer_values_than_draw_count_is_unmeasurable() -> None:
    result = draw_mean_moments([1.0, 2.0, 3.0], draw_count=4)
    assert isinstance(result, Unmeasurable)
    assert result.detail is UnmeasurableDetail.TOO_FEW_DRAWS


@pytest.mark.parametrize("bad_value", [math.nan, math.inf, -math.inf, 0.0, -1.0])
def test_non_finite_or_non_positive_value_is_unmeasurable(bad_value: float) -> None:
    values = [1.0] * 14 + [bad_value]
    result = draw_mean_moments(values, draw_count=4)
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.FEATURE_ABSENT
    assert result.detail is UnmeasurableDetail.NON_FINITE_INPUT


def test_n_equals_k_variance_is_zero() -> None:
    mean, std = draw_mean_moments([1.0, 2.0, 3.0, 4.0], draw_count=4)
    assert mean == pytest.approx(2.5)
    assert std == 0.0


@pytest.mark.legacy_parity
def test_moments_match_exact_enumeration_within_precision() -> None:
    """legacy `Var(x̄_k) = (sigma^2/k)*(n-k)/(n-1)` 닫힌식과 완전 열거가 부동소수 오차
    안에서 일치한다(회귀 관측 — 판정 근거 아님)."""
    values = [
        0.85,
        0.9,
        0.95,
        1.0,
        1.05,
        1.1,
        1.15,
        1.2,
        1.25,
        1.3,
        1.35,
        1.4,
        1.45,
        1.5,
        1.55,
    ]
    mean, std = draw_mean_moments(values, draw_count=4)
    exact = exact_draw_mean_distribution(values, draw_count=4)
    assert isinstance(exact, DrawMeanDistribution)
    assert mean == pytest.approx(exact.mean, rel=1e-9)
    assert std == pytest.approx(exact.std, rel=1e-9)


def test_exact_distribution_reuses_unmeasurable_for_bad_input() -> None:
    result = exact_draw_mean_distribution([], draw_count=4)
    assert isinstance(result, Unmeasurable)


@given(
    values=st.lists(
        st.floats(min_value=0.01, max_value=1e6, allow_nan=False, allow_infinity=False),
        min_size=15,
        max_size=15,
    )
)
def test_draw_mean_moments_mean_is_within_value_range(values: list[float]) -> None:
    mean, std = draw_mean_moments(values, draw_count=4)
    assert min(values) - 1e-6 <= mean <= max(values) + 1e-6
    assert std >= 0.0


def test_central_interval_and_quantile_are_clamped() -> None:
    exact = exact_draw_mean_distribution([1.0, 2.0, 3.0, 4.0, 5.0], draw_count=4)
    assert isinstance(exact, DrawMeanDistribution)
    low, high = exact.central_interval(1.5)  # coverage > 1 → clamp 1.0
    assert low == exact.quantile(0.0)
    assert high == exact.quantile(1.0)
