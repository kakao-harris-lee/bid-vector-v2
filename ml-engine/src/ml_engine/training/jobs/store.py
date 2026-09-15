"""`ml_engine.training.jobs.store` — 프로세스 로컬 job 저장소(scope.md, D-2C-3).
`job_id`는 `uuid4`(불투명 문자열). idempotency 는 `start_or_reuse` 결과 타입 셋으로
표현한다(설계 검토 (1) 「멱등」) — dict 직접 조회를 흩뿌리지 않는다.

job 영속화는 하지 않는다(`OPEN-5E-JOB-PERSISTENCE`, 프로세스 재시작 시 소실 — M6 6B).
"""

from __future__ import annotations

import threading
import uuid
from dataclasses import dataclass
from datetime import datetime

from ml_engine.training.jobs.state import JobRecord
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
    """프로세스 로컬 dict + 잠금. 유일 진입점은 `start_or_reuse`·`get`·`replace`."""

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

    def replace(self, record: JobRecord) -> None:
        """전이 결과를 저장한다 — 호출자(`runner.py`)가 이미 `transition()`으로 만든
        새 `JobRecord`를 넣는다(이 저장소는 전이 규칙을 모른다)."""
        with self._lock:
            self._job_by_id[record.job_id] = record
