"""RED — `ml_engine.features.rows.AwardRateFeatureSpace.build_row`(D-5B-3, 신규 설계 지점).

규칙표: 범주 OOV→NaN+OOV, 금액 결측→RowRejected, 분모 결측→RowRejected,
agency Missing→NaN NaN, 미관측→category 평균 0.0(Observed), 관측→log1p(n)(Observed).
NaN 위치 ≡ provenance 위치. `inspect.signature` 고정(시각 인자 없음). `Observed` 값은
`FEATURE_SCHEMA_V2`가 선언한 열별 `range` 안에 있어야 한다(verifier r1 L-1).
"""

from __future__ import annotations

import inspect
import math

from hypothesis import given
from hypothesis import strategies as st

from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.features.encoding import (
    AgencyTargetEncoding,
    AwardRateObservation,
    Built,
    EncodingPolicy,
    build_agency_target_encoding,
)
from ml_engine.features.facts import FeatureFacts
from ml_engine.features.rows import (
    FeatureRow,
    MissingColumn,
    MissingFact,
    Observed,
    RowRejected,
)
from ml_engine.features.schema import FEATURE_SCHEMA_V2
from ml_engine.features.vocabulary import OutOfVocabulary, Vocabulary

_POLICY = EncodingPolicy(agency_prior_strength=12.0, category_prior_strength=40.0)

_EMPTY_ENCODING = AgencyTargetEncoding(
    agency_means={}, category_means={}, global_mean=0.0
)


def _feature_space(observations: list[AwardRateObservation] | None = None):
    from ml_engine.features.rows import AwardRateFeatureSpace

    if observations:
        outcome = build_agency_target_encoding(observations, policy=_POLICY)
        assert isinstance(outcome, Built)
        encoding = outcome.encoding
    else:
        # 관측 0 은 `NoObservations`(verifier r1 M-2) — 행 조립 test 는 여기서 그 가드를
        # 다시 재판정하지 않고, 빈 encoding 표를 직접 만들어 build_row 규칙만 확인한다.
        encoding = _EMPTY_ENCODING
    return AwardRateFeatureSpace(
        categories=Vocabulary(("civil", "electrical")),
        denominator_sources=Vocabulary(
            ("CLEAN", "DERIVED_VAT", "DERIVED_YEGA", "SUSPECT_RATIO", "UNKNOWN")
        ),
        agency_encoding=encoding,
    )


def _assert_observed_values_within_schema_range(row: FeatureRow) -> None:
    """verifier r1 L-1 — `Observed` 값은 `FEATURE_SCHEMA_V2`가 선언한 범위 안에 있어야 한다."""
    for value, provenance, column in zip(
        row.values, row.provenance.columns, FEATURE_SCHEMA_V2.columns, strict=True
    ):
        if isinstance(provenance, Observed) and column.range is not None:
            assert column.range.minimum <= value <= column.range.maximum, (
                column.name,
                value,
            )


def _valid_money() -> common_pb2.Money:
    return common_pb2.Money(
        amount_won=500_000_000,
        currency=common_pb2.CURRENCY_KRW,
        basis=common_pb2.BASIS_BASE_AMOUNT,
        vat_treatment=common_pb2.VAT_TREATMENT_INCLUSIVE,
        provenance=common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED,
    )


def _valid_inputs(
    *,
    category: str | None = "civil",
    agency: str | None = "agency-1",
    denominator: int | None = common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN,
    amount_missing: bool = False,
) -> features_pb2.FeatureInputs:
    inputs = features_pb2.FeatureInputs()
    if amount_missing:
        inputs.base_amount.missing = common_pb2.MISSING_REASON_NOT_COLLECTED_YET
    else:
        inputs.base_amount.value.CopyFrom(_valid_money())
    if category is None:
        inputs.category_code.missing = common_pb2.MISSING_REASON_UNKNOWN
    else:
        inputs.category_code.value = category
    if agency is None:
        inputs.agency_id.missing = common_pb2.MISSING_REASON_UNKNOWN
    else:
        inputs.agency_id.value = agency
    if denominator is None:
        inputs.base_amount_provenance_label.missing = common_pb2.MISSING_REASON_UNKNOWN
    else:
        inputs.base_amount_provenance_label.value = denominator
    return inputs


def test_build_row_signature_has_no_time_argument() -> None:
    from ml_engine.features.rows import AwardRateFeatureSpace

    signature = inspect.signature(AwardRateFeatureSpace.build_row)
    assert set(signature.parameters) == {"self", "facts"}


def test_build_row_normal_case_all_observed() -> None:
    space = _feature_space(
        [AwardRateObservation(agency="agency-1", category="civil", value=0.8)]
    )
    facts = FeatureFacts.from_proto(_valid_inputs())
    assert isinstance(facts, FeatureFacts)
    row = space.build_row(facts)
    assert isinstance(row, FeatureRow)
    assert len(row.values) == 5
    assert row.provenance.columns == (
        Observed(),
        Observed(),
        Observed(),
        Observed(),
        Observed(),
    )
    assert all(math.isfinite(value) for value in row.values)
    assert row.values[0] == 0.0  # "civil" 은 vocab 위치 0
    assert row.values[1] == math.log10(500_000_000)
    assert row.values[4] == 0.0  # "CLEAN" 은 denominator vocab 위치 0
    _assert_observed_values_within_schema_range(row)


def test_build_row_missing_base_amount_rejects_whole_row() -> None:
    space = _feature_space()
    facts = FeatureFacts.from_proto(_valid_inputs(amount_missing=True))
    assert isinstance(facts, FeatureFacts)
    result = space.build_row(facts)
    assert result == RowRejected(MissingFact.BASE_AMOUNT)


def test_build_row_missing_denominator_source_rejects_whole_row() -> None:
    space = _feature_space()
    facts = FeatureFacts.from_proto(_valid_inputs(denominator=None))
    assert isinstance(facts, FeatureFacts)
    result = space.build_row(facts)
    assert result == RowRejected(MissingFact.DENOMINATOR_SOURCE)


def test_build_row_out_of_vocabulary_category_is_nan_with_provenance() -> None:
    space = _feature_space()
    facts = FeatureFacts.from_proto(_valid_inputs(category="never-seen-category"))
    assert isinstance(facts, FeatureFacts)
    row = space.build_row(facts)
    assert isinstance(row, FeatureRow)
    assert math.isnan(row.values[0])
    assert row.provenance.columns[0] == OutOfVocabulary("never-seen-category")


def test_build_row_missing_category_is_nan_with_missing_reason() -> None:
    space = _feature_space()
    facts = FeatureFacts.from_proto(_valid_inputs(category=None))
    assert isinstance(facts, FeatureFacts)
    row = space.build_row(facts)
    assert isinstance(row, FeatureRow)
    assert math.isnan(row.values[0])
    assert row.provenance.columns[0] == MissingColumn(common_pb2.MISSING_REASON_UNKNOWN)


def test_build_row_missing_agency_yields_nan_nan_with_same_reason() -> None:
    space = _feature_space()
    facts = FeatureFacts.from_proto(_valid_inputs(agency=None))
    assert isinstance(facts, FeatureFacts)
    row = space.build_row(facts)
    assert isinstance(row, FeatureRow)
    assert math.isnan(row.values[2])
    assert math.isnan(row.values[3])
    assert row.provenance.columns[2] == MissingColumn(common_pb2.MISSING_REASON_UNKNOWN)
    assert row.provenance.columns[3] == MissingColumn(common_pb2.MISSING_REASON_UNKNOWN)


def test_build_row_unobserved_agency_sample_count_is_zero_not_missing() -> None:
    """미관측(표본 0)은 결측이 아니다 — log1p(0)=0.0, provenance 는 Observed."""
    space = _feature_space(
        [AwardRateObservation(agency="known-agency", category="civil", value=0.8)]
    )
    facts = FeatureFacts.from_proto(_valid_inputs(agency="brand-new-agency"))
    assert isinstance(facts, FeatureFacts)
    row = space.build_row(facts)
    assert isinstance(row, FeatureRow)
    assert row.values[3] == 0.0
    assert row.provenance.columns[3] == Observed()
    assert not math.isnan(row.values[2])
    assert row.provenance.columns[2] == Observed()
    _assert_observed_values_within_schema_range(row)


def test_nan_position_equals_provenance_missing_or_oov_position() -> None:
    space = _feature_space()
    facts = FeatureFacts.from_proto(_valid_inputs(category="unknown-cat", agency=None))
    assert isinstance(facts, FeatureFacts)
    row = space.build_row(facts)
    assert isinstance(row, FeatureRow)
    for value, provenance in zip(row.values, row.provenance.columns, strict=True):
        is_missing_or_oov = isinstance(provenance, (MissingColumn, OutOfVocabulary))
        assert math.isnan(value) == is_missing_or_oov


@given(
    amount_won=st.integers(min_value=1, max_value=10_000_000_000),
    category=st.sampled_from(["civil", "electrical", "unseen"]),
    agency=st.sampled_from(["agency-1", "agency-2", "unseen-agency"]),
)
def test_build_row_property_five_columns_and_finite_or_nan_only(
    amount_won: int, category: str, agency: str
) -> None:
    space = _feature_space(
        [AwardRateObservation(agency="agency-1", category="civil", value=0.5)]
    )
    inputs = _valid_inputs(category=category, agency=agency)
    inputs.base_amount.value.amount_won = amount_won
    facts = FeatureFacts.from_proto(inputs)
    assert isinstance(facts, FeatureFacts)
    row = space.build_row(facts)
    assert isinstance(row, FeatureRow)
    assert len(row.values) == 5
    for value in row.values:
        assert math.isfinite(value) or math.isnan(value)
    _assert_observed_values_within_schema_range(row)
