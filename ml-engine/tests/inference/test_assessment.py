"""RED — `ml_engine.inference.assessment`(K5 이식 + provenance 게이트, scope.md ①⑤,
D-5D-5, 설계 검토 구현 지시 4). `admit_clean`이 유일한 `CleanAssessmentSample` 생성
지점인지, `resolve_assessment_posterior`가 legacy 3계층 수축과 같은 값을 내는지 확인.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from ml_engine.inference.assessment import (
    AssessmentProvenance,
    AssessmentSample,
    CleanAssessmentSample,
    LevelObservation,
    admit_clean,
    aggregate_level_observation,
    resolve_assessment_posterior,
)
from ml_engine.inference.policy import InferencePolicy, load_inference_policy
from ml_engine.inference.results import (
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)

_POLICY_PATH = Path(__file__).resolve().parents[2] / "policy" / "inference-v1.yaml"


@pytest.fixture(scope="module")
def policy() -> InferencePolicy:
    loaded = load_inference_policy(_POLICY_PATH)
    assert isinstance(loaded, InferencePolicy)
    return loaded


def test_admit_clean_only_passes_clean_provenance() -> None:
    samples = [
        AssessmentSample(1.0, AssessmentProvenance.CLEAN),
        AssessmentSample(2.0, AssessmentProvenance.DERIVED_YEGA),
        AssessmentSample(3.0, AssessmentProvenance.CLEAN),
        AssessmentSample(4.0, AssessmentProvenance.SUSPECT_RATIO),
    ]
    clean, excluded = admit_clean(samples)
    assert clean == (CleanAssessmentSample(1.0), CleanAssessmentSample(3.0))
    assert excluded == 2


def test_admit_clean_excluded_count_is_not_silently_dropped() -> None:
    """조용한 drop 금지 — 제외 건수가 반환값에 명시적으로 실린다."""
    samples = [AssessmentSample(1.0, AssessmentProvenance.UNKNOWN)]
    clean, excluded = admit_clean(samples)
    assert clean == ()
    assert excluded == 1


def test_aggregate_level_observation_only_accepts_clean_samples() -> None:
    """provenance 게이트가 값이 정수라서 통과하는 경로를 막는다(D-5D-5, ML-04 축어) —
    `aggregate_level_observation`은 `CleanAssessmentSample`만 받는다(타입 검사로 대신
    확인 — mypy strict 가 이 시그니처를 강제한다)."""
    clean_samples, _ = admit_clean(
        [AssessmentSample(1.0, AssessmentProvenance.CLEAN)] * 5
    )
    observation = aggregate_level_observation(clean_samples)
    assert observation == LevelObservation(sample_count=5, mean=1.0, variance=0.0)


def test_aggregate_level_observation_empty_is_none() -> None:
    assert aggregate_level_observation(()) is None


def test_global_level_sample_count_below_one_is_unmeasurable(
    policy: InferencePolicy,
) -> None:
    result = resolve_assessment_posterior(
        agency=None,
        category=None,
        global_level=None,
        policy=policy,
    )
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
    assert result.detail is UnmeasurableDetail.TOO_FEW_DRAWS


@pytest.mark.legacy_parity
def test_missing_levels_fall_through_with_zero_weight(policy: InferencePolicy) -> None:
    """legacy-behavior — 계층이 없으면 그 계층의 가중치는 정확히 0(회귀 관측, 판정 아님)."""
    global_level = LevelObservation(sample_count=10, mean=1.0, variance=0.01)
    posterior = resolve_assessment_posterior(
        agency=None, category=None, global_level=global_level, policy=policy
    )
    assert not isinstance(posterior, Unmeasurable)
    assert posterior.level_weights.agency == 0.0
    assert posterior.level_weights.category == 0.0
    assert posterior.level_weights.global_ == 1.0
    assert posterior.mean == pytest.approx(1.0)


@pytest.mark.legacy_parity
def test_three_level_shrinkage_weights_sum_to_one(policy: InferencePolicy) -> None:
    global_level = LevelObservation(sample_count=1000, mean=1.0, variance=0.02)
    category_level = LevelObservation(sample_count=50, mean=1.02, variance=0.015)
    agency_level = LevelObservation(sample_count=9, mean=1.05, variance=0.01)
    posterior = resolve_assessment_posterior(
        agency=agency_level,
        category=category_level,
        global_level=global_level,
        policy=policy,
    )
    assert not isinstance(posterior, Unmeasurable)
    total = (
        posterior.level_weights.agency
        + posterior.level_weights.category
        + posterior.level_weights.global_
    )
    assert total == pytest.approx(1.0)
    # κ=12 에서 n=9 → 9/(9+12) = 0.4286(legacy-behavior 재현).
    assert posterior.level_weights.agency == pytest.approx(9 / 21)


def test_predictive_std_has_policy_floor(policy: InferencePolicy) -> None:
    """수축된 표준편차는 `assessment.min_predictive_std` 밑으로 내려가지 않는다(legacy
    `max(sqrt(variance), MIN_PREDICTIVE_STD)`)."""
    global_level = LevelObservation(sample_count=5, mean=1.0, variance=0.0)
    posterior = resolve_assessment_posterior(
        agency=None, category=None, global_level=global_level, policy=policy
    )
    assert not isinstance(posterior, Unmeasurable)
    assert posterior.std >= float(policy.assessment_min_predictive_std)
