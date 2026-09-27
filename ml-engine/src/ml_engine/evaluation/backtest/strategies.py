"""`ml_engine.evaluation.backtest.strategies` — 전략 다섯의 정의와 그중 셋의 구현
(D-6G-3). 모든 전략은 공고마다 **투찰금액 하나**를 낸다.

**전략이 보는 것은 `StrategyInput` 뿐이다.** 그 타입에는 대상 공고의 개찰 결과가 없고,
경쟁자 관측은 **그 공고 개찰일 이전** 것만 들어 있다(`build_strategy_input` 이 자른다).
누출 금지(위협 모델 ①)가 문서가 아니라 함수 시그니처로 닫히는 자리다.

여기 있는 것: **S0**(밴드 내 균등 난수) · **S1**(규칙 앵커) · **S4**(제도 분포 + 경쟁자
분포 몬테카를로). 여기 **없는** 것: **S2**(분포 엔진) — evaluation 층은
`ml_engine.inference` 를 import 할 수 없어(import-linter forbidden) `StrategyLike`
Protocol 뒤에 두고 조립 근(`ml_engine.app`)이 주입한다. **S3**(GBM) — V2 에 학습 산출물이
없어 N/A 선언(D-6G-3).

투찰률의 기준은 **기초금액**이다(`투찰금액 ÷ 기초금액`) — 투찰 시점에 예정가격을 알 수
없으므로 예정가격 기준 비율은 전략의 결정 변수가 될 수 없다. 분포 엔진의
`Candidate.bid_rate` 도 같은 기준이다(`scale * center` = `투찰/예정 * 예정/기초`).

투찰금액은 **원 단위로 올림**한다 — 내림하면 하한 경계 공고에서 1원 차이로 실격이 생겨
전략 간 비교에 계통 편향이 들어간다(선행 조사 02 §4.1 권고).
"""

from __future__ import annotations

import hashlib
import math
from collections.abc import Sequence
from dataclasses import dataclass
from datetime import date
from enum import StrEnum
from typing import Protocol

import numpy as np

from ml_engine.evaluation.backtest.exclusions import AdmittedNotice
from ml_engine.evaluation.backtest.floor import floor_price, rate_from_basis_points
from ml_engine.evaluation.backtest.institution import sample_assessment_ratios
from ml_engine.evaluation.backtest.policy import StrategyBacktestPolicy
from ml_engine.evaluation.backtest.snapshot import BusinessCategory, NoticeObservation

_HALF = 2.0


@dataclass(frozen=True)
class CompetitorObservation:
    """지난 공고의 경쟁자 한 건 — 투찰률과 그 공고의 맥락. **전부 대상 공고보다 앞선
    개찰분에서만 온다.**

    공고 식별자·상호는 없다(D-6G-10). 예비가격·추첨 번호가 함께 오는 이유는 S2(분포
    엔진)가 표본마다 그 공고의 추첨 관측을 요구하기 때문이다 — 그 값들도 **지난** 공고의
    것이라 누출이 아니다."""

    opened_on: date
    bid_rate: float
    participant_count: int
    base_amount: float
    category: BusinessCategory
    reserve_prices: tuple[float, ...] | None
    drawn_serial_numbers: tuple[int, ...] | None


@dataclass(frozen=True)
class StrategyInput:
    """전략이 보는 것 전부. **대상 공고의 개찰 결과는 이 타입에 없다.**"""

    notice: NoticeObservation
    floor_rate: float
    a_value_total: float
    reserve_range_begin_rate: float
    reserve_range_end_rate: float
    competitors: tuple[CompetitorObservation, ...]
    seed: int

    @property
    def expected_assessment_ratio(self) -> float:
        """E[R] — 제도상 사정률의 기대값은 예가 범위의 중점이다(15구간 균등 + 무작위
        평균이라 편향이 없다). 비대칭 범위면 중점도 1 이 아니다."""
        return (
            1.0 + (self.reserve_range_begin_rate + self.reserve_range_end_rate) / _HALF
        )

    @property
    def reserve_half_width(self) -> float:
        """예가 범위 반폭 — 상수가 아니라 그 공고의 필드에서 온다(조사 02 §4.4).
        값이 없는 공고는 애초에 승인되지 않는다(제외 `RESERVE_PRICE_RANGE_ABSENT`,
        D-6G-16) — 여기서 `None` 을 다룰 필요가 없다."""
        return (self.reserve_range_end_rate - self.reserve_range_begin_rate) / _HALF

    @property
    def expected_participant_count(self) -> int:
        """경쟁자 수 추정 — 지난 창 공고들의 참가자 수 중앙값. 대상 공고의
        `participant_count` 는 개찰 결과라 **쓰지 않는다**(누출 금지)."""
        if not self.competitors:
            return 0
        counts = sorted(item.participant_count for item in self.competitors)
        return int(counts[len(counts) // 2])


class AbstentionReason(StrEnum):
    """전략이 금액을 내지 못한 사유. 채점에서는 「부적격이고 이기지 못했다」로 세고,
    사유별 계수를 판정 JSON 에 싣는다 — 기권을 조용히 제외하면 표본이 전략마다 달라져
    쌍대 검정이 깨진다(우회 ④)."""

    INSUFFICIENT_COMPETITOR_SAMPLES = "INSUFFICIENT_COMPETITOR_SAMPLES"
    ENGINE_UNMEASURABLE = "ENGINE_UNMEASURABLE"
    NON_FINITE_RESULT = "NON_FINITE_RESULT"
    NOT_APPLICABLE_CATEGORY = "NOT_APPLICABLE_CATEGORY"
    """전략의 정의가 그 업무에 서지 않는다 — S1 의 offset 이 **공사 전용**으로
    측정된 값이라(D-6G-15) 용역·물품에서는 금액을 내지 않는다. 관측값으로 새 상수를
    만들지 않는다."""


@dataclass(frozen=True)
class BidAmount:
    amount: float


@dataclass(frozen=True)
class Abstained:
    reason: AbstentionReason


type StrategyOutcome = BidAmount | Abstained


class StrategyLike(Protocol):
    """전략 하나 — 이름과 「입력 하나에서 금액 하나」. S2 도 이 계약만 만족하면 되고,
    그 구현은 조립 근이 준다(층 경계)."""

    @property
    def name(self) -> str: ...

    def bid(
        self, request: StrategyInput, policy: StrategyBacktestPolicy
    ) -> StrategyOutcome: ...


def _round_up_won(amount: float) -> StrategyOutcome:
    if not math.isfinite(amount):
        return Abstained(AbstentionReason.NON_FINITE_RESULT)
    return BidAmount(float(math.ceil(amount)))


def bid_from_rate(request: StrategyInput, rate: float) -> StrategyOutcome:
    """투찰률(기초금액 기준) → 원 단위 올림 금액. 전략 셋이 공유하는 유일한 환산."""
    return _round_up_won(request.notice.base_amount * rate)


def notice_rng(request: StrategyInput) -> np.random.Generator:
    """`(판정 seed, 공고 키 해시)` 에서 결정적으로 나오는 난수원 — 공고 처리 순서가
    바뀌어도 같은 표본이 나온다(재현성, 위협 모델 ③)."""
    # 구분자 `:` 는 **전략 내부 난수** 전용이다. 표본 뽑기 순서(Kotlin 레인,
    # 스키마 §5)는 `sha256("<seed>|<notice_key_hash>")` 로 구분자가 `|` 다 — 두
    # 용도가 다르고 서로를 재현하지 않으므로 일부러 다른 구분자를 쓴다(같은 문자열을
    # 쓰면 표본 선택과 전략 난수가 상관을 갖는다).
    material = f"{request.seed}:{request.notice.notice_key_hash}".encode()
    digest = hashlib.sha256(material).digest()
    return np.random.default_rng(int.from_bytes(digest, "big"))


@dataclass(frozen=True)
class UniformBandStrategy:
    """**S0** — 밴드 내 균등 난수. 투찰률을 `[r(1-h), r(1+h)]` 에서 균등 추출한다
    (h = 그 공고의 예가 범위 반폭). 이 실험의 **기준선**이고, 「분포 엔진이 난수보다
    나은가」의 「난수」가 바로 이것이다."""

    name: str = "S0"

    def bid(
        self, request: StrategyInput, policy: StrategyBacktestPolicy
    ) -> StrategyOutcome:
        del policy
        half_width = request.reserve_half_width
        low = request.floor_rate * (1.0 - half_width)
        high = request.floor_rate * (1.0 + half_width)
        return bid_from_rate(request, float(notice_rng(request).uniform(low, high)))


@dataclass(frozen=True)
class RuleAnchorStrategy:
    """**S1** — 규칙 앵커(D-6G-15 확정). `하한율 + offset` 이고 `E[R]` 를 곱하지
    않는다 — legacy 의 앵커가 `floor + 선언 offset` 이고 `x E[예정가]` 변형은 legacy 가
    기각했다(`app/ai/construction_scenario.py::resolve_scenario_anchor_rates`).

    **공사에서만 채점한다.** offset 은 legacy 가 공사 정착행에서 잰 백분위수(p50)라
    용역·물품에 그 값을 옮길 근거가 없다 — 그 업무에서는 금액을 내지 않고 기권으로
    공시한다(관측값으로 새 상수를 만들지 않는다). S1 은 보조 가설이다."""

    name: str = "S1"

    def bid(
        self, request: StrategyInput, policy: StrategyBacktestPolicy
    ) -> StrategyOutcome:
        if request.notice.category is not BusinessCategory.CONSTRUCTION:
            return Abstained(AbstentionReason.NOT_APPLICABLE_CATEGORY)
        return bid_from_rate(
            request,
            request.floor_rate + rate_from_basis_points(policy.strategies.s1_offset_bp),
        )


def _candidate_rates(
    request: StrategyInput, policy: StrategyBacktestPolicy
) -> np.ndarray:
    """후보 투찰률 격자 — 밴드 하단에서 시작해 정책이 정한 폭만큼. 격자를 결과를 보고
    고르지 않게 폭·점 수를 정책으로 고정한다."""
    low = request.floor_rate * (1.0 - request.reserve_half_width)
    span = rate_from_basis_points(policy.strategies.s4_grid_span_bp)
    return np.linspace(low, low + span, policy.strategies.s4_grid_size)


def _win_probabilities(
    request: StrategyInput,
    policy: StrategyBacktestPolicy,
    rates: np.ndarray,
) -> np.ndarray:
    """후보 투찰률마다 승률 추정 — 제도 분포에서 사정률을 뽑고, 경쟁자 투찰률을 지난
    창 표본에서 복원추출해 「적격이고 최저」일 빈도를 센다."""
    rng = notice_rng(request)
    iterations = policy.strategies.s4_iteration_count
    ratios = sample_assessment_ratios(
        rng,
        count=iterations,
        begin_rate=request.reserve_range_begin_rate,
        end_rate=request.reserve_range_end_rate,
        reserve_price_count=policy.institution.reserve_price_count,
        draw_count=policy.institution.draw_count,
    )
    base = request.notice.base_amount
    floors = np.array(
        [
            floor_price(
                planned_price=ratio * base,
                floor_rate=request.floor_rate,
                a_value_total=request.a_value_total,
            )
            for ratio in ratios
        ]
    )
    pool = np.array([item.bid_rate for item in request.competitors])
    rivals = max(request.expected_participant_count - 1, 0)
    draws = rng.choice(pool, size=(iterations, rivals)) * base
    eligible_rivals = np.where(draws >= floors[:, None], draws, np.inf)
    best_rival = eligible_rivals.min(axis=1) if rivals else np.full(iterations, np.inf)
    amounts = rates[None, :] * base
    wins = (amounts >= floors[:, None]) & (amounts < best_rival[:, None])
    return np.asarray(wins.mean(axis=0), dtype=np.float64)


@dataclass(frozen=True)
class InstitutionalMonteCarloStrategy:
    """**S4** — 제도 분포 + 경쟁자 분포 몬테카를로. 2026-09-27 조사가 「이득이 나는
    유일한 자리」로 지목한 「경쟁자 밀집 회피」를 직접 최적화한다. 동률이면 **낮은
    투찰률**을 고른다(결정적 tie-break)."""

    name: str = "S4"

    def bid(
        self, request: StrategyInput, policy: StrategyBacktestPolicy
    ) -> StrategyOutcome:
        if len(request.competitors) < policy.strategies.s4_min_competitor_samples:
            return Abstained(AbstentionReason.INSUFFICIENT_COMPETITOR_SAMPLES)
        rates = _candidate_rates(request, policy)
        probabilities = _win_probabilities(request, policy, rates)
        return bid_from_rate(request, float(rates[int(np.argmax(probabilities))]))


def build_competitor_pool(
    history: Sequence[AdmittedNotice], *, before: date
) -> tuple[CompetitorObservation, ...]:
    """대상 공고의 **개찰일 이전** 개찰분에서만 경쟁자 투찰률을 모은다(누출 금지 ①).

    경계는 엄격한 `<` 다 — 같은 날 개찰분의 결과는 투찰 시점에 알 수 없다. 이 한 줄이
    「경쟁 표본은 그 공고 개찰일 이전 데이터만」(D-6G-3)의 유일한 구현이고,
    `test_backtest_strategies.py` 가 대상 공고 자신을 이력에 심어 RED 를 확인한다."""
    return tuple(
        CompetitorObservation(
            opened_on=item.row.outcome.opened_on,
            bid_rate=amount / item.row.notice.base_amount,
            participant_count=item.participant_count,
            base_amount=item.row.notice.base_amount,
            category=item.row.notice.category,
            reserve_prices=item.row.outcome.reserve_prices,
            drawn_serial_numbers=item.row.outcome.drawn_serial_numbers,
        )
        for item in history
        if item.row.outcome.opened_on < before
        for amount in item.bid_amounts
    )


def build_strategy_input(
    target: AdmittedNotice, history: Sequence[AdmittedNotice], *, seed: int
) -> StrategyInput:
    """전략 입력 조립 — 개찰 결과 두 반쪽이 만나는 **유일한 자리**이고, 여기서 쓰는
    개찰 값은 대상 공고의 `opened_on`(절단 기준) 하나뿐이다."""
    return StrategyInput(
        notice=target.row.notice,
        floor_rate=target.floor_rate,
        a_value_total=target.a_value_total,
        reserve_range_begin_rate=target.reserve_range_begin_rate,
        reserve_range_end_rate=target.reserve_range_end_rate,
        competitors=build_competitor_pool(history, before=target.row.outcome.opened_on),
        seed=seed,
    )
