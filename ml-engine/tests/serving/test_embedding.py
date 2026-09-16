"""RED — `ml_engine.serving.embedding.EmbeddingServicer`(scope.md ⑤, D-5E-2). 검증
순서(우회 (20)): envelope → TextKind → text → 그 뒤에야 MODEL_NOT_READY."""

from __future__ import annotations

from ml_engine.contracts import embedding_pb2, error_pb2, prediction_pb2
from ml_engine.serving.embedding import EmbeddingServicer
from ml_engine.serving.readiness import PreloadOutcome, ReadinessGate


def _servicer(*, text_max_chars: int = 10) -> EmbeddingServicer:
    gate = ReadinessGate.from_preload([PreloadOutcome(name="x", ok=True)])
    return EmbeddingServicer(gate, text_max_chars=text_max_chars)


def _valid_embed_request() -> embedding_pb2.EmbedTextRequest:
    request = embedding_pb2.EmbedTextRequest()
    request.envelope.base.request_id = "req-1"
    request.envelope.base.correlation_id = "corr-1"
    request.text = "hello"
    request.kind = embedding_pb2.TEXT_KIND_NOTICE
    return request


def test_get_embedding_metadata_reports_not_ready_and_dimension_zero() -> None:
    servicer = _servicer()
    request = embedding_pb2.GetEmbeddingMetadataRequest()
    request.envelope.request_id = "r"
    request.envelope.correlation_id = "c"
    response = servicer.GetEmbeddingMetadata(request, context=None)
    assert response.WhichOneof("result") == "metadata"
    assert response.metadata.dimension == 0
    assert response.metadata.readiness == prediction_pb2.READINESS_NOT_READY
    assert not response.metadata.HasField("promoted")


def test_get_embedding_metadata_empty_envelope_is_invalid_request() -> None:
    servicer = _servicer()
    request = embedding_pb2.GetEmbeddingMetadataRequest()
    request.envelope.request_id = ""
    request.envelope.correlation_id = "c"
    response = servicer.GetEmbeddingMetadata(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST


def test_embed_text_validated_request_is_model_not_ready() -> None:
    servicer = _servicer()
    response = servicer.EmbedText(_valid_embed_request(), context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_MODEL_NOT_READY
    assert response.failure.retryable is False
    assert response.failure.detail_code == "EMBEDDING_MODEL_ABSENT"


def test_embed_text_empty_envelope_is_invalid_request_not_model_not_ready() -> None:
    """우회 (20) — 검증이 미준비 판정보다 먼저다."""
    servicer = _servicer()
    request = _valid_embed_request()
    request.envelope.base.request_id = ""
    response = servicer.EmbedText(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST


def test_embed_text_unspecified_kind_is_invalid_request() -> None:
    servicer = _servicer()
    request = _valid_embed_request()
    request.kind = embedding_pb2.TEXT_KIND_UNSPECIFIED
    response = servicer.EmbedText(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST


def test_embed_text_undefined_kind_integer_is_invalid_request() -> None:
    servicer = _servicer()
    request = _valid_embed_request()
    request.kind = 99
    response = servicer.EmbedText(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST


def test_embed_text_empty_text_is_invalid_request() -> None:
    servicer = _servicer()
    request = _valid_embed_request()
    request.text = "   "
    response = servicer.EmbedText(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "TEXT_EMPTY"


def test_embed_text_too_long_is_invalid_request() -> None:
    servicer = _servicer(text_max_chars=3)
    request = _valid_embed_request()
    request.text = "abcd"
    response = servicer.EmbedText(request, context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "TEXT_TOO_LONG"


def test_operator_profile_text_kind_is_accepted_by_validation() -> None:
    servicer = _servicer()
    request = _valid_embed_request()
    request.kind = embedding_pb2.TEXT_KIND_OPERATOR_PROFILE
    response = servicer.EmbedText(request, context=None)
    # 검증은 통과하고 실물 부재로 MODEL_NOT_READY 여야 한다(INVALID_REQUEST 아님).
    assert response.failure.code == error_pb2.FAILURE_CODE_MODEL_NOT_READY
