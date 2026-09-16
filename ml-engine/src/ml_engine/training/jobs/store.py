"""`ml_engine.training.jobs.store` — 프로세스 로컬 job 저장소(scope.md, D-2C-3).
`job_id`는 `uuid4`(불투명 문자열). idempotency 는 `start_or_reuse` 결과 타입 셋으로
표현한다(설계 검토 (1) 「멱등」) — dict 직접 조회를 흩뿌리지 않는다.

**verifier r1 H-2** — 예전에는 `get()`과 (이제는 제거된) `replace()`를 따로
호출해 전이를 썼는데, 그 사이가 원자적이지 않았다: 두 writer(job 실행 종료·
`CancelTrainingJob`)가 각자 읽은 stale record 로 전이를 계산해, client 에
`CANCELLED`를 응답한 뒤 저장소가 `SUCCEEDED`로 역행할 수 있었다(전이표 자체는
우회되지 않지만 저장소 계층에서 종료 상태 불변식이 깨진다). `apply_transition`
이 읽기·전이 계산·쓰기를 **한 잠금 아래** 수행해 이 경합을 구조적으로 없앤다 —
**유일** 쓰기 진입점이다(verifier r2 R2-4 — `replace()`는 production 호출자가
0이 된 뒤 완전히 제거했다. `get()` 뒤 별도로 쓰면 원자성이 깨지는 그 우회
자체를 없앤다).

job 영속화는 하지 않는다(`OPEN-5E-JOB-PERSISTENCE`, 프로세스 재시작 시 소실 — M6 6B).
"""

from __future__ import annotations

import threading
import uuid
from dataclasses import dataclass
from datetime import datetime

from ml_engine.training.jobs.state import (
    ArtifactReference,
    EvaluationReportReference,
    JobEvent,
    JobFailure,
    JobRecord,
    TransitionRejected,
    transition,
)
from ml_engine.training.jobs.state import JobState as _JobState

_ACCEPTED = _JobState.ACCEPTED


@dataclass(frozen=True)
class Started:
    """새 job 이 만들어졌다."""

    record: JobRecord


@dataclass(frozen=True)
class Reused:
    """같은 `idempotency_key`+같은 `dataset_id` — 기존 job 을 그대로 돌려준다."""

    record: JobRecord


@dataclass(frozen=True)
class Conflict:
    """같은 `idempotency_key`, 다른 `dataset_id` — `IDEMPOTENCY_CONFLICT`(2A 어휘)."""

    existing_dataset_id: str
    requested_dataset_id: str


class InMemoryJobStore:
    """프로세스 로컬 dict + 잠금. 유일 진입점은 `start_or_reuse`·`get`·
    `apply_transition`(verifier r2 R2-4 — `replace`는 production 호출자가
    0이 되어 제거했다)."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._job_by_id: dict[str, JobRecord] = {}
        self._job_id_by_idempotency_key: dict[str, str] = {}

    def start_or_reuse(
        self, idempotency_key: str, dataset_id: str, *, accepted_at: datetime
    ) -> Started | Reused | Conflict:
        with self._lock:
            existing_job_id = self._job_id_by_idempotency_key.get(idempotency_key)
            if existing_job_id is not None:
                existing = self._job_by_id[existing_job_id]
                if existing.dataset_id != dataset_id:
                    return Conflict(
                        existing_dataset_id=existing.dataset_id,
                        requested_dataset_id=dataset_id,
                    )
                return Reused(record=existing)

            job_id = str(uuid.uuid4())
            record = JobRecord(
                job_id=job_id,
                state=_ACCEPTED,
                dataset_id=dataset_id,
                accepted_at=accepted_at,
            )
            self._job_by_id[job_id] = record
            self._job_id_by_idempotency_key[idempotency_key] = job_id
            return Started(record=record)

    def get(self, job_id: str) -> JobRecord | None:
        with self._lock:
            return self._job_by_id.get(job_id)

    def apply_transition(
        self,
        job_id: str,
        event: JobEvent,
        *,
        at: datetime,
        artifact: ArtifactReference | None = None,
        evaluation: EvaluationReportReference | None = None,
        failure: JobFailure | None = None,
    ) -> JobRecord | TransitionRejected | None:
        """읽기·`transition()`·쓰기를 **한 잠금** 아래 원자적으로 수행한다(H-2). 이
        메서드로만 종료 전이(SUCCEED/FAIL/CANCEL)를 쓰면, 두 writer 가 경합해도
        실제로 나중에 실행되는 쪽이 항상 **직전 writer 가 쓴 최신 상태**를 보고
        전이를 계산한다 — stale record 로 인한 종료 상태 역행이 구조적으로 불가능하다.
        전이표가 이미 종료 상태의 대부분의 이벤트를 거부하므로(예: SUCCEEDED 에
        CANCEL 이 오면 멱등 no-op 으로 SUCCEEDED 를 그대로 반환), 이 원자성만으로
        정합성이 성립한다. `job_id`가 없으면 `None`."""
        with self._lock:
            current = self._job_by_id.get(job_id)
            if current is None:
                return None
            result = transition(
                current,
                event,
                at=at,
                artifact=artifact,
                evaluation=evaluation,
                failure=failure,
            )
            if isinstance(result, JobRecord):
                self._job_by_id[job_id] = result
            return result
