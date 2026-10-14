"""`ml_engine.training.jobs.state` — 2C `TrainingJobService` 전이표(scope.md ④)의 실물
(설계 검토 (1) 「전이표」). 표는 **선언 데이터**(frozen mapping)다 — `if state == …` 사슬을
피한다. `JobRecord`는 조합 불변식(2C `TrainingJob` 주석)을 생성자에서 강제한다 — 위반
조합은 애초에 만들 수 없다(설계 검토 (1) 「조합 불변식」).

`ArtifactReference`·`EvaluationReportReference`·`JobFailure`는 2C wire 메시지의 Python
값 타입이다(값만 — proto 직렬화는 servicer 가 한다).
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from types import MappingProxyType


class JobState(StrEnum):
    """2C `JobState`(UNSPECIFIED 제외 — 이 표현은 유효한 상태만 나른다)."""

    ACCEPTED = "ACCEPTED"
    RUNNING = "RUNNING"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class JobEvent(StrEnum):
    """`transition`의 유일 진입점이 받는 이벤트 — 상태 대입이 아니라 이벤트 발생만
    표현한다(설계 검토 (1))."""

    START = "START"
    SUCCEED = "SUCCEED"
    FAIL = "FAIL"
    CANCEL = "CANCEL"


_TERMINAL_STATES: frozenset[JobState] = frozenset(
    {JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED}
)

# 전이표(2C 전이표 + 종료 상태 Cancel 멱등 no-op) — 선언 데이터, `if` 사슬 없음.
_TRANSITIONS: MappingProxyType[tuple[JobState, JobEvent], JobState] = MappingProxyType(
    {
        (JobState.ACCEPTED, JobEvent.START): JobState.RUNNING,
        (JobState.RUNNING, JobEvent.SUCCEED): JobState.SUCCEEDED,
        (JobState.RUNNING, JobEvent.FAIL): JobState.FAILED,
        # M6/6B-4 D-6B4-1(ADR 0010 D-8 개정) — 대기 job 을 실행 없이 끝내는 간선.
        # 쓰이는 자리 **셋**(계약 문면과 같은 목록, vr M-3): [1] 대기 큐 상한 초과 폐기
        # (`WORKER_RESOURCE_EXHAUSTED`/`PENDING_QUEUE_OVERFLOW`) [2] 재기동 때 모르는
        # spec 의 대기 고아 처분(`TRAINING_ERROR`/`TRAINING_SPEC_UNSUPPORTED_ON_RESTART`)
        # [3] 워커가 집는 도중 START 커밋 전 크래시(`TRAINING_ERROR`/`RUNNER_CRASHED`).
        # 「실행했다」를 뜻하지 않으므로 `started_at` 은 미설정으로 남는다(`_apply_fail`
        # 이 record 의 값을 그대로 옮긴다).
        (JobState.ACCEPTED, JobEvent.FAIL): JobState.FAILED,
        (JobState.ACCEPTED, JobEvent.CANCEL): JobState.CANCELLED,
        (JobState.RUNNING, JobEvent.CANCEL): JobState.CANCELLED,
        # 종료 상태 Cancel 은 멱등 no-op(현재 상태 그대로) — 새 전이가 아니라 표에
        # 명시된 데이터다(설계 검토 (1)).
        (JobState.SUCCEEDED, JobEvent.CANCEL): JobState.SUCCEEDED,
        (JobState.FAILED, JobEvent.CANCEL): JobState.FAILED,
        (JobState.CANCELLED, JobEvent.CANCEL): JobState.CANCELLED,
    }
)


class JobFailureCode(StrEnum):
    """2C `JobFailureCode`(UNSPECIFIED 제외)."""

    DATASET_UNREADABLE = "DATASET_UNREADABLE"
    DATASET_CHECKSUM_MISMATCH = "DATASET_CHECKSUM_MISMATCH"
    TRAINING_ERROR = "TRAINING_ERROR"
    EVALUATION_ERROR = "EVALUATION_ERROR"
    CANCELLED_BY_REQUEST = "CANCELLED_BY_REQUEST"
    WORKER_RESOURCE_EXHAUSTED = "WORKER_RESOURCE_EXHAUSTED"


_RETRYABLE_BY_CODE: MappingProxyType[JobFailureCode, bool] = MappingProxyType(
    {
        JobFailureCode.DATASET_UNREADABLE: False,
        JobFailureCode.DATASET_CHECKSUM_MISMATCH: False,
        JobFailureCode.TRAINING_ERROR: False,
        JobFailureCode.EVALUATION_ERROR: False,
        JobFailureCode.CANCELLED_BY_REQUEST: False,
        JobFailureCode.WORKER_RESOURCE_EXHAUSTED: True,
    }
)
"""M6/6B-4 cr M-5 — `retryable` 의 **유일** 사상. `training.proto` 는 job failure 의
`retryable` 을 「계약이 정하는 값 … application failure 의 `retryable` 과 대칭」이라 하고
ADR 0010 은 application 층에서 코드별 사상(`MODEL_NOT_READY` 만 참)을 쓴다. 앞 판은 같은
`TRAINING_ERROR` 가 자리에 따라 양쪽 값을 들었다(저장소 `_failed` 는 참, 실행기
`_transition_failed` 는 거짓) — 그 대칭이 깨져 있었다.

용량 고갈만 참이다: 대기 큐 상한 폐기는 나중에 다시 넣으면 들어갈 수 있다. 나머지는 같은
입력을 다시 넣어도 같은 자리에서 실패하거나(dataset·checksum·학습·평가) 재시도의 대상이
아니다(요청에 의한 취소). **재기동 처분**(`RUNNER_LOST_ON_RESTART` ·
`TRAINING_SPEC_UNSUPPORTED_ON_RESTART`)은 `TRAINING_ERROR` 를 쓰므로 거짓이다 — 뒤쪽은
같은 spec 재제출이 `UNSUPPORTED_TRAINING_SPEC`(비재시도)로 거부되므로 참이면 거짓말이었다
(vr L-3). 앞쪽은 새 제출이면 돌 수 있으나 그것은 **새 job** 이고, 전용 코드 선택은
`OPEN-6B4-STORE-UNAVAILABLE-CODE` 와 같은 축으로 남긴다(알려진 제한)."""


@dataclass(frozen=True)
class JobFailure:
    """2C `JobFailure` 값 타입 — `detail_code`는 닫힌 어휘(`DetailCode`, pipeline.py 소유)
    문자열이다(자유 문자열 금지).

    `retryable` 은 **필드가 아니라 `code` 의 함수**다(cr M-5) — 호출자가 넣을 인자가 없어
    자리마다 다른 값을 고르는 일이 구조적으로 불가능하다."""

    code: JobFailureCode
    detail_code: str

    def __post_init__(self) -> None:
        if not self.detail_code:
            raise ValueError("detail_code 는 비어 있을 수 없습니다.")

    @property
    def retryable(self) -> bool:
        return _RETRYABLE_BY_CODE[self.code]


@dataclass(frozen=True)
class ArtifactReference:
    """2C `ArtifactReference` 값 타입 — `release`는 2B `ModelRelease` 다섯 성분을
    평탄화해 담는다(새 릴리스 식별 타입을 만들지 않는다, 2C 문면과 같은 축)."""

    uri: str
    release_id: str
    artifact_checksum: str
    feature_schema_version: str
    code_version: str
    dataset_id: str
    manifest_schema_version: str

    def __post_init__(self) -> None:
        for field_name in (
            "uri",
            "release_id",
            "artifact_checksum",
            "feature_schema_version",
            "code_version",
            "dataset_id",
            "manifest_schema_version",
        ):
            if not getattr(self, field_name):
                raise ValueError(f"{field_name} 는 비어 있을 수 없습니다.")


@dataclass(frozen=True)
class EvaluationReportReference:
    """2C `EvaluationReportReference` 값 타입."""

    uri: str
    checksum: str
    report_schema_version: str

    def __post_init__(self) -> None:
        for field_name in ("uri", "checksum", "report_schema_version"):
            if not getattr(self, field_name):
                raise ValueError(f"{field_name} 는 비어 있을 수 없습니다.")


@dataclass(frozen=True)
class JobRecord:
    """2C `TrainingJob`의 Python 스냅샷 — **조합 불변식**(2C 주석)을 생성자에서 강제한다:
    `SUCCEEDED ⟹ artifact∧evaluation`, `FAILED ⟹ failure`, 그 밖은 셋 미설정. 시각
    불변식(D-2C-5): `accepted_at ≤ started_at ≤ finished_at`(설정된 것만 비교).

    frozen 이므로 전이는 항상 `transition()`이 새 인스턴스를 만든다 — 상태 직접 대입
    경로가 없다(설계 검토 (2b) 「전이 함수만」)."""

    job_id: str
    state: JobState
    dataset_id: str
    accepted_at: datetime
    started_at: datetime | None = None
    finished_at: datetime | None = None
    artifact: ArtifactReference | None = None
    evaluation: EvaluationReportReference | None = None
    failure: JobFailure | None = None

    def __post_init__(self) -> None:
        if not self.job_id:
            raise ValueError("job_id 는 비어 있을 수 없습니다.")
        if not self.dataset_id:
            raise ValueError("dataset_id 는 비어 있을 수 없습니다.")

        if self.state is JobState.SUCCEEDED:
            if self.artifact is None or self.evaluation is None:
                raise ValueError(
                    "SUCCEEDED 는 artifact·evaluation 이 모두 설정돼야 합니다."
                )
            if self.failure is not None:
                raise ValueError("SUCCEEDED 에 failure 가 설정될 수 없습니다.")
        elif self.state is JobState.FAILED:
            if self.failure is None:
                raise ValueError("FAILED 는 failure 가 설정돼야 합니다.")
            if self.artifact is not None or self.evaluation is not None:
                raise ValueError("FAILED 에 artifact·evaluation 이 설정될 수 없습니다.")
        else:
            if (
                self.artifact is not None
                or self.evaluation is not None
                or self.failure is not None
            ):
                raise ValueError(
                    f"{self.state} 상태에서는 artifact·evaluation·failure 가 전부 "
                    "미설정이어야 합니다."
                )

        if self.started_at is not None and self.started_at < self.accepted_at:
            raise ValueError("started_at 은 accepted_at 보다 앞일 수 없습니다.")
        if self.finished_at is not None:
            if self.finished_at < self.accepted_at:
                raise ValueError("finished_at 은 accepted_at 보다 앞일 수 없습니다.")
            if self.started_at is not None and self.finished_at < self.started_at:
                raise ValueError("finished_at 은 started_at 보다 앞일 수 없습니다.")


_PENDING_FAILURE_REASONS: frozenset[tuple[JobFailureCode, str]] = frozenset(
    {
        (JobFailureCode.WORKER_RESOURCE_EXHAUSTED, "PENDING_QUEUE_OVERFLOW"),
        (JobFailureCode.TRAINING_ERROR, "TRAINING_SPEC_UNSUPPORTED_ON_RESTART"),
        (JobFailureCode.TRAINING_ERROR, "RUNNER_CRASHED"),
    }
)
"""`ACCEPTED → FAILED` 간선의 **사유 전부**(cr M-3). 계약 문면 세 자리(`training.proto`
`JobState` 주석 · ADR 0010 D-8 개정 블록 · 아래 전이표 주석)가 이 간선의 쓰임을 「정확히
셋」이라 선언하는데, 앞 판은 셋 각각을 양성으로만 확인했고 **넷째가 생기는 것을 막는 술어가
없었다** — 교차 등식 게이트는 간선 집합만 보고 사유는 보지 않는다.

이제 집합이 선언 데이터이고 `transition()` 이 그 밖을 거부한다. 넷째 사유를 쓰려면 이 집합을
고쳐야 하고, 그러면 문면 대조 게이트가 붉어진다. `detail_code` 를 문자열로 적는 이유는
`DetailCode`(pipeline.py)가 이 모듈을 import 하기 때문이다 — 게이트가 각 문자열이 그 닫힌
어휘의 값임을 단언한다.

**대기 출발에만 걸린다**: 실행 중 실패(파이프라인 어휘 전체)는 `RUNNING → FAILED` 이고
이 제약의 대상이 아니다. RUNNING 고아 처분(`RUNNER_LOST_ON_RESTART`)도 그쪽 간선이다."""


class TransitionRejectionReason(StrEnum):
    """거부 사유 닫힌 어휘 — 「표에 없다」와 「사유가 그 출발 상태에서 쓸 수 없다」를
    구분한다(M6/6B-4 설계 검토 우회 (3)). 둘을 한 값으로 뭉치면 호출자의 로그가
    (RUNNING, FAIL) 처럼 **표에 있는** 쌍을 「표에 없다」고 말한다."""

    NOT_IN_TABLE = "NOT_IN_TABLE"
    RESOURCE_EXHAUSTED_NOT_PENDING = "RESOURCE_EXHAUSTED_NOT_PENDING"
    PENDING_FAILURE_REASON_UNLISTED = "PENDING_FAILURE_REASON_UNLISTED"


@dataclass(frozen=True)
class TransitionRejected:
    """전이가 성립하지 않았다 — 예외가 아니라 결과 타입. 사유는 `reason`(닫힌 어휘)."""

    from_state: JobState
    event: JobEvent
    reason: TransitionRejectionReason = TransitionRejectionReason.NOT_IN_TABLE


def _apply_start(record: JobRecord, new_state: JobState, at: datetime) -> JobRecord:
    return JobRecord(
        job_id=record.job_id,
        state=new_state,
        dataset_id=record.dataset_id,
        accepted_at=record.accepted_at,
        started_at=at,
    )


def _apply_succeed(
    record: JobRecord,
    new_state: JobState,
    at: datetime,
    artifact: ArtifactReference | None,
    evaluation: EvaluationReportReference | None,
) -> JobRecord:
    return JobRecord(
        job_id=record.job_id,
        state=new_state,
        dataset_id=record.dataset_id,
        accepted_at=record.accepted_at,
        started_at=record.started_at,
        finished_at=at,
        artifact=artifact,
        evaluation=evaluation,
    )


def _apply_fail(
    record: JobRecord, new_state: JobState, at: datetime, failure: JobFailure | None
) -> JobRecord:
    return JobRecord(
        job_id=record.job_id,
        state=new_state,
        dataset_id=record.dataset_id,
        accepted_at=record.accepted_at,
        started_at=record.started_at,
        finished_at=at,
        failure=failure,
    )


def _apply_cancel(record: JobRecord, new_state: JobState, at: datetime) -> JobRecord:
    return JobRecord(
        job_id=record.job_id,
        state=new_state,
        dataset_id=record.dataset_id,
        accepted_at=record.accepted_at,
        started_at=record.started_at,
        finished_at=at,
    )


def _failure_reason_rejection(
    record: JobRecord, event: JobEvent, failure: JobFailure | None
) -> TransitionRejectionReason | None:
    """사유 축의 두 닫힘(전이표가 상태 쌍만 보는 자리를 메운다). 표에 있는 쌍이어도 사유가
    그 출발 상태에서 쓸 수 없으면 거부한다.

    ① 설계 검토 우회 (3) — `WORKER_RESOURCE_EXHAUSTED`(대기 큐 상한 폐기)는 **대기에서만**.
    (RUNNING, FAIL) 쌍 자체는 표에 있으므로 전이표만으로는 「워커가 이미 집은 job 을 큐가
    넘쳐서 끝냈다」를 막지 못한다.

    ② cr M-3 — 반대 방향: 대기 출발의 FAIL 은 **등재된 사유 셋**만 쓴다. 대기 job 은 실행된
    적이 없으므로 실행 중에만 생길 수 있는 사유로 끝날 수 없고, 계약 문면의 「정확히 셋」이
    여기서 닫힌다(넷째를 쓰려면 `_PENDING_FAILURE_REASONS` 를 고쳐야 한다)."""
    if (
        failure is not None
        and failure.code is JobFailureCode.WORKER_RESOURCE_EXHAUSTED
        and record.state is not JobState.ACCEPTED
    ):
        return TransitionRejectionReason.RESOURCE_EXHAUSTED_NOT_PENDING
    if record.state is JobState.ACCEPTED and event is JobEvent.FAIL:
        reason = None if failure is None else (failure.code, failure.detail_code)
        if reason not in _PENDING_FAILURE_REASONS:
            return TransitionRejectionReason.PENDING_FAILURE_REASON_UNLISTED
    return None


def transition(
    record: JobRecord,
    event: JobEvent,
    *,
    at: datetime,
    artifact: ArtifactReference | None = None,
    evaluation: EvaluationReportReference | None = None,
    failure: JobFailure | None = None,
) -> JobRecord | TransitionRejected:
    """유일 전이 진입점(설계 검토 (1)). 표에 없는 쌍은 `TransitionRejected`. 종료 상태의
    `CANCEL`은 멱등 no-op — `record`를 그대로 돌려준다(새 시각 대입 없음). 이벤트별
    조립은 `_apply_*` 헬퍼로, 사유 축의 거부는 `_failure_reason_rejection` 으로 나눈다
    (설계 래칫 함수 50줄)."""
    key = (record.state, event)
    new_state = _TRANSITIONS.get(key)
    if new_state is None:
        return TransitionRejected(from_state=record.state, event=event)

    rejection = _failure_reason_rejection(record, event, failure)
    if rejection is not None:
        return TransitionRejected(
            from_state=record.state, event=event, reason=rejection
        )

    if record.state in _TERMINAL_STATES and event is JobEvent.CANCEL:
        return record

    if event is JobEvent.START:
        return _apply_start(record, new_state, at)
    if event is JobEvent.SUCCEED:
        return _apply_succeed(record, new_state, at, artifact, evaluation)
    if event is JobEvent.FAIL:
        return _apply_fail(record, new_state, at, failure)
    # JobEvent.CANCEL (비종료 상태에서)
    return _apply_cancel(record, new_state, at)
