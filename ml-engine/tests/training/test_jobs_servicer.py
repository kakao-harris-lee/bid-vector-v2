"""RED — `ml_engine.training.jobs.servicer.TrainingJobServicer`(scope.md ⑥). 검증
규칙표 + 멱등 + readiness + 전이 in-process(실 servicer, fake pipeline/runner 조합)."""

from __future__ import annotations

import threading
import time

from ml_engine.contracts import error_pb2, training_pb2
from ml_engine.training.jobs.pipeline import (
    ArtifactRefValue,
    CancelToken,
    DatasetRefInput,
    EvaluationRefValue,
    PipelineOutcome,
)
from ml_engine.training.jobs.runner import JobRunner
from ml_engine.training.jobs.servicer import TrainingJobServicer
from ml_engine.training.jobs.store import InMemoryJobStore
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
    *, is_ready: bool = True, pipeline: object | None = None
) -> tuple[TrainingJobServicer, JobRunner, InMemoryJobStore]:
    store = InMemoryJobStore()
    runner = JobRunner(store, max_workers=2)
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


def test_start_training_returns_accepted_immediately() -> None:
    servicer, runner, _store_unused = _build_servicer()
    response = servicer.StartTraining(_valid_request(), context=None)
    assert response.WhichOneof("result") == "handle"
    assert response.handle.state == training_pb2.JOB_STATE_ACCEPTED
    runner.shutdown(wait=True)


def test_empty_request_id_is_invalid_request() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = _valid_request()
    request.envelope.request_id = ""
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_empty_idempotency_key_is_invalid_request() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = _valid_request(idempotency_key="")
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_idempotency_key_too_long_is_invalid_request() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = _valid_request(idempotency_key="x" * 257)
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_requested_release_id_present_is_invalid_request() -> None:
    """설계 검토 우회 (14)."""
    servicer, runner, _store_unused = _build_servicer()
    request = _valid_request()
    request.requested_release_id = "some-release"
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_unsupported_dataset_uri_scheme_is_invalid_request() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = _valid_request()
    request.dataset.uri = "s3://bucket/dataset"
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_malformed_manifest_checksum_is_invalid_request() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = _valid_request()
    request.dataset.manifest_checksum = "not-a-checksum"
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_unknown_training_spec_version_is_unsupported_training_spec() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = _valid_request(training_spec_version="unknown-v9")
    response = servicer.StartTraining(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC
    runner.shutdown(wait=True)


def test_not_ready_returns_model_not_ready_and_retryable() -> None:
    """우회 (1) — 정책 preload 실패 상태에서는 job 을 못 돌린다."""
    servicer, runner, _store_unused = _build_servicer(is_ready=False)
    response = servicer.StartTraining(_valid_request(), context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_MODEL_NOT_READY
    assert response.failure.retryable is True
    runner.shutdown(wait=True)


def test_same_idempotency_key_twice_returns_same_job_id() -> None:
    servicer, runner, _store_unused = _build_servicer()
    first = servicer.StartTraining(_valid_request(), context=None)
    second = servicer.StartTraining(_valid_request(), context=None)
    assert first.handle.job_id == second.handle.job_id
    runner.shutdown(wait=True)


def test_same_key_different_dataset_is_idempotency_conflict() -> None:
    servicer, runner, _store_unused = _build_servicer()
    servicer.StartTraining(_valid_request(), context=None)
    conflicting = _valid_request()
    conflicting.dataset.dataset_id = "ds-other"
    response = servicer.StartTraining(conflicting, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_IDEMPOTENCY_CONFLICT
    runner.shutdown(wait=True)


def test_get_training_job_unknown_job_id_is_job_not_found() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = "unknown"
    response = servicer.GetTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_JOB_NOT_FOUND
    runner.shutdown(wait=True)


def test_get_training_job_returns_eventually_succeeded_job() -> None:
    servicer, runner, _store_unused = _build_servicer()
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


def test_cancel_unknown_job_id_is_job_not_found() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = training_pb2.CancelTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = "unknown"
    response = servicer.CancelTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_JOB_NOT_FOUND
    runner.shutdown(wait=True)


def test_cancel_accepted_job_transitions_to_cancelled() -> None:
    servicer, runner, _store_unused = _build_servicer()
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


# ---- H-3 — GetTrainingJob·CancelTrainingJob envelope 검증(verifier r1) ----


def test_get_training_job_empty_request_id_is_invalid_request_not_job_not_found() -> (
    None
):
    servicer, runner, _store_unused = _build_servicer()
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = ""
    request.envelope.correlation_id = "c"
    request.job_id = "some-job"
    response = servicer.GetTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_get_training_job_empty_correlation_id_is_invalid_request() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = ""
    request.job_id = "some-job"
    response = servicer.GetTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_get_training_job_empty_job_id_is_invalid_request_not_job_not_found() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = training_pb2.GetTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = ""
    response = servicer.GetTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "JOB_ID_EMPTY"
    runner.shutdown(wait=True)


def test_cancel_training_job_empty_request_id_is_invalid_request() -> None:
    servicer, runner, _store_unused = _build_servicer()
    request = training_pb2.CancelTrainingJobRequest()
    request.envelope.request_id = ""
    request.envelope.correlation_id = "c"
    request.job_id = "some-job"
    response = servicer.CancelTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    runner.shutdown(wait=True)


def test_cancel_training_job_empty_job_id_is_invalid_request_not_job_not_found() -> (
    None
):
    servicer, runner, _store_unused = _build_servicer()
    request = training_pb2.CancelTrainingJobRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    request.job_id = ""
    response = servicer.CancelTrainingJob(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "JOB_ID_EMPTY"
    runner.shutdown(wait=True)


# ---- H-2 — store 경합(양방향): 종료 상태가 역행하지 않는다(verifier r1, 실 servicer·
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


def test_cancel_committed_before_pipeline_finishes_state_never_regresses_to_succeeded() -> (
    None
):
    """H-2 정방향 — cancel 이 먼저 store 에 CANCELLED 를 커밋한 뒤 파이프라인이 뒤늦게
    성공으로 끝나도, 그 결과가 CANCELLED 를 SUCCEEDED 로 덮어쓰지 않는다."""
    release_event = threading.Event()
    servicer, runner, store = _build_servicer(
        pipeline=_BlockingThenSucceedingPipeline(release_event)
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


def test_runner_finishes_before_cancel_request_returns_succeeded_idempotent() -> None:
    """H-2 역방향(verifier r1 「다음 라운드에 확인할 것」 2) — 러너가 먼저 SUCCEEDED 를
    store 에 커밋한 뒤에야 CancelTrainingJob 이 도착하면, 종료 상태 멱등 no-op 규칙대로
    CancelTrainingJob 은 CANCELLED 를 강제하지 않고 **실제 최신 상태**(SUCCEEDED)를
    돌려준다 — stale read 로 계산한 CANCELLED 로 store 를 덮어쓰지 않는다."""
    servicer, runner, store = _build_servicer()
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
