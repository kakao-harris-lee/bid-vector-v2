"""RED — `ml_engine.features.schema`(D-5B-1·D-5B-8). 열 다섯 순서·kind·version·registry·
`UnsupportedSchema`·`verify_feature_names` 양방향."""

from __future__ import annotations

from ml_engine.features.schema import (
    FEATURE_SCHEMA_V2,
    SUPPORTED_FEATURE_SCHEMAS,
    FeatureKind,
    MissingPolicy,
    NameMismatch,
    UnsupportedSchema,
    Verified,
    resolve_schema,
    verify_feature_names,
)

_EXPECTED_ORDER = (
    "category",
    "log_amount",
    "agency_encoding",
    "agency_sample_count",
    "denominator_source",
)


def test_schema_v2_version_string() -> None:
    assert FEATURE_SCHEMA_V2.version == "award-rate-features-v2"


def test_schema_v2_column_order_is_legacy_order() -> None:
    assert tuple(column.name for column in FEATURE_SCHEMA_V2.columns) == _EXPECTED_ORDER


def test_schema_v2_categorical_columns() -> None:
    kinds = {column.name: column.kind for column in FEATURE_SCHEMA_V2.columns}
    assert kinds["category"] == FeatureKind.CATEGORICAL
    assert kinds["denominator_source"] == FeatureKind.CATEGORICAL
    assert kinds["log_amount"] == FeatureKind.CONTINUOUS
    assert kinds["agency_encoding"] == FeatureKind.CONTINUOUS
    assert kinds["agency_sample_count"] == FeatureKind.CONTINUOUS


def test_schema_v2_missing_policy_matches_row_rejection_rules() -> None:
    """base_amount·denominator_source 결측은 행 거부, 나머지는 NaN — schema 가 그 규칙을
    선언적으로 고정한다(rows.py 가 이 표를 어길 수 없다는 근거가 아니라, 두 자리가 일치해야
    한다는 계약)."""
    policies = {column.name: column.missing for column in FEATURE_SCHEMA_V2.columns}
    assert policies["log_amount"] == MissingPolicy.REJECT_ROW_ON_MISSING
    assert policies["denominator_source"] == MissingPolicy.REJECT_ROW_ON_MISSING
    assert policies["category"] == MissingPolicy.NAN_ON_MISSING
    assert policies["agency_encoding"] == MissingPolicy.NAN_ON_MISSING
    assert policies["agency_sample_count"] == MissingPolicy.NAN_ON_MISSING


def test_supported_feature_schemas_registry_contains_v2() -> None:
    assert SUPPORTED_FEATURE_SCHEMAS[FEATURE_SCHEMA_V2.version] is FEATURE_SCHEMA_V2


def test_resolve_schema_known_version() -> None:
    assert resolve_schema("award-rate-features-v2") is FEATURE_SCHEMA_V2


def test_resolve_schema_unknown_version_is_result_type_not_exception() -> None:
    result = resolve_schema("award-rate-features-v99")
    assert result == UnsupportedSchema("award-rate-features-v99")


def test_verify_feature_names_matches() -> None:
    result = verify_feature_names(list(_EXPECTED_ORDER), FEATURE_SCHEMA_V2)
    assert result == Verified()


def test_verify_feature_names_mismatch_reports_expected_and_actual() -> None:
    scrambled = list(_EXPECTED_ORDER[::-1])
    result = verify_feature_names(scrambled, FEATURE_SCHEMA_V2)
    assert isinstance(result, NameMismatch)
    assert result.expected == _EXPECTED_ORDER
    assert result.actual == tuple(scrambled)


def test_verify_feature_names_fail_closed_on_extra_name() -> None:
    result = verify_feature_names([*_EXPECTED_ORDER, "extra"], FEATURE_SCHEMA_V2)
    assert isinstance(result, NameMismatch)
