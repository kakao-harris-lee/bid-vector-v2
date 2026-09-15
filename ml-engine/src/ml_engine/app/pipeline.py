"""`ml_engine.app.pipeline` — 조립 근(D-5E-1) `TrainingPipeline` 실물. `read_dataset_files`
→ `load_dataset` → `train_award_rate_gbm` → `write_artifact` → `build_weekly_maturity`
(K7) → `run_holdout` → `write_artifact_files`. 취소는 단계 경계 넷에서 확인한다(load
뒤·train 뒤·artifact 뒤·holdout 뒤, D-2D-7) — 다음 단계를 시작하지 않는 것으로 자원을
해제한다(계산이 이미 시작된 단계를 중간에 끊지는 않는다, 설계 검토 (1)).

이 모듈만 `serving`·`training`·`inference`·`adapters`를 한 자리에서 잇는다(어느 층도
혼자서는 이 넷을 다 import 할 수 없다 — 착수 조사)."""

from __future__ import annotations

import uuid
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

from ml_engine.adapters.artifact_files import (
    ArtifactWriteRejected,
    write_artifact_files,
)
from ml_engine.adapters.dataset_files import DatasetUnreadable, read_dataset_files
from ml_engine.evaluation import EvaluationPolicy, EvaluationReportV1, WeekMaturity
from ml_engine.evaluation.report import canonical_report_bytes, report_checksum
from ml_engine.features import CanonicalizationRejected
from ml_engine.inference.maturity import (
    MaturityWindow,
    Observed,
    SettlementObservation,
    build_weekly_maturity,
)
from ml_engine.training.artifact_writer import ArtifactBytes, write_artifact
from ml_engine.training.booster import TrainerLike
from ml_engine.training.dataset import DatasetReference, DatasetRejected, LoadedDataset
from ml_engine.training.dataset import load_dataset as _load_dataset
from ml_engine.training.holdout import HoldoutRejected, run_holdout
from ml_engine.training.jobs.pipeline import (
    ArtifactRefValue,
    CancelToken,
    DatasetRefInput,
    DetailCode,
    EvaluationRefValue,
    PipelineCancelled,
    PipelineFailed,
    PipelineOutcome,
    map_dataset_rejected,
    map_dataset_unreadable,
    map_holdout_rejected,
    map_training_rejected,
)
from ml_engine.training.jobs.state import JobFailureCode
from ml_engine.training.policy import TrainingPolicy
from ml_engine.training.spec import TrainingSpec
from ml_engine.training.train import (
    CodeVersion,
    TrainedArtifact,
    TrainingRejected,
    train_award_rate_gbm,
)

_MANIFEST_SCHEMA_VERSION = "artifact-manifest-v1"


def _week_maturity(window: MaturityWindow) -> WeekMaturity:
    if isinstance(window.maturity, Observed):
        opened_count = window.maturity.opened_count
        settled_count = window.maturity.settled_count
    else:
        opened_count = 0
        settled_count = 0
    return WeekMaturity(
        start=window.start,
        end=window.end,
        opened_count=opened_count,
        settled_count=settled_count,
    )


@dataclass(frozen=True)
class _PipelineDeps:
    spec: TrainingSpec
    training_policy: TrainingPolicy
    evaluation_policy: EvaluationPolicy
    maturity_window_days: int
    trainer: TrainerLike
    code_version: CodeVersion
    artifact_out_dir: Path


class _ConcreteTrainingPipeline:
    """`training.jobs.pipeline.TrainingPipeline` Protocol 을 만족한다(구조적 서브타입,
    명시적 상속 없음)."""

    def __init__(self, deps: _PipelineDeps) -> None:
        self._deps = deps

    def run(
        self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
    ) -> PipelineOutcome | PipelineFailed | PipelineCancelled:
        dataset = self._load(dataset_ref)
        if isinstance(dataset, PipelineFailed):
            return dataset
        if cancel_token.is_cancelled():
            return PipelineCancelled()

        trained = self._train(dataset)
        if isinstance(trained, PipelineFailed):
            return trained
        if cancel_token.is_cancelled():
            return PipelineCancelled()

        artifact = write_artifact(trained)
        if not isinstance(artifact, ArtifactBytes):
            return PipelineFailed(
                JobFailureCode.TRAINING_ERROR, DetailCode.ARTIFACT_WRITE_REJECTED
            )
        if cancel_token.is_cancelled():
            return PipelineCancelled()

        return self._evaluate_and_finalize(dataset, artifact)

    def _load(self, dataset_ref: DatasetRefInput) -> LoadedDataset | PipelineFailed:
        files = read_dataset_files(dataset_ref.uri)
        if isinstance(files, DatasetUnreadable):
            code, detail = map_dataset_unreadable(files.reason)
            return PipelineFailed(code, detail)
        if files.settlements_bytes is None:
            return PipelineFailed(
                JobFailureCode.DATASET_UNREADABLE, DetailCode.SETTLEMENTS_ABSENT
            )
        expected = DatasetReference(
            dataset_id=dataset_ref.dataset_id,
            manifest_checksum=dataset_ref.manifest_checksum,
        )
        loaded = _load_dataset(
            files.manifest_bytes, files.rows_bytes, expected, files.settlements_bytes
        )
        if isinstance(loaded, DatasetRejected):
            code, detail = map_dataset_rejected(loaded.reason)
            return PipelineFailed(code, detail)
        return loaded

    def _train(self, dataset: LoadedDataset) -> TrainedArtifact | PipelineFailed:
        trained = train_award_rate_gbm(
            dataset,
            self._deps.spec,
            self._deps.training_policy,
            self._deps.trainer,
            self._deps.code_version,
        )
        if isinstance(trained, TrainingRejected):
            code, detail = map_training_rejected(trained.reason)
            return PipelineFailed(code, detail)
        return trained

    def _run_holdout(
        self, dataset: LoadedDataset
    ) -> EvaluationReportV1 | PipelineFailed:
        observations = tuple(
            SettlementObservation(opened_at=row.opened_at, settled=row.settled)
            for row in dataset.settlement_rows
        )
        windows = build_weekly_maturity(
            observations, window_days=self._deps.maturity_window_days
        )
        maturities = tuple(_week_maturity(window) for window in windows)

        report = run_holdout(
            dataset,
            maturities,
            self._deps.spec,
            self._deps.training_policy,
            self._deps.evaluation_policy,
            self._deps.trainer,
            self._deps.code_version,
        )
        if isinstance(report, HoldoutRejected):
            code, detail = map_holdout_rejected(report.reason)
            return PipelineFailed(code, detail)
        return report

    def _write_outcome(
        self, artifact: ArtifactBytes, report: EvaluationReportV1
    ) -> PipelineOutcome | PipelineFailed:
        report_bytes = canonical_report_bytes(report)
        report_hash = report_checksum(report)
        if isinstance(report_bytes, CanonicalizationRejected) or isinstance(
            report_hash, CanonicalizationRejected
        ):
            return PipelineFailed(
                JobFailureCode.EVALUATION_ERROR,
                DetailCode.REPORT_CANONICALIZATION_REJECTED,
            )

        job_dir = self._deps.artifact_out_dir / str(uuid.uuid4())
        refs = write_artifact_files(job_dir, artifact.bytes, report_bytes)
        if isinstance(refs, ArtifactWriteRejected):
            return PipelineFailed(
                JobFailureCode.TRAINING_ERROR, DetailCode.OUTPUT_DIR_NOT_EMPTY
            )

        return PipelineOutcome(
            artifact=ArtifactRefValue(
                uri=refs.artifact_uri,
                release_id=artifact.release.release_id,
                artifact_checksum=artifact.sha256,
                feature_schema_version=artifact.release.feature_schema_version,
                code_version=artifact.release.code_version,
                dataset_id=artifact.release.dataset_id,
                manifest_schema_version=_MANIFEST_SCHEMA_VERSION,
            ),
            evaluation=EvaluationRefValue(
                uri=refs.report_uri,
                checksum=report_hash,
                report_schema_version=report.report_schema_version,
            ),
        )

    def _evaluate_and_finalize(
        self, dataset: LoadedDataset, artifact: ArtifactBytes
    ) -> PipelineOutcome | PipelineFailed:
        report = self._run_holdout(dataset)
        if isinstance(report, PipelineFailed):
            return report
        return self._write_outcome(artifact, report)


def build_training_pipeline(
    spec: TrainingSpec,
    *,
    training_policy: TrainingPolicy,
    evaluation_policy: EvaluationPolicy,
    maturity_window_days: int,
    trainer: TrainerLike,
    code_version: CodeVersion,
    artifact_out_dir: Path,
) -> _ConcreteTrainingPipeline:
    """`training.jobs.servicer.TrainingJobServicer`가 요청마다 부르는 팩토리
    (`Callable[[TrainingSpec], TrainingPipeline]`) 뒤에 이 함수를 부분 적용해 놓는다
    (`app/server.py`가 조립)."""
    deps = _PipelineDeps(
        spec=spec,
        training_policy=training_policy,
        evaluation_policy=evaluation_policy,
        maturity_window_days=maturity_window_days,
        trainer=trainer,
        code_version=code_version,
        artifact_out_dir=artifact_out_dir,
    )
    return _ConcreteTrainingPipeline(deps)


def pipeline_factory(
    *,
    training_policy: TrainingPolicy,
    evaluation_policy: EvaluationPolicy,
    maturity_window_days: int,
    trainer: TrainerLike,
    code_version: CodeVersion,
    artifact_out_dir: Path,
) -> Callable[[TrainingSpec], _ConcreteTrainingPipeline]:
    """`TrainingJobServicer(pipeline_factory=…)`에 그대로 주입하는 부분 적용 —
    `spec`만 요청마다 다르고 나머지는 서버 시동 시 고정이다."""

    def _factory(spec: TrainingSpec) -> _ConcreteTrainingPipeline:
        return build_training_pipeline(
            spec,
            training_policy=training_policy,
            evaluation_policy=evaluation_policy,
            maturity_window_days=maturity_window_days,
            trainer=trainer,
            code_version=code_version,
            artifact_out_dir=artifact_out_dir,
        )

    return _factory
