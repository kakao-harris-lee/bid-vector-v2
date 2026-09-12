"""Reuse: bid-vector/app/services/ml_training/award_rate_gbm.py@ed4b06c

`ml_engine.training.booster` — LightGBM 학습을 포트 뒤로 감춘다(legacy `_train_booster`,
scope ⑥). `lightgbm` import 는 **이 모듈에서만**(경계 유지 — legacy 지연 import 사유
계승: API/serving 프로세스가 학습 의존성을 로드하지 않게 한다). `TrainerLike`는 test
fake 주입 자리(설계 검토 (2b) — 고정 항목 「무엇을 주입하는가」: 모델 실물)다.

`lightgbm`의 구체 예외(`lightgbm.basic.LightGBMError`)만 이 모듈 안에서 잡아
`TrainerFailed`로 옮긴다 — 넓은 `except Exception`은 쓰지 않는다.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from typing import Protocol, cast, runtime_checkable

import numpy as np

from ml_engine.training.spec import LightGbmHyperparameters


@runtime_checkable
class BoosterLike(Protocol):
    """학습된 부스터의 최소 계약 — LightGBM `Booster`가 구조적으로 만족한다(덕 타이핑,
    `lightgbm` import 없이 이 모듈 밖에서도 타입을 참조할 수 있다)."""

    def predict(self, matrix: np.ndarray, /) -> np.ndarray: ...

    def model_to_string(self) -> str: ...

    def feature_name(self) -> list[str]: ...


@dataclass(frozen=True)
class TrainerFailed:
    """`lightgbm`의 구체 예외를 결과 타입으로 옮긴 것 — 예외로 새지 않는다."""

    detail: str


class TrainerLike(Protocol):
    """`params`는 `TrainingSpec.hyperparameters`에서만 온다(파라미터 산포 금지, 설계
    검토 (1))."""

    def train(
        self,
        matrix: np.ndarray,
        labels: np.ndarray,
        feature_names: Sequence[str],
        categorical_indices: Sequence[int],
        params: LightGbmHyperparameters,
        seed: int,
        rounds: int,
    ) -> BoosterLike | TrainerFailed: ...


class LightGbmTrainer:
    """실물 trainer — `lightgbm` import 는 `train` 안에서만 일어난다(legacy `:232` 지연
    import 계승)."""

    def train(
        self,
        matrix: np.ndarray,
        labels: np.ndarray,
        feature_names: Sequence[str],
        categorical_indices: Sequence[int],
        params: LightGbmHyperparameters,
        seed: int,
        rounds: int,
    ) -> BoosterLike | TrainerFailed:
        import lightgbm as lgb

        lightgbm_params = {
            "objective": params.objective,
            "metric": params.metric,
            "learning_rate": params.learning_rate,
            "num_leaves": params.num_leaves,
            "min_data_in_leaf": params.min_data_in_leaf,
            "feature_fraction": params.feature_fraction,
            "bagging_fraction": params.bagging_fraction,
            "bagging_freq": params.bagging_freq,
            "lambda_l2": params.lambda_l2,
            "verbosity": params.verbosity,
            "deterministic": params.deterministic,
            "force_row_wise": params.force_row_wise,
            "num_threads": params.num_threads,
            "seed": seed,
        }
        try:
            dataset = lgb.Dataset(
                matrix,
                label=labels,
                feature_name=list(feature_names),
                categorical_feature=list(categorical_indices),
                free_raw_data=False,
            )
            booster = lgb.train(lightgbm_params, dataset, num_boost_round=rounds)
        except lgb.basic.LightGBMError as exc:
            return TrainerFailed(str(exc))
        # `lgb.Booster.predict`의 실제 stub 시그니처는 `str | Path | ndarray | ...`처럼
        # `BoosterLike.predict`보다 넓다(입력을 더 받는 초집합) — 우리는 항상 ndarray만
        # 넘기므로 런타임 계약은 성립하되, mypy strict 는 그 넓은 유니온이 프로토콜의
        # 좁은 반환/인자 타입과 정확히 일치하지 않는다고 본다. `cast`로 이 자리 하나만
        # 명시한다(전역 `[[tool.mypy.overrides]]` 추가는 금지 — 편집 규율).
        return cast(BoosterLike, booster)


def booster_to_text(booster: BoosterLike) -> str:
    """부스터 텍스트 직렬화(`model_to_string()`) — pickle 을 쓰지 않는다(legacy 사유:
    (a) 릴리스 아티팩트가 JSON 한 파일로 유지 (b) pickle 복원은 임의 코드 실행 표면)."""
    return str(booster.model_to_string())
