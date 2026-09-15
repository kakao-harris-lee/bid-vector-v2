"""Reuse: bid-vector/app/domain/settlement_maturity.py@ed4b06c

`ml_engine.inference.maturity` — K7 이식(정산 성숙도, scope.md ①). legacy `0/0 → 0.0`
접힘(`MaturityWindow.maturity` 프로퍼티, `:83-86`)을 `Maturity = Observed | NoObservation`
sealed 타입으로 대체한다(data-dictionary.md §6.4 「`0/0`을 비율로 표현할 수 있는 경로를
만들지 않는다」). `app.core.time`(`ensure_utc`·`to_kst`) 의존은 이식하지 않고
`zoneinfo.ZoneInfo("Asia/Seoul")`로 대체한다(stdlib, legacy `KST = timezone(timedelta(
hours=9))`와 값은 같으나 DST 규칙까지 명시적으로 다루는 표준 라이브러리 경로).
"""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import UTC, datetime, timedelta
from zoneinfo import ZoneInfo

_KST = ZoneInfo("Asia/Seoul")


@dataclass(frozen=True)
class SettlementObservation:
    """공고 하나의 (개찰 시각, 결과를 아는가) — legacy 그대로."""

    opened_at: datetime
    settled: bool


@dataclass(frozen=True)
class Observed:
    """이 구간에 개찰이 1건 이상 있었다 — `ratio`는 분모가 항상 양수다."""

    settled_count: int
    opened_count: int

    def __post_init__(self) -> None:
        if self.opened_count <= 0:
            raise ValueError(
                "Observed.opened_count 는 0보다 커야 한다 — 0/0 은 NoObservation 이다"
            )

    @property
    def ratio(self) -> float:
        return self.settled_count / self.opened_count


@dataclass(frozen=True)
class NoObservation:
    """이 구간에 개찰이 없었다(legacy `0/0 → 0.0` 접힘 제거) — 비율을 나르지 않는다."""


type Maturity = Observed | NoObservation


def resolve_maturity(*, opened_count: int, settled_count: int) -> Maturity:
    """`(opened_count, settled_count)` → `Maturity` — `opened_count <= 0`이면
    `NoObservation`(legacy `0/0 → 0.0` 접힘의 대체, data-dictionary.md §6.4)."""
    if opened_count <= 0:
        return NoObservation()
    return Observed(settled_count=settled_count, opened_count=opened_count)


@dataclass(frozen=True)
class MaturityWindow:
    """구간 `[start, end)` 하나의 성숙도 — legacy 그대로 반개구간(시작 포함, 끝 제외)."""

    start: datetime
    end: datetime
    maturity: Maturity

    def contains(self, value: datetime) -> bool:
        return self.start <= _ensure_utc(value) < self.end


def _ensure_utc(value: datetime) -> datetime:
    """naive datetime 은 UTC 로 간주한다(legacy `app.core.time.ensure_utc`와 같은 계약)."""
    return value if value.tzinfo is not None else value.replace(tzinfo=UTC)


def week_start_utc(value: datetime) -> datetime:
    """`value`가 속한 **KST 주**의 시작(월요일 00:00 KST)을 UTC 로 돌려준다(legacy 그대로,
    `zoneinfo` 로 KST 변환)."""
    local = _ensure_utc(value).astimezone(_KST)
    monday = local.replace(hour=0, minute=0, second=0, microsecond=0) - timedelta(
        days=local.weekday()
    )
    return monday.astimezone(UTC)


def build_weekly_maturity(
    observations: Sequence[SettlementObservation], *, window_days: int
) -> tuple[MaturityWindow, ...]:
    """관측을 KST 주로 묶어 성숙도 구간 표를 만든다(시작 시각 오름차순, legacy 그대로 —
    관측 없는 주는 표에 나타나지 않는다). `window_days`는 `InferencePolicy.
    maturity_window_days`(D-5D-8, legacy `MATURITY_WINDOW_LENGTH = 7일`)에서 온다."""
    window_length = timedelta(days=window_days)
    totals: dict[datetime, list[int]] = defaultdict(lambda: [0, 0])
    for observation in observations:
        counts = totals[week_start_utc(observation.opened_at)]
        counts[0] += 1
        counts[1] += 1 if observation.settled else 0
    return tuple(
        MaturityWindow(
            start=start,
            end=start + window_length,
            maturity=resolve_maturity(opened_count=opened, settled_count=settled),
        )
        for start, (opened, settled) in sorted(totals.items())
    )
