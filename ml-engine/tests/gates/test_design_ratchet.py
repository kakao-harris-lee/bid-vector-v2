"""M5/5A 게이트 — 래칫 한도가 ML-11.2 승인값(50/500)과 같음을 단언하고, allowlist
와일드카드 거부·양성 대조 표본(오버사이즈 함수·약한 `dict` 경계)이 실제로 잡히는지 확인
(설계 검토 (2) 우회 (3)·(10))."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

import pytest

from tools import design_ratchet

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_FIXTURE_FILE = (
    Path(__file__).resolve().parent / "fixtures" / "ratchet_violations" / "sample.py"
)


def test_approved_limits_match_ml_11_2() -> None:
    config = design_ratchet.load_config()
    assert config.function_soft_limit_lines == 50
    assert config.file_loc_soft_limit == 500


def test_allowlist_is_empty_at_5a() -> None:
    """D-5A-3·설계 검토 (1) — 5A 시점 allowlist 는 비어 있어야 한다."""
    config = design_ratchet.load_config()
    assert config.allowlist == frozenset()


def test_wildcard_allowlist_path_is_rejected(tmp_path: Path) -> None:
    bad_pyproject = tmp_path / "pyproject.toml"
    bad_pyproject.write_text(
        "[tool.design-ratchet]\n"
        "function_soft_limit_lines = 50\n"
        "file_loc_soft_limit = 500\n"
        'target_dirs = ["src/ml_engine"]\n'
        "[[tool.design-ratchet.allowlist]]\n"
        'path = "src/ml_engine/*.py"\n'
        'metric = "functions_over_soft_limit"\n',
        encoding="utf-8",
    )
    with pytest.raises(ValueError, match="와일드카드"):
        design_ratchet.load_config(bad_pyproject)


def test_oversized_function_is_flagged_by_scan() -> None:
    source = _FIXTURE_FILE.read_text(encoding="utf-8")
    config = design_ratchet.load_config()
    metrics = design_ratchet.scan_source(source, config)
    assert metrics.functions_over_soft_limit == 1


def test_weak_dict_boundary_is_flagged_by_scan() -> None:
    source = _FIXTURE_FILE.read_text(encoding="utf-8")
    config = design_ratchet.load_config()
    metrics = design_ratchet.scan_source(source, config)
    assert metrics.dict_boundary_functions == 1


def test_fixture_directory_is_outside_target_dirs() -> None:
    """양성 대조 표본이 실제 S-6 스캔에는 안 걸림을 확인(`tests/`는 target_dirs 밖)."""
    config = design_ratchet.load_config()
    assert not any(
        _FIXTURE_FILE.is_relative_to(_ML_ENGINE_ROOT / target)
        for target in config.target_dirs
    )


def test_cli_check_on_real_source_exits_zero() -> None:
    result = subprocess.run(
        [sys.executable, "tools/design_ratchet.py", "--check"],
        cwd=_ML_ENGINE_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr
