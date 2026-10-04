"""D-6G-66 — **표본 업무 범위는 manifest 가 말한다.**

`sample_divisions` 가 표본 목록에서만 오면, 한 업무가 통째로 빠질 때 distinct 가 줄고
최소 표본 문턱도 **함께 내려간다**. 결측이 자기 검사를 낮추는 모양이고 그 하락은
조용하다 — 초록 CI 아래에서 설계가 요구한 것보다 적은 데이터로 실험이 진행된다.

v5 는 문턱의 출처를 **확정 범위**(`sample_scope_divisions`, 생산 쪽이 manifest 에 싣는
설정값)로 옮긴다. 이 파일이 잠그는 것 다섯:

1. 문턱이 표본 목록이 아니라 범위에서 온다(둘을 **다르게** 둬야 갈린다)
2. 한 업무의 행이 전부 사라져도 문턱은 그대로고, 그 업무가 판정문에 UNDERPOWERED 로 남는다
3. 표본의 업무 ⊆ 범위 — 아니면 스냅숏 전체 거부
4. **행의** 업무도 범위 안 — 밖이면 채점에 들어가면서 공시에서 사라진다
5. 범위 값은 §2.1 의 닫힌 어휘이고 오름차순·중복 없음

더해서 manifest 의 **키 집합**을 **세 자리**에서 맞댄다(M6/6G-2c, D-6G2c-12) — 스키마 문서
§2 · 판독기의 허용 키 · **test 쪽 manifest 생성기 둘**. 앞 판은 문서와 판독기만 맞댔고,
생성기는 그 등식 밖이었다: 칸을 하나 늘리면 판독이 거부해 잡히기는 하지만 **어느 자리를
고쳐야 하는지** 등식이 말해 주지 않았다(6G r5 등재 제한). 생성기를 하나로 합치지 않는 이유는
따로 있다 — 깨뜨린 manifest 를 짓는 test 가 헐거워진다(저자 판단 승계). 그래서 합치는 대신
**둘 다 등식에 넣는다**.
"""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

import pytest
from _backtest_support import (
    manifest_bytes,
    row_payload,
    rows_bytes,
    sample_list_bytes,
)

from ml_engine.evaluation.backtest.observations import (
    LoadedSnapshot,
    SnapshotRejected,
)
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
    strategy_backtest_policy_checksum,
)
from ml_engine.evaluation.backtest.reasons import DivisionCoverage
from ml_engine.evaluation.backtest.records import (
    BacktestRequest,
    DivisionCoverageRecord,
)
from ml_engine.evaluation.backtest.report import canonical_verdict_bytes
from ml_engine.evaluation.backtest.run import (
    _division_coverage,
    run_strategy_backtest,
)
from ml_engine.evaluation.backtest.snapshot import (
    _MANIFEST_KEYS as READER_MANIFEST_KEYS,
)
from ml_engine.evaluation.backtest.snapshot import (
    SnapshotRejectionReason,
    load_snapshot,
)
from ml_engine.evaluation.backtest.strategies import UniformBandStrategy
from tests.app.test_backtest_job import _derived_policy
from tests.evaluation._backtest_fixture import (
    BoardSpec,
    BoardWindow,
    build_board_rows,
    build_files,
)
from tests.evaluation._backtest_support import PlannedBidStrategy

_TESTS_ROOT = Path(__file__).resolve().parents[1]
_REPO_ROOT = _TESTS_ROOT.parents[1]
_SCHEMA_DOCUMENT = (
    _REPO_ROOT / "reports" / "evidence" / "m6" / "6g" / "snapshot-schema.md"
)
_SHIPPED_BACKTEST_POLICY = (
    _TESTS_ROOT.parents[0] / "policy" / "strategy-backtest-v1.yaml"
)


def _policy() -> StrategyBacktestPolicy:
    loaded = load_strategy_backtest_policy(_SHIPPED_BACKTEST_POLICY)
    assert isinstance(loaded, StrategyBacktestPolicy), loaded
    return loaded


def _rows(labels: dict[str, str]) -> bytes:
    """`{라벨: 업무}` -> `rows.jsonl` 바이트."""
    payloads: list[dict[str, Any]] = [
        row_payload(label, notice_category=category)
        for label, category in labels.items()
    ]
    return rows_bytes(payloads)


def _load(
    rows: bytes,
    *,
    listing_division: str = "SERVICE",
    scope: tuple[str, ...] | None = None,
) -> LoadedSnapshot | SnapshotRejected:
    listing = sample_list_bytes(rows, division=listing_division)
    return load_snapshot(
        manifest_bytes(rows, sample_list=listing, sample_scope_divisions=scope),
        rows,
        listing,
    )


def _loaded(rows: bytes, **kwargs: Any) -> LoadedSnapshot:
    result = _load(rows, **kwargs)
    assert isinstance(result, LoadedSnapshot), result
    return result


def _verdict_payload(snapshot: LoadedSnapshot) -> dict[str, Any]:
    """이 판은 최소 표본에 못 미쳐 **멈춤**으로 끝난다 — 멈춤도 산출물이고 표본
    결정식과 업무 대표를 그대로 싣는다(`records.BacktestStopped`)."""
    policy = _policy()
    outcome = run_strategy_backtest(
        BacktestRequest(
            snapshot=snapshot,
            baseline=UniformBandStrategy(),
            candidates=(),
            primary_names=(),
            policy=policy,
            policy_checksum=strategy_backtest_policy_checksum(policy),
        )
    )
    payload: dict[str, Any] = json.loads(canonical_verdict_bytes(outcome))
    return payload


def _documented_manifest_keys() -> set[str]:
    """스키마 문서 §2 의 manifest 예제가 적은 키 집합 — 문서를 **읽기만** 한다."""
    text = _SCHEMA_DOCUMENT.read_text(encoding="utf-8")
    marker = "## 2. `manifest.json`"
    assert marker in text, "스키마 문서에서 §2 를 찾지 못했다"
    block = re.search(r"```json\n(.+?)\n```", text[text.index(marker) :], re.DOTALL)
    assert block is not None, "§2 의 manifest 예제 블록을 찾지 못했다"
    return set(json.loads(block.group(1)))


def test_manifest_key_set_equals_the_schema_document() -> None:
    """문서 §2 의 manifest 예제와 판독기의 허용 키가 같다.

    판독은 미지 키에 스냅숏 전체를 거부하므로, 생산 쪽이 칸을 하나 늘리면 문서를
    고치는 것만으로는 부족하다 — 둘을 맞대 두면 한쪽만 움직인 순간 여기서 걸린다."""
    documented = _documented_manifest_keys()
    assert documented == set(READER_MANIFEST_KEYS), (
        f"문서 §2: {sorted(documented)} · 판독기: {sorted(READER_MANIFEST_KEYS)}"
    )


def test_both_manifest_generators_emit_the_documented_key_set() -> None:
    """D-6G2c-12 — **세 자리 등식**: test 쪽 manifest 생성기 **둘** == 문서 §2 ==
    판독기 허용 키.

    앞 판의 등식은 문서와 판독기 둘뿐이었고 생성기는 밖이었다. 칸을 하나 늘리면 두
    생성기가 그 칸을 빠뜨린 채 남고, 판독이 미지/부재로 거부하기는 하지만 **어느 자리를
    고쳐야 하는지**는 말해 주지 않는다 — 거부 문면은 manifest 의 칸 이름만 나른다
    (6G r5 가 등재한 제한 그대로다). 생성기까지 등식에 넣으면 빠뜨린 쪽이 **이름으로**
    지목된다.

    둘을 하나로 합치지 않는다: 하나는 인자로 **깨뜨린 manifest** 를 짓는 생성기이고
    (`_backtest_support.manifest_bytes`), 다른 하나는 커밋된 왕복 golden 과 바이트가
    묶인 고정 판이다(`_backtest_fixture.build_files`). 합치면 전자의 자유도가 후자의
    고정에 묶여 깨뜨리는 test 가 헐거워진다(저자 판단 승계)."""
    documented = _documented_manifest_keys()
    rows = rows_bytes([row_payload("n-1")])
    support_keys = set(json.loads(manifest_bytes(rows)))
    fixture_manifest, _, _ = build_files()
    fixture_keys = set(json.loads(fixture_manifest))

    assert support_keys == documented, (
        "`_backtest_support.manifest_bytes` 의 키가 문서 §2 와 다르다 — "
        f"생성기에만: {sorted(support_keys - documented)} · "
        f"문서에만: {sorted(documented - support_keys)}"
    )
    assert fixture_keys == documented, (
        "`_backtest_fixture.build_files` 의 키가 문서 §2 와 다르다 — "
        f"생성기에만: {sorted(fixture_keys - documented)} · "
        f"문서에만: {sorted(documented - fixture_keys)}"
    )
    assert documented == set(READER_MANIFEST_KEYS), (
        f"문서 §2: {sorted(documented)} · 판독기: {sorted(READER_MANIFEST_KEYS)}"
    )


def test_division_coverage_vocabulary_matches_the_schema_document() -> None:
    """판정문의 업무 대표 어휘도 닫힌 셋이고 문서가 정본이다."""
    text = _SCHEMA_DOCUMENT.read_text(encoding="utf-8")
    marker = "판정문의 업무 대표 어휘"
    assert marker in text, "스키마 문서에서 업무 대표 어휘 문장을 찾지 못했다"
    section = text[text.index(marker) : text.index(marker) + 200]
    documented = {token.strip("`") for token in re.findall(r"`[A-Z_]+`", section)}
    assert documented == {str(value) for value in DivisionCoverage}, (
        f"문서: {sorted(documented)} · 코드: "
        f"{sorted(str(value) for value in DivisionCoverage)}"
    )


def test_scope_outside_the_closed_vocabulary_rejects_the_snapshot() -> None:
    """D-6G-53 과 같은 이유 — 문턱이 이 값의 **수**로 정해지므로 어휘 밖 값을 받으면
    오타 하나가 문턱을 조용히 올리거나 내린다."""
    rows = _rows({"n-1": "SERVICE"})
    rejected = _load(rows, scope=("SERVICE", "SERVIVE"))
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.UNKNOWN_BUSINESS_DIVISION


def test_scope_must_be_sorted_and_deduplicated() -> None:
    """같은 입력이면 같은 바이트 — 이 칸도 예외가 아니다(중복은 수까지 부풀린다)."""
    rows = _rows({"n-1": "SERVICE"})
    for scope in (("SERVICE", "CONSTRUCTION"), ("SERVICE", "SERVICE")):
        rejected = _load(rows, scope=scope)
        assert isinstance(rejected, SnapshotRejected), scope
        assert rejected.reason is SnapshotRejectionReason.INVALID_VALUE


def test_empty_scope_rejects_the_snapshot() -> None:
    """빈 범위는 문턱을 0 으로 만든다 — 그 판은 무엇이든 통과한다."""
    rejected = _load(_rows({"n-1": "SERVICE"}), scope=())
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.INVALID_VALUE


def test_sample_divisions_must_be_a_subset_of_the_scope() -> None:
    """표본에 범위 밖 업무가 있으면 둘 중 하나가 거짓이다 — 문턱을 범위에서 정하는
    이상 조용히 넘길 수 없다."""
    rows = _rows({"n-1": "SERVICE"})
    rejected = _load(rows, listing_division="GOODS", scope=("CONSTRUCTION", "SERVICE"))
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.SAMPLE_SCOPE_MISMATCH


def test_rows_outside_the_scope_reject_the_snapshot() -> None:
    """행의 업무가 범위 밖이면 그 행은 채점에 들어가면서 **업무 대표 공시에서는
    보이지 않는다**(공시가 범위를 돌며 세기 때문이다). 조용한 결측이라 멈춘다."""
    rows = _rows({"n-1": "SERVICE", "n-2": "CONSTRUCTION"})
    rejected = _load(rows, listing_division="SERVICE", scope=("SERVICE",))
    assert isinstance(rejected, SnapshotRejected)
    assert rejected.reason is SnapshotRejectionReason.SAMPLE_SCOPE_MISMATCH


def test_threshold_comes_from_the_scope_not_from_the_sample_list() -> None:
    """**변이 표적**: 문턱을 다시 표본 목록에서 세면 여기가 붉어진다.

    표본 목록에는 업무가 하나뿐이고 범위에는 셋이다 — 두 수가 달라야 어느 쪽에서
    세는지가 갈린다(같은 판에서는 어느 쪽이든 통과한다)."""
    rows = _rows({"n-1": "SERVICE", "n-2": "SERVICE"})
    scope = ("CONSTRUCTION", "GOODS", "SERVICE")
    snapshot = _loaded(rows, listing_division="SERVICE", scope=scope)
    assert snapshot.sample_divisions == ("SERVICE",)
    assert snapshot.sample_scope_divisions == scope

    policy = _policy()
    expected = policy.sampling.minimum_required_sample(
        rows_per_window=policy.verdict.min_window_rows,
        window_count=policy.verdict.min_window_count,
        category_count=len(scope),
    )
    from_listing = policy.sampling.minimum_required_sample(
        rows_per_window=policy.verdict.min_window_rows,
        window_count=policy.verdict.min_window_count,
        category_count=len(snapshot.sample_divisions),
    )
    assert expected != from_listing, "판을 잘못 지었다 — 두 문턱이 같으면 못 가른다"
    assert _verdict_payload(snapshot)["sampling"]["minimum_required_sample"] == expected


def test_threshold_holds_and_the_empty_division_is_disclosed_absent() -> None:
    """**변이 표적 둘**: 한 업무의 행을 전부 지워도 ⑴ 문턱이 내려가지 않고 ⑵ 그 업무가
    판정문에 남는다.

    D-6G-66 이 요구한 거동이 그것이다 — 결측이 문턱을 낮추는 대신 **보이게** 된다.
    D-6G2c-17 이 그 표지를 갈랐다: 행 0 은 `ABSENT` 이고, **행이 있는데 적은** 업무는
    `UNDERPOWERED` 다. 이 판의 두 업무는 행이 한둘뿐이라 창당 표본 하한에 한참 못 미친다 —
    그래서 앞 판이 `COVERED` 로 공시하던 자리가 전부 `UNDERPOWERED` 로 내려온다."""
    scope = ("CONSTRUCTION", "SERVICE")
    both = _loaded(
        _rows({"n-1": "SERVICE", "n-2": "CONSTRUCTION"}),
        listing_division="SERVICE",
        scope=scope,
    )
    only_service = _loaded(
        _rows({"n-1": "SERVICE", "n-3": "SERVICE"}),
        listing_division="SERVICE",
        scope=scope,
    )

    full = _verdict_payload(both)
    thinned = _verdict_payload(only_service)
    assert (
        thinned["sampling"]["minimum_required_sample"]
        == full["sampling"]["minimum_required_sample"]
    ), "한 업무의 행이 사라지자 문턱이 내려갔다 — 결측이 자기 검사를 낮춘다"

    coverage = thinned["snapshot"]["division_coverage"]
    assert coverage["CONSTRUCTION"] == {
        "row_count": 0,
        "status": str(DivisionCoverage.ABSENT),
    }, "행이 0 인 업무가 판정문에서 ABSENT 로 공시되지 않는다"
    assert coverage["SERVICE"] == {
        "row_count": 2,
        "status": str(DivisionCoverage.UNDERPOWERED),
    }, "행 둘뿐인 업무가 COVERED 로 접혔다 — 표지가 행 수의 참/거짓으로 지어졌다"
    assert full["snapshot"]["division_coverage"]["CONSTRUCTION"] == {
        "row_count": 1,
        "status": str(DivisionCoverage.UNDERPOWERED),
    }, "행 하나뿐인 업무가 COVERED 로 읽힌다 — 6G verifier r5 L-8 의 자리다"


def test_the_division_label_turns_over_at_the_window_row_floor() -> None:
    """D-6G2c-17 의 **문턱 경계** — 표지가 갈리는 자리가 정책의 창당 표본 하한이다.

    기대값을 test 에 적지 않는다(적으면 하한이 상수가 된 것과 구별되지 않는다) — 로드된
    정책에서 꺼내 그 값의 **앞·뒤·그 자리**를 잰다. 새 정책 키를 만들지 않았으므로 이
    하한이 움직이면 표지도 함께 움직여야 한다."""
    required = _policy().verdict.min_window_rows
    assert _division_coverage(0, required) is DivisionCoverage.ABSENT
    assert _division_coverage(1, required) is DivisionCoverage.UNDERPOWERED
    assert _division_coverage(required - 1, required) is DivisionCoverage.UNDERPOWERED
    assert _division_coverage(required, required) is DivisionCoverage.COVERED
    assert _division_coverage(required + 1, required) is DivisionCoverage.COVERED


def test_the_coverage_record_refuses_a_label_its_row_count_contradicts() -> None:
    """verifier r1 F-7 — 표지와 행 수가 어긋난 값은 **만들 수 없다**.

    `ABSENT` 는 「행이 하나도 오지 않았다」는 뜻이다. `row_count=0` 인데 `COVERED` 인 값을 지을
    수 있으면 판정문이 스스로 모순된 것을 실을 수 있다 — 지금 생성자는 판정 경로 하나뿐이지만
    타입이 그것을 보증하지는 않았다(그 표면이 (2b) 에 올라 있다).

    문턱 쪽은 여기서 보지 않는다(정책을 모른다) — 양성 대조로 하한 미만·이상 둘이 모두 서는지
    함께 확인한다."""
    required = _policy().verdict.min_window_rows
    for row_count, status in (
        (0, DivisionCoverage.ABSENT),
        (1, DivisionCoverage.UNDERPOWERED),
        (required, DivisionCoverage.COVERED),
    ):
        assert DivisionCoverageRecord("SERVICE", row_count, status).status is status

    for row_count, status in (
        (0, DivisionCoverage.COVERED),
        (0, DivisionCoverage.UNDERPOWERED),
        (1, DivisionCoverage.ABSENT),
        (required, DivisionCoverage.ABSENT),
    ):
        with pytest.raises(ValueError, match="업무 대표 표지와 행 수"):
            DivisionCoverageRecord("SERVICE", row_count, status)


def test_the_verdict_discloses_all_three_division_labels() -> None:
    """표지 셋이 **판정문에서** 다 난다 — 어느 하나가 도달 불가면 그 이름은 장식이다.

    업무 셋을 범위에 두고 행 수를 하한 이상 · 하나 · 0 으로 갈라 둔다. 이 판은 최소 표본에
    못 미쳐 **멈춤**으로 끝나지만 멈춤도 산출물이고 업무 대표를 그대로 싣는다."""
    required = _policy().verdict.min_window_rows
    labels = {f"covered-{index}": "SERVICE" for index in range(required)}
    labels["thin-1"] = "GOODS"
    scope = ("CONSTRUCTION", "GOODS", "SERVICE")
    snapshot = _loaded(_rows(labels), listing_division="SERVICE", scope=scope)

    coverage = _verdict_payload(snapshot)["snapshot"]["division_coverage"]
    assert coverage["SERVICE"] == {
        "row_count": required,
        "status": str(DivisionCoverage.COVERED),
    }
    assert coverage["GOODS"] == {
        "row_count": 1,
        "status": str(DivisionCoverage.UNDERPOWERED),
    }
    assert coverage["CONSTRUCTION"] == {
        "row_count": 0,
        "status": str(DivisionCoverage.ABSENT),
    }


def test_the_division_label_does_not_change_the_window_verdict(tmp_path: Path) -> None:
    """D-6G2c-17 — 업무 표지와 **창 단위** UNDERPOWERED(D-6G-31)는 이름만 같은 다른 축이다.

    한 업무가 `UNDERPOWERED` 인 판에서도 창은 자기 축으로 판정된다: 창 셋이 전부 섰고
    후보가 `StrategyPassed` 다. 업무 표지를 창 판정에 물리면 이 판이 `NotEvaluable` 로
    내려앉아 여기서 붉어진다.

    판 짓기: 채점 창 셋은 용역으로 채우고(창마다 50 공고), 공사는 창 **밖**에 다섯만 둔다 —
    창당 행 하한(이 판에서 10)보다 적어 공사는 `UNDERPOWERED` 이고, 창은 용역 행으로 선다.
    정책은 `test_backtest_job._derived_policy` 를 **그대로 쓴다**(완화 축의 허용 목록이 그
    모듈의 test 로 잠겨 있다 — 여기서 두 번째 완화 경로를 만들지 않는다)."""
    board = build_board_rows(
        BoardSpec(
            (BoardWindow(10, 0, 40, 0),) * 3,
            pad=5,
            pad_categories=("CONSTRUCTION",),
        )
    )
    rows = rows_bytes(list(board.payloads))
    listing = sample_list_bytes(rows, division="SERVICE")
    snapshot = load_snapshot(manifest_bytes(rows, sample_list=listing), rows, listing)
    assert isinstance(snapshot, LoadedSnapshot), snapshot

    policy = load_strategy_backtest_policy(_derived_policy(tmp_path / "policy"))
    assert isinstance(policy, StrategyBacktestPolicy), policy
    outcome = run_strategy_backtest(
        BacktestRequest(
            snapshot=snapshot,
            baseline=PlannedBidStrategy("S0-planned", board.baseline_plan),
            candidates=(PlannedBidStrategy("C-planned", board.candidate_plan),),
            primary_names=(),
            policy=policy,
            policy_checksum=strategy_backtest_policy_checksum(policy),
        )
    )
    payload: dict[str, Any] = json.loads(canonical_verdict_bytes(outcome))
    assert "strategies" in payload, (
        f"판이 멈췄다 — 창 축을 잴 수 없다: {payload.get('stopped')} {payload.get('detail')}"
    )

    coverage = payload["snapshot"]["division_coverage"]
    assert coverage["CONSTRUCTION"]["status"] == str(DivisionCoverage.UNDERPOWERED), (
        f"판을 잘못 지었다 — 공사가 UNDERPOWERED 가 아니면 두 축을 가를 수 없다: {coverage}"
    )
    assert coverage["SERVICE"]["status"] == str(DivisionCoverage.COVERED), coverage

    candidate = next(
        item for item in payload["strategies"] if item["strategy"] == "C-planned"
    )
    assert candidate["outcome"] == "StrategyPassed", candidate
    assert len(candidate["windows"]) == len(payload["selected_windows"])
    assert all(window["passed"] for window in candidate["windows"]), candidate
    assert not any(window["underpowered"] for window in candidate["windows"]), (
        "창이 검정력 미달로 내려갔다 — 업무 표지가 창 축에 샜을 수 있다"
    )


def test_scope_is_carried_into_the_verdict_next_to_the_sample_divisions() -> None:
    """문턱의 근거가 판정문에 보인다 — 값이 없으면 문턱이 왜 그 값인지 알 수 없다."""
    scope = ("CONSTRUCTION", "SERVICE")
    snapshot = _loaded(
        _rows({"n-1": "SERVICE"}), listing_division="SERVICE", scope=scope
    )
    payload = _verdict_payload(snapshot)["snapshot"]
    assert payload["sample_scope_divisions"] == list(scope)
    assert payload["sample_divisions"] == ["SERVICE"]
