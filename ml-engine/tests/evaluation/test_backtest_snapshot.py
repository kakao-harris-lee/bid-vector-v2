"""RED — M6/6G D-6G-2. 실험 입력은 **불변 스냅숏**이고, 그 판독은 조용한 fallback 없이
fail-closed 다. 이 test 가 잠그는 것 넷:

1. **투찰 시점 / 개찰 시점 분리** — 한 행이 `notice`(투찰 시점에 알 수 있는 것)와
   `outcome`(개찰로 드러나는 것) 둘로 갈린다. 누출 금지(위협 모델 ①)를 문서가 아니라
   **타입**으로 닫는 자리다.
2. **미지 키 거부** — 스냅숏에 상호·사업자번호가 실리는 경로를 구조로 막는다(D-6G-10).
3. **manifest 대조** — row 수와 `rows.jsonl` 의 sha256 이 맞지 않으면 거부(재현성).
4. **닫힌 어휘** — 업무 대분류가 셋 밖이면 행을 조용히 버리지 않고 스냅숏을 거부한다.
"""

from __future__ import annotations

import hashlib
import json
from typing import Any

import pytest
from _backtest_support import (
    manifest_bytes,
    notice_key_hash,
    row_payload,
    rows_bytes,
)

from ml_engine.evaluation.backtest.snapshot import (
    BusinessCategory,
    LoadedSnapshot,
    SnapshotRejected,
    SnapshotRejectionReason,
    load_snapshot,
)


def _load(payloads: list[dict[str, Any]], **manifest_kwargs: Any) -> object:
    rows = rows_bytes(payloads)
    return load_snapshot(manifest_bytes(rows, **manifest_kwargs), rows)


def _loaded(payloads: list[dict[str, Any]]) -> LoadedSnapshot:
    result = _load(payloads)
    assert isinstance(result, LoadedSnapshot), result
    return result


def test_valid_snapshot_parses_notice_and_outcome_halves() -> None:
    snapshot = _loaded([row_payload("n-1"), row_payload("n-2")])
    assert snapshot.snapshot_id == "snapshot-test-1"
    assert len(snapshot.rows) == 2
    first = snapshot.rows[0]
    assert first.notice.notice_key_hash == notice_key_hash("n-1")
    assert first.notice.category is BusinessCategory.SERVICE
    assert first.notice.base_amount == pytest.approx(1_000_000_000.0)
    assert first.notice.floor_rate == pytest.approx(0.87745)
    assert len(first.outcome.reserve_prices) == 15
    assert len(first.outcome.drawn_serial_numbers) == 4
    assert [row.rank for row in first.outcome.bidder_rows] == [1, 2, 3, 4]


def test_notice_half_carries_no_opening_result_attribute() -> None:
    """누출 금지를 타입으로 — 전략에 넘어가는 `notice` 에는 개찰 결과 이름이 없다."""
    notice = _loaded([row_payload("n-1")]).rows[0].notice
    forbidden = {
        "planned_price",
        "reserve_prices",
        "drawn_serial_numbers",
        "bidder_rows",
        "participant_count",
        "opened_on",
    }
    assert not forbidden & set(vars(notice))


def test_snapshot_checksum_is_the_sha256_of_the_rows_bytes() -> None:
    rows = rows_bytes([row_payload("n-1")])
    loaded = load_snapshot(manifest_bytes(rows), rows)
    assert isinstance(loaded, LoadedSnapshot)
    assert loaded.rows_sha256 == hashlib.sha256(rows).hexdigest()


@pytest.mark.parametrize(
    ("manifest_kwargs", "reason"),
    [
        ({"row_count": 99}, SnapshotRejectionReason.ROW_COUNT_MISMATCH),
        ({"rows_sha256": "a" * 64}, SnapshotRejectionReason.CHECKSUM_MISMATCH),
        (
            {"schema_version": "snapshot-v2"},
            SnapshotRejectionReason.UNSUPPORTED_SCHEMA_VERSION,
        ),
    ],
)
def test_manifest_mismatch_is_rejected(
    manifest_kwargs: dict[str, Any], reason: SnapshotRejectionReason
) -> None:
    rejected = _load([row_payload("n-1")], **manifest_kwargs)
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is reason


def test_unknown_top_level_row_key_is_rejected() -> None:
    payload = row_payload("n-1")
    payload["extra"] = 1
    rejected = _load([payload])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.UNKNOWN_FIELD


def test_unknown_notice_key_is_rejected_so_identifiers_cannot_ride_along() -> None:
    """상호가 실려 오면 행이 통과하지 않는다 — 허용 목록이 아니라 전수 대조."""
    payload = row_payload("n-1")
    payload["notice"]["corp_name"] = "가상건설"
    rejected = _load([payload])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.UNKNOWN_FIELD
    assert "corp_name" in rejected.detail


def test_unknown_bidder_row_key_is_rejected() -> None:
    payload = row_payload("n-1")
    payload["outcome"]["bidder_rows"][0]["business_number"] = "123-45-67890"
    rejected = _load([payload])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.UNKNOWN_FIELD


def test_unknown_business_category_is_rejected_not_silently_dropped() -> None:
    rejected = _load([row_payload("n-1", notice_category="FOREIGN")])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.UNKNOWN_CATEGORY


def test_missing_field_is_rejected() -> None:
    payload = row_payload("n-1")
    del payload["notice"]["base_amount"]
    rejected = _load([payload])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.MISSING_FIELD


@pytest.mark.parametrize(
    "mutation",
    [
        {"notice_base_amount": "1000"},
        {"notice_noticed_on": "2026-13-01"},
        {"notice_bid_close_at": "not-a-timestamp"},
        {"notice_notice_ordinal": "1"},
        {"notice_is_local_government": "false"},
    ],
)
def test_wrong_typed_field_is_rejected(mutation: dict[str, Any]) -> None:
    rejected = _load([row_payload("n-1", **mutation)])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.INVALID_VALUE


def test_non_finite_number_is_rejected() -> None:
    rows = rows_bytes([row_payload("n-1")]).replace(
        b'"base_amount": 1000000000.0', b'"base_amount": NaN'
    )
    rejected = load_snapshot(manifest_bytes(rows), rows)
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.INVALID_VALUE


def test_malformed_json_line_is_rejected() -> None:
    rows = rows_bytes([row_payload("n-1")])[:-10]
    rejected = load_snapshot(manifest_bytes(rows), rows)
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.MALFORMED_JSON


def test_duplicate_notice_key_hash_is_rejected() -> None:
    rejected = _load([row_payload("n-1"), row_payload("n-1")])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.DUPLICATE_NOTICE


def test_malformed_manifest_is_rejected() -> None:
    rows = rows_bytes([row_payload("n-1")])
    rejected = load_snapshot(b"{not json", rows)
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.MALFORMED_JSON


def test_rows_are_sorted_by_notice_key_hash_for_reproducibility() -> None:
    """같은 스냅숏이면 같은 판정 — 행 순서가 입력 파일 순서에 끌려다니지 않는다."""
    forward = _loaded([row_payload("n-1"), row_payload("n-2"), row_payload("n-3")])
    backward = _loaded([row_payload("n-3"), row_payload("n-1"), row_payload("n-2")])
    assert [row.notice.notice_key_hash for row in forward.rows] == [
        row.notice.notice_key_hash for row in backward.rows
    ]
    assert forward.rows_sha256 != backward.rows_sha256


def test_empty_snapshot_is_rejected() -> None:
    rejected = load_snapshot(manifest_bytes(b""), b"")
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.EMPTY


def test_a_value_is_parsed_with_its_own_open_timestamp() -> None:
    payload = row_payload(
        "n-1",
        notice_category="CONSTRUCTION",
        notice_a_value={"total": 873_130_896.0, "open_at": "2026-06-05T16:10:19+09:00"},
    )
    snapshot = _loaded([payload])
    a_value = snapshot.rows[0].notice.a_value
    assert a_value is not None
    assert a_value.total == pytest.approx(873_130_896.0)
    assert a_value.open_at.isoformat() == "2026-06-05T16:10:19+09:00"


def test_manifest_period_is_carried_for_the_verdict_record() -> None:
    snapshot = _loaded([row_payload("n-1")])
    assert snapshot.period_start.isoformat() == "2026-06-01"
    assert snapshot.period_end.isoformat() == "2026-08-31"
    assert snapshot.sample_list_sha256 == "0" * 64


def test_rejection_detail_never_repeats_a_notice_key_hash() -> None:
    """거부 사유가 공고 식별자를 실어 나르지 않는다 — 보고에 식별자가 없다(D-6G-9)."""
    payload = row_payload("n-1")
    payload["notice"]["corp_name"] = "가상건설"
    rejected = _load([payload])
    assert isinstance(rejected, SnapshotRejected)
    assert notice_key_hash("n-1") not in rejected.detail


def test_loader_accepts_bytes_not_paths() -> None:
    """파일 읽기는 `ml_engine.adapters` 의 몫 — 이 커널은 바이트만 본다(층 경계)."""
    rows = rows_bytes([row_payload("n-1")])
    assert isinstance(load_snapshot(manifest_bytes(rows), rows), LoadedSnapshot)
    assert isinstance(
        load_snapshot(json.dumps({"schema_version": "snapshot-v1"}).encode(), rows),
        SnapshotRejected,
    )
