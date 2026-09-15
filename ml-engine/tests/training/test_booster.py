"""RED — `ml_engine.training.booster`(scope ⑥). `TrainerLike`는 test fake 주입 자리
(설계 검토 (2b)). 실 LightGBM 1건은 `test_train_artifact.py`의 재현성 test 와 겸한다."""

from __future__ import annotations

import numpy as np

from ml_engine.training.booster import BoosterLike, LightGbmTrainer, booster_to_text
from ml_engine.training.spec import LightGbmHyperparameters

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


def test_light_gbm_trainer_trains_and_predicts() -> None:
    trainer = LightGbmTrainer()
    matrix = np.array([[1.0], [2.0], [3.0], [4.0]])
    labels = np.array([0.1, 0.2, 0.3, 0.4])
    result = trainer.train(matrix, labels, ("x",), (), _HYPERPARAMETERS, 1, 10)
    assert isinstance(result, BoosterLike)
    predictions = result.predict(matrix)
    assert predictions.shape == (4,)
    assert list(result.feature_name()) == ["x"]


def test_booster_to_text_returns_string_not_pickle() -> None:
    trainer = LightGbmTrainer()
    matrix = np.array([[1.0], [2.0], [3.0], [4.0]])
    labels = np.array([0.1, 0.2, 0.3, 0.4])
    booster = trainer.train(matrix, labels, ("x",), (), _HYPERPARAMETERS, 1, 5)
    assert isinstance(booster, BoosterLike)
    text = booster_to_text(booster)
    assert isinstance(text, str)
    assert len(text) > 0


class _FakeBooster:
    def __init__(self, prediction_value: float, feature_names: list[str]) -> None:
        self._prediction_value = prediction_value
        self._feature_names = feature_names

    def predict(self, matrix: np.ndarray) -> np.ndarray:
        return np.full(matrix.shape[0], self._prediction_value)

    def model_to_string(self) -> str:
        return "fake-booster"

    def feature_name(self) -> list[str]:
        return self._feature_names


class _FakeTrainer:
    """설계 검토 (2b) — `TrainerLike` 주입 자리에 넣는 test fake."""

    def __init__(self, prediction_value: float, feature_names: list[str]) -> None:
        self._prediction_value = prediction_value
        self._feature_names = feature_names

    def train(
        self, matrix, labels, feature_names, categorical_indices, params, seed, rounds
    ):
        return _FakeBooster(self._prediction_value, self._feature_names)


def test_fake_trainer_can_return_non_finite_predictions() -> None:
    """우회 후보 (7)·(18) — fake trainer 가 NaN 예측을 낼 수 있다는 전제(호출부가 잡아야
    한다는 계약을 여기서 성립만 확인, 실제 방어는 encoding_oof/artifact_writer test)."""
    fake = _FakeTrainer(float("nan"), ["x"])
    booster = fake.train(None, None, ("x",), (), _HYPERPARAMETERS, 1, 1)
    predictions = booster.predict(np.zeros((3, 1)))
    assert not np.all(np.isfinite(predictions))
