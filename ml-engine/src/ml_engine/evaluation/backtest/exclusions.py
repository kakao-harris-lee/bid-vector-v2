"""`ml_engine.evaluation.backtest.exclusions` — 제외 판정과 승인(D-6G-13·16·21).
제외는 **입력 단계에서만** 일어나고 **전략을 보기 전에** 끝난다 — 그래서 어떤 전략에게도
유리한 제외가 성립하지 않는다(위협 모델 ④, 우회 ④). 전략 간 집합 동일성은 `run` 이
단언한다.

제외는 침묵이 아니라 기록이다 — 사유는 enum 한 줄씩이고, 규칙 표의 **순서대로** 먼저
걸리는 사유가 기록된다(5C-2 `windows._exclusion_reason` 과 같은 형태). 계수는 0건도
공시한다.

**승인된 행은 좁혀진 타입을 갖는다** — `AdmittedNotice` 에서는 하한율·A·예가 범위율이
`None` 이 아니고, 실제 하한가와 적격 투찰자 중 최저 금액이 이미 계산돼 있다. 「없는 값을
상수로 메우지 않는다」(D-6G-16)를 타입으로 닫는 자리다: 값이 없으면 승인이 나지 않는다.

**판정할 수 없는 것은 제외가 아니라 「판정 불가」로 센다**(D-6G-21) — 지자체 발주(⑪)는
authoritative 한 기관 코드 대응이 없어(`OPEN-6G-LOCAL-GOVERNMENT-JUDGEMENT`) 그 규칙이
**한 번도 발화하지 않는다**. 계수 0 을 조용히 넘기지 않고 `undecidable_counts` 가
「가르지 못한 표본 수」로 공시한다.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass
from datetime import date
from enum import StrEnum
from typing import Final

from ml_engine.evaluation.backtest.floor import (
    floor_price,
    is_eligible,
    pure_construction_floor,
)
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.backtest.snapshot import (
    BusinessCategory,
    NoticeObservation,
    SnapshotRow,
)


class ExclusionReason(StrEnum):
    """제외 사유 어휘(D-6G-13 ①~⑮ + 스키마가 드러낸 입력 부재 셋)."""

    LOCAL_GOVERNMENT = "LOCAL_GOVERNMENT"
    """⑪ — **오늘 한 번도 발화하지 않는다.** 기관 코드에서 지자체를 가를 authoritative
    대응이 없고(스키마 §4), 지어내면 DEC-03 을 코드가 조용히 재정의한다. 계수 0 은
    「지자체가 없었다」가 아니라 「가르지 못했다」이며 `undecidable_counts` 가 공시한다."""

    FOREIGN_CAPITAL = "FOREIGN_CAPITAL"
    """⑫ — **구조적으로 0.** 수집 갈래가 외자 오퍼레이션을 부르지 않아 그 행이 들어오지
    않는다(스키마 §4). 「걸러서 0」이 아니라 「들어오지 않아 0」이다."""

    BID_METHOD_ABSENT = "BID_METHOD_ABSENT"
    """낙찰방법이 부재해 ①⑦⑩ 을 판정할 수 없다 — 조용히 통과시키지 않는다(fail-closed)."""

    SMALL_SUM_QUOTE = "SMALL_SUM_QUOTE"
    SME_COMPETITION_SCREENING = "SME_COMPETITION_SCREENING"
    SHIP_MANUFACTURING = "SHIP_MANUFACTURING"
    NOT_QUALIFICATION_SCREENING = "NOT_QUALIFICATION_SCREENING"
    FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY = "FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY"
    REBID_OR_AMENDED = "REBID_OR_AMENDED"
    SINGLE_PREARRANGED_PRICE = "SINGLE_PREARRANGED_PRICE"
    FLOOR_RATE_ABSENT_OR_OUT_OF_BAND = "FLOOR_RATE_ABSENT_OR_OUT_OF_BAND"

    RESERVE_PRICE_RANGE_ABSENT = "RESERVE_PRICE_RANGE_ABSENT"
    """D-6G-16 — 예가 범위율이 없으면 S0 의 반폭 h 가 서지 않는다. **상수 2%·3% 로
    메우지 않고**(그것이 공고별 필드라는 것이 P-3 의 발견이다) 예비가격 15개로 역산하지도
    않는다(역산은 개찰 결과를 투찰 시점 입력으로 쓰는 누출이다)."""

    RESERVE_DRAW_INCOMPLETE = "RESERVE_DRAW_INCOMPLETE"
    A_VALUE_ABSENT_OR_LATE = "A_VALUE_ABSENT_OR_LATE"
    PURE_CONSTRUCTION_COST_ABSENT = "PURE_CONSTRUCTION_COST_ABSENT"

    BIDDER_AMOUNT_ABSENT = "BIDDER_AMOUNT_ABSENT"
    """(P-1.5) 투찰금액이 없는 행이 섞이면 「적격 투찰자 중 최저」가 최저라는 보장이
    없다 — 빠진 행이 더 낮았을 수 있어 would-have-won 이 부푼다. 통째로 뺀다."""

    NO_ELIGIBLE_BIDDER = "NO_ELIGIBLE_BIDDER"
    TIED_LOWEST = "TIED_LOWEST"


class UndecidableAxis(StrEnum):
    """제외 규칙이 **발화하지 못한** 축 — 「해당 없음」과 구별해 센다(D-6G-21)."""

    LOCAL_GOVERNMENT = "LOCAL_GOVERNMENT"
    SHIP_MANUFACTURING_CLASS = "SHIP_MANUFACTURING_CLASS"


# 낙찰방법명 어휘 — 조달청 OpenAPI 참고자료의 `sucsfbidMthdNm` 샘플 문면에서 왔다
# (선행 조사 02 §2 축어 인용). **순서가 있다**: 아래 셋을 먼저 보고 나서 적격심사를
# 본다 — 소액수의·중소기업자간 공고의 방법명에도 「심사」 계열 어휘가 함께 나타난다.
_SMALL_SUM_QUOTE_MARKERS: Final[frozenset[str]] = frozenset({"소액수의"})
_SME_COMPETITION_MARKERS: Final[frozenset[str]] = frozenset({"중소기업자간"})
_SHIP_MANUFACTURING_MARKERS: Final[frozenset[str]] = frozenset({"선박"})
_QUALIFICATION_SCREENING_MARKERS: Final[frozenset[str]] = frozenset(
    {"적격심사", "계약이행능력심사"}
)
# 진행 구분(`progrsDivCdNm`)이 유찰·재입찰·정정을 말하는 문면. 차수와 **둘 다** 본다 —
# 한쪽만 보면 첫 차수로 재공고된 건을 놓친다.
_REBID_MARKERS: Final[frozenset[str]] = frozenset({"유찰", "재입찰", "정정", "취소"})
# 선박 제조 물품의 조달 분류 코드 — **아직 비어 있다.** 코드 공간의 authoritative
# 대응을 확보하지 못했다. 비어 있는 동안 ⑧ 은 낙찰방법명 표지로만 발화하고, 그 한계는
# `UndecidableAxis.SHIP_MANUFACTURING_CLASS` 가 공시한다.
_SHIP_MANUFACTURING_CLASS_CODES: Final[frozenset[str]] = frozenset()
_MULTIPLE_PREARRANGED_PRICE_NAME: Final[str] = "복수예가"
_FIRST_NOTICE_ORDINAL: Final[int] = 1


@dataclass(frozen=True)
class ExcludedNotice:
    """제외된 공고 하나와 사유. 식별자는 해시뿐이고 보고에는 사유별 계수만 나간다."""

    notice_key_hash: str
    reason: ExclusionReason


@dataclass(frozen=True)
class AdmittedNotice:
    """승인된 공고 — 채점과 전략에 필요한 값이 **전부 해소된** 좁혀진 타입.

    `bid_amounts` 는 투찰금액 전부다(`BIDDER_AMOUNT_ABSENT` 가 결측 있는 공고를 이미
    걸렀으므로 원문 행 수와 같다). 참가자 수는 그 길이로 센다 — 목록 축의
    `participant_count` 는 부재 가능이라 판정 입력으로 쓰지 않는다."""

    row: SnapshotRow
    floor_rate: float
    a_value_total: float
    reserve_range_begin_rate: float
    reserve_range_end_rate: float
    actual_floor_price: float
    pure_cost_floor: float | None
    bid_amounts: tuple[float, ...]
    lowest_eligible_amount: float

    @property
    def eligibility_floors(self) -> tuple[float, ...]:
        if self.pure_cost_floor is None:
            return (self.actual_floor_price,)
        return (self.actual_floor_price, self.pure_cost_floor)

    @property
    def participant_count(self) -> int:
        return len(self.bid_amounts)

    @property
    def assessment_ratio(self) -> float:
        """실현 사정률(예정가격 ÷ 기초금액) — P-4 적합도의 관측값. **개찰 결과**라
        전략에 넘어가지 않는다."""
        return self.row.outcome.planned_price / self.row.notice.base_amount


@dataclass(frozen=True)
class AdmissionResult:
    admitted: tuple[AdmittedNotice, ...]
    excluded: tuple[ExcludedNotice, ...]
    undecidable: tuple[tuple[UndecidableAxis, int], ...]


def _has_marker(name: str | None, markers: frozenset[str]) -> bool:
    return name is not None and any(marker in name for marker in markers)


def effective_date_for(
    category: BusinessCategory, policy: StrategyBacktestPolicy
) -> date:
    """업무별 2026 하한율 개정 시행일 — 포함 여부는 **공고일** 기준(D-6G-14)."""
    if category is BusinessCategory.CONSTRUCTION:
        return policy.effective.construction
    if category is BusinessCategory.SERVICE:
        return policy.effective.service
    return policy.effective.goods


def resolve_a_value(notice: NoticeObservation) -> float | None:
    """A 합산액 해소(D-6G-12). 공사가 아니면 산식에 A 가 없으므로 0. 공사인데 A 가
    없거나 **A 공개일시가 입찰 마감 뒤**면 투찰 시점에 알 수 없어 `None`(제외 ⑥) —
    여기가 업무 축을 보는 유일한 자리다.

    `a_value.total` 이 무엇을 더한 값인지는 생산 쪽 계약이다(스키마 §3.3 — 일곱 항목,
    품질관리비는 술어가 참일 때만, 표준시장단가금액은 제외). 소비 쪽에서 다시 더하거나
    빼지 않는다."""
    if notice.category is not BusinessCategory.CONSTRUCTION:
        return 0.0
    if notice.a_value is None:
        return None
    if notice.a_value.open_at > notice.bid_close_at:
        return None
    return notice.a_value.total


def _is_local(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    """⑪ — authoritative 판정이 없으므로 **항상 거짓**이다(D-6G-21). 기관 코드로
    추측하지 않는다: 추측은 보조 민감도 판에서만, 추정 표지와 함께."""
    del row
    return False


def _is_foreign(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    """⑫ — 외자 행 자체가 수집되지 않아 구조적으로 거짓이다(스키마 §4)."""
    del row
    return False


def _is_bid_method_absent(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return row.notice.successful_bid_method_name is None


def _is_small_sum_quote(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return _has_marker(row.notice.successful_bid_method_name, _SMALL_SUM_QUOTE_MARKERS)


def _is_sme_competition(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return _has_marker(row.notice.successful_bid_method_name, _SME_COMPETITION_MARKERS)


def _is_ship_manufacturing(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    """⑧ — 조달 분류 코드가 정본이지만 그 코드 공간이 미확정이라, 오늘은 낙찰방법명
    표지로만 발화한다(한계는 `UndecidableAxis` 로 공시)."""
    code = row.notice.procurement_class_code
    if code is not None and code in _SHIP_MANUFACTURING_CLASS_CODES:
        return True
    return row.notice.category is BusinessCategory.GOODS and _has_marker(
        row.notice.successful_bid_method_name, _SHIP_MANUFACTURING_MARKERS
    )


def _is_not_qualification(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return not _has_marker(
        row.notice.successful_bid_method_name, _QUALIFICATION_SCREENING_MARKERS
    )


def _is_before_effective(row: SnapshotRow, policy: StrategyBacktestPolicy) -> bool:
    return row.notice.noticed_on < effective_date_for(row.notice.category, policy)


def _is_rebid(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return row.notice.notice_ordinal != _FIRST_NOTICE_ORDINAL or _has_marker(
        row.notice.progress_division, _REBID_MARKERS
    )


def _is_single_prearranged(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    """④ — 결정방법이 부재하면 판정할 수 없으므로 제외 쪽으로 접는다(fail-closed)."""
    return (
        row.notice.prearranged_price_decision_method != _MULTIPLE_PREARRANGED_PRICE_NAME
    )


def _is_floor_rate_unusable(row: SnapshotRow, policy: StrategyBacktestPolicy) -> bool:
    rate = row.notice.floor_rate
    return rate is None or not policy.floor.contains(rate)


def _is_reserve_range_absent(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    begin = row.notice.reserve_range_begin_rate
    end = row.notice.reserve_range_end_rate
    return begin is None or end is None or end <= begin


def _is_reserve_draw_incomplete(
    row: SnapshotRow, policy: StrategyBacktestPolicy
) -> bool:
    prices = row.outcome.reserve_prices
    drawn = row.outcome.drawn_serial_numbers
    if prices is None or drawn is None:
        return True
    return (
        len(prices) != policy.institution.reserve_price_count
        or len(drawn) != policy.institution.draw_count
    )


def _is_a_value_unusable(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return resolve_a_value(row.notice) is None


def _is_pure_cost_absent(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return (
        row.notice.category is BusinessCategory.CONSTRUCTION
        and row.notice.pure_construction_cost is None
    )


def _is_bidder_amount_absent(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return not row.outcome.bidder_rows or any(
        bidder.amount is None for bidder in row.outcome.bidder_rows
    )


_Predicate = Callable[[SnapshotRow, StrategyBacktestPolicy], bool]

# 규칙 표 — 순서대로 먼저 걸리는 사유가 기록된다. 제도 축(⑪⑫)과 낙찰방법(①⑦⑧⑩)을
# 먼저 보는 이유: 그 공고들은 애초에 이 실험의 지표 정의가 성립하지 않는 자리라,
# 값 결측으로 뒤늦게 세면 「무엇 때문에 빠졌는가」가 뒤섞인다(위협 모델 ④).
_RULES: Final[tuple[tuple[ExclusionReason, _Predicate], ...]] = (
    (ExclusionReason.LOCAL_GOVERNMENT, _is_local),
    (ExclusionReason.FOREIGN_CAPITAL, _is_foreign),
    (ExclusionReason.BID_METHOD_ABSENT, _is_bid_method_absent),
    (ExclusionReason.SMALL_SUM_QUOTE, _is_small_sum_quote),
    (ExclusionReason.SME_COMPETITION_SCREENING, _is_sme_competition),
    (ExclusionReason.SHIP_MANUFACTURING, _is_ship_manufacturing),
    (ExclusionReason.NOT_QUALIFICATION_SCREENING, _is_not_qualification),
    (ExclusionReason.FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY, _is_before_effective),
    (ExclusionReason.REBID_OR_AMENDED, _is_rebid),
    (ExclusionReason.SINGLE_PREARRANGED_PRICE, _is_single_prearranged),
    (ExclusionReason.FLOOR_RATE_ABSENT_OR_OUT_OF_BAND, _is_floor_rate_unusable),
    (ExclusionReason.RESERVE_PRICE_RANGE_ABSENT, _is_reserve_range_absent),
    (ExclusionReason.RESERVE_DRAW_INCOMPLETE, _is_reserve_draw_incomplete),
    (ExclusionReason.A_VALUE_ABSENT_OR_LATE, _is_a_value_unusable),
    (ExclusionReason.PURE_CONSTRUCTION_COST_ABSENT, _is_pure_cost_absent),
    (ExclusionReason.BIDDER_AMOUNT_ABSENT, _is_bidder_amount_absent),
)

EXCLUSION_RULE_ORDER: Final[tuple[ExclusionReason, ...]] = (
    *(reason for reason, _ in _RULES),
    ExclusionReason.NO_ELIGIBLE_BIDDER,
    ExclusionReason.TIED_LOWEST,
)


def _structural_reason(
    row: SnapshotRow, policy: StrategyBacktestPolicy
) -> ExclusionReason | None:
    for reason, predicate in _RULES:
        if predicate(row, policy):
            return reason
    return None


def _pure_cost_floor(row: SnapshotRow, policy: StrategyBacktestPolicy) -> float | None:
    cost = row.notice.pure_construction_cost
    if row.notice.category is not BusinessCategory.CONSTRUCTION or cost is None:
        return None
    return pure_construction_floor(
        pure_construction_cost=cost,
        base_amount=row.notice.base_amount,
        planned_price=row.outcome.planned_price,
        ratio=policy.floor.pure_construction_cost_ratio,
    )


@dataclass(frozen=True)
class _Resolved:
    """규칙 표를 통과한 행의 해소된 값 — `None` 이 하나도 없다."""

    floor_rate: float
    a_value_total: float
    begin_rate: float
    end_rate: float
    amounts: tuple[float, ...]


def _resolve(row: SnapshotRow) -> _Resolved | None:
    """규칙 표가 이미 걸렀음을 타입에 반영한다 — 재검증이 아니라 좁히기다."""
    rate = row.notice.floor_rate
    a_total = resolve_a_value(row.notice)
    begin = row.notice.reserve_range_begin_rate
    end = row.notice.reserve_range_end_rate
    amounts = tuple(
        bidder.amount for bidder in row.outcome.bidder_rows if bidder.amount is not None
    )
    if rate is None or a_total is None or begin is None or end is None:
        return None
    return _Resolved(rate, a_total, begin, end, amounts)


def _admit(
    row: SnapshotRow, policy: StrategyBacktestPolicy
) -> AdmittedNotice | ExclusionReason:
    """구조 제외를 통과한 행에 실제 하한가와 적격 투찰자 통계를 붙인다. ⑭⑮ 는 이
    계산이 있어야 판정되므로 여기서 난다."""
    structural = _structural_reason(row, policy)
    if structural is not None:
        return structural
    resolved = _resolve(row)
    if resolved is None:  # 규칙 표가 이미 걸렀다 — 도달하지 않는 안전망.
        return ExclusionReason.FLOOR_RATE_ABSENT_OR_OUT_OF_BAND
    actual_floor = floor_price(
        planned_price=row.outcome.planned_price,
        floor_rate=resolved.floor_rate,
        a_value_total=resolved.a_value_total,
    )
    pure_floor = _pure_cost_floor(row, policy)
    floors = (actual_floor,) if pure_floor is None else (actual_floor, pure_floor)
    eligible = [amount for amount in resolved.amounts if is_eligible(amount, floors)]
    if not eligible:
        return ExclusionReason.NO_ELIGIBLE_BIDDER
    lowest = min(eligible)
    if sum(1 for amount in eligible if amount == lowest) > 1:
        return ExclusionReason.TIED_LOWEST
    return AdmittedNotice(
        row=row,
        floor_rate=resolved.floor_rate,
        a_value_total=resolved.a_value_total,
        reserve_range_begin_rate=resolved.begin_rate,
        reserve_range_end_rate=resolved.end_rate,
        actual_floor_price=actual_floor,
        pure_cost_floor=pure_floor,
        bid_amounts=resolved.amounts,
        lowest_eligible_amount=lowest,
    )


def undecidable_counts(
    admitted: Sequence[AdmittedNotice],
) -> tuple[tuple[UndecidableAxis, int], ...]:
    """**제외 규칙이 발화하지 못한** 축의 표본 수(D-6G-21). 「해당 없음」과 「가르지
    못했다」는 다르고, 후자를 0 으로 적으면 판정문이 거짓말을 한다."""
    return (
        (UndecidableAxis.LOCAL_GOVERNMENT, len(admitted)),
        (
            UndecidableAxis.SHIP_MANUFACTURING_CLASS,
            sum(
                1
                for item in admitted
                if item.row.notice.category is BusinessCategory.GOODS
            ),
        ),
    )


def admit_rows(
    rows: Sequence[SnapshotRow], policy: StrategyBacktestPolicy
) -> AdmissionResult:
    """전략을 보기 전에 한 번만 돈다 — 결과는 전략 전부가 공유하는 단일 집합이다."""
    admitted: list[AdmittedNotice] = []
    excluded: list[ExcludedNotice] = []
    for row in rows:
        outcome = _admit(row, policy)
        if isinstance(outcome, AdmittedNotice):
            admitted.append(outcome)
        else:
            excluded.append(ExcludedNotice(row.notice.notice_key_hash, outcome))
    return AdmissionResult(
        tuple(admitted), tuple(excluded), undecidable_counts(admitted)
    )


def exclusion_counts(
    excluded: Sequence[ExcludedNotice],
) -> tuple[tuple[ExclusionReason, int], ...]:
    """사유별 계수 — 사유 전부를 낸다(0건도 공시한다, 「제외의 정직성」)."""
    tally = dict.fromkeys(EXCLUSION_RULE_ORDER, 0)
    for item in excluded:
        tally[item.reason] += 1
    return tuple((reason, tally[reason]) for reason in EXCLUSION_RULE_ORDER)


def bid_method_fill_rate(rows: Sequence[SnapshotRow]) -> float:
    """낙찰방법 칸의 채움률(D-6G-22) — 판정문에 공시한다. 채움률이 낮으면 ①⑦⑩ 제외가
    실제로는 「판정 못 함」이라는 뜻이다."""
    if not rows:
        return 0.0
    filled = sum(1 for row in rows if row.notice.successful_bid_method_name is not None)
    return filled / len(rows)
