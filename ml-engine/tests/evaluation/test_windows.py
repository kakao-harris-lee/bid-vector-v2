"""RED — `ml_engine.evaluation.windows`(scope ⑤, `Reuse: award_rate_windows.py@ed4b06c`).
성숙도는 **입력**(D-5C2-2) — `WeekMaturity(start, end, opened_count, settled_count)`.
비율 분할(`train_fraction`) API 는 만들지 않는다(F4)."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import UTC, datetime, timedelta

import pytest

from ml_engine.evaluation.policy import EvaluationPolicy
from ml_engine.evaluation.windows import (
    InvalidMaturityInput,
    WeekMaturity,
    WindowExclusionReason,
    holdout_overlaps,
    indices_in_window,
    plan_evaluation_windows,
)

_POLICY = EvaluationPolicy(
    version="test",
    paired_t_threshold=2.58,
    gate_baseline="category_x_band",
    gate_model="gbm_all_strata",
    gate_stratum="clean-base",
    maturity_threshold=0.70,
    min_evaluation_rows=2,
    max_origins=5,
    agency_baseline_min_count=2,
    stability_seeds=(1,),
    amount_band_edges=(1e8, 5e8, 1e9, 5e9),
    segment_axes=("category", "amount_band"),
)


@dataclass(frozen=True)
class _Row:
    opened_at: datetime
    stratum: str


def _dt(day: int) -> datetime:
    return datetime(2026, 1, 1, tzinfo=UTC) + timedelta(days=day)


def _week(start_day: int, *, opened: int, settled: int) -> WeekMaturity:
    return WeekMaturity(
        start=_dt(start_day), end=_dt(start_day + 7), opened_count=opened, settled_count=settled
    )


def test_week_maturity_rejects_end_not_after_start() -> None:
    with pytest.raises(ValueError):
        WeekMaturity(start=_dt(10), end=_dt(10), opened_count=1, settled_count=1)


def test_week_maturity_rejects_negative_counts() -> None:
    with pytest.raises(ValueError):
        WeekMaturity(start=_dt(1), end=_dt(8), opened_count=-1, settled_count=0)


def test_indices_in_window_filters_by_stratum_and_bounds() -> None:
    rows = [
        _Row(opened_at=_dt(2), stratum="clean-base"),
        _Row(opened_at=_dt(9), stratum="clean-base"),  # 창 밖(다음 주)
        _Row(opened_at=_dt(3), stratum="reserve-estimate"),  # 층 불일치
    ]
    window = _week(1, opened=10, settled=8)
    indices = indices_in_window(rows, window, stratum="clean-base")
    assert indices == [0]


def test_plan_evaluation_windows_excludes_immature_window() -> None:
    rows = [_Row(opened_at=_dt(2), stratum="clean-base") for _ in range(5)]
    immature = _week(1, opened=10, settled=5)  # 0.5 < 0.70
    plan = plan_evaluation_windows(rows, [immature], _POLICY)
    assert not isinstance(plan, InvalidMaturityInput)
    assert plan.selected == ()
    assert len(plan.excluded) == 1
    assert plan.excluded[0].reason == WindowExclusionReason.IMMATURE


def test_plan_evaluation_windows_zero_opened_count_is_immature() -> None:
    rows = [_Row(opened_at=_dt(2), stratum="clean-base")]
    window = _week(1, opened=0, settled=0)
    plan = plan_evaluation_windows(rows, [window], _POLICY)
    assert not isinstance(plan, InvalidMaturityInput)
    assert plan.excluded[0].reason == WindowExclusionReason.IMMATURE


def test_plan_evaluation_windows_excludes_insufficient_rows() -> None:
    rows = [
        _Row(opened_at=_dt(0), stratum="clean-base"),  # 학습측 (opened_at < start)
        _Row(opened_at=_dt(2), stratum="clean-base"),  # 창 안 1건 < min_evaluation_rows(2)
    ]
    mature_but_thin = _week(1, opened=10, settled=9)
    plan = plan_evaluation_windows(rows, [mature_but_thin], _POLICY)
    assert not isinstance(plan, InvalidMaturityInput)
    assert plan.excluded[0].reason == WindowExclusionReason.INSUFFICIENT_EVALUATION_ROWS


def test_plan_evaluation_windows_excludes_no_training_rows() -> None:
    rows = [
        _Row(opened_at=_dt(2), stratum="clean-base"),
        _Row(opened_at=_dt(3), stratum="clean-base"),
    ]  # 전부 창 안 — 학습측(opened_at < start)이 없다
    mature = _week(1, opened=10, settled=9)
    plan = plan_evaluation_windows(rows, [mature], _POLICY)
    assert not isinstance(plan, InvalidMaturityInput)
    assert plan.excluded[0].reason == WindowExclusionReason.NO_TRAINING_ROWS


def test_plan_evaluation_windows_selects_valid_window() -> None:
    rows = [
        _Row(opened_at=_dt(0), stratum="clean-base"),
        _Row(opened_at=_dt(2), stratum="clean-base"),
        _Row(opened_at=_dt(3), stratum="clean-base"),
    ]
    mature = _week(1, opened=10, settled=9)
    plan = plan_evaluation_windows(rows, [mature], _POLICY)
    assert not isinstance(plan, InvalidMaturityInput)
    assert plan.selected == (mature,)
    assert plan.excluded == ()


def test_plan_evaluation_windows_earliest_reason_wins() -> None:
    """규칙 표 순서 — 성숙도 미달이면서 표본도 적으면 IMMATURE 가 기록된다."""
    rows = [_Row(opened_at=_dt(2), stratum="clean-base")]  # 학습측도 없고 표본도 1건
    immature_and_thin = _week(1, opened=10, settled=1)  # 0.1 < 0.70
    plan = plan_evaluation_windows(rows, [immature_and_thin], _POLICY)
    assert not isinstance(plan, InvalidMaturityInput)
    assert plan.excluded[0].reason == WindowExclusionReason.IMMATURE


def test_plan_evaluation_windows_beyond_max_origins_drops_oldest() -> None:
    policy = EvaluationPolicy(
        version="test",
        paired_t_threshold=2.58,
        gate_baseline="category_x_band",
        gate_model="gbm_all_strata",
        gate_stratum="clean-base",
        maturity_threshold=0.70,
        min_evaluation_rows=2,
        max_origins=1,
        agency_baseline_min_count=2,
        stability_seeds=(1,),
        amount_band_edges=(1e8, 5e8, 1e9, 5e9),
        segment_axes=("category", "amount_band"),
    )
    rows = [
        _Row(opened_at=_dt(0), stratum="clean-base"),
        _Row(opened_at=_dt(2), stratum="clean-base"),
        _Row(opened_at=_dt(3), stratum="clean-base"),
        _Row(opened_at=_dt(9), stratum="clean-base"),
        _Row(opened_at=_dt(10), stratum="clean-base"),
    ]
    week1 = _week(1, opened=10, settled=9)
    week2 = _week(8, opened=10, settled=9)
    plan = plan_evaluation_windows(rows, [week1, week2], policy)
    assert not isinstance(plan, InvalidMaturityInput)
    assert plan.selected == (week2,)
    beyond = [e for e in plan.excluded if e.reason == WindowExclusionReason.BEYOND_MAX_ORIGINS]
    assert len(beyond) == 1
    assert beyond[0].window == week1


def test_plan_evaluation_windows_rejects_overlapping_maturity_windows() -> None:
    rows: list[_Row] = []
    week1 = _week(1, opened=1, settled=1)
    overlapping = WeekMaturity(
        start=_dt(5), end=_dt(12), opened_count=1, settled_count=1
    )
    result = plan_evaluation_windows(rows, [week1, overlapping], _POLICY)
    assert isinstance(result, InvalidMaturityInput)


def test_no_fraction_based_split_api_exists() -> None:
    """F4 — 비율 분할 API 를 만들지 않는다(정적 확인)."""
    import ml_engine.evaluation.windows as windows_module

    assert not any("fraction" in name.lower() for name in dir(windows_module))


def test_holdout_overlaps_counts_shared_indices() -> None:
    rows = [
        _Row(opened_at=_dt(2), stratum="clean-base"),
        _Row(opened_at=_dt(3), stratum="clean-base"),
    ]
    window_a = WeekMaturity(start=_dt(1), end=_dt(8), opened_count=1, settled_count=1)
    window_b = WeekMaturity(start=_dt(1), end=_dt(10), opened_count=1, settled_count=1)
    overlaps = holdout_overlaps(rows, [window_a, window_b], stratum="clean-base")
    assert len(overlaps) == 1
    assert overlaps[0].row_count == 2


def test_holdout_overlaps_zero_for_disjoint_windows() -> None:
    rows = [
        _Row(opened_at=_dt(2), stratum="clean-base"),
        _Row(opened_at=_dt(9), stratum="clean-base"),
    ]
    window_a = _week(1, opened=1, settled=1)
    window_b = _week(8, opened=1, settled=1)
    overlaps = holdout_overlaps(rows, [window_a, window_b], stratum="clean-base")
    assert overlaps[0].row_count == 0
