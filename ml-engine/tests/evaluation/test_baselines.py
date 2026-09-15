"""RED — `ml_engine.evaluation.baselines`(scope ②, `Reuse: award_rate_scoring.py@ed4b06c`).
다섯 베이스라인은 `EvaluationPolicy`에서 파생된 **선언 데이터**다(분기 아님).
`group_mean_predictions`는 비율이 아니라 행별 커버리지 마스크를 낸다(조사 02 §1-5(e))."""

from __future__ import annotations

import numpy as np

from ml_engine.evaluation.baselines import baseline_specs, group_mean_predictions
from ml_engine.evaluation.policy import EvaluationPolicy
from ml_engine.features import FeatureFacts, Missing, Present

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


def _facts(
    *, amount: float = 2e8, category: str = "civil", agency: str = "a1"
) -> FeatureFacts:
    return FeatureFacts(
        base_amount=Present(amount),
        category_code=Present(category),
        agency_id=Present(agency),
        denominator_source=Present("clean-base"),
    )


def test_baseline_specs_has_five_declared_baselines() -> None:
    specs = baseline_specs(_POLICY)
    names = [spec.name for spec in specs]
    assert names == [
        "global_mean",
        "category",
        "amount_band",
        "category_x_band",
        "agency",
    ]


def test_baseline_specs_gate_baseline_name_is_present_in_table() -> None:
    specs = baseline_specs(_POLICY)
    assert _POLICY.gate_baseline in [spec.name for spec in specs]


def test_baseline_specs_agency_min_count_comes_from_policy() -> None:
    specs = baseline_specs(_POLICY)
    agency_spec = next(spec for spec in specs if spec.name == "agency")
    assert agency_spec.min_count == _POLICY.agency_baseline_min_count


def test_global_mean_baseline_ignores_grouping_key() -> None:
    specs = baseline_specs(_POLICY)
    spec = next(s for s in specs if s.name == "global_mean")
    train_facts = [_facts(category="civil"), _facts(category="electrical")]
    train_labels = np.array([0.8, 0.6])
    test_facts = [_facts(category="civil"), _facts(category="unknown-cat")]
    predictions, covered = group_mean_predictions(
        spec, train_facts, train_labels, test_facts, global_mean=0.7
    )
    assert np.allclose(predictions, [0.7, 0.7])
    assert covered.all()


def test_category_baseline_falls_back_to_global_mean_when_uncovered() -> None:
    specs = baseline_specs(_POLICY)
    spec = next(s for s in specs if s.name == "category")
    train_facts = [_facts(category="civil")]
    train_labels = np.array([0.9])
    test_facts = [_facts(category="civil"), _facts(category="never-seen")]
    predictions, covered = group_mean_predictions(
        spec, train_facts, train_labels, test_facts, global_mean=0.5
    )
    assert predictions[0] == 0.9
    assert covered[0]
    assert predictions[1] == 0.5
    assert not covered[1]


def test_agency_baseline_respects_min_count_fallback() -> None:
    """AGENCY_BASELINE_MIN_COUNT — 얕은 기관은 전역 평균으로 떨어진다(조사 02 §1-1)."""
    specs = baseline_specs(_POLICY)
    spec = next(s for s in specs if s.name == "agency")
    train_facts = [_facts(agency="shallow")]  # 1건 < min_count(2)
    train_labels = np.array([0.99])
    test_facts = [_facts(agency="shallow")]
    predictions, covered = group_mean_predictions(
        spec, train_facts, train_labels, test_facts, global_mean=0.5
    )
    assert predictions[0] == 0.5
    assert not covered[0]


def test_amount_band_baseline_uses_policy_edges() -> None:
    specs = baseline_specs(_POLICY)
    spec = next(s for s in specs if s.name == "amount_band")
    low = _facts(amount=5e7)  # < 1e8
    high = _facts(amount=6e9)  # > 5e9
    assert spec.key(low) != spec.key(high)


def test_category_x_band_key_combines_both_axes() -> None:
    specs = baseline_specs(_POLICY)
    spec = next(s for s in specs if s.name == "category_x_band")
    a = _facts(category="civil", amount=2e8)
    b = _facts(category="electrical", amount=2e8)
    assert spec.key(a) != spec.key(b)


def test_missing_category_falls_back_to_unknown_key() -> None:
    specs = baseline_specs(_POLICY)
    spec = next(s for s in specs if s.name == "category")
    facts = FeatureFacts(
        base_amount=Present(2e8),
        category_code=Missing(1),
        agency_id=Present("a1"),
        denominator_source=Present("clean-base"),
    )
    assert spec.key(facts) == "unknown"


def test_coverage_mask_reflects_per_row_fallback() -> None:
    """비율이 아니라 마스크 — 폴백 소수 행 분해에 필요(조사 02 §1-5(e))."""
    specs = baseline_specs(_POLICY)
    spec = next(s for s in specs if s.name == "category")
    train_facts = [_facts(category="civil")]
    train_labels = np.array([0.8])
    test_facts = [
        _facts(category="civil"),
        _facts(category="civil"),
        _facts(category="never-seen"),
    ]
    _predictions, covered = group_mean_predictions(
        spec, train_facts, train_labels, test_facts, global_mean=0.5
    )
    assert list(covered) == [True, True, False]
