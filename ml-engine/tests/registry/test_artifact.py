"""RED — `ml_engine.registry.artifact`(scope.md ⑦, D-5D 계약 인수, 설계 검토 구현 지시 7).
변조 표본 다섯: checksum 불일치·schema version 미지원·feature_names 불일치·
feature_manifest_checksum 불일치·sample_scope 미선언 — 전부 `ArtifactRejected`(객체 미생성).
"""

from __future__ import annotations

import copy
import hashlib
import json

import pytest

from ml_engine.features import FEATURE_SCHEMA_V2
from ml_engine.registry.artifact import (
    ArtifactManifestV1,
    ArtifactRejected,
    LoadedArtifact,
    ModelReleaseRef,
    load_artifact,
)

_FEATURE_NAMES = [column.name for column in FEATURE_SCHEMA_V2.columns]


def _manifest_payload() -> dict[str, object]:
    return {
        "manifest_schema_version": "artifact-manifest-v1",
        "release": {
            "release_id": "release-2026-09-12",
            "artifact_checksum": "",  # filled in by _raw_bytes
            "feature_schema_version": FEATURE_SCHEMA_V2.version,
            "code_version": "ml-engine@abc1234",
            "dataset_id": "dataset-2026-09-01",
        },
        "feature_manifest_checksum": "f" * 64,
        "feature_names": list(_FEATURE_NAMES),
        "sample_scope": "serving-population",
        "residual_std": 0.03,
        "training_row_count": 12345,
        "booster_model": "tree0\n...",
        "reproducibility": {"seed": 20260812, "num_threads": 4, "deterministic": True},
    }


def _raw_bytes(payload: dict[str, object]) -> bytes:
    """checksum 을 자기 참조로 채운 뒤 직렬화한다(양성 경로)."""
    body = copy.deepcopy(payload)
    # checksum 은 raw bytes 전체의 sha256 이다 — 먼저 checksum 없이 직렬화해 해시를 구하고
    # 그 값을 release.artifact_checksum 에 채운 뒤 다시 직렬화한다(순환 참조 회피를 위해
    # 테스트에서는 release.artifact_checksum 자리를 고정 sentinel 로 채우고, expected 도
    # 그 sentinel 로 맞춘다 — 실제 5C 산출물은 별도 채널로 checksum 을 계산해 써 넣는다).
    body["release"]["artifact_checksum"] = "0" * 64  # type: ignore[index]
    serialized = json.dumps(body, sort_keys=True).encode("utf-8")
    return serialized


def _expected_ref(raw: bytes, payload: dict[str, object]) -> ModelReleaseRef:
    release = payload["release"]
    assert isinstance(release, dict)
    return ModelReleaseRef(
        release_id=str(release["release_id"]),
        artifact_checksum=hashlib.sha256(raw).hexdigest(),
        feature_schema_version=str(release["feature_schema_version"]),
        feature_manifest_checksum=str(payload["feature_manifest_checksum"]),
    )


def test_valid_artifact_loads_successfully() -> None:
    payload = _manifest_payload()
    raw = _raw_bytes(payload)
    expected = _expected_ref(raw, payload)
    result = load_artifact(raw, expected)
    assert isinstance(result, LoadedArtifact)
    assert isinstance(result.manifest, ArtifactManifestV1)
    assert result.manifest.feature_names == tuple(_FEATURE_NAMES)
    assert result.manifest.reproducibility.seed == 20260812


def test_checksum_mismatch_rejects_without_constructing_object() -> None:
    payload = _manifest_payload()
    raw = _raw_bytes(payload)
    expected = _expected_ref(raw, payload)
    tampered = raw + b" "  # 1바이트 변조
    result = load_artifact(tampered, expected)
    assert isinstance(result, ArtifactRejected)
    assert "checksum" in result.reason


def test_unsupported_feature_schema_version_is_rejected() -> None:
    payload = _manifest_payload()
    raw = _raw_bytes(payload)
    expected = _expected_ref(raw, payload)
    expected_with_bad_schema = ModelReleaseRef(
        release_id=expected.release_id,
        artifact_checksum=expected.artifact_checksum,
        feature_schema_version="award-rate-features-v999",
        feature_manifest_checksum=expected.feature_manifest_checksum,
    )
    result = load_artifact(raw, expected_with_bad_schema)
    assert isinstance(result, ArtifactRejected)


def test_feature_names_order_mismatch_is_rejected() -> None:
    payload = _manifest_payload()
    names = list(payload["feature_names"])  # type: ignore[arg-type]
    payload["feature_names"] = list(reversed(names))
    raw = _raw_bytes(payload)
    expected = _expected_ref(raw, payload)
    result = load_artifact(raw, expected)
    assert isinstance(result, ArtifactRejected)
    assert "feature_names" in result.reason


def test_feature_manifest_checksum_mismatch_is_rejected() -> None:
    payload = _manifest_payload()
    raw = _raw_bytes(payload)
    expected = _expected_ref(raw, payload)
    tampered_expected = ModelReleaseRef(
        release_id=expected.release_id,
        artifact_checksum=expected.artifact_checksum,
        feature_schema_version=expected.feature_schema_version,
        feature_manifest_checksum="a" * 64,
    )
    result = load_artifact(raw, tampered_expected)
    assert isinstance(result, ArtifactRejected)
    assert "feature_manifest_checksum" in result.reason


@pytest.mark.parametrize("sample_scope", ["", None])
def test_undeclared_sample_scope_is_rejected(sample_scope: object) -> None:
    payload = _manifest_payload()
    payload["sample_scope"] = sample_scope
    raw = _raw_bytes(payload)
    expected = _expected_ref(raw, payload)
    result = load_artifact(raw, expected)
    assert isinstance(result, ArtifactRejected)
    assert "sample_scope" in result.reason


def test_malformed_json_is_rejected() -> None:
    raw = b"not json"
    expected_matching = ModelReleaseRef(
        release_id="r",
        artifact_checksum=hashlib.sha256(raw).hexdigest(),
        feature_schema_version="v",
        feature_manifest_checksum="c",
    )
    result = load_artifact(raw, expected_matching)
    assert isinstance(result, ArtifactRejected)


def test_missing_reproducibility_is_rejected() -> None:
    payload = _manifest_payload()
    del payload["reproducibility"]
    raw = _raw_bytes(payload)
    expected = _expected_ref(raw, payload)
    result = load_artifact(raw, expected)
    assert isinstance(result, ArtifactRejected)
    assert "reproducibility" in result.reason
