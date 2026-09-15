"""`ml_engine.serving.grpc` — 유일 `grpc` import 진입점(5A ③, pyproject.toml
`ignore_imports` 예외 둘 중 하나). `build_server`가 세 servicer 를 **한 서버**에 등록하고
(ADR 0010 D-8), bounded concurrency(`maximum_concurrent_rpcs`)를 건다. `shutdown()`은
readiness `NOT_READY`가 `server.stop`보다 **먼저**(설계 검토 (1) 「종료 순서」).

`training_pb2_grpc.TrainingJobServiceServicer`(생성 base class, `ml_engine.contracts`
재수출)만 타입으로 받는다 — `ml_engine.training.jobs.servicer` 의 **구체 구현은 import
하지 않는다**(forbidden 계약 — `serving`은 `training`을 모른다). 실 인스턴스는 조립 근
(`ml_engine.app`)이 만들어 넘긴다."""

from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass

import grpc

from ml_engine.contracts import (
    embedding_pb2_grpc,
    prediction_pb2_grpc,
    training_pb2_grpc,
)
from ml_engine.serving.embedding import EmbeddingServicer
from ml_engine.serving.policy import ServingPolicy
from ml_engine.serving.prediction import BidPredictionServicer
from ml_engine.serving.readiness import ReadinessGate


@dataclass(frozen=True)
class Servicers:
    """`build_server`에 등록할 servicer 셋 — 조립 근이 만든 인스턴스."""

    prediction: BidPredictionServicer
    embedding: EmbeddingServicer
    training_job: training_pb2_grpc.TrainingJobServiceServicer


def build_server(policy: ServingPolicy, servicers: Servicers) -> grpc.Server:
    """`grpc.server(ThreadPoolExecutor(max_workers), maximum_concurrent_rpcs=…)` —
    N+1 번째 동시 요청은 gRPC `RESOURCE_EXHAUSTED`(scope.md ①)."""
    server = grpc.server(
        ThreadPoolExecutor(max_workers=policy.max_workers),
        maximum_concurrent_rpcs=policy.max_concurrent_rpcs,
    )
    prediction_pb2_grpc.add_BidPredictionServiceServicer_to_server(
        servicers.prediction, server
    )
    embedding_pb2_grpc.add_EmbeddingServiceServicer_to_server(
        servicers.embedding, server
    )
    training_pb2_grpc.add_TrainingJobServiceServicer_to_server(
        servicers.training_job, server
    )
    return server


def is_deadline_active(context: grpc.ServicerContext) -> bool:
    """긴 계산 앞에서 deadline 확인(ADR 0010 D-2) — 취소된 요청에 계산하지 않는다."""
    return context.is_active()


def shutdown(gate: ReadinessGate, server: grpc.Server, *, grace_seconds: float) -> None:
    """종료 순서(설계 검토 (1)): readiness `NOT_READY` 선행 → `server.stop(grace)` →
    대기. 새 요청은 readiness 가 이미 `NOT_READY`인 뒤에야 gRPC 계층에 닿는다."""
    gate.begin_shutdown()
    stopped_event = server.stop(grace_seconds)
    stopped_event.wait()
