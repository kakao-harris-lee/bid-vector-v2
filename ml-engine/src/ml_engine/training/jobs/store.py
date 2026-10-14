"""`ml_engine.training.jobs.store` — training job 저장소(D-2C-3 · M6/6B-4 Q-2·Q-4·Q-5).
`job_id`는 `uuid4`(불투명 문자열). idempotency 는 `start_or_reuse` 결과 타입으로
표현한다(설계 검토 (1) 「멱등」) — dict 직접 조회를 흩뿌리지 않는다.

**H-2** — `get()`과 (이제는 제거된) `replace()`를 따로 호출해 전이를 쓰면 그 사이가
원자적이지 않다: 두 writer(job 실행 종료·`CancelTrainingJob`)가 각자 읽은 stale record 로
전이를 계산해, client 에 `CANCELLED`를 응답한 뒤 저장소가 `SUCCEEDED`로 역행할 수 있다.
`apply_transition`이 읽기·전이 계산·쓰기를 **한 잠금 아래** 수행해 이 경합을 구조적으로
없앤다 — **유일** 쓰기 진입점이다.

**M6/6B-4** — 그 한 잠금이 이제 셋을 더 든다(설계 검토 (1)·우회 (1)(9)):

1. **대기 큐 상한.** 「대기」는 별도 플래그·카운터가 아니라 **상태**(`ACCEPTED`)다 —
   대기 수는 저장소에서 `ACCEPTED`를 세는 것이고 캐시하지 않는다(카운터를 두면 상태와
   갈라진다). 상한을 넘으면 `accepted_at`(동률 `job_id`) 최소인 대기 job 을
   `FAILED(WORKER_RESOURCE_EXHAUSTED, PENDING_QUEUE_OVERFLOW)`로 끝낸다(운영자 결정
   2026-09-26 ③ 최신성 우선). **적재와 폐기가 같은 잠금 안**이라 둘 사이에 다른 제출이
   끼어 상한이 깨지는 창이 없다.
2. **영속.** 저널 쓰기도 같은 잠금 안이다(우회 (9) — 밖에서 쓰면 H-2 가 다시 열린다).
   **쓰기가 성공한 뒤에만 메모리를 바꾼다** — 「메모리는 바뀌었는데 디스크는 아니다」가
   되면 재기동이 사실을 잃는다. 그래서 변이는 전부 「다음 상태를 만들고 → 저널에 쓰고 →
   커밋」 순서이고, 쓰기 실패는 `JournalFailed` 결과 타입이다(예외로 새지 않는다).
3. **재기동 처분**(`load_job_store`) — RUNNING 고아는 재개가 아니라 처분(`FAILED`,
   `RUNNER_LOST_ON_RESTART`)이고, ACCEPTED 고아는 실행된 적이 없어 대기 큐로 재적재한다
   (상한을 다시 적용해 초과분은 최고참부터 폐기). 판독 불가 저널은 빈 저장소가 아니라
   `JournalFailed` — 조립 근이 기동을 거부한다.
"""

from __future__ import annotations

import dataclasses
import logging
import threading
import uuid
from dataclasses import dataclass
from datetime import datetime

from ml_engine.training.jobs.journal import (
    JobSubmission,
    JournalAbsent,
    JournalEntry,
    JournalFailed,
    JournalSnapshot,
)
from ml_engine.training.jobs.journal_file import JobJournal
from ml_engine.training.jobs.pipeline import DetailCode
from ml_engine.training.jobs.state import (
    ArtifactReference,
    EvaluationReportReference,
    JobEvent,
    JobFailure,
    JobFailureCode,
    JobRecord,
    TransitionRejected,
    transition,
)
from ml_engine.training.jobs.state import JobState as _JobState

_logger = logging.getLogger(__name__)

_ACCEPTED = _JobState.ACCEPTED
_RUNNING = _JobState.RUNNING

_TERMINAL_EVENTS: frozenset[JobEvent] = frozenset(
    {JobEvent.SUCCEED, JobEvent.FAIL, JobEvent.CANCEL}
)
"""종단 시각(`finished_at`)을 쓰는 이벤트들 — 이 셋만 `_finish_at` 조정을 지난다.
`START` 는 `started_at` 을 쓰고 그 불변식(`accepted_at ≤ started_at`)은 조정 대상이
아니다(실행이 시작된 시각을 뒤로 밀면 「언제 집었는가」가 거짓이 된다 — 그 자리는 시계
역행에서 `TransitionRejected` 가 아니라 `ValueError` 로 남고, 호출자는 워커 하나뿐이라
저널의 미래 시각과 만나지 않는다. 알려진 제한으로 적는다)."""


@dataclass(frozen=True)
class Started:
    """새 job 이 만들어졌다. `discarded`는 이 적재가 상한을 넘겨 **함께 끝낸** 대기
    job 들이다(보통 0 또는 1개) — 호출자가 그 사실을 로그로 남긴다.

    `record`는 적재·폐기가 끝난 **커밋 뒤** 기록이다 — 시계 역행 같은 비정상 입력에서
    새 job 자신이 최고참이 되어 폐기되는 경우에도 응답이 거짓말하지 않는다."""

    record: JobRecord
    discarded: tuple[JobRecord, ...] = ()


@dataclass(frozen=True)
class Reused:
    """같은 `idempotency_key`+같은 `dataset_id` — 기존 job 을 그대로 돌려준다.
    적재가 아니므로 **폐기도 없다**(설계 검토 우회 (2))."""

    record: JobRecord


@dataclass(frozen=True)
class Conflict:
    """같은 `idempotency_key`, 다른 `dataset_id` — `IDEMPOTENCY_CONFLICT`(2A 어휘)."""

    existing_dataset_id: str
    requested_dataset_id: str


@dataclass(frozen=True)
class PendingJob:
    """대기 job 하나 — 조립 근이 재기동 뒤 **실행기에 다시 넣을** 때 쓴다."""

    record: JobRecord
    submission: JobSubmission


@dataclass(frozen=True)
class RestartDisposition:
    """재기동 처분 계수(Q-5 — 로그로 남는다). 수렴 고정점은 **처분 0**: 다시 기동해도
    RUNNING 고아도 상한 초과 폐기도 더 나오지 않는다."""

    running_orphans_failed: int
    pending_over_limit_failed: int
    pending_requeued: int


@dataclass(frozen=True)
class LoadedJobStore:
    store: JobStore
    disposition: RestartDisposition


def _snapshot(entries: dict[str, JournalEntry]) -> JournalSnapshot:
    """`job_id` 순 — 같은 상태가 항상 같은 바이트가 되도록."""
    return JournalSnapshot(entries=tuple(entries[key] for key in sorted(entries)))


def _pending_sorted(entries: dict[str, JournalEntry]) -> list[JournalEntry]:
    """대기(ACCEPTED)만, `accepted_at` 오름차순(동률 `job_id`) — 「가장 오래된 대기」의
    정의이자 재적재 순서다."""
    return sorted(
        (entry for entry in entries.values() if entry.record.state is _ACCEPTED),
        key=lambda entry: (entry.record.accepted_at, entry.record.job_id),
    )


def _finish_at(record: JobRecord, at: datetime) -> datetime:
    """종단 시각 — `accepted_at ≤ started_at ≤ finished_at`(D-2C-5)을 지킨다. 시계가
    역행한 입력(비방어)에서도 불변식이 깨지지 않도록 기록값 아래로 내려가지 않는다."""
    floor = record.started_at if record.started_at is not None else record.accepted_at
    return max(at, record.accepted_at, floor)


def _failed(
    record: JobRecord,
    *,
    at: datetime,
    code: JobFailureCode,
    detail_code: DetailCode,
) -> JobRecord:
    result = transition(
        record,
        JobEvent.FAIL,
        at=_finish_at(record, at),
        failure=JobFailure(code=code, detail_code=detail_code.value),
    )
    if not isinstance(result, JobRecord):  # pragma: no cover — 호출자가 출발 상태를
        # 고르므로(ACCEPTED·RUNNING, 둘 다 표에 FAIL 이 있고 사유 제약도 만족) 도달 불가.
        raise AssertionError(f"FAIL 전이가 거부됐다: {result}")
    return result


def _discard_pending_overflow(
    entries: dict[str, JournalEntry], pending_queue_max: int, *, at: datetime
) -> tuple[JobRecord, ...]:
    """상한 초과분을 **최고참부터** 끝낸다(`entries`를 제자리에서 고친다)."""
    pending = _pending_sorted(entries)
    overflow = len(pending) - pending_queue_max
    if overflow <= 0:
        return ()
    discarded: list[JobRecord] = []
    for entry in pending[:overflow]:
        record = _failed(
            entry.record,
            at=at,
            code=JobFailureCode.WORKER_RESOURCE_EXHAUSTED,
            detail_code=DetailCode.PENDING_QUEUE_OVERFLOW,
        )
        entries[record.job_id] = dataclasses.replace(entry, record=record)
        discarded.append(record)
    return tuple(discarded)


def _fail_running_orphans(
    entries: dict[str, JournalEntry], *, at: datetime
) -> tuple[JobRecord, ...]:
    """재기동 뒤 RUNNING 은 고아다 — 실행기와 `CancelToken`이 사라졌으므로 **재개하지
    않고 처분한다**(ADR 0010 D-8 개정, 기존 `RUNNING → FAILED` 간선). 재실행은 중복
    학습·산출물 덮어쓰기를 부른다(설계 검토 우회 (8))."""
    orphans: list[JobRecord] = []
    for entry in tuple(entries.values()):
        if entry.record.state is not _RUNNING:
            continue
        record = _failed(
            entry.record,
            at=at,
            code=JobFailureCode.TRAINING_ERROR,
            detail_code=DetailCode.RUNNER_LOST_ON_RESTART,
        )
        entries[record.job_id] = dataclasses.replace(entry, record=record)
        orphans.append(record)
    return tuple(orphans)


def _require_positive(pending_queue_max: int) -> None:
    if pending_queue_max < 1:
        raise ValueError(
            f"pending_queue_max 는 1 이상이어야 합니다: {pending_queue_max}"
        )


def _require_aware(at: datetime, name: str) -> None:
    """cr M-2 곁가지 — naive 시각을 **입구에서** 거부한다. 앞 판은 `_finish_at` 의
    `max(at, record.accepted_at, …)` 와 `_pending_sorted` 의 정렬이 naive/aware 혼합에서
    `TypeError` 를 던져, 결과 타입이 아니라 예외가 조립 근을 벗어났다. 시계는 호출자가
    쥔 값이므로 `pending_queue_max` 와 같은 갈래(프로그래밍 오류)다."""
    if at.tzinfo is None:
        raise ValueError(f"{name} 에 시간대가 있어야 합니다: {at.isoformat()}")


class JobStore:
    """잠금 하나 아래의 job 기록 + **필수** 저널. 저널 없는 갈래를 두지 않는다 —
    `journal=None` 을 받으면 「조용히 비영속으로 조립」 경로가 생긴다(위협 모델 ⑤).

    유일 진입점은 `start_or_reuse`·`get`·`apply_transition`·`pending_jobs` 넷이다."""

    def __init__(
        self,
        *,
        journal: JobJournal,
        pending_queue_max: int,
        entries: tuple[JournalEntry, ...] = (),
    ) -> None:
        _require_positive(pending_queue_max)
        self._journal = journal
        self._pending_queue_max = pending_queue_max
        self._lock = threading.Lock()
        self._entry_by_id: dict[str, JournalEntry] = {
            entry.record.job_id: entry for entry in entries
        }

    def _commit(self, next_entries: dict[str, JournalEntry]) -> JournalFailed | None:
        """저널에 **먼저** 쓰고, 성공했을 때만 메모리를 바꾼다(상태·저널 일치).

        vr M-1 — 「교체됨, 내구성 미확인」(`durable=False`)도 **성공 쪽**이다. 교체는 이미
        일어났으므로 메모리를 되돌리면 디스크와 갈라진다(거절했다고 답한 job 이 재기동 뒤
        실행된다).

        남는 위험은 전원 상실 시 그 교체가 사라지는 것이고, **전이와 적재에서 결과가 다르다**
        (vr L-2 — 앞 문면은 「재기동 처분이 덮는다」로 뭉뚱그렸다): 기존 job 의 전이라면
        디스크에 **이전 상태**가 남아 재기동 처분이 덮지만, 방금 적재한 **새 job** 은 이전
        상태에 없으므로 처분이 아니라 **소실**이다(`ACCEPTED` 를 응답했는데 재기동 뒤
        `JOB_NOT_FOUND` 이고, 같은 멱등 키 재제출은 새 job 을 만든다). 그래서 조용히 넘기지
        않고 오류로 남긴다."""
        written = self._journal.write(_snapshot(next_entries))
        if isinstance(written, JournalFailed):
            return written
        if not written.durable:
            _logger.error(
                "저널 교체의 내구성을 확인하지 못했다 — 메모리는 커밋한다(디스크와 "
                "갈라지지 않게). 전원 상실 시 이 전이가 사라질 수 있다."
            )
        self._entry_by_id = next_entries
        return None

    def _existing(self, idempotency_key: str) -> JournalEntry | None:
        for entry in self._entry_by_id.values():
            if entry.idempotency_key == idempotency_key:
                return entry
        return None

    def start_or_reuse(
        self,
        idempotency_key: str,
        dataset_id: str,
        *,
        accepted_at: datetime,
        submission: JobSubmission,
    ) -> Started | Reused | Conflict | JournalFailed:
        """판정(멱등)·적재·폐기·저널 쓰기를 **한 잠금** 아래 수행한다(설계 검토 우회
        (1)). 재사용·충돌은 적재가 아니라 저널을 쓰지 않는다."""
        _require_aware(accepted_at, "accepted_at")
        with self._lock:
            existing = self._existing(idempotency_key)
            if existing is not None:
                if existing.record.dataset_id != dataset_id:
                    return Conflict(
                        existing_dataset_id=existing.record.dataset_id,
                        requested_dataset_id=dataset_id,
                    )
                return Reused(record=existing.record)

            job_id = str(uuid.uuid4())
            next_entries = dict(self._entry_by_id)
            next_entries[job_id] = JournalEntry(
                idempotency_key=idempotency_key,
                record=JobRecord(
                    job_id=job_id,
                    state=_ACCEPTED,
                    dataset_id=dataset_id,
                    accepted_at=accepted_at,
                ),
                submission=submission,
            )
            discarded = _discard_pending_overflow(
                next_entries, self._pending_queue_max, at=accepted_at
            )
            failed = self._commit(next_entries)
            if failed is not None:
                return failed
            return Started(record=next_entries[job_id].record, discarded=discarded)

    def get(self, job_id: str) -> JobRecord | None:
        with self._lock:
            entry = self._entry_by_id.get(job_id)
            return None if entry is None else entry.record

    def pending_jobs(self) -> tuple[PendingJob, ...]:
        """대기 job 전부, `accepted_at` 오름차순. 조립 근이 재기동 뒤 재적재에 쓴다 —
        순서를 외부에서 주입하는 자리는 없다(설계 검토 (2b))."""
        with self._lock:
            return tuple(
                PendingJob(record=entry.record, submission=entry.submission)
                for entry in _pending_sorted(self._entry_by_id)
            )

    def apply_transition(
        self,
        job_id: str,
        event: JobEvent,
        *,
        at: datetime,
        artifact: ArtifactReference | None = None,
        evaluation: EvaluationReportReference | None = None,
        failure: JobFailure | None = None,
    ) -> JobRecord | TransitionRejected | JournalFailed | None:
        """읽기·`transition()`·저널 쓰기·커밋을 **한 잠금** 아래 원자적으로 수행한다
        (H-2 · 우회 (9)). `job_id`가 없으면 `None`. 종단 상태의 멱등 no-op 은 바뀌는
        것이 없어 저널을 쓰지 않는다 — 쓰기 실패가 그 호출을 깨뜨리지 않는다.

        **종단 시각은 `_finish_at` 으로 조정한다**(code-review B). 그러지 않으면 기동 시계가
        저널의 `accepted_at` 보다 앞설 때(컨테이너 시계 보정·NTP 역행) `JobRecord` 의 시각
        불변식이 `ValueError` 를 내고, 그 예외가 조립 근을 벗어나 **매 기동 실패**가 된다
        (저널이 그대로 남으므로 재기동해도 같다). `_failed` 는 자기 자리에서 이미 조정하고
        있었는데, `server._resubmit` 처럼 **이 메서드를 직접 부르는** 호출자는 그 조정을
        지나지 않았다 — 조정을 호출자마다 반복하지 않고 **한 자리로** 모은다."""
        _require_aware(at, "at")
        with self._lock:
            entry = self._entry_by_id.get(job_id)
            if entry is None:
                return None
            result = transition(
                entry.record,
                event,
                at=_finish_at(entry.record, at) if event in _TERMINAL_EVENTS else at,
                artifact=artifact,
                evaluation=evaluation,
                failure=failure,
            )
            if isinstance(result, TransitionRejected) or result is entry.record:
                return result
            next_entries = dict(self._entry_by_id)
            next_entries[job_id] = dataclasses.replace(entry, record=result)
            failed = self._commit(next_entries)
            if failed is not None:
                return failed
            return result


def load_job_store(
    *, journal: JobJournal, pending_queue_max: int, at: datetime
) -> LoadedJobStore | JournalFailed:
    """저널에서 저장소를 복원하고 **고아를 처분한다**(Q-5). 판독 불가·쓰기 불가는
    `JournalFailed` — 조립 근이 기동을 거부한다(조용한 빈 저장소 금지, 위협 모델 ⑤).

    저널이 없으면(첫 기동) 빈 저장소로 시작하되 **저널을 한 번 쓴다** — 그 자리가 쓰기
    가능한지를 기동 시점에 확인한다(볼륨·소유자 잘못을 첫 job 제출까지 미루지 않는다)."""
    _require_positive(pending_queue_max)
    _require_aware(at, "at")
    loaded = journal.read()
    if isinstance(loaded, JournalFailed):
        return loaded
    entries: dict[str, JournalEntry] = (
        {}
        if isinstance(loaded, JournalAbsent)
        else {entry.record.job_id: entry for entry in loaded.entries}
    )
    orphans = _fail_running_orphans(entries, at=at)
    discarded = _discard_pending_overflow(entries, pending_queue_max, at=at)
    written = journal.write(_snapshot(entries))
    if isinstance(written, JournalFailed):
        return written
    if not written.durable:
        # cr L-3 — 기동 1회 쓰기도 같은 갈래를 본다(앞 판은 `_commit` 만 로그를 남겨
        # 이 자리가 조용했고, runbook 의 「내구성 미확인을 로그로 남긴다」가 무조건으로
        # 읽혔다).
        _logger.error(
            "기동 시 저널 쓰기의 내구성을 확인하지 못했다 — 처분 결과가 전원 상실 시 "
            "사라질 수 있다(다음 기동이 같은 처분을 다시 한다)."
        )
    return LoadedJobStore(
        store=JobStore(
            journal=journal,
            pending_queue_max=pending_queue_max,
            entries=tuple(entries.values()),
        ),
        disposition=RestartDisposition(
            running_orphans_failed=len(orphans),
            pending_over_limit_failed=len(discarded),
            pending_requeued=len(_pending_sorted(entries)),
        ),
    )
