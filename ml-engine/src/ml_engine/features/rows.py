"""Reuse: bid-vector/app/domain/award_rate_features.py@ed4b06c

`ml_engine.features.rows` — 결측·미지의 좌표화 + 유일 조립 지점(D-5B-3, 신규 설계 지점 —
digest 충돌 2). legacy `AwardRateFeatureSpace.build_row`(조립 단일 지점 원칙)를 그대로
이어받되, 입력·출력 형태를 전부 바꿨다.

수행한 수정(reuse.md와 대조): (1) 입력이 `amount: float`·`category: str | None` 등 원시
값이 아니라 검증된 `FeatureFacts`(D-5B-4가 이미 Money·oneof·빈 키를 판정한 뒤) 하나다 —
`build_row`는 `Money` 성분을 다시 보지 않는다(설계 검토 (2) 우회 (11)). (2) 반환값이
`tuple[float, ...]`가 아니라 `FeatureRow | RowRejected` — 값 옆에 열별 `RowProvenance`가
붙고, 기초금액·분모 출처 결측은 행 자체가 거부된다(legacy는 `max(amount,1.0)`·빈 문자열
분모로 조용히 접었다). (3) 어휘 밖 코드는 `sentinel -1.0`이 아니라 `NaN` +
`OutOfVocabulary` provenance다(LightGBM은 NaN을 결측으로 다룬다 — 5D가 소비할 때 그대로
성립). (4) `agency` 결측(wire Missing)과 미관측(어휘엔 있으나 표본 0)을 구별한다 —
legacy는 둘 다 `log1p(0)=0.0`으로 겹쳐 표현했지만(조사 §c-4), V2는 전자를 NaN, 후자를
`Observed`인 진짜 `0.0`으로 가른다(§6.3 「모든 피처가 결측 사유를 provenance로」).
`category_training_rows`(서빙 가용성 판정)는 이식하지 않았다 — 그 판정은 5D(serving 가용성)
소관이라 out_of_scope다.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from math import log1p, log10, nan

from ml_engine.features.encoding import AgencyTargetEncoding
from ml_engine.features.facts import FactValue, FeatureFacts, Missing, Present
from ml_engine.features.vocabulary import OutOfVocabulary, Vocabulary


@dataclass(frozen=True)
class Observed:
    """이 열의 값이 결측·미지 없이 그대로 관측됐다(표본 0의 진짜 관측 포함)."""


@dataclass(frozen=True)
class MissingColumn:
    """이 열의 원 fact 가 wire 상 결측이었다(`common_pb2.MissingReason` 값)."""

    reason: int


ColumnProvenance = Observed | MissingColumn | OutOfVocabulary


@dataclass(frozen=True)
class RowProvenance:
    """열마다 하나씩 — `FeatureSchema.columns`와 같은 순서."""

    columns: tuple[ColumnProvenance, ...]


@dataclass(frozen=True)
class FeatureRow:
    """`values`는 LightGBM 행렬 한 행, `provenance`는 행렬 밖에서 나른다."""

    values: tuple[float, ...]
    provenance: RowProvenance


class MissingFact(StrEnum):
    """행 자체를 거부하게 만드는 결측 fact — 나머지(category·agency)는 열 단위 NaN."""

    BASE_AMOUNT = "base_amount"
    DENOMINATOR_SOURCE = "denominator_source"


@dataclass(frozen=True)
class RowRejected:
    reason: MissingFact


def _categorical_column(
    vocabulary: Vocabulary, fact: FactValue[str]
) -> tuple[float, ColumnProvenance]:
    if isinstance(fact, Missing):
        return nan, MissingColumn(fact.reason)
    code = vocabulary.code_of(fact.value)
    if isinstance(code, OutOfVocabulary):
        return nan, code
    return float(code), Observed()


def _agency_columns(
    encoding: AgencyTargetEncoding,
    agency_fact: FactValue[str],
    category_key: str | None,
) -> tuple[float, ColumnProvenance, float, ColumnProvenance]:
    if isinstance(agency_fact, Missing):
        provenance: ColumnProvenance = MissingColumn(agency_fact.reason)
        return nan, provenance, nan, provenance
    encoded, sample_count = encoding.encode(
        agency=agency_fact.value, category=category_key
    )
    return encoded, Observed(), log1p(float(sample_count)), Observed()


@dataclass(frozen=True)
class AwardRateFeatureSpace:
    """피처 공간 전체 — 어휘 + 인코딩 표. 학습·서빙이 공유하는 유일한 조립 지점
    (`build_row`)을 갖는다."""

    categories: Vocabulary
    denominator_sources: Vocabulary
    agency_encoding: AgencyTargetEncoding

    def build_row(self, facts: FeatureFacts) -> FeatureRow | RowRejected:
        """한 공고의 피처 행. 시각 인자 없음(누수 차단 시그니처 계승)."""
        if isinstance(facts.base_amount, Missing):
            return RowRejected(MissingFact.BASE_AMOUNT)
        if isinstance(facts.denominator_source, Missing):
            return RowRejected(MissingFact.DENOMINATOR_SOURCE)

        category_key = (
            facts.category_code.value
            if isinstance(facts.category_code, Present)
            else None
        )
        category_value, category_prov = _categorical_column(
            self.categories, facts.category_code
        )
        (
            agency_encoding_value,
            agency_encoding_prov,
            agency_sample_value,
            agency_sample_prov,
        ) = _agency_columns(self.agency_encoding, facts.agency_id, category_key)
        denominator_value, denominator_prov = _categorical_column(
            self.denominator_sources, facts.denominator_source
        )

        values = (
            category_value,
            log10(facts.base_amount.value),
            agency_encoding_value,
            agency_sample_value,
            denominator_value,
        )
        provenance = RowProvenance(
            (
                category_prov,
                Observed(),
                agency_encoding_prov,
                agency_sample_prov,
                denominator_prov,
            )
        )
        return FeatureRow(values=values, provenance=provenance)
