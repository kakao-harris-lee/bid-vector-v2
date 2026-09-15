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
    _trial_outcome,
    gate_outcome,
    passes_gate,
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


def test__trial_outcome_no_evaluable_window_when_n_below_two() -> None:
    outcome = _trial_outcome(
        baseline_rmse=0.1,
        model_rmse=0.05,
        model_predictions=np.array([0.5]),
        baseline_predictions=np.array([0.6]),
        targets=np.array([0.55]),
        policy=_POLICY,
    )
    assert isinstance(outcome, NotEvaluable)
    assert outcome.reason == NotEvaluableReason.NO_EVALUABLE_WINDOW


def test__trial_outcome_underpowered_when_improvement_below_mde() -> None:
    targets = np.array([0.7, 0.71, 0.69, 0.70, 0.705])
    baseline = targets + np.array([0.01, -0.01, 0.02, -0.02, 0.01])
    model = targets + np.array([0.009, -0.011, 0.021, -0.019, 0.011])  # 거의 동일
    outcome = _trial_outcome(
        baseline_rmse=0.1,
        model_rmse=0.099,
        model_predictions=model,
        baseline_predictions=baseline,
        targets=targets,
        policy=_POLICY,
    )
    assert isinstance(outcome, NotEvaluable)
    assert outcome.reason == NotEvaluableReason.UNDERPOWERED


def test__trial_outcome_passed_when_model_clearly_better() -> None:
    model, baseline, targets = _strong_win()
    from ml_engine.evaluation.scoring import rmse_bias_std

    baseline_rmse, _, _ = rmse_bias_std(baseline, targets)
    model_rmse, _, _ = rmse_bias_std(model, targets)
    outcome = _trial_outcome(
        baseline_rmse=baseline_rmse,
        model_rmse=model_rmse,
        model_predictions=model,
        baseline_predictions=baseline,
        targets=targets,
        policy=_POLICY,
    )
    assert isinstance(outcome, Passed)


def test__trial_outcome_failed_when_model_worse_but_evaluable() -> None:
    model, baseline, targets = _strong_win()
    from ml_engine.evaluation.scoring import rmse_bias_std

    # 모델과 베이스라인을 맞바꿔 모델이 지게 만든다.
    baseline_rmse, _, _ = rmse_bias_std(model, targets)
    model_rmse, _, _ = rmse_bias_std(baseline, targets)
    outcome = _trial_outcome(
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

    for func in (gate_outcome, passes_gate):
        signature = inspect.signature(func)
        assert "threshold" not in signature.parameters
        assert "policy" in signature.parameters


def test_trial_outcome_is_not_public() -> None:
    """verifier r1 H-3 — `trial_outcome`은 안정성 없이 `Passed`(→ `Promotable`)를
    만들 수 있어 public 이면 위협 모델 (f)의 우회 표면이었다. 모듈 안에서는 여전히
    `_trial_outcome`으로 존재하지만(이 파일 자신의 판정식 검증용) evaluation
    패키지의 public 표면(`__all__`)에는 없다."""
    import ml_engine.evaluation as evaluation_module

    assert "trial_outcome" not in evaluation_module.__all__
    assert not hasattr(evaluation_module, "trial_outcome")


def test_passes_gate_is_the_single_predicate_definition() -> None:
    """verifier r1 H-2 — `gate_outcome`·`_trial_outcome`이 내는 판정이 `passes_gate`
    하나로 설명된다(별도 판정식이 없다는 것을 값으로 확인)."""
    model, baseline, targets = _strong_win()
    from ml_engine.evaluation.scoring import paired_t, rmse_bias_std

    baseline_rmse, _, _ = rmse_bias_std(baseline, targets)
    model_rmse, _, _ = rmse_bias_std(model, targets)
    statistic = paired_t(model, baseline, targets)
    outcome = gate_outcome(
        baseline_rmse=baseline_rmse,
        model_rmse=model_rmse,
        model_predictions=model,
        baseline_predictions=baseline,
        targets=targets,
        policy=_POLICY,
        stability=_STABLE,
    )
    assert isinstance(outcome, Passed) == passes_gate(
        baseline_rmse, model_rmse, statistic, _POLICY
    )


def test_passes_gate_requires_both_rmse_and_statistic_conditions() -> None:
    """verifier r1 H-2 변이 #6 재현 — 안정성 sweep 의 인라인 재구현이 `and
    statistic < -threshold` 조건을 빠뜨려도 `pytest tests -q`가 630 passed 였다.
    두 조건이 **각각** 필요함을 직접 확인한다(단일 조건만으로는 통과하지 않는다)."""
    # rmse 는 개선됐지만 통계량이 유의 수준(2.58)에 못 미친다 → 통과하면 안 된다.
    assert not passes_gate(0.1, 0.05, -1.0, _POLICY)
    # 통계량은 임계를 넘지만 rmse 자체는 개선되지 않았다(model >= baseline) → 통과하면 안 된다.
    assert not passes_gate(0.1, 0.1, -3.0, _POLICY)
    # 둘 다 충족하면 통과한다.
    assert passes_gate(0.1, 0.05, -3.0, _POLICY)
