"""RED — `ml_engine.training.jobs.state`(scope.md ④, 설계 검토 (1) 전이표·조합 불변식).
전이표 규칙표 + `JobRecord` 아홉 조합 규칙표 + 시각 불변식."""

from __future__ import annotations

from datetime import UTC, datetime

import pytest

from ml_engine.training.jobs.state import (
    ArtifactReference,
    EvaluationReportReference,
    JobEvent,
    JobFailure,
    JobFailureCode,
    JobRecord,
    JobState,
    TransitionRejected,
    transition,
)

_NOW = datetime(2026, 1, 1, tzinfo=UTC)
_LATER = datetime(2026, 1, 1, 1, tzinfo=UTC)


def _record(state: JobState, **overrides: object) -> JobRecord:
    base: dict[str, object] = dict(
        job_id="job-1", state=state, dataset_id="ds-1", accepted_at=_NOW
    )
    base.update(overrides)
    return JobRecord(**base)  # type: ignore[arg-type]


def _valid_record(state: JobState, **overrides: object) -> JobRecord:
    """전이표 test 용 — `state`가 요구하는 조합 불변식을 자동으로 채운 유효 레코드
    (조합 불변식 자체를 검사하는 test 는 `_record`를 직접 쓴다)."""
    filled: dict[str, object] = {}
    if state is JobState.SUCCEEDED:
        filled = {"artifact": _artifact(), "evaluation": _evaluation()}
    elif state is JobState.FAILED:
        filled = {"failure": _failure()}
    filled.update(overrides)
    return _record(state, **filled)


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


def _failure() -> JobFailure:
    return JobFailure(
        code=JobFailureCode.TRAINING_ERROR, retryable=False, detail_code="X"
    )


# ---- 전이표(scope.md ④) ----


@pytest.mark.parametrize(
    ("from_state", "event", "to_state"),
    [
        (JobState.ACCEPTED, JobEvent.START, JobState.RUNNING),
        (JobState.RUNNING, JobEvent.SUCCEED, JobState.SUCCEEDED),
        (JobState.RUNNING, JobEvent.FAIL, JobState.FAILED),
        (JobState.ACCEPTED, JobEvent.CANCEL, JobState.CANCELLED),
        (JobState.RUNNING, JobEvent.CANCEL, JobState.CANCELLED),
    ],
)
def test_allowed_transitions(
    from_state: JobState, event: JobEvent, to_state: JobState
) -> None:
    record = _valid_record(
        from_state, started_at=_NOW if from_state is JobState.RUNNING else None
    )
    kwargs: dict[str, object] = {}
    if event is JobEvent.SUCCEED:
        kwargs = {"artifact": _artifact(), "evaluation": _evaluation()}
    if event is JobEvent.FAIL:
        kwargs = {"failure": _failure()}
    result = transition(record, event, at=_LATER, **kwargs)
    assert isinstance(result, JobRecord)
    assert result.state is to_state


@pytest.mark.parametrize(
    ("from_state", "event"),
    [
        (JobState.RUNNING, JobEvent.START),
        (JobState.ACCEPTED, JobEvent.SUCCEED),
        (JobState.ACCEPTED, JobEvent.FAIL),
        (JobState.SUCCEEDED, JobEvent.START),
        (JobState.FAILED, JobEvent.START),
    ],
)
def test_disallowed_transitions_are_rejected(
    from_state: JobState, event: JobEvent
) -> None:
    result = transition(_valid_record(from_state), event, at=_LATER)
    assert isinstance(result, TransitionRejected)


@pytest.mark.parametrize(
    "state", [JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED]
)
def test_cancel_in_terminal_state_is_idempotent_no_op(state: JobState) -> None:
    """종료 상태 Cancel 은 멱등 no-op — 같은 레코드를 그대로 돌려준다(새 시각 없음)."""
    kwargs: dict[str, object] = {}
    if state is JobState.SUCCEEDED:
        kwargs = {"artifact": _artifact(), "evaluation": _evaluation()}
    if state is JobState.FAILED:
        kwargs = {"failure": _failure()}
    record = _valid_record(state, finished_at=_NOW, **kwargs)
    result = transition(record, JobEvent.CANCEL, at=_LATER)
    assert result is record


# ---- 조합 불변식(2C TrainingJob 주석, 우회 후보 (3)(5)(6)) ----


def test_succeeded_requires_artifact_and_evaluation() -> None:
    with pytest.raises(ValueError, match="SUCCEEDED"):
        _record(JobState.SUCCEEDED, artifact=_artifact())
    with pytest.raises(ValueError, match="SUCCEEDED"):
        _record(JobState.SUCCEEDED, evaluation=_evaluation())


def test_succeeded_forbids_failure() -> None:
    with pytest.raises(ValueError, match="SUCCEEDED"):
        _record(
            JobState.SUCCEEDED,
            artifact=_artifact(),
            evaluation=_evaluation(),
            failure=_failure(),
        )


def test_failed_requires_failure() -> None:
    with pytest.raises(ValueError, match="FAILED"):
        _record(JobState.FAILED)


def test_failed_forbids_artifact_and_evaluation() -> None:
    with pytest.raises(ValueError, match="FAILED"):
        _record(JobState.FAILED, failure=_failure(), artifact=_artifact())


@pytest.mark.parametrize(
    "state", [JobState.ACCEPTED, JobState.RUNNING, JobState.CANCELLED]
)
def test_non_terminal_states_forbid_all_three(state: JobState) -> None:
    with pytest.raises(ValueError):
        _record(state, artifact=_artifact())


def test_cancelled_with_artifact_attached_is_impossible() -> None:
    """우회 후보 (3) — 타입으로 닫는다."""
    with pytest.raises(ValueError):
        _record(JobState.CANCELLED, artifact=_artifact(), evaluation=_evaluation())


# ---- 시각 불변식(D-2C-5) ----


def test_started_at_before_accepted_at_is_rejected() -> None:
    with pytest.raises(ValueError, match="started_at"):
        _record(JobState.RUNNING, started_at=_NOW.replace(year=2025))


def test_finished_at_before_started_at_is_rejected() -> None:
    with pytest.raises(ValueError, match="finished_at"):
        _record(
            JobState.FAILED,
            started_at=_LATER,
            finished_at=_NOW,
            failure=_failure(),
        )


def test_valid_timestamp_order_succeeds() -> None:
    record = _record(
        JobState.SUCCEEDED,
        started_at=_NOW,
        finished_at=_LATER,
        artifact=_artifact(),
        evaluation=_evaluation(),
    )
    assert record.finished_at == _LATER


# ---- job_id/dataset_id 비어 있음 거부 ----


def test_empty_job_id_is_rejected() -> None:
    with pytest.raises(ValueError, match="job_id"):
        _record(JobState.ACCEPTED, job_id="")


def test_empty_dataset_id_is_rejected() -> None:
    with pytest.raises(ValueError, match="dataset_id"):
        _record(JobState.ACCEPTED, dataset_id="")


def test_detail_code_empty_is_rejected() -> None:
    with pytest.raises(ValueError, match="detail_code"):
        JobFailure(code=JobFailureCode.TRAINING_ERROR, retryable=False, detail_code="")
