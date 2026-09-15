"""Reuse: bid-vector/app/services/ml_training/award_rate_holdout.py@ed4b06c

`ml_engine.training._holdout_window` — 베이스라인·진단·안정성·판정 조립(scope ⑦ 세부).
`holdout.py`의 비공개 내부 모듈이다(public 표면은 `run_holdout`/`HoldoutRejected`뿐) —
창 분할·GBM 학습은 형제 파일 `_holdout_fit.py`(설계 래칫 `file_loc_soft_limit` 준수를
위한 분리, checklist.md 「계약과 어긋나 판단이 필요했던 자리」).
"""

from __future__ import annotations

import dataclasses

import numpy as np

from ml_engine.evaluation import (
    BaselineSpec,
    CoverageSplit,
    EvaluationPolicy,
    GateOutcome,
    ModelScore,
    SegmentScore,
    StabilitySummary,
    StabilityTrial,
    UnlearnedCell,
    WeekMaturity,
    WindowExclusionReason,
    WindowResult,
    baseline_specs,
    category_counts,
    coverage_splits,
    gate_outcome,
    group_mean_predictions,
    improvement_ratio,
    paired_t,
    rmse_bias_std,
    segment_scores,
    segment_specs,
    summarize_stability,
    unlearned_cells,
)
from ml_engine.training._holdout_fit import (
    ModelFit,
    Split,
    WindowSkip,
    WindowSuccess,
    build_split,
    feature_space_from_manifest,
    fit_models,
    matrix_for,
    stability_seed_order,
    sub_dataset,
)
from ml_engine.training.booster import TrainerLike
from ml_engine.training.corpus import TrainingRow
from ml_engine.training.dataset import LoadedDataset
from ml_engine.training.policy import TrainingPolicy
from ml_engine.training.release import derive_release_id
from ml_engine.training.spec import TrainingSpec, spec_checksum
from ml_engine.training.train import CodeVersion, TrainingRejected, train_award_rate_gbm


@dataclasses.dataclass(frozen=True)
class _BaselineFit:
    scores: tuple[ModelScore, ...]
    gate_predictions: np.ndarray
    gate_covered: np.ndarray
    gate_rmse: float
    gate_spec: BaselineSpec


def _fit_baselines(split: Split, evaluation_policy: EvaluationPolicy) -> _BaselineFit:
    """다섯 베이스라인을 게이트 학습 구간(`gate_train`)으로 적합하고 평가 행렬로 채점."""
    specs = baseline_specs(evaluation_policy)
    scores: list[ModelScore] = []
    gate_predictions: np.ndarray | None = None
    gate_covered: np.ndarray | None = None
    gate_rmse = 0.0
    for spec_item in specs:
        predictions, covered = group_mean_predictions(
            spec_item,
            split.gate_train_facts,
            split.gate_train_labels,
            split.usable_facts,
            global_mean=split.gate_train_mean,
        )
        rmse, bias, std = rmse_bias_std(predictions, split.targets)
        scores.append(
            ModelScore(
                name=spec_item.name,
                rmse=rmse,
                bias=bias,
                residual_std=std,
                coverage=float(np.mean(covered)) if covered.size else 0.0,
            )
        )
        if spec_item.name == evaluation_policy.gate_baseline:
            gate_predictions, gate_covered, gate_rmse = predictions, covered, rmse

    assert gate_predictions is not None
    assert gate_covered is not None
    gate_spec = next(s for s in specs if s.name == evaluation_policy.gate_baseline)
    return _BaselineFit(
        scores=tuple(scores),
        gate_predictions=gate_predictions,
        gate_covered=gate_covered,
        gate_rmse=gate_rmse,
        gate_spec=gate_spec,
    )


def _run_stability(
    dataset: LoadedDataset,
    split: Split,
    spec: TrainingSpec,
    training_policy: TrainingPolicy,
    evaluation_policy: EvaluationPolicy,
    trainer: TrainerLike,
    code_version: CodeVersion,
    fit: ModelFit,
    baselines: _BaselineFit,
) -> StabilitySummary | WindowSkip:
    """D-5C2-5 — `stability_seeds` 전부 재채점, 헤드라인 seed 는 재학습 없이 재사용."""
    trials: list[StabilityTrial] = []
    for seed in stability_seed_order(spec.seed, evaluation_policy):
        if seed == spec.seed:
            trial_predictions = fit.predictions_all
        else:
            trial_artifact = train_award_rate_gbm(
                sub_dataset(dataset, split.train_rows_raw),
                dataclasses.replace(spec, seed=seed),
                training_policy,
                trainer,
                code_version,
            )
            if isinstance(trial_artifact, TrainingRejected):
                return WindowSkip(
                    WindowExclusionReason.TRAINING_REJECTED,
                    f"stability seed {seed}: {trial_artifact.reason.value}",
                )
            trial_space = feature_space_from_manifest(trial_artifact.feature_manifest)
            trial_predictions = trial_artifact.booster.predict(
                matrix_for(trial_space, split.usable_test_rows)
            )
        trial_rmse, _, _ = rmse_bias_std(trial_predictions, split.targets)
        trial_statistic = paired_t(
            trial_predictions, baselines.gate_predictions, split.targets
        )
        trials.append(
            StabilityTrial(
                seed=seed,
                improvement_ratio=improvement_ratio(baselines.gate_rmse, trial_rmse),
                paired_t=trial_statistic,
                passed=(
                    trial_rmse < baselines.gate_rmse
                    and trial_statistic < -evaluation_policy.paired_t_threshold
                ),
            )
        )
    return summarize_stability(trials)


@dataclasses.dataclass(frozen=True)
class _Diagnostics:
    segments: tuple[SegmentScore, ...]
    coverage: tuple[CoverageSplit, ...]
    unlearned: tuple[UnlearnedCell, ...]


def _compute_diagnostics(
    split: Split,
    evaluation_policy: EvaluationPolicy,
    fit: ModelFit,
    baselines: _BaselineFit,
) -> _Diagnostics:
    return _Diagnostics(
        segments=tuple(
            segment_scores(
                split.usable_facts,
                split.targets,
                baseline_predictions=baselines.gate_predictions,
                model_predictions=fit.predictions_all,
                specs=segment_specs(evaluation_policy),
            )
        ),
        coverage=tuple(
            coverage_splits(
                baselines.gate_covered,
                fit.predictions_all,
                baselines.gate_predictions,
                split.targets,
            )
        ),
        unlearned=tuple(
            unlearned_cells(
                baselines.gate_spec, split.gate_train_facts, split.usable_facts
            )
        ),
    )


def _assemble_window_result(
    window: WeekMaturity,
    spec: TrainingSpec,
    code_version: CodeVersion,
    split: Split,
    fit: ModelFit,
    baselines: _BaselineFit,
    diagnostics: _Diagnostics,
    stability: StabilitySummary,
    outcome: GateOutcome,
) -> WindowResult:
    release_id = derive_release_id(
        dataset_id=f"{fit.trained_all.dataset_id}#window={window.start.isoformat()}",
        training_spec_version=spec.version,
        training_spec_checksum=spec_checksum(spec),
        seed=spec.seed,
        code_version=code_version.value,
    )
    models = [fit.model_score]
    if fit.conservative_score is not None:
        models.append(fit.conservative_score)
    return WindowResult(
        window_start=window.start.isoformat(),
        window_end=window.end.isoformat(),
        window_maturity=window.maturity_ratio,
        window_opened_count=window.opened_count,
        window_settled_count=window.settled_count,
        cutoff_at=window.start.isoformat(),
        train_row_count=len(split.train_rows_all),
        gate_train_row_count=len(split.gate_train),
        gate_test_row_count=len(split.usable_test_rows),
        train_mean=split.gate_train_mean,
        test_mean=float(np.mean(split.targets)) if split.targets.size else 0.0,
        test_std=float(np.std(split.targets, ddof=1))
        if split.targets.size > 1
        else 0.0,
        train_categories=tuple(
            category_counts([row.facts for row in split.train_rows_all])
        ),
        test_categories=tuple(category_counts(split.usable_facts)),
        baselines=baselines.scores,
        models=tuple(models),
        segments=diagnostics.segments,
        coverage=diagnostics.coverage,
        unlearned_baseline_cells=diagnostics.unlearned,
        outcome=outcome,
        stability=stability,
        release_id=release_id,
    )


def _finalize_window(
    window: WeekMaturity,
    spec: TrainingSpec,
    code_version: CodeVersion,
    evaluation_policy: EvaluationPolicy,
    split: Split,
    fit: ModelFit,
    baselines: _BaselineFit,
    diagnostics: _Diagnostics,
    stability: StabilitySummary,
) -> WindowSuccess:
    outcome: GateOutcome = gate_outcome(
        baseline_rmse=baselines.gate_rmse,
        model_rmse=fit.model_score.rmse,
        model_predictions=fit.predictions_all,
        baseline_predictions=baselines.gate_predictions,
        targets=split.targets,
        policy=evaluation_policy,
        stability=stability,
    )
    return WindowSuccess(
        _assemble_window_result(
            window,
            spec,
            code_version,
            split,
            fit,
            baselines,
            diagnostics,
            stability,
            outcome,
        )
    )


def evaluate_one_window(
    window: WeekMaturity,
    ordered_rows: tuple[TrainingRow, ...],
    dataset: LoadedDataset,
    spec: TrainingSpec,
    training_policy: TrainingPolicy,
    evaluation_policy: EvaluationPolicy,
    trainer: TrainerLike,
    code_version: CodeVersion,
) -> WindowSuccess | WindowSkip:
    """`holdout.py`가 창마다 부르는 유일 진입점(모듈 경계 안에서는 비공개, 형제
    파일에는 공개 — leading underscore 없음)."""
    split = build_split(window, ordered_rows, dataset, evaluation_policy.gate_stratum)
    if isinstance(split, WindowSkip):
        return split

    fit = fit_models(
        dataset, split, spec, training_policy, evaluation_policy, trainer, code_version
    )
    if isinstance(fit, WindowSkip):
        return fit

    baselines = _fit_baselines(split, evaluation_policy)
    diagnostics = _compute_diagnostics(split, evaluation_policy, fit, baselines)

    stability = _run_stability(
        dataset,
        split,
        spec,
        training_policy,
        evaluation_policy,
        trainer,
        code_version,
        fit,
        baselines,
    )
    if isinstance(stability, WindowSkip):
        return stability

    return _finalize_window(
        window,
        spec,
        code_version,
        evaluation_policy,
        split,
        fit,
        baselines,
        diagnostics,
        stability,
    )
