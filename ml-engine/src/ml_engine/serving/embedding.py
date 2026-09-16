"""`ml_engine.serving.embedding` — `EmbeddingServicer`(scope.md ⑤, D-5E-2). 임베딩 모델
실물이 없다 — 검증을 통과한 요청은 `ApplicationFailure(MODEL_NOT_READY,
EMBEDDING_MODEL_ABSENT)`다(해시 fallback 없음, §5 「조용한 fallback 금지」).

검증 순서(설계 검토 우회 (20)): envelope → `TextKind` → `text`(빈/공백·상한) 검증이
**먼저**, 미준비 판정은 그 **뒤**다 — 검증 결함이 미준비 응답에 가려지지 않게 한다."""

from __future__ import annotations

from ml_engine.contracts import (
    embedding_pb2,
    embedding_pb2_grpc,
    error_pb2,
    prediction_pb2,
)
from ml_engine.serving.readiness import ReadinessGate
from ml_engine.serving.status import (
    ReadinessDetailCode,
    ServicerContext,
    ValidationDetailCode,
    envelope_violation,
    fill_application_failure,
)

_KNOWN_TEXT_KINDS: frozenset[int] = frozenset(
    {embedding_pb2.TEXT_KIND_NOTICE, embedding_pb2.TEXT_KIND_OPERATOR_PROFILE}
)


# `serving.prediction.BidPredictionServicer`와 같은 구조적 사정(mypy 가 생성 base 를
# `Any`로 본다) — `type: ignore[misc]` 근거는 그 파일 주석 참고.
class EmbeddingServicer(embedding_pb2_grpc.EmbeddingServiceServicer):  # type: ignore[misc]
    def __init__(self, gate: ReadinessGate, *, text_max_chars: int) -> None:
        if text_max_chars < 1:
            raise ValueError(f"text_max_chars 는 1 이상이어야 합니다: {text_max_chars}")
        self._gate = gate
        self._text_max_chars = text_max_chars

    def GetEmbeddingMetadata(  # noqa: N802
        self,
        request: embedding_pb2.GetEmbeddingMetadataRequest,
        context: ServicerContext,
    ) -> embedding_pb2.GetEmbeddingMetadataResponse:
        response = embedding_pb2.GetEmbeddingMetadataResponse()
        violation = envelope_violation(
            request.envelope.request_id, request.envelope.correlation_id
        )
        if violation is not None:
            fill_application_failure(
                response.failure,
                code=error_pb2.FAILURE_CODE_INVALID_REQUEST,
                retryable=False,
                detail_code=violation,
            )
            return response

        response.metadata.dimension = 0
        response.metadata.readiness = prediction_pb2.READINESS_NOT_READY
        return response

    def EmbedText(  # noqa: N802
        self, request: embedding_pb2.EmbedTextRequest, context: ServicerContext
    ) -> embedding_pb2.EmbedTextResponse:
        response = embedding_pb2.EmbedTextResponse()
        violation = self._validate(request)
        if violation is not None:
            fill_application_failure(
                response.failure,
                code=error_pb2.FAILURE_CODE_INVALID_REQUEST,
                retryable=False,
                detail_code=violation,
            )
            return response

        # 검증을 통과했다 — 그러나 임베딩 모델 실물이 없다(D-5E-2).
        fill_application_failure(
            response.failure,
            code=error_pb2.FAILURE_CODE_MODEL_NOT_READY,
            retryable=False,
            detail_code=ReadinessDetailCode.EMBEDDING_MODEL_ABSENT,
        )
        return response

    def _validate(
        self, request: embedding_pb2.EmbedTextRequest
    ) -> ValidationDetailCode | None:
        violation = envelope_violation(
            request.envelope.base.request_id, request.envelope.base.correlation_id
        )
        if violation is not None:
            return violation
        if request.kind not in _KNOWN_TEXT_KINDS:
            return ValidationDetailCode.TEXT_KIND_UNSPECIFIED
        text = request.text
        if not text.strip():
            return ValidationDetailCode.TEXT_EMPTY
        if len(text) > self._text_max_chars:
            return ValidationDetailCode.TEXT_TOO_LONG
        return None
