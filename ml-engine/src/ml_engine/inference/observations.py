"""Reuse: bid-vector/app/ai/predictors/distribution_extraction.py@ed4b06c
(`observe_reserve_draw`·`realized_assessment_ratio` — 관문·흐름의 산술 출처)

`ml_engine.inference.observations` — wire `CompetitionSample` 한 건을 K5/K6 커널이 소비할
`ReserveDrawSample`로 정제한다(scope.md ①). legacy `observe_reserve_draw`는 `base_amount
<= 0` 하나만 명시 거부하고 나머지(예비가 개수·center 밴드)는 필터링 뒤 재계수로
**암묵** 처리했다(`ratios = [p/base for p in reserve_prices if p > 0]` 뒤
`len(ratios) != 15`이면 거부 — "개수 부족"과 "가격이 음수라 빠짐"이 응답에서 구별되지
않는다). 이 모듈은 그 둘을 **분리된 사유**로 낸다(조용한 drop 금지, 위협 모델 (b))(D-5D-2
「사유가 섞이면 구별 안 됨」과 같은 원칙).

8 사유(`SampleRejectionReason`): `BASE_AMOUNT_INVALID`(5성분 검증, 5B `features/facts.py`
`_resolve_base_amount`와 같은 규칙 — `CompetitionSample.base_amount`는 oneof/Fact 래퍼가
아닌 바로 `Money`라 그 함수를 그대로 재사용할 수 없어 규칙만 재현한다, 5B 파일은 편집하지
않는다) · `NO_RESERVE_DRAW`(optional 미설정) · `PRICE_COUNT_MISMATCH`(!= `policy.
reserve_expected_price_count`) · `RESERVE_PRICE_INVALID`(예비가 `Money` 하나라도 4성분
규칙 위반 — verifier r1 F-2, `amount_won > 0`만 보던 구판의 `NonPositivePrice`를 대체·
포섭한다: `_validate_money_amount`(basis·currency·provenance·amount_won)를 `base_amount`와
같은 규칙으로 재사용) · `RESERVE_DRAW_UNMEASURABLE`(K6 `draw_mean_moments`가 자체적으로
`Unmeasurable`을 낼 때 — 표본 15개가 전부 동일값이면 모분산 0, legacy 에는 없던 경로다·
golden M-3) · `CENTER_OUT_OF_BAND`(K6 결과 `mean` 이 `policy.assessment_plausible_*` 밖) ·
`BID_RATE_UNPARSEABLE`(`Rate.fraction` 이 빈 문자열·비수치·비유한 — verifier r1 F-1,
`parse_rate` 관문 신설) · `BID_RATE_OUT_OF_BAND`(파싱된 `observed_bid_rate` 가 `policy.
bid_ratio_plausible_*` 밖 — D-5D2-6 이 `bid_to_assessment_ratio` 환산을 이식하지 않기로
하면서, legacy 가 그 비에 적용하던 개연 밴드를 이 모듈은 관측값 자체의 sanity 밴드로
재사용한다).

**verifier r1 F-1(high)** — 이전 판은 `Decimal(sample.observed_bid_rate.fraction)`을
관문 없이 호출해 미설정(proto 기본값 `""`)·비수치 문자열에서 `decimal.InvalidOperation`
이 `serve_bid_rates` 밖으로 새 표본 한 건이 요청 전체를 죽였다(위협 모델 (a) 위반 —
「실패는 전부 `Unmeasurable`」과 정면 충돌). `parse_rate`(신규, 저장소에서 wire `Rate`
를 파싱하는 유일한 자리) 가 그 경계를 막는다 — `observed_bid_rate`뿐 아니라 있으면
`award_rate`(optional)도 같은 관문을 거친다(현재 조립기가 `award_rate` 값을 소비하지
않아도, 파싱 가능성은 무조건 확인한다 — 「계약 밖 값을 조용히 접지 않는다」와 같은 원칙).

실현 사정률(`realized_assessment_ratio`)은 **거부 축이 아니다** — 추첨번호 유효 개수가
2 미만이거나 평균이 개연 밴드 밖이면 `None`(진단용 결측, 설계 검토 (1))이고 표본은 그대로
admit 된다. `selected_numbers`의 1-기반 인덱스가 범위 밖이면 legacy 처럼 조용히 걸러진다
(그 자체가 관측의 결함이 아니다 — 유효 인덱스가 몇 개 남는지만 2 미만 판정에 쓰인다).
"""

from __future__ import annotations

from collections.abc import Iterable
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
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
    """관문 여덟 — 설계 검토 (5) 구현 지시 3 「7 사유」에 verifier r1 F-1·F-2 가
    `BID_RATE_UNPARSEABLE` 신설 + `NON_POSITIVE_PRICE`→`RESERVE_PRICE_INVALID` 확장·
    개명(포섭)을 더했다. wire 로 나가지 않는다(Python 결과 타입 전용,
    `Diagnostics.excluded_observations`가 사유별이 아니라 총계만 나른다)."""

    BASE_AMOUNT_INVALID = "BASE_AMOUNT_INVALID"
    NO_RESERVE_DRAW = "NO_RESERVE_DRAW"
    PRICE_COUNT_MISMATCH = "PRICE_COUNT_MISMATCH"
    RESERVE_PRICE_INVALID = "RESERVE_PRICE_INVALID"
    RESERVE_DRAW_UNMEASURABLE = "RESERVE_DRAW_UNMEASURABLE"
    CENTER_OUT_OF_BAND = "CENTER_OUT_OF_BAND"
    BID_RATE_UNPARSEABLE = "BID_RATE_UNPARSEABLE"
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


def _validate_money_amount(money: common_pb2.Money) -> float | None:
    """5B `features/facts.py::_resolve_base_amount`와 같은 4성분 규칙(basis·currency·
    provenance·amount_won) — `CompetitionSample.base_amount`도 `reserve_draw.
    reserve_prices`의 각 `Money`도 oneof/Fact 래퍼가 아닌 바로 `Money`라 그 함수를 직접
    재사용하지 못해 규칙만 재현한다(5B 파일 편집 금지, reuse.md 에 근거 기록). 이 모듈
    안에서는 두 호출부(`observe_sample`의 base_amount, `_resolve_reserve_draw`의 예비가
    15개)가 이 함수 하나를 공유한다(verifier r1 F-2 — 중복 재구현 금지)."""
    if money.basis != common_pb2.BASIS_BASE_AMOUNT:
        return None
    if money.currency != common_pb2.CURRENCY_KRW:
        return None
    if money.provenance == common_pb2.AMOUNT_PROVENANCE_KIND_UNSPECIFIED:
        return None
    if money.amount_won <= 0:
        return None
    return float(money.amount_won)


def parse_rate(fraction: str) -> Decimal | None:
    """wire `Rate.fraction` 파싱 관문(verifier r1 F-1) — 빈 문자열·비수치·비유한
    (`NaN`·`Infinity`)을 걸러 유한 `Decimal`만 반환한다. 업무적 타당성(밴드)은 호출부가
    별도로 본다 — 이 함수는 「파싱 가능한가」만 판정한다. 저장소에서 wire `Rate`를
    파싱하는 유일한 자리(5B 에 대응 함수 없음, grep 확인)."""
    try:
        value = Decimal(fraction)
    except InvalidOperation:
        return None
    if not value.is_finite():
        return None
    return value


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


def _validated_reserve_price_amounts(
    reserve_prices: Iterable[common_pb2.Money],
) -> list[float] | SampleRejected:
    """예비가 15개 각각을 `_validate_money_amount`(base_amount 와 같은 4성분 규칙)로
    검증한다 — verifier r1 F-2, `amount_won > 0`만 보던 구판을 대체한다."""
    validated: list[float] = []
    for price in reserve_prices:
        amount = _validate_money_amount(price)
        if amount is None:
            return SampleRejected(SampleRejectionReason.RESERVE_PRICE_INVALID)
        validated.append(amount)
    return validated


def _resolve_reserve_draw(
    sample: features_pb2.CompetitionSample, base_amount: float, policy: InferencePolicy
) -> _ReserveDraw | SampleRejected:
    """reserve_draw 존재 → 개수 → 4성분 검증 → K6 draw 연산 → center 밴드.
    `observe_sample` 관문 순서 중 base_amount 검증 **뒤** 다섯 단계(`base_amount`는
    호출부가 이미 검증해 넘긴다 — 이 함수 안에서 다시 검증하지 않는다)."""
    if not sample.HasField("reserve_draw"):
        return SampleRejected(SampleRejectionReason.NO_RESERVE_DRAW)

    reserve_prices = sample.reserve_draw.reserve_prices
    if len(reserve_prices) != policy.reserve_expected_price_count:
        return SampleRejected(SampleRejectionReason.PRICE_COUNT_MISMATCH)

    validated_amounts = _validated_reserve_price_amounts(reserve_prices)
    if isinstance(validated_amounts, SampleRejected):
        return validated_amounts

    ratios = [amount / base_amount for amount in validated_amounts]
    draw_result = draw_mean_moments(ratios, policy.reserve_draw_count)
    if isinstance(draw_result, Unmeasurable):
        return SampleRejected(SampleRejectionReason.RESERVE_DRAW_UNMEASURABLE)
    center, draw_std = draw_result

    plausible_min = float(policy.assessment_plausible_min)
    plausible_max = float(policy.assessment_plausible_max)
    if not plausible_min <= center <= plausible_max:
        return SampleRejected(SampleRejectionReason.CENTER_OUT_OF_BAND)

    return _ReserveDraw(ratios=ratios, center=center, draw_std=draw_std)


def _resolve_observed_bid_rate(
    sample: features_pb2.CompetitionSample, policy: InferencePolicy
) -> float | SampleRejected:
    """`observed_bid_rate` 파싱(verifier r1 F-1, `parse_rate`) → 밴드(D-5D2-6). `award_
    rate`(optional, 조립기가 소비하지 않는다)도 있으면 같은 파싱 관문만 거친다 — 계약
    밖 값을 조용히 접지 않는다는 원칙은 소비 여부와 무관하다."""
    rate = parse_rate(sample.observed_bid_rate.fraction)
    if rate is None:
        return SampleRejected(SampleRejectionReason.BID_RATE_UNPARSEABLE)
    observed_bid_rate = float(rate)
    bid_ratio_min = float(policy.bid_ratio_plausible_min)
    bid_ratio_max = float(policy.bid_ratio_plausible_max)
    if not bid_ratio_min <= observed_bid_rate <= bid_ratio_max:
        return SampleRejected(SampleRejectionReason.BID_RATE_OUT_OF_BAND)
    if sample.HasField("award_rate") and parse_rate(sample.award_rate.fraction) is None:
        return SampleRejected(SampleRejectionReason.BID_RATE_UNPARSEABLE)
    return observed_bid_rate


def observe_sample(
    sample: features_pb2.CompetitionSample, policy: InferencePolicy
) -> ReserveDrawSample | SampleRejected:
    """wire `CompetitionSample` → `ReserveDrawSample | SampleRejected`(scope.md ①).
    관문 순서: base_amount → (reserve_draw 존재 → 개수 → 4성분 검증 → K6 draw 연산 →
    center 밴드, `_resolve_reserve_draw`) → (실현 사정률, 거부 아님) → (bid_rate 파싱 →
    밴드 → award_rate 파싱, `_resolve_observed_bid_rate`) → provenance 미러."""
    base_amount = _validate_money_amount(sample.base_amount)
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

    observed_bid_rate = _resolve_observed_bid_rate(sample, policy)
    if isinstance(observed_bid_rate, SampleRejected):
        return observed_bid_rate

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
