"""`ml_engine.training.jobs.servicer` — `TrainingJobServicer`(scope.md ⑥). grpc 진입점
예외(5A ③, pyproject.toml `ignore_imports` 둘 중 하나) — `training`은 여전히 `serving`을
모른다(forbidden 계약, `serving.readiness.ReadinessGate`를 직접 import 하지 않는다).
준비 상태는 조립 근이 주입하는 `Callable[[], bool]`(`is_ready`)로만 받는다.

envelope·필드 검증은 이 모듈이 독립적으로 갖는다(`serving.status`를 import 할 수 없어
생기는 작은 중복 — 2B/2C 생성 로직 중복과 같은 갈래, checklist.md 「계약과 어긋나 판단이
필요했던 자리」).
"""

from __future__ import annotations

import logging
import re
from collections.abc import Callable
from datetime import UTC, datetime
from enum import StrEnum
from urllib.parse import urlparse

import grpc
from google.protobuf.timestamp_pb2 import Timestamp  # type: ignore[import-untyped]

from ml_engine.contracts import error_pb2, training_pb2, training_pb2_grpc
from ml_engine.training.jobs.journal import JobSubmission, JournalFailed
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
)
from ml_engine.training.jobs.store import Conflict, JobStore, Started
from ml_engine.training.spec import (
    TrainingSpec,
    UnsupportedTrainingSpec,
    resolve_training_spec,
)

_logger = logging.getLogger(__name__)

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
    # cr L-5(선재 결함) — 빈 `dataset_id` 는 `JobRecord.__post_init__` 의 `ValueError` 로
    # 핸들러를 벗어났다(gRPC `UNKNOWN`). application failure 층에서 거부한다(ADR 0010 D-3).
    # `JOB_ID_EMPTY` 와 같이 `serving.status.ValidationDetailCode` 에는 없는 값이다
    # (training 전용 필드의 검증이라 그 어휘가 들 자리가 아니다).
    DATASET_ID_EMPTY = "DATASET_ID_EMPTY"


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


def _fill_store_unavailable(
    failure: training_pb2.ApplicationFailure, journal_failed: JournalFailed
) -> None:
    """저널(job 영속)에 쓰지 못했다 — 요청을 **받지 않은** 것이고 재시도 대상이다.

    2A `FailureCode` 여덟에 「저장소를 쓸 수 없다」가 없다. 이 slice 는 proto 메시지·
    enum 을 바꾸지 않으므로(out_scope) 그 중 뜻이 가장 가까운 `MODEL_NOT_READY`
    (retryable, ADR 0010 §4 「프로세스는 살아 있고 계약이 정의한 상태」)에 얹고
    `detail_code` 로 사유를 좁힌다 — 전용 코드 신설은 `OPEN-6B4-STORE-UNAVAILABLE-CODE`.
    """
    _logger.error("job 저널 쓰기 실패로 요청을 받지 못했다 — %s", journal_failed)
    failure.code = error_pb2.FAILURE_CODE_MODEL_NOT_READY
    failure.retryable = True
    failure.detail_code = "JOB_STORE_UNAVAILABLE"


def _log_discarded(outcome: Started) -> None:
    """상한 폐기를 **폐기 시점에** 남긴다(vr H-3). `Started.discarded` 를 아무도 읽지
    않으면 job_id 를 쥔 쪽 말고는 폐기를 알 방법이 없다 — `ListTrainingJobs` 도 metric 도
    없고(scope out_scope, `OPEN-6B4-QUEUE-METRIC`), 워커가 뒤늦게 그 job 에 닿아 내는
    `_NotStarted` info 는 폐기 시점도 사유도 아니며 재기동이 먼저 오면 그조차 없다.
    runbook §2 「폐기를 어떻게 아는가」의 「서버 로그: 있다」 행이 이 자리다."""
    for record in outcome.discarded:
        _logger.warning(
            "대기 큐 상한 초과로 대기 job 을 폐기했다 — job_id=%s accepted_at=%s 사유=%s",
            record.job_id,
            record.accepted_at.isoformat(),
            # 2C 조합 불변식상 `FAILED` 는 `failure` 를 반드시 든다(`JobRecord.__post_init__`).
            record.failure.detail_code if record.failure is not None else "UNKNOWN",
        )


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
        store: JobStore,
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
        if not request.dataset.dataset_id.strip():
            return _ValidationDetailCode.DATASET_ID_EMPTY
        return None

    def _accept_or_reuse(
        self, request: training_pb2.StartTrainingRequest, spec: TrainingSpec
    ) -> training_pb2.StartTrainingResponse:
        response = training_pb2.StartTrainingResponse()
        outcome = self._store.start_or_reuse(
            request.idempotency_key,
            request.dataset.dataset_id,
            accepted_at=datetime.now(UTC),
            # 재기동 뒤 **대기 job 을 실제로 실행할 수 있도록** 제출 입력을 함께 적는다
            # (M6/6B-4 Q-5 — 저널 항목의 일부, wire 메시지 변경 0).
            submission=JobSubmission(
                dataset_uri=request.dataset.uri,
                manifest_checksum=request.dataset.manifest_checksum,
                training_spec_version=request.training_spec_version,
            ),
        )
        if isinstance(outcome, Conflict):
            response.failure.code = error_pb2.FAILURE_CODE_IDEMPOTENCY_CONFLICT
            response.failure.retryable = False
            response.failure.detail_code = "IDEMPOTENCY_CONFLICT"
            return response
        if isinstance(outcome, JournalFailed):
            _fill_store_unavailable(response.failure, outcome)
            return response
        # R2-1 유지 — `start_or_reuse` 가 돌려준 스냅숏을 그대로 쓰지 않는다. 그 사이에
        # 다른 writer(`CancelTrainingJob`)가 종단으로 옮겼을 수 있다. 저장소를 다시 읽어
        # **실제 최신 상태**로 응답한다(폐기로 `FAILED` 가 된 경우도 같은 자리에서
        # 정직해진다).
        #
        # 이 읽기는 `_enqueue` **앞**이다(cr H-1, 팀장 결정 (a)). 뒤에 두면 워커가 그
        # 사이 job 을 집어 `RUNNING` 을 커밋할 수 있고, 그러면 응답이 proto
        # `StartTrainingResponse` 주석(「**새 job** 은 즉시 `ACCEPTED`」)을 깬다 — 실측
        # `job_workers: 1` 에서 400회 중 1회. `start_or_reuse` 가 한 잠금 안에서 적재·
        # 폐기·저널을 끝냈으므로 그 직후 상태는 결정적이고(`ACCEPTED`, 또는 시계 역행으로
        # 자기 폐기된 `FAILED`), 빠지는 값은 「워커가 이미 집었는가」 하나 — 그것이 바로
        # 문면이 금지하는 값이다.
        current = self._store.get(outcome.record.job_id)
        if isinstance(outcome, Started):
            _log_discarded(outcome)
            self._enqueue(outcome.record.job_id, request, spec)
        response.handle.job_id = outcome.record.job_id
        response.handle.state = _job_state_to_proto(
            current.state if current is not None else outcome.record.state
        )
        return response

    def _enqueue(
        self,
        job_id: str,
        request: training_pb2.StartTrainingRequest,
        spec: TrainingSpec,
    ) -> None:
        """실행 대기열에 넣기만 한다 — `RUNNING` 전이는 워커가 집는 순간에 일어난다
        (M6/6B-4 Q-2). 그래서 이 호출은 상태를 돌려줄 것이 없다."""
        self._runner.submit(
            job_id,
            DatasetRefInput(
                uri=request.dataset.uri,
                manifest_checksum=request.dataset.manifest_checksum,
                dataset_id=request.dataset.dataset_id,
            ),
            self._pipeline_factory(spec),
        )

    def GetTrainingJob(  # noqa: N802
        self,
        request: training_pb2.GetTrainingJobRequest,
        context: grpc.ServicerContext,
    ) -> training_pb2.GetTrainingJobResponse:
        response = training_pb2.GetTrainingJobResponse()
        violation = self._validate_get_or_cancel(request.envelope, request.job_id)
        if violation is not None:
            _fill_failure(
                response.failure,
                code=error_pb2.FAILURE_CODE_INVALID_REQUEST,
                retryable=False,
                detail_code=violation,
            )
            return response

        record = self._store.get(request.job_id)
        if record is None:
            response.failure.code = error_pb2.FAILURE_CODE_JOB_NOT_FOUND
            response.failure.retryable = False
            response.failure.detail_code = "JOB_NOT_FOUND"
            return response
        response.job.CopyFrom(_to_proto_job(record))
        return response

    @staticmethod
    def _validate_get_or_cancel(
        envelope: training_pb2.RequestEnvelope, job_id: str
    ) -> _ValidationDetailCode | None:
        """H-3 — `GetTrainingJob`·`CancelTrainingJob`도 `RequestEnvelope`
        를 검증한다(`common.proto` 「모든 RPC의 필수 봉투」, 설계 검토 (5) 7). 빈
        `job_id`는 구조적으로 무효한 요청이라 `JOB_NOT_FOUND`가 아니라
        `INVALID_REQUEST(JOB_ID_EMPTY)`다 — 미지 job_id(유효한 형식이지만 없음)와
        구분한다."""
        violation = _envelope_violation(envelope.request_id, envelope.correlation_id)
        if violation is not None:
            return violation
        if not job_id.strip():
            return _ValidationDetailCode.JOB_ID_EMPTY
        return None

    def CancelTrainingJob(  # noqa: N802
        self,
        request: training_pb2.CancelTrainingJobRequest,
        context: grpc.ServicerContext,
    ) -> training_pb2.CancelTrainingJobResponse:
        response = training_pb2.CancelTrainingJobResponse()
        violation = self._validate_get_or_cancel(request.envelope, request.job_id)
        if violation is not None:
            _fill_failure(
                response.failure,
                code=error_pb2.FAILURE_CODE_INVALID_REQUEST,
                retryable=False,
                detail_code=violation,
            )
            return response

        # H-2 — `get()`→`transition()`→`replace()`를 따로 부르지 않는다. `apply_
        # transition`이 한 잠금 아래 원자적으로 수행해, 파이프라인 종료 전이(runner)
        # 와 경합해도 실제로 나중에 실행되는 쪽이 최신 상태를 보고 계산한다(stale
        # record 로 인한 CANCELLED→SUCCEEDED 역행 방지).
        result = self._store.apply_transition(
            request.job_id, JobEvent.CANCEL, at=datetime.now(UTC)
        )
        if result is None:
            response.failure.code = error_pb2.FAILURE_CODE_JOB_NOT_FOUND
            response.failure.retryable = False
            response.failure.detail_code = "JOB_NOT_FOUND"
            return response
        if isinstance(result, JournalFailed):
            # 취소를 **영속시키지 못했다**. 메모리도 바뀌지 않았으므로 상태는 그대로다 —
            # 「취소됐다」고 답하면 거짓이 된다(M6/6B-4 우회 (9)).
            _fill_store_unavailable(response.failure, result)
            return response
        if isinstance(result, TransitionRejected):  # pragma: no cover — 표가 CANCEL 을
            # 모든 상태에 정의하므로 도달 불가(state.py `_TRANSITIONS`).
            response.failure.code = error_pb2.FAILURE_CODE_JOB_NOT_FOUND
            response.failure.retryable = False
            response.failure.detail_code = "TRANSITION_REJECTED"
            return response
        self._runner.cancel(request.job_id)
        response.job.CopyFrom(_to_proto_job(result))
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
