"""RED — `ml_engine.training.train`·`ml_engine.training.artifact_writer`(scope ⑥⑧⑨⑩).
`TrainingPolicy`·`TrainingSpec`는 이 test 에서 직접 구성한다(컨벤션상 허용 — 정책 YAML을
test 용으로 낮추지 않는다). 실 LightGBM 재현성 test 1건 포함(`num_threads=1` 고정,
`libomp` 로컬 확인 — commands.md 참조)."""

from __future__ import annotations

import json
from datetime import UTC, datetime, timedelta

import pytest

from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.features import NameMismatch
from ml_engine.training.artifact_writer import ArtifactBytes, write_artifact
from ml_engine.training.booster import LightGbmTrainer, TrainerFailed
from ml_engine.training.dataset import DatasetManifestV1, LoadedDataset, RawTrainingRow
from ml_engine.training.policy import TrainingPolicy
from ml_engine.training.release import ReleaseInputs
from ml_engine.training.spec import LightGbmHyperparameters, TrainingSpec
from ml_engine.training.train import (
    CodeVersion,
    TrainedArtifact,
    TrainingRejected,
    TrainingRejectionReason,
    train_award_rate_gbm,
)

_REAL_HYPERPARAMETERS = LightGbmHyperparameters(
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


def _feature_inputs(*, category: str, agency: str) -> features_pb2.FeatureInputs:
    inputs = features_pb2.FeatureInputs()
    inputs.base_amount.value.amount_won = 100_000_000
    inputs.base_amount.value.currency = common_pb2.CURRENCY_KRW
    inputs.base_amount.value.basis = common_pb2.BASIS_BASE_AMOUNT
    inputs.base_amount.value.provenance = common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED
    inputs.category_code.value = category
    inputs.agency_id.value = agency
    inputs.base_amount_provenance_label.value = (
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN
    )
    return inputs


def _synthetic_rows(n: int) -> tuple[RawTrainingRow, ...]:
    rows = []
    for i in range(n):
        category = "civil" if i % 2 == 0 else "it"
        agency = f"agency-{i % 4}"
        label = 0.5 + 0.001 * (i % 10)
        rows.append(
            RawTrainingRow(
                feature_inputs=_feature_inputs(category=category, agency=agency),
                label_value=label,
                opened_at=datetime(2026, 1, 1, tzinfo=UTC) + timedelta(days=i),
                stratum="clean-base",
            )
        )
    return tuple(rows)


def _dataset(n: int) -> LoadedDataset:
    manifest = DatasetManifestV1(
        dataset_id="ds-repro",
        sample_scope="feed-origin-only",
        feed_origin_only=True,
        row_count=n,
        rows_checksum="0" * 64,
        opened_at_first=datetime(2026, 1, 1, tzinfo=UTC),
        opened_at_last=datetime(2026, 1, 1, tzinfo=UTC) + timedelta(days=n),
        feature_schema_version="award-rate-features-v2",
    )
    return LoadedDataset(manifest=manifest, raw_rows=_synthetic_rows(n))


def _small_spec(**overrides: object) -> TrainingSpec:
    base: dict[str, object] = dict(
        version="test-train-v1",
        hyperparameters=_REAL_HYPERPARAMETERS,
        num_boost_round=5,
        encoding_folds=3,
        seed=42,
        min_residual_std=0.002,
    )
    base.update(overrides)
    return TrainingSpec(**base)  # type: ignore[arg-type]


class _WrongNameBooster:
    def predict(self, matrix):
        import numpy as np

        return np.zeros(matrix.shape[0])

    def model_to_string(self) -> str:
        return "wrong-name-booster"

    def feature_name(self) -> list[str]:
        return ["not", "the", "right", "names", "!"]


class _WrongNameTrainer:
    def train(
        self, matrix, labels, feature_names, categorical_indices, params, seed, rounds
    ):
        return _WrongNameBooster()


class _AlwaysFailingTrainer:
    def train(
        self, matrix, labels, feature_names, categorical_indices, params, seed, rounds
    ):
        return TrainerFailed("always fails")


def test_train_award_rate_gbm_rejects_empty_dataset_as_all_rows_rejected() -> None:
    dataset = LoadedDataset(
        manifest=_dataset(1).manifest,
        raw_rows=(),
    )
    policy = TrainingPolicy(version="test-v1", min_training_rows=1)
    result = train_award_rate_gbm(
        dataset, _small_spec(), policy, LightGbmTrainer(), CodeVersion("sha-1")
    )
    assert isinstance(result, TrainingRejected)
    assert result.reason == TrainingRejectionReason.ALL_ROWS_REJECTED


def test_train_award_rate_gbm_insufficient_training_rows() -> None:
    dataset = _dataset(5)
    policy = TrainingPolicy(version="test-v1", min_training_rows=500)
    result = train_award_rate_gbm(
        dataset,
        _small_spec(encoding_folds=2),
        policy,
        LightGbmTrainer(),
        CodeVersion("sha-1"),
    )
    assert isinstance(result, TrainingRejected)
    assert result.reason == TrainingRejectionReason.INSUFFICIENT_TRAINING_ROWS


def test_train_award_rate_gbm_trainer_failure_propagates() -> None:
    dataset = _dataset(20)
    policy = TrainingPolicy(version="test-v1", min_training_rows=5)
    result = train_award_rate_gbm(
        dataset,
        _small_spec(encoding_folds=2),
        policy,
        _AlwaysFailingTrainer(),
        CodeVersion("sha-1"),
    )
    assert isinstance(result, TrainingRejected)
    assert result.reason == TrainingRejectionReason.TRAINER_ERROR


def _train_and_write(n: int = 40, min_training_rows: int = 5) -> ArtifactBytes:
    dataset = _dataset(n)
    policy = TrainingPolicy(version="test-v1", min_training_rows=min_training_rows)
    trained = train_award_rate_gbm(
        dataset, _small_spec(), policy, LightGbmTrainer(), CodeVersion("sha-abc123")
    )
    assert isinstance(trained, TrainedArtifact)
    release_inputs = ReleaseInputs(
        dataset_id=trained.dataset_id,
        training_spec_version=trained.training_spec_version,
        training_spec_checksum=trained.training_spec_checksum,
        seed=trained.reproducibility.seed,
        code_version=trained.code_version,
    )
    written = write_artifact(trained, release_inputs)
    assert isinstance(written, ArtifactBytes)
    return written


def test_train_and_write_artifact_succeeds_with_real_lightgbm() -> None:
    written = _train_and_write()
    assert written.sha256
    payload = json.loads(written.bytes)
    assert payload["training_row_count"] == 40


def test_train_and_write_artifact_reproducible_bytes() -> None:
    """M5 완료 조건 「같은 manifest/seed 입력이 재현 가능한 artifact」·D-5C-12 (a)."""
    first = _train_and_write()
    second = _train_and_write()
    assert first.bytes == second.bytes
    assert first.sha256 == second.sha256


_EXPECTED_TOP_LEVEL_FIELDS = (
    "manifest_schema_version",
    "release",
    "feature_manifest_checksum",
    "feature_names",
    "sample_scope",
    "residual_std",
    "training_row_count",
    "booster_model",
    "reproducibility",
    "training_spec_version",
    "training_spec_checksum",
    "training_policy_version",
    "feed_origin_only",
    "categories",
    "denominator_sources",
    "agency_encoding",
    "rejected_rows",
)
_EXPECTED_RELEASE_FIELDS = (
    "release_id",
    "feature_schema_version",
    "code_version",
    "dataset_id",
)
_EXPECTED_REPRODUCIBILITY_FIELDS = ("seed", "num_threads", "deterministic")


def test_write_artifact_field_set_matches_5d_scope_plus_5c1_additions() -> None:
    """5D scope ⑦ `ArtifactManifestV1` 필드 집합(문자열 tuple 고정) + 5C-1 추가 여섯 —
    D-5C-9(release 에 `artifact_checksum` 없음)."""
    written = _train_and_write()
    payload = json.loads(written.bytes)
    assert set(payload.keys()) == set(_EXPECTED_TOP_LEVEL_FIELDS)
    assert set(payload["release"].keys()) == set(_EXPECTED_RELEASE_FIELDS)
    assert "artifact_checksum" not in payload["release"]
    assert set(payload["reproducibility"].keys()) == set(
        _EXPECTED_REPRODUCIBILITY_FIELDS
    )


def test_write_artifact_sha256_matches_recomputed_hash() -> None:
    import hashlib

    written = _train_and_write()
    assert hashlib.sha256(written.bytes).hexdigest() == written.sha256


def test_write_artifact_rejects_feature_name_mismatch() -> None:
    dataset = _dataset(20)
    policy = TrainingPolicy(version="test-v1", min_training_rows=5)
    trained = train_award_rate_gbm(
        dataset,
        _small_spec(encoding_folds=2),
        policy,
        LightGbmTrainer(),
        CodeVersion("sha-1"),
    )
    assert isinstance(trained, TrainedArtifact)
    tampered = TrainedArtifact(
        booster=_WrongNameBooster(),
        feature_manifest=trained.feature_manifest,
        residual_std=trained.residual_std,
        training_row_count=trained.training_row_count,
        sample_scope=trained.sample_scope,
        feed_origin_only=trained.feed_origin_only,
        dataset_id=trained.dataset_id,
        code_version=trained.code_version,
        training_spec_version=trained.training_spec_version,
        training_spec_checksum=trained.training_spec_checksum,
        training_policy_version=trained.training_policy_version,
        reproducibility=trained.reproducibility,
        rejected_rows=trained.rejected_rows,
    )
    release_inputs = ReleaseInputs(
        dataset_id=tampered.dataset_id,
        training_spec_version=tampered.training_spec_version,
        training_spec_checksum=tampered.training_spec_checksum,
        seed=tampered.reproducibility.seed,
        code_version=tampered.code_version,
    )
    result = write_artifact(tampered, release_inputs)
    assert isinstance(result, NameMismatch)


def test_code_version_rejects_blank() -> None:
    with pytest.raises(ValueError):
        CodeVersion("   ")
    with pytest.raises(ValueError):
        CodeVersion("")
