"""RED — Kotlin 소비자 규칙 미러(D-5E2-7, scope.md ⑦). 정본은 Kotlin 코드다
(`adapters/src/main/kotlin/bidvector/adapters/ml/{ReleaseShapeValidation,ReleaseCheck,
ParsedSuccessFields,FractionRules,CandidateShapeValidation}.kt`) — 이 모듈은 그 다섯
규칙을 Python 으로 옮겨 실 servicer 응답(2F testdata 요청 + SUPPORTED schema + 완성
case 정책)에 적용한다. 미러가 어긋나면 Kotlin 이 옳다.

교차 언어 실서버 통합(실 Kotlin ↔ 실 Python)은 이 test 의 범위가 아니다
(`OPEN-5E2-CROSSLANG-REAL-SERVER`, 6C) — 여기는 같은 프로세스 안에서 규칙만 미러한다.
"""

from __future__ import annotations

import tempfile
from decimal import Decimal, InvalidOperation
from pathlib import Path

import yaml

from ml_engine.contracts import common_pb2, prediction_pb2
from ml_engine.inference.policy import InferencePolicy, load_inference_policy
from ml_engine.serving.prediction import BidPredictionServicer
from ml_engine.serving.readiness import PreloadOutcome, ReadinessGate
from ml_engine.serving.runtime import PredictionRuntime, build_derived_release

_REPO_ROOT = Path(__file__).resolve().parents[3]
_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_TESTDATA_REQUEST = (
    _REPO_ROOT
    / "contracts"
    / "testdata"
    / "prediction"
    / "calculate_optimal_bid_request.binpb"
)
_SHIPPED_POLICY_PATH = _ML_ENGINE_ROOT / "policy" / "inference-v1.yaml"

# `_policy_support.py`(tests/inference)와 같은 placeholder — `OPEN-5D2-POLICY-VALUES`
# 값 결정이 아니다(5D-3 test 가 쓰는 값과 동일, tests/serving 은 tests/inference 의
# 비공개 helper 를 import 하지 않는다 — 디렉터리 경계를 넘지 않는다는 관례).
_AGENCY_SAMPLE_THRESHOLD_PLACEHOLDER = 1

# testdata 요청의 competition_samples 는 2건뿐이고 그중 예비가격 추첨 관측(reserve_draw)이
# 있는 것은 1건이다 — `policy/inference-v1.yaml`의 `reserve.min_reserve_records: 8`에
# 못 미쳐 실 엔진이 `Unmeasurable(INSUFFICIENT_SAMPLES)`를 낸다(실측). 이 test 는 후보
# 3건의 형태·release·decimal 정규형을 Kotlin 규칙으로 미러해야 하므로 `Success`가
# 필요하다 — testdata 의 **유일한 완비 표본**(reserve_draw·observed_bid_rate·base_amount
# 전부 있는 competition_samples[0])을 복제해 개수만 채운다(값을 새로 짓지 않는다, 그
# 표본 자체는 authoritative testdata). `dataset-extract.md`가 금지하는 "가까운 값으로
# 대체"가 아니라 같은 표본의 반복이다.
_MIN_RESERVE_RECORDS_FOR_SUCCESS = 10


def _completed_case_inference_policy() -> InferencePolicy:
    raw_values = dict(yaml.safe_load(_SHIPPED_POLICY_PATH.read_text(encoding="utf-8")))
    raw_values.setdefault(
        "assessment.agency_sample_threshold", _AGENCY_SAMPLE_THRESHOLD_PLACEHOLDER
    )
    with tempfile.TemporaryDirectory(
        prefix="bidvector-inference-policy-kotlin-parity-"
    ) as tmp:
        temp_path = Path(tmp) / "inference-v1.yaml"
        temp_path.write_text(yaml.safe_dump(raw_values), encoding="utf-8")
        policy = load_inference_policy(temp_path)
    assert isinstance(policy, InferencePolicy), f"완성 case 정책 로드 실패: {policy!r}"
    return policy


def _success_request() -> prediction_pb2.CalculateOptimalBidRequest:
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_TESTDATA_REQUEST.read_bytes())
    request.envelope.feature_schema_version = "award-rate-features-v2"
    request.envelope.model_release_selector.ClearField("exact_release")
    request.envelope.model_release_selector.latest_promoted.SetInParent()
    complete_sample = request.competition_samples[0]
    del request.competition_samples[:]
    for _ in range(_MIN_RESERVE_RECORDS_FOR_SUCCESS):
        sample = request.competition_samples.add()
        sample.CopyFrom(complete_sample)
    return request


def _servicer_and_runtime() -> tuple[BidPredictionServicer, PredictionRuntime]:
    policy = _completed_case_inference_policy()
    release = build_derived_release(policy, "sha-kotlin-parity-test")
    runtime = PredictionRuntime(
        policy=policy,
        release=release,
        supported_feature_schema_versions=("award-rate-features-v2",),
    )
    gate = ReadinessGate.from_preload(
        [PreloadOutcome(name="inference", ok=True, reason=None)]
    )
    servicer = BidPredictionServicer(gate, ("award-rate-features-v2",), runtime)
    return servicer, runtime


class _ActiveContext:
    def is_active(self) -> bool:
        return True


# ---- Kotlin 규칙 미러(정본은 Kotlin 코드, docstring 이 파일 이름으로 가리킨다) ----


def _is_normalized_fraction(value: str) -> bool:
    """`FractionRules.kt::isNormalizedFraction` 미러."""
    if not value or any(char in ("e", "E") for char in value):
        return False
    try:
        parsed = Decimal(value)
    except InvalidOperation:
        return False
    if not parsed.is_finite():
        return False
    return format(parsed, "f") == value


def _has_valid_derived_release_shape(release: prediction_pb2.ModelRelease) -> bool:
    """`ReleaseShapeValidation.kt::hasValidReleaseShape`(DERIVED 분기) 미러 — 넷
    비공백(dataset_id 제외) + `release_id` 접두 `distribution/`."""
    if release.release_kind != prediction_pb2.RELEASE_KIND_DERIVED:
        return False
    fields = (
        release.release_id,
        release.artifact_checksum,
        release.feature_schema_version,
        release.code_version,
    )
    all_non_blank = all(field.strip() for field in fields)
    return all_non_blank and release.release_id.startswith("distribution/")


def _release_satisfies_latest_promoted(
    response_release: prediction_pb2.ModelRelease,
    promoted: prediction_pb2.ModelRelease | None,
) -> bool:
    """`ReleaseCheck.kt::releaseSatisfiesSelector`(LATEST_PROMOTED 분기) 미러."""
    if promoted is None:
        return False
    return (
        response_release.release_id == promoted.release_id
        and response_release.artifact_checksum == promoted.artifact_checksum
    )


def _has_exactly_three_ordered_candidate_labels(
    success: prediction_pb2.Success,
) -> bool:
    """`ParsedSuccessFields.kt::hasExactlyThreeOrderedCandidates` 미러."""
    labels = [candidate.label for candidate in success.candidates]
    return labels == [
        prediction_pb2.CANDIDATE_LABEL_CONSERVATIVE,
        prediction_pb2.CANDIDATE_LABEL_BASE,
        prediction_pb2.CANDIDATE_LABEL_AGGRESSIVE,
    ]


def _has_ordered_candidate_rates(success: prediction_pb2.Success) -> bool:
    """`CandidateShapeValidation.kt::hasOrderedCandidateRates` 미러 — conservative
    ≤ base ≤ aggressive."""
    rates = [Decimal(candidate.bid_rate.fraction) for candidate in success.candidates]
    if len(rates) != 3:
        return True
    return rates[0] <= rates[1] <= rates[2]


def _is_acceptable_success_shape(success: prediction_pb2.Success) -> bool:
    """`ParsedSuccessFields.kt::isAcceptableSuccessShape` 미러 — 다섯 검사 전부."""
    checks = [
        success.uncertainty.sample_size >= 1,
        _has_exactly_three_ordered_candidate_labels(success),
        all(
            candidate.origin == common_pb2.BID_RATE_ORIGIN_RECOMMENDED
            for candidate in success.candidates
        ),
        _has_valid_derived_release_shape(success.release),
        _has_ordered_candidate_rates(success),
    ]
    return all(checks)


# ---- test ----


def test_real_response_is_success() -> None:
    """전제 확인 — 이 test 파일의 나머지가 의미 있으려면 실 엔진이 Success 를 내야
    한다(사전 조사에서 testdata 원본 2건은 Unmeasurable 이었다)."""
    servicer, _runtime = _servicer_and_runtime()
    response = servicer.CalculateOptimalBid(_success_request(), _ActiveContext())
    assert response.WhichOneof("result") == "success"


def test_fraction_rules_accept_all_decimal_fields() -> None:
    servicer, _runtime = _servicer_and_runtime()
    response = servicer.CalculateOptimalBid(_success_request(), _ActiveContext())
    success = response.success
    fraction_strings = [
        success.fitness.score,
        success.uncertainty.dispersion,
        success.uncertainty.estimate_margin,
        success.diagnostics.shrinkage_weight.fraction,
        *(candidate.bid_rate.fraction for candidate in success.candidates),
        *(candidate.weight.fraction for candidate in success.candidates),
    ]
    for value in fraction_strings:
        assert _is_normalized_fraction(value), f"정규형 위반: {value!r}"


def test_release_shape_is_valid_derived() -> None:
    servicer, _runtime = _servicer_and_runtime()
    response = servicer.CalculateOptimalBid(_success_request(), _ActiveContext())
    assert _has_valid_derived_release_shape(response.success.release)


def test_release_satisfies_latest_promoted_selector() -> None:
    servicer, runtime = _servicer_and_runtime()
    metadata_request = prediction_pb2.GetModelMetadataRequest()
    metadata_request.envelope.request_id = "req-meta"
    metadata_request.envelope.correlation_id = "corr-meta"
    metadata_response = servicer.GetModelMetadata(metadata_request, context=None)
    promoted = metadata_response.metadata.promoted

    response = servicer.CalculateOptimalBid(_success_request(), _ActiveContext())
    assert _release_satisfies_latest_promoted(response.success.release, promoted)
    # runtime.release 와도 동일해야 한다(D-5E2-1 「한 함수 한 입력」).
    assert response.success.release.release_id == runtime.release.release_id


def test_parsed_success_fields_shape_is_acceptable() -> None:
    servicer, _runtime = _servicer_and_runtime()
    response = servicer.CalculateOptimalBid(_success_request(), _ActiveContext())
    assert _is_acceptable_success_shape(response.success)
