"""Reuse: bid-vector/app/ai/predictors/distribution_extraction.py@ed4b06c
(`observe_reserve_draw`·`realized_assessment_ratio` — 관문·흐름의 산술 출처)

`ml_engine.inference.observations` — wire `CompetitionSample` 한 건을 K5/K6 커널이 소비할
`ReserveDrawSample`로 정제한다(scope.md ①). legacy `observe_reserve_draw`는 `base_amount
<= 0` 하나만 명시 거부하고 나머지(예비가 개수·center 밴드)는 필터링 뒤 재계수로
**암묵** 처리했다(`ratios = [p/base for p in reserve_prices if p > 0]` 뒤
`len(ratios) != 15`이면 거부 — "개수 부족"과 "가격이 음수라 빠짐"이 응답에서 구별되지
않는다). 이 모듈은 그 둘을 **분리된 사유**로 낸다(조용한 drop 금지, 위협 모델 (b))(D-5D-2
「사유가 섞이면 구별 안 됨」과 같은 원칙).

7 사유(`SampleRejectionReason`): `BASE_AMOUNT_INVALID`(5성분 검증, 5B `features/facts.py`
`_resolve_base_amount`와 같은 규칙 — `CompetitionSample.base_amount`는 oneof/Fact 래퍼가
아닌 바로 `Money`라 그 함수를 그대로 재사용할 수 없어 규칙만 재현한다, 5B 파일은 편집하지
않는다) · `NO_RESERVE_DRAW`(optional 미설정) · `PRICE_COUNT_MISMATCH`(!= `policy.
reserve_expected_price_count`) · `NON_POSITIVE_PRICE`(하나라도 `amount_won <= 0`) ·
`RESERVE_DRAW_UNMEASURABLE`(K6 `draw_mean_moments`가 자체적으로 `Unmeasurable`을 낼 때 —
표본 15개가 전부 동일값이면 모분산 0, legacy 에는 없던 경로다·golden M-3) ·
`CENTER_OUT_OF_BAND`(K6 결과 `mean` 이 `policy.assessment_plausible_*` 밖) ·
`BID_RATE_OUT_OF_BAND`(`observed_bid_rate` 자체가 `policy.bid_ratio_plausible_*` 밖 —
D-5D2-6 이 `bid_to_assessment_ratio` 환산을 이식하지 않기로 하면서, legacy 가 그 비에
적용하던 개연 밴드를 이 모듈은 관측값 자체의 sanity 밴드로 재사용한다).

실현 사정률(`realized_assessment_ratio`)은 **거부 축이 아니다** — 추첨번호 유효 개수가
2 미만이거나 평균이 개연 밴드 밖이면 `None`(진단용 결측, 설계 검토 (1))이고 표본은 그대로
admit 된다. `selected_numbers`의 1-기반 인덱스가 범위 밖이면 legacy 처럼 조용히 걸러진다
(그 자체가 관측의 결함이 아니다 — 유효 인덱스가 몇 개 남는지만 2 미만 판정에 쓰인다).
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import StrEnum
from statistics import fmean

from ml_engine.contracts import common_pb2, features_pb2
from ml_engine.inference.assessment import AssessmentProvenance
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.reserve_draw import draw_mean_moments
from ml_engine.inference.results import Unmeasurable

_PROVENANCE_LABELS: dict[int, AssessmentProvenance] = {
    common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN: AssessmentProvenance.CLEAN,
    common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_DERIVED_YEGA: (
        AssessmentProvenance.DERIVED_YEGA
    ),
    common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_DERIVED_VAT: (
        AssessmentProvenance.DERIVED_VAT
    ),
    common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_SUSPECT_RATIO: (
        AssessmentProvenance.SUSPECT_RATIO
    ),
    common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_UNKNOWN: AssessmentProvenance.UNKNOWN,
}


class SampleRejectionReason(StrEnum):
    """관문 일곱 — 설계 검토 (5) 구현 지시 3 「7 사유」. wire 로 나가지 않는다(Python
    결과 타입 전용, `Diagnostics.excluded_observations`가 사유별이 아니라 총계만 나른다)."""

    BASE_AMOUNT_INVALID = "BASE_AMOUNT_INVALID"
    NO_RESERVE_DRAW = "NO_RESERVE_DRAW"
    PRICE_COUNT_MISMATCH = "PRICE_COUNT_MISMATCH"
    NON_POSITIVE_PRICE = "NON_POSITIVE_PRICE"
    RESERVE_DRAW_UNMEASURABLE = "RESERVE_DRAW_UNMEASURABLE"
    CENTER_OUT_OF_BAND = "CENTER_OUT_OF_BAND"
    BID_RATE_OUT_OF_BAND = "BID_RATE_OUT_OF_BAND"


@dataclass(frozen=True)
class SampleRejected:
    """표본 하나가 거부됐다 — 사유만 나른다(값은 버려진다, 개수는 호출부가 센다)."""

    reason: SampleRejectionReason


@dataclass(frozen=True)
class ReserveDrawSample:
    """관문을 통과한 표본 하나 — K5/K6 가 바로 소비할 수 있는 형태."""

    center: float
    draw_std: float
    realized_assessment_ratio: float | None
    observed_bid_rate: float
    provenance: AssessmentProvenance


def _validate_base_amount(money: common_pb2.Money) -> float | None:
    """5B `features/facts.py::_resolve_base_amount`와 같은 규칙(basis·currency·
    provenance·amount_won) — `CompetitionSample.base_amount`는 oneof/Fact 래퍼가 아닌
    바로 `Money`라 그 함수를 직접 재사용하지 못해 규칙만 재현한다(5B 파일 편집 금지,
    reuse.md 에 근거 기록)."""
    if money.basis != common_pb2.BASIS_BASE_AMOUNT:
        return None
    if money.currency != common_pb2.CURRENCY_KRW:
        return None
    if money.provenance == common_pb2.AMOUNT_PROVENANCE_KIND_UNSPECIFIED:
        return None
    if money.amount_won <= 0:
        return None
    return float(money.amount_won)


def _realized_assessment_ratio(
    *,
    ratios: list[float],
    selected_numbers: list[int],
    policy: InferencePolicy,
) -> float | None:
    """legacy `realized_assessment_ratio` 그대로: 1-기반 인덱스로 예비가 비율을 골라
    평균한다 — 범위 밖 인덱스는 조용히 걸러지고(legacy 관례), 유효 개수 2 미만이거나
    평균이 개연 밴드 밖이면 `None`(거부 아님)."""
    picked = [
        ratios[number - 1] for number in selected_numbers if 1 <= number <= len(ratios)
    ]
    if len(picked) < 2:
        return None
    realized = fmean(picked)
    plausible_min = float(policy.assessment_plausible_min)
    plausible_max = float(policy.assessment_plausible_max)
    if not plausible_min <= realized <= plausible_max:
        return None
    return realized


@dataclass(frozen=True)
class _ReserveDraw:
    """예비가 관문(개수·양수성·K6) 통과 뒤의 중간 산물 — `observe_sample`의 함수 길이를
    설계 래칫(50줄) 안으로 유지하려는 분리(내용은 그대로, 우회 경로 아님)."""

    ratios: list[float]
    center: float
    draw_std: float


def _resolve_reserve_draw(
    sample: features_pb2.CompetitionSample, base_amount: float, policy: InferencePolicy
) -> _ReserveDraw | SampleRejected:
    """reserve_draw 존재 → 개수 → 양수성 → K6 draw 연산 → center 밴드. `observe_sample`
    관문 순서 중 base_amount 검증 **뒤** 다섯 단계(`base_amount`는 호출부가 이미
    검증해 넘긴다 — 이 함수 안에서 다시 검증하지 않는다)."""
    if not sample.HasField("reserve_draw"):
        return SampleRejected(SampleRejectionReason.NO_RESERVE_DRAW)

    reserve_prices = sample.reserve_draw.reserve_prices
    if len(reserve_prices) != policy.reserve_expected_price_count:
        return SampleRejected(SampleRejectionReason.PRICE_COUNT_MISMATCH)
    if any(price.amount_won <= 0 for price in reserve_prices):
        return SampleRejected(SampleRejectionReason.NON_POSITIVE_PRICE)

    ratios = [price.amount_won / base_amount for price in reserve_prices]
    draw_result = draw_mean_moments(ratios, policy.reserve_draw_count)
    if isinstance(draw_result, Unmeasurable):
        return SampleRejected(SampleRejectionReason.RESERVE_DRAW_UNMEASURABLE)
    center, draw_std = draw_result

    plausible_min = float(policy.assessment_plausible_min)
    plausible_max = float(policy.assessment_plausible_max)
    if not plausible_min <= center <= plausible_max:
        return SampleRejected(SampleRejectionReason.CENTER_OUT_OF_BAND)

    return _ReserveDraw(ratios=ratios, center=center, draw_std=draw_std)


def observe_sample(
    sample: features_pb2.CompetitionSample, policy: InferencePolicy
) -> ReserveDrawSample | SampleRejected:
    """wire `CompetitionSample` → `ReserveDrawSample | SampleRejected`(scope.md ①).
    관문 순서: base_amount → (reserve_draw 존재 → 개수 → 양수성 → K6 draw 연산 →
    center 밴드, `_resolve_reserve_draw`) → (실현 사정률, 거부 아님) → bid_rate 밴드 →
    provenance 미러."""
    base_amount = _validate_base_amount(sample.base_amount)
    if base_amount is None:
        return SampleRejected(SampleRejectionReason.BASE_AMOUNT_INVALID)

    reserve_draw = _resolve_reserve_draw(sample, base_amount, policy)
    if isinstance(reserve_draw, SampleRejected):
        return reserve_draw

    realized_assessment_ratio = _realized_assessment_ratio(
        ratios=reserve_draw.ratios,
        selected_numbers=list(sample.reserve_draw.selected_numbers),
        policy=policy,
    )

    observed_bid_rate = float(Decimal(sample.observed_bid_rate.fraction))
    bid_ratio_min = float(policy.bid_ratio_plausible_min)
    bid_ratio_max = float(policy.bid_ratio_plausible_max)
    if not bid_ratio_min <= observed_bid_rate <= bid_ratio_max:
        return SampleRejected(SampleRejectionReason.BID_RATE_OUT_OF_BAND)

    provenance = _PROVENANCE_LABELS.get(
        sample.base_amount_provenance_label, AssessmentProvenance.UNKNOWN
    )

    return ReserveDrawSample(
        center=reserve_draw.center,
        draw_std=reserve_draw.draw_std,
        realized_assessment_ratio=realized_assessment_ratio,
        observed_bid_rate=observed_bid_rate,
        provenance=provenance,
    )
