"""`ml_engine.serving.grpc` — 유일 `grpc` import 진입점(5A ③, pyproject.toml
`ignore_imports` 예외 둘 중 하나). `build_server`가 세 servicer 를 **한 서버**에 등록하고
(ADR 0010 D-8), bounded concurrency(`maximum_concurrent_rpcs`)를 건다. `shutdown()`은
readiness `NOT_READY`가 `server.stop`보다 **먼저**(설계 검토 (1) 「종료 순서」).

`training_pb2_grpc.TrainingJobServiceServicer`(생성 base class, `ml_engine.contracts`
재수출)만 타입으로 받는다 — `ml_engine.training.jobs.servicer` 의 **구체 구현은 import
하지 않는다**(forbidden 계약 — `serving`은 `training`을 모른다). 실 인스턴스는 조립 근
(`ml_engine.app`)이 만들어 넘긴다.

verifier r1 M-8 — `is_deadline_active(context.is_active())`(ADR 0010 D-2 「긴 계산
앞에서 deadline 확인」)를 이 slice에서는 지웠다. 5E-1 의 세 servicer 는 이 확인이
붙을 자리가 없다: `GetModelMetadata`·`GetEmbeddingMetadata`·`EmbedText`(검증 실패 또는
`MODEL_NOT_READY`)는 O(1) 이고, `StartTraining`은 검증 뒤 즉시 `ACCEPTED`를 반환하며
실제 학습은 백그라운드 스레드에서 돈다(그 안의 취소 확인은 `CancelToken`이 다른
경로로 이미 한다, D-2D-7). 실 계산(`CalculateOptimalBid`)이 생기는 5E-2 에서 다시
필요해지면 그때 배선한다 — 선언만 있고 호출자가 없는 상태로 남겨두지 않는다."""

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


def shutdown(gate: ReadinessGate, server: grpc.Server, *, grace_seconds: float) -> None:
    """종료 순서(설계 검토 (1)): readiness `NOT_READY` 선행 → `server.stop(grace)` →
    대기. 새 요청은 readiness 가 이미 `NOT_READY`인 뒤에야 gRPC 계층에 닿는다."""
    gate.begin_shutdown()
    stopped_event = server.stop(grace_seconds)
    stopped_event.wait()
