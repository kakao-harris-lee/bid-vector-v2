"""`ml_engine.inference.policy` — `InferencePolicy`(신규, D-5D-8) + `load_inference_policy`.

5A `registry.policy.load_policy`(D-M5-6, `known_keys` 전수 로더)의 `PolicyError`(예외)를
**경계에서** 결과 타입 `PolicyRejected`로 바꾼다(digest §6 (c) — 구성 실패 축과 추론 실패
축을 섞지 않는다). 값 범위 불변식(z>0·가중치 합 1·밴드 하한<상한·표본 임계 ≥1)도 여기서
검증하고 위반은 전부 `PolicyRejected`다(scope.md D-5D-8).

`gbm.min_category_rows`만 예외다 — 정책 파일이 `0`을 줘도 거부하지 않는다(D-5D-3): 하한
1 클램프는 이 모듈이 아니라 **소비 지점의 코드 불변식**(`predict.py::segment_availability`
의 `max(1, ·)`)이 두 번째 겹으로 보장한다. 이 모듈은 그 값이 음수가 아님만 확인한다 —
"정책 파일이 0 을 줘도 1"이 성립하려면 0 자체를 거부하지 않아야 하기 때문이다.
"""

from __future__ import annotations

import dataclasses
from collections.abc import Mapping
from decimal import Decimal, InvalidOperation
from pathlib import Path

from ml_engine.inference.rounding import quantize_bid_rate
from ml_engine.registry.policy import Policy, PolicyError, PolicyScalar, load_policy

SHIPPED_INFERENCE_POLICY_VERSION = "inference-v1"

_KNOWN_KEYS: frozenset[str] = frozenset(
    {
        "scenario.z",
        "scenario.conservative.weight",
        "scenario.base.weight",
        "scenario.aggressive.weight",
        "scenario.conservative.z_sign",
        "scenario.base.z_sign",
        "scenario.aggressive.z_sign",
        "scenario.clamp_min",
        "scenario.clamp_max",
        "scenario.bid_rate_digits",
        "assessment.agency_prior_strength",
        "assessment.category_prior_strength",
        "assessment.min_predictive_std",
        "assessment.min_samples_for_variance",
        "assessment.plausible_min",
        "assessment.plausible_max",
        "reserve.draw_count",
        "reserve.expected_price_count",
        "reserve.min_reserve_records",
        "bid_ratio.min_samples",
        "bid_ratio.plausible_min",
        "bid_ratio.plausible_max",
        "gbm.min_category_rows",
        "maturity.window_days",
    }
)

# `threshold >= 1` 불변식이 적용되는 표본 임계 키(D-5D-8) — `gbm.min_category_rows`는
# D-5D-3 에 의해 별도 취급(0 허용, 코드 클램프가 두 번째 겹).
_POSITIVE_THRESHOLD_KEYS: tuple[str, ...] = (
    "scenario.bid_rate_digits",
    "assessment.min_samples_for_variance",
    "reserve.draw_count",
    "reserve.expected_price_count",
    "reserve.min_reserve_records",
    "bid_ratio.min_samples",
    "maturity.window_days",
)


@dataclasses.dataclass(frozen=True)
class PolicyRejected:
    """정책 로드·값 불변식 실패 — 예외가 아니라 결과 타입(구성 실패 축)."""

    reason: str


@dataclasses.dataclass(frozen=True)
class InferencePolicy:
    """5D 가 소비하는 정책 값 전체 — 값 자체는 여기 없다(D-M5-6), 형태만."""

    version: str
    scenario_z: Decimal
    scenario_weights: tuple[
        Decimal, Decimal, Decimal
    ]  # (conservative, base, aggressive)
    scenario_z_signs: tuple[int, int, int]
    scenario_clamp_min: Decimal
    scenario_clamp_max: Decimal
    scenario_bid_rate_digits: int
    assessment_agency_prior_strength: Decimal
    assessment_category_prior_strength: Decimal
    assessment_min_predictive_std: Decimal
    assessment_min_samples_for_variance: int
    assessment_plausible_min: Decimal
    assessment_plausible_max: Decimal
    reserve_draw_count: int
    reserve_expected_price_count: int
    reserve_min_reserve_records: int
    bid_ratio_min_samples: int
    bid_ratio_plausible_min: Decimal
    bid_ratio_plausible_max: Decimal
    gbm_min_category_rows: int
    maturity_window_days: int


def _to_decimal(value: PolicyScalar) -> Decimal:
    if isinstance(value, bool):
        raise TypeError(f"bool 은 decimal 정책 값이 될 수 없다: {value!r}")
    return Decimal(str(value))


def _to_int(value: PolicyScalar) -> int:
    if isinstance(value, bool):
        raise TypeError(f"bool 은 int 정책 값이 될 수 없다: {value!r}")
    if isinstance(value, float) and not value.is_integer():
        raise TypeError(f"정수 정책 값에 소수가 들어왔다: {value!r}")
    if isinstance(value, str):
        raise TypeError(f"정수 정책 값에 문자열이 들어왔다: {value!r}")
    return int(value)


def _coerce_values(
    values: Mapping[str, PolicyScalar],
) -> InferencePolicy | PolicyRejected:
    """평탄 키 → 타입 값(`Decimal`/`int`)만 담당한다 — `version`은 빈 문자열로 두고
    호출부가 채운다(값 불변식 검증은 `_validate_invariants`가 별도로 한다)."""
    try:
        return InferencePolicy(
            version="",
            scenario_z=_to_decimal(values["scenario.z"]),
            scenario_weights=(
                _to_decimal(values["scenario.conservative.weight"]),
                _to_decimal(values["scenario.base.weight"]),
                _to_decimal(values["scenario.aggressive.weight"]),
            ),
            scenario_z_signs=(
                _to_int(values["scenario.conservative.z_sign"]),
                _to_int(values["scenario.base.z_sign"]),
                _to_int(values["scenario.aggressive.z_sign"]),
            ),
            scenario_clamp_min=_to_decimal(values["scenario.clamp_min"]),
            scenario_clamp_max=_to_decimal(values["scenario.clamp_max"]),
            scenario_bid_rate_digits=_to_int(values["scenario.bid_rate_digits"]),
            assessment_agency_prior_strength=_to_decimal(
                values["assessment.agency_prior_strength"]
            ),
            assessment_category_prior_strength=_to_decimal(
                values["assessment.category_prior_strength"]
            ),
            assessment_min_predictive_std=_to_decimal(
                values["assessment.min_predictive_std"]
            ),
            assessment_min_samples_for_variance=_to_int(
                values["assessment.min_samples_for_variance"]
            ),
            assessment_plausible_min=_to_decimal(values["assessment.plausible_min"]),
            assessment_plausible_max=_to_decimal(values["assessment.plausible_max"]),
            reserve_draw_count=_to_int(values["reserve.draw_count"]),
            reserve_expected_price_count=_to_int(
                values["reserve.expected_price_count"]
            ),
            reserve_min_reserve_records=_to_int(values["reserve.min_reserve_records"]),
            bid_ratio_min_samples=_to_int(values["bid_ratio.min_samples"]),
            bid_ratio_plausible_min=_to_decimal(values["bid_ratio.plausible_min"]),
            bid_ratio_plausible_max=_to_decimal(values["bid_ratio.plausible_max"]),
            gbm_min_category_rows=_to_int(values["gbm.min_category_rows"]),
            maturity_window_days=_to_int(values["maturity.window_days"]),
        )
    except (TypeError, ValueError, InvalidOperation, KeyError) as exc:
        return PolicyRejected(f"정책 값 타입 오류: {exc}")


def _validate_bands(raw: InferencePolicy) -> str | None:
    """밴드 하한 < 상한 불변식(D-5D-8) + `clamp_min > 0`(verifier r1 M-2) + `quantize(
    clamp_min, bid_rate_digits) > 0`(verifier r2 F-1) — 위반 시 사유 문자열, 통과 시
    `None`. `clamp_min > 0` 만으로는 부족했다 — `scenario.py::build_scenario_candidates`
    는 클램프 **뒤**에 정책 `bid_rate_digits`로 quantize 하므로, quantize 전에는 양수여도
    quantize 뒤 0 이 되는 조합(예: `bid_rate_digits 1`+`clamp_min 0.04` → `quantize`
    결과 `0.0`)이 `Candidate.bid_rate <= 0`인 후보를 만들어 `Candidate.__post_init__`의
    `ValueError`가 결과 타입 경계 밖으로 샌다. 그래서 이 검사는 `scenario.py`와 **같은**
    `quantize_bid_rate`(`rounding.py`, 단일 출처)로 판정한다 — 두 곳이 각자 quantize를
    구현하면 정책이 통과시킨 값과 커널이 실제로 내는 값의 반올림 규칙이 갈릴 수 있다."""
    if not raw.scenario_clamp_min > 0:
        return f"scenario.clamp_min 은 0보다 커야 한다: {raw.scenario_clamp_min}"
    quantized_clamp_min = quantize_bid_rate(
        raw.scenario_clamp_min, raw.scenario_bid_rate_digits
    )
    if not quantized_clamp_min > 0:
        return (
            f"scenario.clamp_min({raw.scenario_clamp_min})은 scenario.bid_rate_digits"
            f"({raw.scenario_bid_rate_digits}) 자리로 quantize 한 뒤에도 0보다 커야 한다: "
            f"quantize 결과 {quantized_clamp_min}"
        )
    if not raw.scenario_clamp_min < raw.scenario_clamp_max:
        return (
            f"scenario.clamp_min({raw.scenario_clamp_min}) 은 "
            f"scenario.clamp_max({raw.scenario_clamp_max}) 보다 작아야 한다"
        )
    if not raw.assessment_plausible_min < raw.assessment_plausible_max:
        return (
            f"assessment.plausible_min({raw.assessment_plausible_min}) 은 "
            f"assessment.plausible_max({raw.assessment_plausible_max}) 보다 작아야 한다"
        )
    if not raw.bid_ratio_plausible_min < raw.bid_ratio_plausible_max:
        return (
            f"bid_ratio.plausible_min({raw.bid_ratio_plausible_min}) 은 "
            f"bid_ratio.plausible_max({raw.bid_ratio_plausible_max}) 보다 작아야 한다"
        )
    return None


def _validate_thresholds(raw: InferencePolicy) -> str | None:
    """표본 임계 ≥ 1 불변식(D-5D-8) — `gbm.min_category_rows`는 D-5D-3 에 의해 별도
    취급(음수만 거부)."""
    threshold_values = {
        "scenario.bid_rate_digits": raw.scenario_bid_rate_digits,
        "assessment.min_samples_for_variance": raw.assessment_min_samples_for_variance,
        "reserve.draw_count": raw.reserve_draw_count,
        "reserve.expected_price_count": raw.reserve_expected_price_count,
        "reserve.min_reserve_records": raw.reserve_min_reserve_records,
        "bid_ratio.min_samples": raw.bid_ratio_min_samples,
        "maturity.window_days": raw.maturity_window_days,
    }
    for key in _POSITIVE_THRESHOLD_KEYS:
        if threshold_values[key] < 1:
            return f"{key} 는 1 이상이어야 한다: {threshold_values[key]}"
    if raw.gbm_min_category_rows < 0:
        return f"gbm.min_category_rows 는 음수일 수 없다: {raw.gbm_min_category_rows}"
    return None


def _validate_invariants(raw: InferencePolicy) -> str | None:
    """z>0·가중치 합 1·가중치 각각 ≥0·`min_predictive_std>0`·밴드 하한<상한(clamp_min>0
    포함)·임계≥1(D-5D-8, verifier r1 M-2 보강) — 위반 시 사유, 통과 시 `None`."""
    if raw.scenario_z <= 0:
        return f"scenario.z 는 0보다 커야 한다: {raw.scenario_z}"
    if any(weight < 0 for weight in raw.scenario_weights):
        return f"scenario 가중치는 음수일 수 없다: {raw.scenario_weights}"
    weight_sum = sum(raw.scenario_weights, start=Decimal("0"))
    if weight_sum != Decimal("1"):
        return f"scenario 가중치 합은 정확히 1 이어야 한다: {weight_sum}"
    if raw.assessment_min_predictive_std <= 0:
        return (
            "assessment.min_predictive_std 는 0보다 커야 한다: "
            f"{raw.assessment_min_predictive_std}"
        )
    return _validate_bands(raw) or _validate_thresholds(raw)


def load_inference_policy(path: Path) -> InferencePolicy | PolicyRejected:
    """`path`의 YAML 을 `InferencePolicy`로 검증한다 — 미지 키·타입 오류·값 불변식
    위반은 전부 `PolicyRejected`(예외로 새지 않는다)."""
    try:
        policy: Policy = load_policy(path, known_keys=_KNOWN_KEYS)
    except PolicyError as exc:
        return PolicyRejected(str(exc))
    except OSError as exc:
        return PolicyRejected(f"정책 파일을 읽을 수 없다: {exc}")

    missing = _KNOWN_KEYS - set(policy.values)
    if missing:
        return PolicyRejected(f"정책 키 누락: {sorted(missing)}")

    coerced = _coerce_values(policy.values)
    if isinstance(coerced, PolicyRejected):
        return coerced

    invariant_violation = _validate_invariants(coerced)
    if invariant_violation is not None:
        return PolicyRejected(invariant_violation)

    return dataclasses.replace(coerced, version=policy.version)
