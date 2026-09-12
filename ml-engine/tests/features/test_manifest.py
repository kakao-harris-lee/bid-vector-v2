"""RED — `ml_engine.features.manifest`(D-5B-5). canonical 결정성·키 순서 무관·float repr
민감·sha256 hex 64 소문자·`verify_manifest` 불일치·**입력 순서 무관 정렬 불변식**(verifier
r1 M-1 — 같은 내용을 다른 배열 순서로 들고 온 두 manifest 는 같은 checksum 을 내야 한다)."""

from __future__ import annotations

import hashlib

from ml_engine.features.manifest import (
    ChecksumMismatch,
    FeatureManifest,
    ManifestAgencyMean,
    ManifestCategoryMean,
    ManifestColumn,
    Verified,
    canonical_json,
    compute_checksum,
    verify_manifest,
)


def _manifest(*, global_mean: float = 0.55) -> FeatureManifest:
    return FeatureManifest(
        schema_version="award-rate-features-v2",
        columns=(
            ManifestColumn("category", "CATEGORICAL"),
            ManifestColumn("log_amount", "CONTINUOUS"),
        ),
        categories=("civil", "electrical"),
        denominator_sources=("CLEAN", "UNKNOWN"),
        agency_means=(
            ManifestAgencyMean(agency="a1", category="civil", mean=0.7, count=3),
            ManifestAgencyMean(agency="a2", category="electrical", mean=0.3, count=1),
        ),
        category_means=(
            ManifestCategoryMean(category="civil", mean=0.65),
            ManifestCategoryMean(category="electrical", mean=0.35),
        ),
        global_mean=global_mean,
        agency_prior_strength=12.0,
        category_prior_strength=40.0,
    )


def test_canonical_json_is_deterministic_for_same_manifest() -> None:
    manifest = _manifest()
    assert canonical_json(manifest) == canonical_json(manifest)


def test_canonical_json_no_whitespace_and_sorted_keys() -> None:
    payload = canonical_json(_manifest()).decode("utf-8")
    assert " " not in payload
    assert payload.index('"agency_means"') < payload.index(
        '"schema_version"'
    )  # 알파벳순


def test_canonical_json_sensitive_to_float_difference() -> None:
    baseline = canonical_json(_manifest(global_mean=0.55))
    nudged = canonical_json(_manifest(global_mean=0.55 + 1e-15))
    assert baseline != nudged


def test_checksum_is_sha256_hex_lowercase_64() -> None:
    checksum = compute_checksum(_manifest())
    assert len(checksum) == 64
    assert checksum == checksum.lower()
    int(checksum, 16)  # 유효 hex


def test_checksum_matches_direct_sha256_of_canonical_json() -> None:
    manifest = _manifest()
    expected = hashlib.sha256(canonical_json(manifest)).hexdigest()
    assert compute_checksum(manifest) == expected


def test_verify_manifest_accepts_matching_checksum() -> None:
    manifest = _manifest()
    checksum = compute_checksum(manifest)
    assert verify_manifest(manifest, checksum) == Verified()


def test_verify_manifest_rejects_mismatch() -> None:
    manifest = _manifest()
    result = verify_manifest(manifest, "0" * 64)
    assert isinstance(result, ChecksumMismatch)
    assert result.expected == "0" * 64
    assert result.actual == compute_checksum(manifest)


def test_manifest_sorts_agency_and_category_means_regardless_of_input_order() -> None:
    """verifier r1 M-1 — 같은 내용을 뒤집은 순서로 넣어도 같은 checksum."""
    forward = _manifest()
    reversed_input = FeatureManifest(
        schema_version=forward.schema_version,
        columns=forward.columns,
        categories=tuple(reversed(forward.categories)),
        denominator_sources=tuple(reversed(forward.denominator_sources)),
        agency_means=tuple(reversed(forward.agency_means)),
        category_means=tuple(reversed(forward.category_means)),
        global_mean=forward.global_mean,
        agency_prior_strength=forward.agency_prior_strength,
        category_prior_strength=forward.category_prior_strength,
    )
    assert compute_checksum(forward) == compute_checksum(reversed_input)


def test_manifest_post_init_stores_sorted_tuples() -> None:
    manifest = FeatureManifest(
        schema_version="award-rate-features-v2",
        columns=(ManifestColumn("category", "CATEGORICAL"),),
        categories=("electrical", "civil"),
        denominator_sources=("UNKNOWN", "CLEAN"),
        agency_means=(
            ManifestAgencyMean(agency="a2", category="civil", mean=0.3, count=1),
            ManifestAgencyMean(agency="a1", category="civil", mean=0.7, count=3),
        ),
        category_means=(
            ManifestCategoryMean(category="electrical", mean=0.35),
            ManifestCategoryMean(category="civil", mean=0.65),
        ),
        global_mean=0.5,
        agency_prior_strength=12.0,
        category_prior_strength=40.0,
    )
    assert manifest.categories == ("civil", "electrical")
    assert manifest.denominator_sources == ("CLEAN", "UNKNOWN")
    assert [entry.agency for entry in manifest.agency_means] == ["a1", "a2"]
    assert [entry.category for entry in manifest.category_means] == [
        "civil",
        "electrical",
    ]


def test_manifest_different_content_still_yields_different_checksum() -> None:
    """정렬이 fail-closed 방향을 뒤집지 않는다 — 실제로 다른 값은 여전히 다른 checksum."""
    baseline = _manifest()
    changed = _manifest(global_mean=0.99)
    assert compute_checksum(baseline) != compute_checksum(changed)
