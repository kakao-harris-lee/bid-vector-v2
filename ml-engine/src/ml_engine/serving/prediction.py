"""`ml_engine.serving.prediction` — `BidPredictionServicer`(scope.md ①~④). M5/5E-2 —
`CalculateOptimalBid`를 채운다(5E-1 D-5E-3 (a)의 "5E-2 가 채운다"를 이행). 검증 순서
(닫힌 어휘, scope.md ①)는 `_validate`가 소유한다 — servicer 는 그 결과만 본다:

  ⑴ envelope base(request_id·correlation_id 비공백)
  ⑵ feature_schema_version ∈ 지원 집합
  ⑶ model_release_selector 미설정 거부, exact_release 는 현재 DERIVED release 와
     id·checksum 둘 다 같아야 한다(런타임이 있을 때만 대조 가능 — 없으면 ⑸ 가 잡는다)
  ⑷ objective 가 SCENARIO_TRIPLE 이어야 한다(UNSPECIFIED·그 밖 값은 거부)
  ⑸ gate 스냅샷이 READY 가 아님(정책 넷 preload 중 하나라도 실패) → `MODEL_NOT_READY`
     (code-reviewer HIGH R-H1 시정 — `runtime` 유무가 아니라 gate 를 본다)
  ⑹ `context.is_active()` 거짓 → 계산 없이 반환(ADR 0010 D-2)
  ⑺ `serve_bid_rates` 호출 → `serving.wire.map_kernel_result`

검증이 미준비보다 앞선다(설계 검토 우회 (20) 승계) — 검증 결함이 `MODEL_NOT_READY`에
가려지지 않는다. `deadline_policy_version`은 검증하지 않는다(서버가 쓰지 않음, 2A ④ 밖).

`GetModelMetadata`는 `readiness`를 gate 실물로 매핑하고, READY 일 때만 `promoted`를
채운다(D-5E2-10, D-5E-3 해제) — `promoted`와 `CalculateOptimalBid` 응답의 `release`는
같은 `PredictionRuntime.release` 객체의 복사본이다(설계 검토 (1) 「한 함수 한 입력」)."""

from __future__ import annotations

import logging
from dataclasses import dataclass
from enum import StrEnum

from ml_engine.contracts import (
    error_pb2,
    features_pb2,
    prediction_pb2,
    prediction_pb2_grpc,
)
from ml_engine.inference.engine import serve_bid_rates
from ml_engine.serving.readiness import Readiness, ReadinessGate
from ml_engine.serving.runtime import PredictionRuntime
from ml_engine.serving.status import (
    ReadinessDetailCode,
    ServicerContext,
    ValidationDetailCode,
    envelope_violation,
    fill_application_failure,
)
from ml_engine.serving.wire import MappingRejected, map_kernel_result

_logger = logging.getLogger(__name__)

_READINESS_TO_WIRE: dict[Readiness, int] = {
    Readiness.LOADING: prediction_pb2.READINESS_LOADING,
    Readiness.READY: prediction_pb2.READINESS_READY,
    Readiness.NOT_READY: prediction_pb2.READINESS_NOT_READY,
}


@dataclass(frozen=True)
class _Rejection:
    """`_validate`의 거부 결과 — `ApplicationFailure`의 세 성분(ADR 0010 D-3)."""

    code: int
    retryable: bool
    detail_code: StrEnum


def _validate(
    request: prediction_pb2.CalculateOptimalBidRequest,
    gate: ReadinessGate,
    runtime: PredictionRuntime | None,
    supported_feature_schema_versions: tuple[str, ...],
) -> _Rejection | None:
    envelope = request.envelope
    violation = envelope_violation(
        envelope.base.request_id, envelope.base.correlation_id
    )
    if violation is not None:
        return _Rejection(error_pb2.FAILURE_CODE_INVALID_REQUEST, False, violation)

    if envelope.feature_schema_version not in supported_feature_schema_versions:
        return _Rejection(
            error_pb2.FAILURE_CODE_UNSUPPORTED_SCHEMA,
            False,
            ValidationDetailCode.FEATURE_SCHEMA_VERSION_UNSUPPORTED,
        )

    selector_rejection = _validate_selector(envelope.model_release_selector, runtime)
    if selector_rejection is not None:
        return selector_rejection

    objective_rejection = _validate_objective(request.objective)
    if objective_rejection is not None:
        return objective_rejection

    # code-reviewer HIGH(R-H1) — 미준비 판정은 **gate 스냅샷**을 본다, `runtime` 유무가
    # 아니라(D-5E2-10, scope 위협 모델 (f) 「⑸는 gate 실물」). 이전 판은 `runtime is
    # None`만 봤는데, 조립 근이 inference 정책 성공만으로 `runtime`을 만들면서
    # training/evaluation/serving 정책이 깨져 gate 가 NOT_READY(`GetModelMetadata`가
    # `promoted` 미설정)여도 `CalculateOptimalBid`은 계산을 진행해 성공 응답을 냈다 —
    # Kotlin `ReleaseCheck`(`latest_promoted`)가 `promoted`가 없어 그 정직한 응답을
    # 폐기하는 반면 서버는 자신이 미준비임을 스스로 드러내지 않는 상태였다. 이제
    # `runtime`이 있어도 gate 가 READY 가 아니면 거부한다 — 두 조건의 동시 성립은
    # 조립 근(`app/server.py::_prediction_runtime`)이 gate 와 같은 기준으로 보장한다.
    if gate.snapshot().state is not Readiness.READY:
        return _Rejection(
            error_pb2.FAILURE_CODE_MODEL_NOT_READY,
            True,
            ReadinessDetailCode.SERVER_NOT_READY,
        )
    return None


def _validate_selector(
    selector: prediction_pb2.ModelReleaseSelector, runtime: PredictionRuntime | None
) -> _Rejection | None:
    selector_case = selector.WhichOneof("selector")
    if selector_case is None:
        return _Rejection(
            error_pb2.FAILURE_CODE_INVALID_REQUEST,
            False,
            ValidationDetailCode.MODEL_RELEASE_SELECTOR_UNSPECIFIED,
        )
    if selector_case == "exact_release" and runtime is not None:
        exact = selector.exact_release
        release = runtime.release
        if (
            exact.release_id != release.release_id
            or exact.artifact_checksum != release.artifact_checksum
        ):
            return _Rejection(
                error_pb2.FAILURE_CODE_UNSUPPORTED_RELEASE,
                False,
                ValidationDetailCode.RELEASE_MISMATCH,
            )
    return None


def _validate_objective(objective: int) -> _Rejection | None:
    if objective == features_pb2.OPTIMIZATION_OBJECTIVE_UNSPECIFIED:
        return _Rejection(
            error_pb2.FAILURE_CODE_INVALID_REQUEST,
            False,
            ValidationDetailCode.OBJECTIVE_UNSPECIFIED,
        )
    if objective != features_pb2.OPTIMIZATION_OBJECTIVE_SCENARIO_TRIPLE:
        return _Rejection(
            error_pb2.FAILURE_CODE_INVALID_REQUEST,
            False,
            ValidationDetailCode.OBJECTIVE_UNSUPPORTED,
        )
    return None


# `prediction_pb2_grpc`는 `contracts/__init__.py`처럼 `bidvector.*`(생성물) 재수출이라
# mypy 는 이 base 를 `Any`로 본다 — "Class cannot subclass ... (has type Any)"(구조적,
# `contracts/__init__.py` 구현 노트 참고). 생성물이 VCS 밖인 한 계속 필요한 예외다.
class BidPredictionServicer(prediction_pb2_grpc.BidPredictionServiceServicer):  # type: ignore[misc]
    """`runtime`은 preload 성공 시 조립 근(`app/server.py`)이 만든 `PredictionRuntime`
    하나다 — preload 실패면 `None`(D-5E2-1). `gate`는 `GetModelMetadata.readiness`
    매핑에 쓴다(D-5E2-10)."""

    def __init__(
        self,
        gate: ReadinessGate,
        supported_feature_schema_versions: tuple[str, ...],
        runtime: PredictionRuntime | None,
    ) -> None:
        self._gate = gate
        self._supported_feature_schema_versions = supported_feature_schema_versions
        self._runtime = runtime

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
        response.metadata.readiness = _READINESS_TO_WIRE[snapshot.state]
        # D-5E2-10 — READY 일 때만 promoted 를 채운다(값 지어내기 금지, NOT_READY/
        # LOADING 은 미설정). `self._runtime is not None`은 READY 와 동치여야 하지만
        # (둘 다 preload 성공에서만 성립) 방어적으로 둘 다 확인한다 — 배선 결함이
        # 생겨도 값을 지어내지 않는다.
        if snapshot.state is Readiness.READY and self._runtime is not None:
            response.metadata.promoted.CopyFrom(self._runtime.release)
        return response

    def CalculateOptimalBid(  # noqa: N802 — grpc 생성 시그니처
        self,
        request: prediction_pb2.CalculateOptimalBidRequest,
        context: ServicerContext,
    ) -> prediction_pb2.CalculateOptimalBidResponse:
        response = prediction_pb2.CalculateOptimalBidResponse()
        rejection = _validate(
            request, self._gate, self._runtime, self._supported_feature_schema_versions
        )
        if rejection is not None:
            fill_application_failure(
                response.failure,
                code=rejection.code,
                retryable=rejection.retryable,
                detail_code=rejection.detail_code,
            )
            return response

        if not context.is_active():
            # ADR 0010 D-2 — 긴 계산 앞에서 deadline 확인. 클라이언트는 이미 transport
            # 층에서 취소/deadline 초과를 관측하므로 이 응답의 내용은 무의미하다(oneof
            # 미설정 그대로 반환, 값을 지어내지 않는다).
            return response

        runtime = self._runtime
        if runtime is None:
            # `_validate`가 이미 `runtime is None`을 걸렀으므로 여기 도달하면 배선
            # 결함이다(server.py::_unreachable_pipeline_factory 와 같은 관례).
            raise AssertionError(
                "검증을 통과했는데 runtime 이 없다 — 배선 결함(D-5E2-1)."
            )

        result = serve_bid_rates(request, runtime.policy)
        mapped = map_kernel_result(
            result, runtime.release, request.envelope.feature_schema_version
        )
        if isinstance(mapped, MappingRejected):
            # D-5E2-6 — 매핑 불변식 위반은 엔진 결함이다. Unmeasurable 로 위장하지
            # 않는다(값 지어내기 금지) — 예외로 올려 grpc 런타임이 INTERNAL/UNKNOWN
            # 으로 옮기게 한다(BLE 규율, 5E-1 ⑩).
            raise RuntimeError(f"매핑 불변식 위반(D-5E2-6): {mapped.reason}")
        return mapped
