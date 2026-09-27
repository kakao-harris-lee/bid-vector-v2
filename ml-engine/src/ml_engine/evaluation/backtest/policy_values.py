"""`ml_engine.evaluation.backtest.policy_values` — 사전 등록 정책의 **값 타입**과 불변식.
로더(`policy`)에서 분리한 이유는 파일 크기다(설계 래칫 500줄).

여기 있는 것은 값과 그 값이 지켜야 할 것뿐이다 — 파일을 읽는 일은 하지 않는다. 임계를
낱개로 받는 함수는 없고, 유의수준을 **주/보조 중 어느 쪽으로 쓸지 고르는 것도** 이쪽
(`VerdictThresholds.alpha_for`)이다: 판정 코드가 고르면 그 선택이 diff 에 드러나지
않는다(D-6G-32).
"""

from __future__ import annotations

import math
from dataclasses import dataclass
from datetime import date


def _require_finite_unit_open(value: float, name: str) -> None:
    """(0, 1) 열린 구간 — 확률·유의수준·검정력이 사는 자리."""
    if not math.isfinite(value) or not (0 < value < 1):
        raise ValueError(f"{name} 은 (0, 1) 안의 유한값이어야 합니다: {value}")


def _require_finite_positive(value: float, name: str) -> None:
    if not math.isfinite(value) or value <= 0:
        raise ValueError(f"{name} 은 양의 유한값이어야 합니다: {value}")


def _require_finite_non_negative(value: float, name: str) -> None:
    if not math.isfinite(value) or value < 0:
        raise ValueError(f"{name} 은 음이 아닌 유한값이어야 합니다: {value}")


@dataclass(frozen=True)
class VerdictThresholds:
    """판정식 임계 한 벌(A-3 승인값)."""

    min_relative_improvement: float
    alpha: float
    primary_hypothesis_count: int
    ineligibility_noninferiority_margin: float
    target_power: float
    min_window_count: int
    min_window_rows: int

    def __post_init__(self) -> None:
        _require_finite_positive(
            self.min_relative_improvement, "verdict.min_relative_improvement"
        )
        _require_finite_unit_open(self.alpha, "verdict.alpha")
        _require_finite_unit_open(self.target_power, "verdict.target_power")
        _require_finite_non_negative(
            self.ineligibility_noninferiority_margin,
            "verdict.ineligibility_noninferiority_margin",
        )
        if self.primary_hypothesis_count < 1:
            raise ValueError(
                "verdict.primary_hypothesis_count 는 1 이상이어야 합니다: "
                f"{self.primary_hypothesis_count}"
            )
        if self.min_window_count < 1:
            raise ValueError(
                f"verdict.min_window_count 는 1 이상이어야 합니다: "
                f"{self.min_window_count}"
            )
        if self.min_window_rows < 2:
            raise ValueError(
                "verdict.min_window_rows 는 2 이상이어야 합니다(쌍대 검정 하한): "
                f"{self.min_window_rows}"
            )

    @property
    def primary_alpha(self) -> float:
        """Bonferroni 보정 뒤 **주 가설** 유의수준(우회 ⑥)."""
        return self.alpha / self.primary_hypothesis_count

    def alpha_for(self, *, primary: bool) -> float:
        """가설 종류에 맞는 유의수준 — **보정을 고르는 것도 정책 객체의 몫**이다
        (D-6G-32, code-review r1 M-2). D-6G-6 은 주 가설(S2 세 후보)만 Bonferroni
        /3 이고 보조(S1·S4)는 보정 없는 유의수준으로 따로 공시한다. 판정 코드가 어느 쪽을 쓸지 직접
        고르면 그 선택이 diff 에 드러나지 않는다.

        **보조가 주보다 느슨하다** — 그 완화가 숨지 않게 판정 JSON 이 전략마다
        `alpha_used` 를 싣는다."""
        return self.primary_alpha if primary else self.alpha


@dataclass(frozen=True)
class WindowRules:
    """창 규칙(D-6G-5·14) — 비중첩 주 단위 + embargo."""

    days: int
    embargo_days: int

    def __post_init__(self) -> None:
        if self.days < 1:
            raise ValueError(f"window.days 는 1 이상이어야 합니다: {self.days}")
        if self.embargo_days < 0:
            raise ValueError(
                f"window.embargo_days 는 음수일 수 없습니다: {self.embargo_days}"
            )


@dataclass(frozen=True)
class InstitutionConstants:
    """복수예비가격 제도 상수 — 15구간 균등 + 무작위 4개 평균."""

    reserve_price_count: int
    draw_count: int

    def __post_init__(self) -> None:
        if self.draw_count < 1:
            raise ValueError(
                f"institution.draw_count 는 1 이상이어야 합니다: {self.draw_count}"
            )
        if self.reserve_price_count <= self.draw_count:
            raise ValueError(
                "institution.reserve_price_count 는 draw_count 보다 커야 합니다: "
                f"{self.reserve_price_count} <= {self.draw_count}"
            )


@dataclass(frozen=True)
class FloorRateBand:
    """낙찰하한율 개연 밴드(D-6G-13 ②)와 공사 순공사원가 배제 비율(P-3 §4.3)."""

    rate_band_low: float
    rate_band_high: float
    pure_construction_cost_ratio: float

    def __post_init__(self) -> None:
        _require_finite_unit_open(self.rate_band_low, "floor.rate_band_low")
        _require_finite_unit_open(self.rate_band_high, "floor.rate_band_high")
        _require_finite_unit_open(
            self.pure_construction_cost_ratio, "floor.pure_construction_cost_ratio"
        )
        if self.rate_band_low >= self.rate_band_high:
            raise ValueError(
                "floor.rate_band_low 는 rate_band_high 보다 작아야 합니다: "
                f"{self.rate_band_low} >= {self.rate_band_high}"
            )

    def contains(self, rate: float) -> bool:
        return self.rate_band_low <= rate <= self.rate_band_high


@dataclass(frozen=True)
class EffectiveDates:
    """2026 낙찰하한율 개정 시행일(업무별). 포함 여부는 **공고일** 기준이다(D-6G-14)."""

    construction: date
    service: date
    goods: date


@dataclass(frozen=True)
class StrategyConstants:
    """전략 상수 — S1 offset 과 S4 몬테카를로 격자(사전 등록)."""

    s1_offset_bp: float
    s4_iteration_count: int
    s4_grid_size: int
    s4_grid_span_bp: float
    s4_min_competitor_samples: int

    def __post_init__(self) -> None:
        if not math.isfinite(self.s1_offset_bp):
            raise ValueError(
                f"strategy.s1_offset_bp 는 유한값이어야 합니다: {self.s1_offset_bp}"
            )
        _require_finite_non_negative(self.s4_grid_span_bp, "strategy.s4_grid_span_bp")
        if self.s4_iteration_count < 1:
            raise ValueError(
                "strategy.s4_iteration_count 는 1 이상이어야 합니다: "
                f"{self.s4_iteration_count}"
            )
        if self.s4_grid_size < 2:
            raise ValueError(
                f"strategy.s4_grid_size 는 2 이상이어야 합니다: {self.s4_grid_size}"
            )
        if self.s4_min_competitor_samples < 1:
            raise ValueError(
                "strategy.s4_min_competitor_samples 는 1 이상이어야 합니다: "
                f"{self.s4_min_competitor_samples}"
            )


@dataclass(frozen=True)
class SamplingBudget:
    """표본 크기 결정식(D-6G-20·32) — 호출 예산이 표본의 위를, 판정 요구가 아래를 정한다.

    공고당 호출 수는 **업무별**이다(verifier r1 M-3 — 공사는 A값 오퍼레이션이 더
    붙는다). 최소 필요 표본은 **파생값**이고 정책에 숫자로 적지 않는다: 창 규칙이
    바뀌면 같이 움직여야 하는데, 따로 적으면 조용히 어긋난다."""

    list_call_count: int
    calls_per_notice_construction: int
    calls_per_notice_service: int
    calls_per_notice_goods: int
    max_total_calls: int
    headroom_ratio: float

    def __post_init__(self) -> None:
        for name in (
            "list_call_count",
            "calls_per_notice_construction",
            "calls_per_notice_service",
            "calls_per_notice_goods",
            "max_total_calls",
        ):
            if getattr(self, name) < 1:
                raise ValueError(f"sampling.{name} 는 1 이상이어야 합니다")
        _require_finite_non_negative(self.headroom_ratio, "sampling.headroom_ratio")

    def calls_per_notice_for(self, category: str) -> int:
        """업무 이름 -> 공고당 상세 호출 수. **닫힌 셋**이고 미지 업무는 거부한다 —
        조용히 기본값으로 접으면 공사가 셋으로 계상된다."""
        table = {
            "CONSTRUCTION": self.calls_per_notice_construction,
            "SERVICE": self.calls_per_notice_service,
            "GOODS": self.calls_per_notice_goods,
        }
        if category not in table:
            raise KeyError(f"호출 수를 모르는 업무입니다: {category!r}")
        return table[category]

    def minimum_required_sample(
        self, *, rows_per_window: int, window_count: int, category_count: int
    ) -> int:
        """창당 하한 x 창 최소 수 x 업무 수 x (1 + 여유), 올림."""
        core = rows_per_window * window_count * category_count
        return math.ceil(core * (1.0 + self.headroom_ratio))


@dataclass(frozen=True)
class SensitivityRules:
    """지자체 민감도(D-6G-21) — 주 판정 옆에 함께 내는 보조 판 둘의 규칙."""

    wide_reserve_half_width: float

    def __post_init__(self) -> None:
        _require_finite_positive(
            self.wide_reserve_half_width, "sensitivity.wide_reserve_half_width"
        )


@dataclass(frozen=True)
class FitThresholds:
    """P-4 제도 분포 적합도 임계 — 맞지 않으면 판정 대신 멈춤."""

    alpha: float
    min_sample_count: int
    max_bin_ratio_deviation: float

    def __post_init__(self) -> None:
        _require_finite_unit_open(self.alpha, "fit.alpha")
        _require_finite_positive(
            self.max_bin_ratio_deviation, "fit.max_bin_ratio_deviation"
        )
        if self.min_sample_count < 1:
            raise ValueError(
                f"fit.min_sample_count 는 1 이상이어야 합니다: {self.min_sample_count}"
            )


@dataclass(frozen=True)
class StrategyBacktestPolicy:
    """사전 등록 정책 한 벌. 기본값 없음 — 「안 넘겼다」와 「미공시」를 구별한다."""

    version: str
    verdict: VerdictThresholds
    windows: WindowRules
    institution: InstitutionConstants
    floor: FloorRateBand
    effective: EffectiveDates
    strategies: StrategyConstants
    fit: FitThresholds
    sampling: SamplingBudget
    sensitivity: SensitivityRules
    stability_seeds: tuple[int, ...]

    def __post_init__(self) -> None:
        if not self.stability_seeds:
            raise ValueError("stability_seeds 는 비어 있을 수 없습니다.")
        if len(set(self.stability_seeds)) != len(self.stability_seeds):
            raise ValueError(
                f"stability_seeds 에 중복이 있습니다: {self.stability_seeds!r}"
            )
