"""M5/5A 게이트 — import-linter 실제 계약 실행 + 양성 대조(설계 검토 (5)-2, RED 목록 1번).

`tests/gates/fixtures/bad_serving/`·`bad_contracts_bypass/`(둘 다 독립 미니 프로젝트, 각자
`pyproject.toml`)가 **실제로 실패**함을 증명한다 — 계약이 문서에만 있고 아무것도 안 거르는
상태가 아님을 실행으로 확인한다(우회 (1)·(7) 방어).
"""

from __future__ import annotations

import subprocess
import sys
import tomllib
from pathlib import Path

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_LINT_IMPORTS_BIN = Path(sys.executable).parent / "lint-imports"
_FIXTURES_ROOT = Path(__file__).resolve().parent / "fixtures"


def _run_lint_imports(cwd: Path) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [str(_LINT_IMPORTS_BIN), "--config", "pyproject.toml", "--no-cache"],
        cwd=cwd,
        capture_output=True,
        text=True,
        check=False,
    )


def test_real_ml_engine_import_contracts_pass() -> None:
    result = _run_lint_imports(_ML_ENGINE_ROOT)
    assert result.returncode == 0, result.stdout + result.stderr


def test_bad_serving_fixture_is_broken_by_lint_imports() -> None:
    """`ml_engine.serving`이 `sqlalchemy`를 import하면 실패해야 한다(우회 (1))."""
    result = _run_lint_imports(_FIXTURES_ROOT / "bad_serving")
    assert result.returncode != 0
    assert "BROKEN" in result.stdout
    assert "sqlalchemy" in result.stdout


def test_bad_contracts_bypass_fixture_is_broken_by_lint_imports() -> None:
    """`ml_engine.features`가 `contracts` 재수출을 건너뛰고 `bidvector`를 직접 import하면
    실패해야 한다(우회 (7) — 재수출 하나만 허용)."""
    result = _run_lint_imports(_FIXTURES_ROOT / "bad_contracts_bypass")
    assert result.returncode != 0
    assert "BROKEN" in result.stdout
    assert "bidvector" in result.stdout


def test_forbidden_contract_covers_bidvector_and_generated_outside_contracts() -> None:
    data = tomllib.loads((_ML_ENGINE_ROOT / "pyproject.toml").read_text(encoding="utf-8"))
    contracts = data["tool"]["importlinter"]["contracts"]
    matching = [c for c in contracts if "bidvector" in c.get("forbidden_modules", [])]
    assert matching, "bidvector 를 막는 forbidden 계약이 pyproject.toml 에 없다"
    forbidden = matching[0]["forbidden_modules"]
    assert "ml_engine.contracts._generated" in forbidden
    assert "ml_engine.contracts" not in matching[0]["source_modules"]
