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
    sample_list_bytes,
)

from ml_engine.evaluation.backtest.observations import (
    BusinessCategory,
    LoadedSnapshot,
    SnapshotRejected,
)
from ml_engine.evaluation.backtest.sample_list import parse_sample_list
from ml_engine.evaluation.backtest.snapshot import (
    SnapshotRejectionReason,
    load_snapshot,
)


def _load(payloads: list[dict[str, Any]], **manifest_kwargs: Any) -> object:
    rows = rows_bytes(payloads)
    return load_snapshot(
        manifest_bytes(rows, **manifest_kwargs), rows, sample_list_bytes(rows)
    )


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
    loaded = load_snapshot(manifest_bytes(rows), rows, sample_list_bytes(rows))
    assert isinstance(loaded, LoadedSnapshot)
    assert loaded.rows_sha256 == hashlib.sha256(rows).hexdigest()


@pytest.mark.parametrize(
    ("manifest_kwargs", "reason"),
    [
        ({"row_count": 99}, SnapshotRejectionReason.ROW_COUNT_MISMATCH),
        ({"rows_sha256": "a" * 64}, SnapshotRejectionReason.CHECKSUM_MISMATCH),
        (
            {"schema_version": "snapshot-v3"},
            SnapshotRejectionReason.UNSUPPORTED_SCHEMA_VERSION,
        ),
        (
            {"schema_version": "snapshot-v5"},
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
        {"notice_notice_ordinal": True},
    ],
)
def test_wrong_typed_field_is_rejected(mutation: dict[str, Any]) -> None:
    rejected = _load([row_payload("n-1", **mutation)])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.INVALID_VALUE


def test_non_finite_number_is_rejected() -> None:
    rows = rows_bytes([row_payload("n-1")]).replace(
        b'"floor_rate": 0.87745', b'"floor_rate": NaN'
    )
    rejected = load_snapshot(manifest_bytes(rows), rows, sample_list_bytes(rows))
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.INVALID_VALUE


def test_malformed_json_line_is_rejected() -> None:
    rows = rows_bytes([row_payload("n-1")])[:-10]
    rejected = load_snapshot(manifest_bytes(rows), rows, sample_list_bytes(rows))
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.MALFORMED_JSON


def test_duplicate_notice_key_hash_is_rejected() -> None:
    rejected = _load([row_payload("n-1"), row_payload("n-1")])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.DUPLICATE_NOTICE


def test_malformed_manifest_is_rejected() -> None:
    rows = rows_bytes([row_payload("n-1")])
    rejected = load_snapshot(b"{not json", rows, sample_list_bytes(rows))
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
    rejected = load_snapshot(manifest_bytes(b""), b"", b"")
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.EMPTY


def test_a_value_is_parsed_with_its_own_open_timestamp() -> None:
    payload = row_payload(
        "n-1",
        notice_category="CONSTRUCTION",
        notice_a_value={
            "total": 873_130_896,
            "open_at": "2026-06-05T16:10:19+09:00",
            "standard_market_price_applicable": True,
        },
    )
    snapshot = _loaded([payload])
    a_value = snapshot.rows[0].notice.a_value
    assert a_value is not None
    assert a_value.total == pytest.approx(873_130_896.0)
    assert a_value.open_at.isoformat() == "2026-06-05T16:10:19+09:00"
    assert a_value.standard_market_price_applicable is True


def test_a_value_standard_market_price_predicate_accepts_null() -> None:
    """`Y`/`N` 밖의 값이나 부재는 `null`(판정 불가) — 추출이 지어내지 않는다."""
    payload = row_payload(
        "n-1",
        notice_category="CONSTRUCTION",
        notice_a_value={
            "total": 1,
            "open_at": "2026-06-05T09:00:00+09:00",
            "standard_market_price_applicable": None,
        },
    )
    a_value = _loaded([payload]).rows[0].notice.a_value
    assert a_value is not None
    assert a_value.standard_market_price_applicable is None


def test_a_value_rejects_an_unknown_key() -> None:
    payload = row_payload(
        "n-1",
        notice_category="CONSTRUCTION",
        notice_a_value={
            "total": 1,
            "open_at": "2026-06-05T09:00:00+09:00",
            "standard_market_price_applicable": None,
            "standard_market_price_amount": 1,
        },
    )
    rejected = _load([payload])
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.UNKNOWN_FIELD


def test_manifest_period_is_carried_for_the_verdict_record() -> None:
    snapshot = _loaded([row_payload("n-1")])
    # 기간은 **행들의 개찰일 범위**다(D-6G-32) — manifest 가 적은 값을 그대로 옮기지
    # 않고 행에서 재계산해 대조하므로, 이 값은 fixture 의 개찰일과 같아야 한다.
    assert snapshot.period_start == snapshot.rows[0].outcome.opened_on
    assert snapshot.period_end == snapshot.rows[0].outcome.opened_on
    rows = rows_bytes([row_payload("n-1")])
    assert (
        snapshot.sample_list_sha256
        == hashlib.sha256(sample_list_bytes(rows)).hexdigest()
    )
    assert snapshot.sample_size == len(snapshot.rows)


@pytest.mark.parametrize(
    "manifest_kwargs",
    [
        {"period_start": "2020-01-01"},
        {"period_end": "2099-12-31"},
        {"period_start": "2020-01-01", "period_end": "2099-12-31"},
    ],
)
def test_manifest_period_must_match_the_rows(
    manifest_kwargs: dict[str, Any],
) -> None:
    """D-6G-32 「manifest 가 사실을 말하는가」 — 기간은 생산 쪽이 적는 값이고 행과
    묶이는 자리가 없으면 아무 기간이나 적을 수 있다(표본 목록 해시와 같은 계열).
    생산 계약이 「기간 = 행들의 개찰일 범위」이므로 **일치**를 요구한다."""
    rows = rows_bytes([row_payload("n-1"), row_payload("n-2")])
    rejected = load_snapshot(
        manifest_bytes(rows, **manifest_kwargs), rows, sample_list_bytes(rows)
    )
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.PERIOD_MISMATCH


def test_sample_list_hash_is_the_file_bytes_not_a_row_recompute() -> None:
    """v4(D-6G-39) — `sample_list_sha256` 은 **`sample-list.tsv` 바이트**의 해시다.
    v3 까지는 행에서 역산한 값이라 판독이 같은 식으로 다시 계산해 맞췄고, 그 대조는
    정의상 언제나 참이었다(순환). 이제 파일이 정본이라 **실패할 수 있다**."""
    rows = rows_bytes([row_payload("n-1"), row_payload("n-2")])
    listing = sample_list_bytes(rows)
    rejected = load_snapshot(
        manifest_bytes(rows, sample_list_sha256="b" * 64), rows, listing
    )
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.CHECKSUM_MISMATCH


def test_rows_outside_the_sample_list_are_rejected() -> None:
    """⑵ 모든 행의 키가 파일의 키 집합 **안**이어야 한다(우회 ⑦) — 표본 밖 공고를
    끼워 넣으면 거부다."""
    sampled = rows_bytes([row_payload("n-1")])
    listing = sample_list_bytes(sampled)
    rows = rows_bytes([row_payload("n-1"), row_payload("intruder")])
    rejected = load_snapshot(
        manifest_bytes(rows, sample_list=listing, sample_size=1), rows, listing
    )
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.SAMPLE_LIST_MISMATCH


def test_rows_may_be_a_strict_subset_of_the_sample_list() -> None:
    """행이 표본의 **진부분집합인 것은 정상**이다 — 상세를 못 받았거나 공고 canonical
    이 없는 표본이 있다. 그 차이는 manifest 의 두 계수로 설명된다."""
    rows = rows_bytes([row_payload("n-1")])
    listing = sample_list_bytes(rows, extra_keys=("f" * 64, "e" * 64))
    loaded = load_snapshot(
        manifest_bytes(
            rows,
            sample_list=listing,
            sampled_without_detail=1,
            sampled_without_notice=1,
        ),
        rows,
        listing,
    )
    assert isinstance(loaded, LoadedSnapshot), loaded
    assert loaded.sample_size == 3
    assert loaded.sampled_without_detail == 1
    assert loaded.sampled_without_notice == 1


def test_sample_accounting_identity_must_close() -> None:
    """⑶ `sample_size == row_count + 상세없음 + 공고없음`. 표본 하나하나가 행이
    되었거나 되지 못한 사유로 계수된다 — 깨지면 구조 실패다.

    **앞 대조와 겹치지 않게 세운 판이다.** `sample_size` 를 파일의 키 수와 **맞춰**
    둔다(2 == 2) — 그래야 「파일 키 수 != sample_size」 대조가 먼저 걸리지 않고, 남는
    것이 항등식뿐이다. 이 자리를 맞추기 전에는 항등식을 통째로 지워도 test 가 초록
    이었다(변이 실측에서 살아남았다)."""
    rows = rows_bytes([row_payload("n-1")])
    listing = sample_list_bytes(rows, extra_keys=("f" * 64,))
    rejected = load_snapshot(
        manifest_bytes(
            rows,
            sample_list=listing,
            sample_size=2,
            sampled_without_detail=0,
            sampled_without_notice=0,
        ),
        rows,
        listing,
    )
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.SAMPLE_COUNT_MISMATCH


def test_declared_sample_size_must_match_the_file_it_points_at() -> None:
    """⑵ `sample_size` 는 **표본 목록 파일의 키 수**와 같아야 한다.

    항등식만으로는 못 막는 우회가 있다: 표본을 600건 뽑아 놓고 **남은 행만큼으로
    선언을 줄이면**(`sample_size = 행 수`, 결측 계수 0) 항등식은 깨끗하게 닫힌다.
    그러면 「결과를 보기 전에 표본이 확정됐다」가 사후 선택으로 무너지는데도 통과한다.
    파일이 표본의 정본이므로, 선언은 **파일**과 맞아야 한다.

    항등식 test 와 일부러 반대 판이다 — 저쪽은 파일 수에 선언을 맞추고 항등식을
    깨고, 이쪽은 항등식을 닫고 파일 수를 어긋내 대조 둘이 각각 서는지를 가른다."""
    rows = rows_bytes([row_payload("n-1")])
    listing = sample_list_bytes(rows, extra_keys=("f" * 64, "e" * 64))
    rejected = load_snapshot(
        manifest_bytes(
            rows,
            sample_list=listing,
            sample_size=1,
            sampled_without_detail=0,
            sampled_without_notice=0,
        ),
        rows,
        listing,
    )
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.SAMPLE_COUNT_MISMATCH


@pytest.mark.parametrize(
    "listing",
    [
        b"deadbeef\tSERVICE\t2026-W25\n",
        (("a" * 64) + "\tSERVICE\n"),
        (("b" * 64) + "\tSERVICE\t2026-W25"),
        (("c" * 64) + "\tSERVICE\t2026-W25\n" + ("a" * 64) + "\tSERVICE\t2026-W25\n"),
        (("A" * 64) + "\tSERVICE\t2026-W25\n"),
    ],
)
def test_malformed_sample_list_is_rejected(listing: bytes | str) -> None:
    """짧은 해시 · 칸 수 · 끝 개행 · 오름차순 · 대문자 hex — 형태가 다르면 두 레인이
    다른 파일을 본다는 뜻이라 거부한다. 정렬을 대신 고쳐 주지 않는다(파일이 정본이고
    그 바이트가 해시 대상이다).

    사유는 **정확히 하나**를 요구한다. 두 사유의 합집합으로 느슨하게 적었더니 정렬
    검사를 통째로 지워도 초록이었다 — 형태 검사가 빠진 자리를 계수 대조가 받아
    「어쨌든 거부됐다」로 덮었기 때문이다(변이 실측에서 살아남았다). 형태 위반은
    형태 사유로 서야 한다."""
    raw = listing if isinstance(listing, bytes) else listing.encode("utf-8")
    rows = rows_bytes([row_payload("n-1")])
    rejected = load_snapshot(manifest_bytes(rows, sample_list=raw), rows, raw)
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.SAMPLE_LIST_MALFORMED


def test_parse_sample_list_keeps_the_file_order() -> None:
    keys = parse_sample_list(
        (
            ("a" * 64)
            + "\tCONSTRUCTION\t2026-W07\n"
            + ("b" * 64)
            + "\tSERVICE\t2026-W08\n"
        ).encode()
    )
    assert keys.keys == ("a" * 64, "b" * 64)
    assert keys.divisions == ("CONSTRUCTION", "SERVICE")


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
    assert isinstance(
        load_snapshot(manifest_bytes(rows), rows, sample_list_bytes(rows)),
        LoadedSnapshot,
    )
    assert isinstance(
        load_snapshot(
            json.dumps({"schema_version": "snapshot-v4"}).encode(),
            rows,
            sample_list_bytes(rows),
        ),
        SnapshotRejected,
    )


def test_sample_list_divisions_come_from_the_file_not_a_code_constant() -> None:
    """M-6 — 최소 표본 결정식의 **업무 수**는 코드 상수(`len(BusinessCategory)`)가
    아니라 `sample-list.tsv` 의 distinct `business_division` 집합 크기다.

    두 레인이 다 읽는 단일 출처이기 때문이다 — Kotlin 의 수집 대상 업무 설정이 그
    파일에 반영되고, 설정이 둘이면 셋으로 센 최소치는 영영 닿지 않는 문턱이 된다.
    스키마 §2.1 이 이 칸을 **행의 `category` 와 다른 축**(Kotlin `BusinessDivision`
    어휘)이라고 못 박았으므로 닫힌 셋으로 검사하지 않는다 — 세기만 한다."""
    rows = rows_bytes([row_payload("n-1"), row_payload("n-2"), row_payload("n-3")])
    listing = sample_list_bytes(rows, divisions=("CONSTRUCTION", "SERVICE"))
    loaded = load_snapshot(manifest_bytes(rows, sample_list=listing), rows, listing)
    assert isinstance(loaded, LoadedSnapshot), loaded
    assert loaded.sample_divisions == ("CONSTRUCTION", "SERVICE")


def test_sample_list_divisions_are_sorted_and_deduplicated() -> None:
    """공시·재현 때문에 순서가 고정돼야 한다. 세는 것은 **distinct** 집합이다."""
    rows = rows_bytes([row_payload(f"n-{index}") for index in range(4)])
    listing = sample_list_bytes(rows, divisions=("SERVICE", "CONSTRUCTION"))
    loaded = load_snapshot(manifest_bytes(rows, sample_list=listing), rows, listing)
    assert isinstance(loaded, LoadedSnapshot), loaded
    assert loaded.sample_divisions == ("CONSTRUCTION", "SERVICE")


@pytest.mark.parametrize("blank", ["", "  "])
def test_blank_sample_list_columns_are_rejected(blank: str) -> None:
    """빈 업무 축은 세기의 분모를 조용히 바꾼다 — 형태 실패로 거부한다."""
    key = "a" * 64
    raw = f"{key}\t{blank}\t2026-W25\n".encode()
    rows = rows_bytes([row_payload("n-1")])
    rejected = load_snapshot(manifest_bytes(rows, sample_list=raw), rows, raw)
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.SAMPLE_LIST_MALFORMED


def test_notice_observed_count_keeps_samples_that_lost_only_their_detail() -> None:
    """M-8 — `has_*` 채움률의 분모. 표본에서 **공고 canonical 이 없던 것만** 뺀다.

    상세를 못 받아 행이 되지 못한 표본은 공고 축이 관측됐으므로 분모에 남는다.
    둘을 같이 빼면 「상세를 받은 공고만」의 비율이 되어 채움률이 실제보다 높게 나오고,
    둘 다 남기면 관측될 수 없었던 표본까지 분모에 들어간다."""
    rows = rows_bytes([row_payload("n-1")])
    listing = sample_list_bytes(rows, extra_keys=("f" * 64, "e" * 64))
    loaded = load_snapshot(
        manifest_bytes(
            rows,
            sample_list=listing,
            sampled_without_detail=1,
            sampled_without_notice=1,
        ),
        rows,
        listing,
    )
    assert isinstance(loaded, LoadedSnapshot), loaded
    assert loaded.sample_size == 3
    # 행 1 + 상세 없음 1 = 2. 공고 없음 1 만 빠진다.
    assert loaded.notice_observed_count == 2


def test_reader_cannot_distinguish_a_post_hoc_shrink_of_the_sample_list() -> None:
    """**판독의 경계를 명시한다** — 세 파일만 보는 판독은 「결과를 본 뒤 줄인 표본」과
    「원래 작았던 표본」을 구별하지 못한다. 생산 레인이 실측해 보고한 자리다.

    추출이 행이 된 표본만 목록에 남기고 `sample_size` 를 행 수로, 두 계수를 0 으로
    적고 해시를 그 파일로 다시 계산하면 v4 의 대조 넷이 **전부 참**이 된다 —
    ⑴ 해시가 그 파일의 것이고 ⑵ 행이 목록 안이고 ⑵' 키 수가 선언과 같고 ⑶ 항등식이
    닫힌다. 원리상 그렇고, 어떤 술어를 더해도 이 판독 자리에서는 닫히지 않는다:
    줄어든 파일 셋은 작은 표본의 파일 셋과 **바이트로 구별되지 않는다**.

    이것을 막는 것은 판독이 아니라 (a) 수집 시점에 확정된 파일 그 자체의 보존
    (D-6G-45 의 실행 상태 디렉터리 — 이미 있으면 덮어쓰지 않는다)과 (b) 생산 쪽
    왕복 test 다. 생산 레인 실측: 같은 축소가 저쪽 E2E 에서는 **2 failed** 다.

    이 test 가 통과하는 것이 정상이다. 있는 이유는 **이 경계가 닫혀 있다고 잘못
    읽히지 않게** 하기 위해서다 — 누군가 판독에 술어를 더해 「이제 막힌다」고 적으면
    이 자리에서 먼저 마주친다."""
    rows = rows_bytes([row_payload("n-1")])

    honest_listing = sample_list_bytes(rows, extra_keys=("f" * 64, "e" * 64))
    honest = load_snapshot(
        manifest_bytes(
            rows,
            sample_list=honest_listing,
            sampled_without_detail=1,
            sampled_without_notice=1,
        ),
        rows,
        honest_listing,
    )

    shrunk_listing = sample_list_bytes(rows)
    shrunk = load_snapshot(
        manifest_bytes(
            rows,
            sample_list=shrunk_listing,
            sample_size=1,
            sampled_without_detail=0,
            sampled_without_notice=0,
        ),
        rows,
        shrunk_listing,
    )

    assert isinstance(honest, LoadedSnapshot), honest
    assert isinstance(shrunk, LoadedSnapshot), shrunk
    # 판독이 가르지 못한다는 사실 자체 — 행은 같고 선언만 다르다.
    assert honest.rows == shrunk.rows
    assert (honest.sample_size, shrunk.sample_size) == (3, 1)


@pytest.mark.parametrize(
    "division", ["SERVICES", "service", "CONSTRUCTION_SERVICE", "기타"]
)
def test_business_division_outside_the_closed_vocabulary_is_rejected(
    division: str,
) -> None:
    """D-6G-53 — 업무 구분은 스키마 §2.1 의 닫힌 셋이다. 밖이면 **스냅숏 전체 거부**.

    세기만 하던 앞 판에는 조용한 실패가 있었다: distinct 수가 최소 표본 문턱을
    정하는데(업무 하나당 1,739건), 서로 다른 두 업무가 같은 문자열로 **합쳐지는**
    오타면 distinct 수가 줄어 문턱이 **내려가고**, 설계가 요구한 것보다 적은
    데이터로 실험이 그대로 진행된다."""
    rows = rows_bytes([row_payload("n-1"), row_payload("n-2")])
    listing = sample_list_bytes(rows, divisions=(division,))
    rejected = load_snapshot(manifest_bytes(rows, sample_list=listing), rows, listing)
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.UNKNOWN_BUSINESS_DIVISION


def test_every_vocabulary_member_is_accepted() -> None:
    """양성 대조 — 닫힌 셋을 좁히면 출하 어휘가 거부된다."""
    rows = rows_bytes([row_payload(f"n-{index}") for index in range(4)])
    listing = sample_list_bytes(
        rows, divisions=("CONSTRUCTION", "SERVICE", "GOODS", "FOREIGN")
    )
    loaded = load_snapshot(manifest_bytes(rows, sample_list=listing), rows, listing)
    assert isinstance(loaded, LoadedSnapshot), loaded
    assert loaded.sample_divisions == ("CONSTRUCTION", "FOREIGN", "GOODS", "SERVICE")


def test_notice_key_hash_must_be_hex_characters() -> None:
    """cr r3 L-6 — 길이 64 + 소문자만 보던 앞 판은 `z` 64자를 받았다. Kotlin
    `NoticeKeyHash.ofHex` 는 `[0-9a-f]{64}` 다 — 두 레인의 판독 강도가 달랐다."""
    raw = ("z" * 64 + "\tSERVICE\t2026-W25\n").encode()
    rows = rows_bytes([row_payload("n-1")])
    rejected = load_snapshot(manifest_bytes(rows, sample_list=raw), rows, raw)
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.SAMPLE_LIST_MALFORMED


def test_broken_utf8_names_the_file_that_broke() -> None:
    """cr r3 L-7 — 앞 판은 `UnicodeDecodeError` 를 바깥에서 한꺼번에 받아 **언제나**
    `SAMPLE_LIST_MALFORMED` 로 접었다. 깨진 `rows.jsonl` 이 「표본 목록이 형태를
    어겼다」로 보고되면 사유가 뿌리를 가리키지 못한다."""
    rows = rows_bytes([row_payload("n-1")])
    listing = sample_list_bytes(rows)

    # manifest 는 **온전한** 행에서 만들고 해시만 깨진 바이트에 맞춘다 — 헬퍼가 깨진
    # 바이트를 읽지 않게. 판독이 보는 것은 세 블롭이고, 여기서 재는 것은 사유다.
    broken_rows = b"\xff\xfe" + rows
    manifest = manifest_bytes(
        rows, sample_list=listing, rows_sha256=hashlib.sha256(broken_rows).hexdigest()
    )
    rejected = load_snapshot(manifest, broken_rows, listing)
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.MALFORMED_JSON

    broken_listing = b"\xff\xfe" + listing
    rejected = load_snapshot(
        manifest_bytes(rows, sample_list=broken_listing), rows, broken_listing
    )
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.SAMPLE_LIST_MALFORMED
