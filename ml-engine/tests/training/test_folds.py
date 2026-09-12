"""RED — `ml_engine.training.folds`(D-5C-4). rng 계약 고정(legacy `_fold_indices`) +
분할 불변식(합=전체, 서로소)."""

from __future__ import annotations

import numpy as np
import pytest

from ml_engine.training.folds import fold_indices


def test_fold_indices_partition_is_disjoint_and_exhaustive() -> None:
    folds = fold_indices(23, folds=5, seed=7)
    all_indices = sorted(index for part in folds for index in part.tolist())
    assert all_indices == list(range(23))


def test_fold_indices_deterministic_for_same_seed() -> None:
    first = fold_indices(50, folds=5, seed=20260812)
    second = fold_indices(50, folds=5, seed=20260812)
    assert len(first) == len(second)
    for a, b in zip(first, second, strict=True):
        assert np.array_equal(a, b)


def test_fold_indices_different_seed_differs() -> None:
    first = fold_indices(50, folds=5, seed=1)
    second = fold_indices(50, folds=5, seed=2)
    flattened_a = [tuple(part.tolist()) for part in first]
    flattened_b = [tuple(part.tolist()) for part in second]
    assert flattened_a != flattened_b


def test_fold_indices_drops_empty_folds_when_folds_exceed_rows() -> None:
    folds = fold_indices(3, folds=5, seed=1)
    assert all(part.size > 0 for part in folds)
    total = sum(part.size for part in folds)
    assert total == 3


@pytest.mark.legacy_parity
def test_legacy_parity_rng_contract_seed_20260812_n13_folds5() -> None:
    """관측이지 판정 근거가 아니다(CLAUDE.md 운영자 지시) — legacy
    `np.random.default_rng(seed).permutation(row_count)` → `array_split` 이 이 정확한
    인덱스를 낸다는 사실만 고정한다(`_fold_indices`, `award_rate_gbm.py:214-217`)."""
    folds = fold_indices(13, folds=5, seed=20260812)
    observed = [part.tolist() for part in folds]
    assert observed == [
        [0, 2, 6],
        [7, 10, 4],
        [8, 9, 5],
        [11, 12],
        [3, 1],
    ]
