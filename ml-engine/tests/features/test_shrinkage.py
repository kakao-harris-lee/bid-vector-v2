"""RED — `ml_engine.features.shrinkage`(D-5B-6, legacy `assessment_shrinkage.py` 이식).

`pseudo_count_weight`·`shrink_toward` 원시 연산 둘만. legacy 산식 대조는 `legacy-behavior`
라벨(재현 확인용이지 정답 판정 근거가 아니다 — CLAUDE.md 운영자 지시).
"""

from __future__ import annotations

from hypothesis import given
from hypothesis import strategies as st

from ml_engine.features.shrinkage import pseudo_count_weight, shrink_toward


def test_pseudo_count_weight_zero_samples_is_zero() -> None:
    assert pseudo_count_weight(0, 12.0) == 0.0


def test_pseudo_count_weight_legacy_behavior_n_equals_kappa_is_half() -> None:
    """legacy-behavior — n == κ 이면 자기 가중치는 정확히 0.5(재현 확인용, 정답 판정 아님)."""
    assert pseudo_count_weight(12, 12.0) == 0.5


@given(
    sample_count=st.integers(min_value=0, max_value=100_000),
    prior_strength=st.floats(min_value=0.001, max_value=1000.0, allow_nan=False),
)
def test_pseudo_count_weight_bounded_and_monotonic_in_n(
    sample_count: int, prior_strength: float
) -> None:
    weight = pseudo_count_weight(sample_count, prior_strength)
    assert 0.0 <= weight < 1.0
    # 표본이 늘면 자기 가중치도 늘거나 같다(단조).
    assert pseudo_count_weight(sample_count + 1, prior_strength) >= weight


@given(
    observed_mean=st.floats(min_value=-1e6, max_value=1e6, allow_nan=False),
    sample_count=st.integers(min_value=0, max_value=100_000),
    prior_mean=st.floats(min_value=-1e6, max_value=1e6, allow_nan=False),
    prior_strength=st.floats(min_value=0.001, max_value=1000.0, allow_nan=False),
)
def test_shrink_toward_returns_convex_combination(
    observed_mean: float, sample_count: int, prior_mean: float, prior_strength: float
) -> None:
    value, weight = shrink_toward(
        observed_mean,
        sample_count,
        prior_mean=prior_mean,
        prior_strength=prior_strength,
    )
    assert weight == pseudo_count_weight(sample_count, prior_strength)
    low, high = sorted((observed_mean, prior_mean))
    assert low - 1e-9 <= value <= high + 1e-9


def test_shrink_toward_zero_samples_returns_prior_mean_exactly() -> None:
    value, weight = shrink_toward(999.0, 0, prior_mean=0.42, prior_strength=12.0)
    assert value == 0.42
    assert weight == 0.0
