"""`ml_engine.serving.prediction` — `BidPredictionServicer`(scope.md ③④). `CalculateOptimalBid`는
**오버라이드하지 않는다**(D-5E-3 (a)) — 생성 servicer 기본 구현이 gRPC `UNIMPLEMENTED`를
낸다(grpc 런타임 관례, 미구현 메서드는 `context.set_code(UNIMPLEMENTED)`). 5E-2 가 2F
병합 뒤 이 메서드를 채운다.

`GetModelMetadata`는 `readiness`를 **항상 `NOT_READY`**로 낸다(정책 preload 성공 여부와
무관 — 이 slice 에는 예측 서빙 자체가 없다, D-5E-3). `promoted`는 미설정."""

from __future__ import annotations

import logging

from ml_engine.contracts import error_pb2, prediction_pb2, prediction_pb2_grpc
from ml_engine.serving.readiness import ReadinessGate
from ml_engine.serving.status import (
    ServicerContext,
    envelope_violation,
    fill_application_failure,
)

_logger = logging.getLogger(__name__)


# `prediction_pb2_grpc.BidPredictionServiceServicer`는 `contracts/__init__.py`처럼
# `bidvector.*`(생성물, mypy override 로 `Any`) 재수출이라 mypy 는 이 base 를 `Any`로
# 본다 — "Class cannot subclass ... (has type Any)"(구조적, `.contracts-generated/`가
# CI 의 S-3 시점엔 아직 없다는 같은 사정, `contracts/__init__.py` 구현 노트 참고). 생성물이
# VCS 밖인 한 계속 필요한 예외다(해소 없음, 5A override 규율과 같은 성질).
class BidPredictionServicer(prediction_pb2_grpc.BidPredictionServiceServicer):  # type: ignore[misc]
    """`gate`는 preload 실패 사유를 로그에 남기는 데만 쓴다(wire `Readiness` 는 이 slice
    에서 gate 상태와 무관하게 항상 `NOT_READY`다, D-5E-3)."""

    def __init__(
        self, gate: ReadinessGate, supported_feature_schema_versions: tuple[str, ...]
    ) -> None:
        self._gate = gate
        self._supported_feature_schema_versions = supported_feature_schema_versions

    def GetModelMetadata(  # noqa: N802 — grpc 생성 시그니처
        self,
        request: prediction_pb2.GetModelMetadataRequest,
        context: ServicerContext,
    ) -> prediction_pb2.GetModelMetadataResponse:
        violation = envelope_violation(
            request.envelope.request_id, request.envelope.correlation_id
        )
        response = prediction_pb2.GetModelMetadataResponse()
        if violation is not None:
            fill_application_failure(
                response.failure,
                code=error_pb2.FAILURE_CODE_INVALID_REQUEST,
                retryable=False,
                detail_code=violation,
            )
            return response

        snapshot = self._gate.snapshot()
        if snapshot.reasons:
            _logger.warning(
                "GetModelMetadata request_id=%s — preload 실패 사유: %s",
                request.envelope.request_id,
                snapshot.reasons,
            )
        response.metadata.supported_feature_schema_versions.extend(
            self._supported_feature_schema_versions
        )
        response.metadata.readiness = prediction_pb2.READINESS_NOT_READY
        return response
