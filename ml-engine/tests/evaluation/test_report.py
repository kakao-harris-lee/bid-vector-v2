"""RED — `ml_engine.evaluation.report`(scope ⑧). `EvaluationReportV1` + 승격 측정
`Promotion`. canonical JSON(5B 규칙과 같은 형태, 새 함수) + sha256 → 재현성·NaN 거부."""

from __future__ import annotations

import math

import pytest

from ml_engine.evaluation.diagnostics import CategoryCount, StabilitySummary
from ml_engine.evaluation.report import (
    EvaluationReportV1,
    ModelScore,
    NotPromotable,
    Promotable,
    PromotionNotEvaluable,
    StratumCount,
    WindowResult,
    canonical_report_bytes,
    derive_promotion,
    report_checksum,
)
from ml_engine.evaluation.verdict import Failed, NotEvaluable, NotEvaluableReason, Passed
from ml_engine.evaluation.windows import HoldoutOverlap, WindowExclusion, WindowExclusionReason
from ml_engine.features import CanonicalizationRejected

_STABILITY = StabilitySummary(
    trials=(),
    passed_count=1,
    sign_consistent=True,
    verdict_consistent=True,
    min_improvement_ratio=0.1,
    max_improvement_ratio=0.1,
    min_abs_paired_t=3.0,
    max_abs_paired_t=3.0,
)

_PASSED = Passed(
    baseline_rmse=0.1,
    model_rmse=0.05,
    improvement_ratio=0.5,
    paired_t=-3.0,
    min_detectable_improvement=0.1,
    required_row_count=None,
)


def _window_result(*, outcome: object = _PASSED) -> WindowResult:
    return WindowResult(
        window_start="2026-06-22T00:00:00+00:00",
        window_end="2026-06-29T00:00:00+00:00",
        window_maturity=0.8,
        window_opened_count=100,
        window_settled_count=80,
        cutoff_at="2026-06-22T00:00:00+00:00",
        train_row_count=500,
        gate_train_row_count=300,
        gate_test_row_count=145,
        train_mean=0.7,
        test_mean=0.72,
        test_std=0.05,
        train_categories=(CategoryCount(category="civil", row_count=300),),
        test_categories=(CategoryCount(category="civil", row_count=100),),
        baselines=(
            ModelScore(name="category_x_band", rmse=0.1, bias=0.01, residual_std=0.09, coverage=0.9),
        ),
        models=(ModelScore(name="gbm_all_strata", rmse=0.05, bias=0.0, residual_std=0.05),),
        segments=(),
        coverage=(),
        unlearned_baseline_cells=(),
        outcome=outcome,
        stability=_STABILITY,
        release_id="release-abc",
    )


def _minimal_report(**overrides: object) -> EvaluationReportV1:
    fields: dict[str, object] = dict(
        report_schema_version="evaluation-report-v1",
        policy_version="evaluation-v1",
        policy_checksum="a" * 64,
        training_spec_version="award-rate-gbm-training-v1",
        training_spec_checksum="b" * 64,
        feature_schema_version="award-rate-v2",
        dataset_id="dataset-1",
        corpus_row_count=1000,
        corpus_opened_at_first="2026-01-01T00:00:00+00:00",
        corpus_opened_at_last="2026-07-01T00:00:00+00:00",
        feed_origin_only=True,
        corpus_strata=(StratumCount(stratum="clean-base", row_count=800),),
        corpus_categories=(CategoryCount(category="civil", row_count=500),),
        windows=(_window_result(),),
        excluded_windows=(),
        holdout_overlaps=(),
        unaccounted_row_count=0,
        stability_seed_count=1,
        promotion=Promotable(window_start="2026-06-22T00:00:00+00:00"),
    )
    fields.update(overrides)
    return EvaluationReportV1(**fields)  # type: ignore[arg-type]


def test_derive_promotion_none_latest_is_not_evaluable() -> None:
    result = derive_promotion(None, window_start=None)
    assert isinstance(result, PromotionNotEvaluable)
    assert result.reason == NotEvaluableReason.NO_EVALUABLE_WINDOW


def test_derive_promotion_not_evaluable_passes_through_reason() -> None:
    result = derive_promotion(
        NotEvaluable(NotEvaluableReason.SEED_UNSTABLE), window_start=None
    )
    assert isinstance(result, PromotionNotEvaluable)
    assert result.reason == NotEvaluableReason.SEED_UNSTABLE


def test_derive_promotion_passed_becomes_promotable() -> None:
    result = derive_promotion(_PASSED, window_start="2026-06-22T00:00:00+00:00")
    assert isinstance(result, Promotable)
    assert result.window_start == "2026-06-22T00:00:00+00:00"


def test_derive_promotion_failed_becomes_not_promotable() -> None:
    failed = Failed(
        baseline_rmse=0.1,
        model_rmse=0.12,
        improvement_ratio=-0.2,
        paired_t=1.0,
        min_detectable_improvement=0.05,
        required_row_count=200,
    )
    result = derive_promotion(failed, window_start="2026-06-22T00:00:00+00:00")
    assert isinstance(result, NotPromotable)
    assert result.reasons


def test_derive_promotion_passed_without_window_start_raises() -> None:
    with pytest.raises(ValueError):
        derive_promotion(_PASSED, window_start=None)


def test_canonical_report_bytes_is_deterministic() -> None:
    report = _minimal_report()
    first = canonical_report_bytes(report)
    second = canonical_report_bytes(report)
    assert isinstance(first, bytes)
    assert first == second


def test_report_checksum_is_sha256_hex() -> None:
    report = _minimal_report()
    checksum = report_checksum(report)
    assert isinstance(checksum, str)
    assert len(checksum) == 64
    int(checksum, 16)  # 유효한 hex


def test_report_checksum_changes_when_a_field_changes() -> None:
    report_a = _minimal_report(unaccounted_row_count=0)
    report_b = _minimal_report(unaccounted_row_count=5)
    assert report_checksum(report_a) != report_checksum(report_b)


def test_canonical_report_bytes_rejects_non_finite_metric() -> None:
    window = _window_result()
    non_finite_window = WindowResult(
        window_start=window.window_start,
        window_end=window.window_end,
        window_maturity=window.window_maturity,
        window_opened_count=window.window_opened_count,
        window_settled_count=window.window_settled_count,
        cutoff_at=window.cutoff_at,
        train_row_count=window.train_row_count,
        gate_train_row_count=window.gate_train_row_count,
        gate_test_row_count=window.gate_test_row_count,
        train_mean=math.nan,
        test_mean=window.test_mean,
        test_std=window.test_std,
        train_categories=window.train_categories,
        test_categories=window.test_categories,
        baselines=window.baselines,
        models=window.models,
        segments=window.segments,
        coverage=window.coverage,
        unlearned_baseline_cells=window.unlearned_baseline_cells,
        outcome=window.outcome,
        stability=window.stability,
        release_id=window.release_id,
    )
    report = _minimal_report(windows=(non_finite_window,))
    result = canonical_report_bytes(report)
    assert isinstance(result, CanonicalizationRejected)


def test_report_includes_excluded_windows_and_overlaps() -> None:
    from ml_engine.evaluation.windows import WeekMaturity
    from datetime import UTC, datetime

    window = WeekMaturity(
        start=datetime(2026, 6, 1, tzinfo=UTC),
        end=datetime(2026, 6, 8, tzinfo=UTC),
        opened_count=10,
        settled_count=1,
    )
    exclusion = WindowExclusion(
        window=window, reason=WindowExclusionReason.IMMATURE, evaluation_row_count=3
    )
    overlap = HoldoutOverlap(
        first_start=window.start, second_start=window.end, row_count=0
    )
    report = _minimal_report(excluded_windows=(exclusion,), holdout_overlaps=(overlap,))
    checksum = report_checksum(report)
    assert isinstance(checksum, str)
