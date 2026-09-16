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


@dataclass(frozen=True)
class JobFailure:
    """2C `JobFailure` 값 타입 — `detail_code`는 닫힌 어휘(`DetailCode`, pipeline.py 소유)
    문자열이다(자유 문자열 금지)."""

    code: JobFailureCode
    retryable: bool
    detail_code: str

    def __post_init__(self) -> None:
        if not self.detail_code:
            raise ValueError("detail_code 는 비어 있을 수 없습니다.")


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


@dataclass(frozen=True)
class TransitionRejected:
    """전이표에 없는 (state, event) 쌍 — 예외가 아니라 결과 타입."""

    from_state: JobState
    event: JobEvent


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
    조립은 `_apply_*` 헬퍼로 나눈다(설계 래칫 함수 50줄)."""
    key = (record.state, event)
    new_state = _TRANSITIONS.get(key)
    if new_state is None:
        return TransitionRejected(from_state=record.state, event=event)

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
