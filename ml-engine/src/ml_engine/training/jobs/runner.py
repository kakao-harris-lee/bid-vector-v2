"""`ml_engine.training.jobs.runner` — `JobRunner`(scope.md ⑦, ADR 0010 §6 fork 금지).
`ThreadPoolExecutor(max_workers=policy.job_workers)`만 쓴다 — `os.fork`·`multiprocessing`
없음(정적 test 가 이 파일에 그 이름이 없음을 확인한다).

취소 존중: 파이프라인이 단계 경계에서 `PipelineCancelled`를 내면 `CANCELLED`로 전이한다
(계산은 이미 파이프라인 내부에서 중단됐다 — D-2D-7). future 콜백이 예외로 죽으면
`FAILED(TRAINING_ERROR, RUNNER_CRASHED)`로 전이한다(설계 검토 우회 (13) — `RUNNING`에
영원히 머무는 job 방지). 예외 자체는 삼키지 않고 로그로 남긴다(`BLE` 없음, 구체 예외
없이 `Exception`을 잡지만 이것은 **콜백 경계**이지 도메인 실패 분류가 아니다 — 파이프라인
내부는 이미 결과 타입만 다루므로 여기 도달하는 예외는 전부 "분류되지 않은 크래시"다)."""

from __future__ import annotations

import logging
import threading
from concurrent.futures import Future, ThreadPoolExecutor
from datetime import UTC, datetime

from ml_engine.training.jobs.pipeline import (
    CancelToken,
    DatasetRefInput,
    DetailCode,
    PipelineCancelled,
    PipelineFailed,
    PipelineOutcome,
    TrainingPipeline,
)
from ml_engine.training.jobs.state import (
    ArtifactReference,
    EvaluationReportReference,
    JobEvent,
    JobFailure,
    JobFailureCode,
    JobRecord,
    TransitionRejected,
)
from ml_engine.training.jobs.store import InMemoryJobStore

_logger = logging.getLogger(__name__)


class JobRunner:
    """`store`에 있는 job 을 스레드 풀에서 실행한다. `cancel(job_id)`는 진행 중인
    실행의 `CancelToken`을 세운다 — 파이프라인이 다음 단계 경계에서 확인한다."""

    def __init__(self, store: InMemoryJobStore, *, max_workers: int) -> None:
        if max_workers < 1:
            raise ValueError(f"max_workers 는 1 이상이어야 합니다: {max_workers}")
        self._store = store
        self._executor = ThreadPoolExecutor(max_workers=max_workers)
        self._cancel_tokens: dict[str, CancelToken] = {}
        self._lock_tokens_guard = threading.Lock()

    def submit(
        self, job_id: str, dataset_ref: DatasetRefInput, pipeline: TrainingPipeline
    ) -> JobRecord | TransitionRejected:
        """`job_id`를 `RUNNING`으로 전이하고 파이프라인을 스레드 풀에 제출한다.

        verifier r2 R2-1 — `_accept_or_reuse`가 `start_or_reuse`로 job 을 ACCEPTED
        로 만든 뒤 이 메서드를 호출하는 사이, 다른 writer(`CancelTrainingJob`)가
        먼저 CANCEL 을 커밋할 수 있다(경합에서 Cancel 이 이긴다). 이전에는 START
        전이 거부를 `ValueError`로 던져 그 StartTraining 호출 자체가 처리되지 않은
        예외로 gRPC `UNKNOWN`이 됐다 — **예외로 알리지 않는다.** 거부되면 파이프라인을
        전혀 제출하지 않고 `TransitionRejected`를 돌려준다(호출자가 최신 상태를
        다시 읽어 정직하게 응답한다)."""
        started = self._store.apply_transition(
            job_id, JobEvent.START, at=datetime.now(UTC)
        )
        if started is None:
            raise KeyError(
                f"알 수 없는 job_id: {job_id}"
            )  # 구조적으로 불가능(방금 생성)
        if isinstance(started, TransitionRejected):
            _logger.info(
                "job_id=%s START 전이 거부(이미 다른 writer 가 종료 상태로 옮김) — %s",
                job_id,
                started,
            )
            return started

        token = CancelToken()
        with self._lock_tokens_guard:
            self._cancel_tokens[job_id] = token

        future = self._executor.submit(pipeline.run, dataset_ref, token)

        def _callback(
            done: Future[PipelineOutcome | PipelineFailed | PipelineCancelled],
        ) -> None:
            self._on_done(job_id, done)

        future.add_done_callback(_callback)
        return started

    def cancel(self, job_id: str) -> None:
        """진행 중인 job 의 `CancelToken`을 세운다 — job 이 실행 중이 아니면 no-op
        (servicer 의 `CancelTrainingJob`이 전이표로 상태 자체를 이미 처리한다)."""
        with self._lock_tokens_guard:
            token = self._cancel_tokens.get(job_id)
        if token is not None:
            token.cancel()

    def cancel_all(self) -> None:
        """진행 중인 job 전부의 `CancelToken`을 세운다(M-3, `app.server` 의 SIGTERM
        경로). 각 파이프라인이 다음 단계 경계에서 이를 확인해 스스로 멈춘다 — 스레드를
        강제 중단하지는 않는다(fork 없음과 같은 이유로 강제 종료 API 가 없다)."""
        with self._lock_tokens_guard:
            tokens = tuple(self._cancel_tokens.values())
        for token in tokens:
            token.cancel()

    def _on_done(
        self,
        job_id: str,
        future: Future[PipelineOutcome | PipelineFailed | PipelineCancelled],
    ) -> None:
        with self._lock_tokens_guard:
            self._cancel_tokens.pop(job_id, None)

        if self._store.get(job_id) is None:
            _logger.error("job_id=%s 완료 콜백 도달 — 저장소에 없음", job_id)
            return
        # H-2 — 여기서 "이미 CANCELLED 면 반환"이라는 별도 조기 검사를 하지 않는다.
        # `apply_transition`이 읽기·전이 계산·쓰기를 한 잠금 아래 수행하므로, 실제로
        # 이미 CANCELLED(또는 다른 종료 상태)라면 아래 `_transition_*` 호출이 그
        # **최신** 상태를 보고 전이표대로 거부(멱등 no-op 또는 `TransitionRejected`)
        # 한다 — 별도 조기 검사가 오히려 그 자체로 stale read 가 될 수 있었다.

        # `future.exception()`은 콜백 안에서 이미 완료된 future 에 대해 즉시 반환한다
        # (블로킹 없음) — 콜러블이 던진 예외를 **재발생 없이** 조회하는 표준 API 라
        # `except Exception:`(BLE, 위협 모델 (m))을 쓰지 않고도 "분류되지 않은 크래시"
        # (우회 13)를 감지할 수 있다. 예외 자체는 로그로 남기고 삼키지 않는다.
        crash = future.exception()
        if crash is not None:
            _logger.error(
                "job_id=%s 파이프라인 실행 중 예외 — RUNNER_CRASHED 로 전이",
                job_id,
                exc_info=crash,
            )
            self._transition_failed(
                job_id, JobFailureCode.TRAINING_ERROR, DetailCode.RUNNER_CRASHED
            )
            return
        outcome = future.result()

        if isinstance(outcome, PipelineCancelled):
            self._transition_cancelled(job_id)
        elif isinstance(outcome, PipelineFailed):
            self._transition_failed(job_id, outcome.code, outcome.detail_code)
        else:
            # `Future`의 타입이 `PipelineOutcome | PipelineFailed | PipelineCancelled`
            # 셋으로 닫혀 있어(`_callback`의 시그니처) mypy 가 이 분기를 `PipelineOutcome`
            # 하나로 좁힌다 — 네 번째 갈래를 위한 `else` 를 남기면 `warn_unreachable`이
            # 죽은 코드로 거부한다(실측).
            self._transition_succeeded(job_id, outcome)

    def _transition_succeeded(self, job_id: str, outcome: PipelineOutcome) -> None:
        artifact = ArtifactReference(
            uri=outcome.artifact.uri,
            release_id=outcome.artifact.release_id,
            artifact_checksum=outcome.artifact.artifact_checksum,
            feature_schema_version=outcome.artifact.feature_schema_version,
            code_version=outcome.artifact.code_version,
            dataset_id=outcome.artifact.dataset_id,
            manifest_schema_version=outcome.artifact.manifest_schema_version,
        )
        evaluation = EvaluationReportReference(
            uri=outcome.evaluation.uri,
            checksum=outcome.evaluation.checksum,
            report_schema_version=outcome.evaluation.report_schema_version,
        )
        result = self._store.apply_transition(
            job_id,
            JobEvent.SUCCEED,
            at=datetime.now(UTC),
            artifact=artifact,
            evaluation=evaluation,
        )
        if isinstance(result, TransitionRejected):
            _logger.info(
                "job_id=%s SUCCEED 전이 거부(이미 다른 writer 가 종료 상태로 옮김) — %s",
                job_id,
                result,
            )

    def _transition_failed(
        self, job_id: str, code: JobFailureCode, detail_code: DetailCode
    ) -> None:
        failure = JobFailure(code=code, retryable=False, detail_code=detail_code.value)
        result = self._store.apply_transition(
            job_id, JobEvent.FAIL, at=datetime.now(UTC), failure=failure
        )
        if isinstance(result, TransitionRejected):
            _logger.info(
                "job_id=%s FAIL 전이 거부(이미 다른 writer 가 종료 상태로 옮김) — %s",
                job_id,
                result,
            )

    def _transition_cancelled(self, job_id: str) -> None:
        result = self._store.apply_transition(
            job_id, JobEvent.CANCEL, at=datetime.now(UTC)
        )
        if isinstance(result, TransitionRejected):  # pragma: no cover — CANCEL 은 표에
            # 모든 상태에 정의돼 있어(종료 상태는 멱등 no-op) 도달 불가.
            _logger.error("job_id=%s CANCEL 전이 거부 — %s", job_id, result)

    def shutdown(self, *, wait: bool) -> None:
        self._executor.shutdown(wait=wait, cancel_futures=not wait)
