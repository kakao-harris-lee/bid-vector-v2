"""Reuse: bid-vector/app/services/ml_training/award_rate_gbm.py@ed4b06c

`ml_engine.training.artifact_writer` — `TrainedArtifact` → 최종 canonical JSON 바이트
(scope ⑧). legacy `_assemble_artifact`(pydantic 계약 조립)를 이어받되, 계약 모델이
V2 결과 타입으로 바뀌었다. **D-5C-9** — Python 타입 `ReleaseIdentity`(4필드)는
`artifact_checksum`을 갖지 않는다(자기참조 금지). wire `ArtifactReference.release.
artifact_checksum`(= 이 함수가 낸 `ArtifactBytes.sha256`, 최종 bytes 전체의 sha256)은
호출자가 별도로 나른다.

**D-5C-9b(계약 갱신 이력 2026-09-13)** — 그러나 바이트 **안**의 JSON `release.
artifact_checksum` 자리는 비워두지 않는다. 5D read model(`registry/artifact.py`
`_parse_release`)이 그 키를 **비어 있지 않은 문자열**로 요구하기 때문이다(등가성은
보지 않는 자기서술 필드, 5D 문면). 그래서 이 자리에는 **「그 필드를 빈 문자열 `""`로
둔 canonical bytes 의 sha256」**(두 단계 직렬화 — legacy manifest `payload_sha256`과
같은 형태, 결정적)을 채운다. 이 값은 `ArtifactBytes.sha256`(최종 bytes 전체의
sha256)과 **다르다** — 같은 이름·다른 정의(`OPEN-5C-ARTIFACT-CHECKSUM-PLACEMENT`,
`checklist.md` 참고).

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
from typing import TypedDict

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

type _JsonValue = (
    str | int | float | bool | list[_JsonValue] | dict[str, _JsonValue] | None
)
"""`json.loads`가 낸 재귀 JSON 값 — `object` 대신 named alias 를 써 설계 래칫의 약한
경계 판정을 피한다(registry/artifact.py `JsonValue`와 같은 근거, import 는 하지 않는다
— training → registry 의존은 scope 밖)."""


class _ReleasePayload(TypedDict):
    """바이트 안 `release` 하위 객체 — D-5C-9b `artifact_checksum`(블랭크 canonical
    bytes 의 sha256)을 포함한 다섯 필드. named TypedDict 로 두어 함수 경계에
    `dict[str, Any]`(설계 래칫이 막는 약한 경계)가 나타나지 않게 한다."""

    release_id: str
    artifact_checksum: str
    feature_schema_version: str
    code_version: str
    dataset_id: str


class _ArtifactPayload(TypedDict):
    """`write_artifact`의 canonical JSON payload 전체 — 함수 경계(파라미터·반환)에
    구조화된 이름을 두어 `dict[str, object]` 약한 경계를 피한다(같은 근거는
    `registry/artifact.py`의 `JsonValue`)."""

    manifest_schema_version: str
    release: _ReleasePayload
    feature_manifest_checksum: str
    feature_names: list[str]
    sample_scope: str
    residual_std: float
    training_row_count: int
    booster_model: str
    reproducibility: dict[str, object]
    training_spec_version: str
    training_spec_checksum: str
    training_policy_version: str
    feed_origin_only: bool
    categories: list[str]
    denominator_sources: list[str]
    agency_encoding: _JsonValue
    rejected_rows: dict[str, object]


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


def _canonical_bytes(payload: _ArtifactPayload) -> bytes:
    return json.dumps(
        payload, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def _assemble_payload(
    trained: TrainedArtifact,
    release: ReleaseIdentity,
    feature_manifest_checksum: str,
    agency_encoding_embedded: _JsonValue,
    rejected_rows: _RejectedRowsJson,
) -> _ArtifactPayload:
    """`release.artifact_checksum` 자리를 빈 문자열로 채운 payload(D-5C-9b 1단계) —
    `_fill_self_described_artifact_checksum`이 채운다."""
    release_payload: _ReleasePayload = {
        "release_id": release.release_id,
        "artifact_checksum": "",
        "feature_schema_version": release.feature_schema_version,
        "code_version": release.code_version,
        "dataset_id": release.dataset_id,
    }
    return {
        "manifest_schema_version": _MANIFEST_SCHEMA_VERSION,
        "release": release_payload,
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


def _fill_self_described_artifact_checksum(payload: _ArtifactPayload) -> None:
    """D-5C-9b 2단계 — `release.artifact_checksum`을 블랭크 canonical bytes 의 sha256
    으로 채운다(`payload["release"]`를 in-place 로 수정, 자기참조 회피)."""
    blank_bytes = _canonical_bytes(payload)
    payload["release"]["artifact_checksum"] = hashlib.sha256(blank_bytes).hexdigest()


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
    payload = _assemble_payload(
        trained,
        release,
        feature_manifest_checksum,
        agency_encoding_embedded,
        _serialize_rejected_rows(trained.rejected_rows),
    )
    _fill_self_described_artifact_checksum(payload)

    raw_bytes = _canonical_bytes(payload)
    sha256 = hashlib.sha256(raw_bytes).hexdigest()
    return ArtifactBytes(bytes=raw_bytes, sha256=sha256, release=release)
