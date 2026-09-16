"""RED — `ml_engine.serving.grpc`(scope.md ①, 설계 검토 (1) 「bounded concurrency」·
「종료 순서」). 유일 grpc import 진입점 — in-process 실 소켓으로 세 servicer 등록을
확인하고, 종료 순서는 fake 객체의 호출 기록으로 관측한다."""

from __future__ import annotations

import threading
import time
from concurrent.futures import ThreadPoolExecutor

import grpc
import pytest

from ml_engine.contracts import (
    embedding_pb2_grpc,
    prediction_pb2_grpc,
    training_pb2,
    training_pb2_grpc,
)
from ml_engine.serving.embedding import EmbeddingServicer
from ml_engine.serving.grpc import Servicers, build_server, shutdown
from ml_engine.serving.policy import ServingPolicy
from ml_engine.serving.prediction import BidPredictionServicer
from ml_engine.serving.readiness import PreloadOutcome, ReadinessGate
from ml_engine.training.jobs.pipeline import (
    TrainingPipeline,
)
from ml_engine.training.jobs.runner import JobRunner
from ml_engine.training.jobs.servicer import TrainingJobServicer
from ml_engine.training.jobs.store import InMemoryJobStore


def _policy(**overrides: object) -> ServingPolicy:
    base: dict[str, object] = dict(
        version="test-v1",
        max_workers=4,
        max_concurrent_rpcs=4,
        shutdown_grace_seconds=1.0,
        job_workers=1,
        idempotency_key_max_chars=256,
        embedding_text_max_chars=1000,
        dataset_uri_schemes=("file",),
    )
    base.update(overrides)
    return ServingPolicy(**base)  # type: ignore[arg-type]


def _gate() -> ReadinessGate:
    return ReadinessGate.from_preload([PreloadOutcome(name="x", ok=True)])


class _BlockingStore:
    """`GetTrainingJob`을 `release_event`가 열릴 때까지 붙잡아 동시성 상한 test 의
    슬로우 RPC 자리를 흉내 낸다."""

    def __init__(self, release_event: threading.Event) -> None:
        self._release_event = release_event
        self.in_flight = 0
        self._lock = threading.Lock()

    def get(self, job_id: str) -> None:
        with self._lock:
            self.in_flight += 1
        self._release_event.wait(timeout=5.0)
        return None


def _build_real_servicers(
    policy: ServingPolicy, gate: ReadinessGate, store: object
) -> Servicers:
    runner = JobRunner(InMemoryJobStore(), max_workers=1)

    def _factory(spec: object) -> TrainingPipeline:
        raise AssertionError("test 는 job 을 시작하지 않는다")

    training_job_servicer = TrainingJobServicer(
        store=store,  # type: ignore[arg-type]
        runner=runner,
        is_ready=lambda: True,
        pipeline_factory=_factory,  # type: ignore[arg-type]
        dataset_uri_schemes=frozenset({"file"}),
        idempotency_key_max_chars=256,
    )
    return Servicers(
        prediction=BidPredictionServicer(gate, ("v2",)),
        embedding=EmbeddingServicer(gate, text_max_chars=1000),
        training_job=training_job_servicer,
    )


def test_build_server_registers_three_services_and_serves() -> None:
    policy = _policy()
    gate = _gate()
    store = InMemoryJobStore()
    servicers = _build_real_servicers(policy, gate, store)
    server = build_server(policy, servicers)
    port = server.add_insecure_port("127.0.0.1:0")
    server.start()
    try:
        with grpc.insecure_channel(f"127.0.0.1:{port}") as channel:
            prediction_stub = prediction_pb2_grpc.BidPredictionServiceStub(channel)
            embedding_stub = embedding_pb2_grpc.EmbeddingServiceStub(channel)
            training_stub = training_pb2_grpc.TrainingJobServiceStub(channel)

            from ml_engine.contracts import embedding_pb2, prediction_pb2

            pred_request = prediction_pb2.GetModelMetadataRequest()
            pred_request.envelope.request_id = "r"
            pred_request.envelope.correlation_id = "c"
            pred_response = prediction_stub.GetModelMetadata(pred_request, timeout=5)
            assert pred_response.WhichOneof("result") == "metadata"

            embed_request = embedding_pb2.GetEmbeddingMetadataRequest()
            embed_request.envelope.request_id = "r"
            embed_request.envelope.correlation_id = "c"
            embed_response = embedding_stub.GetEmbeddingMetadata(
                embed_request, timeout=5
            )
            assert embed_response.WhichOneof("result") == "metadata"

            job_request = training_pb2.GetTrainingJobRequest()
            job_request.envelope.request_id = "r"
            job_request.envelope.correlation_id = "c"
            job_request.job_id = "unknown"
            job_response = training_stub.GetTrainingJob(job_request, timeout=5)
            assert job_response.WhichOneof("result") == "failure"
    finally:
        server.stop(None)


def test_maximum_concurrent_rpcs_exhausted_returns_resource_exhausted() -> None:
    policy = _policy(max_workers=8, max_concurrent_rpcs=1)
    gate = _gate()
    release_event = threading.Event()
    store = _BlockingStore(release_event)
    servicers = _build_real_servicers(policy, gate, store)
    server = build_server(policy, servicers)
    port = server.add_insecure_port("127.0.0.1:0")
    server.start()
    try:
        channel = grpc.insecure_channel(f"127.0.0.1:{port}")
        stub = training_pb2_grpc.TrainingJobServiceStub(channel)

        def _call() -> object:
            request = training_pb2.GetTrainingJobRequest()
            request.envelope.request_id = "r"
            request.envelope.correlation_id = "c"
            request.job_id = "job-1"
            return stub.GetTrainingJob(request, timeout=5)

        with ThreadPoolExecutor(max_workers=2) as pool:
            first = pool.submit(_call)
            deadline = time.monotonic() + 2.0
            while store.in_flight < 1 and time.monotonic() < deadline:
                time.sleep(0.01)
            assert store.in_flight >= 1

            with pytest.raises(grpc.RpcError) as excinfo:
                _call()
            assert excinfo.value.code() == grpc.StatusCode.RESOURCE_EXHAUSTED

            release_event.set()
            first.result(timeout=5)
        channel.close()
    finally:
        release_event.set()
        server.stop(None)


class _RecordingGate:
    def __init__(self) -> None:
        self.calls: list[str] = []

    def begin_shutdown(self) -> None:
        self.calls.append("begin_shutdown")


class _RecordingServer:
    def __init__(self) -> None:
        self.calls: list[str] = []
        self._event = threading.Event()
        self._event.set()

    def stop(self, grace: float) -> threading.Event:
        self.calls.append(f"stop({grace})")
        return self._event


def test_shutdown_calls_begin_shutdown_before_stop() -> None:
    gate = _RecordingGate()
    server = _RecordingServer()
    shutdown(gate, server, grace_seconds=3.0)  # type: ignore[arg-type]
    assert gate.calls == ["begin_shutdown"]
    assert server.calls == ["stop(3.0)"]
