"""RED — `ml_engine.training.jobs.store`(D-2C-3 · M6/6B-4 Q-2·Q-4). idempotency 결과
타입 셋 + uuid4 job_id + **대기 큐 상한과 최고참 폐기** + 저널 왕복·거래 성질.

M6/6B-4 — 저장소는 더 이상 프로세스 로컬 dict 만이 아니다. 저널은 **필수 인자**이므로
「조용히 비영속으로 조립」 갈래가 없다(test 도 `tmp_path` 저널을 쓴다).
"""

from __future__ import annotations

import threading
import uuid
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest

from ml_engine.serving.policy import ServingPolicy, load_serving_policy
from ml_engine.training.jobs.journal import (
    JobJournal,
    JobSubmission,
    JournalFailed,
    JournalSnapshot,
)
from ml_engine.training.jobs.pipeline import DetailCode
from ml_engine.training.jobs.state import (
    JobEvent,
    JobFailure,
    JobFailureCode,
    JobRecord,
    JobState,
    TransitionRejected,
)
from ml_engine.training.jobs.store import Conflict, JobStore, Reused, Started

_NOW = datetime(2026, 1, 1, tzinfo=UTC)
_SHIPPED_POLICY_PATH = (
    Path(__file__).resolve().parents[2] / "policy" / "serving-v1.yaml"
)


def _submission() -> JobSubmission:
    return JobSubmission(
        dataset_uri="file:///tmp/dataset",
        manifest_checksum="a" * 64,
        training_spec_version="training-spec-v1",
    )


def _store(tmp_path: Path, *, pending_queue_max: int = 4) -> JobStore:
    return JobStore(
        journal=JobJournal(tmp_path / "journal"), pending_queue_max=pending_queue_max
    )


def _submit(
    store: JobStore, key: str, *, dataset_id: str = "ds-1", at: datetime = _NOW
) -> Started | Reused | Conflict | JournalFailed:
    return store.start_or_reuse(
        key, dataset_id, accepted_at=at, submission=_submission()
    )


def _started(
    store: JobStore, key: str, *, dataset_id: str = "ds-1", at: datetime = _NOW
) -> Started:
    outcome = _submit(store, key, dataset_id=dataset_id, at=at)
    assert isinstance(outcome, Started)
    return outcome


def _states(store: JobStore, job_ids: list[str]) -> list[JobState]:
    states: list[JobState] = []
    for job_id in job_ids:
        record = store.get(job_id)
        assert record is not None
        states.append(record.state)
    return states


# ---- 기존 idempotency 계약(무변경 축) ----


def test_start_or_reuse_creates_new_job_with_uuid4_job_id(tmp_path: Path) -> None:
    result = _started(_store(tmp_path), "key-1")
    assert result.record.state is JobState.ACCEPTED
    assert result.record.dataset_id == "ds-1"
    assert uuid.UUID(result.record.job_id).version == 4


def test_same_key_same_dataset_returns_same_job(tmp_path: Path) -> None:
    store = _store(tmp_path)
    first = _started(store, "key-1")
    second = _submit(store, "key-1")
    assert isinstance(second, Reused)
    assert second.record.job_id == first.record.job_id


def test_same_key_different_dataset_is_conflict(tmp_path: Path) -> None:
    store = _store(tmp_path)
    _started(store, "key-1")
    conflict = _submit(store, "key-1", dataset_id="ds-2")
    assert isinstance(conflict, Conflict)
    assert conflict.existing_dataset_id == "ds-1"
    assert conflict.requested_dataset_id == "ds-2"


def test_different_keys_create_different_jobs(tmp_path: Path) -> None:
    store = _store(tmp_path)
    first = _started(store, "key-1")
    second = _started(store, "key-2")
    assert first.record.job_id != second.record.job_id


def test_get_unknown_job_id_returns_none(tmp_path: Path) -> None:
    assert _store(tmp_path).get("unknown") is None


def test_apply_transition_persists_running_state(tmp_path: Path) -> None:
    store = _store(tmp_path)
    job_id = _started(store, "key-1").record.job_id
    result = store.apply_transition(job_id, JobEvent.START, at=_NOW)
    assert isinstance(result, JobRecord)
    assert result.state is JobState.RUNNING
    assert store.get(job_id) == result


def test_same_key_after_terminal_reuses_job_id_not_new(tmp_path: Path) -> None:
    """설계 검토 우회 (16) — 종단 뒤에도 같은 키 재요청은 `Reused`(같은 job_id)."""
    store = _store(tmp_path)
    job_id = _started(store, "key-1").record.job_id
    assert isinstance(
        store.apply_transition(job_id, JobEvent.CANCEL, at=_NOW), JobRecord
    )
    reused = _submit(store, "key-1")
    assert isinstance(reused, Reused)
    assert reused.record.job_id == job_id
    assert reused.record.state is JobState.CANCELLED


# ---- M6/6B-4 Q-2 — 대기 큐 상한과 최고참 폐기 ----


def test_submissions_up_to_the_limit_all_stay_pending(tmp_path: Path) -> None:
    store = _store(tmp_path, pending_queue_max=4)
    job_ids = [
        _started(
            store, f"key-{index}", at=_NOW + timedelta(seconds=index)
        ).record.job_id
        for index in range(4)
    ]
    assert _states(store, job_ids) == [JobState.ACCEPTED] * 4


def test_fifth_pending_submission_discards_the_oldest(tmp_path: Path) -> None:
    """운영자 결정 2026-09-26 ③ — 최신성 우선. 상한 4 에서 다섯째 제출이 **첫째**를
    끝낸다(새 job 은 대기로 남는다)."""
    store = _store(tmp_path, pending_queue_max=4)
    job_ids = [
        _started(
            store, f"key-{index}", at=_NOW + timedelta(seconds=index)
        ).record.job_id
        for index in range(4)
    ]
    fifth = _started(store, "key-4", at=_NOW + timedelta(seconds=4))

    assert [record.job_id for record in fifth.discarded] == [job_ids[0]]
    assert fifth.record.state is JobState.ACCEPTED
    assert _states(store, job_ids) == [
        JobState.FAILED,
        JobState.ACCEPTED,
        JobState.ACCEPTED,
        JobState.ACCEPTED,
    ]


def test_discarded_job_carries_worker_resource_exhausted_and_overflow_detail(
    tmp_path: Path,
) -> None:
    """폐기 사실이 **job 상태로 남는다**(위협 모델 ② — 조회하는 쪽이 사유를 안다)."""
    store = _store(tmp_path, pending_queue_max=1)
    first = _started(store, "key-0", at=_NOW)
    _started(store, "key-1", at=_NOW + timedelta(seconds=1))

    record = store.get(first.record.job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert record.failure is not None
    assert record.failure.code is JobFailureCode.WORKER_RESOURCE_EXHAUSTED
    assert record.failure.detail_code == DetailCode.PENDING_QUEUE_OVERFLOW.value
    assert record.failure.retryable is True
    assert record.started_at is None  # 실행된 적이 없다


def test_running_job_is_never_discarded(tmp_path: Path) -> None:
    """위협 모델 ③ — 워커가 집은 job 은 대기가 아니므로 폐기 대상이 아니다."""
    store = _store(tmp_path, pending_queue_max=1)
    running = _started(store, "key-running", at=_NOW)
    assert isinstance(
        store.apply_transition(running.record.job_id, JobEvent.START, at=_NOW),
        JobRecord,
    )
    for index in range(3):
        _started(store, f"key-{index}", at=_NOW + timedelta(seconds=index + 1))

    record = store.get(running.record.job_id)
    assert record is not None
    assert record.state is JobState.RUNNING


def test_terminal_job_is_never_discarded(tmp_path: Path) -> None:
    store = _store(tmp_path, pending_queue_max=1)
    cancelled = _started(store, "key-cancelled", at=_NOW)
    assert isinstance(
        store.apply_transition(cancelled.record.job_id, JobEvent.CANCEL, at=_NOW),
        JobRecord,
    )
    for index in range(3):
        _started(store, f"key-{index}", at=_NOW + timedelta(seconds=index + 1))

    record = store.get(cancelled.record.job_id)
    assert record is not None
    assert record.state is JobState.CANCELLED


def test_tie_on_accepted_at_is_broken_by_job_id(tmp_path: Path) -> None:
    """동률은 `job_id` 로 깬다 — 어느 것이 폐기되는지가 결정적이어야 한다."""
    store = _store(tmp_path, pending_queue_max=2)
    job_ids = [
        _started(store, f"key-{index}", at=_NOW).record.job_id for index in range(3)
    ]
    oldest_by_tie_break = min(job_ids)

    record = store.get(oldest_by_tie_break)
    assert record is not None
    assert record.state is JobState.FAILED
    assert sum(1 for state in _states(store, job_ids) if state is JobState.FAILED) == 1


def test_idempotent_reuse_discards_nothing(tmp_path: Path) -> None:
    """설계 검토 우회 (2) — 멱등 재사용은 적재가 아니므로 폐기도 없다."""
    store = _store(tmp_path, pending_queue_max=2)
    job_ids = [
        _started(
            store, f"key-{index}", at=_NOW + timedelta(seconds=index)
        ).record.job_id
        for index in range(2)
    ]
    for _ in range(5):
        assert isinstance(_submit(store, "key-0"), Reused)
    assert _states(store, job_ids) == [JobState.ACCEPTED, JobState.ACCEPTED]


def test_concurrent_submissions_never_exceed_the_limit(tmp_path: Path) -> None:
    """설계 검토 우회 (1) — 동시 제출이 각자 「대기 4」를 보고 둘 다 적재하면 상한이
    깨진다. 판정·적재·폐기·저널 쓰기가 한 잠금 아래 있음을 래치로 실측한다."""
    store = _store(tmp_path, pending_queue_max=4)
    submitters = 12
    barrier = threading.Barrier(submitters)
    outcomes: list[Started] = []
    guard = threading.Lock()

    def _submit_at_once(index: int) -> None:
        barrier.wait(timeout=10.0)
        outcome = _submit(store, f"key-{index}", at=_NOW + timedelta(seconds=index))
        assert isinstance(outcome, Started)
        with guard:
            outcomes.append(outcome)

    threads = [
        threading.Thread(target=_submit_at_once, args=(index,))
        for index in range(submitters)
    ]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=20.0)

    assert len(outcomes) == submitters
    states = _states(store, [outcome.record.job_id for outcome in outcomes])
    assert sum(1 for state in states if state is JobState.ACCEPTED) == 4
    assert sum(1 for state in states if state is JobState.FAILED) == submitters - 4


def test_pending_queue_max_below_one_is_rejected(tmp_path: Path) -> None:
    for value in (0, -1):
        with pytest.raises(ValueError, match="pending_queue_max"):
            _store(tmp_path, pending_queue_max=value)


# ---- Q-3 값→거동 — 정책 파일 값이 런타임 큐에 닿는다 ----


def test_shipped_policy_value_reaches_the_queue(tmp_path: Path) -> None:
    """출하 `serving-v1.yaml` 의 `pending_queue_max` 가 **실제 폐기 시점**을 정한다.
    값이 4 가 아니게 되면(3·5 로 바꿔치우기) 이 test 가 붉어진다."""
    policy = load_serving_policy(_SHIPPED_POLICY_PATH)
    assert isinstance(policy, ServingPolicy)
    store = _store(tmp_path, pending_queue_max=policy.pending_queue_max)

    for index in range(4):
        outcome = _started(store, f"key-{index}", at=_NOW + timedelta(seconds=index))
        assert outcome.discarded == ()

    fifth = _started(store, "key-4", at=_NOW + timedelta(seconds=4))
    assert len(fifth.discarded) == 1


# ---- Q-4 — 저널은 `apply_transition`·`start_or_reuse` 안에서(설계 검토 우회 (9)) ----


def test_every_mutation_is_journaled(tmp_path: Path) -> None:
    store = _store(tmp_path)
    journal = JobJournal(tmp_path / "journal")
    job_id = _started(store, "key-1").record.job_id

    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert [entry.record.state for entry in loaded.entries] == [JobState.ACCEPTED]
    assert loaded.entries[0].idempotency_key == "key-1"
    assert loaded.entries[0].submission == _submission()

    assert isinstance(
        store.apply_transition(job_id, JobEvent.START, at=_NOW), JobRecord
    )
    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert [entry.record.state for entry in loaded.entries] == [JobState.RUNNING]


def test_discard_and_admission_land_in_one_journal_write(tmp_path: Path) -> None:
    """폐기와 적재가 **같은** 저널 상태로 보인다 — 둘 사이에 상한이 깨진 상태가 디스크에
    남지 않는다."""
    store = _store(tmp_path, pending_queue_max=1)
    journal = JobJournal(tmp_path / "journal")
    _started(store, "key-0", at=_NOW)
    _started(store, "key-1", at=_NOW + timedelta(seconds=1))

    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    states = sorted(entry.record.state.value for entry in loaded.entries)
    assert states == ["ACCEPTED", "FAILED"]


def test_journal_write_failure_leaves_memory_unchanged(tmp_path: Path) -> None:
    """설계 검토 우회 (9) — 저널 쓰기가 실패하면 메모리도 바뀌지 않는다(상태·저널 일치).
    「메모리는 바뀌었는데 디스크는 아니다」가 되면 재기동이 사실을 잃는다."""
    store = _store(tmp_path, pending_queue_max=4)
    existing = _started(store, "key-1").record.job_id

    directory = tmp_path / "journal"
    directory.chmod(0o500)
    try:
        rejected_admission = _submit(store, "key-2")
        rejected_transition = store.apply_transition(existing, JobEvent.START, at=_NOW)
    finally:
        directory.chmod(0o700)

    assert isinstance(rejected_admission, JournalFailed)
    assert isinstance(rejected_transition, JournalFailed)
    record = store.get(existing)
    assert record is not None
    assert record.state is JobState.ACCEPTED  # START 가 반영되지 않았다
    assert isinstance(_submit(store, "key-2"), Started)  # 키가 선점되지 않았다


def test_terminal_cancel_no_op_does_not_rewrite_journal(tmp_path: Path) -> None:
    """종단 Cancel 은 멱등 no-op 이라 쓸 것이 없다 — 쓰기 실패가 그 호출을 깨지 않는다."""
    store = _store(tmp_path)
    job_id = _started(store, "key-1").record.job_id
    assert isinstance(
        store.apply_transition(job_id, JobEvent.CANCEL, at=_NOW), JobRecord
    )

    directory = tmp_path / "journal"
    directory.chmod(0o500)
    try:
        again = store.apply_transition(job_id, JobEvent.CANCEL, at=_NOW)
    finally:
        directory.chmod(0o700)
    assert isinstance(again, JobRecord)
    assert again.state is JobState.CANCELLED


def test_pending_discard_transition_is_allowed_but_start_after_it_is_rejected(
    tmp_path: Path,
) -> None:
    store = _store(tmp_path)
    job_id = _started(store, "key-1").record.job_id
    discarded = store.apply_transition(
        job_id,
        JobEvent.FAIL,
        at=_NOW,
        failure=JobFailure(
            code=JobFailureCode.WORKER_RESOURCE_EXHAUSTED,
            detail_code=DetailCode.PENDING_QUEUE_OVERFLOW.value,
        ),
    )
    assert isinstance(discarded, JobRecord)  # ACCEPTED 에서는 허용된다
    assert isinstance(
        store.apply_transition(job_id, JobEvent.START, at=_NOW), TransitionRejected
    )


def test_apply_transition_on_unknown_job_id_is_none(tmp_path: Path) -> None:
    assert _store(tmp_path).apply_transition("nope", JobEvent.START, at=_NOW) is None


# ---- 대기 목록(조립 근이 재적재에 쓴다) ----


def test_pending_jobs_are_ordered_by_accepted_at_then_job_id(tmp_path: Path) -> None:
    store = _store(tmp_path, pending_queue_max=4)
    third = _started(store, "key-3", at=_NOW + timedelta(seconds=3))
    first = _started(store, "key-1", at=_NOW + timedelta(seconds=1))
    second = _started(store, "key-2", at=_NOW + timedelta(seconds=2))
    running = _started(store, "key-running", at=_NOW)
    assert isinstance(
        store.apply_transition(running.record.job_id, JobEvent.START, at=_NOW),
        JobRecord,
    )

    pending = store.pending_jobs()
    assert [job.record.job_id for job in pending] == [
        first.record.job_id,
        second.record.job_id,
        third.record.job_id,
    ]
    assert pending[0].submission == _submission()
