"""RED — `ml_engine.training.jobs.runner`(scope.md ⑦, ADR 0010 §6 fork 금지·D-2D-7
취소 카운터). fake `TrainingPipeline`(스레드 풀에서 실행)로 성공·실패·취소·크래시
네 갈래를 전이표로 확인한다.

M6/6B-4 Q-2 — `START` 는 **워커가 집는 순간**에 일어난다. 대기열에서 기다리는 job 이
`ACCEPTED` 로 관측되는 것과, 집었을 때 이미 종단이면 파이프라인을 돌리지 않는 것을 함께
잰다."""

from __future__ import annotations

import threading
import time
from datetime import UTC, datetime, timedelta
from pathlib import Path

from ml_engine.training.jobs.journal import JobSubmission
from ml_engine.training.jobs.journal_file import JobJournal
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
from ml_engine.training.jobs.state import (
    JobEvent,
    JobFailure,
    JobRecord,
    JobState,
)
from ml_engine.training.jobs.store import JobStore, Started

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


def _store(tmp_path: Path, *, pending_queue_max: int = 8) -> JobStore:
    return JobStore(
        journal=JobJournal(tmp_path / "journal"), pending_queue_max=pending_queue_max
    )


def _new_job(store: JobStore, key: str = "key-1", *, at: datetime = _NOW) -> str:
    started = store.start_or_reuse(
        key,
        "ds-1",
        accepted_at=at,
        submission=JobSubmission(
            dataset_uri="file:///x",
            manifest_checksum="0" * 64,
            training_spec_version="training-spec-v1",
        ),
    )
    assert isinstance(started, Started)
    return started.record.job_id


def _wait_for_terminal(store: JobStore, job_id: str, *, timeout: float = 2.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        record = store.get(job_id)
        assert record is not None
        if record.state in (JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED):
            return
        time.sleep(0.01)
    raise AssertionError(f"job {job_id} 가 시간 안에 종료 상태에 도달하지 못함")


def test_submit_transitions_to_running_then_succeeded(tmp_path: Path) -> None:
    store = _store(tmp_path)
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


def test_submit_pipeline_failed_transitions_to_failed(tmp_path: Path) -> None:
    store = _store(tmp_path)
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


def test_pipeline_crash_transitions_to_failed_runner_crashed(tmp_path: Path) -> None:
    """설계 검토 우회 (13) — 예외로 죽어도 `RUNNING`에 영원히 머물지 않는다."""
    store = _store(tmp_path)
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


def test_cancel_before_pipeline_checks_prevents_computation_start(
    tmp_path: Path,
) -> None:
    """D-2D-7 — 취소 뒤 계산 시작 카운터가 증가하지 않는다."""
    store = _store(tmp_path)
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


def test_cancel_training_job_via_servicer_style_transition_then_runner_ignores_late_result(
    tmp_path: Path,
) -> None:
    """`CancelTrainingJob`이 전이표로 먼저 `CANCELLED`를 확정한 뒤, 파이프라인이 뒤늦게
    성공으로 끝나도 재전이하지 않는다(멱등, 종료 상태 유지)."""
    store = _store(tmp_path)
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

    # R2-4 — JobStore.replace 는 production 호출자가 없어
    # 제거됐다. 여기서도 두 단계(transition() 수동 계산 + replace())가 아니라
    # apply_transition 한 번으로 같은 결과(원자적 읽기·계산·쓰기)를 얻는다.
    cancelled = store.apply_transition(job_id, JobEvent.CANCEL, at=datetime.now(UTC))
    assert isinstance(cancelled, JobRecord)

    release_event.set()
    time.sleep(0.2)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.CANCELLED
    runner.shutdown(wait=True)


def test_cancel_all_signals_every_in_flight_cancel_token(tmp_path: Path) -> None:
    """M-3 — SIGTERM 경로가 진행 중 job 전부에 취소를 요청할 때 쓴다.

    **결정적이다**: 토큰 등록이 `submit()` 안에서(호출 스레드에서) 일어나므로 제출 루프가
    끝난 시점에 세 토큰이 모두 등록돼 있다. 앞 판은 등록이 워커 스레드의 `_execute` 안에
    있어서 `cancel_all()` 이 **아직 등록되지 않은** 토큰을 못 보는 경합이었고, 저널 쓰기의
    `fsync` 가 워커에게 시간을 벌어 준 덕에 우연히 통과했다."""
    store = _store(tmp_path)
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

    # 등록 동기 — 제출이 끝나면 세 토큰이 이미 거기 있다(경합에 기대지 않는다).
    assert sorted(runner._cancel_tokens) == sorted(job_ids)

    runner.cancel_all()
    release_event.set()

    for pipeline in pipelines:
        assert pipeline.saw_cancelled.wait(timeout=2.0)
    runner.shutdown(wait=True)


def test_cancel_all_reaches_a_job_still_waiting_in_the_executor_queue(
    tmp_path: Path,
) -> None:
    """**종료 경로의 회귀**(code-review A, `27f9f081` M-3 이후). `cancel_all()` 은 등록된
    토큰만 세운다. 토큰 등록이 워커 스레드의 `_execute` 로 옮겨간 뒤에는, `cancel_all()` 과
    `shutdown(wait=False)` **사이에** 워커가 집는 job 이 **새 토큰**을 받아 취소 신호를 잃고
    파이프라인을 끝까지 돌린다 — 그 스레드가 프로세스 종료를 붙잡는다.

    결정적으로 재현한다: 워커 하나를 첫 job 으로 묶어 두고 둘째 job 을 **실행기 대기열에**
    남긴 상태에서 `cancel_all()` 을 부른다. 둘째 job 의 파이프라인이 나중에 돌 때 **취소된
    토큰**을 받아야 한다."""
    store = _store(tmp_path)
    runner = JobRunner(store, max_workers=1)
    picked_up = threading.Event()
    release_event = threading.Event()

    class _OccupyingPipeline:
        def run(
            self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
        ) -> PipelineCancelled:
            picked_up.set()
            release_event.wait(timeout=5.0)
            return PipelineCancelled()

    class _RecordingPipeline:
        def __init__(self) -> None:
            self.ran = threading.Event()
            self.saw_cancelled = False

        def run(
            self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
        ) -> PipelineCancelled:
            self.saw_cancelled = cancel_token.is_cancelled()
            self.ran.set()
            return PipelineCancelled()

    occupied = _new_job(store, "key-occupied")
    queued_pipeline = _RecordingPipeline()
    queued = _new_job(store, "key-queued", at=_NOW + timedelta(seconds=1))

    runner.submit(occupied, _DATASET_REF, _OccupyingPipeline())
    assert picked_up.wait(timeout=5.0)  # 워커 하나가 묶였다
    runner.submit(queued, _DATASET_REF, queued_pipeline)  # 대기열에 남는다

    runner.cancel_all()
    release_event.set()
    runner.shutdown(wait=True)

    assert queued_pipeline.ran.wait(timeout=5.0)  # 통제군 — 실제로 돌았다
    assert queued_pipeline.saw_cancelled, (
        "실행기 대기열에 있던 job 이 취소 신호를 잃었다 — 새 토큰을 받았다"
    )


def test_cancel_all_with_no_in_flight_jobs_is_a_no_op(tmp_path: Path) -> None:
    store = _store(tmp_path)
    runner = JobRunner(store, max_workers=1)
    runner.cancel_all()  # 예외 없이 조용히 지나간다
    runner.shutdown(wait=True)


# ---- M6/6B-4 Q-2 — 대기열에서 기다리는 job 은 ACCEPTED 다 ----


def test_queued_job_stays_accepted_until_a_worker_picks_it_up(tmp_path: Path) -> None:
    """앞 판은 `submit()` 안에서 곧바로 `RUNNING` 을 커밋해, `job_workers: 1` 에서
    실행 대기열에 앉아 있는 job 이 `RUNNING` 으로 **관측됐다**. 이제 그 job 은
    `ACCEPTED`(대기)이고 워커가 집을 때 `RUNNING` 이 된다."""
    store = _store(tmp_path)
    runner = JobRunner(store, max_workers=1)
    release_event = threading.Event()
    picked_up = threading.Event()

    class _BlockingPipeline:
        def run(
            self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
        ) -> PipelineOutcome:
            picked_up.set()
            release_event.wait(timeout=5.0)
            return _outcome()

    first = _new_job(store, "key-first", at=_NOW)
    second = _new_job(store, "key-second", at=_NOW + timedelta(seconds=1))
    runner.submit(first, _DATASET_REF, _BlockingPipeline())
    assert picked_up.wait(timeout=2.0)
    runner.submit(second, _DATASET_REF, _SucceedingPipeline())

    # 첫째가 워커를 쥐고 있는 동안 둘째는 대기다 — 상태가 그 사실을 말한다.
    waiting = store.get(second)
    assert waiting is not None
    assert waiting.state is JobState.ACCEPTED
    assert waiting.started_at is None
    assert [job.record.job_id for job in store.pending_jobs()] == [second]

    release_event.set()
    _wait_for_terminal(store, first)
    _wait_for_terminal(store, second)
    runner.shutdown(wait=True)


def test_discarded_pending_job_is_not_run_when_the_worker_reaches_it(
    tmp_path: Path,
) -> None:
    """설계 검토 우회 (3) — 폐기된 대기 job 을 워커가 뒤늦게 집어도 **계산을 시작하지
    않는다**(START 가 전이표에서 거부된다). 상태도 덮이지 않는다."""
    store = _store(tmp_path)
    runner = JobRunner(store, max_workers=1)
    job_id = _new_job(store, "key-1")

    class _CountingPipeline:
        def __init__(self) -> None:
            self.run_count = 0

        def run(
            self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
        ) -> PipelineOutcome:
            self.run_count += 1
            return _outcome()

    discarded = store.apply_transition(
        job_id,
        JobEvent.FAIL,
        at=datetime.now(UTC),
        failure=JobFailure(
            code=JobFailureCode.WORKER_RESOURCE_EXHAUSTED,
            detail_code=DetailCode.PENDING_QUEUE_OVERFLOW.value,
        ),
    )
    assert isinstance(discarded, JobRecord)

    pipeline = _CountingPipeline()
    runner.submit(job_id, _DATASET_REF, pipeline)
    runner.shutdown(wait=True)

    assert pipeline.run_count == 0
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert record.failure is not None
    assert record.failure.detail_code == DetailCode.PENDING_QUEUE_OVERFLOW.value


def test_cancel_before_pickup_prevents_the_pipeline_from_running(
    tmp_path: Path,
) -> None:
    """대기 중 취소도 같은 자리에서 닫힌다 — `CancelTrainingJob` 이 `ACCEPTED →
    CANCELLED` 를 커밋하면 워커는 그 job 을 돌리지 않는다."""
    store = _store(tmp_path)
    runner = JobRunner(store, max_workers=1)
    job_id = _new_job(store, "key-1")
    assert isinstance(
        store.apply_transition(job_id, JobEvent.CANCEL, at=datetime.now(UTC)),
        JobRecord,
    )

    class _CountingPipeline:
        def __init__(self) -> None:
            self.run_count = 0

        def run(
            self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
        ) -> PipelineOutcome:
            self.run_count += 1
            return _outcome()

    pipeline = _CountingPipeline()
    runner.submit(job_id, _DATASET_REF, pipeline)
    runner.shutdown(wait=True)

    assert pipeline.run_count == 0
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.CANCELLED


def test_shutdown_cancels_queued_futures_and_leaves_them_pending(
    tmp_path: Path,
) -> None:
    """종료가 대기열의 future 를 취소하면 그 job 은 **대기 상태로 남는다**(전이 없음) —
    다음 기동이 재적재한다. 완료 콜백이 `CancelledError` 로 터지지도 않는다."""
    store = _store(tmp_path)
    runner = JobRunner(store, max_workers=1)
    release_event = threading.Event()
    picked_up = threading.Event()

    class _BlockingPipeline:
        def run(
            self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
        ) -> PipelineCancelled:
            picked_up.set()
            release_event.wait(timeout=5.0)
            return PipelineCancelled()

    first = _new_job(store, "key-first", at=_NOW)
    queued = _new_job(store, "key-queued", at=_NOW + timedelta(seconds=1))
    runner.submit(first, _DATASET_REF, _BlockingPipeline())
    assert picked_up.wait(timeout=2.0)
    runner.submit(queued, _DATASET_REF, _SucceedingPipeline())

    runner.shutdown(wait=False)
    release_event.set()
    time.sleep(0.2)

    record = store.get(queued)
    assert record is not None
    assert record.state is JobState.ACCEPTED


# ---- vr M-3 — 계약 문면이 드는 세 번째 사유(START 커밋 전 크래시) ----


def test_crash_before_the_start_commit_uses_the_accepted_to_failed_edge(
    tmp_path: Path,
) -> None:
    """`ACCEPTED → FAILED` 간선의 세 번째 쓰임 — 워커가 집는 도중 `START` 를 커밋하기
    **전에** 크래시하면 job 은 아직 대기이므로 완료 콜백의 `FAILED(TRAINING_ERROR,
    RUNNER_CRASHED)` 가 이 간선으로 간다. 계약 문면(proto 주석 · ADR 0010 D-8 개정)이
    이 사유를 들므로 실측으로 잠근다 — `started_at` 이 미설정인 것이 「실행된 적 없다」의
    관측값이다."""
    store = _store(tmp_path)
    job_id = _new_job(store)
    original = store.apply_transition

    def _crash_on_start(
        target_job_id: str, event: JobEvent, **kwargs: object
    ) -> object:
        if event is JobEvent.START:
            raise RuntimeError("START 커밋 전 크래시 주입")
        return original(target_job_id, event, **kwargs)  # type: ignore[arg-type]

    store.apply_transition = _crash_on_start  # type: ignore[method-assign]
    runner = JobRunner(store, max_workers=1)
    runner.submit(job_id, _DATASET_REF, _SucceedingPipeline())
    runner.shutdown(wait=True)

    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert record.failure is not None
    assert record.failure.code is JobFailureCode.TRAINING_ERROR
    assert record.failure.detail_code == DetailCode.RUNNER_CRASHED.value
    assert record.started_at is None  # 실행된 적이 없다
