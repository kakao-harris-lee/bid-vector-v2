"""Reuse: bid-vector/app/services/ml_training/award_rate_gbm.py@ed4b06c

`ml_engine.training.residual` — out-of-fold 잔차의 표준편차(legacy `_residual_std`,
시나리오 후보 폭의 입력). 하한을 두어 표본이 얕아 잔차가 0에 가까워도 "다음 공고의
낙찰률이 정확히 이 값"이라는 과신을 방지한다(legacy 사유 계승).
"""

from __future__ import annotations

import numpy as np


def residual_std(residuals: np.ndarray, *, floor: float) -> float:
    """`residuals`의 표본 표준편차(`ddof=1`), 최소 `floor`. 표본 1개 이하면 `floor`."""
    if residuals.size <= 1:
        return floor
    return max(float(np.std(residuals, ddof=1)), floor)
