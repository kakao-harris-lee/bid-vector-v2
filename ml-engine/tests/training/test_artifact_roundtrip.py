"""RED — `write_artifact` → `ml_engine.registry.artifact.load_artifact` 왕복.

계약 갱신 이력(2026-09-13) — 5D 가 `main`에 먼저 병합됐으므로 `OPEN-5C-ARTIFACT-ROUNDTRIP`
은 이 rebase 에서 5C-1 이 닫는다(나중 병합 쪽 규칙). 5D read model 이 5C-1 산출물을 실제로
읽는지 fake trainer 1건 + 실 LightGBM 1건으로 확인한다. `ml_engine.training` 이
`ml_engine.registry.artifact`를 import 하는 것은 이 test 파일 안에서만이다 — production
코드(`src/ml_engine/training/**`)는 5D 파일을 import 하지 않는다(scope.md 「레인 격리」,
D-5C-9 — 필드 집합은 문자열 tuple 로 test 에 고정해 대조한다). `lint-imports`(S-4)의
`root_packages = ["ml_engine"]` 스캔 대상은 `ml-engine/**` 소스 패키지 자체이고 `tests/`는
스캔 대상이 아니므로 이 import 는 계약 위반이 아니다(design-review 구현 지시 3 확인)."""

from __future__ import annotations

import json
from datetime import UTC, datetime, timedelta

import numpy as np

from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.registry.artifact import (
    ArtifactRejected,
    LoadedArtifact,
    ModelReleaseRef,
    load_artifact,
)
from ml_engine.training.artifact_writer import ArtifactBytes, write_artifact
from ml_engine.training.booster import BoosterLike, LightGbmTrainer, TrainerLike
from ml_engine.training.dataset import DatasetManifestV1, LoadedDataset, RawTrainingRow
from ml_engine.training.policy import TrainingPolicy
from ml_engine.training.spec import LightGbmHyperparameters, TrainingSpec
from ml_engine.training.train import CodeVersion, TrainedArtifact, train_award_rate_gbm

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

_EXTRA_5C1_FIELDS = (
    "training_spec_version",
    "training_spec_checksum",
    "training_policy_version",
    "feed_origin_only",
    "categories",
    "denominator_sources",
    "agency_encoding",
    "rejected_rows",
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
        dataset_id="ds-roundtrip",
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
        version="test-roundtrip-v1",
        hyperparameters=_REAL_HYPERPARAMETERS,
        num_boost_round=5,
        encoding_folds=3,
        seed=7,
        min_residual_std=0.002,
    )
    base.update(overrides)
    return TrainingSpec(**base)  # type: ignore[arg-type]


class _ConstantBooster:
    """test fake — `feature_name()`은 `train_award_rate_gbm`이 넘긴 schema 이름을
    그대로 돌려준다(5D `verify_feature_names` 정확 일치를 자연히 만족)."""

    def __init__(self, value: float, feature_names: tuple[str, ...]) -> None:
        self._value = value
        self._feature_names = feature_names

    def predict(self, matrix: np.ndarray) -> np.ndarray:
        return np.full(matrix.shape[0], self._value)

    def model_to_string(self) -> str:
        return "fake-booster-roundtrip"

    def feature_name(self) -> list[str]:
        return list(self._feature_names)


class _ConstantTrainer:
    def train(
        self,
        matrix: np.ndarray,
        labels: np.ndarray,
        feature_names: object,
        categorical_indices: object,
        params: object,
        seed: int,
        rounds: int,
    ) -> BoosterLike:
        return _ConstantBooster(0.5, tuple(feature_names))  # type: ignore[arg-type]


def _train_and_write(*, trainer: TrainerLike, n: int = 40) -> ArtifactBytes:
    dataset = _dataset(n)
    policy = TrainingPolicy(version="test-v1", min_training_rows=5)
    trained = train_award_rate_gbm(
        dataset, _small_spec(), policy, trainer, CodeVersion("sha-roundtrip")
    )
    assert isinstance(trained, TrainedArtifact)
    written = write_artifact(trained)
    assert isinstance(written, ArtifactBytes)
    return written


def _expected_ref(written: ArtifactBytes) -> ModelReleaseRef:
    payload = json.loads(written.bytes)
    return ModelReleaseRef(
        release_id=written.release.release_id,
        artifact_checksum=written.sha256,
        feature_schema_version=written.release.feature_schema_version,
        feature_manifest_checksum=payload["feature_manifest_checksum"],
    )


def test_write_artifact_roundtrips_through_5d_load_artifact_with_fake_trainer() -> None:
    written = _train_and_write(trainer=_ConstantTrainer())
    result = load_artifact(written.bytes, _expected_ref(written))
    assert isinstance(result, LoadedArtifact)


def test_write_artifact_roundtrips_through_5d_load_artifact_with_real_lightgbm() -> (
    None
):
    written = _train_and_write(trainer=LightGbmTrainer())
    result = load_artifact(written.bytes, _expected_ref(written))
    assert isinstance(result, LoadedArtifact)


def test_tampered_artifact_bytes_are_rejected_by_5d_load_artifact() -> None:
    written = _train_and_write(trainer=_ConstantTrainer())
    expected = _expected_ref(written)
    tampered = written.bytes + b" "  # 1바이트 변조
    result = load_artifact(tampered, expected)
    assert isinstance(result, ArtifactRejected)


def test_release_id_mismatch_is_rejected_by_5d_load_artifact() -> None:
    written = _train_and_write(trainer=_ConstantTrainer())
    expected = _expected_ref(written)
    wrong_release_id = ModelReleaseRef(
        release_id="not-" + expected.release_id,
        artifact_checksum=expected.artifact_checksum,
        feature_schema_version=expected.feature_schema_version,
        feature_manifest_checksum=expected.feature_manifest_checksum,
    )
    result = load_artifact(written.bytes, wrong_release_id)
    assert isinstance(result, ArtifactRejected)


def test_5c1_additional_fields_are_not_rejected_by_5d_read_model() -> None:
    """5C-1 이 5D scope ⑦ 기본 필드 밖에 여덟(`training_spec_version` 등, checklist.md
    알려진 제한 5(b))을 더 싣는다 — read model 이 알 수 없는 top-level 키를 거부하지
    않아야 이 slice 산출물을 읽을 수 있다."""
    written = _train_and_write(trainer=_ConstantTrainer())
    result = load_artifact(written.bytes, _expected_ref(written))
    assert isinstance(result, LoadedArtifact)
    payload = json.loads(written.bytes)
    for extra_field in _EXTRA_5C1_FIELDS:
        assert extra_field in payload


def test_loaded_artifact_carries_5c1_manifest_fields_correctly() -> None:
    """`feature_manifest_checksum`·`feature_names`·`sample_scope`·`reproducibility`가
    read model 을 통과해 그대로 나른다."""
    written = _train_and_write(trainer=_ConstantTrainer())
    result = load_artifact(written.bytes, _expected_ref(written))
    assert isinstance(result, LoadedArtifact)
    payload = json.loads(written.bytes)
    manifest = result.manifest
    assert manifest.feature_manifest_checksum == payload["feature_manifest_checksum"]
    assert manifest.feature_names == tuple(payload["feature_names"])
    assert manifest.sample_scope == payload["sample_scope"]
    reproducibility_payload = payload["reproducibility"]
    assert manifest.reproducibility.seed == reproducibility_payload["seed"]
    assert (
        manifest.reproducibility.num_threads == reproducibility_payload["num_threads"]
    )
    assert (
        manifest.reproducibility.deterministic
        == reproducibility_payload["deterministic"]
    )
