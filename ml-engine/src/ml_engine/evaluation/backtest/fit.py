"""`ml_engine.evaluation.backtest.fit` — P-4 제도 분포 적합도. **실험 앞에 돌고, 맞지
않으면 판정 대신 멈춤 결과를 낸다**(scope.md 선행 조사 P-4 — 「사정률은 난수」라는 전제
자체가 흔들리면 실험 설계가 바뀐다).

실측 사정률(예정가격 ÷ 기초금액)이 제도 분포(15구간 균등 + 무작위 4개 평균)와 맞는가를
**두 축**으로 본다: ① 두 표본 KS 검정 ② 구간 비율의 최대 편차.

공고마다 예가 범위가 다르므로(±2% · ±3%, 비대칭도 가능) 사정률을 범위 안 위치
`u = (R - (1+begin)) / (end - begin)` 로 정규화해 한 번에 검정한다 — 평균-of-4-of-15 의
**모양**은 범위 폭과 무관하게 같다.

기준 분포는 큰 몬테카를로 표본이다(정확한 닫힌식 CDF 가 없다) — seed 를 정책이 고정하므로
재현된다. 그래서 단일표본이 아니라 **두 표본** KS 를 쓴다(기준도 표본이라는 사실을
검정에 반영한다).
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from enum import StrEnum
from typing import Final

import numpy as np

from ml_engine.evaluation.backtest.exclusions import AdmittedNotice
from ml_engine.evaluation.backtest.institution import (
    bin_proportions,
    sample_assessment_ratios,
)
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy

# 기준 표본 크기 — 검정의 분해능을 정하는 구조 상수다(임계가 아니다). 관측 표본보다
# 충분히 커서 기준 쪽 표본오차가 판정을 좌우하지 않게 한다.
_REFERENCE_SAMPLE_COUNT: Final[int] = 200_000
# Kolmogorov 분포 급수의 절단 항 수 — 배정도 한계 아래로 수렴하고도 남는다(k=8 에서
# 이미 exp(-128 lambda^2) 이다). 임계가 아니라 수치 절단이다.
_KOLMOGOROV_TERMS: Final[int] = 64
# 정규화 축 `[0, 1]` 을 제도 표본기의 인자로 옮긴 값 — 표본기는 `1 + begin_rate` 에서
# 시작하므로 `[0, 1]` 은 `begin=-1, end=0` 이다(같은 함수를 두 번 만들지 않는다).
_REFERENCE_BEGIN_RATE: Final[float] = -1.0
_REFERENCE_END_RATE: Final[float] = 0.0


class FitRejectionReason(StrEnum):
    INSUFFICIENT_SAMPLES = "INSUFFICIENT_SAMPLES"
    KS_REJECTED = "KS_REJECTED"
    BIN_RATIO_REJECTED = "BIN_RATIO_REJECTED"
    DEGENERATE_RANGE = "DEGENERATE_RANGE"


@dataclass(frozen=True)
class FitResult:
    """적합도 판정 한 벌 — 통과 여부와 함께 실측값을 전부 공시한다."""

    sample_count: int
    ks_statistic: float
    p_value: float
    max_bin_deviation: float
    rejection: FitRejectionReason | None

    @property
    def accepted(self) -> bool:
        return self.rejection is None


def kolmogorov_tail(value: float) -> float:
    """`Q(lambda) = 2 * sum (-1)^(k-1) exp(-2 k^2 lambda^2)` — 두 표본 KS 의 점근
    p 값. Stephens 보정항(유한 표본 보정)을 넣지 않는다 — 기준 표본이 20만이라 유효
    표본 수가 관측 쪽에 지배되고, 보정 상수를 들이면 그 상수가 다시 임계가 된다."""
    if value <= 0.0:
        return 1.0
    terms = np.arange(1, _KOLMOGOROV_TERMS + 1, dtype=np.float64)
    signs = np.where(terms % 2 == 1, 1.0, -1.0)
    total = float((signs * np.exp(-2.0 * terms**2 * value**2)).sum())
    return float(min(1.0, max(0.0, 2.0 * total)))


def two_sample_ks(observed: np.ndarray, reference: np.ndarray) -> tuple[float, float]:
    """두 표본 KS 통계량과 점근 p 값. 경험 CDF 를 정렬 + `searchsorted` 로 만든다."""
    grid = np.sort(np.concatenate((observed, reference)))
    observed_cdf = np.searchsorted(np.sort(observed), grid, side="right") / float(
        observed.size
    )
    reference_cdf = np.searchsorted(np.sort(reference), grid, side="right") / float(
        reference.size
    )
    statistic = float(np.abs(observed_cdf - reference_cdf).max())
    effective = np.sqrt(
        observed.size * reference.size / float(observed.size + reference.size)
    )
    return statistic, kolmogorov_tail(float(effective) * statistic)


def normalized_assessment_ratios(
    admitted: Sequence[AdmittedNotice],
) -> np.ndarray | FitRejectionReason:
    """사정률을 예가 범위 안 위치 `[0, 1]` 로 정규화한다. 범위 폭이 0 이면 정규화가
    성립하지 않는다 — 조용히 건너뛰지 않고 거부한다."""
    values: list[float] = []
    for item in admitted:
        width = item.reserve_range_end_rate - item.reserve_range_begin_rate
        if width <= 0.0:
            return FitRejectionReason.DEGENERATE_RANGE
        values.append(
            (item.assessment_ratio - (1.0 + item.reserve_range_begin_rate)) / width
        )
    return np.asarray(values, dtype=np.float64)


def _reference_sample(policy: StrategyBacktestPolicy, seed: int) -> np.ndarray:
    return sample_assessment_ratios(
        np.random.default_rng(seed),
        count=_REFERENCE_SAMPLE_COUNT,
        begin_rate=_REFERENCE_BEGIN_RATE,
        end_rate=_REFERENCE_END_RATE,
        reserve_price_count=policy.institution.reserve_price_count,
        draw_count=policy.institution.draw_count,
    )


def check_institutional_fit(
    admitted: Sequence[AdmittedNotice], policy: StrategyBacktestPolicy, *, seed: int
) -> FitResult:
    """제도 분포 적합도 — 표본이 모자라거나 KS·구간 비율 어느 쪽이든 어긋나면 거부."""
    observed = normalized_assessment_ratios(admitted)
    if isinstance(observed, FitRejectionReason):
        return FitResult(len(admitted), 0.0, 0.0, 0.0, observed)
    if observed.size < policy.fit.min_sample_count:
        return FitResult(
            int(observed.size),
            0.0,
            0.0,
            0.0,
            FitRejectionReason.INSUFFICIENT_SAMPLES,
        )
    reference = _reference_sample(policy, seed)
    statistic, p_value = two_sample_ks(observed, reference)
    bins = policy.institution.reserve_price_count
    deviation = float(
        np.abs(
            bin_proportions(
                observed,
                begin_rate=_REFERENCE_BEGIN_RATE,
                end_rate=_REFERENCE_END_RATE,
                bin_count=bins,
            )
            - bin_proportions(
                reference,
                begin_rate=_REFERENCE_BEGIN_RATE,
                end_rate=_REFERENCE_END_RATE,
                bin_count=bins,
            )
        ).max()
    )
    rejection = _fit_rejection(p_value, deviation, policy)
    return FitResult(int(observed.size), statistic, p_value, deviation, rejection)


def _fit_rejection(
    p_value: float, deviation: float, policy: StrategyBacktestPolicy
) -> FitRejectionReason | None:
    if p_value < policy.fit.alpha:
        return FitRejectionReason.KS_REJECTED
    if deviation > policy.fit.max_bin_ratio_deviation:
        return FitRejectionReason.BIN_RATIO_REJECTED
    return None
