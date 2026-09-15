"""RED — `ml_engine.evaluation.verdict`(scope ⑥, 신규 — legacy bool 셋 조합을 3값
`GateOutcome` 로). 규칙 순서: 창 없음(n<2) → seed 불안정 → underpowered → 판정식."""

from __future__ import annotations

import numpy as np

from ml_engine.evaluation.diagnostics import StabilitySummary
from ml_engine.evaluation.policy import EvaluationPolicy
from ml_engine.evaluation.verdict import (
    Failed,
    NotEvaluable,
    NotEvaluableReason,
    Passed,
    gate_outcome,
    trial_outcome,
)

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

_STABLE = StabilitySummary(
    trials=(),
    passed_count=1,
    sign_consistent=True,
    verdict_consistent=True,
    min_improvement_ratio=0.1,
    max_improvement_ratio=0.1,
    min_abs_paired_t=3.0,
    max_abs_paired_t=3.0,
)


def _strong_win() -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """확실히 이기는 홀드아웃 — 대응 t 가 임계를 넘고 개선이 뚜렷하다."""
    rng = np.random.default_rng(7)
    targets = rng.normal(loc=0.7, scale=0.02, size=200)
    baseline = targets + rng.normal(scale=0.05, size=200)
    model = targets + rng.normal(scale=0.005, size=200)
    return model, baseline, targets


def test_trial_outcome_no_evaluable_window_when_n_below_two() -> None:
    outcome = trial_outcome(
        baseline_rmse=0.1,
        model_rmse=0.05,
        model_predictions=np.array([0.5]),
        baseline_predictions=np.array([0.6]),
        targets=np.array([0.55]),
        policy=_POLICY,
    )
    assert isinstance(outcome, NotEvaluable)
    assert outcome.reason == NotEvaluableReason.NO_EVALUABLE_WINDOW


def test_trial_outcome_underpowered_when_improvement_below_mde() -> None:
    targets = np.array([0.7, 0.71, 0.69, 0.70, 0.705])
    baseline = targets + np.array([0.01, -0.01, 0.02, -0.02, 0.01])
    model = targets + np.array([0.009, -0.011, 0.021, -0.019, 0.011])  # 거의 동일
    outcome = trial_outcome(
        baseline_rmse=0.1,
        model_rmse=0.099,
        model_predictions=model,
        baseline_predictions=baseline,
        targets=targets,
        policy=_POLICY,
    )
    assert isinstance(outcome, NotEvaluable)
    assert outcome.reason == NotEvaluableReason.UNDERPOWERED


def test_trial_outcome_passed_when_model_clearly_better() -> None:
    model, baseline, targets = _strong_win()
    from ml_engine.evaluation.scoring import rmse_bias_std

    baseline_rmse, _, _ = rmse_bias_std(baseline, targets)
    model_rmse, _, _ = rmse_bias_std(model, targets)
    outcome = trial_outcome(
        baseline_rmse=baseline_rmse,
        model_rmse=model_rmse,
        model_predictions=model,
        baseline_predictions=baseline,
        targets=targets,
        policy=_POLICY,
    )
    assert isinstance(outcome, Passed)


def test_trial_outcome_failed_when_model_worse_but_evaluable() -> None:
    model, baseline, targets = _strong_win()
    from ml_engine.evaluation.scoring import rmse_bias_std

    # 모델과 베이스라인을 맞바꿔 모델이 지게 만든다.
    baseline_rmse, _, _ = rmse_bias_std(model, targets)
    model_rmse, _, _ = rmse_bias_std(baseline, targets)
    outcome = trial_outcome(
        baseline_rmse=baseline_rmse,
        model_rmse=model_rmse,
        model_predictions=baseline,
        baseline_predictions=model,
        targets=targets,
        policy=_POLICY,
    )
    assert isinstance(outcome, Failed)


def test_gate_outcome_seed_unstable_overrides_passed() -> None:
    model, baseline, targets = _strong_win()
    from ml_engine.evaluation.scoring import rmse_bias_std

    baseline_rmse, _, _ = rmse_bias_std(baseline, targets)
    model_rmse, _, _ = rmse_bias_std(model, targets)
    unstable = StabilitySummary(
        trials=(),
        passed_count=1,
        sign_consistent=False,
        verdict_consistent=True,
        min_improvement_ratio=-0.1,
        max_improvement_ratio=0.3,
        min_abs_paired_t=0.5,
        max_abs_paired_t=3.0,
    )
    outcome = gate_outcome(
        baseline_rmse=baseline_rmse,
        model_rmse=model_rmse,
        model_predictions=model,
        baseline_predictions=baseline,
        targets=targets,
        policy=_POLICY,
        stability=unstable,
    )
    assert isinstance(outcome, NotEvaluable)
    assert outcome.reason == NotEvaluableReason.SEED_UNSTABLE


def test_gate_outcome_stable_and_passed_flows_through() -> None:
    model, baseline, targets = _strong_win()
    from ml_engine.evaluation.scoring import rmse_bias_std

    baseline_rmse, _, _ = rmse_bias_std(baseline, targets)
    model_rmse, _, _ = rmse_bias_std(model, targets)
    outcome = gate_outcome(
        baseline_rmse=baseline_rmse,
        model_rmse=model_rmse,
        model_predictions=model,
        baseline_predictions=baseline,
        targets=targets,
        policy=_POLICY,
        stability=_STABLE,
    )
    assert isinstance(outcome, Passed)


def test_gate_outcome_no_evaluable_window_takes_priority_over_stability() -> None:
    outcome = gate_outcome(
        baseline_rmse=0.1,
        model_rmse=0.05,
        model_predictions=np.array([0.5]),
        baseline_predictions=np.array([0.6]),
        targets=np.array([0.55]),
        policy=_POLICY,
        stability=_STABLE,
    )
    assert isinstance(outcome, NotEvaluable)
    assert outcome.reason == NotEvaluableReason.NO_EVALUABLE_WINDOW


def test_no_bare_threshold_parameter_on_public_entry_points() -> None:
    """설계 검토 (1) 첫 행 — 임계를 낱개 인자로 받는 public 함수가 없다."""
    import inspect

    for func in (trial_outcome, gate_outcome):
        signature = inspect.signature(func)
        assert "threshold" not in signature.parameters
        assert "policy" in signature.parameters
