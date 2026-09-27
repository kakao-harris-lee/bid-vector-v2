"""D-6G-27 — **레인 간 왕복 golden** 의 소비 쪽.

verifier r1 H-1 과 code-review r1 M-11 의 공통 뿌리는 하나다: **Kotlin 이 쓴 바이트를
Python 이 한 번도 읽지 않았다.** Python fixture 는 소비 쪽 생성기가 만든 것이라, 생산
쪽이 스키마를 어겨도(추첨번호 상수 `null`, 공고일이 개찰일, 필수 칸이 `null`) 초록 CI
아래 살아남았다 — 실데이터면 승인 0건이 되는 결함 셋이 그렇게 지나갔다.

이 파일이 그 왕복의 **소비 반쪽**이다: Kotlin `SnapshotWriter` 가 출하 추출 경로로 낸
golden 바이트를 `load_snapshot` 과 전 과정에 통과시킨다. 생산 반쪽(같은 바이트를 낸다는
단언)은 Kotlin 레인에 있다 — 스키마가 한쪽만 움직이면 **둘 중 하나가 RED** 다.

**지금은 골격이다.** golden 이 아직 커밋되지 않았다(Kotlin 레인 진행 중). 그때까지 이
test 는 **건너뛰고 그 사실을 이유와 함께 남긴다** — 초록으로 위장하지 않는다. golden 이
오면 경로 상수 하나만 맞추면 선다. 이 잠금이 아직 서지 않았다는 것은 evidence 「알려진
제한」에도 적혀 있다.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from ml_engine.adapters.snapshot_files import SnapshotFiles, read_snapshot_files
from ml_engine.evaluation.backtest.snapshot import (
    LoadedSnapshot,
    load_snapshot,
    sample_list_checksum,
)

_REPO_ROOT = Path(__file__).resolve().parents[3]

GOLDEN_SNAPSHOT_DIR = _REPO_ROOT / "fixtures" / "m6-6g" / "golden-snapshot"
"""생산 쪽이 낸 golden 스냅숏 디렉터리(`manifest.json` + `rows.jsonl`).

**경로는 두 레인이 같이 아는 한 자리여야 한다** — 여기서만 선언하고, Kotlin 쪽이 다른
곳에 커밋하면 이 상수를 고친다. `reports/evidence/` 밖에 두는 이유는 evidence 크기 게이트
(evidence <= 산출물) 다: golden 은 산출물이지 장부가 아니다."""

_ABSENT = (
    "golden 이 아직 없다 — Kotlin 레인이 `SnapshotWriter` 출하 경로로 커밋하면 선다"
    f" (기대 경로: {GOLDEN_SNAPSHOT_DIR})"
)


def _golden_files() -> SnapshotFiles:
    if not GOLDEN_SNAPSHOT_DIR.is_dir():
        pytest.skip(_ABSENT)
    files = read_snapshot_files(GOLDEN_SNAPSHOT_DIR.as_uri())
    assert isinstance(files, SnapshotFiles), files
    return files


def _golden_snapshot() -> LoadedSnapshot:
    files = _golden_files()
    loaded = load_snapshot(files.manifest_bytes, files.rows_bytes)
    assert isinstance(loaded, LoadedSnapshot), (
        f"생산 쪽 golden 이 판독을 통과하지 못했다 — 레인 간 스키마가 갈렸다: {loaded}"
    )
    return loaded


def test_golden_snapshot_is_readable_by_the_shipped_reader() -> None:
    """생산 바이트 -> 소비 판독. 여기가 붉으면 **스키마가 한쪽만 움직인 것**이다."""
    snapshot = _golden_snapshot()
    assert snapshot.rows
    assert len(snapshot.rows_sha256) == 64


def test_golden_snapshot_carries_the_fields_the_exclusion_rules_need() -> None:
    """verifier r1 H-1 이 지목한 세 칸이 상수 `null` 이 아님을 **생산 바이트에서**
    확인한다. 소비 쪽 fixture 로는 잴 수 없던 것이다.

    - `drawn_serial_numbers` — 전 행이 `null` 이면 제외 ⑤ 가 전량에 걸린다
    - `noticed_on` != `opened_on` — 같으면 D-6G-14 의 공고일 기준이 개찰일 기준이 된다
    - `planned_price`·`bid_close_at` — 필수 칸이 비면 스냅숏 전체가 거부된다"""
    snapshot = _golden_snapshot()
    rows = snapshot.rows
    assert any(row.outcome.drawn_serial_numbers for row in rows), (
        "전 행의 추첨번호가 비어 있다 — 제외 ⑤ 가 전량에 걸린다(verifier H-1)"
    )
    assert any(row.notice.noticed_on != row.outcome.opened_on for row in rows), (
        "공고일이 개찰일과 같다 — 시행일 경계 제외가 개찰일 기준으로 돈다(H-2)"
    )
    assert all(row.outcome.planned_price > 0 for row in rows)


def test_golden_manifest_sample_list_matches_its_rows() -> None:
    """생산 쪽이 적은 표본 목록 해시가 **그 파일의 행**과 묶여 있는지(우회 ⑦)."""
    snapshot = _golden_snapshot()
    assert snapshot.sample_list_sha256 == sample_list_checksum(
        [row.notice.notice_key_hash for row in snapshot.rows]
    )


def test_golden_manifest_declares_the_supported_schema_version() -> None:
    """버전이 갈리면 판독이 전체를 거부한다 — 그 거부가 이 test 에서 먼저 보이게."""
    files = _golden_files()
    manifest = json.loads(files.manifest_bytes)
    loaded = load_snapshot(files.manifest_bytes, files.rows_bytes)
    assert isinstance(loaded, LoadedSnapshot), (
        f"golden 의 schema_version={manifest.get('schema_version')!r} 을 판독기가 "
        "지원하지 않는다 — 두 레인이 같이 움직여야 한다"
    )


def test_golden_path_is_declared_in_exactly_one_place() -> None:
    """경로가 흩어지면 golden 이 온 뒤에도 한쪽이 옛 자리를 본다. 이 test 는 golden 이
    없어도 돈다 — 골격이 살아 있다는 확인이다."""
    assert GOLDEN_SNAPSHOT_DIR.name == "golden-snapshot"
    assert GOLDEN_SNAPSHOT_DIR.parent.parent == _REPO_ROOT / "fixtures"
    assert not GOLDEN_SNAPSHOT_DIR.is_relative_to(_REPO_ROOT / "reports")
