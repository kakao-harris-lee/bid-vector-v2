"""RED — `ml_engine.training.encoding_oof`(scope ④). 두 인코딩을 이름으로 가른다 —
학습 행렬의 인코딩 열은 폴드별 OOF, artifact 표는 전 구간 한 번. legacy
`tests/test_award_rate_gbm_training.py:79` 의 OOF 불변식(학습 행렬 인코딩 열 ≠ 전 구간
인코딩, 모든 행)을 이식한다."""

from __future__ import annotations

from datetime import UTC, datetime

import numpy as np

from ml_engine.features import (
    EncodingPolicy,
    FeatureFacts,
    Missing,
    NoObservations,
    Present,
)
from ml_engine.training.booster import TrainerFailed
from ml_engine.training.corpus import AwardRateLabel, TrainingRow
from ml_engine.training.encoding_oof import (
    OutOfFoldBuilt,
    OutOfFoldNoObservations,
    OutOfFoldTrainerFailed,
    full_corpus_feature_space,
    out_of_fold_matrix_and_residuals,
)
from ml_engine.training.spec import LightGbmHyperparameters

_POLICY = EncodingPolicy(agency_prior_strength=12.0, category_prior_strength=40.0)
_HYPERPARAMETERS = LightGbmHyperparameters(
    objective="regression",
    metric="rmse",
    learning_rate=0.3,
    num_leaves=7,
    min_data_in_leaf=1,
    feature_fraction=1.0,
    bagging_fraction=1.0,
    bagging_freq=0,
    lambda_l2=0.0,
    verbosity=-1,
    deterministic=True,
    force_row_wise=True,
    num_threads=1,
)


def _row(*, agency: str, category: str, label: float) -> TrainingRow:
    facts = FeatureFacts(
        base_amount=Present(1e8),
        category_code=Present(category),
        agency_id=Present(agency),
        denominator_source=Present("CLEAN"),
    )
    return TrainingRow(
        facts=facts,
        label=AwardRateLabel(label),
        opened_at=datetime(2026, 1, 1, tzinfo=UTC),
        stratum="clean-base",
    )


def _rows(n: int) -> list[TrainingRow]:
    return [
        _row(agency=f"agency-{i % 3}", category="civil", label=0.5 + 0.01 * (i % 5))
        for i in range(n)
    ]


class _ConstantBooster:
    def __init__(self, value: float, feature_names: tuple[str, ...]) -> None:
        self._value = value
        self._feature_names = feature_names

    def predict(self, matrix: np.ndarray) -> np.ndarray:
        return np.full(matrix.shape[0], self._value)

    def model_to_string(self) -> str:
        return "fake"

    def feature_name(self) -> list[str]:
        return list(self._feature_names)


class _ConstantTrainer:
    def __init__(self, value: float = 0.5) -> None:
        self._value = value

    def train(
        self, matrix, labels, feature_names, categorical_indices, params, seed, rounds
    ):
        return _ConstantBooster(self._value, tuple(feature_names))


class _NonFiniteTrainer:
    def train(
        self, matrix, labels, feature_names, categorical_indices, params, seed, rounds
    ):
        return _ConstantBooster(float("nan"), tuple(feature_names))


class _FailingTrainer:
    def train(
        self, matrix, labels, feature_names, categorical_indices, params, seed, rounds
    ):
        return TrainerFailed("boom")


def test_out_of_fold_matrix_and_residuals_builds_for_enough_rows() -> None:
    rows = _rows(20)
    result = out_of_fold_matrix_and_residuals(
        rows,
        folds=5,
        seed=1,
        policy=_POLICY,
        trainer=_ConstantTrainer(),
        hyperparameters=_HYPERPARAMETERS,
        num_boost_round=1,
    )
    assert isinstance(result, OutOfFoldBuilt)
    assert result.matrix.shape[0] == len(result.admitted_rows)
    assert result.labels.shape[0] == len(result.admitted_rows)
    assert result.residuals.shape[0] == len(result.admitted_rows)
    assert len(result.admitted_rows) == 20


def test_out_of_fold_encoding_differs_from_full_corpus_encoding() -> None:
    """legacy OOF 불변식 이식 — 학습 행렬의 agency_encoding 열은 전 구간 인코딩과 달라야
    한다(적어도 한 행에서, target leakage 차단 증거)."""
    rows = _rows(30)
    oof_result = out_of_fold_matrix_and_residuals(
        rows,
        folds=5,
        seed=1,
        policy=_POLICY,
        trainer=_ConstantTrainer(),
        hyperparameters=_HYPERPARAMETERS,
        num_boost_round=1,
    )
    assert isinstance(oof_result, OutOfFoldBuilt)
    full_space = full_corpus_feature_space(oof_result.admitted_rows, _POLICY)
    assert not isinstance(full_space, NoObservations)

    full_encoding_column = np.array(
        [
            full_space.build_row(row.facts).values[2]  # type: ignore[union-attr]
            for row in oof_result.admitted_rows
        ]
    )
    oof_encoding_column = oof_result.matrix[:, 2]
    assert not np.allclose(oof_encoding_column, full_encoding_column)


def test_out_of_fold_no_observations_when_fit_subset_has_no_valid_agency_category() -> (
    None
):
    def _missing_category_row() -> TrainingRow:
        facts = FeatureFacts(
            base_amount=Present(1e8),
            category_code=Missing(1),
            agency_id=Present("agency-0"),
            denominator_source=Present("CLEAN"),
        )
        return TrainingRow(
            facts=facts,
            label=AwardRateLabel(0.5),
            opened_at=datetime(2026, 1, 1, tzinfo=UTC),
            stratum="clean-base",
        )

    rows = [_missing_category_row() for _ in range(10)]
    result = out_of_fold_matrix_and_residuals(
        rows,
        folds=2,
        seed=1,
        policy=_POLICY,
        trainer=_ConstantTrainer(),
        hyperparameters=_HYPERPARAMETERS,
        num_boost_round=1,
    )
    assert isinstance(result, OutOfFoldNoObservations)


def test_out_of_fold_trainer_failure_is_result_type() -> None:
    rows = _rows(10)
    result = out_of_fold_matrix_and_residuals(
        rows,
        folds=2,
        seed=1,
        policy=_POLICY,
        trainer=_FailingTrainer(),
        hyperparameters=_HYPERPARAMETERS,
        num_boost_round=1,
    )
    assert isinstance(result, OutOfFoldTrainerFailed)
    assert result.detail == "boom"


def test_out_of_fold_non_finite_prediction_is_trainer_error() -> None:
    rows = _rows(10)
    result = out_of_fold_matrix_and_residuals(
        rows,
        folds=2,
        seed=1,
        policy=_POLICY,
        trainer=_NonFiniteTrainer(),
        hyperparameters=_HYPERPARAMETERS,
        num_boost_round=1,
    )
    assert isinstance(result, OutOfFoldTrainerFailed)
    assert "NON_FINITE" in result.detail


def test_full_corpus_feature_space_uses_all_rows_not_folds() -> None:
    rows = _rows(10)
    result = full_corpus_feature_space(rows, _POLICY)
    assert not isinstance(result, NoObservations)
    assert result.categories.values == ("civil",)


def test_full_corpus_feature_space_no_observations_when_empty() -> None:
    result = full_corpus_feature_space([], _POLICY)
    assert isinstance(result, NoObservations)
