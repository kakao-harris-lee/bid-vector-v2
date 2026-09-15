"""RED — `ml_engine.inference.maturity`(K7 이식, scope.md ①⑤, 설계 검토 구현 지시 5).
`0/0 → NoObservation`(legacy 접힘 제거), KST 주 경계(zoneinfo), 관측 없는 주 제외.
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

from ml_engine.inference.maturity import (
    MaturityWindow,
    NoObservation,
    Observed,
    SettlementObservation,
    build_weekly_maturity,
    resolve_maturity,
    week_start_utc,
)


def test_opened_count_zero_resolves_to_no_observation() -> None:
    maturity = resolve_maturity(opened_count=0, settled_count=0)
    assert isinstance(maturity, NoObservation)


def test_opened_count_positive_resolves_to_observed_ratio() -> None:
    maturity = resolve_maturity(opened_count=4, settled_count=3)
    assert isinstance(maturity, Observed)
    assert maturity.ratio == 0.75


def test_observed_rejects_non_positive_opened_count() -> None:
    import pytest

    with pytest.raises(ValueError, match="opened_count"):
        Observed(settled_count=0, opened_count=0)


def test_week_start_utc_monday_kst_boundary() -> None:
    # 2026-09-14(월) 00:00 KST = 2026-09-13 15:00 UTC.
    monday_early_kst_as_utc = datetime(2026, 9, 13, 15, 30, tzinfo=UTC)
    start = week_start_utc(monday_early_kst_as_utc)
    assert start == datetime(2026, 9, 13, 15, 0, tzinfo=UTC)


def test_week_start_utc_sunday_night_kst_still_previous_week() -> None:
    # 2026-09-13(일) 23:00 KST = 2026-09-13 14:00 UTC — 아직 그 주(월요일 이전).
    sunday_night_kst_as_utc = datetime(2026, 9, 13, 14, 0, tzinfo=UTC)
    start = week_start_utc(sunday_night_kst_as_utc)
    assert start == datetime(2026, 9, 6, 15, 0, tzinfo=UTC)


def test_build_weekly_maturity_excludes_weeks_without_observations() -> None:
    week1_open = datetime(2026, 9, 14, 3, 0, tzinfo=UTC)  # 2026-09-14 KST 12:00
    observations = [
        SettlementObservation(opened_at=week1_open, settled=True),
        SettlementObservation(opened_at=week1_open + timedelta(hours=1), settled=False),
    ]
    windows = build_weekly_maturity(observations, window_days=7)
    assert len(windows) == 1
    window = windows[0]
    assert isinstance(window, MaturityWindow)
    assert isinstance(window.maturity, Observed)
    assert window.maturity.opened_count == 2
    assert window.maturity.settled_count == 1


def test_build_weekly_maturity_empty_observations_is_empty_tuple() -> None:
    """빈 주는 표에 나타나지 않는다(legacy 그대로) — 관측이 아예 없으면 표 전체가 빈다."""
    assert build_weekly_maturity([], window_days=7) == ()


def test_maturity_window_contains_is_half_open() -> None:
    start = datetime(2026, 9, 13, 15, 0, tzinfo=UTC)
    window = MaturityWindow(
        start=start, end=start + timedelta(days=7), maturity=NoObservation()
    )
    assert window.contains(start)
    assert not window.contains(start + timedelta(days=7))
    assert window.contains(start + timedelta(days=6, hours=23))
