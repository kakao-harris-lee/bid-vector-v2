"""`ml_engine.evaluation.backtest.mcnemar` — 쌍대 이진 지표의 정확 검정(D-6G-6).

주 지표(would-have-won)는 **이진**이고 공고 단위로 쌍을 이룬다. 5C-2 의
`scoring.paired_t` 는 연속 지표(제곱오차 차이)의 대응 t 라 여기에 맞지 않는다 —
이진 쌍에는 McNemar 검정이 맞고, 표본이 작을 때 카이제곱 근사가 아니라 **이항 분포를
그대로 쓰는 정확 검정**을 쓴다(계약 「재사용 조사」 표의 `scoring.paired_t` 부분 채택).

`scipy` 를 들이지 않는다(5C-2 관례 — `rmse_bias_std`·`paired_t` 도 numpy 직접 구현이다).
이항 꼬리는 로그 팩토리얼 누적합(`np.cumsum(np.log(...))`)으로 계산한다 — 큰 `n` 에서도
언더플로하지 않고, 근사가 아니라 이항 확률 그 자체를 더한다.

**필요 표본 수도 같은 이항 꼬리로 낸다** — 정규 분위수 근사(그 유리함수 상수들)를
들이지 않는다. 임계값을 정확 검정으로 찾고, 대립가설 아래 검정력을 같은 꼬리로 재서
목표 검정력에 닿는 최소 불일치 쌍 수를 **탐색**한다.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from math import log
from typing import Final

import numpy as np

# 귀무가설 아래 불일치 쌍이 어느 쪽으로 갈 확률 — McNemar 의 정의 그 자체다(임계가
# 아니다). 이 값을 바꾸는 것은 검정을 다른 검정으로 바꾸는 일이라 정책 값이 아니다.
_NULL_SUCCESS_PROBABILITY: Final[float] = 0.5
# 필요 표본 탐색의 상한 — 여기까지 올려도 목표 검정력에 닿지 못하면 `None`(「모른다」를
# 큰 수로 위장하지 않는다).
_MAX_SEARCH_PAIRS: Final[int] = 200_000
# 이분 탐색이 계단 하나를 건너뛰는 경우를 대비한 역주행 폭(구조 상수).
_BACK_SCAN_PAIRS: Final[int] = 64


@dataclass(frozen=True)
class DiscordantCounts:
    """불일치 쌍 — `strategy_only` 는 전략만 이긴 공고 수, `baseline_only` 는 S0 만
    이긴 공고 수. 둘 다 이기거나 둘 다 진 공고는 검정에 들어가지 않는다."""

    strategy_only: int
    baseline_only: int

    @property
    def total(self) -> int:
        return self.strategy_only + self.baseline_only


def discordant_counts(
    strategy_wins: Sequence[bool], baseline_wins: Sequence[bool]
) -> DiscordantCounts:
    """쌍대 이진 결과에서 불일치 쌍을 센다. 두 수열은 **같은 공고 순서**여야 한다 —
    길이가 다르면 쌍이 성립하지 않으므로 거부한다(조용한 절단 없음)."""
    if len(strategy_wins) != len(baseline_wins):
        raise ValueError(
            f"쌍대 검정은 같은 길이를 요구합니다: "
            f"{len(strategy_wins)} != {len(baseline_wins)}"
        )
    strategy = np.asarray(strategy_wins, dtype=bool)
    baseline = np.asarray(baseline_wins, dtype=bool)
    return DiscordantCounts(
        strategy_only=int(np.count_nonzero(strategy & ~baseline)),
        baseline_only=int(np.count_nonzero(~strategy & baseline)),
    )


def _log_factorials(n: int) -> np.ndarray:
    return np.concatenate(
        (np.zeros(1), np.cumsum(np.log(np.arange(1, n + 1, dtype=np.float64))))
    )


def binomial_upper_tail(n: int, k: int, probability: float) -> float:
    """`P(X >= k)`, `X ~ Bin(n, probability)`. 로그 공간에서 더해 언더플로를 피한다.

    `probability` 가 경계(0 또는 1)면 로그가 정의되지 않는다 — 그 두 경우의 답은
    분포가 한 점에 몰린다는 사실에서 바로 나오므로 먼저 답한다(사전 등록 Δ 가 커지면
    대립가설 성공 확률이 1 로 포화할 수 있다, 재현 test 에서 실측)."""
    if k <= 0:
        return 1.0
    if k > n:
        return 0.0
    if probability >= 1.0:
        return 1.0
    if probability <= 0.0:
        return 0.0
    log_factorial = _log_factorials(n)
    counts = np.arange(k, n + 1)
    log_terms = (
        log_factorial[n]
        - log_factorial[counts]
        - log_factorial[n - counts]
        + counts * log(probability)
        + (n - counts) * log(1.0 - probability)
    )
    peak = float(log_terms.max())
    return float(np.exp(peak + np.log(np.exp(log_terms - peak).sum())))


def one_sided_p_value(counts: DiscordantCounts) -> float:
    """단측 정확 p — 「전략이 S0 보다 더 자주 이겼다」는 대립가설. 불일치 쌍이 하나도
    없으면 판정할 것이 없어 `1.0`(귀무 기각 불가)."""
    if counts.total == 0:
        return 1.0
    return binomial_upper_tail(
        counts.total, counts.strategy_only, _NULL_SUCCESS_PROBABILITY
    )


def critical_count(pairs: int, alpha: float) -> int | None:
    """유의수준 `alpha` 에서 귀무를 기각하는 최소 `strategy_only` — 어떤 값으로도
    기각할 수 없으면 `None`(표본이 그 유의수준을 낼 수 없다는 사실 자체).

    꼬리 확률은 `k` 에 대해 단조 감소하므로 **이분 탐색**으로 찾는다. 선형 탐색은 큰
    표본에서 꼬리 계산을 `pairs` 번 반복해 필요 표본 탐색 전체를 제곱 비용으로 만든다
    (재현 test 가 10분을 넘겨 드러난 자리다)."""
    if binomial_upper_tail(pairs, pairs, _NULL_SUCCESS_PROBABILITY) > alpha:
        return None
    low, high = 0, pairs
    while low < high:
        middle = (low + high) // 2
        if binomial_upper_tail(pairs, middle, _NULL_SUCCESS_PROBABILITY) <= alpha:
            high = middle
        else:
            low = middle + 1
    return low


def power_at(pairs: int, success_probability: float, alpha: float) -> float:
    """대립가설(`success_probability`) 아래 검정력 — 같은 이항 꼬리로 잰다."""
    threshold = critical_count(pairs, alpha)
    if threshold is None:
        return 0.0
    return binomial_upper_tail(pairs, threshold, success_probability)


def required_discordant_pairs(
    *, success_probability: float, alpha: float, target_power: float
) -> int | None:
    """목표 검정력에 닿는 **최소 불일치 쌍 수**. 대립가설의 성공 확률이 귀무(0.5)보다
    크지 않으면 어떤 표본으로도 닿지 못한다(`None`). 탐색 상한까지 못 닿아도 `None`
    — 「모른다」를 큰 수로 위장하지 않는다.

    검정력은 임계값이 정수로 뛰는 탓에 표본 수에 대해 **엄밀히 단조가 아니다**(작은
    구간에서 뒤집힌다). 그래서 배가 탐색으로 상계를 잡고 이분 탐색으로 좁힌 뒤, 계단
    하나를 놓치지 않도록 `_BACK_SCAN_PAIRS` 만큼 **뒤로 훑어** 더 작은 해가 있으면 그것을
    낸다. 순수 선형 탐색은 상한 20만에서 꼬리 계산을 제곱으로 반복해 쓸 수 없다(실측)."""
    if success_probability <= _NULL_SUCCESS_PROBABILITY:
        return None
    bound = 1
    while bound <= _MAX_SEARCH_PAIRS:
        if power_at(bound, success_probability, alpha) >= target_power:
            return _refine_required_pairs(
                bound, success_probability, alpha, target_power
            )
        bound *= 2
    return None


def _refine_required_pairs(
    bound: int, success_probability: float, alpha: float, target_power: float
) -> int:
    """`bound` 에서 목표 검정력에 닿았을 때 더 작은 해를 찾는다 — 이분 탐색 뒤 짧은
    역주행으로 계단을 확인한다."""
    low, high = max(bound // 2, 1), bound
    while low < high:
        middle = (low + high) // 2
        if power_at(max(middle, 1), success_probability, alpha) >= target_power:
            high = middle
        else:
            low = middle + 1
    best = max(low, 1)
    for candidate in range(max(best - _BACK_SCAN_PAIRS, 1), best):
        if power_at(candidate, success_probability, alpha) >= target_power:
            return candidate
    return best


def alternative_success_probability(
    *,
    window_rows: int,
    baseline_win_rate: float,
    relative_improvement: float,
    pairs: int,
) -> float:
    """사전 등록한 효과 크기 Δ 를 **불일치 쌍의 성공 확률**로 옮긴다.

    S0 의 승률이 `w0` 일 때 Δ 만큼의 상대 개선은 창 전체에서 `n * w0 * Δ` 만큼의 승수
    차이다. 그 차이는 전부 불일치 쌍에서 나오므로(`strategy_only - baseline_only`),
    불일치 쌍 `m` 개 중 전략이 이기는 비율은 `0.5 + n*w0*Δ / (2m)` 이 된다. 관측된
    불일치 쌍 수를 그대로 쓰는 이유는, Δ 를 검출하는 데 필요한 표본이 **그 창의 불일치
    구조**에 달려 있기 때문이다(불일치가 드문 창은 같은 Δ 라도 더 많은 공고가 필요하다).
    """
    if pairs <= 0:
        return _NULL_SUCCESS_PROBABILITY
    shift = window_rows * baseline_win_rate * relative_improvement / (2.0 * pairs)
    return min(1.0, _NULL_SUCCESS_PROBABILITY + shift)
