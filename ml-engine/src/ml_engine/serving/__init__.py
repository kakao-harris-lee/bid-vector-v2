"""ml_engine.serving — M2 generated gRPC servicer(5E). SQLAlchemy·DB driver·requests/httpx
기반 외부 수집·`ml_engine.training`·`ml_engine.adapters`를 모른다(v2-지침서.md §3.2,
pyproject.toml import-linter forbidden 계약이 CI 에서 강제). `serving.grpc` 진입점만
`grpc` import 가 허용된다.

공개 표면 재수출((2b) 값 획득 축 표가 전수) — 다른 패키지는 이 최상위 이름만 보고 하위
모듈을 직접 import 하지 않는다."""

from __future__ import annotations

from ml_engine.serving.embedding import EmbeddingServicer
from ml_engine.serving.grpc import Servicers, build_server, shutdown
from ml_engine.serving.policy import (
    SHIPPED_SERVING_POLICY_VERSION,
    PolicyRejected,
    ServingPolicy,
    load_serving_policy,
)
from ml_engine.serving.prediction import BidPredictionServicer
from ml_engine.serving.readiness import (
    PreloadOutcome,
    Readiness,
    ReadinessGate,
    ReadinessSnapshot,
)
from ml_engine.serving.runtime import PredictionRuntime, build_derived_release
from ml_engine.serving.status import (
    ReadinessDetailCode,
    ServicerContext,
    ValidationDetailCode,
    envelope_violation,
    fill_application_failure,
)

__all__ = [
    "SHIPPED_SERVING_POLICY_VERSION",
    "BidPredictionServicer",
    "EmbeddingServicer",
    "PolicyRejected",
    "PredictionRuntime",
    "PreloadOutcome",
    "Readiness",
    "ReadinessDetailCode",
    "ReadinessGate",
    "ReadinessSnapshot",
    "ServicerContext",
    "Servicers",
    "ServingPolicy",
    "ValidationDetailCode",
    "build_derived_release",
    "build_server",
    "envelope_violation",
    "fill_application_failure",
    "load_serving_policy",
    "shutdown",
]
