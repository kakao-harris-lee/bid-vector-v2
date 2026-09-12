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


def test_out_of_fold_encoding_differs_from_full_corpus_encoding_for_every_row() -> None:
    """legacy OOF 불변식 이식(verifier r1 H-2) — 학습 행렬의 `agency_encoding` 열은 전 구간
    인코딩과 **모든 행**에서 달라야 한다. legacy 원본
    (`bid-vector/tests/test_award_rate_gbm_training.py::
    test_training_matrix_encoding_differs_from_the_self_including_encoding`)이 정확히
    이 두 단언을 갖는다 — 「일부가 아니라 모든 행이 달라야 한다. 한 폴드라도 전체
    인코딩을 쓰면 그 폴드의 행들이 여기서 같아진다」."""
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
    assert np.count_nonzero(
        np.abs(oof_encoding_column - full_encoding_column) > 1e-12
    ) == len(oof_result.admitted_rows)


def test_all_rows_assertion_catches_partial_leak_that_not_allclose_misses() -> None:
    """RED 먼저 재현(verifier r1 H-2) — 검증 레인이 재현한 반례(폴드 하나가 전 구간
    인코딩을 그대로 써서 30행 중 6행이 누수)를 합성 배열로 직접 만든다. `not np.allclose`
    단독은 이 반례를 통과시키지만(24행이 여전히 다르므로 전체 배열은 "가깝지 않다"),
    legacy 의 두 번째 단언(「모든 행이 달라야 한다」)은 이 반례를 정확히 잡아야 한다 —
    이 test 로 그 판별력 자체를 확인한 뒤, 위 test 가 실제 구현에 대해 그 강한 단언을
    통과시킴을 보인다(GREEN)."""
    # 24행은 정말 다르고(누수 없음), 6행은 "폴드 하나가 전 구간 인코딩을 그대로 써서"
    # oof 값과 full 값이 정확히 같다고 가정한 합성 반례.
    oof_column = np.concatenate([np.full(24, 0.11), np.full(6, 0.5)])
    full_column = np.concatenate([np.full(24, 0.20), np.full(6, 0.5)])

    # 약한 단언(현행 배송본이 실제로 썼던 것) — 반례를 통과시킨다(거짓 안전).
    assert not np.allclose(oof_column, full_column)

    # legacy 강도 단언 — 반례를 잡는다(6행이 전 구간과 완전히 같다).
    differing_rows = np.count_nonzero(np.abs(oof_column - full_column) > 1e-12)
    assert differing_rows == 24
    assert differing_rows != len(oof_column)


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
