"""M5/5D-2 — wire `CompetitionSample` 조립 test 지원(golden `_adapter.py`·
`_policy_support.py`와 같은 관례). `test_observations.py`·`test_distribution.py`·
`test_engine.py`가 공유한다(중복 금지)."""

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


def competition_sample(
    *,
    base_amount_won: int = BASE_AMOUNT_WON,
    base_amount_overrides: dict[str, object] | None = None,
    reserve_price_ratios: tuple[float, ...] | None = VALID_RATIOS,
    reserve_price_overrides: dict[int, int] | None = None,
    selected_numbers: tuple[int, ...] = (),
    observed_bid_rate: str = "0.95",
    provenance: int = common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN,
    with_reserve_draw: bool = True,
) -> features_pb2.CompetitionSample:
    sample = features_pb2.CompetitionSample(
        observed_bid_rate=common_pb2.Rate(fraction=observed_bid_rate),
        origin=common_pb2.BID_RATE_ORIGIN_OBSERVED,
        base_amount=money(base_amount_won, **(base_amount_overrides or {})),
        base_amount_provenance_label=provenance,
        opened_on="2026-01-01",
    )
    if with_reserve_draw:
        prices = [
            round(ratio * base_amount_won) for ratio in (reserve_price_ratios or ())
        ]
        for index, override_won in (reserve_price_overrides or {}).items():
            prices[index] = override_won
        for price_won in prices:
            sample.reserve_draw.reserve_prices.append(money(price_won))
        sample.reserve_draw.selected_numbers.extend(selected_numbers)
    return sample
