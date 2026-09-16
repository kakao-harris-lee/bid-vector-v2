"""RED — `ml_engine.serving.prediction.BidPredictionServicer`(scope.md ③④, D-5E-3).
`CalculateOptimalBid`는 오버라이드하지 않는다 — 생성 base 의 기본 `UNIMPLEMENTED`
거동을 그대로 확인한다."""

from __future__ import annotations

from ml_engine.contracts import error_pb2, prediction_pb2
from ml_engine.serving.prediction import BidPredictionServicer
from ml_engine.serving.readiness import PreloadOutcome, ReadinessGate


def _servicer(*, ready: bool = True) -> BidPredictionServicer:
    outcomes = [PreloadOutcome(name="x", ok=ready, reason=None if ready else "r")]
    gate = ReadinessGate.from_preload(outcomes)
    return BidPredictionServicer(gate, ("award-rate-features-v2",))


def _request(request_id: str = "req-1", correlation_id: str = "corr-1"):
    request = prediction_pb2.GetModelMetadataRequest()
    request.envelope.request_id = request_id
    request.envelope.correlation_id = correlation_id
    return request


def test_get_model_metadata_reports_supported_schemas() -> None:
    servicer = _servicer()
    response = servicer.GetModelMetadata(_request(), context=None)
    assert response.WhichOneof("result") == "metadata"
    assert list(response.metadata.supported_feature_schema_versions) == [
        "award-rate-features-v2"
    ]


def test_get_model_metadata_readiness_is_always_not_ready_in_5e1() -> None:
    """D-5E-3 — 정책 preload 성공이어도 예측 서빙 자체가 없어 항상 NOT_READY."""
    servicer = _servicer(ready=True)
    response = servicer.GetModelMetadata(_request(), context=None)
    assert response.metadata.readiness == prediction_pb2.READINESS_NOT_READY


def test_get_model_metadata_readiness_is_not_ready_when_gate_failed_too() -> None:
    servicer = _servicer(ready=False)
    response = servicer.GetModelMetadata(_request(), context=None)
    assert response.metadata.readiness == prediction_pb2.READINESS_NOT_READY


def test_get_model_metadata_promoted_is_unset() -> None:
    servicer = _servicer()
    response = servicer.GetModelMetadata(_request(), context=None)
    assert not response.metadata.HasField("promoted")


def test_get_model_metadata_empty_request_id_is_invalid_request() -> None:
    servicer = _servicer()
    response = servicer.GetModelMetadata(_request(request_id=""), context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST


def test_get_model_metadata_empty_correlation_id_is_invalid_request() -> None:
    servicer = _servicer()
    response = servicer.GetModelMetadata(_request(correlation_id=""), context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST


def test_calculate_optimal_bid_is_unimplemented_by_default() -> None:
    """D-5E-3 (a) — servicer 가 메서드를 오버라이드하지 않으므로 생성 base 의 기본
    구현이 그대로 남는다(호출 시 `NotImplementedError`, grpc 런타임이 이걸
    `UNIMPLEMENTED` status 로 옮긴다 — 여기서는 오버라이드 부재만 확인한다)."""
    servicer = _servicer()
    assert (
        type(servicer).CalculateOptimalBid
        is type(servicer).__mro__[1].CalculateOptimalBid
    )
