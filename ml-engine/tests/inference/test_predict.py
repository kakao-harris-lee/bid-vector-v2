"""RED — `ml_engine.inference.predict`(scope.md ②③⑦, 설계 검토 구현 지시 8). 가용성
게이트가 가용성 게이트·예측 경로 둘 다에서 같은 판정을 내는지, fake booster 의 NaN/
inf/음수가 결과 타입으로 처리되는지 확인.
"""

from __future__ import annotations

import hashlib
import json
import math
from collections.abc import Sequence
from pathlib import Path

import pytest

from ml_engine.features import (
    FEATURE_SCHEMA_V2,
    AgencyTargetEncoding,
    AwardRateFeatureSpace,
    FactValue,
    FeatureFacts,
    Missing,
    Present,
    Vocabulary,
)
from ml_engine.inference.policy import InferencePolicy, load_inference_policy
from ml_engine.inference.predict import (
    Available,
    predict_bid_rates,
    segment_availability,
)
from ml_engine.inference.results import (
    Success,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)
from ml_engine.registry.artifact import LoadedArtifact, ModelReleaseRef, load_artifact

_POLICY_PATH = Path(__file__).resolve().parents[2] / "policy" / "inference-v1.yaml"
_FEATURE_NAMES = [column.name for column in FEATURE_SCHEMA_V2.columns]


@pytest.fixture(scope="module")
def policy() -> InferencePolicy:
    loaded = load_inference_policy(_POLICY_PATH)
    assert isinstance(loaded, InferencePolicy)
    return loaded


class _FakeBooster:
    def __init__(self, value: float) -> None:
        self._value = value

    def predict(self, rows: Sequence[Sequence[float]]) -> Sequence[float]:
        return [self._value for _ in rows]


def _feature_space() -> AwardRateFeatureSpace:
    return AwardRateFeatureSpace(
        categories=Vocabulary(("civil", "electrical")),
        denominator_sources=Vocabulary(("CLEAN",)),
        agency_encoding=AgencyTargetEncoding(
            agency_means={("agency-1", "civil"): (0.95, 40)},
            category_means={"civil": 0.95},
            global_mean=0.9,
        ),
    )


def _facts() -> FeatureFacts:
    present: FactValue[str] = Present("civil")
    agency: FactValue[str] = Present("agency-1")
    denominator: FactValue[str] = Present("CLEAN")
    amount: FactValue[float] = Present(1_000_000_000.0)
    return FeatureFacts(
        base_amount=amount,
        category_code=present,
        agency_id=agency,
        denominator_source=denominator,
    )


def _artifact(residual_std: float = 0.03) -> LoadedArtifact:
    payload = {
        "manifest_schema_version": "artifact-manifest-v1",
        "release": {
            "release_id": "release-1",
            "artifact_checksum": "0" * 64,
            "feature_schema_version": FEATURE_SCHEMA_V2.version,
            "code_version": "ml-engine@abc1234",
            "dataset_id": "dataset-1",
        },
        "feature_manifest_checksum": "f" * 64,
        "feature_names": list(_FEATURE_NAMES),
        "sample_scope": "serving-population",
        "residual_std": residual_std,
        "training_row_count": 1000,
        "booster_model": "tree",
        "reproducibility": {"seed": 1, "num_threads": 4, "deterministic": True},
    }
    raw = json.dumps(payload, sort_keys=True).encode("utf-8")
    expected = ModelReleaseRef(
        release_id="release-1",
        artifact_checksum=hashlib.sha256(raw).hexdigest(),
        feature_schema_version=FEATURE_SCHEMA_V2.version,
        feature_manifest_checksum="f" * 64,
    )
    loaded = load_artifact(raw, expected)
    assert isinstance(loaded, LoadedArtifact)
    return loaded


def test_segment_availability_zero_rows_is_untrained(policy: InferencePolicy) -> None:
    result = segment_availability(0, policy)
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.UNTRAINED_SEGMENT
    assert result.detail is UnmeasurableDetail.NEVER_TRAINED


def test_segment_availability_below_threshold_is_insufficient(
    policy: InferencePolicy,
) -> None:
    result = segment_availability(policy.gbm_min_category_rows - 1, policy)
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
    assert result.detail is UnmeasurableDetail.SHALLOW_SEGMENT


def test_segment_availability_at_threshold_is_available(
    policy: InferencePolicy,
) -> None:
    result = segment_availability(policy.gbm_min_category_rows, policy)
    assert isinstance(result, Available)


def test_predict_bid_rates_uses_same_availability_gate(policy: InferencePolicy) -> None:
    """ML-02 ② — 가용성 게이트와 예측 경로가 같은 판정을 낸다."""
    result = predict_bid_rates(
        facts=_facts(),
        feature_space=_feature_space(),
        artifact=_artifact(),
        policy=policy,
        booster=_FakeBooster(0.95),
        segment_rows=0,
        sample_size=0,
    )
    availability = segment_availability(0, policy)
    assert isinstance(result, Unmeasurable)
    assert isinstance(availability, Unmeasurable)
    assert (result.reason, result.detail) == (availability.reason, availability.detail)


def test_predict_bid_rates_success() -> None:
    policy = load_inference_policy(_POLICY_PATH)
    assert isinstance(policy, InferencePolicy)
    result = predict_bid_rates(
        facts=_facts(),
        feature_space=_feature_space(),
        artifact=_artifact(),
        policy=policy,
        booster=_FakeBooster(0.95),
        segment_rows=policy.gbm_min_category_rows,
        sample_size=40,
    )
    assert isinstance(result, Success)
    assert len(result.candidates) == 3


@pytest.mark.parametrize("bad_value", [math.nan, math.inf, -math.inf])
def test_fake_booster_non_finite_output_is_unmeasurable(
    policy: InferencePolicy, bad_value: float
) -> None:
    result = predict_bid_rates(
        facts=_facts(),
        feature_space=_feature_space(),
        artifact=_artifact(),
        policy=policy,
        booster=_FakeBooster(bad_value),
        segment_rows=policy.gbm_min_category_rows,
        sample_size=40,
    )
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.FEATURE_ABSENT
    assert result.detail is UnmeasurableDetail.NON_FINITE_INPUT


def test_fake_booster_negative_output_is_clamped_not_rejected(
    policy: InferencePolicy,
) -> None:
    """음수 예측은 거부가 아니라 정책 clamp_min 으로 눌린다(legacy `clamp_bid_rate`
    그대로 — 0 률로 접히는 것은 `Candidate.bid_rate > 0` 불변식이 막는다)."""
    result = predict_bid_rates(
        facts=_facts(),
        feature_space=_feature_space(),
        artifact=_artifact(),
        policy=policy,
        booster=_FakeBooster(-5.0),
        segment_rows=policy.gbm_min_category_rows,
        sample_size=40,
    )
    assert isinstance(result, Success)
    for candidate in result.candidates:
        assert candidate.bid_rate == policy.scenario_clamp_min


def test_row_rejected_is_feature_absent(policy: InferencePolicy) -> None:
    missing_amount: FactValue[float] = Missing(1)
    facts = FeatureFacts(
        base_amount=missing_amount,
        category_code=Present("civil"),
        agency_id=Present("agency-1"),
        denominator_source=Present("CLEAN"),
    )
    result = predict_bid_rates(
        facts=facts,
        feature_space=_feature_space(),
        artifact=_artifact(),
        policy=policy,
        booster=_FakeBooster(0.95),
        segment_rows=policy.gbm_min_category_rows,
        sample_size=40,
    )
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.FEATURE_ABSENT
    assert result.detail is UnmeasurableDetail.ROW_REJECTED


def test_sample_size_below_variance_minimum_is_unmeasurable(
    policy: InferencePolicy,
) -> None:
    result = predict_bid_rates(
        facts=_facts(),
        feature_space=_feature_space(),
        artifact=_artifact(),
        policy=policy,
        booster=_FakeBooster(0.95),
        segment_rows=policy.gbm_min_category_rows,
        sample_size=1,
    )
    assert isinstance(result, Unmeasurable)
    assert result.reason is UnmeasurableReason.INSUFFICIENT_SAMPLES
    assert result.detail is UnmeasurableDetail.DEGENERATE_VARIANCE
