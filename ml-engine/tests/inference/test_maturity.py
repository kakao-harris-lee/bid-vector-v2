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


def test_unmeasurable_rows_from_another_policy_version_are_rejected() -> None:
    """cr M-1 재현 — 앞 판은 정책 version 검사가 `KnownTimeAxis` 안에만 있어서, **대체를
    승인한 정책**이 공시된 표가 그 정책으로는 분모에 들어갔어야 할 행을 「측정 불가」로
    적을 수 있었다(예외 없이 통과했다).

    두 정책의 **version 이 달라야** 이 test 가 그 축을 잰다 — 모듈 상단의 `_STRICT`·
    `_PERMISSIVE` 는 승인 칸만 다르고 version 이 같아서(의도: 승인 축만 가른다) 그 둘로는
    헛돈다. 첫 판이 실제로 그래서 초록이었다."""
    approving_other_version = TimeAxisPolicy(
        version="inference-other", substitute_approved=True
    )
    unapproved_elsewhere = SettlementObservation(
        time_axis=resolve_time_axis(
            opened_at=None, bid_close_at=_WEEK1, policy=_STRICT
        ),
        settled=True,
    )
    # 양성 대조 — 같은 정책으로 해석한 행은 그 정책에서 통과한다(예외가 version 때문임을
    # 고정한다).
    same_policy = build_weekly_maturity(
        [unapproved_elsewhere], window_days=7, policy=_STRICT
    )
    assert same_policy.population_row_count == 1
    with pytest.raises(ValueError, match="정책 version"):
        build_weekly_maturity(
            [unapproved_elsewhere], window_days=7, policy=approving_other_version
        )


def _window_with(
    *, opened: int, settled: int, sources: tuple[TimeAxisSourceCount, ...]
) -> MaturityWindow:
    start = datetime(2026, 9, 13, 15, 0, tzinfo=UTC)
    return MaturityWindow(
        start=start,
        end=start + timedelta(days=7),
        maturity=resolve_maturity(opened_count=opened, settled_count=settled),
        time_axis=WindowTimeAxis(
            policy_version="inference-test", source_counts=sources
        ),
    )


def test_maturity_table_rejects_source_counts_that_disagree_with_the_denominator() -> (
    None
):
    """cr M-2 ⑵ — provenance 가 분모와 어긋나면 표를 만들 수 없다."""
    window = _window_with(
        opened=2,
        settled=1,
        sources=(TimeAxisSourceCount(source=TimeAxisSource.OPENED_AT, row_count=5),),
    )
    with pytest.raises(ValueError, match="출처별 계수"):
        MaturityTable(
            windows=(window,),
            windows_excluding_substitute=(window,),
            unmeasurable=(),
            population_row_count=2,
            substitute_row_count=0,
            policy_version="inference-test",
        )


def test_maturity_table_rejects_ghost_rows_on_a_no_observation_window() -> None:
    """cr M-2 ⑴ — `NoObservation` 창이 출처 계수로 **유령 행**을 들 수 없다(앞 판은
    개찰 계수 기준으로만 걸러 이 창이 등식에 보이지 않았다)."""
    window = _window_with(
        opened=0,
        settled=0,
        sources=(TimeAxisSourceCount(source=TimeAxisSource.OPENED_AT, row_count=5),),
    )
    with pytest.raises(ValueError, match="출처별 계수"):
        MaturityTable(
            windows=(window,),
            windows_excluding_substitute=(window,),
            unmeasurable=(),
            population_row_count=0,
            substitute_row_count=0,
            policy_version="inference-test",
        )


def test_maturity_table_rejects_a_substitute_count_that_contradicts_the_windows() -> (
    None
):
    """cr M-2 ⑶ — 「대체가 주 경계를 밀었는데 안 보인다」를 막는 축이 바로 이 값이다.
    앞 판은 이 값이 창들과 모순이어도 생성이 성공했다."""
    window = _window_with(
        opened=1,
        settled=1,
        sources=(TimeAxisSourceCount(source=TimeAxisSource.OPENED_AT, row_count=1),),
    )
    with pytest.raises(ValueError, match="대체 출처 행 수"):
        MaturityTable(
            windows=(window,),
            windows_excluding_substitute=(window,),
            unmeasurable=(),
            population_row_count=1,
            substitute_row_count=1,
            policy_version="inference-test",
        )


# ── cr r3 M-3 — 대체 제외 표도 등식 묶음에 든다 ──────────────────────────────
# 앞 라운드는 `substitute_row_count` 만 닫고 `windows_excluding_substitute` 는 읽지 않았다.
# 그런데 「대체가 주 경계를 밀었는데 안 보인다」(설계 검토 우회 7)를 막는 축이 **두 표의
# 대조**다 — 검사가 없으면 대체 행이 1 인데 두 표가 동일한 표가 섰다(cr 실측).
#
# 아래 셋은 검사 **각각을 단독으로** 겨눈다(입력을 깎아, 거부를 만들 수 있는 검사가 하나만
# 남게 한다). 넷째는 생산자 출력의 양성 대조다.

_OPENED_ONE = TimeAxisSourceCount(source=TimeAxisSource.OPENED_AT, row_count=1)
_SUBSTITUTED_ONE = TimeAxisSourceCount(source=TimeAxisSource.BID_CLOSE_AT, row_count=1)


def test_substitute_exclusion_table_may_not_contain_substitute_rows() -> None:
    """검사 1 — 「대체를 뺀 표」가 대체 출처 행을 들고 있으면 그 이름이 거짓이다."""
    with pytest.raises(ValueError, match="대체 출처 행"):
        MaturityTable(
            windows=(
                _window_with(
                    opened=2, settled=1, sources=(_OPENED_ONE, _SUBSTITUTED_ONE)
                ),
            ),
            windows_excluding_substitute=(
                _window_with(opened=1, settled=1, sources=(_SUBSTITUTED_ONE,)),
            ),
            unmeasurable=(),
            population_row_count=2,
            substitute_row_count=1,
            policy_version="inference-test",
        )


def test_substitute_exclusion_table_keeps_the_opened_at_counts() -> None:
    """검사 2 — 두 표의 **창별 개찰-출처 계수**는 같아야 한다. 대체를 빼는 것이 개찰 행을
    함께 지우거나 주를 옮기면 그 자리에서 드러난다(검사 1 은 통과하는 입력이다 — 제외 표에
    대체 출처 행이 없다)."""
    with pytest.raises(ValueError, match="개찰 출처 계수"):
        MaturityTable(
            windows=(
                _window_with(
                    opened=2, settled=1, sources=(_OPENED_ONE, _SUBSTITUTED_ONE)
                ),
            ),
            windows_excluding_substitute=(
                _window_with(opened=0, settled=0, sources=()),
            ),
            unmeasurable=(),
            population_row_count=2,
            substitute_row_count=1,
            policy_version="inference-test",
        )


def test_without_substitute_rows_the_two_tables_must_be_identical() -> None:
    """검사 3 — 대체 행이 **없으면** 두 표는 같아야 한다. 검사 1·2 는 출처 계수만 보므로
    `settled_count` 가 갈린 표를 통과시킨다 — 그 틈을 이 등식이 닫는다."""
    with pytest.raises(ValueError, match="대체 행이 없으면"):
        MaturityTable(
            windows=(_window_with(opened=1, settled=1, sources=(_OPENED_ONE,)),),
            windows_excluding_substitute=(
                _window_with(opened=1, settled=0, sources=(_OPENED_ONE,)),
            ),
            unmeasurable=(),
            population_row_count=1,
            substitute_row_count=0,
            policy_version="inference-test",
        )


def test_the_producer_satisfies_the_substitute_exclusion_equalities() -> None:
    """**양성 대조** — 실제 생산자의 출력이 위 등식들을 통과한다(거짓 양성 0). 대체 행이
    있는 표와 없는 표 둘 다 본다."""
    with_substitute = build_weekly_maturity(
        [
            _opened(_WEEK1, settled=True),
            _substituted(_WEEK1 + timedelta(hours=2), settled=False),
        ],
        window_days=7,
        policy=_PERMISSIVE,
    )
    assert with_substitute.substitute_row_count == 1
    assert with_substitute.windows_excluding_substitute != with_substitute.windows

    without = build_weekly_maturity(
        [_opened(_WEEK1, settled=True)], window_days=7, policy=_PERMISSIVE
    )
    assert without.substitute_row_count == 0
    assert without.windows_excluding_substitute == without.windows


# ── cr r4 M-1 — 대체 행이 **있을 때** 제외 표의 분자·분모를 묶는다 ────────────
# 앞 라운드 등식 3 은 `substitute_row_count == 0` 가드 안에 있어 **대체 행이 없을 때만**
# 발화한다. 그런데 두 표의 대조가 뜻을 갖는 경우는 대체 행이 **있을 때**다. 등식 1·2 는
# 출처 계수만 보고, `_check_population`·`_check_provenance` 는 `self.windows` 만 순회하므로
# 대체 행이 있으면 제외 표의 `maturity`(SET-06 이 보는 바로 그 비율)가 아무 것에도 묶이지
# 않았다(cr probe: 제외 표 분자·분모를 지어낸 표가 생성 성공).


def test_exclusion_table_window_sources_must_agree_with_its_own_denominator() -> None:
    """창별 출처 등식을 **제외 표에도** 돌린다 — 대체 행이 있어도 발화한다."""
    with pytest.raises(ValueError, match="제외 표의 창"):
        MaturityTable(
            windows=(
                _window_with(
                    opened=2, settled=1, sources=(_OPENED_ONE, _SUBSTITUTED_ONE)
                ),
            ),
            # 출처 계수는 개찰 1 인데 분모를 9 로 지어냈다
            windows_excluding_substitute=(
                _window_with(opened=9, settled=9, sources=(_OPENED_ONE,)),
            ),
            unmeasurable=(),
            population_row_count=2,
            substitute_row_count=1,
            policy_version="inference-test",
        )


def test_exclusion_table_cannot_carry_the_substituted_settlement() -> None:
    """제외 표가 **대체 행의 정산**을 그대로 들면 비율이 1 을 넘는다. 그 모양은 표 등식이
    아니라 **`Observed` 생성**에서 거부된다(cr r4 L-3 — 더 강한 자리로 올렸다): 창을 **짓는
    것 자체**가 불가능하므로 두 표와 모든 창에 걸린다."""
    with pytest.raises(ValueError, match="개찰 수를 넘을 수 없다"):
        _window_with(opened=1, settled=2, sources=(_OPENED_ONE,))


def test_the_producer_satisfies_the_exclusion_denominator_equalities() -> None:
    """**양성 대조** — 대체 행이 있는 생산자 출력이 위 둘을 통과한다."""
    table = build_weekly_maturity(
        [
            _opened(_WEEK1, settled=True),
            _substituted(_WEEK1 + timedelta(hours=2), settled=True),
        ],
        window_days=7,
        policy=_PERMISSIVE,
    )
    assert table.substitute_row_count == 1
    excluded = table.windows_excluding_substitute[0]
    assert excluded.maturity == Observed(settled_count=1, opened_count=1)


def test_observed_rejects_a_ratio_above_one() -> None:
    """cr r4 L-3 — 정산은 개찰의 부분집합이다. 앞 판은 분모만 보아 비율 2.0 이 섰다."""
    with pytest.raises(ValueError, match="개찰 수를 넘을 수 없다"):
        Observed(settled_count=2, opened_count=1)
    with pytest.raises(ValueError, match="음수일 수 없다"):
        Observed(settled_count=-1, opened_count=1)
    assert Observed(settled_count=1, opened_count=1).ratio == 1.0  # 양성 대조


def test_observed_rejects_a_negative_numerator_before_the_upper_bound() -> None:
    """cr r5 L-6 — 두 가드는 독립이다. `settled=-1, opened=1` 은 상한을 통과하므로 음수
    가드가 **먼저** 보지 않으면 그 독립성이 읽는 순서로 드러나지 않는다."""
    with pytest.raises(ValueError, match="음수일 수 없다"):
        Observed(settled_count=-1, opened_count=1)
