"""RED — `ml_engine.training.jobs.store.load_job_store`(M6/6B-4 Q-5). 재기동 등가는
**새 store 인스턴스가 같은 저널을 읽는 것**이다(6D-2 가 Kotlin 에서 쓴 「in-JVM 등가」와
같은 한계 — 프로세스를 실제로 죽이지는 않는다, 알려진 제한).

재는 것: 종단 job 보존 · 멱등 키 보존 · RUNNING 고아 처분(재개 아님) · ACCEPTED 고아
재적재(상한 재적용) · **수렴 고정점**(재재기동 처분 0) · 쓰기 도중 크래시 주입 뒤의 상태 ·
판독 불가는 빈 저장소가 아니다.
"""

from __future__ import annotations

import logging
import os
from datetime import UTC, datetime, timedelta
from pathlib import Path

import pytest

from ml_engine.training.jobs import journal_file
from ml_engine.training.jobs.journal import (
    JobSubmission,
    JournalFailed,
    JournalFailureReason,
    JournalSnapshot,
)
from ml_engine.training.jobs.journal_file import JobJournal
from ml_engine.training.jobs.pipeline import DetailCode
from ml_engine.training.jobs.state import (
    ArtifactReference,
    EvaluationReportReference,
    JobEvent,
    JobFailureCode,
    JobRecord,
    JobState,
)
from ml_engine.training.jobs.store import (
    JobStore,
    LoadedJobStore,
    Reused,
    Started,
    load_job_store,
)

_NOW = datetime(2026, 1, 1, tzinfo=UTC)
_RESTART_AT = datetime(2026, 1, 2, tzinfo=UTC)


def _submission(spec_version: str = "training-spec-v1") -> JobSubmission:
    return JobSubmission(
        dataset_uri="file:///tmp/dataset",
        manifest_checksum="a" * 64,
        training_spec_version=spec_version,
    )


def _journal(tmp_path: Path) -> JobJournal:
    return JobJournal(tmp_path / "journal")


def _journal_path(tmp_path: Path) -> Path:
    """저널 파일 자리 — production 표면에는 없다(vr H-2). test 는 모듈 private 이름으로
    같은 자리를 계산한다."""
    return tmp_path / "journal" / journal_file._FILE_NAME


def _first_boot(tmp_path: Path, *, pending_queue_max: int = 4) -> JobStore:
    return JobStore(journal=_journal(tmp_path), pending_queue_max=pending_queue_max)


def _restart(
    tmp_path: Path, *, pending_queue_max: int = 4, at: datetime = _RESTART_AT
) -> LoadedJobStore:
    loaded = load_job_store(
        journal=_journal(tmp_path), pending_queue_max=pending_queue_max, at=at
    )
    assert isinstance(loaded, LoadedJobStore)
    return loaded


def _submit(store: JobStore, key: str, *, at: datetime = _NOW) -> str:
    outcome = store.start_or_reuse(
        key, "ds-1", accepted_at=at, submission=_submission()
    )
    assert isinstance(outcome, Started)
    return outcome.record.job_id


def _artifact() -> ArtifactReference:
    return ArtifactReference(
        uri="file:///a",
        release_id="r1",
        artifact_checksum="0" * 64,
        feature_schema_version="v2",
        code_version="sha-1",
        dataset_id="ds-1",
        manifest_schema_version="artifact-manifest-v1",
    )


def _evaluation() -> EvaluationReportReference:
    return EvaluationReportReference(
        uri="file:///r", checksum="1" * 64, report_schema_version="evaluation-report-v1"
    )


# ---- 첫 기동 ----


def test_absent_journal_loads_an_empty_store_and_writes_the_journal(
    tmp_path: Path,
) -> None:
    """저널이 없으면 빈 저장소로 시작하되 **한 번 쓴다** — 그 자리가 쓰기 가능한지를
    기동 시점에 확인한다(볼륨·소유자 잘못을 첫 job 제출까지 미루지 않는다)."""
    loaded = _restart(tmp_path)
    assert loaded.store.pending_jobs() == ()
    assert loaded.disposition.running_orphans_failed == 0
    assert loaded.disposition.pending_over_limit_failed == 0
    assert _journal_path(tmp_path).exists()


def test_unwritable_journal_directory_fails_the_load(tmp_path: Path) -> None:
    directory = tmp_path / "journal"
    directory.mkdir()
    directory.chmod(0o500)
    try:
        result = load_job_store(
            journal=_journal(tmp_path), pending_queue_max=4, at=_RESTART_AT
        )
    finally:
        directory.chmod(0o700)
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.WRITE_FAILED


def test_unreadable_journal_is_not_an_empty_store(tmp_path: Path) -> None:
    """위협 모델 ⑤ — 판독 불가는 조용한 초기화가 아니라 **판정 불가**다."""
    journal = _journal(tmp_path)
    _journal_path(tmp_path).parent.mkdir(parents=True)
    _journal_path(tmp_path).write_text("깨진 저널")
    result = load_job_store(journal=journal, pending_queue_max=4, at=_RESTART_AT)
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.MALFORMED


# ---- 보존 ----


@pytest.mark.parametrize(
    ("event", "expected"),
    [
        (JobEvent.CANCEL, JobState.CANCELLED),
        (JobEvent.SUCCEED, JobState.SUCCEEDED),
    ],
)
def test_terminal_jobs_survive_a_restart(
    tmp_path: Path, event: JobEvent, expected: JobState
) -> None:
    store = _first_boot(tmp_path)
    job_id = _submit(store, "key-1")
    if event is JobEvent.SUCCEED:
        assert isinstance(
            store.apply_transition(job_id, JobEvent.START, at=_NOW), JobRecord
        )
        assert isinstance(
            store.apply_transition(
                job_id,
                JobEvent.SUCCEED,
                at=_NOW + timedelta(seconds=1),
                artifact=_artifact(),
                evaluation=_evaluation(),
            ),
            JobRecord,
        )
    else:
        assert isinstance(store.apply_transition(job_id, event, at=_NOW), JobRecord)

    record = _restart(tmp_path).store.get(job_id)
    assert record is not None
    assert record.state is expected
    if expected is JobState.SUCCEEDED:
        assert record.artifact == _artifact()
        assert record.evaluation == _evaluation()


def test_idempotency_key_survives_a_restart(tmp_path: Path) -> None:
    """재기동 뒤 같은 키 재요청은 **새 job 을 만들지 않는다** — 영속이 멱등을 함께
    살려야 재시도가 중복 학습이 되지 않는다."""
    store = _first_boot(tmp_path)
    job_id = _submit(store, "key-1")

    restarted = _restart(tmp_path).store
    reused = restarted.start_or_reuse(
        "key-1", "ds-1", accepted_at=_RESTART_AT, submission=_submission()
    )
    assert isinstance(reused, Reused)
    assert reused.record.job_id == job_id


def test_restarted_pending_job_keeps_its_submission(tmp_path: Path) -> None:
    """재적재가 **실행 가능**해야 한다 — 제출 입력이 저널에서 돌아온다."""
    store = _first_boot(tmp_path)
    job_id = _submit(store, "key-1")
    pending = _restart(tmp_path).store.pending_jobs()
    assert [job.record.job_id for job in pending] == [job_id]
    assert pending[0].submission == _submission()


# ---- 고아 처분 ----


def test_running_orphan_is_failed_with_runner_lost_detail(tmp_path: Path) -> None:
    """설계 검토 우회 (8) — 재개하지 않는다(중복 학습·산출물 덮어쓰기 방지). 처분만."""
    store = _first_boot(tmp_path)
    job_id = _submit(store, "key-1")
    assert isinstance(
        store.apply_transition(job_id, JobEvent.START, at=_NOW), JobRecord
    )

    loaded = _restart(tmp_path)
    record = loaded.store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert record.failure is not None
    assert record.failure.code is JobFailureCode.TRAINING_ERROR
    assert record.failure.detail_code == DetailCode.RUNNER_LOST_ON_RESTART.value
    # cr M-5 — `retryable` 은 코드의 함수다(`TRAINING_ERROR` 는 거짓). 앞 판은 저장소의
    # 처분 경로가 무조건 참을 실어 같은 코드가 자리마다 다른 값을 들었다.
    assert record.failure.retryable is False
    assert loaded.disposition.running_orphans_failed == 1
    assert loaded.store.pending_jobs() == ()  # 재적재 대상이 아니다


def test_accepted_orphans_are_requeued_in_accepted_at_order(tmp_path: Path) -> None:
    store = _first_boot(tmp_path)
    third = _submit(store, "key-3", at=_NOW + timedelta(seconds=3))
    first = _submit(store, "key-1", at=_NOW + timedelta(seconds=1))
    second = _submit(store, "key-2", at=_NOW + timedelta(seconds=2))

    loaded = _restart(tmp_path)
    assert [job.record.job_id for job in loaded.store.pending_jobs()] == [
        first,
        second,
        third,
    ]
    assert loaded.disposition.pending_requeued == 3
    assert loaded.disposition.pending_over_limit_failed == 0


def test_accepted_orphans_over_the_limit_are_discarded_oldest_first(
    tmp_path: Path,
) -> None:
    """설계 검토 우회 (7) — 이전 버전 저널이 상한보다 많은 대기를 들고 있을 수 있다.
    재적재에도 상한을 적용한다(상한이 저널을 통해 우회되지 않는다)."""
    store = _first_boot(tmp_path, pending_queue_max=5)
    job_ids = [
        _submit(store, f"key-{index}", at=_NOW + timedelta(seconds=index))
        for index in range(5)
    ]

    loaded = _restart(tmp_path, pending_queue_max=2)
    assert loaded.disposition.pending_over_limit_failed == 3
    assert [job.record.job_id for job in loaded.store.pending_jobs()] == job_ids[3:]
    for discarded_job_id in job_ids[:3]:
        record = loaded.store.get(discarded_job_id)
        assert record is not None
        assert record.state is JobState.FAILED
        assert record.failure is not None
        assert record.failure.code is JobFailureCode.WORKER_RESOURCE_EXHAUSTED
        assert record.failure.detail_code == DetailCode.PENDING_QUEUE_OVERFLOW.value
        assert record.failure.retryable is True  # 용량 고갈만 참(cr M-5)


def test_restart_converges_second_restart_disposes_nothing(tmp_path: Path) -> None:
    """수렴 고정점(6D-2 선례) — 한 번 처분한 뒤에는 재재기동이 아무것도 처분하지
    않는다. 모든 job 이 종단이거나 대기이고, 대기 수는 상한 아래다."""
    store = _first_boot(tmp_path, pending_queue_max=5)
    running = _submit(store, "key-running", at=_NOW)
    assert isinstance(
        store.apply_transition(running, JobEvent.START, at=_NOW), JobRecord
    )
    for index in range(4):
        _submit(store, f"key-{index}", at=_NOW + timedelta(seconds=index + 1))

    first_restart = _restart(tmp_path, pending_queue_max=2)
    assert first_restart.disposition.running_orphans_failed == 1
    assert first_restart.disposition.pending_over_limit_failed == 2

    second_restart = _restart(
        tmp_path, pending_queue_max=2, at=_RESTART_AT + timedelta(days=1)
    )
    assert second_restart.disposition.running_orphans_failed == 0
    assert second_restart.disposition.pending_over_limit_failed == 0
    assert second_restart.disposition.pending_requeued == 2

    states = [
        record.state
        for record in (
            second_restart.store.get(job_id)
            for job_id in (
                running,
                *[job.record.job_id for job in second_restart.store.pending_jobs()],
            )
        )
        if record is not None
    ]
    assert JobState.RUNNING not in states


# ---- 쓰기 도중 크래시 주입 ----


@pytest.mark.parametrize("crash_at", ["fsync", "replace"])
def test_crash_mid_transition_write_leaves_the_previous_state(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, crash_at: str
) -> None:
    """전이의 저널 쓰기가 끊기면 **이전 상태**가 남는다 — 재기동이 그 이전 상태를 보고
    처분한다(판독 불가 저널이 생기지 않는다)."""
    store = _first_boot(tmp_path)
    job_id = _submit(store, "key-1")

    def _boom(*args: object, **kwargs: object) -> None:
        raise OSError(28, "크래시 주입")

    monkeypatch.setattr(os, crash_at, _boom)
    failed = store.apply_transition(job_id, JobEvent.START, at=_NOW)
    monkeypatch.undo()
    assert isinstance(failed, JournalFailed)

    loaded = _restart(tmp_path)
    record = loaded.store.get(job_id)
    assert record is not None
    assert record.state is JobState.ACCEPTED  # START 가 저널에 닿지 않았다
    assert loaded.disposition.running_orphans_failed == 0
    assert loaded.disposition.pending_requeued == 1


def test_crash_after_commit_keeps_the_new_state(tmp_path: Path) -> None:
    """통제군 — 쓰기가 성공한 전이는 재기동 뒤에도 남는다(위 test 가 「아무것도 안
    남는다」를 재는 것이 아님을 보인다)."""
    store = _first_boot(tmp_path)
    job_id = _submit(store, "key-1")
    assert isinstance(
        store.apply_transition(job_id, JobEvent.START, at=_NOW), JobRecord
    )
    loaded = _restart(tmp_path)
    record = loaded.store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED  # RUNNING 고아로 처분됐다
    assert loaded.disposition.running_orphans_failed == 1


def test_restart_rewrites_the_journal_with_the_disposed_state(tmp_path: Path) -> None:
    """처분이 **저널에 남는다** — 그래서 재재기동이 같은 처분을 반복하지 않는다."""
    store = _first_boot(tmp_path)
    job_id = _submit(store, "key-1")
    assert isinstance(
        store.apply_transition(job_id, JobEvent.START, at=_NOW), JobRecord
    )
    _restart(tmp_path)

    on_disk = _journal(tmp_path).read()
    assert isinstance(on_disk, JournalSnapshot)
    assert [entry.record.state for entry in on_disk.entries] == [JobState.FAILED]


def test_directory_fsync_failure_keeps_disk_and_memory_together(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    """vr M-1 — 교체 뒤 디렉터리 `fsync` 가 실패해도 디스크와 메모리가 갈라지지 않는다.

    앞 판: 결과가 `JournalFailed(WRITE_FAILED)` 라 클라이언트는 `JOB_STORE_UNAVAILABLE`
    (「받지 않았다」)을 받고 메모리 대기는 0 인데 **디스크에는 ACCEPTED 1** 이 남아,
    거절했다고 답한 job 이 재기동 뒤 실행됐다(취소 경로도 같은 모양).

    이제 교체가 끝난 뒤의 실패는 「교체됨, 내구성 미확인」이고 저장소는 메모리를 커밋하며
    그 사실을 로그로 남긴다 — 디스크와 메모리가 같은 상태다."""
    store = _first_boot(tmp_path)

    def _boom(directory: Path) -> None:
        raise OSError(5, "EIO 주입")

    monkeypatch.setattr(journal_file, "_fsync_directory", _boom)
    with caplog.at_level(logging.ERROR, logger="ml_engine.training.jobs.store"):
        job_id = _submit(store, "key-1")
    monkeypatch.undo()

    assert [record.job_id for record in (store.get(job_id),) if record] == [job_id]
    assert [job.record.job_id for job in store.pending_jobs()] == [job_id]
    assert any("내구성" in record.getMessage() for record in caplog.records)

    loaded = _restart(tmp_path)
    assert [job.record.job_id for job in loaded.store.pending_jobs()] == [job_id]


# ---- 재기동 처분도 **job 마다** 로그를 남긴다(code-review 문서 축) ----


def test_restart_disposals_log_one_warning_per_job(
    tmp_path: Path, caplog: pytest.LogCaptureFixture
) -> None:
    """상한 폐기는 폐기 시점에 job 마다 WARNING 을 남긴다(`servicer._log_discarded`).
    **재기동 처분**은 계수 세 줄만 남겨, job_id 를 쥔 쪽 말고는 어느 job 이 어떤 사유로
    처분됐는지 알 방법이 없었다 — `ListTrainingJobs` 도 metric 도 없다.

    처분된 job 마다 job_id · accepted_at · 사유를 남긴다. 통제군: 처분이 없는 기동은 이
    로그를 남기지 않는다."""
    store = _first_boot(tmp_path, pending_queue_max=5)
    orphan = _submit(store, "key-orphan", at=_NOW)
    assert isinstance(store.apply_transition(orphan, JobEvent.START, at=_NOW), JobRecord)
    over_limit = [
        _submit(store, f"key-{index}", at=_NOW + timedelta(seconds=index + 1))
        for index in range(3)
    ]

    with caplog.at_level(logging.WARNING, logger="ml_engine.training.jobs.store"):
        loaded = _restart(tmp_path, pending_queue_max=1)

    assert loaded.disposition.running_orphans_failed == 1
    assert loaded.disposition.pending_over_limit_failed == 2
    messages = [record.getMessage() for record in caplog.records]
    assert len(messages) == 3  # 고아 하나 + 상한 초과 둘

    orphan_lines = [line for line in messages if orphan in line]
    assert len(orphan_lines) == 1
    assert DetailCode.RUNNER_LOST_ON_RESTART.value in orphan_lines[0]
    assert _NOW.isoformat() in orphan_lines[0]

    for discarded_job_id in over_limit[:2]:
        lines = [line for line in messages if discarded_job_id in line]
        assert len(lines) == 1
        assert DetailCode.PENDING_QUEUE_OVERFLOW.value in lines[0]

    # 통제군 — 더 처분할 것이 없는 재기동은 조용하다(수렴).
    caplog.clear()
    with caplog.at_level(logging.WARNING, logger="ml_engine.training.jobs.store"):
        again = _restart(tmp_path, pending_queue_max=1)
    assert again.disposition.running_orphans_failed == 0
    assert again.disposition.pending_over_limit_failed == 0
    assert caplog.records == []
