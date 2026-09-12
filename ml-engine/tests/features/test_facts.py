"""RED — `ml_engine.features.facts.FeatureFacts.from_proto`(D-5B-3·D-5B-4).
oneof 미설정·`MissingReason.UNSPECIFIED`·basis/currency/provenance/amount 각 거부·정상."""

from __future__ import annotations

from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.features.facts import (
    FactRejected,
    FactRejectionReason,
    FeatureFacts,
    Missing,
    Present,
)


def _valid_money(**overrides: object) -> common_pb2.Money:
    money = common_pb2.Money(
        amount_won=1_000_000_000,
        currency=common_pb2.CURRENCY_KRW,
        basis=common_pb2.BASIS_BASE_AMOUNT,
        vat_treatment=common_pb2.VAT_TREATMENT_INCLUSIVE,
        provenance=common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED,
    )
    for key, value in overrides.items():
        setattr(money, key, value)
    return money


def _valid_inputs() -> features_pb2.FeatureInputs:
    inputs = features_pb2.FeatureInputs()
    inputs.base_amount.value.CopyFrom(_valid_money())
    inputs.category_code.value = "Civil Works"
    inputs.agency_id.value = "Agency-42"
    inputs.base_amount_provenance_label.value = (
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN
    )
    return inputs


def test_from_proto_accepts_fully_present_inputs() -> None:
    facts = FeatureFacts.from_proto(_valid_inputs())
    assert isinstance(facts, FeatureFacts)
    assert facts.base_amount == Present(1_000_000_000.0)
    # normalize_feature_key = strip().lower()
    assert facts.category_code == Present("civil works")
    assert facts.agency_id == Present("agency-42")
    assert facts.denominator_source == Present("CLEAN")


def test_from_proto_missing_base_amount_is_present_type() -> None:
    inputs = _valid_inputs()
    inputs.base_amount.missing = common_pb2.MISSING_REASON_NOT_COLLECTED_YET
    facts = FeatureFacts.from_proto(inputs)
    assert isinstance(facts, FeatureFacts)
    assert facts.base_amount == Missing(common_pb2.MISSING_REASON_NOT_COLLECTED_YET)


def test_from_proto_oneof_unset_is_malformed() -> None:
    inputs = features_pb2.FeatureInputs()
    # base_amount 의 oneof 를 아예 건드리지 않는다 — WhichOneof는 None.
    inputs.category_code.value = "civil"
    inputs.agency_id.value = "agency"
    inputs.base_amount_provenance_label.value = (
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN
    )
    result = FeatureFacts.from_proto(inputs)
    assert result == FactRejected(FactRejectionReason.MALFORMED, "base_amount")


def test_from_proto_missing_reason_unspecified_is_malformed() -> None:
    inputs = _valid_inputs()
    inputs.base_amount.missing = common_pb2.MISSING_REASON_UNSPECIFIED
    result = FeatureFacts.from_proto(inputs)
    assert result == FactRejected(FactRejectionReason.MALFORMED, "base_amount")


def test_from_proto_basis_mismatch_rejected() -> None:
    inputs = _valid_inputs()
    inputs.base_amount.value.basis = common_pb2.BASIS_ESTIMATED
    result = FeatureFacts.from_proto(inputs)
    assert result == FactRejected(
        FactRejectionReason.BASIS_MISMATCH, "base_amount.basis"
    )


def test_from_proto_non_krw_currency_rejected() -> None:
    inputs = _valid_inputs()
    inputs.base_amount.value.currency = common_pb2.CURRENCY_UNSPECIFIED
    result = FeatureFacts.from_proto(inputs)
    assert result == FactRejected(
        FactRejectionReason.UNSPECIFIED_ENUM, "base_amount.currency"
    )


def test_from_proto_unspecified_provenance_rejected() -> None:
    inputs = _valid_inputs()
    inputs.base_amount.value.provenance = common_pb2.AMOUNT_PROVENANCE_KIND_UNSPECIFIED
    result = FeatureFacts.from_proto(inputs)
    assert result == FactRejected(
        FactRejectionReason.UNSPECIFIED_ENUM, "base_amount.provenance"
    )


def test_from_proto_non_positive_amount_rejected_no_legacy_clamp() -> None:
    """legacy `max(amount, 1.0)` 접힘 제거 — 0 원은 거부, 예외 아님."""
    inputs = _valid_inputs()
    inputs.base_amount.value.amount_won = 0
    result = FeatureFacts.from_proto(inputs)
    assert result == FactRejected(
        FactRejectionReason.NON_POSITIVE_AMOUNT, "base_amount.amount_won"
    )


def test_from_proto_empty_category_key_rejected() -> None:
    inputs = _valid_inputs()
    inputs.category_code.value = "   "
    result = FeatureFacts.from_proto(inputs)
    assert result == FactRejected(FactRejectionReason.EMPTY_KEY, "category_code")


def test_from_proto_empty_agency_key_rejected() -> None:
    inputs = _valid_inputs()
    inputs.agency_id.value = ""
    result = FeatureFacts.from_proto(inputs)
    assert result == FactRejected(FactRejectionReason.EMPTY_KEY, "agency_id")


def test_from_proto_denominator_source_missing() -> None:
    inputs = _valid_inputs()
    inputs.base_amount_provenance_label.missing = common_pb2.MISSING_REASON_UNKNOWN
    facts = FeatureFacts.from_proto(inputs)
    assert isinstance(facts, FeatureFacts)
    assert facts.denominator_source == Missing(common_pb2.MISSING_REASON_UNKNOWN)


def test_from_proto_denominator_source_unspecified_enum_rejected() -> None:
    inputs = _valid_inputs()
    inputs.base_amount_provenance_label.value = (
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_UNSPECIFIED
    )
    result = FeatureFacts.from_proto(inputs)
    assert result == FactRejected(
        FactRejectionReason.UNSPECIFIED_ENUM, "denominator_source"
    )


def test_from_proto_category_and_agency_missing_are_independent() -> None:
    inputs = _valid_inputs()
    inputs.category_code.missing = common_pb2.MISSING_REASON_UNKNOWN
    inputs.agency_id.missing = common_pb2.MISSING_REASON_NOT_APPLICABLE
    facts = FeatureFacts.from_proto(inputs)
    assert isinstance(facts, FeatureFacts)
    assert facts.category_code == Missing(common_pb2.MISSING_REASON_UNKNOWN)
    assert facts.agency_id == Missing(common_pb2.MISSING_REASON_NOT_APPLICABLE)
