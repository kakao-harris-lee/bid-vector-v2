"""Reuse: bid-vector/app/services/ml_training/award_rate_gbm.py@ed4b06c

`ml_engine.training.matrix` — 학습 행렬 조립(legacy `_feature_matrix`, scope ⑤). 5B
`AwardRateFeatureSpace.build_row` 결과만 소비한다 — `Money` 성분을 다시 검증하지 않는다.
legacy 는 `build_row`가 항상 값을 냈지만(조사 01 §2-3), 5B 의 `build_row`는
`FeatureRow | RowRejected` 합 타입을 낸다. 그 `RowRejected`를 조용히 버리지 않고 사유별로
계수해 `TrainingMatrix.rejected_rows`에 싣는다(`training/corpus.py::with_missing_fact_
rejections`가 상위 회계에 얹는다).
"""

from __future__ import annotations

from collections.abc import Mapping, Sequence
from dataclasses import dataclass
from types import MappingProxyType

import numpy as np

from ml_engine.features import (
    FEATURE_SCHEMA_V2,
    AwardRateFeatureSpace,
    MissingFact,
    RowProvenance,
    RowRejected,
)
from ml_engine.training.corpus import TrainingRow


@dataclass(frozen=True)
class TrainingMatrix:
    """`values`는 LightGBM 입력 행렬(열 순서 = `FEATURE_SCHEMA_V2.columns`), `labels`는
    같은 순서의 라벨, `row_provenances`는 행렬 밖에서 나르는 열별 provenance."""

    values: np.ndarray
    labels: np.ndarray
    row_provenances: tuple[RowProvenance, ...]
    rejected_rows: Mapping[MissingFact, int]


def build_training_matrix(
    space: AwardRateFeatureSpace, rows: Sequence[TrainingRow]
) -> TrainingMatrix:
    """승인된 학습 행마다 `space.build_row`를 부른다. `RowRejected`는 행렬에서 빠지고
    사유별로 계수된다(조용한 drop 아님)."""
    column_count = len(FEATURE_SCHEMA_V2.columns)
    values: list[tuple[float, ...]] = []
    labels: list[float] = []
    provenances: list[RowProvenance] = []
    rejected_counts: dict[MissingFact, int] = {}

    for row in rows:
        built = space.build_row(row.facts)
        if isinstance(built, RowRejected):
            rejected_counts[built.reason] = rejected_counts.get(built.reason, 0) + 1
            continue
        values.append(built.values)
        labels.append(row.label.value)
        provenances.append(built.provenance)

    matrix = (
        np.array(values, dtype=float).reshape(len(values), column_count)
        if values
        else np.zeros((0, column_count), dtype=float)
    )
    return TrainingMatrix(
        values=matrix,
        labels=np.array(labels, dtype=float),
        row_provenances=tuple(provenances),
        rejected_rows=MappingProxyType(rejected_counts),
    )
