"""RED — `ml_engine.evaluation.diagnostics`(scope ④, `Reuse:
award_rate_diagnostics.py@ed4b06c`). 「못 이겼다」와 「못 쟀다」를 가르는 계측."""

from __future__ import annotations

import numpy as np
import pytest

from ml_engine.evaluation.baselines import BaselineSpec
from ml_engine.evaluation.diagnostics import (
    StabilityTrial,
    category_counts,
    coverage_splits,
    minimum_detectable_improvement,
    required_row_count,
    summarize_stability,
    unlearned_cells,
)
from ml_engine.features import FeatureFacts, Missing, Present

_THRESHOLD = 2.58


def _facts(category: str = "civil") -> FeatureFacts:
    return FeatureFacts(
        base_amount=Present(2e8),
        category_code=Present(category),
        agency_id=Present("a1"),
        denominator_source=Present("clean-base"),
    )


def test_required_row_count_none_when_model_worse() -> None:
    assert required_row_count(0.5, 100, threshold=_THRESHOLD) is None


def test_required_row_count_none_when_row_count_non_positive() -> None:
    assert required_row_count(-3.0, 0, threshold=_THRESHOLD) is None


def test_required_row_count_computes_ceiling() -> None:
    result = required_row_count(-5.0, 100, threshold=2.0)
    assert result == int(np.ceil(100 * (2.0 / 5.0) ** 2))


def test_minimum_detectable_improvement_zero_for_single_row() -> None:
    result = minimum_detectable_improvement(
        np.array([0.5]), np.array([0.6]), np.array([0.55]), threshold=_THRESHOLD
    )
    assert result == 0.0


def test_minimum_detectable_improvement_zero_when_baseline_mse_non_positive() -> None:
    targets = np.array([1.0, 1.0, 1.0])
    baseline = targets  # 완벽 -> mse == 0
    model = np.array([0.9, 0.95, 0.8])
    result = minimum_detectable_improvement(
        model, baseline, targets, threshold=_THRESHOLD
    )
    assert result == 0.0


def test_minimum_detectable_improvement_is_non_negative_for_realistic_case() -> None:
    rng = np.random.default_rng(42)
    targets = rng.normal(loc=0.7, scale=0.05, size=50)
    baseline = targets + rng.normal(scale=0.03, size=50)
    model = targets + rng.normal(scale=0.02, size=50)
    result = minimum_detectable_improvement(
        model, baseline, targets, threshold=_THRESHOLD
    )
    assert result >= 0.0


def test_minimum_detectable_improvement_exact_value_for_hand_computed_inputs() -> None:
    """verifier r1 H-2 변이 #8 재현 — 기존 test 는 경계(0.0)·부호(>=0.0)만 확인해
    `x1.33`·`x0.5` 같은 배수 변이가 조용히 통과했다. 손으로 미리 계산한 값과
    정확히 대조한다(baseline_mse=4.0, deviation≈4.6188, gap≈5.9583 →
    detectable_mse=0.0 으로 클램프 → MDE=improvement_ratio(2.0, 0.0)=1.0)."""
    targets = np.array([0.0, 0.0, 0.0, 0.0])
    baseline = np.array([2.0, 2.0, 2.0, 2.0])
    model = np.array([1.0, 1.0, 3.0, 3.0])
    result = minimum_detectable_improvement(model, baseline, targets, threshold=2.58)
    assert result == pytest.approx(1.0)


def test_coverage_splits_separates_covered_and_fallback() -> None:
    covered = np.array([True, True, False])
    model = np.array([0.9, 0.85, 0.5])
    baseline = np.array([0.88, 0.80, 0.6])
    targets = np.array([0.9, 0.9, 0.9])
    splits = coverage_splits(covered, model, baseline, targets)
    names = [s.segment for s in splits]
    assert "baseline-covered" in names
    assert "baseline-fallback" in names
    covered_split = next(s for s in splits if s.segment == "baseline-covered")
    assert covered_split.row_count == 2


def test_coverage_splits_skips_empty_slice() -> None:
    covered = np.array([True, True])
    model = np.array([0.9, 0.85])
    baseline = np.array([0.88, 0.80])
    targets = np.array([0.9, 0.9])
    splits = coverage_splits(covered, model, baseline, targets)
    assert [s.segment for s in splits] == ["baseline-covered"]


def test_unlearned_cells_lists_cells_missing_from_training() -> None:
    spec = BaselineSpec(
        name="category",
        key=lambda facts: (
            facts.category_code.value
            if isinstance(facts.category_code, Present)
            else "unknown"
        ),
    )
    train_facts = [_facts("civil"), _facts("civil")]
    test_facts = [_facts("civil"), _facts("electrical"), _facts("electrical")]
    result = unlearned_cells(spec, train_facts, test_facts)
    assert len(result) == 1
    assert result[0].key == "electrical"
    assert result[0].row_count == 2


def test_unlearned_cells_respects_min_count() -> None:
    spec = BaselineSpec(
        name="agency",
        key=lambda facts: (
            facts.agency_id.value if isinstance(facts.agency_id, Present) else "unknown"
        ),
        min_count=3,
    )
    train_facts = [
        _facts(),
        _facts(),
    ]  # 2건 < min_count(3) -> 학습이 못 배운 것으로 취급
    test_facts = [_facts()]
    result = unlearned_cells(spec, train_facts, test_facts)
    assert len(result) == 1


def test_category_counts_orders_by_row_count_descending() -> None:
    facts = [_facts("civil"), _facts("civil"), _facts("electrical")]
    result = category_counts(facts)
    assert result[0].category == "civil"
    assert result[0].row_count == 2
    assert result[1].category == "electrical"


def test_category_counts_treats_missing_as_unknown() -> None:
    facts = [
        FeatureFacts(
            base_amount=Present(2e8),
            category_code=Missing(1),
            agency_id=Present("a1"),
            denominator_source=Present("clean-base"),
        )
    ]
    result = category_counts(facts)
    assert result[0].category == "unknown"


def test_summarize_stability_sign_consistent_when_all_same_sign() -> None:
    trials = [
        StabilityTrial(seed=1, improvement_ratio=0.1, paired_t=-3.0, passed=True),
        StabilityTrial(seed=2, improvement_ratio=0.05, paired_t=-2.7, passed=True),
    ]
    summary = summarize_stability(trials)
    assert summary.sign_consistent
    assert summary.verdict_consistent
    assert summary.passed_count == 2


def test_summarize_stability_sign_inconsistent_when_signs_flip() -> None:
    trials = [
        StabilityTrial(seed=1, improvement_ratio=0.1, paired_t=-3.0, passed=True),
        StabilityTrial(seed=2, improvement_ratio=-0.05, paired_t=1.0, passed=False),
    ]
    summary = summarize_stability(trials)
    assert not summary.sign_consistent
    assert not summary.verdict_consistent


def test_summarize_stability_empty_trials_is_neutral() -> None:
    summary = summarize_stability([])
    assert summary.sign_consistent
    assert summary.verdict_consistent
    assert summary.passed_count == 0
    assert summary.min_improvement_ratio == 0.0
