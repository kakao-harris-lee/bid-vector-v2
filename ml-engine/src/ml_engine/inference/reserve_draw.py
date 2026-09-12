"""Reuse: bid-vector/app/domain/reserve_draw_distribution.py@ed4b06c

`ml_engine.inference.reserve_draw` — K6 이식(복수예비가격 추첨 4/15 분포, scope.md ①).
닫힌식·완전열거 산술은 legacy 그대로다. 경계 거동만 바뀐다: legacy 는 `draw_count < 1`·
비유한/≤0 값·`len(values) < draw_count`를 전부 `ValueError`로 던졌다 — 이 모듈은 그 셋을
`Unmeasurable`(결과 타입)로 치환한다(위협 모델 (a), scope.md ①).

**golden 통합 M-3 반영(`ml-kernel-008`)** — legacy 는 표본이 전부 같은 값(모분산 0)이어도
그대로 통과시켜 `(mean, 0.0)`을 낸다(추첨 수만큼 완전열거해도 지지집합이 한 점이라 분산이
없다는 뜻과, 「모집단 자체가 퇴화(singular)해 이 표본으로는 분산을 잴 수 없다」는 뜻이
legacy 산식에서는 구별되지 않았다). milestone-5.md 5D 항목이 이름 든 "singular input" 처리
및 승인된 golden case(`ml-kernel-008` degenerate-variance probe)는 후자를 명시적
`Unmeasurable(INSUFFICIENT_SAMPLES, DEGENERATE_VARIANCE)`로 요구한다 — **단, `n == draw_count`
(표본 전부를 뽑는 경우)는 제외**한다. 그 경우의 분산 0은 "이 표본으로 잴 수 없다"가 아니라
"뽑을 수 있는 조합이 하나뿐이라 수학적으로 항상 0"이라는 별개의 구조적 사실이고, legacy도
그 조기 반환을 정상값으로 다뤘다(`:129` 그대로 계승).
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from itertools import combinations
from math import isfinite, sqrt
from statistics import fmean, pvariance

from ml_engine.inference.results import (
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)


@dataclass(frozen=True)
class DrawMeanDistribution:
    """추첨 평균(예정가)의 이산 분포 — 지지집합의 각 원소는 등확률이다(legacy 그대로)."""

    support: tuple[float, ...]
    draw_count: int
    source_count: int

    @property
    def mean(self) -> float:
        return fmean(self.support)

    @property
    def std(self) -> float:
        return sqrt(pvariance(self.support))

    def quantile(self, q: float) -> float:
        safe_q = min(1.0, max(0.0, float(q)))
        position = (len(self.support) - 1) * safe_q
        lower_index = int(position)
        upper_index = min(lower_index + 1, len(self.support) - 1)
        fraction = position - lower_index
        return (self.support[lower_index] * (1.0 - fraction)) + (
            self.support[upper_index] * fraction
        )

    def central_interval(self, coverage: float) -> tuple[float, float]:
        safe_coverage = min(1.0, max(0.0, float(coverage)))
        tail = (1.0 - safe_coverage) / 2.0
        return self.quantile(tail), self.quantile(1.0 - tail)

    def cumulative_probability(self, value: float) -> float:
        below = sum(1 for entry in self.support if entry < value)
        equal = sum(1 for entry in self.support if entry == value)
        return (below + (equal / 2.0)) / len(self.support)


def _validated_values(
    values: Sequence[float], draw_count: int
) -> tuple[float, ...] | Unmeasurable:
    """legacy `_validated_values`의 셋을 결과 타입으로 — `draw_count < 1`은 표본 부족과
    같은 사유(`TOO_FEW_DRAWS`)로 묶는다(호출부 설정 오류이지 값 문제가 아니다)."""
    if draw_count < 1:
        return Unmeasurable(
            UnmeasurableReason.INSUFFICIENT_SAMPLES, UnmeasurableDetail.TOO_FEW_DRAWS
        )
    validated: list[float] = []
    for raw_value in values:
        value = float(raw_value)
        if not isfinite(value) or value <= 0.0:
            return Unmeasurable(
                UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.NON_FINITE_INPUT
            )
        validated.append(value)
    if len(validated) < draw_count:
        return Unmeasurable(
            UnmeasurableReason.INSUFFICIENT_SAMPLES, UnmeasurableDetail.TOO_FEW_DRAWS
        )
    return tuple(validated)


def _admitted_values(
    values: Sequence[float], draw_count: int
) -> tuple[float, ...] | Unmeasurable:
    """`_validated_values` 뒤 singular(모분산 0) 입력을 추가로 거른다(golden M-3,
    `n == draw_count`는 제외 — 모듈 docstring 근거)."""
    validated = _validated_values(values, draw_count)
    if isinstance(validated, Unmeasurable):
        return validated
    if len(validated) != draw_count and pvariance(validated) == 0.0:
        return Unmeasurable(
            UnmeasurableReason.INSUFFICIENT_SAMPLES,
            UnmeasurableDetail.DEGENERATE_VARIANCE,
        )
    return validated


def exact_draw_mean_distribution(
    values: Sequence[float], draw_count: int
) -> DrawMeanDistribution | Unmeasurable:
    """모든 C(n, k) 추첨 조합을 열거한 정확한 추첨 평균 분포(legacy 그대로, 샘플링 오차 0)."""
    validated = _admitted_values(values, draw_count)
    if isinstance(validated, Unmeasurable):
        return validated
    support = sorted(
        fmean(combination) for combination in combinations(validated, draw_count)
    )
    return DrawMeanDistribution(
        support=tuple(support), draw_count=draw_count, source_count=len(validated)
    )


def draw_mean_moments(
    values: Sequence[float], draw_count: int
) -> tuple[float, float] | Unmeasurable:
    """추첨 평균의 (평균, 표준편차)를 열거 없이 닫힌식으로 계산한다(legacy `Var(x̄_k) =
    (sigma^2/k)*(n-k)/(n-1)` 그대로)."""
    validated = _admitted_values(values, draw_count)
    if isinstance(validated, Unmeasurable):
        return validated
    population_size = len(validated)
    mean_value = fmean(validated)
    if population_size == draw_count:
        return mean_value, 0.0
    variance = (pvariance(validated) / draw_count) * (
        (population_size - draw_count) / (population_size - 1)
    )
    return mean_value, sqrt(variance)
