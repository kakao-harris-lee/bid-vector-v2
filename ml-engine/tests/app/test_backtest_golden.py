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
from ml_engine.evaluation.backtest.exclusions import admit_rows, exclusion_counts
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.evaluation.backtest.reasons import ExclusionReason
from ml_engine.evaluation.backtest.snapshot import (
    LoadedSnapshot,
    load_snapshot,
    sample_list_checksum,
)

_TESTS_ROOT = Path(__file__).resolve().parents[1]
_SHIPPED_BACKTEST_POLICY = (
    _TESTS_ROOT.parents[0] / "policy" / "strategy-backtest-v1.yaml"
)


def _policy() -> StrategyBacktestPolicy:
    loaded = load_strategy_backtest_policy(_SHIPPED_BACKTEST_POLICY)
    assert isinstance(loaded, StrategyBacktestPolicy), loaded
    return loaded


GOLDEN_SNAPSHOT_DIR = _TESTS_ROOT / "evaluation" / "fixtures" / "m6-6g-golden"
"""생산 쪽이 낸 golden 스냅숏 디렉터리(`manifest.json` + `rows.jsonl`).

**경로는 두 레인이 같이 아는 한 자리여야 한다** — 여기서만 선언하고, 바뀌면 이 상수
하나를 고친다. 자리 선택의 근거 둘(팀장 지시 2026-09-27):
- `reports/evidence/` 가 아니다 — evidence 크기 게이트(evidence <= 산출물)에 걸린다.
  golden 은 산출물이지 장부가 아니다.
- 저장소 루트 `fixtures/` 도 아니다 — 그쪽은 `data-extract.md` 가 manifest·SHA·출처
  규율로 관리하는 **검증 corpus** 자리다. 레인 간 왕복 golden 은 그 규율의 대상이
  아니고 test 의 입력이므로, 합성 스냅숏 fixture 옆(`tests/evaluation/fixtures/`)에
  둔다."""

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
    """verifier r1 H-1 이 지목한 세 칸이 **상수 `null` 이 아님**을 생산 바이트에서
    확인한다. 소비 쪽 fixture 로는 잴 수 없던 것이다.

    golden 에는 값 결측 행이 각 칸 하나씩 **일부러** 들어 있으므로(팀장 지시), 단언은
    「모든 행이 차 있다」가 아니라 **「채워진 행이 있다」**다 — 전자로 쓰면 의도된
    결측 행이 test 를 붉히고, 그렇다고 `!= None` 비교로 느슨하게 쓰면 결측 행이
    단언을 **거짓 통과**시킨다(`None != date` 는 참이다)."""
    rows = _golden_snapshot().rows
    assert any(row.outcome.drawn_serial_numbers for row in rows), (
        "전 행의 추첨번호가 비어 있다 — 제외 ⑤ 가 전량에 걸린다(verifier H-1)"
    )
    both_dates = [
        row
        for row in rows
        if row.notice.noticed_on is not None and row.outcome.opened_on is not None
    ]
    assert both_dates, "공고일과 개찰일이 함께 있는 행이 없다"
    assert any(row.notice.noticed_on != row.outcome.opened_on for row in both_dates), (
        "공고일이 개찰일과 같다 — 시행일 경계 제외가 개찰일 기준으로 돈다(H-2)"
    )
    prices = [
        row.outcome.planned_price
        for row in rows
        if row.outcome.planned_price is not None
    ]
    assert prices and all(price > 0 for price in prices)


def test_golden_exercises_the_row_level_exclusion_path() -> None:
    """golden 이 **값 결측 행을 일부러 담는다**(팀장 지시) — 그 행들이 v3 의 행 단위
    제외로 내려가고 **나머지 행은 산다**는 것을 생산 바이트에서 확인한다.

    이 단언이 v2 라면 성립하지 않는다: 그때는 한 행의 `null` 이 스냅숏 전체를 거부해
    `load_snapshot` 단계에서 이미 멈춘다(verifier r1 H-1). 판독이 여기까지 왔다는 것
    자체가 갈래가 갈렸다는 증거다."""
    snapshot = _golden_snapshot()
    policy = _policy()
    result = admit_rows(snapshot.rows, policy)
    assert result.admitted, "golden 에서 승인된 행이 하나도 없다"
    reasons = {item.reason for item in result.excluded}
    absence = {
        ExclusionReason.NOTICE_DATE_ABSENT,
        ExclusionReason.BID_CLOSE_AT_ABSENT,
        ExclusionReason.OPENING_DATE_ABSENT,
        ExclusionReason.PLANNED_PRICE_ABSENT,
    }
    assert absence <= reasons, (
        f"값 결측 사유가 다 나오지 않았다 — 빠진 것: {sorted(absence - reasons)}"
    )
    counts = dict(exclusion_counts(result.excluded))
    assert all(counts[reason] >= 1 for reason in absence)


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
    없어도 돈다 — 골격이 살아 있다는 확인이다. 자리 선택의 근거 둘도 함께 잠근다:
    evidence 안이 아니고(크기 게이트), 저장소 루트 `fixtures/` 안도 아니다(그쪽은
    `data-extract.md` 의 검증 corpus 규율 자리다)."""
    assert GOLDEN_SNAPSHOT_DIR.name == "m6-6g-golden"
    assert GOLDEN_SNAPSHOT_DIR.parent == _TESTS_ROOT / "evaluation" / "fixtures"
    repo_root = _TESTS_ROOT.parents[1]
    assert not GOLDEN_SNAPSHOT_DIR.is_relative_to(repo_root / "reports")
    assert not GOLDEN_SNAPSHOT_DIR.is_relative_to(repo_root / "fixtures")
