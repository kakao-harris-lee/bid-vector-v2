"""`ml_engine.features.manifest` — feature manifest + checksum(신규, D-5B-5).

legacy에는 feature schema manifest·checksum이 없었다(조사 §c-5 「dataset manifest/checksum
없음」·「artifact checksum은 release manifest 전용, 추론 경로에 없다」) — 이 모듈은 이식이
아니라 신규 작성이다. `canonical_json`은 ADR 0003·2C ②⑦(sha256 hex 소문자 64자)과 같은
형태를 feature manifest에 적용한다. **artifact/dataset manifest 실물은 5C 소관** — 이
모듈은 `feature_manifest_checksum`으로 5C의 artifact manifest 안에 실릴 값 하나를 낸다.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass


@dataclass(frozen=True)
class ManifestColumn:
    name: str
    kind: str


@dataclass(frozen=True)
class ManifestAgencyMean:
    agency: str
    category: str
    mean: float
    count: int


@dataclass(frozen=True)
class ManifestCategoryMean:
    category: str
    mean: float


@dataclass(frozen=True)
class FeatureManifest:
    """feature manifest 내용 — 전부 canonical_json 이 직렬화할 수 있는 원시 구조만 담는다
    (dict 키에 tuple을 쓰지 않는다, JSON 은 그런 키를 표현할 수 없다).

    verifier r1 M-1 — `categories`·`denominator_sources`·`agency_means`·`category_means`는
    생성 시점에 **키 기준으로 정렬**한다(`Vocabulary`와 같은 불변식). 정렬하지 않으면
    같은 내용을 다른 순서로 들고 온 두 manifest가 다른 checksum을 낸다 — 5C가 dict
    삽입 순서대로 이 필드를 채우면 같은 학습 결과가 실행마다 다른 checksum을 내게 된다.
    `columns`는 정렬하지 않는다 — 그 순서 자체가 feature schema 의 열 순서라는 의미를
    나른다(임의 순서가 아니다)."""

    schema_version: str
    columns: tuple[ManifestColumn, ...]
    categories: tuple[str, ...]
    denominator_sources: tuple[str, ...]
    agency_means: tuple[ManifestAgencyMean, ...]
    category_means: tuple[ManifestCategoryMean, ...]
    global_mean: float
    agency_prior_strength: float
    category_prior_strength: float

    def __post_init__(self) -> None:
        object.__setattr__(self, "categories", tuple(sorted(self.categories)))
        object.__setattr__(
            self, "denominator_sources", tuple(sorted(self.denominator_sources))
        )
        object.__setattr__(
            self,
            "agency_means",
            tuple(
                sorted(
                    self.agency_means, key=lambda entry: (entry.agency, entry.category)
                )
            ),
        )
        object.__setattr__(
            self,
            "category_means",
            tuple(sorted(self.category_means, key=lambda entry: entry.category)),
        )


def canonical_json(manifest: FeatureManifest) -> bytes:
    """키 정렬·구분자 `(",", ":")`·float 는 `repr`(Python `json`의 기본 float 인코딩이
    이미 `float.__repr__`을 쓴다 — shortest round-trip repr, Python 3.12 결정적).

    직렬화 대상 dict 를 별도 헬퍼 함수의 반환 타입으로 뽑지 않는다 — `dict[str, object]`가
    함수 시그니처에 나타나면 설계 래칫의 약한 경계 판정에 걸린다(로컬 변수는 대상이
    아니다, 함수 매개변수·반환 애너테이션만 본다). 이 함수 안 지역 변수로만 둔다.
    """
    manifest_as_json_value = {
        "schema_version": manifest.schema_version,
        "columns": [
            {"name": column.name, "kind": column.kind} for column in manifest.columns
        ],
        "categories": list(manifest.categories),
        "denominator_sources": list(manifest.denominator_sources),
        "agency_means": [
            {
                "agency": entry.agency,
                "category": entry.category,
                "mean": entry.mean,
                "count": entry.count,
            }
            for entry in manifest.agency_means
        ],
        "category_means": [
            {"category": entry.category, "mean": entry.mean}
            for entry in manifest.category_means
        ],
        "global_mean": manifest.global_mean,
        "agency_prior_strength": manifest.agency_prior_strength,
        "category_prior_strength": manifest.category_prior_strength,
    }
    payload = json.dumps(manifest_as_json_value, sort_keys=True, separators=(",", ":"))
    return payload.encode("utf-8")


def compute_checksum(manifest: FeatureManifest) -> str:
    """sha256 hex 소문자 64자(2C ②⑦과 같은 형태)."""
    return hashlib.sha256(canonical_json(manifest)).hexdigest()


@dataclass(frozen=True)
class Verified:
    pass


@dataclass(frozen=True)
class ChecksumMismatch:
    expected: str
    actual: str


def verify_manifest(
    manifest: FeatureManifest, expected_checksum: str
) -> Verified | ChecksumMismatch:
    actual = compute_checksum(manifest)
    return (
        Verified()
        if actual == expected_checksum
        else ChecksumMismatch(expected_checksum, actual)
    )
