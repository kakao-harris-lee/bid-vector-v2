"""RED — `ml_engine.inference.scenario`(scope.md ④⑥, 설계 검토 구현 지시 6). legacy 산식
`clamp(scale·(center + sign·z·std))` 재현, Decimal 경계(scale 보존), §6.5 분기.
"""

from __future__ import annotations

import math
from decimal import ROUND_HALF_EVEN, ROUND_HALF_UP, Decimal

import pytest
from _policy_support import shipped_inference_policy_for_test

from ml_engine.inference.policy import InferencePolicy
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


@pytest.fixture(scope="module")
def policy() -> InferencePolicy:
    return shipped_inference_policy_for_test()


def test_candidates_have_fixed_order_and_labels(policy: InferencePolicy) -> None:
    """M5/5F-1 계약 갱신 (2) — `center=0.9`(이전엔 `1.0`). `scenario.clamp_max` 가
    `1.0`으로 내려간 뒤(D-5F1-1) `center=1.0`은 `aggressive`를 상한에 접어 이
    test 의 엄격 부등식 전제(순서만 확인, clamp 자체는 아래 `test_clamp_band_
    applied`·`test_center_at_clamp_max_folds_base_and_aggressive_but_keeps_
    three_candidates`가 담당)와 우연히 충돌했다 — clamp 를 안 건드리는 `0.9`로
    옮겨 원래 의도(라벨 순서·엄격 순서)를 그대로 검증한다."""
    candidates = build_scenario_candidates(center=0.9, std=0.05, policy=policy)
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
    """legacy `scenario_bid_rates` — `clamp(scale*(center + sign*z*std))`(회귀 관측,
    판정 근거 아님 — S-8 관측 전용, verifier r1 L-4). `center=0.9`(M5/5F-1 계약
    갱신 (2), 이전엔 `1.0` — clamp_max 1.0 하에서 `aggressive` 기대값(clamp 미고려
    raw 산식)이 실제 clamp 된 값과 어긋났다). 이 test 의 관심은 clamp 미적용
    구간에서의 산식 일치이므로 clamp 상한을 안 건드리는 값으로 옮긴다."""
    center, std, scale = 0.9, 0.03, 1.0
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


def test_center_at_clamp_max_folds_base_and_aggressive_but_keeps_three_candidates(
    policy: InferencePolicy,
) -> None:
    """D-5F1-5(M5/5F-1 계약 갱신 (2)) — `scenario.clamp_max` 를 `1.0`으로 내린 뒤
    `center >= clamp_max`인 입력(`center=1.0, std=0.05`)에서 `base`(sign 0)와
    `aggressive`(sign +1, std>0)가 똑같이 상한으로 접혀 같은 값이 된다(`conservative`
    는 sign -1 이라 상한 밑에 남는다). 이것은 엔진 결함이 아니다 — `build_scenario_
    candidates`(src, 무편집)는 후보 간 엄격한 순서를 강제하지 않는다(clamp 는 후보
    셋을 독립적으로 자른다, `test_zero_std_does_not_reject_but_produces_equal_
    candidates`가 이미 셋 다 같은 값이 되는 경로를 허용해 뒀다). 계약도 엄격 순서를
    요구하지 않는다 — Kotlin `CandidateShapeValidation.kt::hasOrderedCandidateRates`
    가 `rates[0] <= rates[1] && rates[1] <= rates[2]`(비엄격 `<=`)를 쓴다. 예외 없이
    후보 3 을 그대로 낸다(값 지어내기·조용한 실패 없음)."""
    candidates = build_scenario_candidates(center=1.0, std=0.05, policy=policy)
    assert isinstance(candidates, tuple)
    assert len(candidates) == 3
    conservative, base, aggressive = candidates
    assert base.bid_rate == policy.scenario_clamp_max
    assert aggressive.bid_rate == policy.scenario_clamp_max
    assert base.bid_rate == aggressive.bid_rate
    assert conservative.bid_rate <= base.bid_rate


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


def test_quantize_rounds_half_up_not_half_even(policy: InferencePolicy) -> None:
    """D-5D-10(verifier r1 L-1) — `center=0.87465`(BASE 후보, sign=0 이라 std 무관)는
    `Decimal("0.87465")`(경계 변환 `Decimal(str(x))`가 만드는 정확한 십진 표현)에서
    정확히 다섯째 자리 5 인 동점이다. `ROUND_HALF_UP`은 `0.8747`, 이전 판
    `ROUND_HALF_EVEN`(banker's rounding)은 `0.8746`을 냈다 — D-5D-10 이 전자로 고정한다.
    **의도된 갈림**: legacy `round(rate, 4)`(Python 내장, 이진 float 값 기준)는 이
    특정 값에서 별개 이유(0.87465 의 실제 이진 표현이 근소하게 0.87465 보다 크다)로
    `0.8747`을 내지만, 그 일치가 「같은 규칙이다」를 뜻하지 않는다 — legacy 출력은
    정답이 아니다(CLAUDE.md 운영자 지시), 이 test 가 고정하는 것은 `ROUND_HALF_UP`
    규칙 자체다."""
    quantum = Decimal("0.0001")
    tie = Decimal("0.87465")
    assert tie.quantize(quantum, rounding=ROUND_HALF_UP) == Decimal("0.8747")
    assert tie.quantize(quantum, rounding=ROUND_HALF_EVEN) == Decimal("0.8746")

    candidates = build_scenario_candidates(center=0.87465, std=0.0, policy=policy)
    assert isinstance(candidates, tuple)
    _, base, _ = candidates
    assert base.bid_rate == Decimal("0.8747")


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
