"""RED — `ml_engine.serving.runtime`(scope.md ③, D-5E2-1~4). DERIVED release 채움
규약·checksum 결정성(같은 정책 두 번 = 같은 값, 값 하나 바꾸면 다른 값)."""

from __future__ import annotations

import dataclasses
from decimal import Decimal

import pytest

from ml_engine.contracts import prediction_pb2
from ml_engine.inference.policy import InferencePolicy
from ml_engine.serving.runtime import (
    PredictionRuntime,
    build_derived_release,
    derived_release_checksum,
)

_POLICY = InferencePolicy(
    version="inference-v1",
    scenario_z=Decimal("1.2816"),
    scenario_weights=(Decimal("0.24"), Decimal("0.52"), Decimal("0.24")),
    scenario_z_signs=(-1, 0, 1),
    scenario_clamp_min=Decimal("0.7"),
    scenario_clamp_max=Decimal("1.4"),
    scenario_bid_rate_digits=4,
    assessment_agency_prior_strength=Decimal("12.0"),
    assessment_category_prior_strength=Decimal("40.0"),
    assessment_min_predictive_std=Decimal("0.002"),
    assessment_min_samples_for_variance=2,
    assessment_plausible_min=Decimal("0.8"),
    assessment_plausible_max=Decimal("1.2"),
    assessment_agency_sample_threshold=1,
    reserve_draw_count=4,
    reserve_expected_price_count=15,
    reserve_min_reserve_records=8,
    bid_ratio_min_samples=3,
    bid_ratio_plausible_min=Decimal("0.5"),
    bid_ratio_plausible_max=Decimal("1.5"),
    gbm_min_category_rows=40,
    maturity_window_days=7,
)


# ---- derived_release_checksum(D-5E2-4) ----


def test_checksum_is_deterministic_for_same_policy() -> None:
    assert derived_release_checksum(_POLICY) == derived_release_checksum(_POLICY)


def test_checksum_is_64_char_lowercase_hex() -> None:
    checksum = derived_release_checksum(_POLICY)
    assert len(checksum) == 64
    assert checksum == checksum.lower()
    int(checksum, 16)  # ValueError 없이 hex 파싱되면 통과


@pytest.mark.parametrize(
    "field_name,new_value",
    [
        ("scenario_z", Decimal("1.3")),
        ("assessment_agency_sample_threshold", 2),
        ("version", "inference-v2"),
    ],
)
def test_checksum_changes_when_a_single_field_changes(
    field_name: str, new_value: object
) -> None:
    changed = dataclasses.replace(_POLICY, **{field_name: new_value})
    assert derived_release_checksum(changed) != derived_release_checksum(_POLICY)


def test_checksum_covers_tuple_fields() -> None:
    """`scenario_weights`(Decimal 3튜플)가 손 목록이 아니라 `dataclasses.fields`
    전수에 포함됨을 확인 — 튜플 성분 하나만 바뀌어도 checksum 이 바뀐다."""
    changed = dataclasses.replace(
        _POLICY, scenario_weights=(Decimal("0.25"), Decimal("0.51"), Decimal("0.24"))
    )
    assert derived_release_checksum(changed) != derived_release_checksum(_POLICY)


def _bump_field_value(
    value: str | int | Decimal | tuple[int, ...] | tuple[Decimal, ...],
) -> str | int | Decimal | tuple[int, ...] | tuple[Decimal, ...]:
    """필드 값과 같은 타입으로 "다른 값"을 만든다(도메인 유효성은 무관 — 이 함수는
    정책 불변식을 통과시킬 필요가 없다, `dataclasses.replace`로 직접 만든 객체를
    `derived_release_checksum`에만 넘긴다)."""
    if isinstance(value, str):
        return value + "-changed"
    if isinstance(value, Decimal):
        return value + Decimal("0.0001")
    if isinstance(value, tuple):
        first, *rest = value
        return (_bump_field_value(first), *rest)  # type: ignore[return-value]
    if isinstance(value, int):
        return value + 1
    raise TypeError(f"알 수 없는 정책 값 타입: {type(value)!r}")


@pytest.mark.parametrize(
    "field", dataclasses.fields(InferencePolicy), ids=lambda f: f.name
)
def test_checksum_changes_for_every_declared_field(
    field: dataclasses.Field[object],
) -> None:
    """verifier r1 M-2 — 손 목록이 아니라 `dataclasses.fields(InferencePolicy)`
    **전수**를 재는 test. 열거에서 필드 하나가 빠지면(예: `maturity_window_days`)
    이 test 의 그 case 만 초록으로 남아 누락을 드러낸다(변이 검증, verifier 재현:
    parametrize 밖 필드를 손 목록에서 빼도 890 passed 로 안 붉어졌다)."""
    current_value = getattr(_POLICY, field.name)
    changed = dataclasses.replace(
        _POLICY, **{field.name: _bump_field_value(current_value)}
    )
    assert derived_release_checksum(changed) != derived_release_checksum(_POLICY)


# ---- build_derived_release(D-2F-2, D-5E2-2~4) ----


def test_release_id_has_distribution_prefix_and_policy_version() -> None:
    release = build_derived_release(_POLICY, "ml-engine-test-sha")
    assert release.release_id == "distribution/inference-v1"


def test_artifact_checksum_has_sha256_prefix_matching_derived_release_checksum() -> (
    None
):
    release = build_derived_release(_POLICY, "ml-engine-test-sha")
    assert release.artifact_checksum == f"sha256:{derived_release_checksum(_POLICY)}"


def test_dataset_id_is_empty_for_derived_release() -> None:
    release = build_derived_release(_POLICY, "ml-engine-test-sha")
    assert release.dataset_id == ""


def test_release_kind_is_derived() -> None:
    release = build_derived_release(_POLICY, "ml-engine-test-sha")
    assert release.release_kind == prediction_pb2.RELEASE_KIND_DERIVED


def test_feature_schema_version_is_the_supported_value() -> None:
    release = build_derived_release(_POLICY, "ml-engine-test-sha")
    assert release.feature_schema_version == "award-rate-features-v2"


def test_code_version_is_taken_from_caller() -> None:
    release = build_derived_release(_POLICY, "ml-engine-test-sha")
    assert release.code_version == "ml-engine-test-sha"


def test_empty_code_version_is_rejected() -> None:
    with pytest.raises(ValueError, match="code_version"):
        build_derived_release(_POLICY, "")


def test_build_derived_release_is_deterministic_for_same_inputs() -> None:
    first = build_derived_release(_POLICY, "sha-a")
    second = build_derived_release(_POLICY, "sha-a")
    assert first.SerializeToString() == second.SerializeToString()


def test_build_derived_release_differs_when_code_version_differs() -> None:
    first = build_derived_release(_POLICY, "sha-a")
    second = build_derived_release(_POLICY, "sha-b")
    assert first.code_version != second.code_version
    assert first.artifact_checksum == second.artifact_checksum


# ---- PredictionRuntime(D-5E2-1) ----


def test_prediction_runtime_holds_policy_release_and_schemas() -> None:
    release = build_derived_release(_POLICY, "sha-a")
    runtime = PredictionRuntime(
        policy=_POLICY,
        release=release,
        supported_feature_schema_versions=("award-rate-features-v2",),
    )
    assert runtime.policy is _POLICY
    assert runtime.release.release_id == release.release_id
    assert runtime.supported_feature_schema_versions == ("award-rate-features-v2",)
