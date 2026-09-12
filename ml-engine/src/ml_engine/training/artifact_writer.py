"""Reuse: bid-vector/app/services/ml_training/award_rate_gbm.py@ed4b06c

`ml_engine.training.artifact_writer` — `TrainedArtifact` → 최종 canonical JSON 바이트
(scope ⑧). legacy `_assemble_artifact`(pydantic 계약 조립)를 이어받되, 계약 모델이
V2 결과 타입으로 바뀌었다. **D-5C-9** — `release` 안에 `artifact_checksum`을 두지
않는다(자기참조 금지). checksum 은 이 함수가 낸 `ArtifactBytes.sha256`이다(2C
`ArtifactReference.release.artifact_checksum`으로 호출자가 옮긴다).

필드 집합은 5D scope ⑦ `ArtifactManifestV1` 그대로(`manifest_schema_version`·`release`·
`feature_manifest_checksum`·`feature_names`·`sample_scope`·`residual_std`·
`training_row_count`·`booster_model`·`reproducibility`) + 5C-1 추가 여덟
(`training_spec_version`·`training_spec_checksum`·`training_policy_version`·
`feed_origin_only`·`categories`·`denominator_sources`·`agency_encoding`·`rejected_rows`).
`agency_encoding`은 5B `FeatureManifest`를 canonical JSON 으로 한 번 굳힌 뒤 그 dict 를
통째로 embed 한다 — 표의 정렬 불변식(`FeatureManifest.__post_init__`)을 다시 구현하지
않는다.

verifier r1 H-1 — `write_artifact`는 **`trained` 하나만** 받는다. `release`(`release_id`·
`feature_schema_version`·`code_version`·`dataset_id`)는 전부 `trained`(호출자 인자가
아니다)에서만 파생한다 — 우회 후보 (12)(`release.dataset_id` 를 요청과 다르게)가 시그니처
차원에서 성립하지 않는다.
"""

from __future__ import annotations

import dataclasses
import hashlib
import json
import math
from dataclasses import dataclass

from ml_engine.features import (
    FEATURE_SCHEMA_V2,
    CanonicalizationRejected,
    NameMismatch,
    NonFiniteValue,
    canonical_json,
    verify_feature_names,
)
from ml_engine.training.booster import booster_to_text
from ml_engine.training.corpus import RejectedRowAccounting
from ml_engine.training.release import ReleaseIdentity, derive_release_id
from ml_engine.training.train import TrainedArtifact

_MANIFEST_SCHEMA_VERSION = "artifact-manifest-v1"


@dataclass(frozen=True)
class ArtifactBytes:
    bytes: bytes
    sha256: str
    release: ReleaseIdentity


@dataclass(frozen=True)
class _RejectedRowsJson:
    fact_rejections: dict[str, int]
    label_rejections: dict[str, int]
    missing_fact_rejections: dict[str, int]


def _serialize_rejected_rows(accounting: RejectedRowAccounting) -> _RejectedRowsJson:
    return _RejectedRowsJson(
        fact_rejections={
            reason.value: count for reason, count in accounting.fact_rejections.items()
        },
        label_rejections={
            reason.value: count for reason, count in accounting.label_rejections.items()
        },
        missing_fact_rejections={
            reason.value: count
            for reason, count in accounting.missing_fact_rejections.items()
        },
    )


def _release_for(trained: TrainedArtifact) -> ReleaseIdentity:
    """다섯 입력 전부 `trained` 에서만 읽는다(H-1 — 호출자 인자 경로 없음)."""
    release_id = derive_release_id(
        dataset_id=trained.dataset_id,
        training_spec_version=trained.training_spec_version,
        training_spec_checksum=trained.training_spec_checksum,
        seed=trained.reproducibility.seed,
        code_version=trained.code_version,
    )
    return ReleaseIdentity(
        release_id=release_id,
        feature_schema_version=trained.feature_manifest.schema_version,
        code_version=trained.code_version,
        dataset_id=trained.dataset_id,
    )


def write_artifact(
    trained: TrainedArtifact,
) -> ArtifactBytes | NameMismatch | CanonicalizationRejected:
    """유일 artifact 조립 지점. `feature_name()` 불일치·비표준 JSON(NaN/Infinity)은
    결과 타입으로 거부한다(예외 없음, 우회 후보 (7))."""
    verification = verify_feature_names(
        trained.booster.feature_name(), FEATURE_SCHEMA_V2
    )
    if isinstance(verification, NameMismatch):
        return verification
    if not math.isfinite(trained.residual_std):
        return CanonicalizationRejected(NonFiniteValue("residual_std"))

    canonical_feature_manifest = canonical_json(trained.feature_manifest)
    if isinstance(canonical_feature_manifest, CanonicalizationRejected):
        return canonical_feature_manifest
    feature_manifest_checksum = hashlib.sha256(canonical_feature_manifest).hexdigest()
    agency_encoding_embedded = json.loads(canonical_feature_manifest)

    release = _release_for(trained)
    rejected_rows = _serialize_rejected_rows(trained.rejected_rows)

    payload = {
        "manifest_schema_version": _MANIFEST_SCHEMA_VERSION,
        "release": dataclasses.asdict(release),
        "feature_manifest_checksum": feature_manifest_checksum,
        "feature_names": list(trained.booster.feature_name()),
        "sample_scope": trained.sample_scope,
        "residual_std": trained.residual_std,
        "training_row_count": trained.training_row_count,
        "booster_model": booster_to_text(trained.booster),
        "reproducibility": dataclasses.asdict(trained.reproducibility),
        "training_spec_version": trained.training_spec_version,
        "training_spec_checksum": trained.training_spec_checksum,
        "training_policy_version": trained.training_policy_version,
        "feed_origin_only": trained.feed_origin_only,
        "categories": list(trained.feature_manifest.categories),
        "denominator_sources": list(trained.feature_manifest.denominator_sources),
        "agency_encoding": agency_encoding_embedded,
        "rejected_rows": dataclasses.asdict(rejected_rows),
    }
    raw_bytes = json.dumps(
        payload, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")
    sha256 = hashlib.sha256(raw_bytes).hexdigest()
    return ArtifactBytes(bytes=raw_bytes, sha256=sha256, release=release)
