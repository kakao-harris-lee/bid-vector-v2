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
