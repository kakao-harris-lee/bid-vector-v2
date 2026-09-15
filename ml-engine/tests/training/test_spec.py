"""RED — `ml_engine.training.spec`(D-5C-2). 값 무변경 이식 대조, 등록표 조회, checksum
결정성, 불변식(폴드≥2·라운드≥1·threads≥1·min_residual_std>0)."""

from __future__ import annotations

import dataclasses

import pytest

from ml_engine.training.spec import (
    TRAINING_SPECS,
    LightGbmHyperparameters,
    TrainingSpec,
    UnsupportedTrainingSpec,
    resolve_training_spec,
    spec_checksum,
)


def test_shipped_spec_matches_policy_values_md() -> None:
    """verifier r1 L-7 — 이 test 는 `policy-values.md` §2 표를 파싱하지 않는다. 표 값을
    이 파일에 하드코딩해 대조한다(사람이 표와 눈으로 대조 확인) — 이름이 시사하는 것보다
    약한 대조다(5A/5B 와 같은 관행). legacy `ed4b06c` `award_rate_gbm.py:93-115` 이식,
    D-5C-2."""
    spec = resolve_training_spec("award-rate-gbm-training-v1")
    assert isinstance(spec, TrainingSpec)
    hyperparameters = spec.hyperparameters
    assert hyperparameters.objective == "regression"
    assert hyperparameters.metric == "rmse"
    assert hyperparameters.learning_rate == 0.05
    assert hyperparameters.num_leaves == 31
    assert hyperparameters.min_data_in_leaf == 40
    assert hyperparameters.feature_fraction == 0.9
    assert hyperparameters.bagging_fraction == 0.9
    assert hyperparameters.bagging_freq == 1
    assert hyperparameters.lambda_l2 == 1.0
    assert hyperparameters.verbosity == -1
    assert hyperparameters.deterministic is True
    assert hyperparameters.force_row_wise is True
    assert hyperparameters.num_threads == 4
    assert spec.num_boost_round == 400
    assert spec.encoding_folds == 5
    assert spec.seed == 20260812
    assert spec.min_residual_std == 0.002


def test_resolve_training_spec_unknown_version_is_result_type_not_exception() -> None:
    result = resolve_training_spec("does-not-exist")
    assert isinstance(result, UnsupportedTrainingSpec)
    assert result.version == "does-not-exist"


def test_training_specs_registry_is_frozen_mapping() -> None:
    with pytest.raises(TypeError):
        TRAINING_SPECS["x"] = TRAINING_SPECS["award-rate-gbm-training-v1"]  # type: ignore[index]


def _hyperparameters(**overrides: object) -> LightGbmHyperparameters:
    base = dict(
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
    )
    base.update(overrides)
    return LightGbmHyperparameters(**base)  # type: ignore[arg-type]


@pytest.mark.parametrize(
    "kwargs",
    [
        {"encoding_folds": 1},
        {"num_boost_round": 0},
        {"min_residual_std": 0.0},
        {"min_residual_std": -0.1},
    ],
)
def test_training_spec_rejects_invalid_invariants(kwargs: dict[str, object]) -> None:
    base = dict(
        version="x",
        hyperparameters=_hyperparameters(),
        num_boost_round=400,
        encoding_folds=5,
        seed=1,
        min_residual_std=0.002,
    )
    base.update(kwargs)
    with pytest.raises(ValueError):
        TrainingSpec(**base)  # type: ignore[arg-type]


def test_training_spec_rejects_zero_threads() -> None:
    with pytest.raises(ValueError):
        TrainingSpec(
            version="x",
            hyperparameters=_hyperparameters(num_threads=0),
            num_boost_round=400,
            encoding_folds=5,
            seed=1,
            min_residual_std=0.002,
        )


def test_spec_checksum_is_deterministic_and_sensitive_to_values() -> None:
    spec = resolve_training_spec("award-rate-gbm-training-v1")
    assert isinstance(spec, TrainingSpec)
    checksum_a = spec_checksum(spec)
    checksum_b = spec_checksum(spec)
    assert checksum_a == checksum_b
    assert len(checksum_a) == 64
    changed = TrainingSpec(
        version=spec.version,
        hyperparameters=spec.hyperparameters,
        num_boost_round=spec.num_boost_round + 1,
        encoding_folds=spec.encoding_folds,
        seed=spec.seed,
        min_residual_std=spec.min_residual_std,
    )
    assert spec_checksum(changed) != checksum_a


def _mutate_hyperparameter_value(value: object) -> object:
    if isinstance(value, bool):
        return not value
    if isinstance(value, int):
        return value + 1
    if isinstance(value, float):
        return value + 1.0
    if isinstance(value, str):
        return value + "-mutated"
    raise TypeError(f"알 수 없는 하이퍼파라미터 값 타입: {type(value)!r}")


def test_spec_checksum_is_sensitive_to_every_hyperparameter_field() -> None:
    """code-reviewer PR #13 LOW-1 — 기존 test(위)는 `num_boost_round` 변경 하나만
    확인했다. `LightGbmHyperparameters` 13필드를 `dataclasses.fields`로 전수 순회해
    필드마다 값을 하나 바꾸면 checksum 이 달라짐을 확인한다 — `spec_checksum`의 손
    나열 payload 가 실제 필드 집합과 어긋나면(향후 필드 추가 뒤 갱신 누락 등) 이
    test 가 그 필드에서 정확히 실패한다."""
    spec = resolve_training_spec("award-rate-gbm-training-v1")
    assert isinstance(spec, TrainingSpec)
    baseline = spec_checksum(spec)

    for field in dataclasses.fields(LightGbmHyperparameters):
        original = getattr(spec.hyperparameters, field.name)
        mutated_hyperparameters = dataclasses.replace(
            spec.hyperparameters,
            **{field.name: _mutate_hyperparameter_value(original)},
        )
        mutated_spec = TrainingSpec(
            version=spec.version,
            hyperparameters=mutated_hyperparameters,
            num_boost_round=spec.num_boost_round,
            encoding_folds=spec.encoding_folds,
            seed=spec.seed,
            min_residual_std=spec.min_residual_std,
        )
        assert spec_checksum(mutated_spec) != baseline, (
            f"필드 {field.name!r} 변경이 spec_checksum 에 반영되지 않음"
        )
