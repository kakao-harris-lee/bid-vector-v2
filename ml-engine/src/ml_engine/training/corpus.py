"""`ml_engine.training.corpus` — 라벨 도메인 + 행 거부 회계(D-5C-5, scope ②). legacy
`AwardRateObservation.value: float`에는 범위 검증이 없었다(조사 01 §2-5 「관측값 도메인
검증 — 없다」) — 이 모듈이 그 빈자리를 채운다. 근거는 legacy 값이 아니라 5B 스키마
사실이다: `agency_encoding` 열의 range 가 `[0, 1]`(수축 평균은 관측의 볼록결합이므로 그
치역이 성립하려면 관측 자체가 `[0, 1]` 안이어야 한다, `OPEN-5B-OBSERVATION-DOMAIN` 해소).

거부는 조용히 버리지 않는다 — 사유별로 계수해 `RejectedRowAccounting`에 싣는다(legacy에
대응물 없음, 신규 — `OPEN-5C-ROW-REJECTION-POLICY` 해소).
"""

from __future__ import annotations

import math
from collections.abc import Mapping, Sequence
from dataclasses import dataclass, field
from datetime import datetime
from enum import StrEnum
from types import MappingProxyType

from ml_engine.features import (
    FactRejected,
    FactRejectionReason,
    FeatureFacts,
    MissingFact,
)
from ml_engine.training.dataset import RawTrainingRow


class LabelRejectionReason(StrEnum):
    NON_FINITE = "NON_FINITE"
    OUT_OF_DOMAIN = "OUT_OF_DOMAIN"


@dataclass(frozen=True)
class AwardRateLabel:
    """낙찰률 라벨 — 닫힌 구간 `[0.0, 1.0]`. 생성은 `admit_label`을 통하는 것이 관례지만
    (Python 가시성 한계, 5B 와 같은 갈래), 직접 생성도 이 불변식을 방어선으로 강제한다
    (`Vocabulary` 와 같은 갈래 — 우회 후보 (4))."""

    value: float

    def __post_init__(self) -> None:
        if not math.isfinite(self.value):
            raise ValueError(f"라벨 값은 유한해야 합니다: {self.value}")
        if not (0.0 <= self.value <= 1.0):
            raise ValueError(f"라벨 값은 [0, 1] 안이어야 합니다: {self.value}")


@dataclass(frozen=True)
class LabelRejected:
    reason: LabelRejectionReason


def admit_label(value: float) -> AwardRateLabel | LabelRejected:
    """라벨 값 판정 — 비유한(`NaN`/`inf`) 또는 `[0, 1]` 밖은 거부(결과 타입, 예외 아님)."""
    if not math.isfinite(value):
        return LabelRejected(LabelRejectionReason.NON_FINITE)
    if not (0.0 <= value <= 1.0):
        return LabelRejected(LabelRejectionReason.OUT_OF_DOMAIN)
    return AwardRateLabel(value)


@dataclass(frozen=True)
class TrainingRow:
    """승인된 학습 행 한 건 — fact 넷·라벨·개찰 시각·stratum. `stratum`은 5C-1이 검사하지
    않고 나른다(5C-2 창 정책의 소비 대상 — dataset 이 선언한 표본 계층 태그)."""

    facts: FeatureFacts
    label: AwardRateLabel
    opened_at: datetime
    stratum: str


_EMPTY_FACT_COUNTS: MappingProxyType[FactRejectionReason, int] = MappingProxyType({})
_EMPTY_LABEL_COUNTS: MappingProxyType[LabelRejectionReason, int] = MappingProxyType({})
_EMPTY_MISSING_FACT_COUNTS: MappingProxyType[MissingFact, int] = MappingProxyType({})


@dataclass(frozen=True)
class RejectedRowAccounting:
    """사유별 거부 계수. `missing_fact_rejections`는 `admit_corpus` 단계에서는 항상 비어
    있다 — 행렬 조립(`matrix.py`, scope ⑤)이 `with_missing_fact_rejections`로 얹는다."""

    fact_rejections: Mapping[FactRejectionReason, int] = field(
        default_factory=lambda: _EMPTY_FACT_COUNTS
    )
    label_rejections: Mapping[LabelRejectionReason, int] = field(
        default_factory=lambda: _EMPTY_LABEL_COUNTS
    )
    missing_fact_rejections: Mapping[MissingFact, int] = field(
        default_factory=lambda: _EMPTY_MISSING_FACT_COUNTS
    )

    @property
    def total(self) -> int:
        return (
            sum(self.fact_rejections.values())
            + sum(self.label_rejections.values())
            + sum(self.missing_fact_rejections.values())
        )


def with_missing_fact_rejections(
    accounting: RejectedRowAccounting,
    missing_fact_rejections: Mapping[MissingFact, int],
) -> RejectedRowAccounting:
    """행렬 조립 단계의 `RowRejected` 계수를 기존 회계에 얹은 새 회계를 낸다(scope ⑤)."""
    return RejectedRowAccounting(
        fact_rejections=accounting.fact_rejections,
        label_rejections=accounting.label_rejections,
        missing_fact_rejections=missing_fact_rejections,
    )


@dataclass(frozen=True)
class AdmittedCorpus:
    rows: tuple[TrainingRow, ...]
    rejected: RejectedRowAccounting


@dataclass(frozen=True)
class CorpusRejected:
    """입력 `rows` 자체가 비었다(구조적 — "0건이 들어와 처리할 것이 없다") — "행을
    받았지만 전부 거부됐다"(`AdmittedCorpus(rows=(), ...)`, `train.py`의
    `ALL_ROWS_REJECTED`)와 다른 상태다."""


def admit_corpus(
    rows: Sequence[RawTrainingRow],
) -> AdmittedCorpus | CorpusRejected:
    """구조적으로 유효한 원행(raw row)마다 fact·라벨 도메인을 판정한다. 거부는 조용히
    버리지 않고 사유별로 계수한다(D-5C-5)."""
    if not rows:
        return CorpusRejected()

    fact_counts: dict[FactRejectionReason, int] = {}
    label_counts: dict[LabelRejectionReason, int] = {}
    admitted: list[TrainingRow] = []

    for raw_row in rows:
        facts = FeatureFacts.from_proto(raw_row.feature_inputs)
        if isinstance(facts, FactRejected):
            fact_counts[facts.reason] = fact_counts.get(facts.reason, 0) + 1
            continue
        label = admit_label(raw_row.label_value)
        if isinstance(label, LabelRejected):
            label_counts[label.reason] = label_counts.get(label.reason, 0) + 1
            continue
        admitted.append(
            TrainingRow(
                facts=facts,
                label=label,
                opened_at=raw_row.opened_at,
                stratum=raw_row.stratum,
            )
        )

    return AdmittedCorpus(
        rows=tuple(admitted),
        rejected=RejectedRowAccounting(
            fact_rejections=MappingProxyType(fact_counts),
            label_rejections=MappingProxyType(label_counts),
        ),
    )
