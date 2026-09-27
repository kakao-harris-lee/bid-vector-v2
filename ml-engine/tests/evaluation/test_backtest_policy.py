"""RED — M6/6G D-6G-6. 전략 백테스트 판정 정책은 **코드 리터럴이 아니라 versioned
파일**에서만 온다(ML-07 acceptance ③④ 승계). 이 test 는 (a) 출하 파일이 A-3 승인값
그대로인지 (b) 미지 키·깨진 YAML·범위 밖 값이 **예외가 아니라 결과 타입**으로 거부되는지
(c) checksum 이 값 하나만 바뀌어도 달라지는지 (d) 로더가 `path` 하나만 받는지를 잠근다.

`institution.*` 두 값은 `policy/inference-v1.yaml` 의 같은 제도 상수와 **대조**한다 —
evaluation 층이 inference 를 import 할 수 없어 값을 따로 나르는 자리라, 두 파일이 조용히
갈리는 것이 이 slice 고유의 표류 표면이다."""

from __future__ import annotations

import inspect
from pathlib import Path

import pytest
import yaml

from ml_engine.evaluation.backtest.policy import (
    SHIPPED_STRATEGY_BACKTEST_POLICY_VERSION,
    StrategyBacktestPolicy,
    load_strategy_backtest_policy,
    strategy_backtest_policy_checksum,
)
from ml_engine.evaluation.policy import PolicyRejected, PolicyRejectionReason

_POLICY_DIR = Path(__file__).resolve().parents[2] / "policy"
_SHIPPED_PATH = _POLICY_DIR / "strategy-backtest-v1.yaml"
_INFERENCE_PATH = _POLICY_DIR / "inference-v1.yaml"


def _shipped() -> StrategyBacktestPolicy:
    loaded = load_strategy_backtest_policy(_SHIPPED_PATH)
    assert isinstance(loaded, StrategyBacktestPolicy), loaded
    return loaded


def _write(tmp_path: Path, text: str) -> Path:
    path = tmp_path / "policy.yaml"
    path.write_text(text, encoding="utf-8")
    return path


def _shipped_text() -> str:
    return _SHIPPED_PATH.read_text(encoding="utf-8")


def test_shipped_policy_carries_the_approved_a3_values() -> None:
    policy = _shipped()
    assert policy.version == SHIPPED_STRATEGY_BACKTEST_POLICY_VERSION
    assert policy.verdict.min_relative_improvement == pytest.approx(0.20)
    assert policy.verdict.alpha == pytest.approx(0.05)
    assert policy.verdict.primary_hypothesis_count == 3
    assert policy.verdict.ineligibility_noninferiority_margin == pytest.approx(0.01)
    assert policy.verdict.target_power == pytest.approx(0.80)
    assert policy.verdict.min_window_count == 3
    assert policy.verdict.min_window_rows == 483
    assert len(policy.stability_seeds) == 5


def test_shipped_institution_constants_match_the_inference_policy() -> None:
    """제도 상수(15구간·4추첨)는 두 정책 파일에 같은 값으로 있어야 한다 — 층 계약상
    한쪽을 다른 쪽에서 읽을 수 없으므로 대조가 유일한 잠금이다."""
    policy = _shipped()
    inference_raw = yaml.safe_load(_INFERENCE_PATH.read_text(encoding="utf-8"))
    assert (
        policy.institution.reserve_price_count
        == (inference_raw["reserve.expected_price_count"])
    )
    assert policy.institution.draw_count == inference_raw["reserve.draw_count"]


def test_shipped_seeds_match_the_evaluation_policy_seeds() -> None:
    """seed 다섯은 5C-2 가 고정한 값 그대로 — 6G 가 새 다섯을 지어내면 그 자체가 seed
    쇼핑 표면이다(우회 ③)."""
    policy = _shipped()
    evaluation_raw = yaml.safe_load(
        (_POLICY_DIR / "evaluation-v1.yaml").read_text(encoding="utf-8")
    )
    expected = tuple(
        evaluation_raw[f"stability_seeds.{index}"]
        for index in range(len(policy.stability_seeds))
    )
    assert policy.stability_seeds == expected


def test_unknown_key_is_rejected_as_a_result_not_an_exception(tmp_path: Path) -> None:
    path = _write(tmp_path, _shipped_text() + "\nverdict.alpha_relaxed: 0.5\n")
    rejected = load_strategy_backtest_policy(path)
    assert isinstance(rejected, PolicyRejected)
    assert rejected.reason is PolicyRejectionReason.MALFORMED


def test_malformed_yaml_is_rejected_without_raising(tmp_path: Path) -> None:
    path = _write(tmp_path, "verdict.alpha: [unclosed\n")
    rejected = load_strategy_backtest_policy(path)
    assert isinstance(rejected, PolicyRejected)
    assert rejected.reason is PolicyRejectionReason.MALFORMED


def test_missing_key_is_rejected(tmp_path: Path) -> None:
    text = "\n".join(
        line
        for line in _shipped_text().splitlines()
        if not line.startswith("verdict.alpha:")
    )
    rejected = load_strategy_backtest_policy(_write(tmp_path, text + "\n"))
    assert isinstance(rejected, PolicyRejected)
    assert rejected.reason is PolicyRejectionReason.INVALID_VALUE


@pytest.mark.parametrize(
    ("key", "value"),
    [
        ("verdict.alpha", "0.0"),
        ("verdict.alpha", "1.5"),
        ("verdict.min_relative_improvement", "-0.1"),
        ("verdict.target_power", "1.2"),
        ("verdict.primary_hypothesis_count", "0"),
        ("verdict.min_window_count", "0"),
        ("verdict.min_window_rows", "1"),
        ("verdict.ineligibility_noninferiority_margin", "-0.01"),
        ("window.days", "0"),
        ("window.embargo_days", "-1"),
        ("institution.reserve_price_count", "3"),
        ("institution.draw_count", "0"),
        ("floor.rate_band_low", "-0.5"),
        ("floor.rate_band_high", "0.1"),
        ("strategy.s4_iteration_count", "0"),
        ("strategy.s4_grid_size", "1"),
        ("strategy.s4_grid_span_bp", "-1.0"),
        ("strategy.s4_min_competitor_samples", "0"),
        ("fit.alpha", "0.0"),
        ("fit.min_sample_count", "0"),
        ("fit.max_bin_ratio_deviation", "-0.01"),
    ],
)
def test_out_of_range_values_are_rejected(tmp_path: Path, key: str, value: str) -> None:
    lines = [
        f"{key}: {value}" if line.startswith(f"{key}:") else line
        for line in _shipped_text().splitlines()
    ]
    rejected = load_strategy_backtest_policy(_write(tmp_path, "\n".join(lines) + "\n"))
    assert isinstance(rejected, PolicyRejected), f"{key}={value} 가 통과했다"
    assert rejected.reason is PolicyRejectionReason.INVALID_VALUE


def test_non_finite_values_are_rejected(tmp_path: Path) -> None:
    lines = [
        "verdict.alpha: .nan" if line.startswith("verdict.alpha:") else line
        for line in _shipped_text().splitlines()
    ]
    rejected = load_strategy_backtest_policy(_write(tmp_path, "\n".join(lines) + "\n"))
    assert isinstance(rejected, PolicyRejected)
    assert rejected.reason is PolicyRejectionReason.INVALID_VALUE


def test_duplicate_seeds_are_rejected(tmp_path: Path) -> None:
    lines = [
        "stability_seeds.1: 20260812" if line.startswith("stability_seeds.1:") else line
        for line in _shipped_text().splitlines()
    ]
    rejected = load_strategy_backtest_policy(_write(tmp_path, "\n".join(lines) + "\n"))
    assert isinstance(rejected, PolicyRejected)
    assert rejected.reason is PolicyRejectionReason.INVALID_VALUE


def test_checksum_changes_when_any_single_value_changes(tmp_path: Path) -> None:
    baseline = strategy_backtest_policy_checksum(_shipped())
    assert len(baseline) == 64
    lines = [
        "verdict.min_relative_improvement: 0.21"
        if line.startswith("verdict.min_relative_improvement:")
        else line
        for line in _shipped_text().splitlines()
    ]
    tweaked = load_strategy_backtest_policy(_write(tmp_path, "\n".join(lines) + "\n"))
    assert isinstance(tweaked, StrategyBacktestPolicy)
    assert strategy_backtest_policy_checksum(tweaked) != baseline


def test_checksum_is_stable_across_comment_only_edits(tmp_path: Path) -> None:
    path = _write(tmp_path, "# 주석만 더한다\n" + _shipped_text())
    reloaded = load_strategy_backtest_policy(path)
    assert isinstance(reloaded, StrategyBacktestPolicy)
    assert strategy_backtest_policy_checksum(reloaded) == (
        strategy_backtest_policy_checksum(_shipped())
    )


def test_loader_takes_only_a_path_parameter() -> None:
    """CLI·환경변수로 값을 주입하는 추가 매개변수가 없다(ML-07 acceptance ④)."""
    signature = inspect.signature(load_strategy_backtest_policy)
    assert list(signature.parameters) == ["path"]


def test_alpha_after_bonferroni_is_the_only_alpha_the_verdict_uses() -> None:
    """Bonferroni 보정은 판정 코드가 아니라 정책 객체가 낸다 — 보정을 잊은 경로가
    생기지 않게(우회 ⑥)."""
    policy = _shipped()
    expected = policy.verdict.alpha / policy.verdict.primary_hypothesis_count
    assert policy.verdict.primary_alpha == pytest.approx(expected)
    assert policy.verdict.primary_alpha < policy.verdict.alpha
