"""RED — `ml_engine.training.corpus`(D-5C-5, scope ②). 라벨 도메인 규칙표(property) +
행 거부 회계 불변식(`admitted + rejected.total == 입력`)."""

from __future__ import annotations

from datetime import UTC, datetime

import pytest
from hypothesis import given
from hypothesis import strategies as st

from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.training.corpus import (
    AdmittedCorpus,
    AwardRateLabel,
    CorpusRejected,
    LabelRejected,
    LabelRejectionReason,
    admit_corpus,
    admit_label,
)
from ml_engine.training.dataset import RawTrainingRow


def _feature_inputs(
    *,
    amount: int = 100_000_000,
    category: str = "civil",
    agency: str = "agency-1",
    denominator_source: int = common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN,
) -> features_pb2.FeatureInputs:
    inputs = features_pb2.FeatureInputs()
    inputs.base_amount.value.amount_won = amount
    inputs.base_amount.value.currency = common_pb2.CURRENCY_KRW
    inputs.base_amount.value.basis = common_pb2.BASIS_BASE_AMOUNT
    inputs.base_amount.value.provenance = common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED
    inputs.category_code.value = category
    inputs.agency_id.value = agency
    inputs.base_amount_provenance_label.value = denominator_source
    return inputs


def _raw_row(*, label: float = 0.9, stratum: str = "clean-base") -> RawTrainingRow:
    return RawTrainingRow(
        feature_inputs=_feature_inputs(),
        label_value=label,
        opened_at=datetime(2026, 1, 1, tzinfo=UTC),
        stratum=stratum,
    )


# --- admit_label 규칙표 ---


@pytest.mark.parametrize("value", [0.0, 1.0, 0.5, -0.0, 0.0001, 0.9999])
def test_admit_label_accepts_closed_range(value: float) -> None:
    result = admit_label(value)
    assert isinstance(result, AwardRateLabel)
    assert result.value == value


@pytest.mark.parametrize("value", [-0.1, 1.0000001, 2.0, -1.0])
def test_admit_label_rejects_out_of_domain(value: float) -> None:
    result = admit_label(value)
    assert isinstance(result, LabelRejected)
    assert result.reason == LabelRejectionReason.OUT_OF_DOMAIN


@pytest.mark.parametrize("value", [float("nan"), float("inf"), float("-inf")])
def test_admit_label_rejects_non_finite(value: float) -> None:
    result = admit_label(value)
    assert isinstance(result, LabelRejected)
    assert result.reason == LabelRejectionReason.NON_FINITE


def test_award_rate_label_direct_construction_enforces_domain() -> None:
    with pytest.raises(ValueError):
        AwardRateLabel(1.2)
    with pytest.raises(ValueError):
        AwardRateLabel(float("nan"))


@given(st.floats(allow_nan=True, allow_infinity=True))
def test_admit_label_never_raises(value: float) -> None:
    result = admit_label(value)
    assert isinstance(result, AwardRateLabel | LabelRejected)


# --- admit_corpus 회계 불변식 ---


def test_admit_corpus_empty_input_is_corpus_rejected() -> None:
    assert isinstance(admit_corpus([]), CorpusRejected)


def test_admit_corpus_all_valid_rows_are_admitted_with_zero_rejections() -> None:
    rows = [_raw_row(label=0.8), _raw_row(label=0.6)]
    result = admit_corpus(rows)
    assert isinstance(result, AdmittedCorpus)
    assert len(result.rows) == 2
    assert result.rejected.total == 0


def test_admit_corpus_label_rejection_is_counted_not_dropped_silently() -> None:
    rows = [_raw_row(label=0.8), _raw_row(label=1.5)]
    result = admit_corpus(rows)
    assert isinstance(result, AdmittedCorpus)
    assert len(result.rows) == 1
    assert result.rejected.total == 1
    assert result.rejected.label_rejections[LabelRejectionReason.OUT_OF_DOMAIN] == 1


def test_admit_corpus_fact_rejection_is_counted() -> None:
    malformed = (
        features_pb2.FeatureInputs()
    )  # 모든 oneof 미설정 → base_amount MALFORMED
    row = RawTrainingRow(
        feature_inputs=malformed,
        label_value=0.5,
        opened_at=datetime(2026, 1, 1, tzinfo=UTC),
        stratum="clean-base",
    )
    result = admit_corpus([row, _raw_row()])
    assert isinstance(result, AdmittedCorpus)
    assert len(result.rows) == 1
    assert result.rejected.total == 1


def test_admit_corpus_accounting_invariant_admitted_plus_rejected_equals_input() -> (
    None
):
    rows = [
        _raw_row(label=0.5),
        _raw_row(label=2.0),
        _raw_row(label=-1.0),
        _raw_row(label=float("nan")),
        _raw_row(label=0.1),
    ]
    result = admit_corpus(rows)
    assert isinstance(result, AdmittedCorpus)
    assert len(result.rows) + result.rejected.total == len(rows)
