"""`ml_engine.training.jobs.runner` — `JobRunner`(scope.md ⑦, ADR 0010 §6 fork 금지).
`ThreadPoolExecutor(max_workers=policy.job_workers)`만 쓴다 — `os.fork`·`multiprocessing`
없음(정적 test 가 이 파일에 그 이름이 없음을 확인한다).

취소 존중: 파이프라인이 단계 경계에서 `PipelineCancelled`를 내면 `CANCELLED`로 전이한다
(계산은 이미 파이프라인 내부에서 중단됐다 — D-2D-7). future 콜백이 예외로 죽으면
`FAILED(TRAINING_ERROR, RUNNER_CRASHED)`로 전이한다(설계 검토 우회 (13) — `RUNNING`에
영원히 머무는 job 방지). 예외 자체는 삼키지 않고 로그로 남긴다(`BLE` 없음, 구체 예외
없이 `Exception`을 잡지만 이것은 **콜백 경계**이지 도메인 실패 분류가 아니다 — 파이프라인
내부는 이미 결과 타입만 다루므로 여기 도달하는 예외는 전부 "분류되지 않은 크래시"다).

**M6/6B-4 Q-2 — `START` 전이는 워커가 실제로 집는 순간에 일어난다.** 앞 판은
`submit()` 안에서 곧바로 `RUNNING` 을 커밋하고 **그 뒤에** `executor.submit` 을 불렀다 —
`job_workers: 1` 에서 둘째 이후 job 은 record 가 `RUNNING` 인 채 실행 대기열에서
기다렸으므로 **관측 가능한 상태가 실행을 뜻하지 않았다**(ACCEPTED 는 두 호출 사이 수 μs
만 존재). 이제 `submit()` 은 대기열에 넣기만 하고 `_execute` 가 워커 스레드에서 START 를
커밋한다 — `ACCEPTED` = 대기, `RUNNING` = 실행 중이 되어 「가장 오래된 대기 job」이
`accepted_at` 으로 정의된다(ADR 0010 D-8 개정).

집을 때 이미 대기가 아니면(폐기·취소로 종단에 갔다) 파이프라인을 **돌리지 않는다**
(`_NotStarted`). 종료 중 대기열에서 취소된 future 도 전이를 만들지 않는다 — job 은
`ACCEPTED` 로 남아 다음 기동이 재적재한다."""

from __future__ import annotations

import logging
import threading
from concurrent.futures import Future, ThreadPoolExecutor
from dataclasses import dataclass
from datetime import UTC, datetime

from ml_engine.training.jobs.journal import JournalFailed
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
from ml_engine.training.jobs.store import JobStore

_logger = logging.getLogger(__name__)


@dataclass(frozen=True)
class _NotStarted:
    """워커가 집었을 때 이 job 이 더는 대기가 아니었다 — 파이프라인을 돌리지 않았다.
    종단 전이도 만들지 않는다(이미 다른 writer 가 상태를 확정했다)."""

    detail: str


type _WorkerResult = PipelineOutcome | PipelineFailed | PipelineCancelled | _NotStarted


def _log_terminal_transition(
    job_id: str,
    event: str,
    result: JobRecord | TransitionRejected | JournalFailed | None,
) -> None:
    """종단 전이의 **세 가지 실패**를 각자의 뜻으로 남긴다. 저널 쓰기 실패는 거부와
    다르다 — 메모리도 바뀌지 않았으므로 job 은 `RUNNING` 으로 남고, 다음 기동이 그것을
    고아로 처분한다. 조용히 넘기면 그 사실을 아무도 모른다."""
    if isinstance(result, TransitionRejected):
        _logger.info(
            "job_id=%s %s 전이 거부(이미 다른 writer 가 종료 상태로 옮김) — %s",
            job_id,
            event,
            result,
        )
    elif isinstance(result, JournalFailed):
        _logger.error(
            "job_id=%s %s 전이의 저널 쓰기 실패 — 상태가 반영되지 않았다(%s). "
            "다음 기동이 이 job 을 고아로 처분한다.",
            job_id,
            event,
            result,
        )
    elif result is None:  # pragma: no cover — 완료 콜백 앞에서 이미 걸러진다.
        _logger.error("job_id=%s %s 전이 대상이 저장소에 없다", job_id, event)


class JobRunner:
    """`store`에 있는 job 을 스레드 풀에서 실행한다. `cancel(job_id)`는 진행 중인
    실행의 `CancelToken`을 세운다 — 파이프라인이 다음 단계 경계에서 확인한다."""

    def __init__(self, store: JobStore, *, max_workers: int) -> None:
        if max_workers < 1:
            raise ValueError(f"max_workers 는 1 이상이어야 합니다: {max_workers}")
        self._store = store
        self._executor = ThreadPoolExecutor(max_workers=max_workers)
        self._cancel_tokens: dict[str, CancelToken] = {}
        self._lock_tokens_guard = threading.Lock()

    def submit(
        self, job_id: str, dataset_ref: DatasetRefInput, pipeline: TrainingPipeline
    ) -> None:
        """대기 job 을 실행 대기열에 넣는다 — **상태는 바꾸지 않는다**(Q-2). `START`
        전이는 워커가 이 job 을 실제로 집는 순간(`_execute`)에 일어나므로, 대기열에서
        기다리는 동안 job 은 `ACCEPTED`(대기)이고 상한·폐기의 대상이다.

        R2-1 의 경합(제출과 취소)은 이제 `submit` 이 아니라 `_execute`
        안에서 판정된다 — 집었을 때 이미 종단이면 파이프라인을 돌리지 않는다. 그래서
        이 메서드는 전이 결과를 돌려줄 것이 없다(호출자는 저장소를 다시 읽어 응답한다)."""
        future = self._executor.submit(self._execute, job_id, dataset_ref, pipeline)

        def _callback(done: Future[_WorkerResult]) -> None:
            self._on_done(job_id, done)

        future.add_done_callback(_callback)

    def _execute(
        self, job_id: str, dataset_ref: DatasetRefInput, pipeline: TrainingPipeline
    ) -> _WorkerResult:
        """워커 스레드에서 돈다. `CancelToken` 을 **START 전이보다 먼저** 등록한다 —
        그 사이에 도착한 `cancel(job_id)` 를 잃지 않기 위해서다(토큰을 나중에 등록하면
        취소 요청이 조용히 사라지고 계산이 그대로 돈다)."""
        token = CancelToken()
        with self._lock_tokens_guard:
            self._cancel_tokens[job_id] = token
        started = self._store.apply_transition(
            job_id, JobEvent.START, at=datetime.now(UTC)
        )
        if not isinstance(started, JobRecord):
            with self._lock_tokens_guard:
                self._cancel_tokens.pop(job_id, None)
            _logger.info(
                "job_id=%s 워커가 집었을 때 대기가 아니었다(폐기·취소·저널 실패) — "
                "파이프라인을 돌리지 않는다: %s",
                job_id,
                started,
            )
            return _NotStarted(detail=str(started))
        return pipeline.run(dataset_ref, token)

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

    def _on_done(self, job_id: str, future: Future[_WorkerResult]) -> None:
        with self._lock_tokens_guard:
            self._cancel_tokens.pop(job_id, None)

        if future.cancelled():
            # `shutdown(wait=False)`가 대기열의 future 를 취소했다 — 집히지 않았으므로
            # 전이가 없다(job 은 ACCEPTED 로 남아 다음 기동이 재적재한다). 여기서
            # `future.exception()`을 부르면 `CancelledError`가 콜백 안에서 터진다.
            _logger.info(
                "job_id=%s 실행 전 종료로 취소됐다 — 대기 상태로 남는다", job_id
            )
            return

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
        self._dispatch(job_id, future.result())

    def _dispatch(self, job_id: str, outcome: _WorkerResult) -> None:
        """워커가 돌려준 네 갈래를 전이로 옮긴다."""
        if isinstance(outcome, _NotStarted):
            return  # 집었을 때 대기가 아니었다 — `_execute`가 이미 로그로 남겼다.
        if isinstance(outcome, PipelineCancelled):
            self._transition_cancelled(job_id)
        elif isinstance(outcome, PipelineFailed):
            self._transition_failed(job_id, outcome.code, outcome.detail_code)
        else:
            # `_WorkerResult`가 넷으로 닫혀 있고 위 셋을 이미 걸렀으므로 mypy 가 이
            # 분기를 `PipelineOutcome` 하나로 좁힌다 — 다섯 번째 갈래를 위한 `else`를
            # 남기면 `warn_unreachable`이 죽은 코드로 거부한다(실측).
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
        _log_terminal_transition(job_id, "SUCCEED", result)

    def _transition_failed(
        self, job_id: str, code: JobFailureCode, detail_code: DetailCode
    ) -> None:
        failure = JobFailure(code=code, retryable=False, detail_code=detail_code.value)
        result = self._store.apply_transition(
            job_id, JobEvent.FAIL, at=datetime.now(UTC), failure=failure
        )
        _log_terminal_transition(job_id, "FAIL", result)

    def _transition_cancelled(self, job_id: str) -> None:
        result = self._store.apply_transition(
            job_id, JobEvent.CANCEL, at=datetime.now(UTC)
        )
        _log_terminal_transition(job_id, "CANCEL", result)

    def shutdown(self, *, wait: bool) -> None:
        self._executor.shutdown(wait=wait, cancel_futures=not wait)
