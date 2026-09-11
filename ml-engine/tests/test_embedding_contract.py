"""M2/2E — `EmbeddingService` provider 계약 test.

Python 쪽 fake servicer(생성 `EmbeddingServiceServicer`의 최소 구현)가 요청 내용에 따라
**실제로 판단한다** — 빈/공백 텍스트·상한 초과·`TEXT_KIND_UNSPECIFIED`는
`ApplicationFailure(INVALID_REQUEST, retryable=False)`, 미지 `feature_schema_version`은
`UNSUPPORTED_SCHEMA`, 정상 요청은 `contracts/testdata/embedding/embed_text_response_success.binpb`의
L2 벡터 + 비공백 release를 낸다(scope.md 「구현 순서」 5). 실 servicer(M5)·실 client(4D-2)는
이 slice 밖이다.

`ml-engine/tests/conftest.py`(2A)는 `common.proto`·`error.proto`만 생성한다 — 이 slice의
`scope.md` in_scope는 `test_embedding_contract.py` **파일 하나**만 지정하고 `conftest.py`는
범위 밖이다(2B `test_prediction_contract.py`와 같은 관례) — 독립 module-scope fixture로
`features.proto`·`prediction.proto`·`embedding.proto`(+ `grpc_python_out`)까지 생성한다.
"""

from __future__ import annotations

import sys
import tempfile
from collections.abc import Iterator
from decimal import Decimal
from pathlib import Path

import pytest
from grpc_tools import protoc

_REPO_ROOT = Path(__file__).resolve().parents[2]
_PROTO_ROOT = _REPO_ROOT / "contracts" / "proto"
_TESTDATA_ROOT = _REPO_ROOT / "contracts" / "testdata" / "embedding"
_POLICY_FILE = _REPO_ROOT / "config" / "quality" / "contract-policy.properties"
_PROTO_FILES = (
    "bidvector/ml/v1/common.proto",
    "bidvector/ml/v1/error.proto",
    "bidvector/ml/v1/features.proto",
    "bidvector/ml/v1/prediction.proto",
    "bidvector/ml/v1/embedding.proto",
)


def _read(name: str) -> bytes:
    return (_TESTDATA_ROOT / name).read_bytes()


def _policy_value(key: str) -> str:
    """`contract-policy.properties`를 직접 읽는다(`test_contract_resilience.py`와 같은
    관례 — Kotlin `ContractPolicySupport`는 다른 언어 런타임에서 보이지 않는다)."""
    for line in _POLICY_FILE.read_text(encoding="utf-8").splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            continue
        if "=" in stripped:
            key_found, _, value = stripped.partition("=")
            if key_found.strip() == key:
                return value.strip()
    raise AssertionError(f"정책 키 '{key}' 가 '{_POLICY_FILE}' 에 없다")


# ---- 생성 stub — 이 module 전용(conftest.py 를 건드리지 않는다) ----


@pytest.fixture(scope="module")
def generated_stub_path() -> Iterator[Path]:
    """`grpc_tools.protoc`로 `contracts/proto`를 임시 디렉터리에 생성한다(`--python_out` +
    `--grpc_python_out`). 생성물은 module 종료 시 삭제된다 — VCS 에도 리포지터리 안에도
    남지 않는다(D-M2-3 (a)와 같은 관례)."""
    with tempfile.TemporaryDirectory(prefix="bidvector-ml-contract-py-2e-") as tmp:
        out_dir = Path(tmp)
        args = [
            "grpc_tools.protoc",
            f"--proto_path={_PROTO_ROOT}",
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
def error_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import error_pb2 as module

    return module


@pytest.fixture(scope="module")
def embedding_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import embedding_pb2 as module

    return module


@pytest.fixture(scope="module")
def embedding_pb2_grpc(generated_stub_path: Path):
    from bidvector.ml.v1 import embedding_pb2_grpc as module

    return module


# ---- fake servicer — 요청 내용을 실제로 판단한다(scope.md 「구현 순서」 5) ----


@pytest.fixture()
def fixture_servicer(embedding_pb2_grpc, embedding_pb2, error_pb2):
    max_chars = int(_policy_value("embedding.text.max-chars"))
    success_bytes = _read("embed_text_response_success.binpb")
    metadata_bytes = _read("get_embedding_metadata_response.binpb")

    class _FixtureServicer(embedding_pb2_grpc.EmbeddingServiceServicer):
        def EmbedText(self, request, context):  # noqa: N802 — grpc 생성 시그니처
            response = embedding_pb2.EmbedTextResponse()
            if not _is_acceptable_text_kind(embedding_pb2, request.kind):
                response.failure.code = error_pb2.FAILURE_CODE_INVALID_REQUEST
                response.failure.retryable = False
                response.failure.detail_code = "TEXT_KIND_UNSPECIFIED"
                return response
            if not request.text.strip():
                response.failure.code = error_pb2.FAILURE_CODE_INVALID_REQUEST
                response.failure.retryable = False
                response.failure.detail_code = "TEXT_EMPTY"
                return response
            if len(request.text) > max_chars:
                response.failure.code = error_pb2.FAILURE_CODE_INVALID_REQUEST
                response.failure.retryable = False
                response.failure.detail_code = "TEXT_TOO_LONG"
                return response
            if request.envelope.feature_schema_version != "text-synthesis-v1":
                response.failure.code = error_pb2.FAILURE_CODE_UNSUPPORTED_SCHEMA
                response.failure.retryable = False
                response.failure.detail_code = "FEATURE_SCHEMA_VERSION_NOT_SUPPORTED"
                return response
            response.ParseFromString(success_bytes)
            return response

        def GetEmbeddingMetadata(self, request, context):  # noqa: N802
            response = embedding_pb2.GetEmbeddingMetadataResponse()
            response.ParseFromString(metadata_bytes)
            return response

    return _FixtureServicer()


# ---- 정상 요청 — L2 벡터 + release 비공백 ----


def test_fake_servicer_embed_text_returns_l2_vector_with_release(fixture_servicer, embedding_pb2):
    request = embedding_pb2.EmbedTextRequest()
    request.ParseFromString(_read("embed_text_request.binpb"))

    actual = fixture_servicer.EmbedText(request, context=None)

    assert actual.WhichOneof("result") == "success"
    epsilon = Decimal(_policy_value("embedding.norm.epsilon"))
    assert _is_acceptable_embedding(actual.success, epsilon)
    assert _is_model_release_non_blank(actual.success.release)


def test_fake_servicer_get_embedding_metadata_returns_promoted_release_and_dimension(
    fixture_servicer, embedding_pb2
):
    request = embedding_pb2.GetEmbeddingMetadataRequest()
    request.envelope.request_id = "req-2e-01"
    request.envelope.correlation_id = "corr-2e-01"

    actual = fixture_servicer.GetEmbeddingMetadata(request, context=None)

    assert actual.WhichOneof("result") == "metadata"
    assert actual.metadata.promoted.release_id == "release-2026-09-01"
    assert actual.metadata.dimension > 0


# ---- fail-closed — 빈/공백 텍스트·상한 초과·UNSPECIFIED kind (④, 우회 후보) ----


def test_fake_servicer_rejects_empty_text(fixture_servicer, embedding_pb2, error_pb2):
    request = embedding_pb2.EmbedTextRequest()
    request.ParseFromString(_read("embed_text_request.binpb"))
    request.text = ""

    actual = fixture_servicer.EmbedText(request, context=None)

    assert actual.WhichOneof("result") == "failure"
    assert actual.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert actual.failure.retryable is False


def test_fake_servicer_rejects_whitespace_only_text(fixture_servicer, embedding_pb2, error_pb2):
    request = embedding_pb2.EmbedTextRequest()
    request.ParseFromString(_read("embed_text_request.binpb"))
    request.text = "   \n\t  "

    actual = fixture_servicer.EmbedText(request, context=None)

    assert actual.WhichOneof("result") == "failure"
    assert actual.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST


def test_fake_servicer_rejects_text_over_max_chars(fixture_servicer, embedding_pb2, error_pb2):
    request = embedding_pb2.EmbedTextRequest()
    request.ParseFromString(_read("embed_text_request.binpb"))
    max_chars = int(_policy_value("embedding.text.max-chars"))
    request.text = "가" * (max_chars + 1)

    actual = fixture_servicer.EmbedText(request, context=None)

    assert actual.WhichOneof("result") == "failure"
    assert actual.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert actual.failure.retryable is False


def test_fake_servicer_rejects_unspecified_text_kind(fixture_servicer, embedding_pb2, error_pb2):
    request = embedding_pb2.EmbedTextRequest()
    request.ParseFromString(_read("embed_text_request.binpb"))
    request.kind = embedding_pb2.TEXT_KIND_UNSPECIFIED

    actual = fixture_servicer.EmbedText(request, context=None)

    assert actual.WhichOneof("result") == "failure"
    assert actual.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert actual.failure.retryable is False


def test_fake_servicer_rejects_unsupported_feature_schema_version(fixture_servicer, embedding_pb2, error_pb2):
    request = embedding_pb2.EmbedTextRequest()
    request.ParseFromString(_read("embed_text_request.binpb"))
    request.envelope.feature_schema_version = "unsupported-schema-v9"

    actual = fixture_servicer.EmbedText(request, context=None)

    assert actual.WhichOneof("result") == "failure"
    assert actual.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_SCHEMA
    assert actual.failure.retryable is False


# ---- testdata 실패 표본 자체 ----


def test_unsupported_schema_failure_testdata_is_not_retryable(embedding_pb2, error_pb2):
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(_read("embed_text_response_failure_unsupported_schema.binpb"))
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_SCHEMA
    assert response.failure.retryable is False


def test_invalid_request_empty_text_failure_testdata_is_not_retryable(embedding_pb2, error_pb2):
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(_read("embed_text_response_failure_invalid_request_empty_text.binpb"))
    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_INVALID_REQUEST
    assert response.failure.retryable is False


# ---- D-2E-1 형태 불변식 — values 개수 == dimension, L2 norm ----


def test_success_testdata_embedding_is_acceptable(embedding_pb2):
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(_read("embed_text_response_success.binpb"))
    epsilon = Decimal(_policy_value("embedding.norm.epsilon"))
    assert _is_acceptable_embedding(response.success, epsilon)


def test_dimension_mismatch_violates_the_contract_invariant(embedding_pb2):
    # verifier r1 F-2(high) — 이전 판은 `del values[0]`로 원소를 "떼기만" 했다. 그러면
    # `dimension`(4)은 그대로인데 남은 3원소의 norm 도 함께 무너져(0.866) **norm 항에서
    # 먼저 걸리고 dimension 항은 확인력이 0**이었다(가드를 지워도 전건 통과 — 실측). 여기서는
    # `dimension` 필드는 testdata 원본 그대로(4) 두고 `values`만 L2 정규화된 **3원소**
    # (1/√3 씩, norm=1)로 바꿔 norm 항은 통과·dimension 항만 단독으로 걸리게 한다.
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(_read("embed_text_response_success.binpb"))
    normalized_three_elements = [float(Decimal(1) / Decimal(3).sqrt())] * 3
    del response.success.values[:]
    response.success.values.extend(normalized_three_elements)
    epsilon = Decimal(_policy_value("embedding.norm.epsilon"))

    # 자기 검증 — 이 3원소 자체가 정규화가 맞다는 것을 dimension 을 3으로 맞춘 사본으로
    # 먼저 확인한다(norm 항이 실제로 통과함을 증명해야 아래 단언이 dimension 항 단독의
    # 결과임을 믿을 수 있다).
    dimension_corrected = embedding_pb2.Embedding()
    dimension_corrected.CopyFrom(response.success)
    dimension_corrected.dimension = 3
    assert _is_acceptable_embedding(dimension_corrected, epsilon)

    assert not _is_acceptable_embedding(response.success, epsilon)


def test_embed_text_dimension_matches_metadata_dimension(embedding_pb2):
    # 설계 검토 (1)·scope.md ② — 「차원은 응답이 나르고 client 는
    # GetEmbeddingMetadata.dimension 과 대조(불일치 = 계약 위반)」의 실제 대응 test
    # (verifier r1 F-2 미구현 지적 반영, 이전 판에는 이 대조가 없었다).
    embed_response = embedding_pb2.EmbedTextResponse()
    embed_response.ParseFromString(_read("embed_text_response_success.binpb"))
    metadata_response = embedding_pb2.GetEmbeddingMetadataResponse()
    metadata_response.ParseFromString(_read("get_embedding_metadata_response.binpb"))
    assert _embedding_dimension_matches_metadata(embed_response.success, metadata_response.metadata)


def test_metadata_dimension_mismatch_must_be_rejected_by_client(embedding_pb2):
    embed_response = embedding_pb2.EmbedTextResponse()
    embed_response.ParseFromString(_read("embed_text_response_success.binpb"))
    metadata_response = embedding_pb2.GetEmbeddingMetadataResponse()
    metadata_response.ParseFromString(_read("get_embedding_metadata_response.binpb"))
    metadata_response.metadata.dimension = embed_response.success.dimension + 1
    assert not _embedding_dimension_matches_metadata(embed_response.success, metadata_response.metadata)


def test_unnormalized_vector_violates_the_contract_invariant(embedding_pb2):
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(_read("embed_text_response_success.binpb"))
    scaled = [value * 2.0 for value in response.success.values]
    del response.success.values[:]
    response.success.values.extend(scaled)
    epsilon = Decimal(_policy_value("embedding.norm.epsilon"))
    assert not _is_acceptable_embedding(response.success, epsilon)


# ---- UNSPECIFIED·정의 밖 정수 거부(fail-closed, proto3 open enum) ----


def test_text_kind_unspecified_is_rejected(embedding_pb2):
    assert not _is_acceptable_text_kind(embedding_pb2, embedding_pb2.TEXT_KIND_UNSPECIFIED)


def test_text_kind_undefined_integer_is_rejected(embedding_pb2):
    request = embedding_pb2.EmbedTextRequest()
    request.ParseFromString(_read("embed_text_request.binpb"))
    request.kind = 99  # proto3 open enum — 파싱은 통과하지만 정의 밖 값이다.
    assert not _is_acceptable_text_kind(embedding_pb2, request.kind)


def test_testdata_kind_is_accepted(embedding_pb2):
    request = embedding_pb2.EmbedTextRequest()
    request.ParseFromString(_read("embed_text_request.binpb"))
    assert _is_acceptable_text_kind(embedding_pb2, request.kind)


def test_vector_normalization_unspecified_is_rejected(embedding_pb2):
    assert not _is_acceptable_vector_normalization(embedding_pb2, embedding_pb2.VECTOR_NORMALIZATION_UNSPECIFIED)


def test_vector_normalization_undefined_integer_is_rejected(embedding_pb2):
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(_read("embed_text_response_success.binpb"))
    response.success.normalization = 99
    assert not _is_acceptable_vector_normalization(embedding_pb2, response.success.normalization)


# ---- release 공백 거부 ----


def test_success_testdata_release_is_non_blank(embedding_pb2):
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(_read("embed_text_response_success.binpb"))
    assert _is_model_release_non_blank(response.success.release)


@pytest.mark.parametrize(
    "component",
    ["release_id", "artifact_checksum", "feature_schema_version", "code_version", "dataset_id"],
)
def test_release_with_blank_component_violates_the_contract_invariant(embedding_pb2, component):
    # verifier r1 F-3(medium) — 이전 판은 `dataset_id` 하나만 변이했다. 4D-1
    # `SuccessShapeFailClosedTest`(Kotlin main)의 같은 규칙은 다섯 성분을 각각 덮는다 —
    # 이 slice도 같은 커버리지로 맞춘다(파라미터화, 성분당 1건).
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(_read("embed_text_response_success.binpb"))
    setattr(response.success.release, component, "")
    assert not _is_model_release_non_blank(response.success.release)


# ---- round-trip(⑦, testdata 바이트 ↔ 생성 타입 — Kotlin 쪽과 대칭) ----


def test_embed_text_request_round_trips(embedding_pb2):
    original = _read("embed_text_request.binpb")
    request = embedding_pb2.EmbedTextRequest()
    request.ParseFromString(original)
    assert request.SerializeToString(deterministic=True) == original


def test_embed_text_response_success_round_trips(embedding_pb2):
    original = _read("embed_text_response_success.binpb")
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_embed_text_response_failure_unsupported_schema_round_trips(embedding_pb2):
    original = _read("embed_text_response_failure_unsupported_schema.binpb")
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_embed_text_response_failure_invalid_request_empty_text_round_trips(embedding_pb2):
    original = _read("embed_text_response_failure_invalid_request_empty_text.binpb")
    response = embedding_pb2.EmbedTextResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_get_embedding_metadata_response_round_trips(embedding_pb2):
    original = _read("get_embedding_metadata_response.binpb")
    response = embedding_pb2.GetEmbeddingMetadataResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


# ---- 계약이 요구하는 거부 규칙 — 순수 함수(Kotlin `EmbeddingContractTest`와 대칭). 실제
# Python validation 구현은 5E 몫이고, 여기서는 test 가 그 규칙을 문서화·고정한다. ----


def _is_acceptable_text_kind(embedding_pb2, value: int) -> bool:
    known_values = {number for _, number in embedding_pb2.TextKind.items()}
    return value != embedding_pb2.TEXT_KIND_UNSPECIFIED and value in known_values


def _is_acceptable_vector_normalization(embedding_pb2, value: int) -> bool:
    known_values = {number for _, number in embedding_pb2.VectorNormalization.items()}
    return value != embedding_pb2.VECTOR_NORMALIZATION_UNSPECIFIED and value in known_values


def _is_acceptable_embedding(embedding_pb2_message, epsilon: Decimal) -> bool:
    """D-2E-1의 형태 불변식 — `len(values) == dimension`이고 L2 norm이 `1 ± epsilon` 안
    (Kotlin `isAcceptableEmbedding`과 대칭). `type(embedding_pb2_message)`의 module에서
    enum 서술자를 얻어 `VectorNormalization` 정의 부분집합을 직접 재계산한다 — 리터럴로
    굳히지 않는다."""
    values = list(embedding_pb2_message.values)
    if len(values) != embedding_pb2_message.dimension:
        return False
    normalization_enum = type(embedding_pb2_message).DESCRIPTOR.fields_by_name["normalization"].enum_type
    unspecified_number = normalization_enum.values_by_name["VECTOR_NORMALIZATION_UNSPECIFIED"].number
    known_numbers = {value.number for value in normalization_enum.values}
    if embedding_pb2_message.normalization == unspecified_number:
        return False
    if embedding_pb2_message.normalization not in known_numbers:
        return False
    sum_of_squares = sum(Decimal(str(value)) * Decimal(str(value)) for value in values)
    norm = sum_of_squares.sqrt()
    return abs(norm - Decimal(1)) <= epsilon


def _is_model_release_non_blank(release) -> bool:
    return bool(
        release.release_id.strip()
        and release.artifact_checksum.strip()
        and release.feature_schema_version.strip()
        and release.code_version.strip()
        and release.dataset_id.strip()
    )


def _embedding_dimension_matches_metadata(embedding, metadata) -> bool:
    """scope.md ②·설계 검토 (1) — `EmbedText` 응답 dimension 과 `GetEmbeddingMetadata`
    dimension 의 client 대조 규칙(Kotlin `embeddingDimensionMatchesMetadata`와 대칭)."""
    return embedding.dimension == metadata.dimension
