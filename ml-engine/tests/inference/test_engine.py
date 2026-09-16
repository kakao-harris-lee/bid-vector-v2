"""RED — `ml_engine.inference.engine`(scope.md ④, D-5D2-1 (b), 설계 검토 구현 지시 6).
진입점 + GBM 미import test(우회 후보 (4)) + M5/5D-3(D-5D3-7) 서빙 경로 3계층 수축."""

from __future__ import annotations

import ast
import dataclasses
import json
from decimal import Decimal
from pathlib import Path

import pytest
from _policy_support import shipped_inference_policy_for_test
from _sample_support import competition_sample

from ml_engine.contracts import common_pb2, features_pb2, prediction_pb2
from ml_engine.inference.engine import serve_bid_rates
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.results import (
    Success,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)

_ENGINE_PATH = (
    Path(__file__).resolve().parents[2]
    / "src"
    / "ml_engine"
    / "inference"
    / "engine.py"
)
_REPO_ROOT = Path(__file__).resolve().parents[3]
_ML_KERNEL_011_INPUT = _REPO_ROOT / "fixtures" / "input" / "ml-kernel-011.json"
_ML_KERNEL_011_EXPECTED = _REPO_ROOT / "fixtures" / "expected" / "ml-kernel-011.json"
_BID_RATES = ("0.90", "0.91", "0.92", "0.93", "0.94", "0.95", "0.96", "0.97")


@pytest.fixture(scope="module")
def policy() -> InferencePolicy:
    return shipped_inference_policy_for_test()


def _valid_features_inputs() -> features_pb2.FeatureInputs:
    inputs = features_pb2.FeatureInputs()
    inputs.base_amount.value.CopyFrom(
        common_pb2.Money(
            amount_won=1_000_000_000,
            currency=common_pb2.CURRENCY_KRW,
            basis=common_pb2.BASIS_BASE_AMOUNT,
            vat_treatment=common_pb2.VAT_TREATMENT_INCLUSIVE,
            provenance=common_pb2.AMOUNT_PROVENANCE_KIND_PUBLISHED,
        )
    )
    inputs.category_code.value = "civil-works"
    inputs.agency_id.value = "agency-1"
    inputs.base_amount_provenance_label.value = (
        common_pb2.BASE_AMOUNT_PROVENANCE_LABEL_CLEAN
    )
    return inputs


def test_serve_bid_rates_success(policy: InferencePolicy) -> None:
    samples = [
        competition_sample(observed_bid_rate=_BID_RATES[i % len(_BID_RATES)])
        for i in range(8)
    ]
    request = prediction_pb2.CalculateOptimalBidRequest(
        features=_valid_features_inputs(), competition_samples=samples
    )
    result = serve_bid_rates(request, policy)
    assert isinstance(result, Success)
    assert len(result.candidates) == 3


def test_serve_bid_rates_malformed_features_is_unmeasurable(
    policy: InferencePolicy,
) -> None:
    request = prediction_pb2.CalculateOptimalBidRequest(
        features=features_pb2.FeatureInputs(),
    )
    result = serve_bid_rates(request, policy)
    assert result == Unmeasurable(
        UnmeasurableReason.FEATURE_ABSENT, UnmeasurableDetail.ROW_REJECTED
    )


def test_serve_bid_rates_too_few_samples_is_unmeasurable(
    policy: InferencePolicy,
) -> None:
    request = prediction_pb2.CalculateOptimalBidRequest(
        features=_valid_features_inputs(),
        competition_samples=[competition_sample() for _ in range(2)],
    )
    result = serve_bid_rates(request, policy)
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES


def test_engine_module_does_not_import_gbm_predict_module() -> None:
    """우회 후보 (4) — `engine.py`가 `ml_engine.inference.predict`(GBM)를 import하면
    엔진 연결이 생긴다(D-5D2-1 (b) 위반). AST 정적 검사(런타임 import 여부와 무관하게
    소스 자체가 그 경로를 참조하지 않아야 한다)."""
    tree = ast.parse(_ENGINE_PATH.read_text(encoding="utf-8"))
    imported_names: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and node.module:
            imported_names.add(node.module)
        elif isinstance(node, ast.Import):
            imported_names.update(alias.name for alias in node.names)
    assert "ml_engine.inference.predict" not in imported_names
    assert not any(name.startswith("ml_engine.registry") for name in imported_names)


def test_engine_module_does_not_have_predict_in_sys_modules_dependency() -> None:
    """런타임 확인 — `ml_engine.inference.engine` 의 전역 이름공간에 GBM `predict_bid_
    rates`가 없다(간접 재수출 경로로 들어오는 것도 막는다)."""
    import ml_engine.inference.engine as engine_module

    assert "predict_bid_rates" not in vars(engine_module)
    assert not hasattr(engine_module, "segment_availability")


# ---- M5/5D-3(D-5D3-7) — 서빙 경로 3계층 수축: golden ml-kernel-011 의 계층 표본 수
# (5/10/500)·정책을 wire 요청으로 조립해 `serve_bid_rates` 경유 `diagnostics`를 011
# expected(segmentSupport DIRECT·agencySampleCount 5·below true·shrinkageWeight 0.25)
# 와 대조한다. golden corpus 자체는 편집하지 않는다 — 이 test 는 011 의 값을 **읽기만**
# 한다(2026-09-12 승인 corpus 범위 유지). mean/variance 는 대조하지 않는다 — 수축
# 가중치는 계층 표본 **수**만의 함수(011 derivation `w = n/(n+k)`)라 값 일치 없이
# 재현 가능하다(D-5D3-7 근거).


def _load_ml_kernel_011() -> tuple[dict[str, object], dict[str, object]]:
    input_case = json.loads(_ML_KERNEL_011_INPUT.read_text(encoding="utf-8"))
    expected_case = json.loads(_ML_KERNEL_011_EXPECTED.read_text(encoding="utf-8"))
    return input_case, expected_case


def _build_three_tier_samples(
    *, agency_count: int, category_only_count: int, remainder_count: int
) -> list[features_pb2.CompetitionSample]:
    """`agency_count`는 기관·공종 축 둘 다 일치, `category_only_count`는 공종만
    일치(기관은 기본값 `Missing(NOT_COLLECTED_YET)`이라 불일치), `remainder_count`는
    둘 다 기본값이라 전역에만 든다(D-5D3-4 — category 집합이 agency 일치 표본을
    포함하도록 앞의 두 구간을 겹치지 않게 쌓는다)."""
    samples: list[features_pb2.CompetitionSample] = []
    index = 0
    for _ in range(agency_count):
        samples.append(
            competition_sample(
                observed_bid_rate=_BID_RATES[index % len(_BID_RATES)],
                agency_id="AGENCY-MATCH",
                category_code="CATEGORY-MATCH",
            )
        )
        index += 1
    for _ in range(category_only_count):
        samples.append(
            competition_sample(
                observed_bid_rate=_BID_RATES[index % len(_BID_RATES)],
                category_code="CATEGORY-MATCH",
            )
        )
        index += 1
    for _ in range(remainder_count):
        samples.append(
            competition_sample(observed_bid_rate=_BID_RATES[index % len(_BID_RATES)])
        )
        index += 1
    return samples


def test_serve_bid_rates_wire_driven_direct_segment_support_matches_golden_011() -> (
    None
):
    """D-5D3-7 — `OPEN-5D2-SAMPLE-SEGMENT`가 서빙 경로(`engine.serve_bid_rates`)에서
    닫혔음을 증명한다: golden 011 은 K5/조립기 진단 함수를 직접 호출해 검증했지만
    (5D-2 알려진 제한 1), 이 test 는 **wire 요청**만으로 같은 진단을 재현한다."""
    input_case, expected_case = _load_ml_kernel_011()
    policy_cfg = input_case["policy"]["assessment"]  # type: ignore[index]
    base_policy = shipped_inference_policy_for_test()
    tuned_policy = dataclasses.replace(
        base_policy,
        assessment_agency_prior_strength=Decimal(
            str(policy_cfg["agencyPriorStrength"])
        ),
        assessment_category_prior_strength=Decimal(
            str(policy_cfg["categoryPriorStrength"])
        ),
        assessment_min_predictive_std=Decimal(str(policy_cfg["minPredictiveStd"])),
        assessment_min_samples_for_variance=policy_cfg["minSamplesForVariance"],
        assessment_agency_sample_threshold=policy_cfg["agencySampleThreshold"],
    )

    levels = input_case["levels"]  # type: ignore[index]
    agency_count = levels["agency"]["sampleCount"]
    category_count = levels["category"]["sampleCount"]
    global_count = levels["global"]["sampleCount"]
    samples = _build_three_tier_samples(
        agency_count=agency_count,
        category_only_count=category_count - agency_count,
        remainder_count=global_count - category_count,
    )

    features = _valid_features_inputs()
    features.agency_id.value = "AGENCY-MATCH"
    features.category_code.value = "CATEGORY-MATCH"
    request = prediction_pb2.CalculateOptimalBidRequest(
        features=features, competition_samples=samples
    )
    result = serve_bid_rates(request, tuned_policy)
    assert isinstance(result, Success)

    expected_diagnostics = expected_case["diagnostics"]  # type: ignore[index]
    assert (
        result.diagnostics.segment_support.value
        == expected_diagnostics["segmentSupport"]
    )
    assert (
        result.diagnostics.agency_sample_count
        == expected_diagnostics["agencySampleCount"]
        == agency_count
    )
    assert (
        result.diagnostics.agency_sample_below_threshold
        is expected_diagnostics["agencySampleBelowThreshold"]
    )
    assert result.diagnostics.shrinkage_weight == Decimal(
        expected_diagnostics["shrinkageWeight"]["fraction"]
    )
