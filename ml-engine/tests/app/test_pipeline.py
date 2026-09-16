"""RED — `ml_engine.app.pipeline`(D-5E-1 조립 근 실물). fake trainer(빠른 결정적
결과) 1건 + 실 `LightGbmTrainer` 1건(scope.md 「구현 순서」 12) — dataset 디렉터리
(`manifest.json`·`rows.jsonl`·`settlements.jsonl`) → job → `PipelineOutcome` → 파일
둘 → refs checksum 재계산까지 전 구간."""

from __future__ import annotations

import hashlib
import json
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest
from google.protobuf import json_format

from ml_engine.app.pipeline import build_training_pipeline
from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.evaluation import EvaluationPolicy
from ml_engine.training.booster import BoosterLike, LightGbmTrainer, TrainerFailed
from ml_engine.training.holdout import HoldoutCancelled
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


# ---- M-2(verifier r1) — 취소 경계 넷을 각각 독립적으로 확인한다. 셋 중 하나를
# 지워도(또는 넷 중 하나를 지워도) 정확히 그 경계에 대응하는 아래 test 가 붉어져야
# 한다(이전에는 하나만 살아도 전체가 초록이었다 — 변이 생존 실측). ----


class _CancelAfterNChecks:
    """`is_cancelled()`가 N+1 번째 호출부터 True 를 낸다 — 그 앞의 경계는 전부
    통과시키고 정확히 경계 N+1 에서 취소가 걸리는 것처럼 흉내 낸다."""

    def __init__(self, trigger_after: int) -> None:
        self._count = 0
        self._trigger_after = trigger_after

    def is_cancelled(self) -> bool:
        self._count += 1
        return self._count > self._trigger_after

    def cancel(self) -> None:  # pragma: no cover — 인터페이스 호환용, 이 test 는 안 씀
        self._trigger_after = 0


def _pipeline_with_counters(
    monkeypatch: pytest.MonkeyPatch, out_dir: Path, trainer: object
):  # type: ignore[no-untyped-def]
    """`ml_engine.app.pipeline`이 지역 이름으로 import 한 네 함수(`train_award_rate_gbm`·
    `write_artifact`·`run_holdout`·`write_artifact_files`)를 호출 횟수 세는 wrapper 로
    바꿔치기한다 — 어느 경계까지 실제로 진행됐는지 부작용(호출 여부)으로 관측한다."""
    import ml_engine.app.pipeline as pipeline_module

    calls = {
        "train": 0,
        "write_artifact": 0,
        "run_holdout": 0,
        "write_artifact_files": 0,
    }

    original_train = pipeline_module.train_award_rate_gbm

    def counting_train(*args: object, **kwargs: object) -> object:
        calls["train"] += 1
        return original_train(*args, **kwargs)  # type: ignore[misc]

    original_write_artifact = pipeline_module.write_artifact

    def counting_write_artifact(*args: object, **kwargs: object) -> object:
        calls["write_artifact"] += 1
        return original_write_artifact(*args, **kwargs)  # type: ignore[misc]

    original_run_holdout = pipeline_module.run_holdout

    def counting_run_holdout(*args: object, **kwargs: object) -> object:
        calls["run_holdout"] += 1
        return original_run_holdout(*args, **kwargs)  # type: ignore[misc]

    original_write_artifact_files = pipeline_module.write_artifact_files

    def counting_write_artifact_files(*args: object, **kwargs: object) -> object:
        calls["write_artifact_files"] += 1
        return original_write_artifact_files(*args, **kwargs)  # type: ignore[misc]

    monkeypatch.setattr(pipeline_module, "train_award_rate_gbm", counting_train)  # type: ignore[attr-defined]
    monkeypatch.setattr(pipeline_module, "write_artifact", counting_write_artifact)  # type: ignore[attr-defined]
    monkeypatch.setattr(pipeline_module, "run_holdout", counting_run_holdout)  # type: ignore[attr-defined]
    monkeypatch.setattr(  # type: ignore[attr-defined]
        pipeline_module, "write_artifact_files", counting_write_artifact_files
    )

    pipeline = build_training_pipeline(
        _spec(),
        training_policy=_training_policy(),
        evaluation_policy=_evaluation_policy(),
        maturity_window_days=7,
        trainer=trainer,
        code_version=CodeVersion("sha-boundary-test"),
        artifact_out_dir=out_dir,
    )
    return pipeline, calls


def test_cancel_boundary_1_after_load_prevents_training(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()
    pipeline, calls = _pipeline_with_counters(monkeypatch, out_dir, _FakeTrainer())

    outcome = pipeline.run(
        _dataset_ref(dataset_dir), _CancelAfterNChecks(trigger_after=0)
    )  # type: ignore[arg-type]

    assert isinstance(outcome, PipelineCancelled)
    assert calls == {
        "train": 0,
        "write_artifact": 0,
        "run_holdout": 0,
        "write_artifact_files": 0,
    }
    assert list(out_dir.iterdir()) == []


def test_cancel_boundary_2_after_train_prevents_artifact_write(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()
    pipeline, calls = _pipeline_with_counters(monkeypatch, out_dir, _FakeTrainer())

    outcome = pipeline.run(
        _dataset_ref(dataset_dir), _CancelAfterNChecks(trigger_after=1)
    )  # type: ignore[arg-type]

    assert isinstance(outcome, PipelineCancelled)
    assert calls["train"] == 1
    assert calls["write_artifact"] == 0
    assert calls["run_holdout"] == 0
    assert calls["write_artifact_files"] == 0
    assert list(out_dir.iterdir()) == []


def test_cancel_boundary_3_after_artifact_write_prevents_holdout(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()
    pipeline, calls = _pipeline_with_counters(monkeypatch, out_dir, _FakeTrainer())

    outcome = pipeline.run(
        _dataset_ref(dataset_dir), _CancelAfterNChecks(trigger_after=2)
    )  # type: ignore[arg-type]

    assert isinstance(outcome, PipelineCancelled)
    assert calls["write_artifact"] == 1
    assert calls["run_holdout"] == 0
    assert calls["write_artifact_files"] == 0
    assert list(out_dir.iterdir()) == []


def test_cancel_boundary_4_after_holdout_prevents_file_write(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    """M-2 — 신설 네 번째 경계(holdout 뒤·`write_artifact_files` 앞). 도입 전에는
    이 경계가 없어 holdout 이 끝나면 취소와 무관하게 파일이 항상 쓰였다."""
    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()
    pipeline, calls = _pipeline_with_counters(monkeypatch, out_dir, _FakeTrainer())

    outcome = pipeline.run(
        _dataset_ref(dataset_dir), _CancelAfterNChecks(trigger_after=3)
    )  # type: ignore[arg-type]

    assert isinstance(outcome, PipelineCancelled)
    assert calls["run_holdout"] == 1
    assert calls["write_artifact_files"] == 0
    assert list(out_dir.iterdir()) == []


def test_never_cancelled_reaches_all_four_stages_and_writes_files(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    """대조군 — 취소가 전혀 없으면 넷 다 정확히 1회씩 불리고 파일이 쓰인다."""
    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()
    pipeline, calls = _pipeline_with_counters(monkeypatch, out_dir, _FakeTrainer())

    outcome = pipeline.run(_dataset_ref(dataset_dir), CancelToken())

    assert isinstance(outcome, PipelineOutcome), outcome
    assert calls == {
        "train": 1,
        "write_artifact": 1,
        "run_holdout": 1,
        "write_artifact_files": 1,
    }
    assert len(list(out_dir.iterdir())) == 1


# ---- M5/5E-3 D-5E3-4 — `HoldoutCancelled`(창 루프 도중 취소, D-5E3-3)를
# `PipelineCancelled`로만 옮긴다. 창 루프 내부 정밀도(학습 전 확인·완료 창 수)는
# `tests/training/test_holdout.py`가 mutation 검증까지 마쳤다 — 여기서는 pipeline
# 층의 배선(같은 `cancel_token.is_cancelled`가 `should_stop`으로 전달되는가)과
# 매핑(우회 (4): `PipelineFailed`로 위장하지 않는가)만 정밀하게 확인한다. ----


def test_run_holdout_receives_cancel_tokens_is_cancelled_as_should_stop(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    """`_run_holdout`이 `run_holdout`을 부를 때 `should_stop=`이 **그 실행에 쓰인
    같은 `cancel_token`**의 `is_cancelled`여야 한다 — 다른 토큰이나 항상-거짓
    스텁을 넘기면 창 루프 도중 취소가 전달되지 않는다."""
    import ml_engine.app.pipeline as pipeline_module

    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()

    captured_kwargs: dict[str, object] = {}
    original_run_holdout = pipeline_module.run_holdout

    def capturing_run_holdout(*args: object, **kwargs: object) -> object:
        captured_kwargs.update(kwargs)
        return original_run_holdout(*args, **kwargs)  # type: ignore[misc]

    monkeypatch.setattr(pipeline_module, "run_holdout", capturing_run_holdout)  # type: ignore[attr-defined]

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
    outcome = pipeline.run(_dataset_ref(dataset_dir), token)
    assert isinstance(outcome, PipelineOutcome), outcome

    assert "should_stop" in captured_kwargs
    should_stop = captured_kwargs["should_stop"]
    assert callable(should_stop)
    assert should_stop() is False  # type: ignore[operator]
    token.cancel()
    assert should_stop() is True  # type: ignore[operator]  # 같은 토큰에 바인딩됐다


def test_holdout_cancelled_mid_windows_maps_to_pipeline_cancelled_with_no_artifact_files(
    monkeypatch: pytest.MonkeyPatch, tmp_path: Path
) -> None:
    """D-5E3-4 — `run_holdout`이 창 루프 도중 `HoldoutCancelled`를 내면(2 창 중 1
    창만 완료) pipeline 은 `PipelineFailed`로 위장하지 않고 `PipelineCancelled`만
    낸다(우회 (4)). artifact 디렉터리에는 아무 파일도 쓰이지 않는다(부분 결과
    없음, D-2D-7)."""
    import ml_engine.app.pipeline as pipeline_module

    dataset_dir = tmp_path / "dataset"
    dataset_dir.mkdir()
    _write_dataset(dataset_dir, n=6)
    out_dir = tmp_path / "artifacts"
    out_dir.mkdir()

    def fake_run_holdout(*args: object, **kwargs: object) -> HoldoutCancelled:
        assert "should_stop" in kwargs  # 배선 자체는 다른 test 가 이미 확인
        return HoldoutCancelled(completed_windows=1)

    monkeypatch.setattr(pipeline_module, "run_holdout", fake_run_holdout)  # type: ignore[attr-defined]

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

    assert isinstance(outcome, PipelineCancelled)
    assert list(out_dir.iterdir()) == []
