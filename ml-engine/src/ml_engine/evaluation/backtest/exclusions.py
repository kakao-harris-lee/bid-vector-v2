"""`ml_engine.evaluation.backtest.exclusions` — 제외 판정과 승인(D-6G-13). 제외는
**입력 단계에서만** 일어나고 **전략을 보기 전에** 끝난다 — 그래서 어떤 전략에게도
유리한 제외가 성립하지 않는다(위협 모델 ④, 우회 ④). 전략 간 집합 동일성은
`run` 이 단언한다.

제외는 침묵이 아니라 기록이다 — 사유 열다섯은 enum 한 줄씩이고, 규칙 표의 **순서대로**
먼저 걸리는 사유가 기록된다(5C-2 `windows._exclusion_reason` 과 같은 형태).

**승인된 행은 좁혀진 타입을 갖는다** — `AdmittedNotice` 는 `floor_rate` 가 `None` 이
아니고, A 가 해소됐고, 실제 하한가와 적격 투찰자 중 최저 금액이 이미 계산돼 있다. 채점이
그 값들을 다시 구하지 않는다(판정 단일 지점).

**알려진 제한(⑧ 선박 제조 물품)**: 판정 입력이 낙찰방법명뿐이다 — 스냅숏에 공고명을
싣지 않으므로(개인정보·식별자 최소화, D-6G-2) 방법명이 그 사실을 말하지 않는 공고는
걸러지지 않는다. 판정문에 한계로 적는다.
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
    """제외 사유 어휘(D-6G-13 ①~⑮). 새 사유는 코드 분기가 아니라 이 enum 한 줄이고,
    `run` 이 사유별로 계수해 판정 JSON 에 싣는다."""

    LOCAL_GOVERNMENT = "LOCAL_GOVERNMENT"
    FOREIGN_CAPITAL = "FOREIGN_CAPITAL"
    NOT_QUALIFICATION_SCREENING = "NOT_QUALIFICATION_SCREENING"
    SMALL_SUM_QUOTE = "SMALL_SUM_QUOTE"
    SME_COMPETITION_SCREENING = "SME_COMPETITION_SCREENING"
    SHIP_MANUFACTURING = "SHIP_MANUFACTURING"
    FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY = "FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY"
    REBID_OR_AMENDED = "REBID_OR_AMENDED"
    SINGLE_PREARRANGED_PRICE = "SINGLE_PREARRANGED_PRICE"
    FLOOR_RATE_ABSENT_OR_OUT_OF_BAND = "FLOOR_RATE_ABSENT_OR_OUT_OF_BAND"
    RESERVE_DRAW_INCOMPLETE = "RESERVE_DRAW_INCOMPLETE"
    A_VALUE_ABSENT_OR_LATE = "A_VALUE_ABSENT_OR_LATE"
    PURE_CONSTRUCTION_COST_ABSENT = "PURE_CONSTRUCTION_COST_ABSENT"
    NO_ELIGIBLE_BIDDER = "NO_ELIGIBLE_BIDDER"
    TIED_LOWEST = "TIED_LOWEST"


# 낙찰방법명 어휘 — 조달청 OpenAPI 참고자료의 `sucsfbidMthdNm` 샘플 문면에서 왔다
# (02 §2 축어 인용). **순서가 있다**: 아래 셋을 먼저 보고 나서 적격심사를 본다 —
# 소액수의·중소기업자간 공고의 방법명에도 「심사」 계열 어휘가 함께 나타나기 때문이다.
_SMALL_SUM_QUOTE_MARKERS: Final[frozenset[str]] = frozenset({"소액수의"})
_SME_COMPETITION_MARKERS: Final[frozenset[str]] = frozenset({"중소기업자간"})
_SHIP_MANUFACTURING_MARKERS: Final[frozenset[str]] = frozenset({"선박"})
_QUALIFICATION_SCREENING_MARKERS: Final[frozenset[str]] = frozenset(
    {"적격심사", "계약이행능력심사"}
)
_MULTIPLE_PREARRANGED_PRICE_NAME: Final[str] = "복수예가"
_FIRST_NOTICE_ORDINAL: Final[int] = 1


@dataclass(frozen=True)
class ExcludedNotice:
    """제외된 공고 하나와 사유. 식별자는 해시뿐이고 보고에는 사유별 계수만 나간다."""

    notice_key_hash: str
    reason: ExclusionReason


@dataclass(frozen=True)
class AdmittedNotice:
    """승인된 공고 — 채점에 필요한 실제값이 이미 해소된 좁혀진 타입."""

    row: SnapshotRow
    floor_rate: float
    a_value_total: float
    actual_floor_price: float
    pure_cost_floor: float | None
    lowest_eligible_amount: float
    tied_lowest_count: int

    @property
    def eligibility_floors(self) -> tuple[float, ...]:
        if self.pure_cost_floor is None:
            return (self.actual_floor_price,)
        return (self.actual_floor_price, self.pure_cost_floor)


@dataclass(frozen=True)
class AdmissionResult:
    admitted: tuple[AdmittedNotice, ...]
    excluded: tuple[ExcludedNotice, ...]


def _has_marker(name: str, markers: frozenset[str]) -> bool:
    return any(marker in name for marker in markers)


def effective_date_for(
    category: BusinessCategory, policy: StrategyBacktestPolicy
) -> date:
    """업무별 2026 하한율 개정 시행일 — 포함 여부는 **공고일** 기준(D-6G-14)."""
    if category is BusinessCategory.CONSTRUCTION:
        return policy.effective.construction
    if category is BusinessCategory.SERVICE:
        return policy.effective.service
    return policy.effective.goods


def resolve_a_value(
    notice: NoticeObservation,
) -> float | None:
    """A 합산액 해소(D-6G-12). 공사가 아니면 산식에 A 가 없으므로 0. 공사인데 A 가
    없거나 **A 공개일시가 입찰 마감 뒤**면 투찰 시점에 알 수 없는 값이라 `None`
    (제외 사유 ⑥) — 여기가 업무 축을 보는 유일한 자리다."""
    if notice.category is not BusinessCategory.CONSTRUCTION:
        return 0.0
    if notice.a_value is None:
        return None
    if notice.a_value.open_at > notice.bid_close_at:
        return None
    return notice.a_value.total


def _is_local(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return row.notice.is_local_government


def _is_foreign(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return row.notice.is_foreign_capital


def _is_small_sum_quote(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return _has_marker(row.notice.successful_bid_method_name, _SMALL_SUM_QUOTE_MARKERS)


def _is_sme_competition(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return _has_marker(row.notice.successful_bid_method_name, _SME_COMPETITION_MARKERS)


def _is_ship_manufacturing(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
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
    return row.notice.notice_ordinal != _FIRST_NOTICE_ORDINAL


def _is_single_prearranged(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return (
        row.notice.prearranged_price_decision_method != _MULTIPLE_PREARRANGED_PRICE_NAME
    )


def _is_floor_rate_unusable(row: SnapshotRow, policy: StrategyBacktestPolicy) -> bool:
    rate = row.notice.floor_rate
    return rate is None or not policy.floor.contains(rate)


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


_Predicate = Callable[[SnapshotRow, StrategyBacktestPolicy], bool]

# 규칙 표 — 순서대로 먼저 걸리는 사유가 기록된다. 제도 축(⑪⑫)과 낙찰방법(①⑦⑧⑩)을
# 먼저 보는 이유: 그 공고들은 애초에 이 실험의 지표 정의가 성립하지 않는 자리라,
# 값 결측(②⑤⑥⑨)으로 뒤늦게 세면 「무엇 때문에 빠졌는가」가 뒤섞인다(위협 모델 ④).
_RULES: Final[tuple[tuple[ExclusionReason, _Predicate], ...]] = (
    (ExclusionReason.LOCAL_GOVERNMENT, _is_local),
    (ExclusionReason.FOREIGN_CAPITAL, _is_foreign),
    (ExclusionReason.SMALL_SUM_QUOTE, _is_small_sum_quote),
    (ExclusionReason.SME_COMPETITION_SCREENING, _is_sme_competition),
    (ExclusionReason.SHIP_MANUFACTURING, _is_ship_manufacturing),
    (ExclusionReason.NOT_QUALIFICATION_SCREENING, _is_not_qualification),
    (ExclusionReason.FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY, _is_before_effective),
    (ExclusionReason.REBID_OR_AMENDED, _is_rebid),
    (ExclusionReason.SINGLE_PREARRANGED_PRICE, _is_single_prearranged),
    (ExclusionReason.FLOOR_RATE_ABSENT_OR_OUT_OF_BAND, _is_floor_rate_unusable),
    (ExclusionReason.RESERVE_DRAW_INCOMPLETE, _is_reserve_draw_incomplete),
    (ExclusionReason.A_VALUE_ABSENT_OR_LATE, _is_a_value_unusable),
    (ExclusionReason.PURE_CONSTRUCTION_COST_ABSENT, _is_pure_cost_absent),
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


def _admit(
    row: SnapshotRow, policy: StrategyBacktestPolicy
) -> AdmittedNotice | ExclusionReason:
    """구조 제외를 통과한 행에 실제 하한가와 적격 투찰자 통계를 붙인다. ⑭⑮ 는 이
    계산이 있어야 판정되므로 여기서 난다."""
    structural = _structural_reason(row, policy)
    if structural is not None:
        return structural
    rate = row.notice.floor_rate
    a_total = resolve_a_value(row.notice)
    if rate is None or a_total is None:  # 규칙 표가 이미 걸렀다(타입 좁히기).
        return ExclusionReason.FLOOR_RATE_ABSENT_OR_OUT_OF_BAND
    actual_floor = floor_price(
        planned_price=row.outcome.planned_price,
        floor_rate=rate,
        a_value_total=a_total,
    )
    pure_floor = _pure_cost_floor(row, policy)
    floors = (actual_floor,) if pure_floor is None else (actual_floor, pure_floor)
    eligible = [
        bidder.amount
        for bidder in row.outcome.bidder_rows
        if is_eligible(bidder.amount, floors)
    ]
    if not eligible:
        return ExclusionReason.NO_ELIGIBLE_BIDDER
    lowest = min(eligible)
    tied = sum(1 for amount in eligible if amount == lowest)
    if tied > 1:
        return ExclusionReason.TIED_LOWEST
    return AdmittedNotice(
        row=row,
        floor_rate=rate,
        a_value_total=a_total,
        actual_floor_price=actual_floor,
        pure_cost_floor=pure_floor,
        lowest_eligible_amount=lowest,
        tied_lowest_count=tied,
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
    return AdmissionResult(tuple(admitted), tuple(excluded))


def exclusion_counts(
    excluded: Sequence[ExcludedNotice],
) -> tuple[tuple[ExclusionReason, int], ...]:
    """사유별 계수 — 사유 열다섯 전부를 낸다(0건도 공시한다, 「제외의 정직성」)."""
    tally = dict.fromkeys(EXCLUSION_RULE_ORDER, 0)
    for item in excluded:
        tally[item.reason] += 1
    return tuple((reason, tally[reason]) for reason in EXCLUSION_RULE_ORDER)
