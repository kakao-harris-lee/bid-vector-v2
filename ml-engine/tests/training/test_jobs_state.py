"""RED — `ml_engine.training.jobs.state`(scope.md ④, 설계 검토 (1) 전이표·조합 불변식).
전이표 규칙표 + `JobRecord` 아홉 조합 규칙표 + 시각 불변식."""

from __future__ import annotations

from datetime import UTC, datetime

import pytest

from ml_engine.training.jobs.pipeline import DetailCode
from ml_engine.training.jobs.state import (
    _PENDING_FAILURE_REASONS,
    ArtifactReference,
    EvaluationReportReference,
    JobEvent,
    JobFailure,
    JobFailureCode,
    JobRecord,
    JobState,
    TransitionRejected,
    TransitionRejectionReason,
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
    return JobFailure(code=JobFailureCode.TRAINING_ERROR, detail_code="X")


def _pending_failure() -> JobFailure:
    """대기 job 을 끝내는 **등재된** 사유 하나(cr M-3)."""
    return JobFailure(
        code=JobFailureCode.WORKER_RESOURCE_EXHAUSTED,
        detail_code=DetailCode.PENDING_QUEUE_OVERFLOW.value,
    )


# ---- 전이표(scope.md ④) ----


@pytest.mark.parametrize(
    ("from_state", "event", "to_state"),
    [
        (JobState.ACCEPTED, JobEvent.START, JobState.RUNNING),
        (JobState.RUNNING, JobEvent.SUCCEED, JobState.SUCCEEDED),
        (JobState.RUNNING, JobEvent.FAIL, JobState.FAILED),
        # M6/6B-4 D-6B4-1 — 대기 큐 상한 초과 폐기(ADR 0010 D-8 개정).
        (JobState.ACCEPTED, JobEvent.FAIL, JobState.FAILED),
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
        # 대기(ACCEPTED) 출발의 FAIL 은 **등재된 사유**여야 한다(cr M-3) — 그 자리의 사유
        # 집합이 계약 문면의 「정확히 셋」이다.
        kwargs = {
            "failure": _pending_failure()
            if from_state is JobState.ACCEPTED
            else _failure()
        }
    result = transition(record, event, at=_LATER, **kwargs)
    assert isinstance(result, JobRecord)
    assert result.state is to_state


@pytest.mark.parametrize(
    ("from_state", "event"),
    [
        (JobState.RUNNING, JobEvent.START),
        (JobState.ACCEPTED, JobEvent.SUCCEED),
        (JobState.SUCCEEDED, JobEvent.START),
        (JobState.FAILED, JobEvent.START),
    ],
)
def test_disallowed_transitions_are_rejected(
    from_state: JobState, event: JobEvent
) -> None:
    result = transition(_valid_record(from_state), event, at=_LATER)
    assert isinstance(result, TransitionRejected)


# ---- M6/6B-4 — 대기 폐기 간선과 그 사유 제약(설계 검토 (2) 우회 3) ----


def test_pending_discard_sets_worker_resource_exhausted_without_started_at() -> None:
    """대기(ACCEPTED) job 은 실행된 적이 없다 — 폐기 뒤에도 `started_at` 이 없고
    `finished_at` 만 선다(「실행했다」고 말하지 않는다)."""
    record = _record(JobState.ACCEPTED)
    result = transition(
        record,
        JobEvent.FAIL,
        at=_LATER,
        failure=JobFailure(
            code=JobFailureCode.WORKER_RESOURCE_EXHAUSTED,
            detail_code="PENDING_QUEUE_OVERFLOW",
        ),
    )
    assert isinstance(result, JobRecord)
    assert result.state is JobState.FAILED
    assert result.started_at is None
    assert result.finished_at == _LATER
    assert result.failure is not None
    assert result.failure.code is JobFailureCode.WORKER_RESOURCE_EXHAUSTED


def test_worker_resource_exhausted_from_running_is_rejected_with_reason() -> None:
    """설계 검토 우회 (3) — 폐기 사유는 **대기에서만** 쓸 수 있다. 워커가 이미 집은
    job(RUNNING)을 「대기 큐가 넘쳐서」 끝내는 전이는 표에 (RUNNING, FAIL) 이 있어도
    거부된다 — 그 사유로는 실행 중 job 을 끝낼 수 없다."""
    record = _record(JobState.RUNNING, started_at=_NOW)
    result = transition(
        record,
        JobEvent.FAIL,
        at=_LATER,
        failure=JobFailure(
            code=JobFailureCode.WORKER_RESOURCE_EXHAUSTED,
            detail_code="PENDING_QUEUE_OVERFLOW",
        ),
    )
    assert isinstance(result, TransitionRejected)
    assert result.reason is TransitionRejectionReason.RESOURCE_EXHAUSTED_NOT_PENDING


def test_running_fail_with_other_code_is_still_allowed() -> None:
    """같은 (RUNNING, FAIL) 쌍이 다른 사유로는 그대로 허용된다 — 위 거부가 간선 자체를
    막지 않음을 통제군으로 확인한다."""
    record = _record(JobState.RUNNING, started_at=_NOW)
    result = transition(record, JobEvent.FAIL, at=_LATER, failure=_failure())
    assert isinstance(result, JobRecord)
    assert result.state is JobState.FAILED


def test_table_miss_reports_not_in_table_reason() -> None:
    result = transition(_valid_record(JobState.RUNNING), JobEvent.START, at=_LATER)
    assert isinstance(result, TransitionRejected)
    assert result.reason is TransitionRejectionReason.NOT_IN_TABLE


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
        JobFailure(code=JobFailureCode.TRAINING_ERROR, detail_code="")


# ---- cr M-5 — `retryable` 은 코드의 함수다(자리마다 고르는 값이 아니다) ----


def test_retryable_is_a_function_of_the_failure_code() -> None:
    """앞 판은 같은 `TRAINING_ERROR` 가 자리에 따라 양쪽 값을 들었다(저장소의 재기동
    처분 `True` · 실행기의 파이프라인 실패 `False`). `training.proto` 는 job failure 의
    `retryable` 을 「계약이 정하는 값 … application failure 의 `retryable` 과 대칭」이라
    하고 ADR 0010 은 application 층에서 **코드별 사상**(`MODEL_NOT_READY` 만 참)을 쓴다 —
    그 대칭대로 여기서도 코드 하나가 값 하나를 든다.

    사상: 용량 고갈(`WORKER_RESOURCE_EXHAUSTED`)만 참이다. 나머지는 같은 입력을 다시
    넣어도 같은 자리에서 실패하거나(dataset·checksum·학습·평가) 재시도의 대상이
    아니다(요청에 의한 취소)."""
    expected = {
        JobFailureCode.DATASET_UNREADABLE: False,
        JobFailureCode.DATASET_CHECKSUM_MISMATCH: False,
        JobFailureCode.TRAINING_ERROR: False,
        JobFailureCode.EVALUATION_ERROR: False,
        JobFailureCode.CANCELLED_BY_REQUEST: False,
        JobFailureCode.WORKER_RESOURCE_EXHAUSTED: True,
    }
    assert set(expected) == set(JobFailureCode)  # 전수 — 코드가 늘면 여기가 붉어진다
    for code, retryable in expected.items():
        assert JobFailure(code=code, detail_code="X").retryable is retryable


def test_retryable_cannot_be_chosen_by_the_caller() -> None:
    """사상이 **한 자리**라는 것의 구조적 근거 — 값을 넣을 인자가 없다(자리를 열거한
    목록이 아니라 생성자 서명이 막는다)."""
    with pytest.raises(TypeError):
        JobFailure(  # type: ignore[call-arg]
            code=JobFailureCode.TRAINING_ERROR, retryable=True, detail_code="X"
        )


# ---- cr M-3 — `ACCEPTED → FAILED` 의 사유는 **정확히 셋**(닫는 술어) ----


def test_pending_fail_reasons_are_exactly_three_and_closed_vocabulary() -> None:
    """계약 문면(proto `JobState` 주석 · ADR 0010 D-8 개정 · `state.py` 주석)이 이 간선의
    쓰임을 「정확히 셋」이라 선언한다. 앞 판은 셋 각각을 **양성**으로만 확인했고 넷째가
    생기는 것을 막는 술어가 없었다 — 교차 등식은 간선 집합만 보고 사유는 보지 않는다."""
    assert len(_PENDING_FAILURE_REASONS) == 3
    for code, detail_code in _PENDING_FAILURE_REASONS:
        assert code in set(JobFailureCode)
        assert detail_code in {member.value for member in DetailCode}


@pytest.mark.parametrize(
    ("code", "detail_code"),
    [
        # RUNNING 고아 처분 사유 — 그 간선은 `RUNNING → FAILED` 이고 대기 출발이 아니다.
        (JobFailureCode.TRAINING_ERROR, DetailCode.RUNNER_LOST_ON_RESTART.value),
        # 실행 중에만 생길 수 있는 사유들
        (JobFailureCode.DATASET_UNREADABLE, DetailCode.NOT_FOUND.value),
        (JobFailureCode.EVALUATION_ERROR, DetailCode.ACCOUNTING_MISMATCH.value),
        (JobFailureCode.TRAINING_ERROR, DetailCode.TRAINER_ERROR.value),
    ],
)
def test_pending_fail_with_an_unlisted_reason_is_rejected(
    code: JobFailureCode, detail_code: str
) -> None:
    """대기 job 은 **실행된 적이 없다** — 실행 중에만 생길 수 있는 사유로 끝낼 수 없다."""
    result = transition(
        _valid_record(JobState.ACCEPTED),
        JobEvent.FAIL,
        at=_LATER,
        failure=JobFailure(code=code, detail_code=detail_code),
    )
    assert isinstance(result, TransitionRejected)
    assert result.reason is TransitionRejectionReason.PENDING_FAILURE_REASON_UNLISTED


@pytest.mark.parametrize("reason", sorted(_PENDING_FAILURE_REASONS))
def test_each_listed_pending_reason_is_accepted(
    reason: tuple[JobFailureCode, str],
) -> None:
    """통제군 — 위 거부가 간선 자체를 막지 않는다(등재된 셋은 전부 지나간다)."""
    code, detail_code = reason
    result = transition(
        _valid_record(JobState.ACCEPTED),
        JobEvent.FAIL,
        at=_LATER,
        failure=JobFailure(code=code, detail_code=detail_code),
    )
    assert isinstance(result, JobRecord)
    assert result.state is JobState.FAILED
    assert result.started_at is None  # 실행된 적이 없다


def test_running_fail_is_not_restricted_to_the_pending_reason_set() -> None:
    """통제군 — 사유 제약은 **대기 출발**에만 걸린다. 실행 중 실패는 그 집합 밖 사유로도
    끝난다(파이프라인 실패 어휘 전체가 그 자리의 것이다)."""
    result = transition(
        _record(JobState.RUNNING, started_at=_NOW),
        JobEvent.FAIL,
        at=_LATER,
        failure=JobFailure(
            code=JobFailureCode.TRAINING_ERROR,
            detail_code=DetailCode.TRAINER_ERROR.value,
        ),
    )
    assert isinstance(result, JobRecord)
    assert result.state is JobState.FAILED
