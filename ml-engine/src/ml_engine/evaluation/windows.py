"""Reuse: bid-vector/app/services/ml_training/award_rate_windows.py@ed4b06c

`ml_engine.evaluation.windows` — 평가 창 정책(scope ⑤): 어떤 표본 위에서 재는가. legacy
`plan_evaluation_windows`(`:241-278`)의 embargo + 표본 하한 + 최근 N 구조를 이었다.

**성숙도는 입력이다**(D-5C2-2) — `WeekMaturity(start, end, opened_count, settled_count)`를
호출자가 준다(5E/M6 이 5D K7 `build_weekly_maturity`로 만든다). 이 모듈은 `settled/opened`
비율을 계산하지만(K7 `Observed.ratio`와 3줄 중복, `OPEN-5C-MATURITY-SOURCE` 종결 — 알려진
제한) 성숙도 **값 자체의 옳음은 방어하지 않는다**(호출자 신뢰, report 가 그대로 공시).

evaluation 층은 `ml_engine.training`을 모른다(layers) — `rows`는 `opened_at`·`stratum`
속성만 구조적으로 요구한다(`StratifiedRow` Protocol, `TrainerLike`/`BoosterLike`와 같은
갈래). **비율 분할(`train_fraction`) API 는 만들지 않는다**(F4 — 다섯 origin 이 같은 5일로
붕괴한 legacy 실측).
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from datetime import datetime
from enum import StrEnum
from typing import Protocol, runtime_checkable

from ml_engine.evaluation.policy import EvaluationPolicy


@runtime_checkable
class StratifiedRow(Protocol):
    """창 정책이 필요로 하는 최소 구조 — `ml_engine.training.corpus.TrainingRow`가
    구조적으로 만족한다(덕 타이핑, `ml_engine.training` import 없이)."""

    @property
    def opened_at(self) -> datetime: ...

    @property
    def stratum(self) -> str: ...


@dataclass(frozen=True)
class WeekMaturity:
    """평가 창 하나의 성숙도 입력 — legacy `MaturityWindow`처럼 반개구간 `[start, end)`.
    `opened_count == 0`은 `NoObservation`이 아니라 여기서는 `IMMATURE`로 접힌다(측정
    불가 = 통과 아님, D-5C2-2)."""

    start: datetime
    end: datetime
    opened_count: int
    settled_count: int

    def __post_init__(self) -> None:
        if self.end <= self.start:
            raise ValueError(f"end 는 start 보다 뒤여야 합니다: {self.start} ~ {self.end}")
        if self.opened_count < 0:
            raise ValueError(f"opened_count 는 음수일 수 없습니다: {self.opened_count}")
        if self.settled_count < 0:
            raise ValueError(f"settled_count 는 음수일 수 없습니다: {self.settled_count}")

    def contains(self, value: datetime) -> bool:
        return self.start <= value < self.end

    @property
    def maturity_ratio(self) -> float | None:
        """`opened_count == 0`이면 비율이 성립하지 않는다(`None`) — K7
        `Observed.ratio`와 같은 식이지만 여기서는 값을 나르지 않고 접는다(알려진 제한,
        `OPEN-5C-MATURITY-SOURCE`)."""
        if self.opened_count == 0:
            return None
        return self.settled_count / self.opened_count


class WindowExclusionReason(StrEnum):
    """제외 사유 어휘(legacy 4 + `TRAINING_REJECTED` 1). 새 사유는 코드 분기가 아니라
    이 enum 한 줄 — 창 제외는 침묵이 아니라 기록이다."""

    IMMATURE = "IMMATURE"
    INSUFFICIENT_EVALUATION_ROWS = "INSUFFICIENT_EVALUATION_ROWS"
    NO_TRAINING_ROWS = "NO_TRAINING_ROWS"
    BEYOND_MAX_ORIGINS = "BEYOND_MAX_ORIGINS"
    TRAINING_REJECTED = "TRAINING_REJECTED"
    """설계 검토 (1) — `training/holdout.py`가 이 창에서 학습이 실패했거나(`TrainingRejected`)
    평가 행렬을 하나도 만들지 못했을 때 흡수해 붙이는 사유(training 층이 붙인다,
    evaluation 자신은 이 값을 산출하지 않는다)."""


@dataclass(frozen=True)
class WindowExclusion:
    """제외된 창 하나와 그 사유. 제외는 리포트에 남는 사실이지 침묵이 아니다."""

    window: WeekMaturity
    reason: WindowExclusionReason
    evaluation_row_count: int


@dataclass(frozen=True)
class WindowPlan:
    """평가에 쓰는 창과 뺀 창 한 벌(시작 시각 오름차순)."""

    selected: tuple[WeekMaturity, ...]
    excluded: tuple[WindowExclusion, ...]


@dataclass(frozen=True)
class InvalidMaturityInput:
    """`maturities`가 정렬 불가능하게 겹칠 때(우회 후보 (13)) — 성숙도 값 자체의 옳음은
    방어 밖이지만, 구간 정의(비겹침)는 이 모듈의 구조적 불변식이다."""

    detail: str


@dataclass(frozen=True)
class HoldoutOverlap:
    """두 창의 홀드아웃이 공유하는 행 수. 설계상 0이어야 하고, 0이 아니면 드러나야 한다."""

    first_start: datetime
    second_start: datetime
    row_count: int


def indices_in_window(
    rows: Sequence[StratifiedRow], window: WeekMaturity, *, stratum: str
) -> list[int]:
    """창 `[start, end)` 안의 평가 층 행 **위치**. 값이 아니라 위치를 내는 이유는 겹침
    회계 때문이다 — 서로 다른 두 행이 우연히 같은 값을 가질 수 있다."""
    return [
        index
        for index, row in enumerate(rows)
        if row.stratum == stratum and window.contains(row.opened_at)
    ]


def _train_row_count(rows: Sequence[StratifiedRow], window: WeekMaturity, *, stratum: str) -> int:
    return sum(1 for row in rows if row.stratum == stratum and row.opened_at < window.start)


def _overlapping(ordered: Sequence[WeekMaturity]) -> bool:
    return any(b.start < a.end for a, b in zip(ordered, ordered[1:]))


@dataclass(frozen=True)
class _WindowFacts:
    window: WeekMaturity
    evaluation_row_count: int
    train_row_count: int


def _exclusion_reason(facts: _WindowFacts, policy: EvaluationPolicy) -> WindowExclusionReason | None:
    """규칙 표 순서대로 — 먼저 걸리는 사유가 기록된다."""
    ratio = facts.window.maturity_ratio
    if ratio is None or ratio < policy.maturity_threshold:
        return WindowExclusionReason.IMMATURE
    if facts.evaluation_row_count < policy.min_evaluation_rows:
        return WindowExclusionReason.INSUFFICIENT_EVALUATION_ROWS
    if facts.train_row_count <= 0:
        return WindowExclusionReason.NO_TRAINING_ROWS
    return None


def plan_evaluation_windows(
    rows: Sequence[StratifiedRow],
    maturities: Sequence[WeekMaturity],
    policy: EvaluationPolicy,
) -> WindowPlan | InvalidMaturityInput:
    """성숙도 구간 표에서 평가 창을 고른다(embargo + 표본 하한 + 최근 N). `maturities`가
    겹치면 `InvalidMaturityInput`(우회 후보 (13) — 성숙도 값 자체가 아니라 구간 정의의
    구조적 불변식)."""
    ordered = sorted(maturities, key=lambda item: item.start)
    if _overlapping(ordered):
        return InvalidMaturityInput("겹치는 성숙도 구간이 있습니다.")

    survivors: list[_WindowFacts] = []
    excluded: list[WindowExclusion] = []
    for window in ordered:
        facts = _WindowFacts(
            window=window,
            evaluation_row_count=len(
                indices_in_window(rows, window, stratum=policy.gate_stratum)
            ),
            train_row_count=_train_row_count(rows, window, stratum=policy.gate_stratum),
        )
        reason = _exclusion_reason(facts, policy)
        if reason is None:
            survivors.append(facts)
        else:
            excluded.append(
                WindowExclusion(
                    window=window, reason=reason, evaluation_row_count=facts.evaluation_row_count
                )
            )

    keep = survivors[-policy.max_origins :] if policy.max_origins > 0 else []
    dropped = survivors[: len(survivors) - len(keep)]
    excluded.extend(
        WindowExclusion(
            window=facts.window,
            reason=WindowExclusionReason.BEYOND_MAX_ORIGINS,
            evaluation_row_count=facts.evaluation_row_count,
        )
        for facts in dropped
    )
    return WindowPlan(
        selected=tuple(facts.window for facts in keep),
        excluded=tuple(sorted(excluded, key=lambda item: item.window.start)),
    )


def holdout_overlaps(
    rows: Sequence[StratifiedRow], windows: Sequence[WeekMaturity], *, stratum: str
) -> list[HoldoutOverlap]:
    """창 쌍마다 홀드아웃이 공유하는 행 수 — 겹침이 **측정된 사실**이 되게 한다(값이
    아니라 인덱스 동일성으로, 설계상 0이어야 함을 주장이 아니라 측정으로)."""
    selections = [set(indices_in_window(rows, window, stratum=stratum)) for window in windows]
    return [
        HoldoutOverlap(
            first_start=windows[first].start,
            second_start=windows[second].start,
            row_count=len(selections[first] & selections[second]),
        )
        for first in range(len(windows))
        for second in range(first + 1, len(windows))
    ]
