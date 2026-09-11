"""M5/5A 게이트 — `ml_engine.registry.policy.load_policy` 형태(D-M5-6): version 필수,
미지 키 거부, frozen. 값은 여기 없다(정책 값 33개는 5C·5D)."""

from __future__ import annotations

import dataclasses
from pathlib import Path

import pytest

from ml_engine.registry.policy import Policy, PolicyError, load_policy


def _write_yaml(tmp_path: Path, text: str) -> Path:
    path = tmp_path / "policy.yaml"
    path.write_text(text, encoding="utf-8")
    return path


def test_version_required(tmp_path: Path) -> None:
    path = _write_yaml(tmp_path, "some_key: 1\n")
    with pytest.raises(PolicyError, match="version"):
        load_policy(path)


def test_empty_version_rejected(tmp_path: Path) -> None:
    path = _write_yaml(tmp_path, 'version: ""\n')
    with pytest.raises(PolicyError, match="version"):
        load_policy(path)


def test_unknown_key_rejected(tmp_path: Path) -> None:
    path = _write_yaml(tmp_path, "version: v1\nunknown_key: 1\n")
    with pytest.raises(PolicyError, match="미지"):
        load_policy(path)


def test_known_key_accepted(tmp_path: Path) -> None:
    path = _write_yaml(tmp_path, "version: v1\nmin_samples: 8\n")
    policy = load_policy(path, known_keys=frozenset({"min_samples"}))
    assert policy.version == "v1"
    assert policy.values == {"min_samples": 8}


def test_policy_is_frozen() -> None:
    policy = Policy(version="v1", values={})
    with pytest.raises(dataclasses.FrozenInstanceError):
        policy.version = "v2"


def test_non_mapping_yaml_rejected(tmp_path: Path) -> None:
    path = _write_yaml(tmp_path, "- 1\n- 2\n")
    with pytest.raises(PolicyError):
        load_policy(path)
