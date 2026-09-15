"""RED — `ml_engine.training.dataset` D-5E-4 확장(`settlements.jsonl`·
`settlements_checksum`). `settlements_bytes=None`(기본값)은 5C-1 이전 동작을 그대로
보존한다(`test_dataset.py`는 무편집) — 이 파일은 D-5E-4 가 더하는 갈래만 다룬다."""

from __future__ import annotations

import hashlib
import json

from ml_engine.training.dataset import (
    DatasetReference,
    DatasetRejected,
    DatasetRejectionReason,
    LoadedDataset,
    RawSettlementRow,
    load_dataset,
)

# `tests/training/test_dataset.py`(out_of_scope, 무편집)와 같은 최소 manifest 형태를
# 독립 복제한다(cross-file import 대신 — pytest rootless 수집에서 `tests` 는 정식
# 패키지가 아니다, 작은 중복이 낫다는 기존 관례와 같은 갈래).
_MANIFEST_DICT: dict[str, object] = {
    "dataset_id": "ds-1",
    "sample_scope": "feed-origin-only",
    "feed_origin_only": True,
    "row_count": 0,
    "rows_checksum": hashlib.sha256(b"").hexdigest(),
    "opened_at_first": "2026-01-01T00:00:00+00:00",
    "opened_at_last": "2026-01-02T00:00:00+00:00",
    "feature_schema_version": "award-rate-features-v2",
}


def _build() -> tuple[bytes, bytes]:
    manifest_bytes = json.dumps(_MANIFEST_DICT).encode("utf-8")
    return manifest_bytes, b""


def _reference(manifest_bytes: bytes) -> DatasetReference:
    return DatasetReference(
        dataset_id="ds-1", manifest_checksum=hashlib.sha256(manifest_bytes).hexdigest()
    )


def _settlements_bytes(rows: list[dict[str, object]]) -> bytes:
    return "\n".join(json.dumps(row) for row in rows).encode("utf-8")


def _manifest_with_settlements(
    manifest_bytes: bytes, settlements_checksum: str
) -> bytes:
    manifest = json.loads(manifest_bytes)
    manifest["settlements_checksum"] = settlements_checksum
    return json.dumps(manifest).encode("utf-8")


def test_settlements_bytes_none_preserves_2c_behavior() -> None:
    """`settlements_bytes` 미전달은 5C-1 이전 호출자와 100% 같다 — `settlement_rows`는
    빈 튜플."""
    manifest_bytes, rows_bytes = _build()
    result = load_dataset(manifest_bytes, rows_bytes, _reference(manifest_bytes))
    assert isinstance(result, LoadedDataset)
    assert result.settlement_rows == ()


def test_settlements_absent_checksum_in_manifest_is_rejected() -> None:
    """settlements_bytes 가 주어졌는데 manifest 에 settlements_checksum 미공시 →
    SETTLEMENTS_CHECKSUM_MISMATCH(D-5E-4)."""
    manifest_bytes, rows_bytes = _build()
    result = load_dataset(
        manifest_bytes,
        rows_bytes,
        _reference(manifest_bytes),
        settlements_bytes=b"",
    )
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.SETTLEMENTS_CHECKSUM_MISMATCH


def test_settlements_round_trip() -> None:
    settlement_rows = [
        {"opened_at": "2026-01-01T00:00:00+00:00", "settled": True},
        {"opened_at": "2026-01-02T00:00:00+00:00", "settled": False},
    ]
    settlements_bytes = _settlements_bytes(settlement_rows)
    settlements_checksum = hashlib.sha256(settlements_bytes).hexdigest()

    manifest_bytes, rows_bytes = _build()
    manifest_bytes = _manifest_with_settlements(manifest_bytes, settlements_checksum)

    result = load_dataset(
        manifest_bytes,
        rows_bytes,
        _reference(manifest_bytes),
        settlements_bytes=settlements_bytes,
    )
    assert isinstance(result, LoadedDataset)
    assert len(result.settlement_rows) == 2
    assert result.settlement_rows[0] == RawSettlementRow(
        opened_at=result.settlement_rows[0].opened_at, settled=True
    )
    assert result.settlement_rows[1].settled is False


def test_settlements_checksum_mismatch_is_rejected() -> None:
    settlements_bytes = _settlements_bytes(
        [{"opened_at": "2026-01-01T00:00:00+00:00", "settled": True}]
    )
    manifest_bytes, rows_bytes = _build()
    manifest_bytes = _manifest_with_settlements(manifest_bytes, "0" * 64)

    result = load_dataset(
        manifest_bytes,
        rows_bytes,
        _reference(manifest_bytes),
        settlements_bytes=settlements_bytes,
    )
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.SETTLEMENTS_CHECKSUM_MISMATCH


def test_settlements_malformed_row_is_rejected() -> None:
    settlements_bytes = b'{"opened_at": "not-a-timestamp", "settled": true}\n'
    settlements_checksum = hashlib.sha256(settlements_bytes).hexdigest()
    manifest_bytes, rows_bytes = _build()
    manifest_bytes = _manifest_with_settlements(manifest_bytes, settlements_checksum)

    result = load_dataset(
        manifest_bytes,
        rows_bytes,
        _reference(manifest_bytes),
        settlements_bytes=settlements_bytes,
    )
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.UNREADABLE


def test_settlements_naive_datetime_is_rejected() -> None:
    """5C-1 `RawTrainingRow` 검증과 같은 규칙(UTC aware 필수, 설계 검토 우회 (17))."""
    settlements_bytes = b'{"opened_at": "2026-01-01T00:00:00", "settled": true}\n'
    settlements_checksum = hashlib.sha256(settlements_bytes).hexdigest()
    manifest_bytes, rows_bytes = _build()
    manifest_bytes = _manifest_with_settlements(manifest_bytes, settlements_checksum)

    result = load_dataset(
        manifest_bytes,
        rows_bytes,
        _reference(manifest_bytes),
        settlements_bytes=settlements_bytes,
    )
    assert isinstance(result, DatasetRejected)
    assert result.reason == DatasetRejectionReason.UNREADABLE
