"""RED — `ml_engine.training.jobs.runner`(scope.md ⑦, ADR 0010 §6 fork 금지·D-2D-7
취소 카운터). fake `TrainingPipeline`(스레드 풀에서 실행)로 성공·실패·취소·크래시
네 갈래를 전이표로 확인한다."""

from __future__ import annotations

import threading
import time
from datetime import UTC, datetime

from ml_engine.training.jobs.pipeline import (
    ArtifactRefValue,
    CancelToken,
    DatasetRefInput,
    DetailCode,
    EvaluationRefValue,
    JobFailureCode,
    PipelineCancelled,
    PipelineFailed,
    PipelineOutcome,
)
from ml_engine.training.jobs.runner import JobRunner
from ml_engine.training.jobs.state import JobEvent, JobRecord, JobState, transition
from ml_engine.training.jobs.store import InMemoryJobStore, Started

_NOW = datetime(2026, 1, 1, tzinfo=UTC)
_DATASET_REF = DatasetRefInput(
    uri="file:///x", manifest_checksum="0" * 64, dataset_id="ds-1"
)


def _outcome() -> PipelineOutcome:
    return PipelineOutcome(
        artifact=ArtifactRefValue(
            uri="file:///a",
            release_id="r1",
            artifact_checksum="0" * 64,
            feature_schema_version="v2",
            code_version="sha-1",
            dataset_id="ds-1",
            manifest_schema_version="artifact-manifest-v1",
        ),
        evaluation=EvaluationRefValue(
            uri="file:///r",
            checksum="1" * 64,
            report_schema_version="evaluation-report-v1",
        ),
    )


class _SucceedingPipeline:
    def run(
        self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
    ) -> PipelineOutcome:
        return _outcome()


class _FailingPipeline:
    def run(
        self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
    ) -> PipelineFailed:
        return PipelineFailed(JobFailureCode.TRAINING_ERROR, DetailCode.TRAINER_ERROR)


class _CancelledPipeline:
    """단계 경계에서 취소를 확인하는 실물을 흉내 — `start_count`로 「계산이 시작되지
    않았다」(D-2D-7)를 관측한다."""

    def __init__(self) -> None:
        self.start_count = 0

    def run(
        self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
    ) -> PipelineCancelled:
        if cancel_token.is_cancelled():
            return PipelineCancelled()
        self.start_count += 1
        return PipelineCancelled()


class _CrashingPipeline:
    def run(
        self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
    ) -> PipelineOutcome:
        raise RuntimeError("분류되지 않은 크래시")


def _new_job(store: InMemoryJobStore, key: str = "key-1") -> str:
    started = store.start_or_reuse(key, "ds-1", accepted_at=_NOW)
    assert isinstance(started, Started)
    return started.record.job_id


def _wait_for_terminal(
    store: InMemoryJobStore, job_id: str, *, timeout: float = 2.0
) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        record = store.get(job_id)
        assert record is not None
        if record.state in (JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED):
            return
        time.sleep(0.01)
    raise AssertionError(f"job {job_id} 가 시간 안에 종료 상태에 도달하지 못함")


def test_submit_transitions_to_running_then_succeeded() -> None:
    store = InMemoryJobStore()
    runner = JobRunner(store, max_workers=2)
    job_id = _new_job(store)
    runner.submit(job_id, _DATASET_REF, _SucceedingPipeline())
    _wait_for_terminal(store, job_id)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.SUCCEEDED
    assert record.artifact is not None
    assert record.evaluation is not None
    runner.shutdown(wait=True)


def test_submit_pipeline_failed_transitions_to_failed() -> None:
    store = InMemoryJobStore()
    runner = JobRunner(store, max_workers=2)
    job_id = _new_job(store)
    runner.submit(job_id, _DATASET_REF, _FailingPipeline())
    _wait_for_terminal(store, job_id)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert record.failure is not None
    assert record.failure.code is JobFailureCode.TRAINING_ERROR
    runner.shutdown(wait=True)


def test_pipeline_crash_transitions_to_failed_runner_crashed() -> None:
    """설계 검토 우회 (13) — 예외로 죽어도 `RUNNING`에 영원히 머물지 않는다."""
    store = InMemoryJobStore()
    runner = JobRunner(store, max_workers=2)
    job_id = _new_job(store)
    runner.submit(job_id, _DATASET_REF, _CrashingPipeline())
    _wait_for_terminal(store, job_id)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert record.failure is not None
    assert record.failure.detail_code == DetailCode.RUNNER_CRASHED.value
    runner.shutdown(wait=True)


def test_cancel_before_pipeline_checks_prevents_computation_start() -> None:
    """D-2D-7 — 취소 뒤 계산 시작 카운터가 증가하지 않는다."""
    store = InMemoryJobStore()
    runner = JobRunner(store, max_workers=1)
    job_id = _new_job(store)

    release_event = threading.Event()

    class _BlockingThenCancelledPipeline:
        def __init__(self) -> None:
            self.start_count = 0

        def run(
            self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
        ) -> PipelineCancelled:
            release_event.wait(timeout=2.0)
            if cancel_token.is_cancelled():
                return PipelineCancelled()
            self.start_count += 1
            return PipelineCancelled()

    pipeline = _BlockingThenCancelledPipeline()
    runner.submit(job_id, _DATASET_REF, pipeline)
    runner.cancel(job_id)
    release_event.set()
    _wait_for_terminal(store, job_id)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.CANCELLED
    assert pipeline.start_count == 0
    runner.shutdown(wait=True)


def test_cancel_training_job_via_servicer_style_transition_then_runner_ignores_late_result() -> (
    None
):
    """`CancelTrainingJob`이 전이표로 먼저 `CANCELLED`를 확정한 뒤, 파이프라인이 뒤늦게
    성공으로 끝나도 재전이하지 않는다(멱등, 종료 상태 유지)."""
    store = InMemoryJobStore()
    runner = JobRunner(store, max_workers=1)
    job_id = _new_job(store)

    release_event = threading.Event()

    class _SlowSucceedingPipeline:
        def run(
            self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
        ) -> PipelineOutcome:
            release_event.wait(timeout=2.0)
            return _outcome()

    runner.submit(job_id, _DATASET_REF, _SlowSucceedingPipeline())

    record = store.get(job_id)
    assert record is not None
    cancelled = transition(record, JobEvent.CANCEL, at=datetime.now(UTC))
    assert isinstance(cancelled, JobRecord)
    store.replace(cancelled)

    release_event.set()
    time.sleep(0.2)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.CANCELLED
    runner.shutdown(wait=True)


def test_cancel_all_signals_every_in_flight_cancel_token() -> None:
    """M-3(verifier r1) — SIGTERM 경로가 진행 중 job 전부에 취소를 요청할 때 쓴다."""
    store = InMemoryJobStore()
    runner = JobRunner(store, max_workers=4)
    release_event = threading.Event()

    class _BlockingPipeline:
        def __init__(self) -> None:
            self.saw_cancelled = threading.Event()

        def run(
            self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
        ) -> PipelineCancelled:
            release_event.wait(timeout=2.0)
            if cancel_token.is_cancelled():
                self.saw_cancelled.set()
            return PipelineCancelled()

    pipelines = [_BlockingPipeline() for _ in range(3)]
    job_ids = [_new_job(store, key=f"key-{i}") for i in range(3)]
    for job_id, pipeline in zip(job_ids, pipelines, strict=True):
        runner.submit(job_id, _DATASET_REF, pipeline)

    runner.cancel_all()
    release_event.set()

    for pipeline in pipelines:
        assert pipeline.saw_cancelled.wait(timeout=2.0)
    runner.shutdown(wait=True)


def test_cancel_all_with_no_in_flight_jobs_is_a_no_op() -> None:
    store = InMemoryJobStore()
    runner = JobRunner(store, max_workers=1)
    runner.cancel_all()  # 예외 없이 조용히 지나간다
    runner.shutdown(wait=True)
