"""RED — `ml_engine.training.policy`(D-5C-3·D-5C-7). `load_training_policy`는 5A
`load_policy`(known_keys 전수) 위에 `min_training_rows ≥ 1` 불변식을 얹는다."""

from __future__ import annotations

from pathlib import Path

import pytest

from ml_engine.training.policy import (
    SHIPPED_TRAINING_POLICY_VERSION,
    PolicyRejected,
    PolicyRejectionReason,
    TrainingPolicy,
    load_training_policy,
)

_SHIPPED_YAML = Path(__file__).resolve().parents[2] / "policy" / "training-v1.yaml"


def test_shipped_policy_file_matches_policy_values_md() -> None:
    """verifier r1 L-7 — 이 test 는 `policy-values.md`를 파싱하지 않는다. 그 표 §1 의 값을
    이 파일에 하드코딩해 실제 `policy/training-v1.yaml`과 대조한다(사람이 표와 눈으로
    대조 확인) — 이름이 시사하는 것보다 약한 대조다(5A/5B 와 같은 관행)."""
    result = load_training_policy(_SHIPPED_YAML)
    assert isinstance(result, TrainingPolicy)
    assert result.version == SHIPPED_TRAINING_POLICY_VERSION
    assert result.min_training_rows == 500


def test_load_training_policy_missing_file_is_rejected(tmp_path: Path) -> None:
    result = load_training_policy(tmp_path / "does-not-exist.yaml")
    assert isinstance(result, PolicyRejected)


def test_load_training_policy_unknown_key_is_rejected(tmp_path: Path) -> None:
    path = tmp_path / "policy.yaml"
    path.write_text("version: training-v1\nmin_training_rows: 500\nextra_key: 1\n")
    result = load_training_policy(path)
    assert isinstance(result, PolicyRejected)
    assert result.reason == PolicyRejectionReason.MALFORMED


@pytest.mark.parametrize("value", [0, -1, "500", True, 1.5])
def test_load_training_policy_invalid_min_training_rows_is_rejected(
    tmp_path: Path, value: object
) -> None:
    path = tmp_path / "policy.yaml"
    path.write_text(f"version: training-v1\nmin_training_rows: {value!r}\n")
    result = load_training_policy(path)
    assert isinstance(result, PolicyRejected)
    assert result.reason == PolicyRejectionReason.INVALID_MIN_TRAINING_ROWS


def test_training_policy_direct_construction_enforces_invariant() -> None:
    with pytest.raises(ValueError):
        TrainingPolicy(version="x", min_training_rows=0)


def test_load_training_policy_malformed_yaml_syntax_is_rejected(
    tmp_path: Path,
) -> None:
    """code-reviewer PR #13 HIGH-2 — 문법이 깨진 YAML(닫히지 않은 flow sequence)은
    5A `load_policy`의 `yaml.safe_load`가 `yaml.YAMLError`를 던지는데, 이전에는
    `except (PolicyError, OSError)`가 그것을 잡지 못해 예외가 그대로 전파됐다(계약
    위반 — "실패는 결과 타입, 예외 아님"). 지금은 결과 타입으로 옮겨진다."""
    path = tmp_path / "policy.yaml"
    path.write_text("version: training-v1\nmin_training_rows: [1, 2\n")
    result = load_training_policy(path)
    assert isinstance(result, PolicyRejected)
    assert result.reason == PolicyRejectionReason.MALFORMED
