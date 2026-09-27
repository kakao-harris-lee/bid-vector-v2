"""`ml_engine.evaluation.backtest.institution` — 복수예비가격 제도가 만드는 사정률
분포. 예가 범위를 균등 폭 `reserve_price_count` 구간으로 나눠 구간마다 균등 난수 하나를
뽑고(예비가격), 그중 무작위 `draw_count` 개의 평균이 예정가격이다. 사정률 R 은
예정가격 ÷ 기초금액.

`docs/discovery/ml-value-sim/reserve_rate_distribution.py`(2026-09-27 조사)의 몬테카를로를
**numpy 로 벡터화**해 옮겼다 — 산식은 그대로고 난수원만 `random.Random` 에서
`numpy.random.Generator` 로 바뀐다(같은 seed 에서 같은 표본, 재현성은 seed 로 닫는다).
추첨 평균의 닫힌식 모멘트는 5D K6(`inference.reserve_draw.draw_mean_moments`)에 이미
있지만 evaluation 층은 inference 를 import 할 수 없고(import-linter forbidden), 여기서
필요한 것은 모멘트가 아니라 **표본**이라 그 함수로는 답하지 못한다.

**비대칭 범위를 지원한다** — 예가 범위는 공고별 필드(`rsrvtnPrceRngBgnRate`/`EndRate`)이고
시작률이 종료율의 반대수라는 보장이 없다(선행 조사 02 §4.4).
"""

from __future__ import annotations

import numpy as np


def sample_assessment_ratios(
    rng: np.random.Generator,
    *,
    count: int,
    begin_rate: float,
    end_rate: float,
    reserve_price_count: int,
    draw_count: int,
) -> np.ndarray:
    """사정률 표본 `count` 개. 한 시행은 (구간별 균등 난수 15개 → 무작위 4개 평균)이다.

    추첨은 **번호**를 뽑는 것이고 번호↔가격 배열이 무작위라, 가격과 무관하게 균등한
    조합을 뽑는 것과 같다(조사 노트 docstring) — 그래서 `argsort` 로 행마다 독립적인
    무작위 순열을 만들어 앞의 `draw_count` 개를 쓴다."""
    low = 1.0 + begin_rate
    step = (end_rate - begin_rate) / reserve_price_count
    edges = low + step * np.arange(reserve_price_count, dtype=np.float64)
    prices = edges + rng.random((count, reserve_price_count)) * step
    order = np.argsort(rng.random((count, reserve_price_count)), axis=1)
    picked = np.take_along_axis(prices, order[:, :draw_count], axis=1)
    return np.asarray(picked.mean(axis=1), dtype=np.float64)


def bin_proportions(
    values: np.ndarray, *, begin_rate: float, end_rate: float, bin_count: int
) -> np.ndarray:
    """예가 범위를 `bin_count` 구간으로 나눈 비율 — 범위 밖 값은 양 끝 구간으로
    모은다(제도상 R 은 범위 안에 있어야 하고, 밖의 값은 그 자체가 부적합 신호다)."""
    edges = np.linspace(1.0 + begin_rate, 1.0 + end_rate, bin_count + 1)
    indices = np.clip(np.digitize(values, edges[1:-1]), 0, bin_count - 1)
    counts = np.bincount(indices, minlength=bin_count).astype(np.float64)
    return counts / float(values.size)
