"""`ml_engine.evaluation.backtest.rules` — 제외 규칙 표와 그 어휘(D-6G-13·16·19·21).
`exclusions` 에서 분리한 이유는 파일 크기다(설계 래칫 500줄) — 타입과 승인 조립은 그쪽에
두고, 「어떤 공고가 왜 빠지는가」의 술어만 여기 모았다.

규칙은 **순서가 있는 표**다. 제도 축(⑪⑫)과 낙찰방법(①⑦⑧⑩)을 먼저 보는 이유: 그
공고들은 애초에 이 실험의 지표 정의가 성립하지 않는 자리라, 값 결측으로 뒤늦게 세면
「무엇 때문에 빠졌는가」가 뒤섞인다(위협 모델 ④).

**전략을 보지 않는다** — 이 모듈의 어떤 함수도 전략을 인자로 받지 않는다. 그래서 어떤
전략에게도 유리한 제외가 성립하지 않는다(우회 ④).
"""

from __future__ import annotations

from collections.abc import Callable
from datetime import date
from typing import Final

from ml_engine.evaluation.backtest.floor import pure_construction_floor
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.backtest.reasons import ExclusionReason
from ml_engine.evaluation.backtest.snapshot import (
    BusinessCategory,
    NoticeObservation,
    SnapshotRow,
)

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


def _is_notice_date_absent(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return row.notice.noticed_on is None


def _is_bid_close_at_absent(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return row.notice.bid_close_at is None


def _is_opening_date_absent(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return row.outcome.opened_on is None


def _is_planned_price_absent(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    price = row.outcome.planned_price
    return price is None or price <= 0.0


def resolve_a_value(notice: NoticeObservation) -> float | None:
    """A 합산액 해소(D-6G-12). 여기가 업무 축을 보는 유일한 자리다.

    - 공사가 아니면 산식에 A 가 없으므로 **0**.
    - 공사면 `bid_price_formula_a_applicable`(`bidPrceCalclAYn`, 공사 전용 필수)이
      정본이다. **거짓이면 A값 공고가 아니라서 0** — 예전처럼 `a_value` 부재만 보고
      제외하면 A 를 쓰지 않는 공사가 통째로 빠진다. 판정 불가(`None`)면 제외.
    - A값 공고인데 값이 없거나 **A 공개일시가 입찰 마감 뒤**면 투찰 시점에 알 수 없어
      `None`(제외 ⑥).

    `a_value.total` 이 무엇을 더한 값인지는 생산 쪽 계약이다(스키마 §3.3 — 일곱 항목,
    품질관리비는 술어가 참일 때만, 표준시장단가금액은 제외). 소비 쪽에서 다시 더하거나
    빼지 않는다."""
    if notice.category is not BusinessCategory.CONSTRUCTION:
        return 0.0
    if notice.bid_close_at is None:
        return None
    applicable = notice.bid_price_formula_a_applicable
    if applicable is None:
        return None
    if not applicable:
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
    """공고일이 없으면 여기서 판정하지 않는다 — `NOTICE_DATE_ABSENT` 가 먼저 걸렀고,
    **개찰일로 대체하지 않는다**(v2 가 그렇게 접혀 이 규칙이 개찰일 기준으로 돌았다)."""
    noticed_on = row.notice.noticed_on
    if noticed_on is None:
        return False
    return noticed_on < effective_date_for(row.notice.category, policy)


def _is_rebid(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    return row.notice.notice_ordinal != _FIRST_NOTICE_ORDINAL or _has_marker(
        row.outcome.progress_division, _REBID_MARKERS
    )


def _is_single_prearranged(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    """④ — 결정방법이 부재하면 판정할 수 없으므로 제외 쪽으로 접는다(fail-closed)."""
    return (
        row.notice.prearranged_price_decision_method != _MULTIPLE_PREARRANGED_PRICE_NAME
    )


def _is_floor_rate_unusable(row: SnapshotRow, policy: StrategyBacktestPolicy) -> bool:
    rate = row.notice.floor_rate
    return rate is None or not policy.floor.contains(rate)


def _is_base_amount_unusable(row: SnapshotRow, _policy: StrategyBacktestPolicy) -> bool:
    """투찰 시점 기초금액 — 스냅숏이 공개일시 조건을 이미 걸어 놓았다(스키마 §3.4).
    여기서는 그 결과가 비었는지, 그리고 금액이 양수인지만 본다."""
    amount = row.notice.base_amount
    return amount is None or amount <= 0.0


def _is_opening_base_amount_absent(
    row: SnapshotRow, _policy: StrategyBacktestPolicy
) -> bool:
    amount = row.outcome.opening_base_amount
    return amount is None or amount <= 0.0


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
    # 값 결측은 **그 값을 쓰는 규칙보다 먼저** 온다 — 공고일이 없으면 시행일 경계를
    # 판정할 수 없고, 마감이 없으면 A 의 공개 시점 절단이 서지 않는다(v3, D-6G-28).
    (ExclusionReason.NOTICE_DATE_ABSENT, _is_notice_date_absent),
    (ExclusionReason.FLOOR_RATE_EFFECTIVE_DATE_BOUNDARY, _is_before_effective),
    (ExclusionReason.REBID_OR_AMENDED, _is_rebid),
    (ExclusionReason.SINGLE_PREARRANGED_PRICE, _is_single_prearranged),
    (ExclusionReason.FLOOR_RATE_ABSENT_OR_OUT_OF_BAND, _is_floor_rate_unusable),
    (ExclusionReason.BASE_AMOUNT_ABSENT_OR_LATE, _is_base_amount_unusable),
    (ExclusionReason.OPENING_BASE_AMOUNT_ABSENT, _is_opening_base_amount_absent),
    (ExclusionReason.RESERVE_PRICE_RANGE_ABSENT, _is_reserve_range_absent),
    (ExclusionReason.OPENING_DATE_ABSENT, _is_opening_date_absent),
    (ExclusionReason.PLANNED_PRICE_ABSENT, _is_planned_price_absent),
    (ExclusionReason.RESERVE_DRAW_INCOMPLETE, _is_reserve_draw_incomplete),
    (ExclusionReason.BID_CLOSE_AT_ABSENT, _is_bid_close_at_absent),
    (ExclusionReason.A_VALUE_ABSENT_OR_LATE, _is_a_value_unusable),
    (ExclusionReason.PURE_CONSTRUCTION_COST_ABSENT, _is_pure_cost_absent),
    (ExclusionReason.BIDDER_AMOUNT_ABSENT, _is_bidder_amount_absent),
)

EXCLUSION_RULE_ORDER: Final[tuple[ExclusionReason, ...]] = (
    *(reason for reason, _ in _RULES),
    ExclusionReason.NO_ELIGIBLE_BIDDER,
    ExclusionReason.TIED_LOWEST,
)


def structural_reason(
    row: SnapshotRow, policy: StrategyBacktestPolicy
) -> ExclusionReason | None:
    for reason, predicate in _RULES:
        if predicate(row, policy):
            return reason
    return None


def pure_cost_floor(
    row: SnapshotRow,
    policy: StrategyBacktestPolicy,
    opening_base_amount: float,
    planned_price: float,
) -> float | None:
    """환산 분모는 **개찰결과 출처** 기초금액이다 — 순공사원가가 그 축의 값이다."""
    cost = row.notice.pure_construction_cost
    if row.notice.category is not BusinessCategory.CONSTRUCTION or cost is None:
        return None
    return pure_construction_floor(
        pure_construction_cost=cost,
        base_amount=opening_base_amount,
        planned_price=planned_price,
        ratio=policy.floor.pure_construction_cost_ratio,
    )
