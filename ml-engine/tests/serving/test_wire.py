"""RED — `ml_engine.serving.wire`(scope.md ②, D-5E2-5·6). `map_kernel_result`가
`Success`·`Unmeasurable`을 각각 어떻게 옮기는지, 매핑 불변식 위반이 결과가 아니라
`MappingRejected`가 되는지 확인한다."""

from __future__ import annotations

from decimal import Decimal

import pytest

from ml_engine.contracts import common_pb2, error_pb2, prediction_pb2
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
from ml_engine.serving.wire import MappingRejected, _format_fraction, map_kernel_result


def _candidate(label: CandidateLabel, bid_rate: str, weight: str) -> Candidate:
    return Candidate(
        label=label,
        bid_rate=Decimal(bid_rate),
        weight=Decimal(weight),
        weight_policy_version="candidate-weight-v1",
    )


def _success(
    *,
    sample_size: int = 15,
    training_row_count: int = 0,
    bid_rate: str = "0.8500",
    dispersion: str = "0.0380",
) -> Success:
    return Success(
        candidates=(
            _candidate(CandidateLabel.CONSERVATIVE, bid_rate, "0.2400"),
            _candidate(CandidateLabel.BASE, "0.8750", "0.5200"),
            _candidate(CandidateLabel.AGGRESSIVE, "0.9000", "0.2400"),
        ),
        fitness=Decimal("0.6900"),  # type: ignore[arg-type]
        uncertainty=Uncertainty(
            sample_size=sample_size,
            dispersion=Decimal(dispersion),
            estimate_margin=Decimal("0.0210"),
            interval_source=IntervalSource.POSTERIOR_PREDICTIVE,
        ),
        diagnostics=Diagnostics(
            training_row_count=training_row_count,
            segment_support=SegmentSupport.GLOBAL,
            shrinkage_weight=Decimal("0.0000"),
            excluded_observations=0,
            agency_sample_count=0,
            agency_sample_below_threshold=False,
        ),
    )


def _release() -> prediction_pb2.ModelRelease:
    release = prediction_pb2.ModelRelease()
    release.release_id = "distribution/inference-v1"
    release.artifact_checksum = "sha256:" + "a" * 64
    release.feature_schema_version = "award-rate-features-v2"
    release.code_version = "ml-engine-test"
    release.dataset_id = ""
    release.release_kind = prediction_pb2.RELEASE_KIND_DERIVED
    return release


# ---- Success 매핑 ----


def test_maps_success_candidates_in_order_with_recommended_origin() -> None:
    mapped = map_kernel_result(_success(), _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    labels = [c.label for c in mapped.success.candidates]
    assert labels == [
        prediction_pb2.CANDIDATE_LABEL_CONSERVATIVE,
        prediction_pb2.CANDIDATE_LABEL_BASE,
        prediction_pb2.CANDIDATE_LABEL_AGGRESSIVE,
    ]
    assert all(
        c.origin == common_pb2.BID_RATE_ORIGIN_RECOMMENDED
        for c in mapped.success.candidates
    )


def test_maps_decimal_fields_to_normalized_fraction_strings() -> None:
    mapped = map_kernel_result(_success(), _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert mapped.success.candidates[0].bid_rate.fraction == "0.8500"
    assert mapped.success.uncertainty.dispersion == "0.0380"
    assert mapped.success.fitness.score == "0.6900"


def test_maps_uncertainty_interval_source() -> None:
    mapped = map_kernel_result(_success(), _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert (
        mapped.success.uncertainty.interval_source
        == prediction_pb2.INTERVAL_SOURCE_POSTERIOR_PREDICTIVE
    )


def test_release_is_copied_from_runtime_release_with_schema_echo() -> None:
    release = _release()
    mapped = map_kernel_result(_success(), release, "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert mapped.success.release.release_id == release.release_id
    assert mapped.success.release.artifact_checksum == release.artifact_checksum
    assert mapped.success.release.code_version == release.code_version
    assert mapped.success.release.release_kind == prediction_pb2.RELEASE_KIND_DERIVED
    assert mapped.success.release.feature_schema_version == "award-rate-features-v2"


def test_schema_echo_overrides_runtime_template_value() -> None:
    """verifier r1 L-3 — 지원 집합이 지금 하나뿐이라 요청 에코 값과 런타임 템플릿
    값이 항상 같아서, 위 test 는 echo 대입 줄이 삭제돼도 붉어지지 않는다(변이
    검증으로 확인). 이 test 는 템플릿과 에코에 **서로 다른** 값을 줘 결정적으로
    가른다 — `map_kernel_result`는 schema 값을 검증하지 않는 순수 함수라 이 값들이
    실제로 SUPPORTED 집합에 속할 필요가 없다."""
    release = _release()
    release.feature_schema_version = "template-value"
    mapped = map_kernel_result(_success(), release, "echoed-request-value")
    assert not isinstance(mapped, MappingRejected)
    assert mapped.success.release.feature_schema_version == "echoed-request-value"
    # 나머지 네 성분은 여전히 템플릿 것 그대로다 — echo 는 이 필드 하나만 덮어쓴다.
    assert mapped.success.release.release_id == release.release_id
    assert mapped.success.release.code_version == release.code_version


def test_release_template_is_not_mutated_across_two_mappings() -> None:
    """code-reviewer LOW/verifier r1 근거 — "공유 release 객체를 in-place 로 바꾸지
    않는다"는 설계 불변식(runtime.py·wire.py 문서화)의 회귀 test. 같은 `release`
    객체를 서로 다른 `feature_schema_version` 에코로 두 번 매핑해도 원본 객체
    (런타임 템플릿)는 물들지 않아야 한다 — 향후 `CopyFrom` 대신 인자로 받은
    `release` 에 직접 대입하는 리팩터로 회귀하면 이 test 가 잡는다."""
    release = _release()
    original_schema = release.feature_schema_version
    mapped_a = map_kernel_result(_success(), release, "echo-a")
    mapped_b = map_kernel_result(_success(), release, "echo-b")
    assert not isinstance(mapped_a, MappingRejected)
    assert not isinstance(mapped_b, MappingRejected)
    assert mapped_a.success.release.feature_schema_version == "echo-a"
    assert mapped_b.success.release.feature_schema_version == "echo-b"
    assert release.feature_schema_version == original_schema


def test_diagnostics_are_copied_through() -> None:
    mapped = map_kernel_result(_success(), _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert mapped.success.diagnostics.training_row_count == 0
    assert (
        mapped.success.diagnostics.segment_support
        == prediction_pb2.SEGMENT_SUPPORT_GLOBAL
    )
    assert mapped.success.diagnostics.shrinkage_weight.fraction == "0.0000"


# ---- Unmeasurable 매핑 ----


def test_maps_unmeasurable_reason_and_detail_code() -> None:
    unmeasurable = Unmeasurable(
        reason=UnmeasurableReason.UNTRAINED_SEGMENT,
        detail=UnmeasurableDetail.NEVER_TRAINED,
    )
    mapped = map_kernel_result(unmeasurable, _release(), "award-rate-features-v2")
    assert not isinstance(mapped, MappingRejected)
    assert mapped.WhichOneof("result") == "unmeasurable"
    assert mapped.unmeasurable.reason == error_pb2.UNMEASURABLE_REASON_UNTRAINED_SEGMENT
    assert mapped.unmeasurable.detail_code == "NEVER_TRAINED"


# ---- 매핑 불변식(D-5E2-6) ----


def test_sample_size_zero_success_is_mapping_rejected() -> None:
    mapped = map_kernel_result(
        _success(sample_size=0), _release(), "award-rate-features-v2"
    )
    assert isinstance(mapped, MappingRejected)


def test_nonzero_training_row_count_is_mapping_rejected() -> None:
    """DERIVED release 는 아티팩트가 없어 학습 행 수가 항상 0 이어야 한다."""
    mapped = map_kernel_result(
        _success(training_row_count=4120), _release(), "award-rate-features-v2"
    )
    assert isinstance(mapped, MappingRejected)


def test_non_finite_decimal_is_mapping_rejected() -> None:
    success = _success()
    # frozen dataclass 라도 `object.__setattr__`로 사후 변조할 수 있다 — 엔진 결함을
    # 흉내낸다(구성 시점 `__post_init__`은 통과했지만 이후 값이 오염된 경우).
    object.__setattr__(success.uncertainty, "dispersion", Decimal("Infinity"))
    mapped = map_kernel_result(success, _release(), "award-rate-features-v2")
    assert isinstance(mapped, MappingRejected)


def test_nan_candidate_rate_is_mapping_rejected_not_raised() -> None:
    """code-reviewer HIGH/verifier r2 N-1 — `Decimal("NaN")`은 순서 비교에서
    `decimal.InvalidOperation`을 던진다. 비유한 검사가 범위 검사보다 먼저 돌아야
    `map_kernel_result`가 그 예외를 그대로 흘리지 않고 통제된 `MappingRejected`를
    낸다(D-5E2-6). 사유는 「비유한」이어야 한다(verifier r2 N-7 — 「(0,1] 구간」이
    아니다, 비교 자체가 성립하지 않았으므로)."""
    success = _success()
    object.__setattr__(success.candidates[0], "bid_rate", Decimal("NaN"))
    mapped = map_kernel_result(success, _release(), "award-rate-features-v2")
    assert isinstance(mapped, MappingRejected)
    assert "비유한" in mapped.reason


def test_infinite_candidate_rate_is_mapping_rejected_with_non_finite_reason() -> None:
    """verifier r2 N-7 — `Infinity`는 순서 비교 자체는 성립하지만(범위 밖) 사유는
    여전히 「비유한」이어야 한다(비유한 검사가 먼저 돈다)."""
    success = _success()
    object.__setattr__(success.candidates[0], "bid_rate", Decimal("Infinity"))
    mapped = map_kernel_result(success, _release(), "award-rate-features-v2")
    assert isinstance(mapped, MappingRejected)
    assert "비유한" in mapped.reason


def test_candidate_count_not_three_is_mapping_rejected() -> None:
    success = _success()
    object.__setattr__(success, "candidates", success.candidates[:2])
    mapped = map_kernel_result(success, _release(), "award-rate-features-v2")
    assert isinstance(mapped, MappingRejected)


def test_candidate_rate_above_one_is_mapping_rejected() -> None:
    """verifier r1 H-1 — 대상 공고 후보율 축(D-2B-8·D-2F-4, `Candidate.bid_rate`)은
    1 을 넘을 수 없다. `features.proto` 의 `CompetitionSample.observed_bid_rate`(과거
    표본 관측값 축)와는 다른 축이라 `> 1`을 허용하지 않는다. 엔진 clamp 상한이 1 을
    넘는 정책 값(예: 출하 `scenario.clamp_max = 1.4`)과 만나면 발생할 수 있다 — 값을
    자르지 않고 fail-closed 로 거부한다(`OPEN-5E2-CANDIDATE-RATE-UPPER`)."""
    mapped = map_kernel_result(
        _success(bid_rate="1.2900"), _release(), "award-rate-features-v2"
    )
    assert isinstance(mapped, MappingRejected)


def test_candidate_rate_exactly_one_is_accepted() -> None:
    """경계값 — `(0, 1]`은 닫힌 상한이다(1 자체는 계약 위반이 아니다)."""
    mapped = map_kernel_result(
        _success(bid_rate="1.0000"), _release(), "award-rate-features-v2"
    )
    assert not isinstance(mapped, MappingRejected)


@pytest.mark.parametrize(
    "value",
    ["0.0000001", "0.24", "1", "0.8700"],
)
def test_format_fraction_round_trips_kotlin_normalized_forms(value: str) -> None:
    """D-5E2-5 — Kotlin `FractionRules.isNormalizedFraction`가 받아들이는 형태와
    `format(d, "f")`가 낸 형태가 같음을 왕복으로 확인한다(exponent 없음, scale 보존)."""
    assert _format_fraction(Decimal(value)) == value


@pytest.mark.parametrize("value", ["-0", "-0.0000", "-0.00"])
def test_format_fraction_normalizes_negative_zero(value: str) -> None:
    """code-reviewer MEDIUM(R-M1)/verifier r1 L-1 — `BigDecimal`에는 음수 0 개념이
    없어 `toPlainString()`이 부호를 지운다. `_format_fraction`도 부호만 지워
    (scale·값은 불변) Kotlin 과 같은 형태를 낸다."""
    assert _format_fraction(Decimal(value)) == value.lstrip("-")
