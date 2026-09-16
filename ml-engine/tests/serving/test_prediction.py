"""RED → GREEN — `ml_engine.serving.prediction.BidPredictionServicer`(scope.md ①~④).
M5/5E-2 가 `CalculateOptimalBid`를 채운다(5E-1 의 UNIMPLEMENTED 단언을 대체) — 검증
순서 ①⑴~⑺ 각 1 + 순서 test(검증 실패가 미준비보다 먼저·검증 실패 시 `serve_bid_rates`
0회) + READY/NOT_READY 각."""

from __future__ import annotations

import logging
from decimal import Decimal

import pytest

from ml_engine.contracts import error_pb2, features_pb2, prediction_pb2
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.results import (
    Candidate,
    CandidateLabel,
    Diagnostics,
    IntervalSource,
    SegmentSupport,
    Success,
    Uncertainty,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)
from ml_engine.serving.prediction import BidPredictionServicer
from ml_engine.serving.readiness import PreloadOutcome, ReadinessGate
from ml_engine.serving.runtime import PredictionRuntime, build_derived_release

_POLICY = InferencePolicy(
    version="inference-v1",
    scenario_z=Decimal("1.2816"),
    scenario_weights=(Decimal("0.24"), Decimal("0.52"), Decimal("0.24")),
    scenario_z_signs=(-1, 0, 1),
    scenario_clamp_min=Decimal("0.7"),
    scenario_clamp_max=Decimal("1.4"),
    scenario_bid_rate_digits=4,
    assessment_agency_prior_strength=Decimal("12.0"),
    assessment_category_prior_strength=Decimal("40.0"),
    assessment_min_predictive_std=Decimal("0.002"),
    assessment_min_samples_for_variance=2,
    assessment_plausible_min=Decimal("0.8"),
    assessment_plausible_max=Decimal("1.2"),
    assessment_agency_sample_threshold=1,
    reserve_draw_count=4,
    reserve_expected_price_count=15,
    reserve_min_reserve_records=8,
    bid_ratio_min_samples=3,
    bid_ratio_plausible_min=Decimal("0.5"),
    bid_ratio_plausible_max=Decimal("1.5"),
    gbm_min_category_rows=40,
    maturity_window_days=7,
)

_SUPPORTED_SCHEMAS = ("award-rate-features-v2",)


def _runtime() -> PredictionRuntime:
    release = build_derived_release(_POLICY, "sha-test")
    return PredictionRuntime(
        policy=_POLICY,
        release=release,
        supported_feature_schema_versions=_SUPPORTED_SCHEMAS,
    )


def _gate(*, ready: bool) -> ReadinessGate:
    outcomes = [
        PreloadOutcome(name="inference", ok=ready, reason=None if ready else "r")
    ]
    return ReadinessGate.from_preload(outcomes)


def _servicer(
    *, runtime: PredictionRuntime | None = None, ready: bool | None = None
) -> BidPredictionServicer:
    gate_ready = ready if ready is not None else runtime is not None
    return BidPredictionServicer(_gate(ready=gate_ready), _SUPPORTED_SCHEMAS, runtime)


def _metadata_request(
    request_id: str = "req-1", correlation_id: str = "corr-1"
) -> prediction_pb2.GetModelMetadataRequest:
    request = prediction_pb2.GetModelMetadataRequest()
    request.envelope.request_id = request_id
    request.envelope.correlation_id = correlation_id
    return request


def _valid_calc_request(
    *,
    request_id: str = "req-1",
    correlation_id: str = "corr-1",
    feature_schema_version: str = "award-rate-features-v2",
    objective: int = features_pb2.OPTIMIZATION_OBJECTIVE_SCENARIO_TRIPLE,
) -> prediction_pb2.CalculateOptimalBidRequest:
    """검증을 전부 통과하는 최소 요청 — `latest_promoted` 선택자(대조 불필요)."""
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.envelope.base.request_id = request_id
    request.envelope.base.correlation_id = correlation_id
    request.envelope.feature_schema_version = feature_schema_version
    request.envelope.model_release_selector.latest_promoted.SetInParent()
    request.objective = objective
    return request


def _success() -> Success:
    def candidate(label: CandidateLabel, bid_rate: str, weight: str) -> Candidate:
        return Candidate(
            label=label,
            bid_rate=Decimal(bid_rate),
            weight=Decimal(weight),
            weight_policy_version="candidate-weight-v1",
        )

    return Success(
        candidates=(
            candidate(CandidateLabel.CONSERVATIVE, "0.8500", "0.2400"),
            candidate(CandidateLabel.BASE, "0.8750", "0.5200"),
            candidate(CandidateLabel.AGGRESSIVE, "0.9000", "0.2400"),
        ),
        fitness=Decimal("0.6900"),  # type: ignore[arg-type]
        uncertainty=Uncertainty(
            sample_size=15,
            dispersion=Decimal("0.0380"),
            estimate_margin=Decimal("0.0210"),
            interval_source=IntervalSource.POSTERIOR_PREDICTIVE,
        ),
        diagnostics=Diagnostics(
            training_row_count=0,
            segment_support=SegmentSupport.GLOBAL,
            shrinkage_weight=Decimal("0.0000"),
            excluded_observations=0,
            agency_sample_count=0,
            agency_sample_below_threshold=False,
        ),
    )


# ---- GetModelMetadata ----


def test_get_model_metadata_reports_supported_schemas() -> None:
    servicer = _servicer()
    response = servicer.GetModelMetadata(_metadata_request(), context=None)
    assert response.WhichOneof("result") == "metadata"
    assert list(response.metadata.supported_feature_schema_versions) == [
        "award-rate-features-v2"
    ]


def test_get_model_metadata_readiness_is_ready_when_runtime_present() -> None:
    servicer = _servicer(runtime=_runtime())
    response = servicer.GetModelMetadata(_metadata_request(), context=None)
    assert response.metadata.readiness == prediction_pb2.READINESS_READY


def test_get_model_metadata_readiness_is_not_ready_without_runtime() -> None:
    servicer = _servicer(runtime=None)
    response = servicer.GetModelMetadata(_metadata_request(), context=None)
    assert response.metadata.readiness == prediction_pb2.READINESS_NOT_READY


def test_get_model_metadata_promoted_is_set_when_ready() -> None:
    runtime = _runtime()
    servicer = _servicer(runtime=runtime)
    response = servicer.GetModelMetadata(_metadata_request(), context=None)
    assert response.metadata.HasField("promoted")
    assert response.metadata.promoted.release_id == runtime.release.release_id
    assert (
        response.metadata.promoted.artifact_checksum
        == runtime.release.artifact_checksum
    )


def test_get_model_metadata_promoted_is_unset_when_not_ready() -> None:
    servicer = _servicer(runtime=None)
    response = servicer.GetModelMetadata(_metadata_request(), context=None)
    assert not response.metadata.HasField("promoted")


def test_get_model_metadata_empty_request_id_is_invalid_request() -> None:
    servicer = _servicer()
    response = servicer.GetModelMetadata(_metadata_request(request_id=""), context=None)
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST


def test_get_model_metadata_empty_correlation_id_is_invalid_request() -> None:
    servicer = _servicer()
    response = servicer.GetModelMetadata(
        _metadata_request(correlation_id=""), context=None
    )
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST


# ---- CalculateOptimalBid — 검증 순서 ①⑴~⑺ 각 1 ----


class _ActiveContext:
    def is_active(self) -> bool:
        return True


class _InactiveContext:
    def is_active(self) -> bool:
        return False


def test_step1_empty_request_id_is_invalid_request() -> None:
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request(request_id="")
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "REQUEST_ID_EMPTY"


def test_step2_unsupported_schema_is_unsupported_schema() -> None:
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request(feature_schema_version="bidvector.ml.v1")
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_SCHEMA
    assert response.failure.detail_code == "FEATURE_SCHEMA_VERSION_UNSUPPORTED"


def test_step3a_unset_selector_is_invalid_request() -> None:
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request()
    request.envelope.model_release_selector.ClearField("latest_promoted")
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "MODEL_RELEASE_SELECTOR_UNSPECIFIED"


def test_step3b_exact_release_mismatch_is_unsupported_release() -> None:
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request()
    request.envelope.model_release_selector.exact_release.release_id = "wrong-id"
    request.envelope.model_release_selector.exact_release.artifact_checksum = (
        "sha256:" + "0" * 64
    )
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_RELEASE
    assert response.failure.detail_code == "RELEASE_MISMATCH"


def test_step3b_exact_release_same_id_different_checksum_is_unsupported_release() -> (
    None
):
    """verifier r1 M-4 — D-5E2-9 는 「id·checksum 둘 다 같아야 통과」다. 기존
    `..._mismatch...` test 는 둘 다 틀린 값을 썼다 — 재야 할 case 는 한쪽만 같은
    부분 불일치다."""
    runtime = _runtime()
    servicer = _servicer(runtime=runtime)
    request = _valid_calc_request()
    request.envelope.model_release_selector.exact_release.release_id = (
        runtime.release.release_id
    )
    request.envelope.model_release_selector.exact_release.artifact_checksum = (
        "sha256:" + "1" * 64
    )
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_RELEASE
    assert response.failure.detail_code == "RELEASE_MISMATCH"


def test_step3b_exact_release_same_checksum_different_id_is_unsupported_release() -> (
    None
):
    """verifier r1 M-4 — 반대 방향 부분 불일치(checksum 만 같음)."""
    runtime = _runtime()
    servicer = _servicer(runtime=runtime)
    request = _valid_calc_request()
    request.envelope.model_release_selector.exact_release.release_id = "wrong-id"
    request.envelope.model_release_selector.exact_release.artifact_checksum = (
        runtime.release.artifact_checksum
    )
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_RELEASE
    assert response.failure.detail_code == "RELEASE_MISMATCH"


def test_step3b_exact_release_matching_current_release_passes(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    runtime = _runtime()
    servicer = _servicer(runtime=runtime)
    request = _valid_calc_request()
    request.envelope.model_release_selector.exact_release.release_id = (
        runtime.release.release_id
    )
    request.envelope.model_release_selector.exact_release.artifact_checksum = (
        runtime.release.artifact_checksum
    )
    calls: list[int] = []
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: (calls.append(1), _success())[1],
    )
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "success"
    assert calls == [1]


def test_step4a_unspecified_objective_is_invalid_request() -> None:
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request(
        objective=features_pb2.OPTIMIZATION_OBJECTIVE_UNSPECIFIED
    )
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "OBJECTIVE_UNSPECIFIED"


def test_step4b_unsupported_objective_value_is_invalid_request() -> None:
    """verifier r1 M-1 — `OBJECTIVE_UNSUPPORTED`(닫힌 집합 밖 값, `UNSPECIFIED`와
    다른 사유)에 생산 test 가 없었다. `_validate_objective`는 이미 옳게 이 값을
    내지만(실측 — 5E-1 verifier r1 M-8/L-5 규율의 반대 방향: 생산은 있고 증거가
    없는 어휘를 남기지 않는다) 그것을 고정하는 test 가 없었다."""
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request(objective=99)  # 닫힌 집합(SCENARIO_TRIPLE) 밖 값
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "OBJECTIVE_UNSUPPORTED"


def test_step5_runtime_none_is_model_not_ready() -> None:
    servicer = _servicer(runtime=None)
    request = _valid_calc_request()
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_MODEL_NOT_READY
    assert response.failure.retryable is True
    assert response.failure.detail_code == "SERVER_NOT_READY"


def test_step5_runtime_present_but_gate_not_ready_is_model_not_ready(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """code-reviewer HIGH(R-H1) — 미준비 판정은 gate 스냅샷을 본다, `runtime` 유무가
    아니라. 정상 배선(`app/server.py::_prediction_runtime`)에서는 이 조합(runtime
    있음 + gate NOT_READY)이 생기지 않지만(둘이 같은 조건에서만 성립하도록 조립
    근이 보장한다), 검증 로직 자체가 `runtime`이 아니라 gate 를 보고 있는지는 이
    조합을 직접 구성해야만 확인된다 — `runtime`이 있다는 이유만으로 계산이
    진행되면 Kotlin `ReleaseCheck`(latest_promoted)가 promoted 부재로 그 정직한
    응답을 폐기한다(scope 위협 모델 (f))."""
    servicer = _servicer(runtime=_runtime(), ready=False)
    request = _valid_calc_request()
    calls: list[int] = []
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: calls.append(1),
    )
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_MODEL_NOT_READY
    assert response.failure.detail_code == "SERVER_NOT_READY"
    assert calls == []


def test_step6_inactive_context_returns_without_computing(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request()
    calls: list[int] = []
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: calls.append(1),
    )
    response = servicer.CalculateOptimalBid(request, _InactiveContext())
    assert response.WhichOneof("result") is None
    assert calls == []


def test_step7_success_is_mapped_through_wire(monkeypatch: pytest.MonkeyPatch) -> None:
    runtime = _runtime()
    servicer = _servicer(runtime=runtime)
    request = _valid_calc_request()
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: _success(),
    )
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "success"
    assert len(response.success.candidates) == 3
    assert response.success.release.release_id == runtime.release.release_id
    assert (
        response.success.release.feature_schema_version
        == request.envelope.feature_schema_version
    )


def test_step7_unmeasurable_is_mapped_through_wire(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request()
    unmeasurable = Unmeasurable(
        reason=UnmeasurableReason.INSUFFICIENT_SAMPLES,
        detail=UnmeasurableDetail.TOO_FEW_OBSERVATIONS,
    )
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: unmeasurable,
    )
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.WhichOneof("result") == "unmeasurable"
    assert (
        response.unmeasurable.reason
        == error_pb2.UNMEASURABLE_REASON_INSUFFICIENT_SAMPLES
    )


def test_step7_mapping_rejected_raises(monkeypatch: pytest.MonkeyPatch) -> None:
    """D-5E2-6 — 매핑 불변식 위반은 삼키지 않고 예외로 올린다."""
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request()
    broken = _success()
    object.__setattr__(broken.uncertainty, "sample_size", 0)
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: broken,
    )
    with pytest.raises(RuntimeError, match="D-5E2-6"):
        servicer.CalculateOptimalBid(request, _ActiveContext())


# ---- 순서(검증 > 미준비, 검증 실패 시 serve_bid_rates 0회) ----


def test_validation_failure_precedes_not_ready_check(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """runtime 이 없어도(NOT_READY) 검증 결함(빈 request_id)이 먼저 잡힌다 — 검증
    결함이 MODEL_NOT_READY 에 가려지지 않는다(설계 검토 우회 (20))."""
    servicer = _servicer(runtime=None)
    request = _valid_calc_request(request_id="")
    response = servicer.CalculateOptimalBid(request, _ActiveContext())
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.detail_code == "REQUEST_ID_EMPTY"


@pytest.mark.parametrize(
    "make_request",
    [
        lambda: _valid_calc_request(request_id=""),
        lambda: _valid_calc_request(feature_schema_version="bidvector.ml.v1"),
        lambda: _valid_calc_request(
            objective=features_pb2.OPTIMIZATION_OBJECTIVE_UNSPECIFIED
        ),
    ],
)
def test_validation_failure_never_calls_serve_bid_rates(
    monkeypatch: pytest.MonkeyPatch, make_request
) -> None:
    servicer = _servicer(runtime=_runtime())
    calls: list[int] = []
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: calls.append(1),
    )
    servicer.CalculateOptimalBid(make_request(), _ActiveContext())
    assert calls == []


def test_not_ready_never_calls_serve_bid_rates(monkeypatch: pytest.MonkeyPatch) -> None:
    servicer = _servicer(runtime=None)
    calls: list[int] = []
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: calls.append(1),
    )
    servicer.CalculateOptimalBid(_valid_calc_request(), _ActiveContext())
    assert calls == []


# ---- 로그(code-reviewer MEDIUM R-M2) ----


def test_calculate_optimal_bid_logs_rejection_with_request_id(
    caplog: pytest.LogCaptureFixture,
) -> None:
    """`GetModelMetadata`는 이미 `request_id`를 실은 로그 선례가 있다 —
    `CalculateOptimalBid`도 거부 경로에서 같은 관례를 따른다."""
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request(
        request_id="req-log-reject", feature_schema_version="bidvector.ml.v1"
    )
    with caplog.at_level(logging.WARNING, logger="ml_engine.serving.prediction"):
        servicer.CalculateOptimalBid(request, _ActiveContext())
    assert "req-log-reject" in caplog.text
    assert "FEATURE_SCHEMA_VERSION_UNSUPPORTED" in caplog.text


def test_calculate_optimal_bid_logs_before_raising_on_mapping_rejected(
    monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    """D-5E2-6 예외 직전에 `request_id`를 남긴다 — 예외 메시지 자체엔 없다."""
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request(request_id="req-log-mapping-rejected")
    broken = _success()
    object.__setattr__(broken.uncertainty, "sample_size", 0)
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: broken,
    )
    with (
        caplog.at_level(logging.ERROR, logger="ml_engine.serving.prediction"),
        pytest.raises(RuntimeError),
    ):
        servicer.CalculateOptimalBid(request, _ActiveContext())
    assert "req-log-mapping-rejected" in caplog.text


def test_nan_candidate_rate_is_controlled_exception_with_log(
    monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    """code-reviewer HIGH/verifier r2 N-1 — `wire.py`의 비유한 검사가 범위 검사보다
    앞으로 옮겨진 뒤에도 end-to-end 로 통제된 예외(D-5E2-6 문구)와 ERROR 로그
    (request_id·correlation_id 둘 다)가 나오는지 확인한다. 시정 전에는
    `decimal.InvalidOperation`이 그대로 새 나가 이 test 가 `RuntimeError`를 못 잡고
    실패했다(재현 확인)."""
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request(
        request_id="req-log-nan", correlation_id="corr-log-nan"
    )
    broken = _success()
    object.__setattr__(broken.candidates[0], "bid_rate", Decimal("NaN"))
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: broken,
    )
    with (
        caplog.at_level(logging.ERROR, logger="ml_engine.serving.prediction"),
        pytest.raises(RuntimeError, match="D-5E2-6"),
    ):
        servicer.CalculateOptimalBid(request, _ActiveContext())
    assert "req-log-nan" in caplog.text
    assert "corr-log-nan" in caplog.text


def test_calculate_optimal_bid_logs_completion_with_request_id(
    monkeypatch: pytest.MonkeyPatch, caplog: pytest.LogCaptureFixture
) -> None:
    servicer = _servicer(runtime=_runtime())
    request = _valid_calc_request(request_id="req-log-success")
    monkeypatch.setattr(
        "ml_engine.serving.prediction.serve_bid_rates",
        lambda req, policy: _success(),
    )
    with caplog.at_level(logging.INFO, logger="ml_engine.serving.prediction"):
        servicer.CalculateOptimalBid(request, _ActiveContext())
    assert "req-log-success" in caplog.text
