"""M5/5A 게이트 — 생성 stub 은 VCS 밖이어야 하고(D-5A-0 (b)), 패키지 트리 밖이라
`ml_engine.contracts.<하위>` 라는 Python import 경로 자체가 없어야 한다(verifier r1 F-2 —
트리 안에 있으면 grimp 그래프에 노드로 안 잡혀 forbidden 열거가 아무것도 못 걸렀다. 열거
대신 경로를 없앴다 — 우회 (8))."""

from __future__ import annotations

import importlib
import pkgutil
import subprocess
from pathlib import Path

import pytest

import ml_engine.contracts

_REPO_ROOT = Path(__file__).resolve().parents[3]


def test_generated_dir_has_no_tracked_files() -> None:
    result = subprocess.run(
        ["git", "ls-files", "ml-engine/.contracts-generated"],
        cwd=_REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    tracked = [line for line in result.stdout.splitlines() if line]
    assert not tracked, f"생성 stub 이 커밋됐다: {tracked}"


def test_contracts_package_has_no_generated_submodule_on_disk() -> None:
    """(ㄱ) — `ml_engine.contracts._generated` 는 더는 존재하는 Python 경로가 아니다."""
    with pytest.raises(ModuleNotFoundError):
        importlib.import_module("ml_engine.contracts._generated")


def test_contracts_package_path_has_no_submodules() -> None:
    """(ㄴ) — `ml_engine.contracts` 자신의 패키지 디렉터리(`src/ml_engine/contracts/`)에는
    `__init__.py` 하나만 있고 그 밖의 모듈/하위 패키지가 없다."""
    submodules = list(pkgutil.iter_modules(ml_engine.contracts.__path__))
    assert submodules == [], (
        f"contracts 패키지 디렉터리에 예상 밖 하위 모듈: {submodules}"
    )
