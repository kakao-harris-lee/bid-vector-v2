"""`ml_engine.training.jobs.servicer` — `TrainingJobServicer`(scope.md ⑥). grpc 진입점
예외(5A ③, pyproject.toml `ignore_imports` 둘 중 하나) — `training`은 여전히 `serving`을
모른다(forbidden 계약, `serving.readiness.ReadinessGate`를 직접 import 하지 않는다).
준비 상태는 조립 근이 주입하는 `Callable[[], bool]`(`is_ready`)로만 받는다.

envelope·필드 검증은 이 모듈이 독립적으로 갖는다(`serving.status`를 import 할 수 없어
생기는 작은 중복 — 2B/2C 생성 로직 중복과 같은 갈래, checklist.md 「계약과 어긋나 판단이
필요했던 자리」).
"""

from __future__ import annotations

import re
from collections.abc import Callable
from datetime import UTC, datetime
from enum import StrEnum
from urllib.parse import urlparse

import grpc
from google.protobuf.timestamp_pb2 import Timestamp  # type: ignore[import-untyped]

from ml_engine.contracts import error_pb2, training_pb2, training_pb2_grpc
from ml_engine.training.jobs.pipeline import DatasetRefInput, TrainingPipeline
from ml_engine.training.jobs.runner import JobRunner
from ml_engine.training.jobs.state import (
    ArtifactReference,
    EvaluationReportReference,
    JobEvent,
    JobFailure,
    JobFailureCode,
    JobRecord,
    JobState,
    TransitionRejected,
    transition,
)
from ml_engine.training.jobs.store import Conflict, InMemoryJobStore, Started
from ml_engine.training.spec import (
    TrainingSpec,
    UnsupportedTrainingSpec,
    resolve_training_spec,
)

_SHA256_HEX_PATTERN = re.compile(r"^[0-9a-f]{64}$")


class _ValidationDetailCode(StrEnum):
    """`ApplicationFailure.detail_code` 닫힌 어휘 — `serving.status.ValidationDetailCode`
    와 같은 값들을 독립 소유한다(layer 경계상 import 불가)."""

    REQUEST_ID_EMPTY = "REQUEST_ID_EMPTY"
    CORRELATION_ID_EMPTY = "CORRELATION_ID_EMPTY"
    IDEMPOTENCY_KEY_EMPTY = "IDEMPOTENCY_KEY_EMPTY"
    IDEMPOTENCY_KEY_TOO_LONG = "IDEMPOTENCY_KEY_TOO_LONG"
    DATASET_URI_SCHEME_UNSUPPORTED = "DATASET_URI_SCHEME_UNSUPPORTED"
    MANIFEST_CHECKSUM_INVALID = "MANIFEST_CHECKSUM_INVALID"
    REQUESTED_RELEASE_ID_UNSUPPORTED = "REQUESTED_RELEASE_ID_UNSUPPORTED"
    JOB_ID_EMPTY = "JOB_ID_EMPTY"


def _envelope_violation(
    request_id: str, correlation_id: str
) -> _ValidationDetailCode | None:
    if not request_id.strip():
        return _ValidationDetailCode.REQUEST_ID_EMPTY
    if not correlation_id.strip():
        return _ValidationDetailCode.CORRELATION_ID_EMPTY
    return None


def _fill_failure(
    failure: training_pb2.ApplicationFailure,
    *,
    code: int,
    retryable: bool,
    detail_code: StrEnum,
) -> None:
    failure.code = code
    failure.retryable = retryable
    failure.detail_code = detail_code.value


def _to_timestamp(value: datetime) -> Timestamp:
    timestamp = Timestamp()
    timestamp.FromDatetime(value)
    return timestamp


# `serving.prediction.BidPredictionServicer`와 같은 구조적 사정(생성 base 가 mypy 에는
# `Any`) — 근거는 그 파일 주석 참고.
class TrainingJobServicer(training_pb2_grpc.TrainingJobServiceServicer):  # type: ignore[misc]
    """`is_ready`는 preload 성공 여부만 본다(정책 없이 job 을 돌리지 않는다, 우회 (1)).
    `pipeline_factory`는 조립 근이 만든 `TrainingSpec → TrainingPipeline` 팩토리다."""

    def __init__(
        self,
        *,
        store: InMemoryJobStore,
        runner: JobRunner,
        is_ready: Callable[[], bool],
        pipeline_factory: Callable[[TrainingSpec], TrainingPipeline],
        dataset_uri_schemes: frozenset[str],
        idempotency_key_max_chars: int,
    ) -> None:
        self._store = store
        self._runner = runner
        self._is_ready = is_ready
        self._pipeline_factory = pipeline_factory
        self._dataset_uri_schemes = dataset_uri_schemes
        self._idempotency_key_max_chars = idempotency_key_max_chars

    def StartTraining(  # noqa: N802 — grpc 생성 시그니처
        self,
        request: training_pb2.StartTrainingRequest,
        context: grpc.ServicerContext,
    ) -> training_pb2.StartTrainingResponse:
        response = training_pb2.StartTrainingResponse()
        violation = self._validate_start(request)
        if violation is not None:
            _fill_failure(
                response.failure,
                code=error_pb2.FAILURE_CODE_INVALID_REQUEST,
                retryable=False,
                detail_code=violation,
            )
            return response

        spec = resolve_training_spec(request.training_spec_version)
        if isinstance(spec, UnsupportedTrainingSpec):
            response.failure.code = error_pb2.FAILURE_CODE_UNSUPPORTED_TRAINING_SPEC
            response.failure.retryable = False
            response.failure.detail_code = "UNSUPPORTED_TRAINING_SPEC"
            return response

        if not self._is_ready():
            response.failure.code = error_pb2.FAILURE_CODE_MODEL_NOT_READY
            response.failure.retryable = True
            response.failure.detail_code = "SERVER_NOT_READY"
            return response

        return self._accept_or_reuse(request, spec)

    def _validate_start(
        self, request: training_pb2.StartTrainingRequest
    ) -> _ValidationDetailCode | None:
        violation = _envelope_violation(
            request.envelope.request_id, request.envelope.correlation_id
        )
        if violation is not None:
            return violation
        if not request.idempotency_key.strip():
            return _ValidationDetailCode.IDEMPOTENCY_KEY_EMPTY
        if len(request.idempotency_key) > self._idempotency_key_max_chars:
            return _ValidationDetailCode.IDEMPOTENCY_KEY_TOO_LONG
        if request.HasField("requested_release_id"):
            return _ValidationDetailCode.REQUESTED_RELEASE_ID_UNSUPPORTED
        scheme = urlparse(request.dataset.uri).scheme
        if scheme not in self._dataset_uri_schemes:
            return _ValidationDetailCode.DATASET_URI_SCHEME_UNSUPPORTED
        if not _SHA256_HEX_PATTERN.match(request.dataset.manifest_checksum):
            return _ValidationDetailCode.MANIFEST_CHECKSUM_INVALID
        return None

    def _accept_or_reuse(
        self, request: training_pb2.StartTrainingRequest, spec: TrainingSpec
    ) -> training_pb2.StartTrainingResponse:
        response = training_pb2.StartTrainingResponse()
        outcome = self._store.start_or_reuse(
            request.idempotency_key,
            request.dataset.dataset_id,
            accepted_at=datetime.now(UTC),
        )
        if isinstance(outcome, Conflict):
            response.failure.code = error_pb2.FAILURE_CODE_IDEMPOTENCY_CONFLICT
            response.failure.retryable = False
            response.failure.detail_code = "IDEMPOTENCY_CONFLICT"
            return response
        if isinstance(outcome, Started):
            dataset_ref = DatasetRefInput(
                uri=request.dataset.uri,
                manifest_checksum=request.dataset.manifest_checksum,
                dataset_id=request.dataset.dataset_id,
            )
            self._runner.submit(
                outcome.record.job_id, dataset_ref, self._pipeline_factory(spec)
            )
        response.handle.job_id = outcome.record.job_id
        response.handle.state = _job_state_to_proto(outcome.record.state)
        return response

    def GetTrainingJob(  # noqa: N802
        self,
        request: training_pb2.GetTrainingJobRequest,
        context: grpc.ServicerContext,
    ) -> training_pb2.GetTrainingJobResponse:
        response = training_pb2.GetTrainingJobResponse()
        record = self._store.get(request.job_id)
        if record is None:
            response.failure.code = error_pb2.FAILURE_CODE_JOB_NOT_FOUND
            response.failure.retryable = False
            response.failure.detail_code = "JOB_NOT_FOUND"
            return response
        response.job.CopyFrom(_to_proto_job(record))
        return response

    def CancelTrainingJob(  # noqa: N802
        self,
        request: training_pb2.CancelTrainingJobRequest,
        context: grpc.ServicerContext,
    ) -> training_pb2.CancelTrainingJobResponse:
        response = training_pb2.CancelTrainingJobResponse()
        record = self._store.get(request.job_id)
        if record is None:
            response.failure.code = error_pb2.FAILURE_CODE_JOB_NOT_FOUND
            response.failure.retryable = False
            response.failure.detail_code = "JOB_NOT_FOUND"
            return response

        cancelled = transition(record, JobEvent.CANCEL, at=datetime.now(UTC))
        if isinstance(cancelled, TransitionRejected):  # pragma: no cover — 표가 CANCEL
            # 을 모든 상태에 정의하므로 도달 불가(state.py `_TRANSITIONS`).
            response.failure.code = error_pb2.FAILURE_CODE_JOB_NOT_FOUND
            response.failure.retryable = False
            response.failure.detail_code = "TRANSITION_REJECTED"
            return response
        self._store.replace(cancelled)
        self._runner.cancel(request.job_id)
        response.job.CopyFrom(_to_proto_job(cancelled))
        return response


def _job_state_to_proto(state: JobState) -> int:
    # `training_pb2.JOB_STATE_*`는 생성물(mypy 관점 `Any`) 이라 dict 값 타입을 명시
    # 하지 않으면 인덱싱 결과가 `Any`로 새 나간다("Returning Any" — 실측). 명시 애너
    # 테이션으로 이 자리에서 `int`로 좁힌다.
    mapping: dict[JobState, int] = {
        JobState.ACCEPTED: training_pb2.JOB_STATE_ACCEPTED,
        JobState.RUNNING: training_pb2.JOB_STATE_RUNNING,
        JobState.SUCCEEDED: training_pb2.JOB_STATE_SUCCEEDED,
        JobState.FAILED: training_pb2.JOB_STATE_FAILED,
        JobState.CANCELLED: training_pb2.JOB_STATE_CANCELLED,
    }
    return mapping[state]


def _job_failure_code_to_proto(code: JobFailureCode) -> int:
    mapping: dict[JobFailureCode, int] = {
        JobFailureCode.DATASET_UNREADABLE: training_pb2.JOB_FAILURE_CODE_DATASET_UNREADABLE,
        JobFailureCode.DATASET_CHECKSUM_MISMATCH: (
            training_pb2.JOB_FAILURE_CODE_DATASET_CHECKSUM_MISMATCH
        ),
        JobFailureCode.TRAINING_ERROR: training_pb2.JOB_FAILURE_CODE_TRAINING_ERROR,
        JobFailureCode.EVALUATION_ERROR: training_pb2.JOB_FAILURE_CODE_EVALUATION_ERROR,
        JobFailureCode.CANCELLED_BY_REQUEST: (
            training_pb2.JOB_FAILURE_CODE_CANCELLED_BY_REQUEST
        ),
        JobFailureCode.WORKER_RESOURCE_EXHAUSTED: (
            training_pb2.JOB_FAILURE_CODE_WORKER_RESOURCE_EXHAUSTED
        ),
    }
    return mapping[code]


def _fill_artifact(
    target: training_pb2.ArtifactReference, artifact: ArtifactReference
) -> None:
    target.uri = artifact.uri
    target.release.release_id = artifact.release_id
    target.release.artifact_checksum = artifact.artifact_checksum
    target.release.feature_schema_version = artifact.feature_schema_version
    target.release.code_version = artifact.code_version
    target.release.dataset_id = artifact.dataset_id
    target.manifest_schema_version = artifact.manifest_schema_version


def _fill_evaluation(
    target: training_pb2.EvaluationReportReference,
    evaluation: EvaluationReportReference,
) -> None:
    target.uri = evaluation.uri
    target.checksum = evaluation.checksum
    target.report_schema_version = evaluation.report_schema_version


def _fill_job_failure(target: training_pb2.JobFailure, failure: JobFailure) -> None:
    target.code = _job_failure_code_to_proto(failure.code)
    target.retryable = failure.retryable
    target.detail_code = failure.detail_code


def _to_proto_job(record: JobRecord) -> training_pb2.TrainingJob:
    job = training_pb2.TrainingJob()
    job.job_id = record.job_id
    job.state = _job_state_to_proto(record.state)
    job.accepted_at.CopyFrom(_to_timestamp(record.accepted_at))
    if record.started_at is not None:
        job.started_at.CopyFrom(_to_timestamp(record.started_at))
    if record.finished_at is not None:
        job.finished_at.CopyFrom(_to_timestamp(record.finished_at))
    if record.artifact is not None:
        _fill_artifact(job.artifact, record.artifact)
    if record.evaluation is not None:
        _fill_evaluation(job.evaluation, record.evaluation)
    if record.failure is not None:
        _fill_job_failure(job.failure, record.failure)
    return job
