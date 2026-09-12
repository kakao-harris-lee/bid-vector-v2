"""RED — `ml_engine.training.matrix`(scope ⑤). 5B `build_row` 결과만 소비, `RowRejected`
는 조용히 버리지 않고 회계로."""

from __future__ import annotations

from datetime import UTC, datetime

from ml_engine.features import (
    AgencyTargetEncoding,
    AwardRateFeatureSpace,
    FactRejected,
    FeatureFacts,
    Missing,
    MissingFact,
    Present,
    Vocabulary,
)
from ml_engine.training.corpus import AwardRateLabel, TrainingRow
from ml_engine.training.matrix import build_training_matrix

_SPACE = AwardRateFeatureSpace(
    categories=Vocabulary(("civil", "it")),
    denominator_sources=Vocabulary(("CLEAN", "UNKNOWN")),
    agency_encoding=AgencyTargetEncoding(
        agency_means={("agency-1", "civil"): (0.8, 5)},
        category_means={"civil": 0.7},
        global_mean=0.6,
    ),
)


def _row(*, base_amount=1e8, denominator_source="CLEAN", label=0.8) -> TrainingRow:
    facts = FeatureFacts(
        base_amount=Present(base_amount) if base_amount is not None else Missing(1),
        category_code=Present("civil"),
        agency_id=Present("agency-1"),
        denominator_source=(
            Present(denominator_source)
            if denominator_source is not None
            else Missing(1)
        ),
    )
    return TrainingRow(
        facts=facts,
        label=AwardRateLabel(label),
        opened_at=datetime(2026, 1, 1, tzinfo=UTC),
        stratum="clean-base",
    )


def test_build_training_matrix_admits_valid_rows() -> None:
    result = build_training_matrix(_SPACE, [_row(), _row(label=0.5)])
    assert result.values.shape == (2, 5)
    assert list(result.labels) == [0.8, 0.5]
    assert len(result.row_provenances) == 2
    assert result.rejected_rows == {}


def test_build_training_matrix_rejects_missing_base_amount_and_counts_it() -> None:
    rows = [_row(), _row(base_amount=None)]
    result = build_training_matrix(_SPACE, rows)
    assert result.values.shape == (1, 5)
    assert list(result.labels) == [0.8]
    assert result.rejected_rows[MissingFact.BASE_AMOUNT] == 1


def test_build_training_matrix_rejects_missing_denominator_source() -> None:
    rows = [_row(denominator_source=None)]
    result = build_training_matrix(_SPACE, rows)
    assert result.values.shape == (0, 5)
    assert result.rejected_rows[MissingFact.DENOMINATOR_SOURCE] == 1


def test_build_training_matrix_empty_rows_yields_empty_matrix() -> None:
    result = build_training_matrix(_SPACE, [])
    assert result.values.shape == (0, 5)
    assert result.labels.shape == (0,)
    assert result.rejected_rows == {}


def test_feature_facts_validates_only_at_from_proto_boundary_convention() -> None:
    """확인: `FeatureFacts.from_proto` 검증 우회 없이 정상 왕복도 성립(레이어 교차 확인)."""
    from ml_engine.contracts import common_pb2, features_pb2

    inputs = features_pb2.FeatureInputs()
    inputs.base_amount.value.amount_won = 100_000_000
    inputs.base_amount.value.currency = common_pb2.CURRENCY_KRW
    inputs.base_amount.value.basis = common_pb2.BASIS_BASE_AMOUNT
    inputs.base_amount.value.provenance = common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED
    inputs.category_code.value = "civil"
    inputs.agency_id.value = "agency-1"
    inputs.base_amount_provenance_label.value = (
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN
    )
    facts = FeatureFacts.from_proto(inputs)
    assert not isinstance(facts, FactRejected)
