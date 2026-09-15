"""RED — `ml_engine.inference.engine`(scope.md ④, D-5D2-1 (b), 설계 검토 구현 지시 6).
진입점 + GBM 미import test(우회 후보 (4))."""

from __future__ import annotations

import ast
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
