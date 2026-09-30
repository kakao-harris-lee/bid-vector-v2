"""`ml_engine.evaluation.backtest.metrics` — 지표 셋(D-6G-4, D-6G-10 정정 반영).

공고 하나·전략 하나마다 셋을 낸다:
① **적격** — 투찰금액이 실제 실격선 전부를 넘는가(하한가, 공사는 순공사원가선까지).
② **하한 대비 bp** — `(투찰금액 ÷ 실제 낙찰하한가 - 1) * 10^4`. **적격일 때만** 낸다.
③ **would-have-won** — 적격 ∧ 투찰금액 < **실제 적격 투찰자 중 최저 투찰금액**.
   D-6G-10 이 「1위 대조」를 「전체 순위 대조」로 고쳤다(P-1 이 전 참가업체 행을 준다).

**기권은 「부적격이고 이기지 못했다」로 센다.** 기권 공고를 표본에서 빼면 전략마다
표본이 달라져 쌍대 검정이 깨진다(우회 ④) — 대신 사유별로 계수해 공시한다.

**반사실은 재지 못한다.** 우리가 실제로 들어갔다면 다른 입찰자의 행동이 달랐을 수
있다. ③ 은 「그때 그 가격으로 들어갔다면 그 판에서 최저 적격이었는가」일 뿐이고, 이
한계는 판정문에 적는다(위협 모델 「지키지 않는 것」).
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass

import numpy as np

from ml_engine.evaluation.backtest.exclusions import AdmittedNotice
from ml_engine.evaluation.backtest.floor import basis_points_above, is_eligible
from ml_engine.evaluation.backtest.strategies import (
    Abstained,
    AbstentionReason,
    BidAmount,
    StrategyOutcome,
)


@dataclass(frozen=True)
class NoticeScore:
    """공고 하나에 대한 전략 하나의 채점."""

    notice_key_hash: str
    eligible: bool
    basis_points_above_floor: float | None
    would_have_won: bool
    abstention: AbstentionReason | None


def score_notice(admitted: AdmittedNotice, outcome: StrategyOutcome) -> NoticeScore:
    """채점은 전략을 부른 **뒤에** 개찰 결과를 읽는다 — 전략에 결과를 넘기는 경로가
    아니라, 결과를 전략의 산출물과 맞대는 경로다."""
    key = admitted.row.notice.notice_key_hash
    if isinstance(outcome, Abstained):
        return NoticeScore(key, False, None, False, outcome.reason)
    eligible = is_eligible(outcome.amount, admitted.eligibility_floors)
    if not eligible:
        return NoticeScore(key, False, None, False, None)
    return NoticeScore(
        notice_key_hash=key,
        eligible=True,
        basis_points_above_floor=basis_points_above(
            outcome.amount, admitted.actual_floor_price
        ),
        would_have_won=outcome.amount < admitted.lowest_eligible_amount,
        abstention=None,
    )


@dataclass(frozen=True)
class StrategyScores:
    """전략 하나의 창 하나에 대한 채점 묶음. 공고 순서는 입력 순서를 그대로 지킨다 —
    쌍대 검정이 그 순서로 짝을 맞춘다."""

    name: str
    scores: tuple[NoticeScore, ...]

    @property
    def wins(self) -> tuple[bool, ...]:
        return tuple(score.would_have_won for score in self.scores)

    @property
    def win_rate(self) -> float:
        return _rate(score.would_have_won for score in self.scores)

    @property
    def ineligible_rate(self) -> float:
        return _rate(not score.eligible for score in self.scores)

    @property
    def eligible_count(self) -> int:
        return sum(1 for score in self.scores if score.eligible)

    @property
    def abstention_count(self) -> int:
        return sum(1 for score in self.scores if score.abstention is not None)

    @property
    def basis_point_quartiles(self) -> tuple[float, float, float] | None:
        """적격 공고의 하한 대비 bp 사분위(하·중앙·상). 적격이 하나도 없으면 `None`
        — 빈 분포에 0 을 지어내지 않는다."""
        values = [
            score.basis_points_above_floor
            for score in self.scores
            if score.basis_points_above_floor is not None
        ]
        if not values:
            return None
        quartiles = np.quantile(np.asarray(values), (0.25, 0.5, 0.75))
        return (float(quartiles[0]), float(quartiles[1]), float(quartiles[2]))

    def abstention_counts(self) -> tuple[tuple[AbstentionReason, int], ...]:
        tally = dict.fromkeys(AbstentionReason, 0)
        for score in self.scores:
            if score.abstention is not None:
                tally[score.abstention] += 1
        return tuple((reason, tally[reason]) for reason in AbstentionReason)


def _rate(flags: Iterable[bool]) -> float:
    values = list(flags)
    if not values:
        return 0.0
    return sum(1 for value in values if value) / len(values)


def score_strategy(
    name: str,
    admitted: Sequence[AdmittedNotice],
    outcomes: Sequence[StrategyOutcome],
) -> StrategyScores:
    """공고와 산출물을 **같은 순서로** 짝짓는다 — 길이가 다르면 쌍이 성립하지 않아
    거부한다(조용한 절단 없음)."""
    if len(admitted) != len(outcomes):
        raise ValueError(
            f"채점은 공고와 산출물의 개수가 같아야 합니다: "
            f"{len(admitted)} != {len(outcomes)}"
        )
    return StrategyScores(
        name=name,
        scores=tuple(
            score_notice(notice, outcome)
            for notice, outcome in zip(admitted, outcomes, strict=True)
        ),
    )


def scored_notice_keys(scores: StrategyScores) -> tuple[str, ...]:
    """전략이 실제로 채점한 공고 집합 — `run` 이 전략 간 동일성을 단언할 때 쓴다."""
    return tuple(score.notice_key_hash for score in scores.scores)


__all__ = [
    "BidAmount",
    "NoticeScore",
    "StrategyScores",
    "score_notice",
    "score_strategy",
    "scored_notice_keys",
]
