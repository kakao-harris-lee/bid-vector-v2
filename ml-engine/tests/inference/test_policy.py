"""RED — `ml_engine.inference.policy`(D-5D-8, scope.md 구현 지시 1). `load_inference_policy`
가 `PolicyError`(예외)를 `PolicyRejected`(결과 타입)로 바꾸고 값 불변식을 검증하는지 확인.
"""

from __future__ import annotations

from decimal import Decimal
from pathlib import Path

import pytest
import yaml

from ml_engine.inference.policy import (
    InferencePolicy,
    PolicyRejected,
    load_inference_policy,
)

_POLICY_PATH = Path(__file__).resolve().parents[2] / "policy" / "inference-v1.yaml"


def _base_values() -> dict[str, object]:
    return dict(yaml.safe_load(_POLICY_PATH.read_text(encoding="utf-8")))


def _write(tmp_path: Path, values: dict[str, object]) -> Path:
    path = tmp_path / "policy.yaml"
    path.write_text(yaml.safe_dump(values), encoding="utf-8")
    return path


def test_shipped_policy_file_loads_successfully() -> None:
    policy = load_inference_policy(_POLICY_PATH)
    assert isinstance(policy, InferencePolicy)
    assert policy.version == "inference-v1"
    assert policy.scenario_z == Decimal("1.2816")
    assert policy.scenario_weights == (
        Decimal("0.24"),
        Decimal("0.52"),
        Decimal("0.24"),
    )
    assert policy.scenario_z_signs == (-1, 0, 1)
    assert policy.scenario_clamp_min == Decimal("0.7")
    assert policy.scenario_clamp_max == Decimal("1.4")
    assert policy.scenario_bid_rate_digits == 4
    assert policy.assessment_agency_prior_strength == Decimal("12.0")
    assert policy.assessment_category_prior_strength == Decimal("40.0")
    assert policy.assessment_min_predictive_std == Decimal("0.002")
    assert policy.assessment_min_samples_for_variance == 2
    assert policy.assessment_plausible_min == Decimal("0.8")
    assert policy.assessment_plausible_max == Decimal("1.2")
    assert policy.reserve_draw_count == 4
    assert policy.reserve_expected_price_count == 15
    assert policy.reserve_min_reserve_records == 8
    assert policy.bid_ratio_min_samples == 3
    assert policy.bid_ratio_plausible_min == Decimal("0.5")
    assert policy.bid_ratio_plausible_max == Decimal("1.5")
    assert policy.gbm_min_category_rows == 40
    assert policy.maturity_window_days == 7


def test_missing_file_is_rejected(tmp_path: Path) -> None:
    result = load_inference_policy(tmp_path / "missing.yaml")
    assert isinstance(result, PolicyRejected)


def test_unknown_key_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    values["unknown.key"] = 1
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


def test_missing_key_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    del values["maturity.window_days"]
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


def test_z_not_positive_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    values["scenario.z"] = 0.0
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


def test_weights_not_summing_to_one_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    values["scenario.base.weight"] = 0.51
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


def test_clamp_band_inverted_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    values["scenario.clamp_min"] = 1.4
    values["scenario.clamp_max"] = 0.7
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


def test_assessment_band_inverted_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    values["assessment.plausible_min"] = 1.2
    values["assessment.plausible_max"] = 0.8
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


def test_bid_ratio_band_inverted_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    values["bid_ratio.plausible_min"] = 1.5
    values["bid_ratio.plausible_max"] = 0.5
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


@pytest.mark.parametrize(
    "key",
    [
        "reserve.draw_count",
        "reserve.expected_price_count",
        "reserve.min_reserve_records",
        "bid_ratio.min_samples",
        "assessment.min_samples_for_variance",
        "maturity.window_days",
        "scenario.bid_rate_digits",
    ],
)
def test_threshold_below_one_is_rejected(tmp_path: Path, key: str) -> None:
    values = _base_values()
    values[key] = 0
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


def test_gbm_min_category_rows_zero_is_not_rejected_by_policy(tmp_path: Path) -> None:
    """D-5D-3 — 정책 파일이 0 을 줘도 정책 로더는 거부하지 않는다(하한 1 클램프는
    `predict.segment_availability`의 코드 불변식이 두 번째 겹으로 보장한다)."""
    values = _base_values()
    values["gbm.min_category_rows"] = 0
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, InferencePolicy)
    assert result.gbm_min_category_rows == 0


def test_gbm_min_category_rows_negative_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    values["gbm.min_category_rows"] = -1
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


def test_clamp_min_non_positive_is_rejected(tmp_path: Path) -> None:
    """verifier r1 M-2 — `clamp_min <= 0`은 하한<상한 검사만으로는 걸리지 않는다(예:
    -1.0 < 1.4). 이 값이 통과하면 `build_scenario_candidates`가 `bid_rate <= 0`인 후보를
    만들어 `Candidate.__post_init__`의 `ValueError`가 결과 타입 경계 밖으로 샌다."""
    values = _base_values()
    values["scenario.clamp_min"] = -1.0
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)
    assert "clamp_min" in result.reason


def test_clamp_min_zero_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    values["scenario.clamp_min"] = 0.0
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)


@pytest.mark.parametrize(
    "weight_key",
    [
        "scenario.conservative.weight",
        "scenario.base.weight",
        "scenario.aggressive.weight",
    ],
)
def test_negative_weight_is_rejected_even_when_sum_is_one(
    tmp_path: Path, weight_key: str
) -> None:
    """verifier r1 M-2 — 가중치 셋이 합 1 을 유지해도 개별 값이 음수면 거부한다."""
    values = _base_values()
    values[weight_key] = -0.10
    other_keys = [
        key
        for key in (
            "scenario.conservative.weight",
            "scenario.base.weight",
            "scenario.aggressive.weight",
        )
        if key != weight_key
    ]
    # 합을 정확히 1로 유지 — 나머지 둘에 0.10을 절반씩 보탠다.
    values[other_keys[0]] = float(values[other_keys[0]]) + 0.05
    values[other_keys[1]] = float(values[other_keys[1]]) + 0.05
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)
    assert "가중치" in result.reason


def test_min_predictive_std_non_positive_is_rejected(tmp_path: Path) -> None:
    values = _base_values()
    values["assessment.min_predictive_std"] = 0.0
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)
    assert "min_predictive_std" in result.reason
