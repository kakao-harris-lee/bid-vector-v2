"""`ml_engine.evaluation.backtest.floor` — 낙찰하한가 산식(D-6G-12). 규정 문면이
authoritative 다(D-ML-3 관례) — 출처와 도출은 `_workspace/m6-6g/02_p3-floor-formula.md`
§3.

**산식 하나, 업무 차이는 A 가 진다.** 공사(시설공사) 적격심사의 입찰가격 평점식은
`X = (입찰가격 - A) ÷ (예정가격 - A)` 이므로 하한가는 `(예정가격 - A) * r + A` 다.
국가계약 물품·일반용역은 산식에 A 가 없어 `예정가격 * r` 인데, 그것은 같은 식에
`A = 0` 을 넣은 것과 같다. 그래서 **산식은 한 줄이고, 업무 축은 「A 를 얼마로 보는가」로만
들어온다**(그 판정은 `exclusions.resolve_a_value` 가 진다 — 여기서 업무를 다시 보지
않는다, 판정 단일 지점).

**반올림을 넣지 않는다.** 규정은 금액이 아니라 **비율**을 소수 다섯째 자리에서 반올림해
평가하고, `sucsfbidLwltRate` 가 주는 값(`87.745` 등)이 이미 그 반올림 경계를 반영한
실무 하한율이다 — `투찰금액 >= (예정가격 - A) * r + A` 로 쓰면 규정과 동치다(02 §4.1).
"""

from __future__ import annotations


def floor_price(
    *, planned_price: float, floor_rate: float, a_value_total: float
) -> float:
    """낙찰하한가 = (예정가격 - A) * 하한율 + A. 공사가 아니면 `a_value_total` 이 0 이라
    `예정가격 * 하한율` 로 접힌다(같은 식의 특수경우 — 두 산식을 따로 두지 않는다)."""
    return (planned_price - a_value_total) * floor_rate + a_value_total


def pure_construction_floor(
    *,
    pure_construction_cost: float,
    base_amount: float,
    planned_price: float,
    ratio: float,
) -> float:
    """공사의 **두 번째 실격선** — 순공사원가의 `ratio` 배 미만 투찰은 낙찰자가 되지
    못한다(국가계약법 시행령 제42조 계열).

    **근사임을 명시한다**: API 가 주는 `bssAmtPurcnstcst` 는 **기초금액** 기준이고
    규정의 배제 기준은 **예정가격** 기준이다. 사정률(예정가격 ÷ 기초금액)로 환산하지만
    원가 구성비가 사정률에 비례한다는 가정이 들어간다 — 판정문에 한계로 적는다
    (02 §4.3)."""
    return pure_construction_cost * (planned_price / base_amount) * ratio


def is_eligible(amount: float, floors: tuple[float, ...]) -> bool:
    """실격선 전부를 넘겨야 적격이다 — 하한가 하나, 공사는 순공사원가선까지 둘."""
    return all(amount >= threshold for threshold in floors)


def basis_points_above(amount: float, reference: float) -> float:
    """기준 대비 bp — `(금액 ÷ 기준 - 1) * 10⁴`. 지표 ②(D-6G-4)의 유일 정의."""
    return (amount / reference - 1.0) * 10_000.0
