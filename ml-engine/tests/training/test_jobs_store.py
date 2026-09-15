"""RED — `ml_engine.training.jobs.store`(D-2C-3, 설계 검토 (1) 「멱등」). idempotency
결과 타입 셋(Started/Reused/Conflict) + uuid4 job_id."""

from __future__ import annotations

import uuid
from datetime import UTC, datetime

from ml_engine.training.jobs.state import JobRecord, JobState
from ml_engine.training.jobs.store import Conflict, InMemoryJobStore, Reused, Started

_NOW = datetime(2026, 1, 1, tzinfo=UTC)


def test_start_or_reuse_creates_new_job_with_uuid4_job_id() -> None:
    store = InMemoryJobStore()
    result = store.start_or_reuse("key-1", "ds-1", accepted_at=_NOW)
    assert isinstance(result, Started)
    assert result.record.state is JobState.ACCEPTED
    assert result.record.dataset_id == "ds-1"
    parsed = uuid.UUID(result.record.job_id)
    assert parsed.version == 4


def test_same_key_same_dataset_returns_same_job() -> None:
    store = InMemoryJobStore()
    first = store.start_or_reuse("key-1", "ds-1", accepted_at=_NOW)
    second = store.start_or_reuse("key-1", "ds-1", accepted_at=_NOW)
    assert isinstance(first, Started)
    assert isinstance(second, Reused)
    assert second.record.job_id == first.record.job_id


def test_same_key_different_dataset_is_conflict() -> None:
    store = InMemoryJobStore()
    started = store.start_or_reuse("key-1", "ds-1", accepted_at=_NOW)
    assert isinstance(started, Started)
    conflict = store.start_or_reuse("key-1", "ds-2", accepted_at=_NOW)
    assert isinstance(conflict, Conflict)
    assert conflict.existing_dataset_id == "ds-1"
    assert conflict.requested_dataset_id == "ds-2"


def test_different_keys_create_different_jobs() -> None:
    store = InMemoryJobStore()
    first = store.start_or_reuse("key-1", "ds-1", accepted_at=_NOW)
    second = store.start_or_reuse("key-2", "ds-1", accepted_at=_NOW)
    assert isinstance(first, Started)
    assert isinstance(second, Started)
    assert first.record.job_id != second.record.job_id


def test_get_unknown_job_id_returns_none() -> None:
    store = InMemoryJobStore()
    assert store.get("unknown") is None


def test_get_returns_stored_record() -> None:
    store = InMemoryJobStore()
    started = store.start_or_reuse("key-1", "ds-1", accepted_at=_NOW)
    assert isinstance(started, Started)
    assert store.get(started.record.job_id) == started.record


def test_replace_reflects_transition() -> None:
    store = InMemoryJobStore()
    started = store.start_or_reuse("key-1", "ds-1", accepted_at=_NOW)
    assert isinstance(started, Started)
    running = JobRecord(
        job_id=started.record.job_id,
        state=JobState.RUNNING,
        dataset_id="ds-1",
        accepted_at=_NOW,
        started_at=_NOW,
    )
    store.replace(running)
    assert store.get(started.record.job_id) == running


def test_same_key_after_terminal_reuses_job_id_not_new() -> None:
    """설계 검토 우회 (16) — `CANCELLED` 뒤에도 같은 키 재요청은 `Reused`(같은
    job_id) — 재시도는 새 키로 해야 한다는 2C 문면."""
    store = InMemoryJobStore()
    started = store.start_or_reuse("key-1", "ds-1", accepted_at=_NOW)
    assert isinstance(started, Started)
    cancelled = JobRecord(
        job_id=started.record.job_id,
        state=JobState.CANCELLED,
        dataset_id="ds-1",
        accepted_at=_NOW,
        finished_at=_NOW,
    )
    store.replace(cancelled)
    reused = store.start_or_reuse("key-1", "ds-1", accepted_at=_NOW)
    assert isinstance(reused, Reused)
    assert reused.record.job_id == started.record.job_id
    assert reused.record.state is JobState.CANCELLED
