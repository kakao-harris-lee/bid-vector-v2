"""RED — `ml_engine.features.manifest`(D-5B-5). canonical 결정성·키 순서 무관·float repr
민감·sha256 hex 64 소문자·`verify_manifest` 불일치."""

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
