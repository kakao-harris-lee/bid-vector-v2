"""`ml_engine.serving.status` — ADR 0010 D-3 조립 규칙의 Python 쪽(scope.md ⑩). 검증
실패·미준비·미지 job 은 status OK + `ApplicationFailure`다 — 이 모듈은 그 조립을 한
자리로 모은다(servicer 마다 `code`·`retryable`·`detail_code` 세 줄을 반복하지 않기
위함). 예기치 않은 예외를 여기서 잡지 않는다(`BLE` 없음) — servicer 는 구체 예외만
결과 타입으로 다루고, 분류되지 않은 예외는 grpc 런타임이 그대로 `UNKNOWN`/`INTERNAL`로
전파한다(위협 모델 (m))."""

from __future__ import annotations

from enum import StrEnum
from typing import Protocol


class ValidationDetailCode(StrEnum):
    """envelope·요청 필드 검증 실패의 닫힌 어휘(`ApplicationFailure.detail_code`) —
    자유 문자열 금지.

    verifier r1 L-5 — `MODEL_RELEASE_SELECTOR_UNSPECIFIED`·
    `FEATURE_SCHEMA_VERSION_UNSUPPORTED`는 5E-1 의 어느 servicer 도 내지 않아(선언만
    되고 생산 경로가 없었다) 지웠었다. M5/5E-2 — `CalculateOptimalBid`가 채워지며
    `model_release_selector`·`feature_schema_version` 검증이 실제로 필요해져 다시
    넣는다(`serving/prediction.py::_validate`가 생산, scope.md ①). 같은 이유로
    `RELEASE_MISMATCH`·`OBJECTIVE_UNSPECIFIED`·`OBJECTIVE_UNSUPPORTED`도 신설한다 —
    선언만 있고 호출자가 없는 어휘를 남기지 않는다는 규율은 그대로다(각 값의 생산
    지점은 같은 커밋의 `prediction.py`)."""

    REQUEST_ID_EMPTY = "REQUEST_ID_EMPTY"
    CORRELATION_ID_EMPTY = "CORRELATION_ID_EMPTY"
    IDEMPOTENCY_KEY_EMPTY = "IDEMPOTENCY_KEY_EMPTY"
    IDEMPOTENCY_KEY_TOO_LONG = "IDEMPOTENCY_KEY_TOO_LONG"
    DATASET_URI_SCHEME_UNSUPPORTED = "DATASET_URI_SCHEME_UNSUPPORTED"
    MANIFEST_CHECKSUM_INVALID = "MANIFEST_CHECKSUM_INVALID"
    REQUESTED_RELEASE_ID_UNSUPPORTED = "REQUESTED_RELEASE_ID_UNSUPPORTED"
    TEXT_EMPTY = "TEXT_EMPTY"
    TEXT_TOO_LONG = "TEXT_TOO_LONG"
    TEXT_KIND_UNSPECIFIED = "TEXT_KIND_UNSPECIFIED"
    MODEL_RELEASE_SELECTOR_UNSPECIFIED = "MODEL_RELEASE_SELECTOR_UNSPECIFIED"
    FEATURE_SCHEMA_VERSION_UNSUPPORTED = "FEATURE_SCHEMA_VERSION_UNSUPPORTED"
    RELEASE_MISMATCH = "RELEASE_MISMATCH"
    OBJECTIVE_UNSPECIFIED = "OBJECTIVE_UNSPECIFIED"
    OBJECTIVE_UNSUPPORTED = "OBJECTIVE_UNSUPPORTED"


class ReadinessDetailCode(StrEnum):
    """`MODEL_NOT_READY` 응답의 닫힌 어휘. verifier r1 L-5 — `PREDICTION_UNIMPLEMENTED`는
    어디서도 생산되지 않아(`GetModelMetadata`는 `readiness` enum 값만 채우고
    `detail_code`를 나르는 자리가 없다, D-5E-3) 지웠었다. M5/5E-2 — `SERVER_NOT_READY`
    신설(`CalculateOptimalBid`가 runtime 미가용일 때 생산, scope.md ①⑸) — 같은 값을
    `training/jobs/servicer.py`가 자유 문자열 리터럴로도 내지만(그 모듈은 이 slice
    out_of_scope, 값만 우연히 같다) 이 enum 은 `prediction.py` 전용 정본이다."""

    EMBEDDING_MODEL_ABSENT = "EMBEDDING_MODEL_ABSENT"
    SERVER_NOT_READY = "SERVER_NOT_READY"


class _ApplicationFailureLike(Protocol):
    """`error_pb2.ApplicationFailure`의 구조적 타입 — servicer 마다 다른 생성 모듈에서
    온 메시지를 받는다(proto 클래스는 실행 시점 생성물이라 정적 import 불가)."""

    code: int
    retryable: bool
    detail_code: str


class ServicerContext(Protocol):
    """`grpc.ServicerContext`의 구조적 부분집합 — `serving.prediction`·`serving.embedding`
    은 `grpc`를 직접 import 하지 않는다(5A ③ 예외는 `serving.grpc`·
    `training.jobs.servicer` 둘뿐, pyproject.toml `ignore_imports`)."""

    def is_active(self) -> bool: ...


def fill_application_failure(
    failure: _ApplicationFailureLike,
    *,
    code: int,
    retryable: bool,
    detail_code: StrEnum,
) -> None:
    """`failure`(이미 `response.failure` 로 얻은 proto 메시지)를 in-place 로 채운다 —
    유일 조립 지점(ADR 0010 D-3)."""
    failure.code = code
    failure.retryable = retryable
    failure.detail_code = detail_code.value


def envelope_violation(
    request_id: str, correlation_id: str
) -> ValidationDetailCode | None:
    """`RequestEnvelope`(또는 그것을 감싼 `PredictionEnvelope.base`) 공통 검증(2A ④) —
    두 필드 모두 비어 있지 않아야 한다."""
    if not request_id.strip():
        return ValidationDetailCode.REQUEST_ID_EMPTY
    if not correlation_id.strip():
        return ValidationDetailCode.CORRELATION_ID_EMPTY
    return None
