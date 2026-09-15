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

# M5/5D-2 — `assessment.agency_sample_threshold`는 출하 `inference-v1.yaml`에 없다
# (D-5D2-3, `OPEN-5D2-POLICY-VALUES` 값 미정, 운영자 결정 2026-09-13 (c)). 이 test 파일의
# 다른 모든 test 는 이 키와 무관한 동작(다른 필드의 불변식)을 검증하므로, `_base_values()`가
# 실제 출하 값 위에 이 키만 synthetic 으로 채워 넣는다 — 값 자체(`1`, 가장 관대해 다른
# 어떤 test 의 단언에도 영향을 주지 않는다)는 `OPEN-5D2-POLICY-VALUES`의 답이 아니다.
_SYNTHETIC_AGENCY_SAMPLE_THRESHOLD = 1


def _base_values() -> dict[str, object]:
    values = dict(yaml.safe_load(_POLICY_PATH.read_text(encoding="utf-8")))
    values["assessment.agency_sample_threshold"] = _SYNTHETIC_AGENCY_SAMPLE_THRESHOLD
    return values


def _write(tmp_path: Path, values: dict[str, object]) -> Path:
    path = tmp_path / "policy.yaml"
    path.write_text(yaml.safe_dump(values), encoding="utf-8")
    return path


def test_shipped_policy_file_is_rejected_missing_agency_sample_threshold() -> None:
    """D-5D2-3 — `assessment.agency_sample_threshold` 키 신설 이후, 그 값이 아직
    승인되지 않아(`OPEN-5D2-POLICY-VALUES`) 출하 파일에 없다. 이 로드 실패가
    의도된 상태다: 서빙(5E)이 켜지려면 값 승인이 선행돼야 함이 로더에서 드러난다."""
    result = load_inference_policy(_POLICY_PATH)
    assert isinstance(result, PolicyRejected)
    assert "agency_sample_threshold" in result.reason


def test_shipped_values_with_agency_sample_threshold_declared_load_successfully(
    tmp_path: Path,
) -> None:
    """출하 값 스물셋 + synthetic `agency_sample_threshold` 하나가 전부 올바르게
    타입 변환·불변식 통과되는지 — 이 키 신설이 기존 스물세 값의 로드를 깨지 않는다."""
    policy = load_inference_policy(_write(tmp_path, _base_values()))
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
    assert policy.assessment_agency_sample_threshold == 1
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
        "assessment.agency_sample_threshold",
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


def test_clamp_min_quantizes_to_zero_is_rejected_digits_1(tmp_path: Path) -> None:
    """verifier r2 F-1 재현 1 — `clamp_min > 0`(quantize **전**) 만으로는 부족하다.
    `bid_rate_digits=1`에서 `quantize_bid_rate(0.04, 1)`은 `0.0`(0.04 는 한 자리로
    반올림하면 0)이라, 이전 판은 이 정책을 통과시켜 `build_scenario_candidates`가
    `Candidate.__post_init__`의 `ValueError`를 던지게 했다."""
    values = _base_values()
    values["scenario.bid_rate_digits"] = 1
    values["scenario.clamp_min"] = 0.04
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)
    assert "clamp_min" in result.reason


def test_clamp_min_quantizes_to_zero_is_rejected_digits_4(tmp_path: Path) -> None:
    """verifier r2 F-1 재현 2 — `bid_rate_digits=4`(출하 기본)에서도
    `clamp_min=0.00001`(1e-05)은 quantize 뒤 `0.0000`이 된다."""
    values = _base_values()
    values["scenario.clamp_min"] = 0.00001
    result = load_inference_policy(_write(tmp_path, values))
    assert isinstance(result, PolicyRejected)
    assert "clamp_min" in result.reason


def test_shipped_clamp_min_survives_quantize_check(tmp_path: Path) -> None:
    """출하 정책(clamp_min 0.7, digits 4)은 F-1 불변식에 영향받지 않는다 — 회귀 없음.
    `_base_values()`를 거쳐 로드한다(M5/5D-2 — 출하 파일 자체는 `agency_sample_threshold`
    미선언으로 이제 항상 거부되므로, `agency_sample_threshold` 를 제외한 다른 값의 회귀
    여부는 synthetic 주입 경로로 확인한다)."""
    policy = load_inference_policy(_write(tmp_path, _base_values()))
    assert isinstance(policy, InferencePolicy)
    assert policy.scenario_clamp_min == Decimal("0.7")


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
