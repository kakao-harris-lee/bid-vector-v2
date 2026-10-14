"""RED — `ml_engine.training.jobs.journal`(M6/6B-4 Q-4). 원자 교체(tmp → fsync →
`os.replace` → 디렉터리 fsync) · canonical JSON · 「없음」과 「깨짐」의 구분 · 쓰기 도중
크래시 주입. 위협 모델 ⑤ — 저널 쓰기 도중 크래시는 「이전 상태 또는 새 상태」만 남기고,
판독 불가는 조용한 빈 저장소가 아니라 닫힌 사유 결과 타입이 된다.
"""

from __future__ import annotations

import json
import logging
import os
import stat
from datetime import UTC, datetime
from pathlib import Path

import pytest

from ml_engine.training.jobs import journal_file
from ml_engine.training.jobs.journal import (
    JOURNAL_SCHEMA_VERSION,
    JobSubmission,
    JournalAbsent,
    JournalEntry,
    JournalFailed,
    JournalFailureReason,
    JournalSnapshot,
    JournalWritten,
)
from ml_engine.training.jobs.journal_file import JobJournal
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


def _file(journal: JobJournal) -> Path:
    """저널 파일 자리 — **production 표면에는 없다**(vr H-2: 경로 객체를 밖에 내주지 않아
    둘째 writer 를 조립할 수 없다). test 는 모듈 private 이름으로 같은 자리를 계산한다."""
    return journal._path


def _write_raw(journal: JobJournal, text: str) -> None:
    _file(journal).parent.mkdir(parents=True, exist_ok=True)
    _file(journal).write_text(text, encoding="utf-8")


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
    full = _file(journal).read_bytes()
    _file(journal).write_bytes(full[: len(full) // 2])
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
    _file(journal).mkdir(parents=True)
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
    payload = _file(first).read_bytes()
    assert payload == _file(second).read_bytes()
    assert b", " not in payload and b": " not in payload
    decoded = json.loads(payload)
    assert decoded["schema_version"] == JOURNAL_SCHEMA_VERSION
    assert list(decoded) == sorted(decoded)


# ---- 원자 교체와 크래시 주입(위협 모델 ⑤) ----


def test_successful_write_leaves_no_temporary_file(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(journal.write(_all_states_snapshot()), JournalWritten)
    assert sorted(path.name for path in _file(journal).parent.iterdir()) == [
        _file(journal).name
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
    _file(journal).with_name(_file(journal).name + ".tmp").write_text(
        "부분 쓰기 쓰레기"
    )
    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert loaded == snapshot


def test_leftover_temporary_file_is_replaced_not_appended(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    temporary = _file(journal).with_name(_file(journal).name + ".tmp")
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


def test_retryable_copy_mismatch_loses_to_the_derived_value(
    tmp_path: Path, caplog: pytest.LogCaptureFixture
) -> None:
    """cr M-2 — 저널의 `retryable` 은 `code` 의 **파생값 사본**이고 권위는 `code` 다. 사본이
    어긋나면 **파생값이 이기고 경고를 남긴다** — 기동 거부가 아니다.

    앞 판은 `INVARIANT_VIOLATED` 로 거부했고, 그러면 사상(`_RETRYABLE_BY_CODE`)을 한 줄
    고치는 순간 그 전에 쓰인 **모든 저널이 판독 불가**가 된다(`schema_version` 은 그대로라
    형식 불일치로도 보이지 않는다). 그때 운영자가 만나는 처방은 「파일을 치우고 다시 기동」
    = job 기록 전부 상실이다 — 되돌릴 수 있는 불일치(다시 계산하면 된다)를 되돌릴 수 없는
    자리로 보냈다. 판독 불변식은 조합·시각처럼 **권위 있는 데이터**에만 세운다."""
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(journal.write(_all_states_snapshot()), JournalWritten)
    decoded = json.loads(_file(journal).read_bytes())
    flipped = 0
    for job in decoded["jobs"]:
        if job["failure"] is not None:
            job["failure"]["retryable"] = not job["failure"]["retryable"]
            flipped += 1
    assert flipped == 1  # 통제군 — 실제로 뒤집었다
    _write_raw(journal, json.dumps(decoded))

    with caplog.at_level(logging.WARNING, logger="ml_engine.training.jobs.journal"):
        result = journal.read()

    assert isinstance(result, JournalSnapshot)
    failures = [
        entry.record.failure
        for entry in result.entries
        if entry.record.failure is not None
    ]
    assert len(failures) == 1
    stored = next(
        job["failure"]["retryable"]
        for job in decoded["jobs"]
        if job["failure"] is not None
    )
    # 파생값이 이긴다 — 디스크의 뒤집힌 사본이 아니라 `code` 의 사상이 기록에 실린다.
    assert failures[0].retryable is not stored
    assert failures[0].retryable is (
        failures[0].code is JobFailureCode.WORKER_RESOURCE_EXHAUSTED
    )
    assert any("retryable" in record.getMessage() for record in caplog.records)


def test_old_format_journal_with_stale_retryable_still_boots(tmp_path: Path) -> None:
    """같은 축의 조립 근 쪽 관측 — 구판 사본을 든 저널로도 **기동한다**(기록을 잃지 않는다).
    `load_job_store` 는 `ConfigError` 의 원인인 `JournalFailed` 를 돌려주지 않는다."""
    journal = JobJournal(tmp_path / "journal")
    legacy = json.dumps(
        {
            "schema_version": JOURNAL_SCHEMA_VERSION,
            "jobs": [
                {
                    "job_id": "job-legacy",
                    "state": "FAILED",
                    "dataset_id": "ds-1",
                    "accepted_at": _NOW.isoformat(),
                    "started_at": None,
                    "finished_at": _LATER.isoformat(),
                    "artifact": None,
                    "evaluation": None,
                    "failure": {
                        "code": "TRAINING_ERROR",
                        # 구판이 적던 값 — 지금 사상은 거짓이다.
                        "retryable": True,
                        "detail_code": "RUNNER_LOST_ON_RESTART",
                    },
                    "idempotency_key": "key-legacy",
                    "submission": {
                        "dataset_uri": "file:///tmp/ds",
                        "manifest_checksum": "a" * 64,
                        "training_spec_version": "v1",
                    },
                }
            ],
        }
    )
    _write_raw(journal, legacy)
    snapshot = journal.read()
    assert isinstance(snapshot, JournalSnapshot)
    assert snapshot.entries[0].record.failure is not None
    assert snapshot.entries[0].record.failure.retryable is False


def test_record_combination_violation_is_failed(tmp_path: Path) -> None:
    journal = JobJournal(tmp_path / "journal")
    assert isinstance(journal.write(_all_states_snapshot()), JournalWritten)
    decoded = json.loads(_file(journal).read_bytes())
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
    decoded = json.loads(_file(journal).read_bytes())
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
    decoded = json.loads(_file(journal).read_bytes())
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
    decoded = json.loads(_file(journal).read_bytes())
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
    decoded = json.loads(_file(journal).read_bytes())
    decoded["jobs"][0]["submission"]["dataset_uri"] = ""
    _write_raw(journal, json.dumps(decoded))
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.INVARIANT_VIOLATED


# ---- cr M-2 — 자기가 못 읽는 바이트를 쓰지 않는다 ----


def _naive_snapshot() -> JournalSnapshot:
    """`accepted_at` 에 시간대가 없는 스냅숏 — 모듈 docstring 이 「tz 가 없으면 받지
    않는다」고 선언하지만 앞 판의 검사는 **판독 쪽에만** 있었다."""
    return JournalSnapshot(
        entries=(
            JournalEntry(
                idempotency_key="key-naive",
                record=JobRecord(
                    job_id="job-naive",
                    state=JobState.ACCEPTED,
                    dataset_id="ds-1",
                    accepted_at=datetime(2026, 1, 1),  # 의도된 naive
                ),
                submission=_submission(),
            ),
        )
    )


def test_timezone_naive_timestamp_is_refused_at_write(tmp_path: Path) -> None:
    """되돌릴 수 있는 자리에서 막는다 — 쓰기는 결과 타입(`JournalFailed`)이고 요청은
    거부되지만, 쓰고 나면 다음 기동의 `ConfigError` + 「파일을 치우고 다시 기동」 =
    job 기록 전부 상실이다(위협 모델 ⑤ 를 정상 쓰기 경로가 깨던 자리)."""
    journal = JobJournal(tmp_path / "journal")
    result = journal.write(_naive_snapshot())
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.INVARIANT_VIOLATED
    assert "시간대" in result.detail
    # 파일 자체가 생기지 않는다 — tmp 교체 전에 거부한다.
    assert not _file(journal).exists()


def test_naive_timestamp_bytes_would_not_be_readable(tmp_path: Path) -> None:
    """음성 대조 — 앞 판이 쓰던 바이트(`value.isoformat()` 그대로)는 **판독 불가**다.
    쓰기 거부가 지키는 것이 무엇인지 보인다."""
    journal = JobJournal(tmp_path / "journal")
    legacy_bytes = json.dumps(
        {
            "schema_version": JOURNAL_SCHEMA_VERSION,
            "jobs": [
                {
                    "job_id": "job-naive",
                    "state": "ACCEPTED",
                    "dataset_id": "ds-1",
                    "accepted_at": "2026-01-01T00:00:00",
                    "started_at": None,
                    "finished_at": None,
                    "artifact": None,
                    "evaluation": None,
                    "failure": None,
                    "idempotency_key": "key-naive",
                    "submission": {
                        "dataset_uri": "file:///tmp/ds",
                        "manifest_checksum": "a" * 64,
                        "training_spec_version": "v1",
                    },
                }
            ],
        }
    )
    _write_raw(journal, legacy_bytes)
    result = journal.read()
    assert isinstance(result, JournalFailed)
    assert result.reason is JournalFailureReason.INVARIANT_VIOLATED


# ---- vr M-1 — 교체 **뒤**의 실패는 「쓰지 않았다」가 아니다 ----


def test_directory_fsync_failure_is_written_but_not_durable(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """`os.replace` 가 끝난 뒤 디렉터리 `fsync` 가 실패하면 **교체는 이미 일어났다.**
    그것을 `WRITE_FAILED` 로 답하면 호출자가 「쓰지 않았다」로 읽어 메모리를 되돌리고,
    디스크=새 상태 · 메모리=옛 상태로 갈라진다(vr M-1 실측: 거절했다고 답한 job 이
    재기동 뒤 실행된다). 「교체됨, 내구성 미확인」을 다른 값으로 돌려준다."""
    journal = JobJournal(tmp_path / "journal")

    def _boom(directory: Path) -> None:
        raise OSError(5, "EIO 주입")

    monkeypatch.setattr(journal_file, "_fsync_directory", _boom)
    result = journal.write(_all_states_snapshot())
    monkeypatch.undo()

    assert isinstance(result, JournalWritten)
    assert result.durable is False
    # 교체가 일어났다는 실측 — 파일이 새 내용을 들고 있다.
    loaded = journal.read()
    assert isinstance(loaded, JournalSnapshot)
    assert len(loaded.entries) == len(_all_states_snapshot().entries)


def test_successful_write_is_durable(tmp_path: Path) -> None:
    """통제군 — 정상 경로는 `durable=True` 다(위 test 가 상수를 재는 것이 아님)."""
    result = JobJournal(tmp_path / "journal").write(JournalSnapshot(entries=()))
    assert isinstance(result, JournalWritten)
    assert result.durable is True
