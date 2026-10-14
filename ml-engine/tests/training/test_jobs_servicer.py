"""RED — `ml_engine.training.jobs.servicer.TrainingJobServicer`(scope.md ⑥). 검증
규칙표 + 멱등 + readiness + 전이 in-process(실 servicer, fake pipeline/runner 조합)."""

from __future__ import annotations

import logging
import threading
import time
from datetime import UTC, datetime
from pathlib import Path

import pytest

from ml_engine.contracts import error_pb2, training_pb2
from ml_engine.training.jobs.journal import (
    JobSubmission,
    JournalFailed,
    JournalFailureReason,
)
from ml_engine.training.jobs.journal_file import JobJournal
from ml_engine.training.jobs.pipeline import (
    ArtifactRefValue,
    CancelToken,
    DatasetRefInput,
    DetailCode,
    EvaluationRefValue,
    PipelineOutcome,
)
from ml_engine.training.jobs.runner import JobRunner
from ml_engine.training.jobs.servicer import TrainingJobServicer
from ml_engine.training.jobs.state import JobEvent, JobRecord, JobState
from ml_engine.training.jobs.store import JobStore, Started
from ml_engine.training.spec import TRAINING_SPECS

_KNOWN_SPEC_VERSION = next(iter(TRAINING_SPECS))
_VALID_MANIFEST_CHECKSUM = "a" * 64


class _InstantSucceedingPipeline:
    def run(
        self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
    ) -> PipelineOutcome:
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


def _build_servicer(
    tmp_path: Path,
    *,
    is_ready: bool = True,
    pipeline: object | None = None,
    pending_queue_max: int = 8,
    max_workers: int = 2,
) -> tuple[TrainingJobServicer, JobRunner, JobStore]:
    store = JobStore(
        journal=JobJournal(tmp_path / "journal"), pending_queue_max=pending_queue_max
    )
    runner = JobRunner(store, max_workers=max_workers)
    factory = (
        (lambda spec: pipeline)
        if pipeline is not None
        else (lambda spec: _InstantSucceedingPipeline())
    )
    servicer = TrainingJobServicer(
        store=store,
        runner=runner,
        is_ready=lambda: is_ready,
        pipeline_factory=factory,  # type: ignore[arg-type]
        dataset_uri_schemes=frozenset({"file"}),
        idempotency_key_max_chars=256,
    )
    return servicer, runner, store


def _valid_request(**overrides: object) -> training_pb2.StartTrainingRequest:
    request = training_pb2.StartTrainingRequest()
    request.envelope.request_id = "req-1"
    request.envelope.correlation_id = "corr-1"
    request.idempotency_key = "idem-1"
    request.dataset.uri = "file:///tmp/dataset"
    request.dataset.manifest_checksum = _VALID_MANIFEST_CHECKSUM
    request.dataset.dataset_id = "ds-1"
    request.training_spec_version = _KNOWN_SPEC_VERSION
    for key, value in overrides.items():
        setattr(request, key, value)
    return request


class _SynchronousStartRunner:
    """`submit` 이 워커를 흉내 내 **그 자리에서** START 를 커밋한다 — 「응답을 조립하기
    전에 워커가 이미 job 을 집었다」를 경합 반복(400회)이 아니라 결정적으로 만든다.
    `_accept_or_reuse` 가 저장소를 `_enqueue` **뒤에** 읽으면 handle 이 `RUNNING` 이 되고,
    그것이 proto `StartTrainingResponse` 주석(「즉시 `ACCEPTED` 를 돌려준다」)과 어긋난다."""

    def __init__(self, store: JobStore) -> None:
        self._store = store
        self.started: list[str] = []

    def submit(
        self, job_id: str, dataset_ref: DatasetRefInput, pipeline: object
    ) -> None:
        result = self._store.apply_transition(
            job_id, JobEvent.START, at=datetime.now(UTC)
        )
        assert isinstance(result, JobRecord), result
        self.started.append(job_id)

    def cancel(self, job_id: str) -> None:  # servicer 가 취소 경로에서만 부른다
        return None


def test_start_training_reports_accepted_even_if_a_worker_starts_first(
    tmp_path: Path,
) -> None:
    """proto `StartTrainingResponse` 주석 — `StartTraining` 은 **즉시 `ACCEPTED`** 를
    돌려준다. 워커가 제출 직후 그 job 을 집어 `RUNNING` 을 커밋해도 응답 handle 은
    `ACCEPTED` 다(저장소 읽기가 적재 직후·대기열 투입 **전**이라 결정적이다).

    통제군: 같은 호출에서 워커가 실제로 집었다(`runner.started` · 저장소 상태 `RUNNING`)
    — 이 probe 가 공허하지 않다는 증거다."""
    store = JobStore(journal=JobJournal(tmp_path / "journal"), pending_queue_max=4)
    runner = _SynchronousStartRunner(store)
    servicer = TrainingJobServicer(
        store=store,
        runner=runner,  # type: ignore[arg-type]
        is_ready=lambda: True,
        pipeline_factory=lambda spec: _InstantSucceedingPipeline(),  # type: ignore[arg-type,return-value]
        dataset_uri_schemes=frozenset({"file"}),
        idempotency_key_max_chars=256,
    )

    response = servicer.StartTraining(_valid_request(), context=None)

    assert response.WhichOneof("result") == "handle"
    assert response.handle.state == training_pb2.JOB_STATE_ACCEPTED
    assert runner.started == [response.handle.job_id]
    record = store.get(response.handle.job_id)
    assert record is not None
    assert record.state is JobState.RUNNING


class _NeverStartingRunner:
    """`submit` 이 아무것도 하지 않는다 — 제출한 job 이 전부 `ACCEPTED`(대기) 로 남아
    상한 폐기를 결정적으로 일으킬 수 있다."""

    def submit(
        self, job_id: str, dataset_ref: DatasetRefInput, pipeline: object
    ) -> None:
        return None

    def cancel(self, job_id: str) -> None:
        return None


def test_pending_discard_is_logged_with_job_id_and_reason(
    tmp_path: Path, caplog: pytest.LogCaptureFixture
) -> None:
    """vr H-3 — 상한 폐기는 **폐기 시점에** 로그를 남긴다. `ListTrainingJobs`·metric 이
    없으므로(scope out_scope) job_id 를 쥔 쪽 말고 폐기를 알 수 있는 채널은 서버 로그
    하나다(runbook §2 「폐기를 어떻게 아는가」의 「서버 로그: 있다」 행).

    통제군: 폐기가 없는 제출은 이 로그를 남기지 않는다."""
    store = JobStore(journal=JobJournal(tmp_path / "journal"), pending_queue_max=1)
    servicer = TrainingJobServicer(
        store=store,
        runner=_NeverStartingRunner(),  # type: ignore[arg-type]
        is_ready=lambda: True,
        pipeline_factory=lambda spec: _InstantSucceedingPipeline(),  # type: ignore[arg-type,return-value]
        dataset_uri_schemes=frozenset({"file"}),
        idempotency_key_max_chars=256,
    )

    with caplog.at_level(logging.WARNING, logger="ml_engine.training.jobs.servicer"):
        first = servicer.StartTraining(
            _valid_request(idempotency_key="idem-oldest"), context=None
        )
        assert [record.levelname for record in caplog.records] == []  # 통제군
        servicer.StartTraining(
            _valid_request(idempotency_key="idem-newest"), context=None
        )

    discarded = store.get(first.handle.job_id)
    assert discarded is not None
    assert discarded.state is JobState.FAILED
    assert len(caplog.records) == 1
    message = caplog.records[0].getMessage()
    assert caplog.records[0].levelno == logging.WARNING
    assert first.handle.job_id in message
    assert DetailCode.PENDING_QUEUE_OVERFLOW.value in message
    assert discarded.accepted_at.isoformat() in message


# ---- cr M-3 — 저널을 쓸 수 없을 때의 wire 표면 둘 ----


def _break_journal_writes(store: JobStore) -> None:
    """저장소의 저널 쓰기를 `JournalFailed` 로 고정한다.

    `chmod 0o500`(저장소 층 test 가 쓰는 기법)은 **root 러너에서 쓰기를 막지 못해** test
    가 붉어진다. 여기서 재려는 것은 「쓰기가 실제로 실패할 수 있는가」(그것은
    `tests/training/test_jobs_store.py::test_journal_write_failure_leaves_memory_unchanged`
    가 실제 권한으로 잰다)가 아니라 **그 결과 타입이 wire 에 어떻게 닿는가**이므로,
    결과 타입 경계에서 주입한다."""

    def _always_failing(snapshot: object) -> JournalFailed:
        return JournalFailed(JournalFailureReason.WRITE_FAILED, "주입된 쓰기 실패")

    store._journal.write = _always_failing  # type: ignore[assignment,method-assign]


def _assert_store_unavailable(failure: training_pb2.ApplicationFailure) -> None:
    assert failure.code == error_pb2.FAILURE_CODE_MODEL_NOT_READY
    assert failure.detail_code == "JOB_STORE_UNAVAILABLE"
    assert failure.retryable is True


def test_start_training_with_unwritable_journal_is_store_unavailable(
    tmp_path: Path,
) -> None:
    """저널에 못 쓰면 요청을 **받지 않은** 것이다 — `MODEL_NOT_READY` +
    `JOB_STORE_UNAVAILABLE`(retryable). 저장소에 job 도 남지 않는다."""
    servicer, runner, store = _build_servicer(tmp_path)
    _break_journal_writes(store)

    response = servicer.StartTraining(_valid_request(), context=None)

    assert response.WhichOneof("result") == "failure"
    _assert_store_unavailable(response.failure)
    assert store.pending_jobs() == ()
    runner.shutdown(wait=True)


def test_cancel_training_job_with_unwritable_journal_is_store_unavailable(
    tmp_path: Path,
) -> None:
    """취소를 영속시키지 못했으면 「취소됐다」고 답하지 않는다 — 같은 실패 둘로 답하고
    상태는 그대로다(메모리도 바뀌지 않았다)."""
    store = JobStore(journal=JobJournal(tmp_path / "journal"), pending_queue_max=4)
    servicer = TrainingJobServicer(
        store=store,
        runner=_NeverStartingRunner(),  # type: ignore[arg-type]
        is_ready=lambda: True,
        pipeline_factory=lambda spec: _InstantSucceedingPipeline(),  # type: ignore[arg-type,return-value]
        dataset_uri_schemes=frozenset({"file"}),
        idempotency_key_max_chars=256,
    )
    started = servicer.StartTraining(_valid_request(), context=None)
    job_id = started.handle.job_id
    _break_journal_writes(store)

    request = training_pb2.CancelTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = job_id
    response = servicer.CancelTrainingJob(request, context=None)

    assert response.WhichOneof("result") == "failure"
    _assert_store_unavailable(response.failure)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.ACCEPTED


def test_start_training_returns_without_waiting_for_the_pipeline(
    tmp_path: Path,
) -> None:
    """RPC 는 학습을 기다리지 않는다 — 파이프라인이 붙잡혀 있어도 즉시 handle 로
    답한다. 상태는 proto 주석대로 `ACCEPTED` 다(워커가 그 사이 집었더라도 — 위 test 가
    그 경우를 결정적으로 잰다)."""
    release_event = threading.Event()
    servicer, runner, _store_unused = _build_servicer(
        tmp_path, pipeline=_BlockingThenSucceedingPipeline(release_event)
    )
    response = servicer.StartTraining(_valid_request(), context=None)
    assert response.WhichOneof("result") == "handle"
    assert response.handle.job_id
    assert response.handle.state == training_pb2.JOB_STATE_ACCEPTED
    release_event.set()
    runner.shutdown(wait=True)


def test_start_training_handle_is_accepted_while_every_worker_is_busy(
    tmp_path: Path,
) -> None:
    """M6/6B-4 Q-2 — `ACCEPTED` = 대기가 **wire 에 닿는다.** 워커가 하나이고 그 하나가
    붙잡혀 있으면, 다음 제출의 handle 은 `JOB_STATE_ACCEPTED` 다(앞 판은 이 자리에서
    `RUNNING` 을 실어, 관측 가능한 상태가 실행을 뜻하지 않았다)."""
    release_event = threading.Event()
    servicer, runner, store = _build_servicer(
        tmp_path,
        pipeline=_BlockingThenSucceedingPipeline(release_event),
        max_workers=1,
    )
    first = servicer.StartTraining(
        _valid_request(idempotency_key="idem-first"), context=None
    )
    _wait_for_state(servicer, first.handle.job_id, {training_pb2.JOB_STATE_RUNNING})

    second = servicer.StartTraining(
        _valid_request(idempotency_key="idem-second"), context=None
    )
    assert second.handle.state == training_pb2.JOB_STATE_ACCEPTED
    assert [job.record.job_id for job in store.pending_jobs()] == [second.handle.job_id]

    release_event.set()
    runner.shutdown(wait=True)


def test_empty_request_id_is_invalid_request(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = _valid_request()
    request.envelope.request_id = ""
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_empty_idempotency_key_is_invalid_request(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = _valid_request(idempotency_key="")
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_idempotency_key_too_long_is_invalid_request(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = _valid_request(idempotency_key="x" * 257)
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_requested_release_id_present_is_invalid_request(tmp_path: Path) -> None:
    """설계 검토 우회 (14)."""
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = _valid_request()
    request.requested_release_id = "some-release"
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_unsupported_dataset_uri_scheme_is_invalid_request(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = _valid_request()
    request.dataset.uri = "s3://bucket/dataset"
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_empty_dataset_id_is_invalid_request_not_an_escaping_exception(
    tmp_path: Path,
) -> None:
    """cr L-5(선재) — 빈 `dataset.dataset_id` 는 `_validate_start` 를 지나 `JobRecord.
    __post_init__` 의 `ValueError` 로 **핸들러를 벗어났다**(gRPC `UNKNOWN`). ADR 0010 D-3
    의 application failure 층 규율대로 `INVALID_REQUEST` 로 답한다."""
    servicer, runner, store = _build_servicer(tmp_path)
    request = _valid_request()
    request.dataset.dataset_id = ""

    response = servicer.StartTraining(request, context=None)

    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "DATASET_ID_EMPTY"
    assert response.failure.retryable is False
    assert store.pending_jobs() == ()
    runner.shutdown(wait=True)


def test_malformed_manifest_checksum_is_invalid_request(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = _valid_request()
    request.dataset.manifest_checksum = "not-a-checksum"
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_unknown_training_spec_version_is_unsupported_training_spec(
    tmp_path: Path,
) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = _valid_request(training_spec_version="unknown-v9")
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC
    runner.shutdown(wait=True)


def test_not_ready_returns_model_not_ready_and_retryable(tmp_path: Path) -> None:
    """우회 (1) — 정책 preload 실패 상태에서는 job 을 못 돌린다."""
    servicer, runner, _store_unused = _build_servicer(tmp_path, is_ready=False)
    response = servicer.StartTraining(_valid_request(), context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_MODEL_NOT_READY
    assert response.failure.retryable is True
    runner.shutdown(wait=True)


def test_same_idempotency_key_twice_returns_same_job_id(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    first = servicer.StartTraining(_valid_request(), context=None)
    second = servicer.StartTraining(_valid_request(), context=None)
    assert first.handle.job_id == second.handle.job_id
    runner.shutdown(wait=True)


def test_same_key_different_dataset_is_idempotency_conflict(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    servicer.StartTraining(_valid_request(), context=None)
    conflicting = _valid_request()
    conflicting.dataset.dataset_id = "ds-other"
    response = servicer.StartTraining(conflicting, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_IDEMPOTENCY_CONFLICT
    runner.shutdown(wait=True)


def test_get_training_job_unknown_job_id_is_job_not_found(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = "unknown"
    response = servicer.GetTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_JOB_NOT_FOUND
    runner.shutdown(wait=True)


def test_get_training_job_returns_eventually_succeeded_job(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    started = servicer.StartTraining(_valid_request(), context=None)
    job_id = started.handle.job_id

    deadline = time.monotonic() + 2.0
    while time.monotonic() < deadline:
        get_request = training_pb2.GetTrainingJobRequest()
        get_request.envelope.request_id = "r"
        get_request.envelope.correlation_id = "c"
        get_request.job_id = job_id
        response = servicer.GetTrainingJob(get_request, context=None)
        if response.job.state == training_pb2.JOB_STATE_SUCCEEDED:
            assert response.job.HasField("artifact")
            assert response.job.HasField("evaluation")
            runner.shutdown(wait=True)
            return
        time.sleep(0.01)
    runner.shutdown(wait=True)
    raise AssertionError("job 이 시간 안에 SUCCEEDED 에 도달하지 못함")


def test_cancel_unknown_job_id_is_job_not_found(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = training_pb2.CancelTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = "unknown"
    response = servicer.CancelTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_JOB_NOT_FOUND
    runner.shutdown(wait=True)


def test_cancel_accepted_job_transitions_to_cancelled(tmp_path: Path) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    started = servicer.StartTraining(_valid_request(), context=None)
    cancel_request = training_pb2.CancelTrainingJobRequest()
    cancel_request.envelope.request_id = "r"
    cancel_request.envelope.correlation_id = "c"
    cancel_request.job_id = started.handle.job_id
    response = servicer.CancelTrainingJob(cancel_request, context=None)
    assert response.WhichOneof("result") == "job"
    assert response.job.state in (
        training_pb2.JOB_STATE_CANCELLED,
        training_pb2.JOB_STATE_SUCCEEDED,
    )
    runner.shutdown(wait=True)


# ---- H-3 — GetTrainingJob·CancelTrainingJob envelope 검증 ----


def test_get_training_job_empty_request_id_is_invalid_request_not_job_not_found(
    tmp_path: Path,
) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = ""
    request.envelope.correlation_id = "c"
    request.job_id = "some-job"
    response = servicer.GetTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_get_training_job_empty_correlation_id_is_invalid_request(
    tmp_path: Path,
) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = ""
    request.job_id = "some-job"
    response = servicer.GetTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_get_training_job_empty_job_id_is_invalid_request_not_job_not_found(
    tmp_path: Path,
) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = ""
    response = servicer.GetTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "JOB_ID_EMPTY"
    runner.shutdown(wait=True)


def test_cancel_training_job_empty_request_id_is_invalid_request(
    tmp_path: Path,
) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = training_pb2.CancelTrainingJobRequest()
    request.envelope.request_id = ""
    request.envelope.correlation_id = "c"
    request.job_id = "some-job"
    response = servicer.CancelTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_cancel_training_job_empty_job_id_is_invalid_request_not_job_not_found(
    tmp_path: Path,
) -> None:
    servicer, runner, _store_unused = _build_servicer(tmp_path)
    request = training_pb2.CancelTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = ""
    response = servicer.CancelTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "JOB_ID_EMPTY"
    runner.shutdown(wait=True)


# ---- H-2 — store 경합(양방향): 종료 상태가 역행하지 않는다(실 servicer·
# 실 runner) ----


class _BlockingThenSucceedingPipeline:
    """`release_event`가 열릴 때까지 붙잡혀 있다가 성공으로 끝난다 — 두 writer(runner
    종료 전이·`CancelTrainingJob`)의 실행 순서를 결정적으로 통제하기 위한 fake."""

    def __init__(self, release_event: threading.Event) -> None:
        self._release_event = release_event

    def run(
        self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
    ) -> PipelineOutcome:
        self._release_event.wait(timeout=5.0)
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


def _wait_for_state(
    servicer: TrainingJobServicer,
    job_id: str,
    states: set[int],
    *,
    timeout: float = 2.0,
) -> training_pb2.TrainingJob:
    deadline = time.monotonic() + timeout
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = job_id
    while time.monotonic() < deadline:
        response = servicer.GetTrainingJob(request, context=None)
        if response.WhichOneof("result") == "job" and response.job.state in states:
            return response.job
        time.sleep(0.005)
    raise AssertionError(f"job {job_id} 가 시간 안에 {states} 에 도달하지 못함")


def test_cancel_committed_before_pipeline_finishes_state_never_regresses_to_succeeded(
    tmp_path: Path,
) -> None:
    """H-2 정방향 — cancel 이 먼저 store 에 CANCELLED 를 커밋한 뒤 파이프라인이 뒤늦게
    성공으로 끝나도, 그 결과가 CANCELLED 를 SUCCEEDED 로 덮어쓰지 않는다."""
    release_event = threading.Event()
    servicer, runner, store = _build_servicer(
        tmp_path, pipeline=_BlockingThenSucceedingPipeline(release_event)
    )
    started = servicer.StartTraining(_valid_request(), context=None)
    job_id = started.handle.job_id
    _wait_for_state(servicer, job_id, {training_pb2.JOB_STATE_RUNNING})

    cancel_request = training_pb2.CancelTrainingJobRequest()
    cancel_request.envelope.request_id = "r"
    cancel_request.envelope.correlation_id = "c"
    cancel_request.job_id = job_id
    cancel_response = servicer.CancelTrainingJob(cancel_request, context=None)
    assert cancel_response.job.state == training_pb2.JOB_STATE_CANCELLED

    release_event.set()
    time.sleep(0.3)  # 파이프라인이 끝나고 러너의 늦은 전이 시도가 지나갈 시간을 준다

    record = store.get(job_id)
    assert record is not None
    assert record.state.value == "CANCELLED"
    runner.shutdown(wait=True)


def test_runner_finishes_before_cancel_request_returns_succeeded_idempotent(
    tmp_path: Path,
) -> None:
    """H-2 역방향 — 러너가 먼저 SUCCEEDED 를
    store 에 커밋한 뒤에야 CancelTrainingJob 이 도착하면, 종료 상태 멱등 no-op 규칙대로
    CancelTrainingJob 은 CANCELLED 를 강제하지 않고 **실제 최신 상태**(SUCCEEDED)를
    돌려준다 — stale read 로 계산한 CANCELLED 로 store 를 덮어쓰지 않는다."""
    servicer, runner, store = _build_servicer(tmp_path)
    started = servicer.StartTraining(_valid_request(), context=None)
    job_id = started.handle.job_id
    _wait_for_state(servicer, job_id, {training_pb2.JOB_STATE_SUCCEEDED})

    cancel_request = training_pb2.CancelTrainingJobRequest()
    cancel_request.envelope.request_id = "r"
    cancel_request.envelope.correlation_id = "c"
    cancel_request.job_id = job_id
    cancel_response = servicer.CancelTrainingJob(cancel_request, context=None)
    assert cancel_response.WhichOneof("result") == "job"
    assert cancel_response.job.state == training_pb2.JOB_STATE_SUCCEEDED
    assert cancel_response.job.HasField("artifact")

    record = store.get(job_id)
    assert record is not None
    assert record.state.value == "SUCCEEDED"
    runner.shutdown(wait=True)


def test_cancel_wins_race_before_runner_start_transition_start_training_does_not_crash(
    tmp_path: Path,
) -> None:
    """R2-1 — `_accept_or_reuse`가 `start_or_reuse`로 job 을 ACCEPTED 로
    만든 뒤 `runner.submit`을 부르는데, 그 사이에 CancelTrainingJob 의 CANCEL 전이가
    커밋되면(경합에서 Cancel 이 이김) 앞 판은 `submit`이 `ValueError`를 그대로 던져 그
    StartTraining 호출 자체가 gRPC UNKNOWN 으로 죽었다 — 상태 오염은 없었다.

    **M6/6B-4 이후 그 경합의 자리가 바뀌었다.** `submit`은 더 이상 전이하지 않으므로
    (START 는 워커가 집는 순간) 이 창에서 깨질 것이 없고, 남는 요구는 **응답이 정직한가**
    하나다: `start_or_reuse` 가 돌려준 스냅숏(ACCEPTED)을 그대로 쓰면 이미 CANCELLED 인
    job 을 ACCEPTED 라고 답한다. 저장소를 다시 읽어 응답하는 것을 여기서 잠근다.

    Cancel 을 끼우는 자리는 **`start_or_reuse` 가 돌아온 직후**다(cr H-1 로 저장소 읽기가
    `_enqueue` 앞으로 옮겨졌으므로 `pipeline_factory` 안은 더 이상 그 읽기보다 앞이
    아니다) — threading 경합에 기대지 않고 「다른 writer 가 먼저 종단으로 옮겼다」를
    결정론적으로 만든다."""
    store = JobStore(journal=JobJournal(tmp_path / "journal"), pending_queue_max=8)
    runner = JobRunner(store, max_workers=2)

    captured_job_id: dict[str, str] = {}
    original_start_or_reuse = store.start_or_reuse

    def _capturing_start_or_reuse(
        idempotency_key: str,
        dataset_id: str,
        *,
        accepted_at: datetime,
        submission: JobSubmission,
    ) -> object:
        outcome = original_start_or_reuse(
            idempotency_key, dataset_id, accepted_at=accepted_at, submission=submission
        )
        if isinstance(outcome, Started):
            captured_job_id["job_id"] = outcome.record.job_id
            # 다른 writer(`CancelTrainingJob`)가 이 창에서 이긴다 — servicer 가 저장소를
            # 읽기 전에 종단으로 옮긴다.
            cancelled = store.apply_transition(
                outcome.record.job_id, JobEvent.CANCEL, at=datetime.now(UTC)
            )
            assert isinstance(cancelled, JobRecord)
            assert cancelled.state is JobState.CANCELLED
        return outcome

    store.start_or_reuse = _capturing_start_or_reuse  # type: ignore[method-assign]

    servicer = TrainingJobServicer(
        store=store,
        runner=runner,
        is_ready=lambda: True,
        pipeline_factory=lambda spec: _InstantSucceedingPipeline(),  # type: ignore[arg-type,return-value]
        dataset_uri_schemes=frozenset({"file"}),
        idempotency_key_max_chars=256,
    )

    response = servicer.StartTraining(
        _valid_request(idempotency_key="idem-race"), context=None
    )  # 예외를 던지면 이 줄에서 실패한다(수정 전에는 ValueError 로 죽었다)

    assert response.WhichOneof("result") == "handle"
    job_id = captured_job_id["job_id"]
    assert response.handle.job_id == job_id
    # 최신 상태(CANCELLED)를 응답한다 — start_or_reuse 가 반환한 스냅샷(ACCEPTED)을
    # 그대로 쓰지 않는다.
    assert response.handle.state == training_pb2.JOB_STATE_CANCELLED

    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.CANCELLED  # 상태 오염 없음(verifier 확인 사항)
    runner.shutdown(wait=True)


# ---- cr M-4 — 멱등 재사용 경로의 handle 상태 ----


def test_idempotent_reuse_carries_the_existing_jobs_current_state(
    tmp_path: Path,
) -> None:
    """proto `StartTrainingResponse` 주석의 「즉시 `ACCEPTED`」는 **새 job 경로**의 보증이다.
    같은 멱등 키 재요청(= 멱등 키의 설계 용도인 client 재시도)은 적재가 아니므로 기존 job 의
    **현재 상태**를 싣는다 — 첫 제출 뒤 워커가 집었으면 `RUNNING` 이다.

    거동 자체는 선재이고 Kotlin fake servicer 의 재사용 갈래(`setState(existingJob.state)`)와
    같다. 앞 판은 이 자리를 잠그는 test 가 없어 문면이 무조건으로 읽혔다(cr M-4)."""
    store = JobStore(journal=JobJournal(tmp_path / "journal"), pending_queue_max=4)
    runner = _SynchronousStartRunner(store)
    servicer = TrainingJobServicer(
        store=store,
        runner=runner,  # type: ignore[arg-type]
        is_ready=lambda: True,
        pipeline_factory=lambda spec: _InstantSucceedingPipeline(),  # type: ignore[arg-type,return-value]
        dataset_uri_schemes=frozenset({"file"}),
        idempotency_key_max_chars=256,
    )

    first = servicer.StartTraining(_valid_request(), context=None)
    assert first.handle.state == training_pb2.JOB_STATE_ACCEPTED  # 새 job

    again = servicer.StartTraining(_valid_request(), context=None)

    assert again.handle.job_id == first.handle.job_id  # 새 job 을 만들지 않았다
    assert again.handle.state == training_pb2.JOB_STATE_RUNNING  # 현재 상태
    assert runner.started == [first.handle.job_id]  # 재사용은 적재가 아니다
