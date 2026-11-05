"""RED — `ml_engine.inference.maturity`(K7 이식, scope.md ①⑤, 설계 검토 구현 지시 5).
`0/0 → NoObservation`(legacy 접힘 제거), KST 주 경계(zoneinfo), 관측 없는 주 제외.

M9/ml-lifecycle — SET-06 acceptance 둘을 더한다: **시간축 provenance**(창마다 어느 시각
종류에서 몇 행이 왔는지 + 적용된 정책 version)와 **결측의 명시적 처리**(측정 불가 행이
사유별 계수로 산출되고, 모집단 = 성숙 분모 + 측정 불가 라는 **항등식**이 표의 생성
불변식이다 — 사유 없이 분모에서 사라지는 행이 0 건임을 계수로 잰다).
"""

from __future__ import annotations

from datetime import UTC, datetime, timedelta

import pytest

from ml_engine.inference.maturity import (
    MaturityTable,
    MaturityWindow,
    NoObservation,
    Observed,
    SettlementObservation,
    TimeAxisSourceCount,
    TimeAxisUnmeasurableCount,
    WindowTimeAxis,
    build_weekly_maturity,
    resolve_maturity,
    week_start_utc,
)
from ml_engine.inference.time_axis import (
    KnownTimeAxis,
    TimeAxisPolicy,
    TimeAxisSource,
    TimeAxisUnmeasurableReason,
    UnmeasurableTimeAxis,
    resolve_time_axis,
)

_STRICT = TimeAxisPolicy(version="inference-test", substitute_approved=False)
_PERMISSIVE = TimeAxisPolicy(version="inference-test", substitute_approved=True)
_WEEK1 = datetime(2026, 9, 14, 3, 0, tzinfo=UTC)  # 2026-09-14 KST 12:00 (월)


def _opened(moment: datetime, *, settled: bool) -> SettlementObservation:
    return SettlementObservation(
        time_axis=resolve_time_axis(
            opened_at=moment, bid_close_at=None, policy=_STRICT
        ),
        settled=settled,
    )


def _substituted(moment: datetime, *, settled: bool) -> SettlementObservation:
    return SettlementObservation(
        time_axis=resolve_time_axis(
            opened_at=None, bid_close_at=moment, policy=_PERMISSIVE
        ),
        settled=settled,
    )


def _unmeasurable(*, settled: bool) -> SettlementObservation:
    return SettlementObservation(
        time_axis=resolve_time_axis(opened_at=None, bid_close_at=None, policy=_STRICT),
        settled=settled,
    )


def test_opened_count_zero_resolves_to_no_observation() -> None:
    maturity = resolve_maturity(opened_count=0, settled_count=0)
    assert isinstance(maturity, NoObservation)


def test_opened_count_positive_resolves_to_observed_ratio() -> None:
    maturity = resolve_maturity(opened_count=4, settled_count=3)
    assert isinstance(maturity, Observed)
    assert maturity.ratio == 0.75


def test_observed_rejects_non_positive_opened_count() -> None:
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
    table = build_weekly_maturity(
        [
            _opened(_WEEK1, settled=True),
            _opened(_WEEK1 + timedelta(hours=1), settled=False),
        ],
        window_days=7,
        policy=_STRICT,
    )
    assert len(table.windows) == 1
    window = table.windows[0]
    assert isinstance(window, MaturityWindow)
    assert isinstance(window.maturity, Observed)
    assert window.maturity.opened_count == 2
    assert window.maturity.settled_count == 1


def test_build_weekly_maturity_empty_observations_is_an_empty_table() -> None:
    """빈 주는 표에 나타나지 않는다(legacy 그대로) — 관측이 아예 없으면 표 전체가 빈다.
    그래도 **정책 version 은 남는다**(어느 정책으로 잰 빈 표인가)."""
    table = build_weekly_maturity([], window_days=7, policy=_STRICT)
    assert table.windows == ()
    assert table.unmeasurable == ()
    assert table.population_row_count == 0
    assert table.policy_version == "inference-test"


def test_maturity_window_contains_is_half_open() -> None:
    start = datetime(2026, 9, 13, 15, 0, tzinfo=UTC)
    window = MaturityWindow(
        start=start,
        end=start + timedelta(days=7),
        maturity=NoObservation(),
        time_axis=WindowTimeAxis(policy_version="inference-test", source_counts=()),
    )
    assert window.contains(start)
    assert not window.contains(start + timedelta(days=7))
    assert window.contains(start + timedelta(days=6, hours=23))


def test_window_carries_time_axis_provenance_per_source() -> None:
    """SET-06 — 창의 시간축 값이 **어느 시각 종류에서 몇 행** 왔는지와 적용된 정책
    version 을 함께 싣는다."""
    table = build_weekly_maturity(
        [
            _opened(_WEEK1, settled=True),
            _substituted(_WEEK1 + timedelta(hours=2), settled=False),
            _substituted(_WEEK1 + timedelta(hours=3), settled=True),
        ],
        window_days=7,
        policy=_PERMISSIVE,
    )
    assert len(table.windows) == 1
    provenance = table.windows[0].time_axis
    assert provenance.policy_version == "inference-test"
    assert provenance.source_counts == (
        TimeAxisSourceCount(source=TimeAxisSource.OPENED_AT, row_count=1),
        TimeAxisSourceCount(source=TimeAxisSource.BID_CLOSE_AT, row_count=2),
    )


def test_unmeasurable_rows_are_counted_by_reason_and_never_assigned_to_a_week() -> None:
    """SET-06 — 측정 불가 행은 어느 주에도 배정되지 않고 **사유별 계수**로 산출된다."""
    table = build_weekly_maturity(
        [
            _opened(_WEEK1, settled=True),
            _unmeasurable(settled=True),
            _unmeasurable(settled=False),
            # 미승인 정책으로 해석된 대체 출처 행 — 값이 있어도 측정 불가로 떨어진다.
            SettlementObservation(
                time_axis=resolve_time_axis(
                    opened_at=None, bid_close_at=_WEEK1, policy=_STRICT
                ),
                settled=True,
            ),
        ],
        window_days=7,
        policy=_STRICT,
    )
    assert len(table.windows) == 1
    assert table.windows[0].maturity == Observed(settled_count=1, opened_count=1)
    assert table.unmeasurable == (
        TimeAxisUnmeasurableCount(
            reason=TimeAxisUnmeasurableReason.NO_TIME_AXIS_VALUE, row_count=2
        ),
        TimeAxisUnmeasurableCount(
            reason=TimeAxisUnmeasurableReason.UNAPPROVED_TIME_AXIS_SOURCE,
            row_count=1,
        ),
    )


def test_population_identity_holds_for_every_table() -> None:
    """항등식 — 모집단 = 성숙 분모(창의 개찰 계수 합) + 시간축 측정 불가. 사유 없이
    분모에서 사라지는 행이 0 건임을 **계수로** 잰다."""
    table = build_weekly_maturity(
        [
            _opened(_WEEK1, settled=True),
            _opened(_WEEK1 + timedelta(days=8), settled=False),
            _unmeasurable(settled=True),
        ],
        window_days=7,
        policy=_STRICT,
    )
    opened = sum(
        window.maturity.opened_count
        for window in table.windows
        if isinstance(window.maturity, Observed)
    )
    unmeasurable = sum(item.row_count for item in table.unmeasurable)
    assert table.population_row_count == 3
    assert opened + unmeasurable == table.population_row_count


def test_maturity_table_rejects_a_broken_population_identity() -> None:
    """표를 손으로 조립해 항등식을 깨는 길이 막힌다 — 생성 자체가 실패한다."""
    start = datetime(2026, 9, 13, 15, 0, tzinfo=UTC)
    window = MaturityWindow(
        start=start,
        end=start + timedelta(days=7),
        maturity=Observed(settled_count=1, opened_count=2),
        time_axis=WindowTimeAxis(
            policy_version="inference-test",
            source_counts=(
                TimeAxisSourceCount(source=TimeAxisSource.OPENED_AT, row_count=2),
            ),
        ),
    )
    with pytest.raises(ValueError, match="모집단"):
        MaturityTable(
            windows=(window,),
            windows_excluding_substitute=(window,),
            unmeasurable=(),
            population_row_count=99,
            substitute_row_count=0,
            policy_version="inference-test",
        )


def test_substitute_impact_is_observable_as_share_and_excluded_table() -> None:
    """SET-06 — 대체 출처를 쓴 행의 수와 **그 행들을 제외했을 때의 주별 성숙도**를 함께
    산출한다(대체가 주 경계 이동이나 분모 왜곡을 만들지 않았음을 볼 수 있게)."""
    table = build_weekly_maturity(
        [
            _opened(_WEEK1, settled=True),
            _substituted(_WEEK1 + timedelta(hours=1), settled=False),
        ],
        window_days=7,
        policy=_PERMISSIVE,
    )
    assert table.substitute_row_count == 1
    assert table.windows[0].maturity == Observed(settled_count=1, opened_count=2)
    assert len(table.windows_excluding_substitute) == 1
    assert table.windows_excluding_substitute[0].maturity == Observed(
        settled_count=1, opened_count=1
    )


def test_shipped_policy_makes_the_substitute_table_identical() -> None:
    """출하 정책은 대체를 승인하지 않으므로 대체 행이 0 이고 두 표가 같다 — 승인되는 날
    **같은 측정이 그대로 돈다**(측정이 그때 생기는 것이 아니다)."""
    table = build_weekly_maturity(
        [_opened(_WEEK1, settled=True)], window_days=7, policy=_STRICT
    )
    assert table.substitute_row_count == 0
    assert table.windows == table.windows_excluding_substitute


def test_build_weekly_maturity_rejects_rows_from_another_policy_version() -> None:
    """provenance 를 손으로 지어 다른 정책 version 을 섞는 길이 막힌다 — 한 표의 모든
    행은 같은 정책으로 해석됐어야 한다."""
    foreign = SettlementObservation(
        time_axis=KnownTimeAxis(
            value=_WEEK1,
            source=TimeAxisSource.OPENED_AT,
            policy_version="other-policy",
        ),
        settled=True,
    )
    with pytest.raises(ValueError, match="정책 version"):
        build_weekly_maturity([foreign], window_days=7, policy=_STRICT)


def test_unmeasurable_observation_is_not_silently_dropped() -> None:
    """측정 불가 행만 있는 입력도 **표에 남는다** — 빈 표(관측 없음)와 구별된다."""
    observation = _unmeasurable(settled=True)
    assert isinstance(observation.time_axis, UnmeasurableTimeAxis)
    table = build_weekly_maturity([observation], window_days=7, policy=_STRICT)
    assert table.windows == ()
    assert table.population_row_count == 1
    assert table.unmeasurable == (
        TimeAxisUnmeasurableCount(
            reason=TimeAxisUnmeasurableReason.NO_TIME_AXIS_VALUE, row_count=1
        ),
    )
