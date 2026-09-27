"""RED — M6/6G acceptance 「재현 test: 합성 스냅숏 fixture 하나로 전 과정이 바이트 동일
판정 JSON」.

여기서 재는 것 넷:
1. **바이트 동일** — 같은 스냅숏·같은 정책·같은 seed 면 두 번 돌려 같은 바이트.
2. **입력 줄 순서 무관** — `rows.jsonl` 의 줄을 섞어도 같은 판정이 나온다(판독이 공고
   키로 정렬한다). 재현성이 파일 배치에 기대지 않는다는 뜻이다.
3. **정책이 판정에 닿는다** — 정책 값 하나를 바꾸면 judgement JSON 이 달라지고
   checksum 도 달라진다. 값이 wire 에 닿지 않는 「장식 정책」이 아니다.
4. **전략 다섯이 조립된다** — S0 기준선 + S1 + S2 세 후보 + S4, 그리고 주 가설 수가
   Bonferroni 분모와 같다.

fixture 는 합성이고 비식별이다(`_backtest_fixture.py`) — 실 데이터를 쓰지 않는다.
판정 임계는 **출하 정책 그대로 쓰지 못한다**: 출하 창당 하한이 483 이라 재현 test 에
필요한 공고 수가 수천이 되고 S4 몬테카를로가 CI 를 몇 분씩 잡는다. 그래서 이 test 는
출하 파일에서 **창 크기·몬테카를로 반복 수·적합도 표본 하한만** 낮춘 파생 정책을
`tmp_path` 에 만들어 쓴다 — 판정식(Δ·유의수준·Bonferroni·비열등 한계·검정력)은 출하
값 그대로다. 출하 값이 승인값인지는 `tests/evaluation/test_backtest_policy.py` 가 본다.
"""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any

import pytest

from ml_engine.app.backtest_distribution import S2_STRATEGY_NAMES
from ml_engine.app.backtest_job import (
    JobCompleted,
    JobFailed,
    JobFailureReason,
    build_strategies,
    run_backtest_job,
)
from ml_engine.evaluation.backtest.policy import (
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
)
from ml_engine.inference.policy import InferencePolicy, load_inference_policy
from tests.evaluation._backtest_fixture import build_files, fixture_dir

_POLICY_DIR = Path(__file__).resolve().parents[2] / "policy"
_SHIPPED_BACKTEST_POLICY = _POLICY_DIR / "strategy-backtest-v1.yaml"
_INFERENCE_POLICY = _POLICY_DIR / "inference-v1.yaml"

# 출하 파일에서 낮추는 값 — 전부 **표본 크기·계산량** 축이고 판정식 축이 아니다.
_TEST_OVERRIDES = {
    "verdict.min_window_rows": "25",
    "verdict.min_window_count": "3",
    "strategy.s4_iteration_count": "100",
    "strategy.s4_min_competitor_samples": "30",
    "fit.min_sample_count": "50",
    "fit.max_bin_ratio_deviation": "0.20",
}


def _derived_policy(tmp_path: Path, extra: dict[str, str] | None = None) -> Path:
    overrides = dict(_TEST_OVERRIDES)
    overrides.update(extra or {})
    lines = []
    for line in _SHIPPED_BACKTEST_POLICY.read_text(encoding="utf-8").splitlines():
        key = line.split(":", 1)[0]
        lines.append(f"{key}: {overrides[key]}" if key in overrides else line)
    tmp_path.mkdir(parents=True, exist_ok=True)
    path = tmp_path / "strategy-backtest-test.yaml"
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return path


def _snapshot_dir(tmp_path: Path, *, reverse: bool = False) -> str:
    manifest_bytes, rows_bytes = build_files()
    directory = tmp_path / "snapshot"
    directory.mkdir(parents=True, exist_ok=True)
    if reverse:
        lines = rows_bytes.decode("utf-8").splitlines()
        rows_bytes = ("\n".join(reversed(lines)) + "\n").encode("utf-8")
        manifest = json.loads(manifest_bytes)
        manifest["rows_sha256"] = hashlib.sha256(rows_bytes).hexdigest()
        manifest_bytes = json.dumps(manifest, sort_keys=True).encode("utf-8")
    (directory / "manifest.json").write_bytes(manifest_bytes)
    (directory / "rows.jsonl").write_bytes(rows_bytes)
    return directory.as_uri()


def _main_variant(payload_bytes: bytes) -> dict[str, Any]:
    """판 셋 중 주 판정(첫 원소). `SampleVariant` 선언 순서가 배열 순서다."""
    variants = json.loads(payload_bytes)["variants"]
    assert variants[0]["variant"]["variant"] == "MAIN", variants[0]
    return variants[0]


def _run(snapshot_uri: str, policy_path: Path) -> JobCompleted:
    outcome = run_backtest_job(
        snapshot_uri=snapshot_uri,
        backtest_policy_path=policy_path,
        inference_policy_path=_INFERENCE_POLICY,
    )
    assert isinstance(outcome, JobCompleted), outcome
    return outcome


def test_committed_fixture_matches_its_generator() -> None:
    """커밋된 fixture 가 생성기와 갈리지 않는다 — 갈리면 재현 test 가 무엇을 재는지
    알 수 없다."""
    manifest_bytes, rows_bytes = build_files()
    assert (fixture_dir() / "manifest.json").read_bytes() == manifest_bytes
    assert (fixture_dir() / "rows.jsonl").read_bytes() == rows_bytes


def test_same_snapshot_and_policy_yield_byte_identical_verdict(
    tmp_path: Path,
) -> None:
    policy_path = _derived_policy(tmp_path)
    uri = _snapshot_dir(tmp_path)
    first = _run(uri, policy_path)
    second = _run(uri, policy_path)
    assert first.verdict_bytes == second.verdict_bytes
    assert first.checksum == second.checksum


def test_row_order_does_not_change_the_verdict(tmp_path: Path) -> None:
    policy_path = _derived_policy(tmp_path)
    forward = _run(_snapshot_dir(tmp_path / "a"), policy_path)
    reversed_rows = _run(_snapshot_dir(tmp_path / "b", reverse=True), policy_path)
    payload = _main_variant(forward.verdict_bytes)
    other = _main_variant(reversed_rows.verdict_bytes)
    assert payload["strategies"] == other["strategies"]
    assert payload["exclusions"] == other["exclusions"]
    assert payload["selected_windows"] == other["selected_windows"]


def test_verdict_reaches_the_judgement_stage_with_every_strategy(
    tmp_path: Path,
) -> None:
    payload = _main_variant(
        _run(_snapshot_dir(tmp_path), _derived_policy(tmp_path)).verdict_bytes
    )
    assert "stopped" not in payload
    assert payload["distribution_fit"]["accepted"] is True
    assert [item["strategy"] for item in payload["strategies"]] == [
        "S1",
        "S2c",
        "S2b",
        "S2a",
        "S4",
    ]
    assert payload["primary_hypotheses"] == list(S2_STRATEGY_NAMES)
    assert len(payload["selected_windows"]) >= 3
    assert payload["seeds"] == [20260812, 1, 7, 42, 2026]
    assert payload["limitations"]
    assert payload["undecidable"]["LOCAL_GOVERNMENT"] > 0
    assert payload["sampling"]["within_budget"] is True
    fill = payload["fill_rates"]
    assert fill["successful_bid_method_name"] == 1.0
    assert fill["reserve_range_end_rate"] == 1.0
    # 오늘 fixture 는 공사가 없어 A 적용 여부·순공사원가가 비어 있다 — 채움률 0 을
    # 숨기지 않고 공시한다(D-6G-22).
    assert fill["bid_price_formula_a_applicable"] == 0.0
    assert fill["pure_construction_cost"] == 0.0
    assert payload["base_amount_mismatch_count"] == 0


def test_verdict_carries_the_policy_checksum_and_snapshot_coordinates(
    tmp_path: Path,
) -> None:
    policy_path = _derived_policy(tmp_path)
    payload = _main_variant(_run(_snapshot_dir(tmp_path), policy_path).verdict_bytes)
    loaded = load_strategy_backtest_policy(policy_path)
    assert isinstance(loaded, StrategyBacktestPolicy)
    assert payload["policy_version"] == "strategy-backtest-v1"
    assert len(payload["policy_checksum"]) == 64
    assert payload["snapshot"]["snapshot_id"] == "m6-6g-synthetic-v1"
    assert len(payload["snapshot"]["rows_sha256"]) == 64
    assert len(payload["snapshot"]["sample_list_sha256"]) == 64


def test_changing_one_policy_value_changes_the_verdict(tmp_path: Path) -> None:
    """정책 값이 판정에 닿는다 — 닿지 않으면 checksum 대조(우회 ②)가 아무것도 막지
    못한다."""
    uri = _snapshot_dir(tmp_path)
    baseline = _run(uri, _derived_policy(tmp_path / "base"))
    tweaked = _run(
        uri,
        _derived_policy(
            tmp_path / "tweak", {"verdict.min_relative_improvement": "0.80"}
        ),
    )
    assert baseline.verdict_bytes != tweaked.verdict_bytes
    assert baseline.checksum != tweaked.checksum


def test_every_strategy_scores_the_same_notice_set(tmp_path: Path) -> None:
    """전략 간 표본 동일성 — 어긋나면 `run` 이 멈춘다. 여기서는 멈추지 않았음을 본다."""
    payload = _main_variant(
        _run(_snapshot_dir(tmp_path), _derived_policy(tmp_path)).verdict_bytes
    )
    counts = {
        sum(window["row_count"] for window in item["windows"])
        for item in payload["strategies"]
    }
    assert len(counts) == 1
    assert counts.pop() == payload["scored_notice_count"]


def test_exclusion_counts_report_every_reason(tmp_path: Path) -> None:
    payload = _main_variant(
        _run(_snapshot_dir(tmp_path), _derived_policy(tmp_path)).verdict_bytes
    )
    assert len(payload["exclusions"]) == 20
    assert all(isinstance(value, int) for value in payload["exclusions"].values())
    # ⑪⑫ 는 한 번도 발화하지 않는다 — 0 이 「가르지 못했다」·「들어오지 않았다」임을
    # `undecidable` 이 따로 말한다(D-6G-21).
    assert payload["exclusions"]["LOCAL_GOVERNMENT"] == 0
    assert payload["exclusions"]["FOREIGN_CAPITAL"] == 0


def test_verdict_carries_no_notice_identifier(tmp_path: Path) -> None:
    """판정 JSON 에 공고 식별자가 없다(D-6G-9) — fixture 의 키 해시를 직접 찾는다."""
    manifest_bytes, rows_bytes = build_files()
    del manifest_bytes
    first_key = json.loads(rows_bytes.decode("utf-8").splitlines()[0])["notice"][
        "notice_key_hash"
    ]
    verdict = _run(_snapshot_dir(tmp_path), _derived_policy(tmp_path)).verdict_bytes
    assert first_key.encode("utf-8") not in verdict


def test_unreadable_snapshot_is_a_result_not_an_exception(tmp_path: Path) -> None:
    outcome = run_backtest_job(
        snapshot_uri="s3://nope",
        backtest_policy_path=_derived_policy(tmp_path),
        inference_policy_path=_INFERENCE_POLICY,
    )
    assert isinstance(outcome, JobFailed)
    assert outcome.reason is JobFailureReason.SNAPSHOT_UNREADABLE


def test_rejected_policy_is_a_result_not_an_exception(tmp_path: Path) -> None:
    broken = tmp_path / "broken.yaml"
    broken.write_text("verdict.alpha: [unclosed\n", encoding="utf-8")
    outcome = run_backtest_job(
        snapshot_uri=_snapshot_dir(tmp_path),
        backtest_policy_path=broken,
        inference_policy_path=_INFERENCE_POLICY,
    )
    assert isinstance(outcome, JobFailed)
    assert outcome.reason is JobFailureReason.BACKTEST_POLICY_REJECTED


def test_bonferroni_denominator_matches_the_number_of_primary_hypotheses(
    tmp_path: Path,
) -> None:
    """주 가설 수와 Bonferroni 분모가 갈리면 job 이 시작도 하지 않는다(우회 ⑥)."""
    outcome = run_backtest_job(
        snapshot_uri=_snapshot_dir(tmp_path),
        backtest_policy_path=_derived_policy(
            tmp_path, {"verdict.primary_hypothesis_count": "2"}
        ),
        inference_policy_path=_INFERENCE_POLICY,
    )
    assert isinstance(outcome, JobFailed)
    assert outcome.reason is JobFailureReason.PRIMARY_HYPOTHESIS_COUNT_MISMATCH


def test_three_sample_variants_are_emitted_in_one_document(tmp_path: Path) -> None:
    """주 판정 + 민감도 둘(D-6G-21). 지자체 추정 규칙이 없으므로 (b) 판은 「추정
    불가」를 명시하고 아무것도 빼지 못한다 — 그것을 0 건으로 조용히 적지 않는다."""
    payload = json.loads(
        _run(_snapshot_dir(tmp_path), _derived_policy(tmp_path)).verdict_bytes
    )
    variants = payload["variants"]
    assert [item["variant"]["variant"] for item in variants] == [
        "MAIN",
        "EXCLUDE_WIDE_RESERVE_RANGE",
        "EXCLUDE_ESTIMATED_LOCAL_GOVERNMENT",
    ]
    estimated = variants[2]["variant"]
    assert estimated["estimate_available"] is False
    assert estimated["removed_count"] == 0


def test_strategy_assembly_is_the_preregistered_five() -> None:
    inference_policy = load_inference_policy(_INFERENCE_POLICY)
    assert isinstance(inference_policy, InferencePolicy)
    baseline, candidates = build_strategies(inference_policy)
    assert baseline.name == "S0"
    assert [item.name for item in candidates] == ["S1", "S2c", "S2b", "S2a", "S4"]
    assert "S3" not in {item.name for item in candidates}


@pytest.mark.parametrize("key", sorted(_TEST_OVERRIDES))
def test_derived_policy_only_relaxes_sample_size_axes(key: str) -> None:
    """파생 정책이 손대는 키가 **표본 크기·계산량 축**뿐임을 목록으로 잠근다 — 판정식
    축(Δ·유의수준·Bonferroni·비열등 한계·검정력)을 낮추는 길을 test 가 막는다."""
    judgement_axes = {
        "verdict.min_relative_improvement",
        "verdict.alpha",
        "verdict.primary_hypothesis_count",
        "verdict.ineligibility_noninferiority_margin",
        "verdict.target_power",
    }
    assert key not in judgement_axes
