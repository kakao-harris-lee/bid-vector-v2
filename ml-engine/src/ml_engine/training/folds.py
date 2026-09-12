"""Reuse: bid-vector/app/services/ml_training/award_rate_gbm.py@ed4b06c

`ml_engine.training.folds` — 무작위 K-fold 인덱스(legacy `_fold_indices`, rng 계약 고정,
D-5C-4). 시간 분할이 아니다 — 시간 누수는 dataset cutoff/5C-2 창 경계가 막고, 이 폴드는
학습 구간 **안**에서 인코딩의 자기참조만 끊는다(모듈 docstring 은 legacy 그대로 계승).
"""

from __future__ import annotations

import numpy as np


def fold_indices(row_count: int, *, folds: int, seed: int) -> tuple[np.ndarray, ...]:
    """`np.random.default_rng(seed).permutation(row_count)`를 `folds`개로 나눈다(legacy와
    같은 rng 계약 — seed·row_count·folds 가 같으면 항상 같은 분할). 빈 조각은 버린다
    (legacy `if part.size`와 동일 — `folds > row_count`면 폴드 일부가 빈다)."""
    permutation = np.random.default_rng(seed).permutation(row_count)
    return tuple(part for part in np.array_split(permutation, folds) if part.size)
