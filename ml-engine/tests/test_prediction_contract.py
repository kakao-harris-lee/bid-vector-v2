"""M2/2B — `BidPredictionService` provider 계약 test.

Python 쪽 fake servicer(생성 `BidPredictionServiceServicer`의 최소 구현, 같은
`contracts/testdata/prediction/*.binpb`를 답으로 낸다)가 계약 규칙(fail-closed·oneof·
3후보·`Unmeasurable` 두 사유 분리)을 지키는지 확인한다. servicer 메서드를 **직접 호출**한다
(scope.md 「구현 순서」 5의 두 선택지 중 하나) — 실제 socket 배선·deadline·cancel 은 2D
몫이다(ADR 0010 §6, scope.md 「만들지 않는 것」).

`ml-engine/tests/conftest.py`(2A)는 `common.proto`·`error.proto`만 생성한다. 이 slice의
`scope.md` in_scope는 `ml-engine/tests/test_prediction_contract.py` **파일 하나**만 지정하고
`conftest.py`(`tests/**`가 아니다)는 범위 밖이다 — 이 파일은 `conftest.py`를 편집하지 않고
독립 module-scope fixture로 `features.proto`·`prediction.proto`(+ `grpc_python_out`)까지
생성한다. Kotlin 쪽 `ml-contract`(2A included build)와 같은 `.proto` 단일 출처
(`contracts/proto`)를 쓴다(ADR 0003 D-1).
"""

from __future__ import annotations

import sys
import tempfile
from collections.abc import Iterator
from decimal import Decimal, InvalidOperation
from pathlib import Path

import pytest
from grpc_tools import protoc

_REPO_ROOT = Path(__file__).resolve().parents[2]
_PROTO_ROOT = _REPO_ROOT / "contracts" / "proto"
_TESTDATA_ROOT = _REPO_ROOT / "contracts" / "testdata" / "prediction"
_PROTO_FILES = (
    "bidvector/ml/v1/common.proto",
    "bidvector/ml/v1/error.proto",
    "bidvector/ml/v1/features.proto",
    "bidvector/ml/v1/prediction.proto",
)


def _read(name: str) -> bytes:
    return (_TESTDATA_ROOT / name).read_bytes()


# ---- 생성 stub — 이 module 전용(conftest.py 를 건드리지 않는다) ----


@pytest.fixture(scope="module")
def generated_stub_path() -> Iterator[Path]:
    """`grpc_tools.protoc`로 `contracts/proto`를 임시 디렉터리에 생성한다(`--python_out` +
    `--grpc_python_out`). 생성물은 module 종료 시 삭제된다 — VCS 에도 리포지터리 안에도
    남지 않는다(D-M2-3 (a)와 같은 관례)."""
    with tempfile.TemporaryDirectory(prefix="bidvector-ml-contract-py-2b-") as tmp:
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
def common_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import common_pb2 as module

    return module


@pytest.fixture(scope="module")
def error_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import error_pb2 as module

    return module


@pytest.fixture(scope="module")
def features_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import features_pb2 as module

    return module


@pytest.fixture(scope="module")
def prediction_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import prediction_pb2 as module

    return module


@pytest.fixture(scope="module")
def prediction_pb2_grpc(generated_stub_path: Path):
    from bidvector.ml.v1 import prediction_pb2_grpc as module

    return module


# ---- fake servicer — testdata 를 그대로 답으로 낸다(scope.md 「구현 순서」 5) ----


@pytest.fixture()
def fixture_servicer(prediction_pb2_grpc, prediction_pb2):
    class _FixtureServicer(prediction_pb2_grpc.BidPredictionServiceServicer):
        def __init__(self) -> None:
            self.calculate_response: object | None = None
            self.metadata_response: object | None = None

        def CalculateOptimalBid(self, request, context):  # noqa: N802 — grpc 생성 시그니처
            assert self.calculate_response is not None, "이 test 는 CalculateOptimalBid 응답을 배선하지 않았다"
            return self.calculate_response

        def GetModelMetadata(self, request, context):  # noqa: N802
            assert self.metadata_response is not None, "이 test 는 GetModelMetadata 응답을 배선하지 않았다"
            return self.metadata_response

    return _FixtureServicer()


# ---- ⑨ fake servicer 직접 호출 — 3후보·순서·라벨 고정 ----


def test_fake_servicer_returns_three_ordered_candidates(fixture_servicer, prediction_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    fixture_servicer.calculate_response = response

    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))

    actual = fixture_servicer.CalculateOptimalBid(request, context=None)

    assert actual.WhichOneof("result") == "success"
    labels = [candidate.label for candidate in actual.success.candidates]
    assert labels == [
        prediction_pb2.CANDIDATE_LABEL_CONSERVATIVE,
        prediction_pb2.CANDIDATE_LABEL_BASE,
        prediction_pb2.CANDIDATE_LABEL_AGGRESSIVE,
    ]


def test_fake_servicer_get_model_metadata_returns_promoted_release(fixture_servicer, prediction_pb2):
    response = prediction_pb2.GetModelMetadataResponse()
    response.ParseFromString(_read("get_model_metadata_response.binpb"))
    fixture_servicer.metadata_response = response

    request = prediction_pb2.GetModelMetadataRequest()
    request.envelope.request_id = "req-2b-01"
    request.envelope.correlation_id = "corr-2b-01"

    actual = fixture_servicer.GetModelMetadata(request, context=None)

    assert actual.WhichOneof("result") == "metadata"
    assert actual.metadata.promoted.release_id == "release-2026-09-01"


# ---- 후보 3 고정 — 개수 위반은 거부 ----


def test_success_testdata_has_exactly_three_ordered_candidates(prediction_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    assert _has_three_ordered_candidates(prediction_pb2, response.success)


def test_two_candidates_violates_the_contract_invariant(prediction_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    del response.success.candidates[2]
    assert not _has_three_ordered_candidates(prediction_pb2, response.success)


# ---- 후보 origin 은 항상 RECOMMENDED(scope.md ④, verifier r1 F-2) ----


def test_success_testdata_candidates_are_all_recommended_origin(prediction_pb2, common_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    assert _candidates_have_recommended_origin(response.success, common_pb2)


def test_one_observed_origin_candidate_violates_the_contract_invariant(prediction_pb2, common_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    response.success.candidates[0].origin = common_pb2.BID_RATE_ORIGIN_OBSERVED
    assert not _candidates_have_recommended_origin(response.success, common_pb2)


# ---- Unmeasurable 두 사유 구분 ----


def test_unmeasurable_reasons_differ(prediction_pb2, error_pb2):
    untrained = prediction_pb2.CalculateOptimalBidResponse()
    untrained.ParseFromString(_read("calculate_optimal_bid_response_unmeasurable_untrained_segment.binpb"))
    insufficient = prediction_pb2.CalculateOptimalBidResponse()
    insufficient.ParseFromString(_read("calculate_optimal_bid_response_unmeasurable_insufficient_samples.binpb"))

    assert untrained.WhichOneof("result") == "unmeasurable"
    assert insufficient.WhichOneof("result") == "unmeasurable"
    assert untrained.unmeasurable.reason == error_pb2.UNMEASURABLE_REASON_UNTRAINED_SEGMENT
    assert insufficient.unmeasurable.reason == error_pb2.UNMEASURABLE_REASON_INSUFFICIENT_SAMPLES
    assert untrained.unmeasurable.reason != insufficient.unmeasurable.reason


def test_application_failure_testdata_is_unsupported_schema_and_not_retryable(prediction_pb2, error_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_failure_unsupported_schema.binpb"))

    assert response.WhichOneof("result") == "failure"
    assert response.failure.code == error_pb2.FAILURE_CODE_UNSUPPORTED_SCHEMA
    assert response.failure.retryable is False


# ---- release 일치 규칙 — client 집행 규칙을 순수 함수로(2A ⑥ 인계, Kotlin 쪽과 대칭) ----


def test_exact_release_selector_requires_exact_match(prediction_pb2, common_pb2):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    selector = request.envelope.model_release_selector
    assert selector.WhichOneof("selector") == "exact_release"

    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    release = response.success.release
    mismatched = prediction_pb2.ModelRelease()
    mismatched.CopyFrom(release)
    mismatched.release_id = "other-release-id"

    assert _release_satisfies_selector(selector, release, promoted=None)
    assert not _release_satisfies_selector(selector, mismatched, promoted=None)


def test_latest_promoted_selector_requires_match_with_get_model_metadata(prediction_pb2, common_pb2):
    selector = common_pb2.ModelReleaseSelector()
    selector.latest_promoted.SetInParent()

    metadata_response = prediction_pb2.GetModelMetadataResponse()
    metadata_response.ParseFromString(_read("get_model_metadata_response.binpb"))
    promoted = metadata_response.metadata.promoted
    mismatched = prediction_pb2.ModelRelease()
    mismatched.CopyFrom(promoted)
    mismatched.release_id = "other-release-id"

    assert _release_satisfies_selector(selector, promoted, promoted)
    assert not _release_satisfies_selector(selector, mismatched, promoted)


# ---- UNSPECIFIED·정의 밖 정수 거부(fail-closed, proto3 open enum) ----


def test_optimization_objective_unspecified_is_rejected(prediction_pb2, features_pb2):
    assert not _is_acceptable_objective(features_pb2, features_pb2.OPTIMIZATION_OBJECTIVE_UNSPECIFIED)


def test_optimization_objective_undefined_integer_is_rejected(prediction_pb2, features_pb2):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    request.objective = 99  # proto3 open enum — 파싱은 통과하지만 정의 밖 값이다.
    assert not _is_acceptable_objective(features_pb2, request.objective)


def test_valid_objective_is_accepted(prediction_pb2, features_pb2):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    assert _is_acceptable_objective(features_pb2, request.objective)


def test_candidate_label_unspecified_is_rejected(prediction_pb2):
    assert not _is_acceptable_candidate_label(prediction_pb2, prediction_pb2.CANDIDATE_LABEL_UNSPECIFIED)


def test_candidate_label_undefined_integer_is_rejected(prediction_pb2):
    candidate = prediction_pb2.Candidate()
    candidate.label = 77
    assert not _is_acceptable_candidate_label(prediction_pb2, candidate.label)


# ---- Rate.fraction > 1 거부(D-2B-8, H-11 percent 관용 재유입 차단) ----


def test_bid_rate_fraction_above_one_is_rejected():
    assert _is_valid_bid_rate_fraction("0.9120")
    assert _is_valid_bid_rate_fraction("1.0000")
    assert not _is_valid_bid_rate_fraction("1.0001")
    # legacy `legal_floor_bid_rate` percent/fraction 겸용 반례(조사 01 H-11).
    assert not _is_valid_bid_rate_fraction("87.995")


def test_success_testdata_candidate_fractions_are_all_valid(prediction_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    for candidate in response.success.candidates:
        assert _is_valid_bid_rate_fraction(candidate.bid_rate.fraction)


# ---- decimal string 정규형(Kotlin ContractFractionRules.isNormalizedFraction 과 대칭,
# verifier r1 F-3) — Rate 셋 + decimal 넷 ----


def test_exponential_notation_is_rejected_for_bid_rate_and_weight():
    # verifier r1 변이 T7 — `bid_rate="8.87E-1"`·`weight="5.2E-1"`가 값으로는 [0,1] 안이라
    # 범위 검사만으로는 통과했었다. 정규형 검사를 선행 조건으로 걸어 막는다.
    assert not _is_valid_bid_rate_fraction("8.87E-1")
    assert not _is_normalized_fraction("5.2E-1")
    assert not _is_normalized_fraction("8.87E-1")


def test_testdata_rate_fields_are_all_normalized(prediction_pb2):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    for sample in request.competition_samples:
        assert _is_normalized_fraction(sample.observed_bid_rate.fraction)
        if sample.HasField("award_rate"):
            assert _is_normalized_fraction(sample.award_rate.fraction)

    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    for candidate in response.success.candidates:
        assert _is_normalized_fraction(candidate.bid_rate.fraction)


def test_testdata_decimal_string_fields_are_all_normalized(prediction_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    for candidate in response.success.candidates:
        assert _is_normalized_fraction(candidate.weight.fraction)
    assert _is_normalized_fraction(response.success.fitness.score)
    assert _is_normalized_fraction(response.success.uncertainty.dispersion)
    assert _is_normalized_fraction(response.success.uncertainty.estimate_margin)


def test_nan_fraction_is_rejected():
    # Kotlin 대칭(verifier r1 F-6) — `Decimal("NaN")`은 생성은 되지만 뒤의 범위 비교가
    # `InvalidOperation`을 던진다(수정 전 버그). `_is_normalized_fraction`이 그 앞에서
    # 명시적으로 걸러 예외 없이 `False`를 낸다.
    assert not _is_valid_bid_rate_fraction("NaN")
    assert not _is_normalized_fraction("NaN")


def test_fraction_with_surrounding_whitespace_is_rejected():
    # Kotlin 대칭(verifier r1 F-6) — `Decimal(" 0.5 ")`는 파싱되지만(Python이 공백을
    # 허용) `BigDecimal(" 0.5 ")`는 예외를 던진다(Java는 공백을 허용하지 않음). 정규형
    # 검사(재직렬화 문자열과의 완전 일치)가 공백을 실측하지 못한 차이로 잡는다.
    assert not _is_valid_bid_rate_fraction(" 0.5 ")
    assert not _is_normalized_fraction(" 0.5 ")


# ---- Uncertainty.sample_size >= 1(우회 후보 (3)) ----


def test_success_with_zero_sample_size_violates_the_contract_invariant(prediction_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    response.success.uncertainty.sample_size = 0
    assert not _is_acceptable_success(response.success)


def test_success_testdata_sample_size_is_at_least_one(prediction_pb2):
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(_read("calculate_optimal_bid_response_success.binpb"))
    assert _is_acceptable_success(response.success)


# ---- FeatureInputs fact oneof 미설정 거부 ----


def test_feature_inputs_with_all_facts_set_is_acceptable(prediction_pb2):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    assert _is_acceptable_feature_inputs(request.features)


def test_feature_inputs_rejects_unset_base_amount_fact(prediction_pb2):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    request.features.ClearField("base_amount")
    assert request.features.base_amount.WhichOneof("fact") is None
    assert not _is_acceptable_feature_inputs(request.features)


def test_feature_inputs_rejects_unset_category_code_fact(prediction_pb2):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    request.features.ClearField("category_code")
    assert not _is_acceptable_feature_inputs(request.features)


def test_feature_inputs_rejects_unset_agency_id_fact(prediction_pb2):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    request.features.ClearField("agency_id")
    assert not _is_acceptable_feature_inputs(request.features)


def test_feature_inputs_rejects_unset_base_amount_provenance_label_fact(prediction_pb2):
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_read("calculate_optimal_bid_request.binpb"))
    request.features.ClearField("base_amount_provenance_label")
    assert not _is_acceptable_feature_inputs(request.features)


# ---- round-trip(⑦, testdata 바이트 ↔ 생성 타입 — Kotlin 쪽과 대칭) ----


def test_calculate_optimal_bid_request_round_trips(prediction_pb2):
    original = _read("calculate_optimal_bid_request.binpb")
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(original)
    assert request.SerializeToString(deterministic=True) == original


def test_calculate_optimal_bid_response_success_round_trips(prediction_pb2):
    original = _read("calculate_optimal_bid_response_success.binpb")
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_calculate_optimal_bid_response_unmeasurable_untrained_segment_round_trips(prediction_pb2):
    original = _read("calculate_optimal_bid_response_unmeasurable_untrained_segment.binpb")
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_calculate_optimal_bid_response_unmeasurable_insufficient_samples_round_trips(prediction_pb2):
    original = _read("calculate_optimal_bid_response_unmeasurable_insufficient_samples.binpb")
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_calculate_optimal_bid_response_failure_round_trips(prediction_pb2):
    original = _read("calculate_optimal_bid_response_failure_unsupported_schema.binpb")
    response = prediction_pb2.CalculateOptimalBidResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


def test_get_model_metadata_response_round_trips(prediction_pb2):
    original = _read("get_model_metadata_response.binpb")
    response = prediction_pb2.GetModelMetadataResponse()
    response.ParseFromString(original)
    assert response.SerializeToString(deterministic=True) == original


# ---- 계약이 요구하는 거부 규칙 — 순수 함수(Kotlin `PredictionContractTest`와 대칭). 실제
# Python validation 구현은 5E 몫이고, 여기서는 test 가 그 규칙을 문서화·고정한다
# (scope.md 「구현 순서」 4). ----


def _has_three_ordered_candidates(prediction_pb2, success) -> bool:
    labels = [candidate.label for candidate in success.candidates]
    return labels == [
        prediction_pb2.CANDIDATE_LABEL_CONSERVATIVE,
        prediction_pb2.CANDIDATE_LABEL_BASE,
        prediction_pb2.CANDIDATE_LABEL_AGGRESSIVE,
    ]


def _candidates_have_recommended_origin(success, common_pb2) -> bool:
    return all(candidate.origin == common_pb2.BID_RATE_ORIGIN_RECOMMENDED for candidate in success.candidates)


def _release_satisfies_selector(selector, response_release, promoted) -> bool:
    which = selector.WhichOneof("selector")
    if which == "exact_release":
        return (
            response_release.release_id == selector.exact_release.release_id
            and response_release.artifact_checksum == selector.exact_release.artifact_checksum
        )
    if which == "latest_promoted":
        return (
            promoted is not None
            and response_release.release_id == promoted.release_id
            and response_release.artifact_checksum == promoted.artifact_checksum
        )
    return False


def _is_acceptable_objective(features_pb2, value: int) -> bool:
    known_values = {number for _, number in features_pb2.OptimizationObjective.items()}
    return value != features_pb2.OPTIMIZATION_OBJECTIVE_UNSPECIFIED and value in known_values


def _is_acceptable_candidate_label(prediction_pb2, value: int) -> bool:
    known_values = {number for _, number in prediction_pb2.CandidateLabel.items()}
    return value != prediction_pb2.CANDIDATE_LABEL_UNSPECIFIED and value in known_values


def _is_normalized_fraction(fraction: str) -> bool:
    """Kotlin `ContractFractionRules.isNormalizedFraction`(2A가 세우고 2B가 재사용, scale
    보존·지수 표기 거부)과 대칭인 Python 술어(verifier r1 F-3). `Decimal`은 Java의
    `BigDecimal`과 달리 앞뒤 공백과 `NaN`/`Infinity`를 파싱하므로(F-6 비대칭의 원인),
    재직렬화한 문자열이 입력과 완전히 같은지 비교해 그 차이를 없앤다 — 공백이 섞이면
    재직렬화 결과에 공백이 없어 불일치, `NaN`은 `is_nan()`으로 명시 거부한다."""
    if not fraction or "e" in fraction or "E" in fraction:
        return False
    try:
        value = Decimal(fraction)
    except InvalidOperation:
        return False
    if value.is_nan() or value.is_infinite():
        return False
    return format(value, "f") == fraction


def _is_valid_bid_rate_fraction(fraction: str) -> bool:
    if not _is_normalized_fraction(fraction):
        return False
    value = Decimal(fraction)
    return Decimal("0") <= value <= Decimal("1")


def _is_acceptable_success(success) -> bool:
    return success.uncertainty.sample_size >= 1


def _is_acceptable_feature_inputs(features) -> bool:
    return (
        features.base_amount.WhichOneof("fact") is not None
        and features.category_code.WhichOneof("fact") is not None
        and features.agency_id.WhichOneof("fact") is not None
        and features.base_amount_provenance_label.WhichOneof("fact") is not None
    )
