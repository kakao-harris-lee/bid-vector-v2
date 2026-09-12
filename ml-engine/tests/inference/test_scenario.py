"""RED — `ml_engine.inference.scenario`(scope.md ④⑥, 설계 검토 구현 지시 6). legacy 산식
`clamp(scale·(center + sign·z·std))` 재현, Decimal 경계(scale 보존), §6.5 분기.
"""

from __future__ import annotations

import math
from decimal import Decimal
from pathlib import Path

import pytest

from ml_engine.inference.policy import InferencePolicy, load_inference_policy
from ml_engine.inference.results import (
    Candidate,
    CandidateLabel,
    IntervalSource,
    Uncertainty,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)
from ml_engine.inference.scenario import build_scenario_candidates, resolve_uncertainty

_POLICY_PATH = Path(__file__).resolve().parents[2] / "policy" / "inference-v1.yaml"


@pytest.fixture(scope="module")
def policy() -> InferencePolicy:
    loaded = load_inference_policy(_POLICY_PATH)
    assert isinstance(loaded, InferencePolicy)
    return loaded


def test_candidates_have_fixed_order_and_labels(policy: InferencePolicy) -> None:
    candidates = build_scenario_candidates(center=1.0, std=0.05, policy=policy)
    assert isinstance(candidates, tuple)
    conservative, base, aggressive = candidates
    assert conservative.label is CandidateLabel.CONSERVATIVE
    assert base.label is CandidateLabel.BASE
    assert aggressive.label is CandidateLabel.AGGRESSIVE
    assert conservative.bid_rate < base.bid_rate < aggressive.bid_rate


def test_bid_rate_is_decimal_with_scale_preserved(policy: InferencePolicy) -> None:
    candidates = build_scenario_candidates(center=1.0, std=0.05, policy=policy)
    assert isinstance(candidates, tuple)
    for candidate in candidates:
        assert isinstance(candidate, Candidate)
        # scale 보존 — 4자리 quantize(정책 bid_rate_digits)라 "0.9500" 형태.
        assert (
            -candidate.bid_rate.as_tuple().exponent == policy.scenario_bid_rate_digits
        )


@pytest.mark.legacy_parity
def test_matches_legacy_scenario_bid_rates_formula(policy: InferencePolicy) -> None:
    """legacy `scenario_bid_rates` — `clamp(scale*(center + sign*z*std))`(회귀 관측)."""
    center, std, scale = 1.0, 0.03, 1.0
    z = float(policy.scenario_z)
    expected_base = round(scale * (center + (0 * z * std)), 4)
    expected_conservative = round(scale * (center + (-1 * z * std)), 4)
    expected_aggressive = round(scale * (center + (1 * z * std)), 4)
    candidates = build_scenario_candidates(
        center=center, std=std, policy=policy, scale=scale
    )
    assert isinstance(candidates, tuple)
    conservative, base, aggressive = candidates
    assert float(base.bid_rate) == pytest.approx(expected_base)
    assert float(conservative.bid_rate) == pytest.approx(expected_conservative)
    assert float(aggressive.bid_rate) == pytest.approx(expected_aggressive)


def test_clamp_band_applied(policy: InferencePolicy) -> None:
    """중심이 클램프 상한을 넘으면 세 후보 모두 상한에 눌린다."""
    candidates = build_scenario_candidates(center=10.0, std=0.01, policy=policy)
    assert isinstance(candidates, tuple)
    for candidate in candidates:
        assert candidate.bid_rate == policy.scenario_clamp_max


@pytest.mark.parametrize("bad_value", [math.nan, math.inf, -math.inf])
def test_non_finite_center_or_std_is_unmeasurable(
    policy: InferencePolicy, bad_value: float
) -> None:
    result = build_scenario_candidates(center=bad_value, std=0.05, policy=policy)
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.FEATURE_ABSENT
    assert result.detail is UnmeasurableDetail.NON_FINITE_INPUT


def test_zero_std_does_not_reject_but_produces_equal_candidates(
    policy: InferencePolicy,
) -> None:
    """설계 검토 (14) — `std == 0`은 거부하지 않고 후보 3 이 전부 같은 값으로 난다."""
    candidates = build_scenario_candidates(center=1.0, std=0.0, policy=policy)
    assert isinstance(candidates, tuple)
    conservative, base, aggressive = candidates
    assert conservative.bid_rate == base.bid_rate == aggressive.bid_rate


def test_weight_is_decimal_without_quantize(policy: InferencePolicy) -> None:
    candidates = build_scenario_candidates(center=1.0, std=0.05, policy=policy)
    assert isinstance(candidates, tuple)
    _, base, _ = candidates
    assert base.weight == Decimal("0.52")
    assert base.weight_policy_version == "inference-v1"


def test_resolve_uncertainty_sample_size_below_minimum_is_unmeasurable(
    policy: InferencePolicy,
) -> None:
    """§6.5 — 표본이 `assessment.min_samples_for_variance` 미만이면 margin 을 지어내지
    않는다."""
    result = resolve_uncertainty(
        sample_size=1,
        dispersion=0.02,
        estimate_margin=0.03,
        interval_source=IntervalSource.CROSS_VALIDATION_RESIDUAL,
        policy=policy,
    )
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
    assert result.detail is UnmeasurableDetail.DEGENERATE_VARIANCE


def test_resolve_uncertainty_success(policy: InferencePolicy) -> None:
    result = resolve_uncertainty(
        sample_size=10,
        dispersion=0.02,
        estimate_margin=0.03,
        interval_source=IntervalSource.CROSS_VALIDATION_RESIDUAL,
        policy=policy,
    )
    assert isinstance(result, Uncertainty)
    assert result.sample_size == 10
    assert result.dispersion == Decimal("0.02")
    assert result.estimate_margin == Decimal("0.03")
