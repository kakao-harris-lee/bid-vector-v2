"""Reuse: bid-vector/app/services/ml_training/award_rate_gbm.py@ed4b06c

`ml_engine.training.spec` — 코드 선언 `TrainingSpec`(D-5C-2). 하이퍼파라미터는 정책 YAML이
아니라 여기 코드 상수(`TRAINING_SPECS` 등록표)로 선언한다 — 값이 artifact 바이트에
그대로 박히므로, env로 튜닝 가능하게 하면 같은 데이터셋의 두 실행이 다른 아티팩트를 내고
재현성이 깨진다(legacy `ml_training/constants.py` 사유 계승, 조사 01 §8-4·§11-10).

legacy `LIGHTGBM_PARAMS`(13키, dict[str, Any])·`BOOSTING_ROUNDS`·`DEFAULT_ENCODING_FOLDS`·
`DEFAULT_TRAINING_SEED`·`MIN_RESIDUAL_STD`를 값 무변경으로 이식하되(D-5C-2), 약한 타입
경계(`dict[str, Any]`)를 없애기 위해 `LightGbmHyperparameters`(frozen dataclass, 필드
13개 고정)로 승격했다 — `TrainerLike.train`의 `params` 인자가 이 타입을 받는다(설계 검토
(1) 「파라미터 단일 출처」).
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from types import MappingProxyType


@dataclass(frozen=True)
class LightGbmHyperparameters:
    """legacy `LIGHTGBM_PARAMS`(`award_rate_gbm.py:93-106`)의 타입 있는 승격판. `seed`는
    여기 없다 — `TrainerLike.train`이 별도 인자로 받는다(폴드마다 같은 seed, 값은 같지만
    자리를 나눈 것은 legacy `{**LIGHTGBM_PARAMS, "seed": seed}` 조립 관례를 반영한다)."""

    objective: str
    metric: str
    learning_rate: float
    num_leaves: int
    min_data_in_leaf: int
    feature_fraction: float
    bagging_fraction: float
    bagging_freq: int
    lambda_l2: float
    verbosity: int
    deterministic: bool
    force_row_wise: bool
    num_threads: int


@dataclass(frozen=True)
class TrainingSpec:
    """versioned 학습 하이퍼파라미터 — 값은 `reports/evidence/m5/5c/policy-values.md`
    §2와 대조된다. `version`이 2C `StartTrainingRequest.training_spec_version`의 참조다."""

    version: str
    hyperparameters: LightGbmHyperparameters
    num_boost_round: int
    encoding_folds: int
    seed: int
    min_residual_std: float

    def __post_init__(self) -> None:
        if self.encoding_folds < 2:
            raise ValueError(
                f"encoding_folds 는 2 이상이어야 합니다(OOF 정의 불가): {self.encoding_folds}"
            )
        if self.num_boost_round < 1:
            raise ValueError(
                f"num_boost_round 는 1 이상이어야 합니다: {self.num_boost_round}"
            )
        if self.hyperparameters.num_threads < 1:
            raise ValueError(
                f"num_threads 는 1 이상이어야 합니다: {self.hyperparameters.num_threads}"
            )
        if self.min_residual_std <= 0:
            raise ValueError(
                f"min_residual_std 는 양수여야 합니다: {self.min_residual_std}"
            )


@dataclass(frozen=True)
class UnsupportedTrainingSpec:
    """미지 `training_spec_version` — 예외가 아니라 결과 타입(5E 가 `ApplicationFailure
    (UNSUPPORTED_TRAINING_SPEC)`로 옮긴다, D-5C-2)."""

    version: str


# 출하 값 — legacy `award_rate_gbm.py:93-115`(D-5C-2, 값 무변경 이식). 승인:
# `reports/evidence/m5/5c/policy-values.md` §2.
_AWARD_RATE_GBM_TRAINING_V1 = TrainingSpec(
    version="award-rate-gbm-training-v1",
    hyperparameters=LightGbmHyperparameters(
        objective="regression",
        metric="rmse",
        learning_rate=0.05,
        num_leaves=31,
        min_data_in_leaf=40,
        feature_fraction=0.9,
        bagging_fraction=0.9,
        bagging_freq=1,
        lambda_l2=1.0,
        verbosity=-1,
        deterministic=True,
        force_row_wise=True,
        num_threads=4,
    ),
    num_boost_round=400,
    encoding_folds=5,
    seed=20260812,
    min_residual_std=0.002,
)

TRAINING_SPECS: MappingProxyType[str, TrainingSpec] = MappingProxyType(
    {_AWARD_RATE_GBM_TRAINING_V1.version: _AWARD_RATE_GBM_TRAINING_V1}
)


def resolve_training_spec(version: str) -> TrainingSpec | UnsupportedTrainingSpec:
    """등록표 조회 — 미지 version 은 예외가 아니라 `UnsupportedTrainingSpec`."""
    spec = TRAINING_SPECS.get(version)
    return spec if spec is not None else UnsupportedTrainingSpec(version)


def spec_checksum(spec: TrainingSpec) -> str:
    """sha256 hex(소문자 64자) — 5B `features/manifest.py::canonical_json`과 같은 규칙
    (키 정렬·구분자 `(",", ":")`). `TrainingSpec`의 값은 전부 코드 상수(유한 리터럴)라
    `CanonicalizationRejected` 갈래가 성립하지 않는다 — 결과 타입이 아니라 `str`을 낸다."""
    hyperparameters = spec.hyperparameters
    payload = json.dumps(
        {
            "version": spec.version,
            "hyperparameters": {
                "objective": hyperparameters.objective,
                "metric": hyperparameters.metric,
                "learning_rate": hyperparameters.learning_rate,
                "num_leaves": hyperparameters.num_leaves,
                "min_data_in_leaf": hyperparameters.min_data_in_leaf,
                "feature_fraction": hyperparameters.feature_fraction,
                "bagging_fraction": hyperparameters.bagging_fraction,
                "bagging_freq": hyperparameters.bagging_freq,
                "lambda_l2": hyperparameters.lambda_l2,
                "verbosity": hyperparameters.verbosity,
                "deterministic": hyperparameters.deterministic,
                "force_row_wise": hyperparameters.force_row_wise,
                "num_threads": hyperparameters.num_threads,
            },
            "num_boost_round": spec.num_boost_round,
            "encoding_folds": spec.encoding_folds,
            "seed": spec.seed,
            "min_residual_std": spec.min_residual_std,
        },
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()
