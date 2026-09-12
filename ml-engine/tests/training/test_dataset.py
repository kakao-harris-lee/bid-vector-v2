"""RED — `ml_engine.training.dataset`(D-5C-8, scope ①). fail-closed 변조 표본 다섯
(manifest 1바이트·rows 1바이트·id·schema version·행 수) + 정상 왕복 1."""

from __future__ import annotations

import hashlib
import json

import pytest

from ml_engine.training.dataset import (
    DatasetManifestV1,
    DatasetReference,
    DatasetRejected,
    DatasetRejectionReason,
    LoadedDataset,
    load_dataset,
)

_MANIFEST_DICT = {
    "dataset_id": "ds-1",
    "sample_scope": "feed-origin-only",
    "feed_origin_only": True,
    "row_count": 2,
    "rows_checksum": "",  # filled in by _build below
    "opened_at_first": "2026-01-01T00:00:00+00:00",
    "opened_at_last": "2026-01-02T00:00:00+00:00",
    "feature_schema_version": "award-rate-features-v2",
}

_ROW = {
    "feature_inputs": {
        "baseAmount": {
            "value": {
                "amountWon": "100000000",
                "currency": "CURRENCY_KRW",
                "basis": "BASIS_BASE_AMOUNT",
                "provenance": "AMOUNT_PROVENANCE_KIND_PUBLISHED",
            }
        },
        "categoryCode": {"value": "civil"},
        "agencyId": {"value": "agency-1"},
        "baseAmountProvenanceLabel": {"value": "BASE_AMOUNT_PROVENANCE_LABEL_CLEAN"},
    },
    "label": 0.9,
    "opened_at": "2026-01-01T00:00:00+00:00",
    "stratum": "clean-base",
}


def _build(rows: list[dict[str, object]]) -> tuple[bytes, bytes]:
    rows_bytes = "\n".join(json.dumps(row) for row in rows).encode("utf-8")
    manifest = dict(_MANIFEST_DICT)
    manifest["row_count"] = len(rows)
    manifest["rows_checksum"] = hashlib.sha256(rows_bytes).hexdigest()
    manifest_bytes = json.dumps(manifest).encode("utf-8")
    return manifest_bytes, rows_bytes


def _reference(manifest_bytes: bytes, *, dataset_id: str = "ds-1") -> DatasetReference:
    return DatasetReference(
        dataset_id=dataset_id,
        manifest_checksum=hashlib.sha256(manifest_bytes).hexdigest(),
    )


def test_load_dataset_round_trip_succeeds() -> None:
    manifest_bytes, rows_bytes = _build([_ROW, _ROW])
    result = load_dataset(manifest_bytes, rows_bytes, _reference(manifest_bytes))
    assert isinstance(result, LoadedDataset)
    assert isinstance(result.manifest, DatasetManifestV1)
    assert len(result.raw_rows) == 2
    assert result.manifest.dataset_id == "ds-1"


def test_load_dataset_manifest_checksum_mismatch() -> None:
    manifest_bytes, rows_bytes = _build([_ROW])
    tampered_reference = DatasetReference(dataset_id="ds-1", manifest_checksum="0" * 64)
    result = load_dataset(manifest_bytes, rows_bytes, tampered_reference)
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.CHECKSUM_MISMATCH


def test_load_dataset_id_mismatch() -> None:
    manifest_bytes, rows_bytes = _build([_ROW])
    reference = _reference(manifest_bytes, dataset_id="other-dataset")
    result = load_dataset(manifest_bytes, rows_bytes, reference)
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.ID_MISMATCH


def test_load_dataset_rows_checksum_mismatch() -> None:
    manifest_bytes, rows_bytes = _build([_ROW])
    tampered_rows = rows_bytes + b"\n"
    result = load_dataset(manifest_bytes, tampered_rows, _reference(manifest_bytes))
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.ROWS_CHECKSUM_MISMATCH


def test_load_dataset_schema_unsupported() -> None:
    rows_bytes = "\n".join(json.dumps(row) for row in [_ROW]).encode("utf-8")
    manifest = dict(_MANIFEST_DICT)
    manifest["row_count"] = 1
    manifest["rows_checksum"] = hashlib.sha256(rows_bytes).hexdigest()
    manifest["feature_schema_version"] = "does-not-exist-v99"
    manifest_bytes = json.dumps(manifest).encode("utf-8")
    result = load_dataset(manifest_bytes, rows_bytes, _reference(manifest_bytes))
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.SCHEMA_UNSUPPORTED


def test_load_dataset_row_count_mismatch_is_unreadable() -> None:
    manifest_bytes, rows_bytes = _build([_ROW, _ROW])
    manifest = json.loads(manifest_bytes)
    manifest["row_count"] = 5
    tampered_manifest_bytes = json.dumps(manifest).encode("utf-8")
    result = load_dataset(
        tampered_manifest_bytes, rows_bytes, _reference(tampered_manifest_bytes)
    )
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.UNREADABLE


def test_load_dataset_manifest_missing_field_is_unreadable() -> None:
    manifest = dict(_MANIFEST_DICT)
    del manifest["sample_scope"]
    manifest_bytes = json.dumps(manifest).encode("utf-8")
    result = load_dataset(manifest_bytes, b"", _reference(manifest_bytes))
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.UNREADABLE


def test_load_dataset_manifest_empty_sample_scope_is_unreadable() -> None:
    manifest = dict(_MANIFEST_DICT)
    manifest["sample_scope"] = ""
    manifest["row_count"] = 0
    manifest["rows_checksum"] = hashlib.sha256(b"").hexdigest()
    manifest_bytes = json.dumps(manifest).encode("utf-8")
    result = load_dataset(manifest_bytes, b"", _reference(manifest_bytes))
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.UNREADABLE


def test_load_dataset_row_missing_tzinfo_is_unreadable() -> None:
    naive_row = dict(_ROW)
    naive_row["opened_at"] = "2026-01-01T00:00:00"
    manifest_bytes, rows_bytes = _build([naive_row])
    result = load_dataset(manifest_bytes, rows_bytes, _reference(manifest_bytes))
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.UNREADABLE


@pytest.mark.parametrize("field", ["stratum"])
def test_load_dataset_row_missing_required_field_is_unreadable(field: str) -> None:
    broken_row = dict(_ROW)
    del broken_row[field]
    manifest_bytes, rows_bytes = _build([broken_row])
    result = load_dataset(manifest_bytes, rows_bytes, _reference(manifest_bytes))
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.UNREADABLE
