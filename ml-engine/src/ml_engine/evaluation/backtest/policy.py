"""`ml_engine.evaluation.backtest.policy` — 6G 사전 등록 정책 로더(D-6G-6). 값은 전부
`ml-engine/policy/strategy-backtest-v1.yaml` 에만 있고 코드 리터럴로 새지 않는다(ML-07
acceptance ③). 로더는 `path` 하나만 받는다 — CLI·환경변수로 임계를 완화하는 통로가
없다(같은 acceptance ④).

필드 판독기(`require_number`·`collect_indexed_list` 등)는 5C-2 `evaluation.policy` 의
것을 그대로 쓴다(M5 가 이미 만든 평탄 인덱스 키 규약 — 5A `registry.policy.load_policy`
의 `known_keys` 가 문자열 집합 전수라 YAML 시퀀스를 표현하지 못한다). 두 번째 판독기를
만들지 않는다.

**Bonferroni 보정은 정책 객체가 낸다**(`VerdictThresholds.primary_alpha`) — 판정 코드가
유의수준을 직접 나누면 보정을 잊은 경로가 생긴다(우회 ⑥).
"""

from __future__ import annotations

import hashlib
import json
import math
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Final

from ml_engine.evaluation.policy import (
    PolicyRejected,
    PolicyRejectionReason,
    collect_indexed_list,
    int_tuple,
    require_int,
    require_number,
)
from ml_engine.registry.policy import PolicyError, PolicyScalar
from ml_engine.registry.policy import load_policy as _load_raw_policy

SHIPPED_STRATEGY_BACKTEST_POLICY_VERSION: Final[str] = "strategy-backtest-v1"

_MAX_INDEXED_LIST_LENGTH: Final[int] = 32

_NUMBER_KEYS: Final[tuple[str, ...]] = (
    "verdict.min_relative_improvement",
    "verdict.alpha",
    "verdict.ineligibility_noninferiority_margin",
    "verdict.target_power",
    "floor.rate_band_low",
    "floor.rate_band_high",
    "strategy.s1_offset_bp",
    "strategy.s4_grid_span_bp",
    "fit.alpha",
    "fit.max_bin_ratio_deviation",
)
_INT_KEYS: Final[tuple[str, ...]] = (
    "verdict.primary_hypothesis_count",
    "verdict.min_window_count",
    "verdict.min_window_rows",
    "window.days",
    "window.embargo_days",
    "institution.reserve_price_count",
    "institution.draw_count",
    "strategy.s4_iteration_count",
    "strategy.s4_grid_size",
    "strategy.s4_min_competitor_samples",
    "fit.min_sample_count",
)
_SEED_PREFIX: Final[str] = "stability_seeds"
_KNOWN_KEYS: Final[frozenset[str]] = (
    frozenset(_NUMBER_KEYS)
    | frozenset(_INT_KEYS)
    | frozenset(f"{_SEED_PREFIX}.{index}" for index in range(_MAX_INDEXED_LIST_LENGTH))
)


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
        """Bonferroni 보정 뒤 주 가설 유의수준 — 판정이 쓰는 유일한 유의수준(우회 ⑥)."""
        return self.alpha / self.primary_hypothesis_count


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
    """낙찰하한율 개연 밴드(D-6G-13 ②) — 밖의 값은 제외 사유가 된다."""

    rate_band_low: float
    rate_band_high: float

    def __post_init__(self) -> None:
        _require_finite_unit_open(self.rate_band_low, "floor.rate_band_low")
        _require_finite_unit_open(self.rate_band_high, "floor.rate_band_high")
        if self.rate_band_low >= self.rate_band_high:
            raise ValueError(
                "floor.rate_band_low 는 rate_band_high 보다 작아야 합니다: "
                f"{self.rate_band_low} >= {self.rate_band_high}"
            )

    def contains(self, rate: float) -> bool:
        return self.rate_band_low <= rate <= self.rate_band_high


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
    strategies: StrategyConstants
    fit: FitThresholds
    stability_seeds: tuple[int, ...]

    def __post_init__(self) -> None:
        if not self.stability_seeds:
            raise ValueError("stability_seeds 는 비어 있을 수 없습니다.")
        if len(set(self.stability_seeds)) != len(self.stability_seeds):
            raise ValueError(
                f"stability_seeds 에 중복이 있습니다: {self.stability_seeds!r}"
            )


def _numbers(values: dict[str, PolicyScalar]) -> dict[str, float] | None:
    parsed = {key: require_number(values, key) for key in _NUMBER_KEYS}
    if any(value is None for value in parsed.values()):
        return None
    return {key: value for key, value in parsed.items() if value is not None}


def _integers(values: dict[str, PolicyScalar]) -> dict[str, int] | None:
    parsed = {key: require_int(values, key) for key in _INT_KEYS}
    if any(value is None for value in parsed.values()):
        return None
    return {key: value for key, value in parsed.items() if value is not None}


def _assemble(
    version: str,
    numbers: dict[str, float],
    integers: dict[str, int],
    seeds: tuple[int, ...],
) -> StrategyBacktestPolicy:
    """값 불변식은 각 dataclass 의 `__post_init__` 이 진다 — 여기서는 조립만."""
    return StrategyBacktestPolicy(
        version=version,
        verdict=VerdictThresholds(
            min_relative_improvement=numbers["verdict.min_relative_improvement"],
            alpha=numbers["verdict.alpha"],
            primary_hypothesis_count=integers["verdict.primary_hypothesis_count"],
            ineligibility_noninferiority_margin=numbers[
                "verdict.ineligibility_noninferiority_margin"
            ],
            target_power=numbers["verdict.target_power"],
            min_window_count=integers["verdict.min_window_count"],
            min_window_rows=integers["verdict.min_window_rows"],
        ),
        windows=WindowRules(
            days=integers["window.days"],
            embargo_days=integers["window.embargo_days"],
        ),
        institution=InstitutionConstants(
            reserve_price_count=integers["institution.reserve_price_count"],
            draw_count=integers["institution.draw_count"],
        ),
        floor=FloorRateBand(
            rate_band_low=numbers["floor.rate_band_low"],
            rate_band_high=numbers["floor.rate_band_high"],
        ),
        strategies=StrategyConstants(
            s1_offset_bp=numbers["strategy.s1_offset_bp"],
            s4_iteration_count=integers["strategy.s4_iteration_count"],
            s4_grid_size=integers["strategy.s4_grid_size"],
            s4_grid_span_bp=numbers["strategy.s4_grid_span_bp"],
            s4_min_competitor_samples=integers["strategy.s4_min_competitor_samples"],
        ),
        fit=FitThresholds(
            alpha=numbers["fit.alpha"],
            min_sample_count=integers["fit.min_sample_count"],
            max_bin_ratio_deviation=numbers["fit.max_bin_ratio_deviation"],
        ),
        stability_seeds=seeds,
    )


def load_strategy_backtest_policy(
    path: Path,
) -> StrategyBacktestPolicy | PolicyRejected:
    """`path` 의 YAML 을 읽어 `StrategyBacktestPolicy` 로 검증한다. 미지 키·값 불변식
    위반은 전부 `PolicyRejected`(예외로 새지 않는다, v2-지침서.md §5)."""
    # `yaml` 을 import 하지 않는다 — 뿌리 로더(`registry.policy.load_policy`)가
    # `yaml.YAMLError` 를 `PolicyError` 로 이미 정규화한다(D-5E3-1). 소비 로더가
    # 다시 잡을 필요가 없고, 잡으려 들면 import 계약(「yaml 을 직접 import 하는 곳은
    # registry.policy 하나」)이 막는다 — 그 계약에 예외를 더하지 않는다.
    try:
        raw = _load_raw_policy(path, known_keys=_KNOWN_KEYS)
    except (PolicyError, OSError) as exc:
        return PolicyRejected(PolicyRejectionReason.MALFORMED, str(exc))

    numbers = _numbers(raw.values)
    integers = _integers(raw.values)
    seeds = int_tuple(collect_indexed_list(raw.values, _SEED_PREFIX))
    if numbers is None or integers is None or seeds is None:
        return PolicyRejected(
            PolicyRejectionReason.INVALID_VALUE, f"malformed values: {raw.values!r}"
        )
    try:
        return _assemble(raw.version, numbers, integers, seeds)
    except ValueError as exc:
        return PolicyRejected(PolicyRejectionReason.INVALID_VALUE, str(exc))


def strategy_backtest_policy_checksum(policy: StrategyBacktestPolicy) -> str:
    """sha256 hex(소문자 64자) — 5B `canonical_json`·5C-2 `policy_checksum` 과 같은
    규칙(키 정렬·구분자 `(",", ":")`·`allow_nan=False`). 판정 JSON 이 이 값을 싣고,
    실행 전 승인 커밋 SHA 와 대조된다(우회 ② 「사후 튜닝」)."""
    payload = json.dumps(
        asdict(policy), sort_keys=True, separators=(",", ":"), allow_nan=False
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()
