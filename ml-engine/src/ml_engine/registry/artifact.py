"""`ml_engine.registry.artifact` — `ArtifactManifestV1` read model + `load_artifact`(신규,
scope.md ⑦, D-5D 계약 인수 — **5C 는 이 형태로 write 한다**).

legacy에는 checksum 검증이 없었다(digest §2 「checksum 대조 없음」 — `artifact_provider`는
버전 인지 캐시일 뿐이다) — 이 게이트는 신규다. `sha256(raw) != expected.artifact_checksum`
이면 `LoadedArtifact`를 **아예 만들지 않는다**(위협 모델 (h) — 로드 뒤 검사가 아니라
검증 통과 증거 타입(`_VerifiedBytes`, 모듈 private)만 파싱 함수가 받는다).

`sample_scope` 기본값 금지는 legacy `artifact_contracts.py:200-208`(피처 공간과 직교하는
축, 기본값을 주면 미학습 공종 가드가 조용히 열린다)와 같은 근거다 — 빈 문자열도
`__post_init__`에서 거부한다(설계 검토 (15)).

**verifier r1 M-1 반영**: `LoadedArtifact`는 `manifest`뿐 아니라 `_VerifiedBytes`(모듈
private)도 **필수 인자**로 받는다 — `LoadedArtifact(manifest)` 한 인자만으로는 mypy strict
가 인자 누락으로 거부한다. checksum 대조를 거치지 않은 manifest 로 `LoadedArtifact`를
직접 조립하는 경로가 성립하지 않도록 타입 시그니처 자체가 막는다((2b) 「이 함수만
만든다」의 실제 강제).

**구현 노트(설계 래칫 + `warn_unreachable`)**: JSON 페이로드 필드를 검증하는 함수는
`Any`/`dict[str, Any]`/`object` 매개변수(래칫이 막는 「약한 경계」)를 쓰지 않는다 — 값
타입을 `JsonValue`(재귀적 JSON 값 유니온: 스칼라 + `list[JsonValue]` + `dict[str,
JsonValue]`)로 표현해 `dict[str, JsonValue]`로 받는다. 이전 판(`JsonScalar`, 중첩 컨테이너
배제)은 `feature_names`/`release`/`reproducibility` 같은 중첩 필드가 사실은 list/dict를
담는데도 그 가능성을 타입에서 지워버려, `isinstance(x, list)` 분기가 mypy 관점에서
**도달 불가**(값 타입이 애초에 list일 수 없다고 선언했으므로)가 됐다(verifier r1 M-4,
`--warn-unreachable` 실측 — `feature_names` 목록 검사·`verify_feature_names` 호출부가
죽은 코드). `JsonValue`는 실제 JSON 값의 형태를 정직하게 나타내므로(진짜 JSON 값 전체
— `Any`처럼 아무 타입이나 넣을 수 있다는 뜻이 아니라 JSON 이 표현 가능한 값만이라는 뜻)
이 문제가 없다. 래칫은 `dict`/`Mapping`의 **값** 타입 이름만 보므로(`JsonValue`는 그 이름
목록에 없다) 약한 경계로도 잡히지 않는다.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from math import isfinite
from typing import Any

from ml_engine.features import (
    NameMismatch,
    UnsupportedSchema,
    resolve_schema,
    verify_feature_names,
)

type JsonValue = (
    str | int | float | bool | list[JsonValue] | dict[str, JsonValue] | None
)


@dataclass(frozen=True)
class ModelReleaseRef:
    """`load_artifact`가 검증할 기대값 — 승격 registry(5C/5E)에서 얻는다."""

    release_id: str
    artifact_checksum: str
    feature_schema_version: str
    feature_manifest_checksum: str


@dataclass(frozen=True)
class Reproducibility:
    """재현성 파라미터 — 값은 5C 소유(D-5D-4), 이 모듈은 대조만 한다."""

    seed: int
    num_threads: int
    deterministic: bool


@dataclass(frozen=True)
class ReleaseInfo:
    release_id: str
    artifact_checksum: str
    feature_schema_version: str
    code_version: str
    dataset_id: str


@dataclass(frozen=True)
class ArtifactManifestV1:
    """5D 소유 read model — 5C 가 이 형태로 write 한다(계약 인수). `sample_scope`는 빈
    문자열도 거부한다(기본값 금지, legacy `artifact_contracts.py:200-208`과 같은 근거)."""

    manifest_schema_version: str
    release: ReleaseInfo
    feature_manifest_checksum: str
    feature_names: tuple[str, ...]
    sample_scope: str
    residual_std: float
    training_row_count: int
    booster_model: str
    reproducibility: Reproducibility

    def __post_init__(self) -> None:
        if not self.sample_scope:
            raise ValueError(
                "ArtifactManifestV1.sample_scope 는 빈 문자열일 수 없다(선언 필수)"
            )


@dataclass(frozen=True)
class ArtifactRejected:
    """artifact 로드 실패 — 예외가 아니라 결과 타입, 사유 문자열 하나."""

    reason: str


_SUPPORTED_MANIFEST_SCHEMA_VERSIONS: frozenset[str] = frozenset(
    {"artifact-manifest-v1"}
)


@dataclass(frozen=True)
class _VerifiedBytes:
    """모듈 private — checksum 이 이미 대조된 raw bytes 만. 이 타입 없이는 아래
    파싱 함수에 도달할 수 없다(위협 모델 (h))."""

    raw: bytes


@dataclass(frozen=True)
class LoadedArtifact:
    """`load_artifact`만 만든다 — 검증을 통과한 manifest 를 나른다. `verified`(checksum
    대조 증거)를 필수 인자로 받아 `LoadedArtifact(manifest)` 한 인자 생성을 mypy strict
    가 거부하게 한다(verifier r1 M-1)."""

    manifest: ArtifactManifestV1
    verified: _VerifiedBytes


def _verify_checksum(
    raw: bytes, expected_checksum: str
) -> _VerifiedBytes | ArtifactRejected:
    actual = hashlib.sha256(raw).hexdigest()
    if actual != expected_checksum:
        return ArtifactRejected(
            f"artifact checksum 불일치: expected {expected_checksum!r}, got {actual!r}"
        )
    return _VerifiedBytes(raw)


def _parse_feature_fields(
    payload: dict[str, JsonValue], expected: ModelReleaseRef
) -> tuple[str, tuple[str, ...]] | ArtifactRejected:
    """`manifest_schema_version`·`feature_names`·`feature_manifest_checksum` 검증 —
    통과하면 `(manifest_schema_version, feature_names)`."""
    manifest_schema_version = payload.get("manifest_schema_version")
    if manifest_schema_version not in _SUPPORTED_MANIFEST_SCHEMA_VERSIONS:
        return ArtifactRejected(
            f"미지원 manifest_schema_version: {manifest_schema_version!r}"
        )

    feature_schema = resolve_schema(expected.feature_schema_version)
    if isinstance(feature_schema, UnsupportedSchema):
        return ArtifactRejected(
            f"미지원 feature_schema_version: {expected.feature_schema_version!r}"
        )

    feature_names_raw = payload.get("feature_names")
    if not isinstance(feature_names_raw, list):
        return ArtifactRejected("feature_names 는 문자열 목록이어야 한다")
    feature_names = [name for name in feature_names_raw if isinstance(name, str)]
    if len(feature_names) != len(feature_names_raw):
        return ArtifactRejected("feature_names 는 문자열 목록이어야 한다")
    name_check = verify_feature_names(feature_names, feature_schema)
    if isinstance(name_check, NameMismatch):
        return ArtifactRejected(
            f"feature_names 불일치: expected {name_check.expected!r}, got {name_check.actual!r}"
        )

    feature_manifest_checksum = payload.get("feature_manifest_checksum")
    if feature_manifest_checksum != expected.feature_manifest_checksum:
        return ArtifactRejected(
            "feature_manifest_checksum 불일치: expected "
            f"{expected.feature_manifest_checksum!r}, got {feature_manifest_checksum!r}"
        )
    assert isinstance(manifest_schema_version, str)  # 위에서 이미 _SUPPORTED_* 로 대조
    return manifest_schema_version, tuple(feature_names)


def _parse_release(
    payload: dict[str, JsonValue], expected: ModelReleaseRef
) -> ReleaseInfo | ArtifactRejected:
    """release 하위 객체 다섯 필드 — 전부 비어 있지 않은 문자열. `artifact_checksum`은
    자기서술 필드라 `expected`와의 등가성은 요구하지 않는다(raw bytes 전체 sha256 대조가
    이미 `_verify_checksum`에서 끝났다 — 자기 참조 회피, 모듈 docstring 참고)."""
    fields: dict[str, str] = {}
    for key in (
        "release_id",
        "artifact_checksum",
        "feature_schema_version",
        "code_version",
        "dataset_id",
    ):
        value = payload.get(key)
        if not isinstance(value, str) or not value:
            return ArtifactRejected(
                f"release.{key} 는 비어 있지 않은 문자열이어야 한다"
            )
        fields[key] = value
    if fields["release_id"] != expected.release_id:
        return ArtifactRejected(
            f"release_id 불일치: expected {expected.release_id!r}, got {fields['release_id']!r}"
        )
    return ReleaseInfo(**fields)


def _parse_reproducibility(
    payload: dict[str, JsonValue],
) -> Reproducibility | ArtifactRejected:
    seed = payload.get("seed")
    num_threads = payload.get("num_threads")
    deterministic = payload.get("deterministic")
    if not isinstance(seed, int) or isinstance(seed, bool):
        return ArtifactRejected("reproducibility.seed 는 정수여야 한다")
    if not isinstance(num_threads, int) or isinstance(num_threads, bool):
        return ArtifactRejected("reproducibility.num_threads 는 정수여야 한다")
    if not isinstance(deterministic, bool):
        return ArtifactRejected("reproducibility.deterministic 는 불리언이어야 한다")
    return Reproducibility(
        seed=seed, num_threads=num_threads, deterministic=deterministic
    )


def _parse_scalars(
    payload: dict[str, JsonValue],
) -> tuple[str, float, int, str] | ArtifactRejected:
    """`sample_scope`(빈 문자열도 거부)·`residual_std`(유한값만, verifier r1 L-3)·
    `training_row_count`·`booster_model` — 통과하면 그 넷의 튜플."""
    sample_scope = payload.get("sample_scope")
    if not isinstance(sample_scope, str) or not sample_scope:
        return ArtifactRejected(
            "sample_scope 는 비어 있지 않은 문자열이어야 한다(선언 필수)"
        )
    residual_std = payload.get("residual_std")
    training_row_count = payload.get("training_row_count")
    booster_model = payload.get("booster_model")
    if not isinstance(residual_std, int | float) or isinstance(residual_std, bool):
        return ArtifactRejected("residual_std 는 숫자여야 한다")
    if not isfinite(residual_std):
        # JSON 표준 리터럴 밖(`NaN`/`Infinity`) — Python `json.loads`는 기본으로 이를
        # 허용하지만(허용 확장), 망가진 artifact 를 결측 입력과 같은 사유로 접으면 안
        # 된다(verifier r1 L-3 — 이전 판은 이 값이 그대로 `predict.py`까지 흘러
        # `NON_FINITE_INPUT`으로 늦게 잡혔다. 여기서 즉시 거부한다).
        return ArtifactRejected(f"residual_std 는 유한해야 한다: {residual_std!r}")
    if not isinstance(training_row_count, int) or isinstance(training_row_count, bool):
        return ArtifactRejected("training_row_count 는 정수여야 한다")
    if not isinstance(booster_model, str) or not booster_model:
        return ArtifactRejected("booster_model 은 비어 있지 않은 문자열이어야 한다")
    return sample_scope, float(residual_std), training_row_count, booster_model


def _parse_manifest(
    verified: _VerifiedBytes, expected: ModelReleaseRef
) -> ArtifactManifestV1 | ArtifactRejected:
    try:
        payload: Any = json.loads(verified.raw)
    except json.JSONDecodeError as exc:
        return ArtifactRejected(f"artifact JSON 디코딩 실패: {exc}")
    if not isinstance(payload, dict):
        return ArtifactRejected("artifact 최상위는 객체여야 한다")

    feature_fields = _parse_feature_fields(payload, expected)
    if isinstance(feature_fields, ArtifactRejected):
        return feature_fields
    manifest_schema_version, feature_names = feature_fields

    release_payload = payload.get("release")
    if not isinstance(release_payload, dict):
        return ArtifactRejected("release 는 객체여야 한다")
    release = _parse_release(release_payload, expected)
    if isinstance(release, ArtifactRejected):
        return release

    reproducibility_payload = payload.get("reproducibility")
    if not isinstance(reproducibility_payload, dict):
        return ArtifactRejected(
            "reproducibility 는 객체여야 한다(재현성 파라미터 필수)"
        )
    reproducibility = _parse_reproducibility(reproducibility_payload)
    if isinstance(reproducibility, ArtifactRejected):
        return reproducibility

    scalars = _parse_scalars(payload)
    if isinstance(scalars, ArtifactRejected):
        return scalars
    sample_scope, residual_std, training_row_count, booster_model = scalars

    try:
        return ArtifactManifestV1(
            manifest_schema_version=manifest_schema_version,
            release=release,
            feature_manifest_checksum=str(payload["feature_manifest_checksum"]),
            feature_names=feature_names,
            sample_scope=sample_scope,
            residual_std=residual_std,
            training_row_count=training_row_count,
            booster_model=booster_model,
            reproducibility=reproducibility,
        )
    except ValueError as exc:
        return ArtifactRejected(str(exc))


def load_artifact(
    raw: bytes, expected: ModelReleaseRef
) -> LoadedArtifact | ArtifactRejected:
    """checksum 대조(fail-closed, 실패 시 객체 미생성) → schema version·feature_names·
    feature_manifest_checksum·sample_scope·reproducibility 검증 → `LoadedArtifact`. 이
    함수만 `LoadedArtifact`를 만든다(검증 증거 `_VerifiedBytes`를 필수 인자로 전달)."""
    verified = _verify_checksum(raw, expected.artifact_checksum)
    if isinstance(verified, ArtifactRejected):
        return verified
    manifest = _parse_manifest(verified, expected)
    if isinstance(manifest, ArtifactRejected):
        return manifest
    return LoadedArtifact(manifest, verified)
