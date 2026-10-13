"""`ml_engine.training.jobs.journal` — job 저장소 저널의 **값 타입과 canonical JSON
코덱**(M6/6B-4 Q-4). 이 모듈은 파일을 모른다 — 파일 하나의 소유자는 `journal_file`
(`JobJournal`)이고, 그 선이 「저널 파일에 쓰는 자리는 한 모듈뿐」의 구조적 근거다(vr H-2).

저장소 상태 **전체**를 한 덩이로 적는다(추가 기록이 아니라 전체 교체 — 부분 기록이 남을
창 자체를 없앤다. 이 서버의 job 수는 `job_workers: 1` 과 대기 상한 아래에서 작다. 종단
job 의 보존·정리는 이 slice 밖, `OPEN-6B4-JOURNAL-RETENTION`).

**판독 실패는 조용한 빈 저장소가 아니다.** 「파일이 없다」(`JournalAbsent`, 첫 기동)와
「읽었지만 뜻을 모른다」(`JournalFailed` + 닫힌 사유)를 다른 타입으로 돌려준다 — 호출자
(`store.load_job_store` → 조립 근)가 후자에서 기동을 거부한다.

canonical JSON 관례는 저장소의 기존 자리들과 같다(`training.artifact_writer`·
`evaluation.report`·`serving.runtime`): `sort_keys=True`, `separators=(",", ":")`,
`allow_nan=False`. 시각은 ISO 8601 문자열이고 **tz 가 없으면 받지 않는다** — 쓰기에서도
거부한다(cr M-2: 앞 판은 판독 쪽에만 검사가 있어 못 읽는 바이트를 남겼다).

판독은 `JobRecord`·`JobFailure`·`JobSubmission` 생성자를 그대로 지나므로 2C 조합 불변식과
시각 불변식이 **판독에서도** 한 번 더 선다(저널을 손으로 고치는 주체는 비방어이지만,
그 결과를 조용히 받지는 않는다).
"""

from __future__ import annotations

import json
from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from typing import Final

from ml_engine.training.jobs.state import (
    ArtifactReference,
    EvaluationReportReference,
    JobFailure,
    JobFailureCode,
    JobRecord,
    JobState,
)

JOURNAL_SCHEMA_VERSION: Final[str] = "job-journal-v1"

type _JsonValue = (
    str | int | float | bool | list[_JsonValue] | dict[str, _JsonValue] | None
)


@dataclass(frozen=True)
class JobSubmission:
    """재기동 뒤 **대기 job 을 실제로 실행하려면** 필요한 제출 입력(2C
    `StartTrainingRequest` 중 job 기록에 남지 않는 성분). `dataset_id` 는 `JobRecord`
    가 이미 들고 있어 여기 중복하지 않는다."""

    dataset_uri: str
    manifest_checksum: str
    training_spec_version: str

    def __post_init__(self) -> None:
        for field_name in ("dataset_uri", "manifest_checksum", "training_spec_version"):
            if not getattr(self, field_name):
                raise ValueError(f"{field_name} 는 비어 있을 수 없습니다.")


@dataclass(frozen=True)
class JournalEntry:
    """저널 한 항목 — job 기록 + 멱등 키 + 제출 입력."""

    idempotency_key: str
    record: JobRecord
    submission: JobSubmission

    def __post_init__(self) -> None:
        if not self.idempotency_key:
            raise ValueError("idempotency_key 는 비어 있을 수 없습니다.")


@dataclass(frozen=True)
class JournalSnapshot:
    """저장소 상태 전체."""

    entries: tuple[JournalEntry, ...]


@dataclass(frozen=True)
class JournalAbsent:
    """저널 파일이 아직 없다 — 첫 기동. 「깨짐」과 **다른 타입**이다."""


class JournalFailureReason(StrEnum):
    """닫힌 사유 어휘(`adapters.dataset_files`·`adapters.snapshot_files` 와 같은 모양)."""

    NOT_A_FILE = "NOT_A_FILE"
    READ_FAILED = "READ_FAILED"
    MALFORMED = "MALFORMED"
    SCHEMA_UNSUPPORTED = "SCHEMA_UNSUPPORTED"
    INVARIANT_VIOLATED = "INVARIANT_VIOLATED"
    WRITE_FAILED = "WRITE_FAILED"


@dataclass(frozen=True)
class JournalFailed:
    reason: JournalFailureReason
    detail: str


@dataclass(frozen=True)
class JournalWritten:
    """교체가 **일어났다**. `durable` 은 그 교체 자체가 디스크에 남은 것까지 확인했는가
    (디렉터리 `fsync` 성공, vr M-1). `durable=False` 를 `JournalFailed` 로 뭉치면 호출자가
    「쓰지 않았다」로 읽어 메모리를 되돌리고 **디스크=새 상태 · 메모리=옛 상태**로 갈라진다
    — 거절했다고 답한 job 이 재기동 뒤 실행된다. 교체 뒤의 실패는 「받지 않았다」가 아니다."""

    byte_count: int
    durable: bool


class _RejectedError(Exception):
    """판독·기록 실패를 나르는 **모듈 내부** 예외 — `read`/`decode_snapshot`/
    `encode_snapshot` 경계에서 결과 타입으로 바뀌어 밖으로는 예외가 새지 않는다."""

    def __init__(self, reason: JournalFailureReason, detail: str) -> None:
        super().__init__(detail)
        self.reason = reason
        self.detail = detail


# ---- 인코딩 ----


def _encode_timestamp(value: datetime | None) -> str | None:
    """cr M-2 — tz 없는 시각은 **적지 않는다**. 앞 판의 검사는 판독 쪽에만 있어서 정상
    쓰기 경로가 자기가 못 읽는 바이트를 남겼다(위협 모델 ⑤). 되돌릴 수 있는 자리(쓰기 =
    결과 타입)에서 막고 되돌릴 수 없는 자리(다음 기동 거부 → 기록 전부 상실)로 미루지
    않는다."""
    if value is None:
        return None
    if value.tzinfo is None:
        raise _RejectedError(
            JournalFailureReason.INVARIANT_VIOLATED,
            f"시각에 시간대가 없다 — 저널에 적지 않는다: {value.isoformat()}",
        )
    return value.isoformat()


def _encode_artifact(artifact: ArtifactReference | None) -> _JsonValue:
    if artifact is None:
        return None
    return {
        "uri": artifact.uri,
        "release_id": artifact.release_id,
        "artifact_checksum": artifact.artifact_checksum,
        "feature_schema_version": artifact.feature_schema_version,
        "code_version": artifact.code_version,
        "dataset_id": artifact.dataset_id,
        "manifest_schema_version": artifact.manifest_schema_version,
    }


def _encode_evaluation(evaluation: EvaluationReportReference | None) -> _JsonValue:
    if evaluation is None:
        return None
    return {
        "uri": evaluation.uri,
        "checksum": evaluation.checksum,
        "report_schema_version": evaluation.report_schema_version,
    }


def _encode_failure(failure: JobFailure | None) -> _JsonValue:
    if failure is None:
        return None
    return {
        "code": failure.code.value,
        "retryable": failure.retryable,
        "detail_code": failure.detail_code,
    }


def _encode_entry(entry: JournalEntry) -> _JsonValue:
    record = entry.record
    return {
        "job_id": record.job_id,
        "state": record.state.value,
        "dataset_id": record.dataset_id,
        "accepted_at": _encode_timestamp(record.accepted_at),
        "started_at": _encode_timestamp(record.started_at),
        "finished_at": _encode_timestamp(record.finished_at),
        "artifact": _encode_artifact(record.artifact),
        "evaluation": _encode_evaluation(record.evaluation),
        "failure": _encode_failure(record.failure),
        "idempotency_key": entry.idempotency_key,
        "submission": {
            "dataset_uri": entry.submission.dataset_uri,
            "manifest_checksum": entry.submission.manifest_checksum,
            "training_spec_version": entry.submission.training_spec_version,
        },
    }


def encode_snapshot(snapshot: JournalSnapshot) -> bytes | JournalFailed:
    """canonical JSON 바이트 — 같은 스냅숏은 항상 같은 바이트. **판독할 수 없는 바이트를
    만들지 않는다**(cr M-2): 불변식 위반은 결과 타입이다(`read` 쪽과 같은 모양)."""
    try:
        payload: _JsonValue = {
            "schema_version": JOURNAL_SCHEMA_VERSION,
            "jobs": [_encode_entry(entry) for entry in snapshot.entries],
        }
    except _RejectedError as rejected:
        return JournalFailed(rejected.reason, rejected.detail)
    return json.dumps(
        payload, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


# ---- 디코딩 ----


def _mapping(raw: _JsonValue, where: str) -> dict[str, _JsonValue]:
    if not isinstance(raw, dict):
        raise _RejectedError(
            JournalFailureReason.MALFORMED, f"{where} 가 객체가 아니다"
        )
    return raw


def _text(fields: dict[str, _JsonValue], key: str, where: str) -> str:
    value = fields.get(key)
    if not isinstance(value, str):
        raise _RejectedError(
            JournalFailureReason.MALFORMED, f"{where}.{key} 가 문자열이 아니다"
        )
    return value


def _flag(fields: dict[str, _JsonValue], key: str, where: str) -> bool:
    value = fields.get(key)
    if not isinstance(value, bool):
        raise _RejectedError(
            JournalFailureReason.MALFORMED, f"{where}.{key} 가 참/거짓이 아니다"
        )
    return value


def _timestamp(fields: dict[str, _JsonValue], key: str, where: str) -> datetime:
    text = _text(fields, key, where)
    try:
        parsed = datetime.fromisoformat(text)
    except ValueError as exc:
        raise _RejectedError(
            JournalFailureReason.MALFORMED, f"{where}.{key} 를 시각으로 읽지 못했다"
        ) from exc
    if parsed.tzinfo is None:
        raise _RejectedError(
            JournalFailureReason.INVARIANT_VIOLATED,
            f"{where}.{key} 에 시간대가 없다",
        )
    return parsed


def _optional_timestamp(
    fields: dict[str, _JsonValue], key: str, where: str
) -> datetime | None:
    if key not in fields:
        raise _RejectedError(JournalFailureReason.MALFORMED, f"{where}.{key} 가 없다")
    return None if fields[key] is None else _timestamp(fields, key, where)


def _nested(
    fields: dict[str, _JsonValue], key: str, where: str
) -> dict[str, _JsonValue] | None:
    if key not in fields:
        raise _RejectedError(JournalFailureReason.MALFORMED, f"{where}.{key} 가 없다")
    value = fields[key]
    return None if value is None else _mapping(value, f"{where}.{key}")


def _decode_state(fields: dict[str, _JsonValue], where: str) -> JobState:
    value = _text(fields, "state", where)
    try:
        return JobState(value)
    except ValueError as exc:
        raise _RejectedError(
            JournalFailureReason.MALFORMED, f"{where}.state 가 닫힌 어휘 밖이다"
        ) from exc


def _decode_artifact(
    fields: dict[str, _JsonValue], where: str
) -> ArtifactReference | None:
    nested = _nested(fields, "artifact", where)
    if nested is None:
        return None
    place = f"{where}.artifact"
    return ArtifactReference(
        uri=_text(nested, "uri", place),
        release_id=_text(nested, "release_id", place),
        artifact_checksum=_text(nested, "artifact_checksum", place),
        feature_schema_version=_text(nested, "feature_schema_version", place),
        code_version=_text(nested, "code_version", place),
        dataset_id=_text(nested, "dataset_id", place),
        manifest_schema_version=_text(nested, "manifest_schema_version", place),
    )


def _decode_evaluation(
    fields: dict[str, _JsonValue], where: str
) -> EvaluationReportReference | None:
    nested = _nested(fields, "evaluation", where)
    if nested is None:
        return None
    place = f"{where}.evaluation"
    return EvaluationReportReference(
        uri=_text(nested, "uri", place),
        checksum=_text(nested, "checksum", place),
        report_schema_version=_text(nested, "report_schema_version", place),
    )


def _decode_failure(fields: dict[str, _JsonValue], where: str) -> JobFailure | None:
    nested = _nested(fields, "failure", where)
    if nested is None:
        return None
    place = f"{where}.failure"
    code_value = _text(nested, "code", place)
    try:
        code = JobFailureCode(code_value)
    except ValueError as exc:
        raise _RejectedError(
            JournalFailureReason.MALFORMED, f"{place}.code 가 닫힌 어휘 밖이다"
        ) from exc
    failure = JobFailure(code=code, detail_code=_text(nested, "detail_code", place))
    # `retryable` 은 `code` 의 함수이므로(cr M-5) 저널의 값은 **파생값의 사본**이다. 형식은
    # 그대로 두고(바이트 호환) 사본이 사상과 어긋나면 받지 않는다 — 다른 불변식과 같은 규율.
    if _flag(nested, "retryable", place) is not failure.retryable:
        raise _RejectedError(
            JournalFailureReason.INVARIANT_VIOLATED,
            f"{place}.retryable 이 code 의 사상과 어긋난다",
        )
    return failure


def _decode_submission(fields: dict[str, _JsonValue], where: str) -> JobSubmission:
    nested = _nested(fields, "submission", where)
    if nested is None:
        raise _RejectedError(
            JournalFailureReason.MALFORMED, f"{where}.submission 이 없다"
        )
    place = f"{where}.submission"
    return JobSubmission(
        dataset_uri=_text(nested, "dataset_uri", place),
        manifest_checksum=_text(nested, "manifest_checksum", place),
        training_spec_version=_text(nested, "training_spec_version", place),
    )


def _decode_entry(raw: _JsonValue, index: int) -> JournalEntry:
    where = f"jobs[{index}]"
    fields = _mapping(raw, where)
    record = JobRecord(
        job_id=_text(fields, "job_id", where),
        state=_decode_state(fields, where),
        dataset_id=_text(fields, "dataset_id", where),
        accepted_at=_timestamp(fields, "accepted_at", where),
        started_at=_optional_timestamp(fields, "started_at", where),
        finished_at=_optional_timestamp(fields, "finished_at", where),
        artifact=_decode_artifact(fields, where),
        evaluation=_decode_evaluation(fields, where),
        failure=_decode_failure(fields, where),
    )
    return JournalEntry(
        idempotency_key=_text(fields, "idempotency_key", where),
        record=record,
        submission=_decode_submission(fields, where),
    )


def _require_unique(entries: tuple[JournalEntry, ...]) -> None:
    job_ids = [entry.record.job_id for entry in entries]
    if len(set(job_ids)) != len(job_ids):
        raise _RejectedError(
            JournalFailureReason.INVARIANT_VIOLATED, "job_id 가 중복된다"
        )
    keys = [entry.idempotency_key for entry in entries]
    if len(set(keys)) != len(keys):
        raise _RejectedError(
            JournalFailureReason.INVARIANT_VIOLATED, "idempotency_key 가 중복된다"
        )


def _decode_entries(raw: _JsonValue) -> tuple[JournalEntry, ...]:
    if not isinstance(raw, list):
        raise _RejectedError(JournalFailureReason.MALFORMED, "jobs 가 배열이 아니다")
    entries = tuple(_decode_entry(item, index) for index, item in enumerate(raw))
    _require_unique(entries)
    return entries


def decode_snapshot(payload: bytes) -> JournalSnapshot | JournalFailed:
    """바이트 → 스냅숏. 문법·구조·어휘 오류는 `MALFORMED`, 버전 불일치는
    `SCHEMA_UNSUPPORTED`, 값 불변식 위반은 `INVARIANT_VIOLATED`. 예외로 새지 않는다."""
    try:
        decoded: _JsonValue = json.loads(payload)
        fields = _mapping(decoded, "저널")
        version = _text(fields, "schema_version", "저널")
        if version != JOURNAL_SCHEMA_VERSION:
            raise _RejectedError(
                JournalFailureReason.SCHEMA_UNSUPPORTED,
                f"지원하지 않는 저널 schema_version: {version}",
            )
        if "jobs" not in fields:
            raise _RejectedError(JournalFailureReason.MALFORMED, "jobs 가 없다")
        return JournalSnapshot(entries=_decode_entries(fields["jobs"]))
    except _RejectedError as rejected:
        return JournalFailed(rejected.reason, rejected.detail)
    except (json.JSONDecodeError, UnicodeDecodeError) as exc:
        return JournalFailed(JournalFailureReason.MALFORMED, f"JSON 판독 실패: {exc}")
    except ValueError as exc:
        # `JobRecord`·`JobFailure`·`JobSubmission`·`JournalEntry` 생성자의 불변식 위반
        # (조합·시각·빈 문자열) — 판독에서도 같은 규칙이 선다.
        return JournalFailed(JournalFailureReason.INVARIANT_VIOLATED, str(exc))
