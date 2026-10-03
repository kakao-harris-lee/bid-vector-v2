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
from dataclasses import asdict
from datetime import date
from pathlib import Path
from typing import Final

from ml_engine.evaluation.backtest.policy_values import (
    EffectiveDates,
    ExclusionConstants,
    FitThresholds,
    FloorRateBand,
    InstitutionConstants,
    SamplingBudget,
    SensitivityRules,
    StrategyBacktestPolicy,
    StrategyConstants,
    VerdictThresholds,
    WindowRules,
)
from ml_engine.evaluation.policy import (
    SEED_PREFIX,
    PolicyRejected,
    PolicyRejectionReason,
    collect_indexed_list,
    int_tuple,
    require_int,
    require_number,
    require_str,
    seed_key_mismatch,
)
from ml_engine.registry.policy import PolicyError, PolicyScalar
from ml_engine.registry.policy import load_policy as _load_raw_policy

SHIPPED_STRATEGY_BACKTEST_POLICY_VERSION: Final[str] = "strategy-backtest-v1"

__all__ = [
    "SHIPPED_STRATEGY_BACKTEST_POLICY_VERSION",
    "EffectiveDates",
    "ExclusionConstants",
    "FitThresholds",
    "FloorRateBand",
    "InstitutionConstants",
    "SamplingBudget",
    "SensitivityRules",
    "StrategyBacktestPolicy",
    "StrategyConstants",
    "VerdictThresholds",
    "WindowRules",
    "load_strategy_backtest_policy",
    "strategy_backtest_policy_checksum",
]

_MAX_INDEXED_LIST_LENGTH: Final[int] = 32

_NUMBER_KEYS: Final[tuple[str, ...]] = (
    "verdict.min_relative_improvement",
    "verdict.alpha",
    "verdict.ineligibility_noninferiority_margin",
    "verdict.target_power",
    "floor.rate_band_low",
    "floor.rate_band_high",
    "floor.pure_construction_cost_ratio",
    "strategy.s1_offset_bp",
    "strategy.s4_grid_span_bp",
    "fit.alpha",
    "fit.max_bin_ratio_deviation",
    "sensitivity.wide_reserve_half_width",
    "sampling.headroom_ratio",
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
    "exclusion.first_notice_ordinal",
    "sampling.list_call_count",
    "sampling.calls_per_notice_construction",
    "sampling.calls_per_notice_service",
    "sampling.calls_per_notice_goods",
    "sampling.max_total_calls",
)
_TEXT_KEYS: Final[tuple[str, ...]] = (
    "effective.construction",
    "effective.service",
    "effective.goods",
)

_KNOWN_KEYS: Final[frozenset[str]] = (
    frozenset(_NUMBER_KEYS)
    | frozenset(_INT_KEYS)
    | frozenset(_TEXT_KEYS)
    | frozenset(f"{SEED_PREFIX}.{index}" for index in range(_MAX_INDEXED_LIST_LENGTH))
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


def _dates(values: dict[str, PolicyScalar]) -> dict[str, date] | None:
    """시행일은 ISO 날짜 문자열로만 온다 — 형식이 아니면 `None`(fail-closed)."""
    parsed: dict[str, date] = {}
    for key in _TEXT_KEYS:
        raw = require_str(values, key)
        if raw is None:
            return None
        try:
            parsed[key] = date.fromisoformat(raw)
        except ValueError:
            return None
    return parsed


def _build_verdict(
    numbers: dict[str, float], integers: dict[str, int]
) -> VerdictThresholds:
    return VerdictThresholds(
        min_relative_improvement=numbers["verdict.min_relative_improvement"],
        alpha=numbers["verdict.alpha"],
        primary_hypothesis_count=integers["verdict.primary_hypothesis_count"],
        ineligibility_noninferiority_margin=numbers[
            "verdict.ineligibility_noninferiority_margin"
        ],
        target_power=numbers["verdict.target_power"],
        min_window_count=integers["verdict.min_window_count"],
        min_window_rows=integers["verdict.min_window_rows"],
    )


def _build_strategies(
    numbers: dict[str, float], integers: dict[str, int]
) -> StrategyConstants:
    return StrategyConstants(
        s1_offset_bp=numbers["strategy.s1_offset_bp"],
        s4_iteration_count=integers["strategy.s4_iteration_count"],
        s4_grid_size=integers["strategy.s4_grid_size"],
        s4_grid_span_bp=numbers["strategy.s4_grid_span_bp"],
        s4_min_competitor_samples=integers["strategy.s4_min_competitor_samples"],
    )


def _build_institution(integers: dict[str, int]) -> InstitutionConstants:
    return InstitutionConstants(
        reserve_price_count=integers["institution.reserve_price_count"],
        draw_count=integers["institution.draw_count"],
    )


def _build_effective(dates: dict[str, date]) -> EffectiveDates:
    return EffectiveDates(
        construction=dates["effective.construction"],
        service=dates["effective.service"],
        goods=dates["effective.goods"],
    )


def _assemble(
    version: str,
    numbers: dict[str, float],
    integers: dict[str, int],
    dates: dict[str, date],
    seeds: tuple[int, ...],
) -> StrategyBacktestPolicy:
    """값 불변식은 각 dataclass 의 `__post_init__` 이 진다 — 여기서는 조립만."""
    return StrategyBacktestPolicy(
        version=version,
        verdict=_build_verdict(numbers, integers),
        windows=WindowRules(
            days=integers["window.days"],
            embargo_days=integers["window.embargo_days"],
        ),
        institution=_build_institution(integers),
        floor=FloorRateBand(
            rate_band_low=numbers["floor.rate_band_low"],
            rate_band_high=numbers["floor.rate_band_high"],
            pure_construction_cost_ratio=numbers["floor.pure_construction_cost_ratio"],
        ),
        exclusion=ExclusionConstants(
            first_notice_ordinal=integers["exclusion.first_notice_ordinal"]
        ),
        effective=_build_effective(dates),
        strategies=_build_strategies(numbers, integers),
        fit=FitThresholds(
            alpha=numbers["fit.alpha"],
            min_sample_count=integers["fit.min_sample_count"],
            max_bin_ratio_deviation=numbers["fit.max_bin_ratio_deviation"],
        ),
        sampling=SamplingBudget(
            list_call_count=integers["sampling.list_call_count"],
            calls_per_notice_construction=integers[
                "sampling.calls_per_notice_construction"
            ],
            calls_per_notice_service=integers["sampling.calls_per_notice_service"],
            calls_per_notice_goods=integers["sampling.calls_per_notice_goods"],
            max_total_calls=integers["sampling.max_total_calls"],
            headroom_ratio=numbers["sampling.headroom_ratio"],
        ),
        sensitivity=SensitivityRules(
            wide_reserve_half_width=numbers["sensitivity.wide_reserve_half_width"]
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
    dates = _dates(raw.values)
    seeds = int_tuple(collect_indexed_list(raw.values, SEED_PREFIX))
    if numbers is None or integers is None or dates is None or seeds is None:
        return PolicyRejected(
            PolicyRejectionReason.INVALID_VALUE, f"malformed values: {raw.values!r}"
        )
    mismatch = seed_key_mismatch(raw.values)
    if mismatch is not None:
        return PolicyRejected(PolicyRejectionReason.INVALID_VALUE, mismatch)
    try:
        return _assemble(raw.version, numbers, integers, dates, seeds)
    except ValueError as exc:
        return PolicyRejected(PolicyRejectionReason.INVALID_VALUE, str(exc))


def strategy_backtest_policy_checksum(policy: StrategyBacktestPolicy) -> str:
    """sha256 hex(소문자 64자) — 5B `canonical_json`·5C-2 `policy_checksum` 과 같은
    규칙(키 정렬·구분자 `(",", ":")`·`allow_nan=False`). 판정 JSON 이 이 값을 싣고,
    실행 전 승인 커밋 SHA 와 대조된다(우회 ② 「사후 튜닝」)."""
    payload = json.dumps(
        asdict(policy),
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
        default=str,
    )
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()
