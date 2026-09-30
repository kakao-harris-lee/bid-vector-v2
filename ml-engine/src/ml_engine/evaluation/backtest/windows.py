"""`ml_engine.evaluation.backtest.windows` — 창 계획(D-6G-5·D-6G-14). 5C-2
`evaluation.windows` 의 **구조**(비중첩 창 + embargo + 표본 하한 + 제외를 사유와 함께
기록)를 잇되, 성숙도 축 대신 **개찰 주**로 자른다.

legacy 가 겪은 「다섯 origin 이 같은 5일로 붕괴」(5C-2 F4)를 되풀이하지 않으려고 비율
분할 API 를 만들지 않는다 — 창은 달력 블록이고, 겹침은 주장이 아니라
`window_overlaps` 로 **측정**한다.

**창의 이력은 embargo 앞에서 끊긴다** — 창 시작 `embargo_days` 일 전까지 개찰된 공고만
그 창의 경쟁 표본 원천이 된다. 전략에 넘기는 절단(`build_competitor_pool`)은 공고별로
한 번 더 걸리므로, 이 창 단위 절단은 그보다 **더 보수적인** 바깥 울타리다.

창 포함 여부를 공고일로 판정하는 규칙(D-6G-14)은 여기가 아니라 `exclusions` 가 진다
(시행일 경계 제외 ⑬) — 판정 단일 지점.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date, timedelta
from enum import StrEnum

from ml_engine.evaluation.backtest.exclusions import AdmittedNotice
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy


class WindowExclusionReason(StrEnum):
    INSUFFICIENT_ROWS = "INSUFFICIENT_ROWS"
    NO_HISTORY = "NO_HISTORY"


@dataclass(frozen=True)
class BacktestWindow:
    """반개구간 `[start, end)` 의 개찰 주 하나."""

    index: int
    start: date
    end: date

    def contains(self, value: date) -> bool:
        return self.start <= value < self.end


@dataclass(frozen=True)
class WindowAssignment:
    """창 하나와 그 창에서 채점할 공고, 그리고 경쟁 표본의 원천이 되는 이력."""

    window: BacktestWindow
    notices: tuple[AdmittedNotice, ...]
    history: tuple[AdmittedNotice, ...]


@dataclass(frozen=True)
class WindowExclusion:
    window: BacktestWindow
    reason: WindowExclusionReason
    row_count: int


@dataclass(frozen=True)
class WindowPlan:
    selected: tuple[WindowAssignment, ...]
    excluded: tuple[WindowExclusion, ...]


@dataclass(frozen=True)
class WindowOverlap:
    """두 창이 공유하는 공고 수 — 설계상 0 이고, 0 이 아니면 드러나야 한다."""

    first_index: int
    second_index: int
    notice_count: int


def _calendar_windows(
    opened_days: Sequence[date], policy: StrategyBacktestPolicy
) -> tuple[BacktestWindow, ...]:
    """가장 이른 개찰일에서 시작해 `window.days` 폭의 블록으로 자른다 — 블록 경계가
    데이터가 아니라 달력에서 나오므로 유리한 주만 고를 자리가 없다(우회 ⑤)."""
    if not opened_days:
        return ()
    span = timedelta(days=policy.windows.days)
    start = min(opened_days)
    last = max(opened_days)
    windows: list[BacktestWindow] = []
    while start <= last:
        windows.append(BacktestWindow(len(windows), start, start + span))
        start += span
    return tuple(windows)


def plan_backtest_windows(
    admitted: Sequence[AdmittedNotice], policy: StrategyBacktestPolicy
) -> WindowPlan:
    """창 전부를 만들고, 표본 하한·이력 부재로 빠지는 창을 사유와 함께 기록한다.
    **모든 창이 결과에 남는다** — 고른 창만 공시하면 창 쇼핑과 구별되지 않는다."""
    embargo = timedelta(days=policy.windows.embargo_days)
    windows = _calendar_windows([item.opened_on for item in admitted], policy)
    selected: list[WindowAssignment] = []
    excluded: list[WindowExclusion] = []
    for window in windows:
        notices = tuple(item for item in admitted if window.contains(item.opened_on))
        history = tuple(
            item for item in admitted if item.opened_on < window.start - embargo
        )
        if len(notices) < policy.verdict.min_window_rows:
            excluded.append(
                WindowExclusion(
                    window, WindowExclusionReason.INSUFFICIENT_ROWS, len(notices)
                )
            )
        elif not history:
            excluded.append(
                WindowExclusion(window, WindowExclusionReason.NO_HISTORY, len(notices))
            )
        else:
            selected.append(WindowAssignment(window, notices, history))
    return WindowPlan(tuple(selected), tuple(excluded))


def window_overlaps(plan: WindowPlan) -> tuple[WindowOverlap, ...]:
    """창 쌍마다 공유하는 공고 수 — 비중첩을 주장이 아니라 측정으로 만든다."""
    keys = [
        {item.row.notice.notice_key_hash for item in assignment.notices}
        for assignment in plan.selected
    ]
    return tuple(
        WindowOverlap(
            first_index=plan.selected[first].window.index,
            second_index=plan.selected[second].window.index,
            notice_count=len(keys[first] & keys[second]),
        )
        for first in range(len(keys))
        for second in range(first + 1, len(keys))
    )
