"""RED — `ml_engine.training.jobs.journal`(M6/6B-4 Q-4). 원자 교체(tmp → fsync →
`os.replace` → 디렉터리 fsync) · canonical JSON · 「없음」과 「깨짐」의 구분 · 쓰기 도중
크래시 주입. 위협 모델 ⑤ — 저널 쓰기 도중 크래시는 「이전 상태 또는 새 상태」만 남기고,
판독 불가는 조용한 빈 저장소가 아니라 닫힌 사유 결과 타입이 된다.
"""

from __future__ import annotations

import json
import os
import stat
from datetime import UTC, datetime
from pathlib import Path

import pytest

from ml_engine.training.jobs.journal import (
    JOURNAL_SCHEMA_VERSION,
    JobJournal,
    JobSubmission,
    JournalAbsent,
    JournalEntry,
    JournalFailed,
    JournalFailureReason,
    JournalSnapshot,
    JournalWritten,
)
from ml_engine.training.jobs.state import (
    ArtifactReference,
    EvaluationReportReference,
    JobFailure,
    JobFailureCode,
    JobRecord,
    JobState,
)

_NOW = datetime(2026, 1, 1, tzinfo=UTC)
_LATER = datetime(2026, 1, 1, 1, tzinfo=UTC)


def _submission(spec_version: str = "training-spec-v1") -> JobSubmission:
    return JobSubmission(
        dataset_uri="file:///tmp/dataset",
        manifest_checksum="a" * 64,
        training_spec_version=spec_version,
    )


def _entry(
    job_id: str,
    state: JobState,
    *,
    key: str | None = None,
    **overrides: object,
) -> JournalEntry:
    filled: dict[str, object] = {}
    if state is JobState.SUCCEEDED:
        filled = {
            "started_at": _NOW,
            "finished_at": _LATER,
            "artifact": ArtifactReference(
                uri="file:///a",
                release_id="r1",
                artifact_checksum="0" * 64,
                feature_schema_version="v2",
                code_version="sha-1",
                dataset_id="ds-1",
                manifest_schema_version="artifact-manifest-v1",
            ),
            "evaluation": EvaluationReportReference(
                uri="file:///r",
                checksum="1" * 64,
                report_schema_version="evaluation-report-v1",
            ),
        }
    elif state is JobState.FAILED:
        filled = {
            "finished_at": _LATER,
            "failure": JobFailure(
                code=JobFailureCode.WORKER_RESOURCE_EXHAUSTED,
                retryable=True,
                detail_code="PENDING_QUEUE_OVERFLOW",
            ),
        }
    elif state is JobState.RUNNING:
        filled = {"started_at": _NOW}
    elif state is JobState.CANCELLED:
        filled = {"finished_at": _LATER}
    filled.update(overrides)
    record = JobRecord(
        job_id=job_id,
        state=state,
        dataset_id="ds-1",
        accepted_at=_NOW,
        **filled,  # type: ignore[arg-type]
    )
    return JournalEntry(
        idempotency_key=key if key is not None else f"key-{job_id}",
        record=record,
        submission=_submission(),
    )


def _all_states_snapshot() -> JournalSnapshot:
    return JournalSnapshot(
        entries=tuple(
            _entry(f"job-{index}", state)
            for index, state in enumerate(
                (
                    JobState.ACCEPTED,
                    JobState.RUNNING,
                    JobState.SUCCEEDED,
                    JobState.FAILED,
                    JobState.CANCELLED,
                )
            )
        )
    )


def _write_raw(journal: JobJournal, text: str) -> None:
    journal.path.parent.mkdir(parents=True, exist_ok=True)
    journal.path.write_text(text, encoding="utf-8")


# ---- 「없음」과 「깨짐」의 구분(위협 모델 ⑤) ----


def test_absent_journal_reads_as_absent(tmp_path: Path) -> None:
    result = JobJournal(tmp_path / "journal").read()
    assert isinstance(result, JournalAbsent)


def test_malformed_payload_is_failed_not_absent(tmp_path: Path) -> None:
    """깨진 저널을 「없음」으로 읽으면 조용히 빈 저장소로 시작한다 — 그 경로를 닫는다."""
    journal = JobJournal(tmp_path / "journal")
    _write_raw(journal, "{not json at all")
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.MALFORMED


def test_truncated_payload_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(journal.write(_all_states_snapshot()), JournalWritten)
    full = journal.path.read_bytes()
    journal.path.write_bytes(full[: len(full) // 2])
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.MALFORMED


def test_unsupported_schema_version_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    _write_raw(journal, json.dumps({"schema_version": "job-journal-v99", "jobs": []}))
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.SCHEMA_UNSUPPORTED


def test_journal_path_is_a_directory_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    journal.path.mkdir(parents=True)
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.NOT_A_FILE


# ---- 왕복 등가 ----


def test_round_trip_preserves_every_state(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    snapshot = _all_states_snapshot()
    written = journal.write(snapshot)
    assert isinstance(written, JournalWritten)
    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert loaded == snapshot


def test_round_trip_preserves_submission_and_idempotency_key(tmp_path: Path) -> None:
    """재기동 뒤 대기 job 을 **실행할 수 있어야** 하므로 제출 payload 가 함께 남는다."""
    journal = JobJournal(tmp_path / "journal")
    entry = _entry("job-x", JobState.ACCEPTED, key="idem-x")
    assert isinstance(journal.write(JournalSnapshot(entries=(entry,))), JournalWritten)
    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert loaded.entries[0].idempotency_key == "idem-x"
    assert loaded.entries[0].submission == _submission()


def test_empty_snapshot_round_trips(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(journal.write(JournalSnapshot(entries=())), JournalWritten)
    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert loaded.entries == ()


# ---- canonical JSON(기존 관례: sort_keys·구분자·allow_nan=False) ----


def test_payload_is_canonical_and_byte_stable(tmp_path: Path) -> None:
    first = JobJournal(tmp_path / "a")
    second = JobJournal(tmp_path / "b")
    snapshot = _all_states_snapshot()
    assert isinstance(first.write(snapshot), JournalWritten)
    assert isinstance(second.write(snapshot), JournalWritten)
    payload = first.path.read_bytes()
    assert payload == second.path.read_bytes()
    assert b", " not in payload and b": " not in payload
    decoded = json.loads(payload)
    assert decoded["schema_version"] == JOURNAL_SCHEMA_VERSION
    assert list(decoded) == sorted(decoded)


# ---- 원자 교체와 크래시 주입(위협 모델 ⑤) ----


def test_successful_write_leaves_no_temporary_file(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(journal.write(_all_states_snapshot()), JournalWritten)
    assert sorted(path.name for path in journal.path.parent.iterdir()) == [
        journal.path.name
    ]


@pytest.mark.parametrize("crash_at", ["fsync", "replace"])
def test_crash_mid_write_keeps_previous_readable_snapshot(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, crash_at: str
) -> None:
    """쓰기 도중 크래시(파일 fsync · 원자 교체)는 **이전 상태**를 남긴다 — 판독 불가
    저널이 생기지 않는다. 결과는 예외가 아니라 닫힌 사유다."""
    journal = JobJournal(tmp_path / "journal")
    first = JournalSnapshot(entries=(_entry("job-1", JobState.ACCEPTED),))
    assert isinstance(journal.write(first), JournalWritten)

    def _boom(*args: object, **kwargs: object) -> None:
        raise OSError(28, "크래시 주입")

    monkeypatch.setattr(os, crash_at, _boom)
    second = JournalSnapshot(entries=(_entry("job-2", JobState.ACCEPTED),))
    failed = journal.write(second)
    monkeypatch.undo()

    assert isinstance(failed, JournalFailed)
    assert failed.reason is JournalFailureReason.WRITE_FAILED
    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert loaded == first


def test_leftover_temporary_file_does_not_break_read(tmp_path: Path) -> None:
    """이전 크래시가 남긴 tmp 파일은 판독 대상이 아니다(원자 교체의 중간물)."""
    journal = JobJournal(tmp_path / "journal")
    snapshot = _all_states_snapshot()
    assert isinstance(journal.write(snapshot), JournalWritten)
    journal.path.with_name(journal.path.name + ".tmp").write_text("부분 쓰기 쓰레기")
    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert loaded == snapshot


def test_leftover_temporary_file_is_replaced_not_appended(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    temporary = journal.path.with_name(journal.path.name + ".tmp")
    temporary.parent.mkdir(parents=True, exist_ok=True)
    temporary.write_text("x" * 5000)
    assert isinstance(journal.write(JournalSnapshot(entries=())), JournalWritten)
    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert loaded.entries == ()


def test_unwritable_directory_is_write_failed(tmp_path: Path) -> None:
    directory = tmp_path / "journal"
    directory.mkdir()
    directory.chmod(stat.S_IRUSR | stat.S_IXUSR)
    try:
        result = JobJournal(directory).write(JournalSnapshot(entries=()))
    finally:
        directory.chmod(stat.S_IRWXU)
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.WRITE_FAILED


# ---- 판독은 불변식을 다시 센다(저널을 손으로 고친 주체는 비방어이지만 조용히 받지 않는다) ----


def test_record_combination_violation_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(journal.write(_all_states_snapshot()), JournalWritten)
    decoded = json.loads(journal.path.read_bytes())
    for job in decoded["jobs"]:
        if job["state"] == "SUCCEEDED":
            job["artifact"] = None
    _write_raw(journal, json.dumps(decoded))
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.INVARIANT_VIOLATED


def test_naive_timestamp_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(
        journal.write(JournalSnapshot(entries=(_entry("job-1", JobState.ACCEPTED),))),
        JournalWritten,
    )
    decoded = json.loads(journal.path.read_bytes())
    decoded["jobs"][0]["accepted_at"] = "2026-01-01T00:00:00"
    _write_raw(journal, json.dumps(decoded))
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.INVARIANT_VIOLATED


def test_unknown_state_value_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(
        journal.write(JournalSnapshot(entries=(_entry("job-1", JobState.ACCEPTED),))),
        JournalWritten,
    )
    decoded = json.loads(journal.path.read_bytes())
    decoded["jobs"][0]["state"] = "ZOMBIE"
    _write_raw(journal, json.dumps(decoded))
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.MALFORMED


def test_duplicate_job_id_is_failed(tmp_path: Path) -> None:
    """저장소 색인이 `job_id` 유일성에 기대므로 판독이 그것을 센다."""
    journal = JobJournal(tmp_path / "journal")
    entry = _entry("job-dup", JobState.ACCEPTED, key="k1")
    other = JournalEntry(
        idempotency_key="k2", record=entry.record, submission=entry.submission
    )
    assert isinstance(
        journal.write(JournalSnapshot(entries=(entry, other))), JournalWritten
    )
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.INVARIANT_VIOLATED


def test_duplicate_idempotency_key_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    entries = (
        _entry("job-1", JobState.ACCEPTED, key="same"),
        _entry("job-2", JobState.ACCEPTED, key="same"),
    )
    assert isinstance(journal.write(JournalSnapshot(entries=entries)), JournalWritten)
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.INVARIANT_VIOLATED


def test_missing_required_field_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(
        journal.write(JournalSnapshot(entries=(_entry("job-1", JobState.ACCEPTED),))),
        JournalWritten,
    )
    decoded = json.loads(journal.path.read_bytes())
    del decoded["jobs"][0]["submission"]
    _write_raw(journal, json.dumps(decoded))
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.MALFORMED


def test_empty_submission_field_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(
        journal.write(JournalSnapshot(entries=(_entry("job-1", JobState.ACCEPTED),))),
        JournalWritten,
    )
    decoded = json.loads(journal.path.read_bytes())
    decoded["jobs"][0]["submission"]["dataset_uri"] = ""
    _write_raw(journal, json.dumps(decoded))
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.INVARIANT_VIOLATED
