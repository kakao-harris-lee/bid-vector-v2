"""RED — `ml_engine.inference.availability`(scope.md ③, D-5D2-7). 가용성 게이트와
조립기가 **같은 함수**를 쓰는지(legacy 「availability 에서만 검사, 직접 호출은 표본 1건도
통과」 결함 제거) 확인한다."""

from __future__ import annotations

import pytest
from _policy_support import shipped_inference_policy_for_test

from ml_engine.inference.availability import distribution_availability
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.predict import Available
from ml_engine.inference.results import (
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)


@pytest.fixture(scope="module")
def policy() -> InferencePolicy:
    return shipped_inference_policy_for_test()


def test_below_min_reserve_records_is_too_few_observations(
    policy: InferencePolicy,
) -> None:
    result = distribution_availability(
        observation_count=policy.reserve_min_reserve_records - 1,
        ratio_sample_count=policy.bid_ratio_min_samples,
        policy=policy,
    )
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
    assert result.detail is UnmeasurableDetail.TOO_FEW_OBSERVATIONS


def test_below_min_ratio_samples_is_too_few_ratio_samples(
    policy: InferencePolicy,
) -> None:
    result = distribution_availability(
        observation_count=policy.reserve_min_reserve_records,
        ratio_sample_count=policy.bid_ratio_min_samples - 1,
        policy=policy,
    )
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
    assert result.detail is UnmeasurableDetail.TOO_FEW_RATIO_SAMPLES


def test_observation_gate_checked_before_ratio_gate(policy: InferencePolicy) -> None:
    """둘 다 미달이면 관측 행 수 미달 사유가 먼저 나온다(legacy 관문 순서와 같음)."""
    result = distribution_availability(
        observation_count=0, ratio_sample_count=0, policy=policy
    )
    assert isinstance(result, Unmeasurable)
    assert result.detail is UnmeasurableDetail.TOO_FEW_OBSERVATIONS


def test_at_exact_thresholds_is_available(policy: InferencePolicy) -> None:
    """임계는 `>=`다(001~003 golden 과 같은 경계 규칙)."""
    result = distribution_availability(
        observation_count=policy.reserve_min_reserve_records,
        ratio_sample_count=policy.bid_ratio_min_samples,
        policy=policy,
    )
    assert isinstance(result, Available)


def test_direct_call_with_one_sample_is_rejected_same_as_gate(
    policy: InferencePolicy,
) -> None:
    """D-5D2-7 핵심 — legacy 결함(게이트 우회 시 표본 1건도 통과) 반례. 이 함수를 어디서
    부르든(가용성 확인·조립기 첫 줄) 표본 1건은 항상 거부된다."""
    result = distribution_availability(
        observation_count=1, ratio_sample_count=1, policy=policy
    )
    assert isinstance(result, Unmeasurable)
