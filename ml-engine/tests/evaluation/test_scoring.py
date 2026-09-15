"""RED — `ml_engine.evaluation.scoring`(scope ①, `Reuse: award_rate_scoring.py@ed4b06c`).
순수 채점 커널 — 항등식과 legacy 산식 관측(`legacy_parity`, 판정 아님)."""

from __future__ import annotations

import numpy as np
import pytest

from ml_engine.evaluation.scoring import improvement_ratio, paired_t, rmse_bias_std


def test_rmse_bias_std_identity_rmse_squared_equals_bias_squared_plus_variance() -> (
    None
):
    """`rmse² = bias² + std² * (n-1)/n` — 조사 02 §1-1 항등식."""
    rng = np.random.default_rng(20260812)
    predictions = rng.normal(size=37)
    targets = rng.normal(size=37)
    rmse, bias, std = rmse_bias_std(predictions, targets)
    n = predictions.size
    assert rmse**2 == pytest.approx(bias**2 + std**2 * (n - 1) / n, rel=1e-9)


def test_rmse_bias_std_single_row_has_zero_std() -> None:
    rmse, bias, std = rmse_bias_std(np.array([1.5]), np.array([1.0]))
    assert rmse == pytest.approx(0.5)
    assert bias == pytest.approx(0.5)
    assert std == 0.0


def test_paired_t_negative_means_a_is_better() -> None:
    targets = np.array([1.0, 2.0, 3.0, 4.0])
    a = targets  # a 는 완벽한 예측
    b = targets + np.array([0.5, 1.0, 0.7, 1.3])  # b 는 어긋나되 오차 크기가 다르다
    statistic = paired_t(a, b, targets)
    assert statistic < 0.0


def test_paired_t_zero_deviation_returns_zero() -> None:
    """차이의 표준편차가 0 이면(legacy `deviation <= 0.0`) 0 을 낸다."""
    targets = np.array([1.0, 2.0, 3.0])
    a = targets
    b = targets
    assert paired_t(a, b, targets) == 0.0


def test_paired_t_single_row_returns_zero() -> None:
    targets = np.array([1.0])
    assert paired_t(np.array([2.0]), np.array([3.0]), targets) == 0.0


def test_improvement_ratio_positive_means_model_is_better() -> None:
    assert improvement_ratio(baseline_rmse=1.0, model_rmse=0.5) == pytest.approx(0.5)


def test_improvement_ratio_non_positive_baseline_returns_zero() -> None:
    assert improvement_ratio(baseline_rmse=0.0, model_rmse=0.5) == 0.0
    assert improvement_ratio(baseline_rmse=-1.0, model_rmse=0.5) == 0.0


@pytest.mark.legacy_parity
def test_legacy_parity_rmse_bias_std_fixed_vectors() -> None:
    """legacy `award_rate_scoring.py:128-137` 산식 관측 — 정답 판정이 아니라 회귀 관측."""
    predictions = np.array([0.9, 0.8, 0.95, 0.7])
    targets = np.array([1.0, 0.75, 0.9, 0.8])
    rmse, bias, std = rmse_bias_std(predictions, targets)
    residuals = predictions - targets
    assert rmse == pytest.approx(float(np.sqrt(np.mean(residuals**2))))
    assert bias == pytest.approx(float(np.mean(residuals)))
    assert std == pytest.approx(float(np.std(residuals, ddof=1)))


@pytest.mark.legacy_parity
def test_legacy_parity_paired_t_fixed_vectors() -> None:
    a = np.array([0.9, 0.8, 0.95, 0.7])
    b = np.array([0.85, 0.7, 0.9, 0.6])
    targets = np.array([1.0, 0.75, 0.9, 0.8])
    statistic = paired_t(a, b, targets)
    differences = ((a - targets) ** 2) - ((b - targets) ** 2)
    deviation = float(np.std(differences, ddof=1))
    expected = float(np.mean(differences) / (deviation / np.sqrt(differences.size)))
    assert statistic == pytest.approx(expected)
