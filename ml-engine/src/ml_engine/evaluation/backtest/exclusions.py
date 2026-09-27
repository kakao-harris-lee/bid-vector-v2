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

from ml_engine.evaluation.backtest.floor import floor_price, is_eligible
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.backtest.reasons import ExclusionReason, UndecidableAxis
from ml_engine.evaluation.backtest.rules import (
    EXCLUSION_RULE_ORDER,
    pure_cost_floor,
    resolve_a_value,
    structural_reason,
)
from ml_engine.evaluation.backtest.snapshot import BusinessCategory, SnapshotRow


@dataclass(frozen=True)
class ExcludedNotice:
    """제외된 공고 하나와 사유. 식별자는 해시뿐이고 보고에는 사유별 계수만 나간다."""

    notice_key_hash: str
    reason: ExclusionReason


@dataclass(frozen=True)
class AdmittedNotice:
    """승인된 공고 — 채점과 전략에 필요한 값이 **전부 해소된** 좁혀진 타입.

    기초금액이 둘인 것이 이 타입의 핵심이다(D-6G-19). `base_amount` 는 **투찰 시점**
    (기초금액 조회 출처)이라 전략의 결정 변수가 되고, `opening_base_amount` 는 개찰결과
    출처라 **채점만** 쓴다. 둘을 한 칸에 접으면 전략이 투찰 시점에 몰랐던 값을 입력으로
    쓰게 된다.

    `bid_amounts` 는 투찰금액 전부다(`BIDDER_AMOUNT_ABSENT` 가 결측 있는 공고를 이미
    걸렀으므로 원문 행 수와 같다). 참가자 수는 그 길이로 센다 — 목록 축의
    `participant_count` 는 부재 가능이라 판정 입력으로 쓰지 않는다."""

    row: SnapshotRow
    floor_rate: float
    a_value_total: float
    base_amount: float
    opening_base_amount: float
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
        """실현 사정률(예정가격 ÷ 기초금액) — P-4 적합도의 관측값. 분모는 **개찰결과
        출처**의 기초금액이다(예비가격이 그 축에서 뽑힌다). **개찰 결과**라 전략에
        넘어가지 않는다."""
        return self.row.outcome.planned_price / self.opening_base_amount

    @property
    def base_amount_matches(self) -> bool:
        """두 출처의 기초금액이 같은가 — 다르면 데이터 문제 신호다. 값을 고치지 않고
        **계수만** 공시한다(어느 쪽이 옳은지는 이 레인이 판정할 일이 아니다)."""
        return self.base_amount == self.opening_base_amount


@dataclass(frozen=True)
class AdmissionResult:
    admitted: tuple[AdmittedNotice, ...]
    excluded: tuple[ExcludedNotice, ...]
    undecidable: tuple[tuple[UndecidableAxis, int], ...]


@dataclass(frozen=True)
class _Resolved:
    """규칙 표를 통과한 행의 해소된 값 — `None` 이 하나도 없다."""

    floor_rate: float
    a_value_total: float
    base_amount: float
    opening_base_amount: float
    begin_rate: float
    end_rate: float
    amounts: tuple[float, ...]


def _resolve(row: SnapshotRow) -> _Resolved | None:
    """규칙 표가 이미 걸렀음을 타입에 반영한다 — 재검증이 아니라 좁히기다."""
    rate = row.notice.floor_rate
    a_total = resolve_a_value(row.notice)
    base = row.notice.base_amount
    opening_base = row.outcome.opening_base_amount
    begin = row.notice.reserve_range_begin_rate
    end = row.notice.reserve_range_end_rate
    amounts = tuple(
        bidder.amount for bidder in row.outcome.bidder_rows if bidder.amount is not None
    )
    if rate is None or a_total is None or begin is None or end is None:
        return None
    if base is None or opening_base is None:
        return None
    return _Resolved(rate, a_total, base, opening_base, begin, end, amounts)


def _admit(
    row: SnapshotRow, policy: StrategyBacktestPolicy
) -> AdmittedNotice | ExclusionReason:
    """구조 제외를 통과한 행에 실제 하한가와 적격 투찰자 통계를 붙인다. ⑭⑮ 는 이
    계산이 있어야 판정되므로 여기서 난다."""
    structural: ExclusionReason | None = structural_reason(row, policy)
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
    pure_floor = pure_cost_floor(row, policy, resolved.opening_base_amount)
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
        base_amount=resolved.base_amount,
        opening_base_amount=resolved.opening_base_amount,
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


@dataclass(frozen=True)
class StandardMarketPriceScope:
    """표준시장단가금액 배제의 **영향 범위**(D-6G-17·23). 분모는 **A 값을 가진 공고**다 —
    A 가 없는 공고는 합산액이 없어 애초에 범위 밖이다(스키마 §3.3).

    **계수는 범위이지 크기가 아니다.** 「몇 건이 걸리는가」는 나오지만 「A 가 얼마나
    달라지는가」는 모른다 — `smkpAmt` 의 **금액**을 스냅숏이 싣지 않기 때문이다. 그
    한계는 판정문의 알려진 제한으로 나간다."""

    a_value_present_count: int
    applicable_count: int
    undecidable_count: int


def standard_market_price_scope(
    rows: Sequence[SnapshotRow],
) -> StandardMarketPriceScope:
    """술어가 참인 공고 수 · 판정 불가 수 · 분모를 함께 낸다. 셋을 같이 내는 이유는
    「참이 적다」와 「판정하지 못했다」가 다르기 때문이다."""
    present = [row.notice.a_value for row in rows if row.notice.a_value is not None]
    return StandardMarketPriceScope(
        a_value_present_count=len(present),
        applicable_count=sum(
            1 for item in present if item.standard_market_price_applicable is True
        ),
        undecidable_count=sum(
            1 for item in present if item.standard_market_price_applicable is None
        ),
    )


def _fill_rate(
    rows: Sequence[SnapshotRow], reader: Callable[[SnapshotRow], object]
) -> float:
    if not rows:
        return 0.0
    return sum(1 for row in rows if reader(row) is not None) / len(rows)


def bid_method_fill_rate(rows: Sequence[SnapshotRow]) -> float:
    """낙찰방법 칸의 채움률(D-6G-22) — 판정문에 공시한다. 채움률이 낮으면 ①⑦⑩ 제외가
    실제로는 「판정 못 함」이라는 뜻이다."""
    return _fill_rate(rows, lambda row: row.notice.successful_bid_method_name)


def fill_rates(rows: Sequence[SnapshotRow]) -> tuple[tuple[str, float], ...]:
    """D-6G-22 가 공시를 요구하는 칸들의 채움률. 문서 XML 예제에서 빈 값이 관측된
    칸들이라(스키마 §6) 실수집 뒤 이 수치가 판정의 해석을 바꾼다."""
    return (
        ("successful_bid_method_name", bid_method_fill_rate(rows)),
        (
            "award_method_application_standard",
            _fill_rate(rows, lambda row: row.notice.award_method_application_standard),
        ),
        (
            "application_basis_content",
            _fill_rate(rows, lambda row: row.notice.application_basis_content),
        ),
        (
            "pure_construction_cost",
            _fill_rate(rows, lambda row: row.notice.pure_construction_cost),
        ),
        (
            "bid_price_formula_a_applicable",
            _fill_rate(rows, lambda row: row.notice.bid_price_formula_a_applicable),
        ),
        (
            "reserve_range_end_rate",
            _fill_rate(rows, lambda row: row.notice.reserve_range_end_rate),
        ),
    )
