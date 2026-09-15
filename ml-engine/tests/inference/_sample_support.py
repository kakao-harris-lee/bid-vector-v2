"""M5/5D-2·5D-3 — wire `CompetitionSample` 조립 test 지원(golden `_adapter.py`·
`_policy_support.py`와 같은 관례). `test_observations.py`·`test_distribution.py`·
`test_engine.py`가 공유한다(중복 금지).

M5/5D-3(D-5D3-2) — `agency_id`/`category_code`(M2/2F additive) 기본값은 두 축 모두
`MISSING_REASON_NOT_COLLECTED_YET`다(허용되는 유일한 결측 사유, wire 관례와 일치) —
이 기본값이라야 기존(5D-2) test 가 세그먼트 게이트에서 조용히 거부되지 않고
D-5D3-5 비트 동일 회귀가 성립한다. `agency_id`/`category_code`에 문자열을 주면 그
값으로, `int`(예: `common_pb2.MISSING_REASON_UNKNOWN`)를 주면 그 결측 사유로,
`None`을 주면 oneof 자체를 비워 둔다(판독 거부 재현용)."""

from __future__ import annotations

from ml_engine.contracts import common_pb2, features_pb2

BASE_AMOUNT_WON = 1_000_000_000

# 15개 예비가 비율 — center(closed-form draw mean) == 1.0, 정확히 [0.8, 1.2] 밴드 안,
# 표본이 전부 다른 값이라 pvariance > 0(DEGENERATE_VARIANCE 미발동).
VALID_RATIOS: tuple[float, ...] = (
    0.85,
    0.86,
    0.87,
    0.88,
    0.89,
    0.90,
    0.95,
    1.00,
    1.05,
    1.10,
    1.11,
    1.12,
    1.13,
    1.14,
    1.15,
)


def money(amount_won: int, **overrides: object) -> common_pb2.Money:
    result = common_pb2.Money(
        amount_won=amount_won,
        currency=common_pb2.CURRENCY_KRW,
        basis=common_pb2.BASIS_BASE_AMOUNT,
        vat_treatment=common_pb2.VAT_TREATMENT_INCLUSIVE,
        provenance=common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED,
    )
    for key, value in overrides.items():
        setattr(result, key, value)
    return result


def _set_text_fact(fact: object, value: str | int | None) -> None:
    """`AgencyIdFact`/`CategoryCodeFact`(oneof `value`/`missing`) 공용 설정 — `str`은
    값, `int`는 결측 사유, `None`은 oneof 자체를 비워 둔다(판독 거부 재현용, M5/5D-3)."""
    if value is None:
        return
    if isinstance(value, str):
        fact.value = value  # type: ignore[attr-defined]
    else:
        fact.missing = value  # type: ignore[attr-defined]


def competition_sample(
    *,
    base_amount_won: int = BASE_AMOUNT_WON,
    base_amount_overrides: dict[str, object] | None = None,
    reserve_price_ratios: tuple[float, ...] | None = VALID_RATIOS,
    reserve_price_overrides: dict[int, int] | None = None,
    reserve_price_money_overrides: dict[int, dict[str, object]] | None = None,
    selected_numbers: tuple[int, ...] = (),
    observed_bid_rate: str = "0.95",
    award_rate: str | None = None,
    provenance: int = common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN,
    with_reserve_draw: bool = True,
    agency_id: str | int | None = common_pb2.MISSING_REASON_NOT_COLLECTED_YET,
    category_code: str | int | None = common_pb2.MISSING_REASON_NOT_COLLECTED_YET,
) -> features_pb2.CompetitionSample:
    sample = features_pb2.CompetitionSample(
        observed_bid_rate=common_pb2.Rate(fraction=observed_bid_rate),
        origin=common_pb2.BID_RATE_ORIGIN_OBSERVED,
        base_amount=money(base_amount_won, **(base_amount_overrides or {})),
        base_amount_provenance_label=provenance,
        opened_on="2026-01-01",
    )
    _set_text_fact(sample.agency_id, agency_id)
    _set_text_fact(sample.category_code, category_code)
    if award_rate is not None:
        sample.award_rate.fraction = award_rate
    if with_reserve_draw:
        prices = [
            round(ratio * base_amount_won) for ratio in (reserve_price_ratios or ())
        ]
        for index, override_won in (reserve_price_overrides or {}).items():
            prices[index] = override_won
        money_overrides = reserve_price_money_overrides or {}
        for index, price_won in enumerate(prices):
            sample.reserve_draw.reserve_prices.append(
                money(price_won, **money_overrides.get(index, {}))
            )
        sample.reserve_draw.selected_numbers.extend(selected_numbers)
    return sample
