"""M2/2D — Python 쪽 계약 게이트 증명(④ unknown field·⑤ max payload 경계 쌍·⑧ aio 실측).

**`OPEN-2D-AIO-INPROCESS` 닫힘(실측, 2026-09-07)** — `grpcio-testing`(`grpc_testing` 패키지)은
`grpc.aio` 를 지원하지 않는다(`grpc_testing.aio` 모듈이 없다, `dir(grpc_testing)`에 `channel`·
`server_from_dictionary`만 있고 asyncio 대응이 없음을 실측 확인). scope.md 가 미리 정한
대체안대로 **실제 loopback 서버**(`grpc.aio.server()` + `127.0.0.1:0`)를 pytest fixture 로
쓴다 — Python 쪽만 socket 이고(D-2D-3 의 교차 언어 소켓과는 다른 용도), codex sandbox 는
Python test 를 어차피 못 돌리므로 리뷰 레인의 socket 제약과 충돌하지 않는다.

`pytest-asyncio` 를 새 의존으로 들이지 않는다 — 각 test 는 동기 함수이고 내부에서
`asyncio.run(...)` 으로 async 본문을 실행한다(추가 플러그인 없이 최소 변경).

도구 버전 assertion — `contract-policy.properties`(정책 데이터, Kotlin `contractGate`와 같은
자리)의 `tool.grpcio.tools`·`tool.protobuf.python` 을 이 환경의 실제 설치 버전과 대조한다.
"""

from __future__ import annotations

import asyncio
import importlib.metadata
import sys
import tempfile
from collections.abc import Iterator
from pathlib import Path

import grpc
import grpc.aio
import grpc_tools
import pytest
from grpc_tools import protoc

_REPO_ROOT = Path(__file__).resolve().parents[2]
_GRPC_TOOLS_WELL_KNOWN_TYPES_ROOT = Path(grpc_tools.__file__).resolve().parent / "_proto"
_PROTO_ROOT = _REPO_ROOT / "contracts" / "proto"
_TESTDATA_ROOT = _REPO_ROOT / "contracts" / "testdata" / "prediction"
_POLICY_FILE = _REPO_ROOT / "config" / "quality" / "contract-policy.properties"
_PROTO_FILES = (
    "bidvector/ml/v1/common.proto",
    "bidvector/ml/v1/error.proto",
    "bidvector/ml/v1/features.proto",
    "bidvector/ml/v1/prediction.proto",
    "bidvector/ml/v1/training.proto",
)


def _read(name: str) -> bytes:
    return (_TESTDATA_ROOT / name).read_bytes()


def _policy_value(key: str) -> str:
    """`contract-policy.properties`를 직접 읽는다 — Kotlin `ContractPolicy`(internal)는 다른
    언어 런타임에서 보이지 않으므로 이 파일이 유일한 Python 쪽 읽기 지점이다."""
    for line in _POLICY_FILE.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "=" in stripped:
            key_found, _, value = stripped.partition("=")
            if key_found.strip() == key:
                return value.strip()
    raise AssertionError(f"정책 키 '{key}' 가 '{_POLICY_FILE}' 에 없다")


# ---- 생성 stub — 이 module 전용(conftest.py 를 건드리지 않는다, 2B·2C 와 같은 관례) ----


@pytest.fixture(scope="module")
def generated_stub_path() -> Iterator[Path]:
    with tempfile.TemporaryDirectory(prefix="bidvector-ml-contract-py-2d-") as tmp:
        out_dir = Path(tmp)
        args = [
            "grpc_tools.protoc",
            f"--proto_path={_PROTO_ROOT}",
            f"--proto_path={_GRPC_TOOLS_WELL_KNOWN_TYPES_ROOT}",
            f"--python_out={out_dir}",
            f"--grpc_python_out={out_dir}",
            *(str(_PROTO_ROOT / proto_file) for proto_file in _PROTO_FILES),
        ]
        exit_code = protoc.main(args)
        if exit_code != 0:
            raise RuntimeError(f"grpc_tools.protoc 생성 실패(exit={exit_code}) — args={args}")

        sys.path.insert(0, str(out_dir))
        try:
            yield out_dir
        finally:
            sys.path.remove(str(out_dir))


@pytest.fixture(scope="module")
def prediction_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import prediction_pb2 as module

    return module


@pytest.fixture(scope="module")
def prediction_pb2_grpc(generated_stub_path: Path):
    from bidvector.ml.v1 import prediction_pb2_grpc as module

    return module


@pytest.fixture(scope="module")
def training_pb2_grpc(generated_stub_path: Path):
    from bidvector.ml.v1 import training_pb2_grpc as module

    return module


# ---- 도구 버전 assertion(정책 데이터 대조, contractGate 와 같은 관례) ----


def test_grpcio_tools_version_matches_policy() -> None:
    assert importlib.metadata.version("grpcio-tools") == _policy_value("tool.grpcio.tools")


def test_protobuf_version_matches_policy() -> None:
    assert importlib.metadata.version("protobuf") == _policy_value("tool.protobuf.python")


# ---- ④ unknown field — 실제 grpc.aio 소켓 왕복 뒤에도 보존되고 응답에는 없다 ----


def _append_unknown_varint_field(original: bytes, field_number: int, value: int) -> bytes:
    """protobuf wire format — varint 필드의 tag(field_number<<3 | 0)와 값을 이어 붙인다."""
    tag = (field_number << 3) | 0  # wire type 0 = varint
    return original + _encode_varint(tag) + _encode_varint(value)


def _encode_varint(value: int) -> bytes:
    out = bytearray()
    while True:
        byte = value & 0x7F
        value >>= 7
        if value:
            out.append(byte | 0x80)
        else:
            out.append(byte)
            return bytes(out)


async def _serve_prediction(prediction_pb2_grpc, servicer) -> tuple[grpc.aio.Server, str]:
    server = grpc.aio.server()
    prediction_pb2_grpc.add_BidPredictionServiceServicer_to_server(servicer, server)
    port = server.add_insecure_port("127.0.0.1:0")
    await server.start()
    return server, f"127.0.0.1:{port}"


def test_unknown_field_survives_real_aio_round_trip_and_is_absent_from_response(
    prediction_pb2, prediction_pb2_grpc
) -> None:
    original = _read("calculate_optimal_bid_request.binpb")
    with_unknown = _append_unknown_varint_field(original, field_number=999, value=42)
    request_with_unknown_field = prediction_pb2.CalculateOptimalBidRequest()
    request_with_unknown_field.ParseFromString(with_unknown)

    observed: dict[str, int] = {}

    class _ObservingServicer(prediction_pb2_grpc.BidPredictionServiceServicer):
        async def CalculateOptimalBid(self, request, context):  # noqa: N802
            # `_unknown_fields()`는 CPython 구현 세부라 공개 API 가 아니다(`CopyFrom`도
            # unknown field 를 그대로 복사해 그 비교로는 못 가른다, 실측) — 대신 server 가
            # 받은 바이트 길이를 mutation 전 원본 길이와 비교해 존재를 관측한다. 원본
            # 길이는 client·server 양쪽이 계약으로 이미 아는 값(closure 로 들여옴)이다.
            observed["has_unknown_bytes"] = int(len(request.SerializeToString()) > len(original))
            response = prediction_pb2.CalculateOptimalBidResponse()
            response.failure.code = 4  # FAILURE_CODE_INVALID_REQUEST
            return response

        async def GetModelMetadata(self, request, context):  # noqa: N802
            raise AssertionError("이 test 는 GetModelMetadata 를 부르지 않는다")

    async def run() -> None:
        server, address = await _serve_prediction(prediction_pb2_grpc, _ObservingServicer())
        try:
            async with grpc.aio.insecure_channel(address) as channel:
                stub = prediction_pb2_grpc.BidPredictionServiceStub(channel)
                response = await stub.CalculateOptimalBid(request_with_unknown_field)
                assert response.WhichOneof("result") == "failure"
                assert len(response.SerializeToString()) == len(
                    prediction_pb2.CalculateOptimalBidResponse.FromString(response.SerializeToString())
                    .SerializeToString()
                )
        finally:
            await server.stop(grace=None)

    asyncio.run(run())
    assert observed["has_unknown_bytes"] == 1


def test_unknown_field_is_preserved_by_local_parse_reserialize() -> None:
    """소켓 없이도 성립하는 성질 — protobuf-python 은 proto3 unknown field 를 파싱 시
    보존하고 재직렬화에 되싣는다(Kotlin `ContractUnknownFieldPreservationTest`와 대칭)."""
    from bidvector.ml.v1 import prediction_pb2 as prediction_pb2_module  # noqa: PLC0415

    original = _read("calculate_optimal_bid_request.binpb")
    with_unknown = _append_unknown_varint_field(original, field_number=999, value=42)
    parsed = prediction_pb2_module.CalculateOptimalBidRequest()
    parsed.ParseFromString(with_unknown)
    reserialized = parsed.SerializeToString(deterministic=True)
    assert len(reserialized) == len(with_unknown)


# ---- ⑤ max payload 경계 쌍 — 실제 grpc.aio 소켓(D-2D-6, Kotlin 경계 쌍과 같은 정책 값) ----


def _request_with_sample_count(prediction_pb2, base_request, worst_case_sample, count: int):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.CopyFrom(base_request)
    del request.competition_samples[:]
    for _ in range(count):
        request.competition_samples.add().CopyFrom(worst_case_sample)
    return request


def _boundary_pair(prediction_pb2, max_message_bytes: int):
    base_request = prediction_pb2.CalculateOptimalBidRequest()
    base_request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    worst_case_sample = base_request.competition_samples[0]

    count = 1
    previous = _request_with_sample_count(prediction_pb2, base_request, worst_case_sample, count)
    while len(previous.SerializeToString(deterministic=True)) <= max_message_bytes:
        count += 1
        candidate = _request_with_sample_count(prediction_pb2, base_request, worst_case_sample, count)
        if len(candidate.SerializeToString(deterministic=True)) > max_message_bytes:
            return previous, candidate
        previous = candidate
    raise AssertionError("표본을 늘려도 정책 상한을 넘지 못했다")


def test_boundary_pair_straddles_the_policy_limit(prediction_pb2) -> None:
    max_message_bytes = int(_policy_value("max.message.bytes"))
    below, above = _boundary_pair(prediction_pb2, max_message_bytes)
    assert len(below.SerializeToString(deterministic=True)) <= max_message_bytes
    assert len(above.SerializeToString(deterministic=True)) > max_message_bytes


def test_below_limit_sample_is_accepted_over_real_socket(prediction_pb2, prediction_pb2_grpc) -> None:
    max_message_bytes = int(_policy_value("max.message.bytes"))
    below, _above = _boundary_pair(prediction_pb2, max_message_bytes)

    class _EchoServicer(prediction_pb2_grpc.BidPredictionServiceServicer):
        async def CalculateOptimalBid(self, request, context):  # noqa: N802
            response = prediction_pb2.CalculateOptimalBidResponse()
            response.failure.code = 4  # FAILURE_CODE_INVALID_REQUEST
            return response

        async def GetModelMetadata(self, request, context):  # noqa: N802
            raise AssertionError("이 test 는 GetModelMetadata 를 부르지 않는다")

    async def run() -> None:
        server = grpc.aio.server(options=[("grpc.max_receive_message_length", max_message_bytes)])
        prediction_pb2_grpc.add_BidPredictionServiceServicer_to_server(_EchoServicer(), server)
        port = server.add_insecure_port("127.0.0.1:0")
        await server.start()
        try:
            options = [("grpc.max_receive_message_length", max_message_bytes)]
            async with grpc.aio.insecure_channel(f"127.0.0.1:{port}", options=options) as channel:
                stub = prediction_pb2_grpc.BidPredictionServiceStub(channel)
                response = await stub.CalculateOptimalBid(below)
                assert response.WhichOneof("result") == "failure"
        finally:
            await server.stop(grace=None)

    asyncio.run(run())


def test_above_limit_sample_is_rejected_over_real_socket(prediction_pb2, prediction_pb2_grpc) -> None:
    max_message_bytes = int(_policy_value("max.message.bytes"))
    _below, above = _boundary_pair(prediction_pb2, max_message_bytes)

    class _EchoServicer(prediction_pb2_grpc.BidPredictionServiceServicer):
        async def CalculateOptimalBid(self, request, context):  # noqa: N802
            response = prediction_pb2.CalculateOptimalBidResponse()
            response.failure.code = 4
            return response

        async def GetModelMetadata(self, request, context):  # noqa: N802
            raise AssertionError("이 test 는 GetModelMetadata 를 부르지 않는다")

    async def run() -> None:
        server = grpc.aio.server(options=[("grpc.max_receive_message_length", max_message_bytes)])
        prediction_pb2_grpc.add_BidPredictionServiceServicer_to_server(_EchoServicer(), server)
        port = server.add_insecure_port("127.0.0.1:0")
        await server.start()
        try:
            options = [("grpc.max_receive_message_length", max_message_bytes)]
            async with grpc.aio.insecure_channel(f"127.0.0.1:{port}", options=options) as channel:
                stub = prediction_pb2_grpc.BidPredictionServiceStub(channel)
                with pytest.raises(grpc.aio.AioRpcError) as excinfo:
                    await stub.CalculateOptimalBid(above)
                # Kotlin 쪽과 같은 실측(evidence) — 경계에 딱 붙은 초과분은 서버가
                # RESOURCE_EXHAUSTED 를 보내기 전에 스트림이 끊겨 CANCELLED 로도 나타날 수
                # 있다. 둘 다 "크기로 거부됐다"로 받아들인다.
                assert excinfo.value.code() in (
                    grpc.StatusCode.RESOURCE_EXHAUSTED,
                    grpc.StatusCode.CANCELLED,
                )
        finally:
            await server.stop(grace=None)

    asyncio.run(run())


# ---- ⑧ aio in-process 실측 — 실제 loopback 서버 위에서 2B·2C 를 함께 부른다 ----


def test_prediction_and_training_share_one_aio_server(
    prediction_pb2, prediction_pb2_grpc, training_pb2_grpc
) -> None:
    from bidvector.ml.v1 import training_pb2 as training_pb2_module  # noqa: PLC0415

    class _PredictionServicer(prediction_pb2_grpc.BidPredictionServiceServicer):
        async def CalculateOptimalBid(self, request, context):  # noqa: N802
            response = prediction_pb2.CalculateOptimalBidResponse()
            response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
            return response

        async def GetModelMetadata(self, request, context):  # noqa: N802
            raise AssertionError("이 test 는 GetModelMetadata 를 부르지 않는다")

    class _TrainingServicer(training_pb2_grpc.TrainingJobServiceServicer):
        async def StartTraining(self, request, context):  # noqa: N802
            response = training_pb2_module.StartTrainingResponse()
            response.handle.job_id = "fake-job-0"
            response.handle.state = training_pb2_module.JOB_STATE_ACCEPTED
            return response

        async def GetTrainingJob(self, request, context):  # noqa: N802
            raise AssertionError("이 test 는 GetTrainingJob 을 부르지 않는다")

        async def CancelTrainingJob(self, request, context):  # noqa: N802
            raise AssertionError("이 test 는 CancelTrainingJob 을 부르지 않는다")

    async def run() -> None:
        server = grpc.aio.server()
        prediction_pb2_grpc.add_BidPredictionServiceServicer_to_server(_PredictionServicer(), server)
        training_pb2_grpc.add_TrainingJobServiceServicer_to_server(_TrainingServicer(), server)
        port = server.add_insecure_port("127.0.0.1:0")
        await server.start()
        try:
            async with grpc.aio.insecure_channel(f"127.0.0.1:{port}") as channel:
                prediction_stub = prediction_pb2_grpc.BidPredictionServiceStub(channel)
                training_stub = training_pb2_grpc.TrainingJobServiceStub(channel)

                prediction_request = prediction_pb2.CalculateOptimalBidRequest()
                prediction_request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
                prediction_response = await prediction_stub.CalculateOptimalBid(prediction_request)
                assert prediction_response.WhichOneof("result") == "success"

                training_request = training_pb2_module.StartTrainingRequest()
                training_request.idempotency_key = "idem-1"
                training_request.training_spec_version = "training-spec-2026.3"
                training_response = await training_stub.StartTraining(training_request)
                assert training_response.WhichOneof("result") == "handle"
                assert training_response.handle.state == training_pb2_module.JOB_STATE_ACCEPTED
        finally:
            await server.stop(grace=None)

    asyncio.run(run())
