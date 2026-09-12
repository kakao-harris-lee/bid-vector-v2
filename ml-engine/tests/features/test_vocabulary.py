"""RED — `ml_engine.features.vocabulary`(D-5B-2·D-5B-3). 정렬·중복 없음 불변식,
OOV 코드화, 분모 어휘 = wire `BaseAmountProvenanceLabel` 5값 이름(리터럴 나열 없이 enum 도출)."""

from __future__ import annotations

import pytest

from ml_engine.features.vocabulary import (
    OutOfVocabulary,
    Vocabulary,
    denominator_source_vocabulary,
)


def test_vocabulary_accepts_sorted_unique_tuple() -> None:
    vocab = Vocabulary(("civil", "electrical", "mechanical"))
    assert vocab.values == ("civil", "electrical", "mechanical")


def test_vocabulary_rejects_unsorted() -> None:
    with pytest.raises(ValueError):
        Vocabulary(("mechanical", "civil"))


def test_vocabulary_rejects_duplicates() -> None:
    with pytest.raises(ValueError):
        Vocabulary(("civil", "civil"))


def test_code_of_returns_position() -> None:
    vocab = Vocabulary(("civil", "electrical", "mechanical"))
    assert vocab.code_of("electrical") == 1


def test_code_of_unknown_key_returns_out_of_vocabulary() -> None:
    vocab = Vocabulary(("civil",))
    result = vocab.code_of("unknown-category")
    assert isinstance(result, OutOfVocabulary)
    assert result.key == "unknown-category"


def test_denominator_source_vocabulary_is_five_wire_labels_excluding_unspecified() -> (
    None
):
    vocab = denominator_source_vocabulary()
    assert vocab.values == (
        "CLEAN",
        "DERIVED_VAT",
        "DERIVED_YEGA",
        "SUSPECT_RATIO",
        "UNKNOWN",
    )


def test_denominator_source_vocabulary_derived_from_enum_not_literal() -> None:
    """어휘가 wire enum 과 어긋날 수 없음을 구조로 확인 — 이름 집합이 정확히 일치."""
    from ml_engine.contracts import common_pb2

    expected = sorted(
        name.removeprefix("BASE_AMOUNT_PROVENANCE_LABEL_")
        for name in common_pb2.BaseAmountProvenanceLabel.keys()  # noqa: SIM118
        if name != "BASE_AMOUNT_PROVENANCE_LABEL_UNSPECIFIED"
    )
    assert list(denominator_source_vocabulary().values) == expected
