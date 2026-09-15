"""RED — `ml_engine.evaluation.segments`(scope ③, `Reuse: award_rate_scoring.py@ed4b06c`
갈래). 축은 `category`·`amount_band` 둘(D-5C2-9 — `published_floor` 축 없음).
`regressed_segments`는 `improvement_ratio < 0 ∧ row_count > 1`(1행 세그먼트 제외)."""

from __future__ import annotations

import numpy as np

from ml_engine.evaluation.policy import EvaluationPolicy
from ml_engine.evaluation.segments import (
    regressed_segments,
    segment_scores,
    segment_specs,
)
from ml_engine.features import FeatureFacts, Present

_POLICY = EvaluationPolicy(
    version="test",
    paired_t_threshold=2.58,
    gate_baseline="category_x_band",
    gate_model="gbm_all_strata",
    gate_stratum="clean-base",
    maturity_threshold=0.70,
    min_evaluation_rows=2,
    max_origins=5,
    agency_baseline_min_count=2,
    stability_seeds=(1,),
    amount_band_edges=(1e8, 5e8, 1e9, 5e9),
    segment_axes=("category", "amount_band"),
)


def _facts(*, amount: float = 2e8, category: str = "civil") -> FeatureFacts:
    return FeatureFacts(
        base_amount=Present(amount),
        category_code=Present(category),
        agency_id=Present("a1"),
        denominator_source=Present("clean-base"),
    )


def test_segment_specs_has_two_axes_from_policy() -> None:
    specs = segment_specs(_POLICY)
    assert [spec.axis for spec in specs] == ["category", "amount_band"]


def test_segment_specs_only_declares_axes_present_in_policy() -> None:
    single_axis_policy = EvaluationPolicy(
        version="test",
        paired_t_threshold=2.58,
        gate_baseline="category_x_band",
        gate_model="gbm_all_strata",
        gate_stratum="clean-base",
        maturity_threshold=0.70,
        min_evaluation_rows=2,
        max_origins=5,
        agency_baseline_min_count=2,
        stability_seeds=(1,),
        amount_band_edges=(1e8, 5e8, 1e9, 5e9),
        segment_axes=("category",),
    )
    specs = segment_specs(single_axis_policy)
    assert [spec.axis for spec in specs] == ["category"]


def test_segment_scores_partitions_all_rows_per_axis() -> None:
    facts = [
        _facts(category="civil"),
        _facts(category="civil"),
        _facts(category="electrical"),
    ]
    targets = np.array([0.8, 0.7, 0.6])
    baseline_predictions = np.array([0.75, 0.75, 0.65])
    model_predictions = np.array([0.79, 0.71, 0.62])
    specs = segment_specs(_POLICY)
    scores = segment_scores(
        facts,
        targets,
        baseline_predictions=baseline_predictions,
        model_predictions=model_predictions,
        specs=specs,
    )
    category_scores = [s for s in scores if s.axis == "category"]
    assert sum(s.row_count for s in category_scores) == len(facts)


def test_segment_score_improvement_ratio_negative_means_model_worse() -> None:
    facts = [_facts(category="civil"), _facts(category="civil")]
    targets = np.array([1.0, 0.8])
    baseline_predictions = np.array([0.9, 0.85])  # 베이스라인은 그럭저럭
    model_predictions = np.array([0.2, 0.1])  # 모델이 훨씬 나쁨
    specs = segment_specs(_POLICY)
    scores = segment_scores(
        facts,
        targets,
        baseline_predictions=baseline_predictions,
        model_predictions=model_predictions,
        specs=specs,
    )
    civil_scores = [s for s in scores if s.axis == "category" and s.segment == "civil"]
    assert len(civil_scores) == 1
    assert civil_scores[0].improvement_ratio < 0


def test_regressed_segments_excludes_single_row_segments() -> None:
    """verifier r1 M-2 재현 — 수정 전 test 는 베이스라인이 완벽(`baseline_rmse == 0`)
    이라 `improvement_ratio(0, x) == 0.0`(음수 아님) 자체로 회귀 목록이 비었다.
    `and score.row_count > 1`을 지워도 630 passed 였던 이유가 그것이다. 베이스라인을
    불완전하게 만들어 `improvement_ratio`가 **실제로 음수**인 1행 세그먼트가 그래도
    1행 규칙으로 제외되는지를 확인한다."""
    facts = [_facts(category="civil"), _facts(category="electrical")]
    targets = np.array([1.0, 0.8])
    baseline_predictions = np.array([0.9, 0.75])  # 불완전한 베이스라인(rmse > 0)
    model_predictions = np.array([0.5, 0.3])  # 모델이 훨씬 나쁨 → improvement_ratio < 0
    specs = segment_specs(_POLICY)
    scores = segment_scores(
        facts,
        targets,
        baseline_predictions=baseline_predictions,
        model_predictions=model_predictions,
        specs=specs,
    )
    category_scores = [s for s in scores if s.axis == "category"]
    # 전제 확인 — 이 test 가 실제로 「1행이라서 제외」를 재는지: 개선률이 음수인데도
    # row_count 규칙만으로 제외돼야 한다(음수가 아니면 애초에 이 test 가 무의미하다).
    assert all(s.row_count == 1 for s in category_scores)
    assert all(s.improvement_ratio < 0 for s in category_scores)
    regressed = regressed_segments(scores)
    category_regressed = [s for s in regressed if s.axis == "category"]
    assert category_regressed == []


def test_regressed_segments_includes_multi_row_regression_sorted_by_row_count() -> None:
    facts = [
        _facts(category="civil"),
        _facts(category="civil"),
        _facts(category="civil"),
        _facts(category="electrical"),
        _facts(category="electrical"),
    ]
    targets = np.array([1.0, 0.8, 0.6, 0.9, 0.7])
    baseline_predictions = np.array([0.95, 0.82, 0.55, 0.88, 0.68])
    model_predictions = np.array([0.2, 0.1, 0.05, 0.3, 0.15])  # 둘 다 모델이 나쁨
    specs = segment_specs(_POLICY)
    scores = segment_scores(
        facts,
        targets,
        baseline_predictions=baseline_predictions,
        model_predictions=model_predictions,
        specs=specs,
    )
    regressed = regressed_segments(scores)
    category_regressed = [s for s in regressed if s.axis == "category"]
    assert [s.segment for s in category_regressed] == ["civil", "electrical"]
