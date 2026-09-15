"""RED — `ml_engine.app.pipeline`(D-5E-1 조립 근 실물). fake trainer(빠른 결정적
결과) 1건 + 실 `LightGbmTrainer` 1건(scope.md 「구현 순서」 12) — dataset 디렉터리
(`manifest.json`·`rows.jsonl`·`settlements.jsonl`) → job → `PipelineOutcome` → 파일
둘 → refs checksum 재계산까지 전 구간."""

from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

from google.protobuf import json_format

from ml_engine.app.pipeline import build_training_pipeline
from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.evaluation import EvaluationPolicy
from ml_engine.training.booster import BoosterLike, LightGbmTrainer, TrainerFailed
from ml_engine.training.jobs.pipeline import (
    CancelToken,
    DatasetRefInput,
    PipelineCancelled,
    PipelineFailed,
    PipelineOutcome,
)
from ml_engine.training.policy import TrainingPolicy
from ml_engine.training.spec import LightGbmHyperparameters, TrainingSpec
from ml_engine.training.train import CodeVersion

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


def _spec() -> TrainingSpec:
    return TrainingSpec(
        version="test-app-pipeline-v1",
        hyperparameters=_HYPERPARAMETERS,
        num_boost_round=3,
        encoding_folds=2,
        seed=20260812,
        min_residual_std=0.002,
    )


def _training_policy() -> TrainingPolicy:
    return TrainingPolicy(version="test-v1", min_training_rows=4)


def _evaluation_policy() -> EvaluationPolicy:
    return EvaluationPolicy(
        version="test",
        paired_t_threshold=2.58,
        gate_baseline="category_x_band",
        gate_model="gbm_all_strata",
        gate_stratum="clean-base",
        maturity_threshold=0.70,
        min_evaluation_rows=2,
        max_origins=5,
        agency_baseline_min_count=2,
        stability_seeds=(20260812, 1),
        amount_band_edges=(1e8, 5e8, 1e9, 5e9),
        segment_axes=("category", "amount_band"),
    )


def _feature_inputs_json(*, category: str, agency: str) -> dict[str, object]:
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
    return json_format.MessageToDict(inputs)


def _write_dataset(directory: Path, *, n: int, dataset_id: str = "ds-app-1") -> None:
    rows = []
    for i in range(n):
        category = "civil" if i % 2 == 0 else "it"
        agency = f"agency-{i % 3}"
        opened_at = datetime(2026, 1, 1, tzinfo=UTC) + timedelta(days=i)
        rows.append(
            {
                "feature_inputs": _feature_inputs_json(
                    category=category, agency=agency
                ),
                "label": 0.5 + 0.001 * (i % 10),
                "opened_at": opened_at.isoformat(),
                "stratum": "clean-base",
            }
        )
    rows_bytes = "\n".join(json.dumps(row) for row in rows).encode("utf-8")

    settlements_bytes = b""

    manifest = {
        "dataset_id": dataset_id,
        "sample_scope": "feed-origin-only",
        "feed_origin_only": True,
        "row_count": n,
        "rows_checksum": hashlib.sha256(rows_bytes).hexdigest(),
        "opened_at_first": datetime(2026, 1, 1, tzinfo=UTC).isoformat(),
        "opened_at_last": (
            datetime(2026, 1, 1, tzinfo=UTC) + timedelta(days=n)
        ).isoformat(),
        "feature_schema_version": "award-rate-features-v2",
        "settlements_checksum": hashlib.sha256(settlements_bytes).hexdigest(),
    }
    manifest_bytes = json.dumps(manifest).encode("utf-8")

    (directory / "manifest.json").write_bytes(manifest_bytes)
    (directory / "rows.jsonl").write_bytes(rows_bytes)
    (directory / "settlements.jsonl").write_bytes(settlements_bytes)


def _dataset_ref(directory: Path, dataset_id: str = "ds-app-1") -> DatasetRefInput:
    manifest_bytes = (directory / "manifest.json").read_bytes()
    return DatasetRefInput(
        uri=f"file://{directory}",
        manifest_checksum=hashlib.sha256(manifest_bytes).hexdigest(),
        dataset_id=dataset_id,
    )


class _FakeBooster:
    def __init__(self, feature_names: list[str]) -> None:
        self._feature_names = feature_names

    def predict(self, matrix):  # type: ignore[no-untyped-def]
        import numpy as np

        return np.zeros(matrix.shape[0])

    def model_to_string(self) -> str:
        return "fake-booster-text"

    def feature_name(self) -> list[str]:
        return self._feature_names


class _FakeTrainer:
    """`train_award_rate_gbm`이 요구하는 `TrainerLike` — 실 LightGBM 없이 파이프라인
    배선(파일 I/O·checksum·refs)만 빠르게 확인한다."""

    def train(
        self,
        matrix,  # type: ignore[no-untyped-def]
        labels,  # type: ignore[no-untyped-def]
        feature_names,  # type: ignore[no-untyped-def]
        categorical_indices,  # type: ignore[no-untyped-def]
        params,  # type: ignore[no-untyped-def]
        seed,  # type: ignore[no-untyped-def]
        rounds,  # type: ignore[no-untyped-def]
    ) -> BoosterLike | TrainerFailed:
        return _FakeBooster(list(feature_names))


def test_pipeline_with_fake_trainer_writes_files_and_returns_matching_refs(
    tmp_path: Path,
) -> None:
    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()

    pipeline = build_training_pipeline(
        _spec(),
        training_policy=_training_policy(),
        evaluation_policy=_evaluation_policy(),
        maturity_window_days=7,
        trainer=_FakeTrainer(),
        code_version=CodeVersion("sha-test-1"),
        artifact_out_dir=out_dir,
    )
    outcome = pipeline.run(_dataset_ref(dataset_dir), CancelToken())
    assert isinstance(outcome, PipelineOutcome), outcome

    artifact_path = Path(outcome.artifact.uri.removeprefix("file://"))
    report_path = Path(outcome.evaluation.uri.removeprefix("file://"))
    assert artifact_path.is_file()
    assert report_path.is_file()

    actual_artifact_checksum = hashlib.sha256(artifact_path.read_bytes()).hexdigest()
    assert actual_artifact_checksum == outcome.artifact.artifact_checksum

    actual_report_checksum = hashlib.sha256(report_path.read_bytes()).hexdigest()
    assert actual_report_checksum == outcome.evaluation.checksum

    assert outcome.artifact.dataset_id == "ds-app-1"
    assert outcome.artifact.manifest_schema_version == "artifact-manifest-v1"
    assert outcome.evaluation.report_schema_version == "evaluation-report-v1"


def test_pipeline_cancelled_before_training_returns_pipeline_cancelled(
    tmp_path: Path,
) -> None:
    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()

    pipeline = build_training_pipeline(
        _spec(),
        training_policy=_training_policy(),
        evaluation_policy=_evaluation_policy(),
        maturity_window_days=7,
        trainer=_FakeTrainer(),
        code_version=CodeVersion("sha-test-1"),
        artifact_out_dir=out_dir,
    )
    token = CancelToken()
    token.cancel()
    outcome = pipeline.run(_dataset_ref(dataset_dir), token)
    assert isinstance(outcome, PipelineCancelled)
    assert list(out_dir.iterdir()) == []


def test_pipeline_dataset_unreadable_without_settlements_file(tmp_path: Path) -> None:
    """D-5E-4 우회 (7) — `settlements.jsonl` 자체가 없으면 job 실패
    (`DATASET_UNREADABLE`, `SETTLEMENTS_ABSENT`)."""
    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    (dataset_dir / "settlements.jsonl").unlink()
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()

    pipeline = build_training_pipeline(
        _spec(),
        training_policy=_training_policy(),
        evaluation_policy=_evaluation_policy(),
        maturity_window_days=7,
        trainer=_FakeTrainer(),
        code_version=CodeVersion("sha-test-1"),
        artifact_out_dir=out_dir,
    )
    outcome = pipeline.run(_dataset_ref(dataset_dir), CancelToken())
    assert isinstance(outcome, PipelineFailed)


def test_pipeline_with_real_lightgbm_trainer(tmp_path: Path) -> None:
    """scope.md 「구현 순서」 12 — 실 LightGBM 1건."""
    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=12)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()

    pipeline = build_training_pipeline(
        _spec(),
        training_policy=_training_policy(),
        evaluation_policy=_evaluation_policy(),
        maturity_window_days=7,
        trainer=LightGbmTrainer(),
        code_version=CodeVersion("sha-test-real"),
        artifact_out_dir=out_dir,
    )
    outcome = pipeline.run(_dataset_ref(dataset_dir), CancelToken())
    assert isinstance(outcome, PipelineOutcome), outcome
    artifact_path = Path(outcome.artifact.uri.removeprefix("file://"))
    assert artifact_path.is_file()
