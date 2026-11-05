"""Reuse: bid-vector/app/domain/settlement_maturity.py@ed4b06c

`ml_engine.inference.maturity` — K7 이식(정산 성숙도, scope.md ①). legacy `0/0 → 0.0`
접힘(`MaturityWindow.maturity` 프로퍼티, `:83-86`)을 `Maturity = Observed | NoObservation`
sealed 타입으로 대체한다(data-dictionary.md §6.4 「`0/0`을 비율로 표현할 수 있는 경로를
만들지 않는다」). `app.core.time`(`ensure_utc`·`to_kst`) 의존은 이식하지 않고
`zoneinfo.ZoneInfo("Asia/Seoul")`로 대체한다(stdlib, legacy `KST = timezone(timedelta(
hours=9))`와 값은 같으나 DST 규칙까지 명시적으로 다루는 표준 라이브러리 경로).

M9/ml-lifecycle — SET-06 acceptance 둘을 더했다. 관측은 이제 `datetime` 이 아니라
`TimeAxis`(`ml_engine.inference.time_axis`)를 들고, 집계 결과는 창 목록이 아니라
`MaturityTable` 이다. 표가 지는 것 셋:
- **창별 provenance** — 어느 시각 종류에서 몇 행이 왔는지 + 적용된 정책 version.
- **측정 불가 계수** — 시간축을 정할 수 없던 행을 어느 주에도 배정하지 않고 사유별로 센다.
- **모집단 항등식** — 모집단 = 창의 개찰 계수 합 + 측정 불가 합. 생성 불변식이라, 사유
  없이 분모에서 사라지는 행이 있으면 표를 **만들 수 없다**(침묵이 형태로 불가능해진다).
그리고 대체 출처의 영향을 볼 수 있게 **대체 행을 제외한 주별 표**를 함께 낸다 — 출하
정책은 대체를 승인하지 않아 지금은 두 표가 같고, 승인되는 날 같은 측정이 그대로 돈다.
"""

from __future__ import annotations

from collections import defaultdict
from collections.abc import Sequence
from dataclasses import dataclass, field
from datetime import UTC, datetime, timedelta
from zoneinfo import ZoneInfo

from ml_engine.inference.time_axis import (
    KnownTimeAxis,
    TimeAxis,
    TimeAxisPolicy,
    TimeAxisSource,
    TimeAxisUnmeasurableReason,
)

_KST = ZoneInfo("Asia/Seoul")


@dataclass(frozen=True)
class SettlementObservation:
    """공고 하나의 (시간축, 결과를 아는가). legacy 는 여기에 `opened_at: datetime` 을
    들었다 — 그 형태에는 「이 시각이 어디서 왔는가」와 「시각을 모른다」를 적을 자리가
    없었다(M9/ml-lifecycle, SET-06)."""

    time_axis: TimeAxis
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
class TimeAxisSourceCount:
    """한 창에서 그 시각 종류로 들어온 행 수."""

    source: TimeAxisSource
    row_count: int


@dataclass(frozen=True)
class WindowTimeAxis:
    """창 하나의 시간축 provenance — 출처별 계수 + 적용된 정책 version(M9/ml-lifecycle).
    비율만 들고 다니지 않는 이유와 같은 축이다: 「0.75인데 4건」과 「0.75인데 4,000건」이
    구별돼야 하듯, 「개찰 시각으로 잰 주」와 「대체 시각이 섞인 주」가 구별돼야 한다."""

    policy_version: str
    source_counts: tuple[TimeAxisSourceCount, ...]


@dataclass(frozen=True)
class MaturityWindow:
    """구간 `[start, end)` 하나의 성숙도 — legacy 그대로 반개구간(시작 포함, 끝 제외)."""

    start: datetime
    end: datetime
    maturity: Maturity
    time_axis: WindowTimeAxis
    """기본값 없음 — 「provenance 를 안 넘겼다」와 「출처가 없다」를 구별한다."""

    def contains(self, value: datetime) -> bool:
        return self.start <= _ensure_utc(value) < self.end


@dataclass(frozen=True)
class TimeAxisUnmeasurableCount:
    """시간축을 정할 수 없던 행의 사유별 계수 — 리포트의 **별도 항목**(SET-06)."""

    reason: TimeAxisUnmeasurableReason
    row_count: int


@dataclass(frozen=True)
class MaturityTable:
    """성숙도 리포트 한 벌. `build_weekly_maturity` 가 유일 생성 경로이지만, 손으로
    조립해도 **항등식**이 생성에서 강제된다."""

    windows: tuple[MaturityWindow, ...]
    windows_excluding_substitute: tuple[MaturityWindow, ...]
    """승인된 대체 출처로 들어온 행을 뺀 주별 표 — 대체가 주 경계 이동이나 분모 왜곡을
    만들지 않았음을 두 표의 대조로 확인할 수 있다(SET-06)."""

    unmeasurable: tuple[TimeAxisUnmeasurableCount, ...]
    population_row_count: int
    substitute_row_count: int
    policy_version: str

    def __post_init__(self) -> None:
        if self.population_row_count < 0 or self.substitute_row_count < 0:
            raise ValueError("행 계수는 음수일 수 없다")
        counted = sum(
            window.maturity.opened_count
            for window in self.windows
            if isinstance(window.maturity, Observed)
        ) + sum(item.row_count for item in self.unmeasurable)
        if counted != self.population_row_count:
            raise ValueError(
                "모집단은 성숙 분모와 시간축 측정 불가의 합이어야 한다 — "
                f"모집단 {self.population_row_count} != 계수 {counted}"
            )


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


@dataclass
class _WeekTally:
    """한 주의 집계 누적 — 개찰·정산 계수와 출처별 계수."""

    opened_count: int = 0
    settled_count: int = 0
    sources: dict[TimeAxisSource, int] = field(default_factory=dict)


def _weekly_windows(
    axes: Sequence[tuple[KnownTimeAxis, bool]],
    *,
    window_length: timedelta,
    policy_version: str,
) -> tuple[MaturityWindow, ...]:
    """KST 주로 묶어 창 목록을 만든다(시작 시각 오름차순 — 관측 없는 주는 나타나지
    않는다, legacy 그대로). 출처별 계수는 `TimeAxisSource` 선언 순서로 싣는다."""
    totals: dict[datetime, _WeekTally] = defaultdict(_WeekTally)
    for axis, settled in axes:
        tally = totals[week_start_utc(axis.value)]
        tally.opened_count += 1
        tally.settled_count += 1 if settled else 0
        tally.sources[axis.source] = tally.sources.get(axis.source, 0) + 1
    return tuple(
        MaturityWindow(
            start=start,
            end=start + window_length,
            maturity=resolve_maturity(
                opened_count=tally.opened_count, settled_count=tally.settled_count
            ),
            time_axis=WindowTimeAxis(
                policy_version=policy_version,
                source_counts=tuple(
                    TimeAxisSourceCount(source=source, row_count=tally.sources[source])
                    for source in TimeAxisSource
                    if source in tally.sources
                ),
            ),
        )
        for start, tally in sorted(totals.items())
    )


def build_weekly_maturity(
    observations: Sequence[SettlementObservation],
    *,
    window_days: int,
    policy: TimeAxisPolicy,
) -> MaturityTable:
    """관측을 KST 주로 묶어 성숙도 리포트를 만든다. `window_days`는 `InferencePolicy.
    maturity_window_days`(D-5D-8, legacy `MATURITY_WINDOW_LENGTH = 7일`)에서 오고,
    `policy`는 그 행들을 해석한 정책이다 — 행의 provenance 가 그 정책과 다르면
    `ValueError`(한 표의 모든 행은 같은 정책으로 해석됐어야 한다, 구조적 불변식). 그
    불변식은 **측정 불가 행에도** 걸린다(cr M-1)."""
    window_length = timedelta(days=window_days)
    known: list[tuple[KnownTimeAxis, bool]] = []
    unmeasurable: dict[TimeAxisUnmeasurableReason, int] = {}
    for observation in observations:
        axis = observation.time_axis
        # 정책 version 검사는 **두 갈래 모두**에 걸린다(cr M-1) — 측정 불가 행도 「어느
        # 정책으로 해석해서」 그렇게 판정됐는지가 그 판정의 일부다. 한 갈래만 보면, 대체를
        # 승인한 정책이 공시된 표가 그 정책으로는 분모에 들어갔어야 할 행을 측정 불가로
        # 적을 수 있다.
        if axis.policy_version != policy.version:
            raise ValueError(
                "성숙도 표의 모든 행은 같은 정책 version 으로 해석됐어야 한다 — "
                f"{axis.policy_version!r} != {policy.version!r}"
            )
        if isinstance(axis, KnownTimeAxis):
            known.append((axis, observation.settled))
            continue
        unmeasurable[axis.reason] = unmeasurable.get(axis.reason, 0) + 1
    substituted = [item for item in known if item[0].source != TimeAxisSource.OPENED_AT]
    return MaturityTable(
        windows=_weekly_windows(
            known, window_length=window_length, policy_version=policy.version
        ),
        windows_excluding_substitute=_weekly_windows(
            [item for item in known if item[0].source == TimeAxisSource.OPENED_AT],
            window_length=window_length,
            policy_version=policy.version,
        ),
        unmeasurable=tuple(
            TimeAxisUnmeasurableCount(reason=reason, row_count=unmeasurable[reason])
            for reason in TimeAxisUnmeasurableReason
            if reason in unmeasurable
        ),
        population_row_count=len(observations),
        substitute_row_count=len(substituted),
        policy_version=policy.version,
    )
