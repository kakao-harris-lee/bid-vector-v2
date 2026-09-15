"""Reuse: bid-vector/app/services/ml_training/award_rate_scoring.py@ed4b06c

`ml_engine.evaluation.scoring` — 순수 채점 커널(scope ①). `rmse_bias_std`·`paired_t`·
`improvement_ratio`는 legacy `:128-202`을 값 무변경으로 이식했다(numpy 직접 구현 유지 —
scipy 도입 없음, ddof 차이로 수치가 달라진다, 조사 01 §11-7).

RMSE 하나만 내지 않는다. 편향과 잔차 표준편차를 **같은 잔차에서** 나눠 내야, 좋아 보이는
수치가 "치우침이 줄어서"인지 "모양이 좋아져서"인지 구별된다 — 층 구성비나 시간 드리프트가
바뀌기만 해도 전자는 움직인다.
"""

from __future__ import annotations

import numpy as np


def rmse_bias_std(
    predictions: np.ndarray, targets: np.ndarray
) -> tuple[float, float, float]:
    """(RMSE, 편향, 잔차 표준편차) 한 벌 — 세 지표가 같은 잔차에서 나오게 한다."""
    residuals = predictions - targets
    return (
        float(np.sqrt(np.mean(residuals**2))),
        float(np.mean(residuals)),
        float(np.std(residuals, ddof=1)) if residuals.size > 1 else 0.0,
    )


def paired_t(a: np.ndarray, b: np.ndarray, targets: np.ndarray) -> float:
    """제곱오차 차이(a − b)의 대응 t. 음수면 a 가 낫다. 차이 표준편차가 0(또는 n<=1)이면
    0."""
    differences = ((a - targets) ** 2) - ((b - targets) ** 2)
    deviation = float(np.std(differences, ddof=1)) if differences.size > 1 else 0.0
    if deviation <= 0.0:
        return 0.0
    return float(np.mean(differences) / (deviation / np.sqrt(differences.size)))


def improvement_ratio(baseline_rmse: float, model_rmse: float) -> float:
    """(베이스라인 − 모델) ÷ 베이스라인 — 개선률의 단일 정의. 양수면 모델이 낫다.
    베이스라인이 0 이하면 0(legacy `:198-202`)."""
    if baseline_rmse <= 0:
        return 0.0
    return (baseline_rmse - model_rmse) / baseline_rmse
