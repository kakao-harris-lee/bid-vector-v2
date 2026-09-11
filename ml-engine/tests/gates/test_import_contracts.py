"""M5/5A 게이트 — import-linter 실제 계약 실행 + 양성 대조(설계 검토 (5)-2, RED 목록 1번,
verifier r1 F-1 수정 포함).

`tests/gates/fixtures/bad_serving/`·`bad_contracts_bypass/`(둘 다 독립 미니 프로젝트, 각자
`pyproject.toml`)가 **실제로 실패**함을 증명한다 — 계약이 문서에만 있고 아무것도 안 거르는
상태가 아님을 실행으로 확인한다(우회 (1)·(7) 방어). `good_serving/`은 그 반대 방향(F-1) —
승인 통로의 **간접** 연쇄가 실제로 열려 있는지 증명한다. 「막아야 할 것이 막힘」과 「열어야
할 것이 열림」을 짝으로 둔다(M4/4D-1 init≠gate 교훈과 같은 축).
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
    """`ml_engine.features`가 `contracts` 재수출을 건너뛰고 `bidvector`를 **직접** import하면
    계속 실패해야 한다(우회 (7) — 재수출 하나만 허용, `allow_indirect_imports=true`로도
    안 열림 — F-1 수정이 F-2 방어를 되돌리지 않았는지 확인)."""
    result = _run_lint_imports(_FIXTURES_ROOT / "bad_contracts_bypass")
    assert result.returncode != 0
    assert "BROKEN" in result.stdout
    assert "bidvector" in result.stdout


def test_good_serving_fixture_approved_indirect_path_passes() -> None:
    """verifier r1 F-1 — 승인 통로(`from ml_engine.contracts import common_pb2`)를 쓰는
    `serving`이 `contracts`를 거쳐 간접적으로 `bidvector`에 닿아도 KEPT 여야 한다."""
    result = _run_lint_imports(_FIXTURES_ROOT / "good_serving")
    assert result.returncode == 0, result.stdout + result.stderr
    assert "KEPT" in result.stdout


def test_forbidden_contract_forbids_bidvector_with_indirect_imports_allowed() -> None:
    data = tomllib.loads(
        (_ML_ENGINE_ROOT / "pyproject.toml").read_text(encoding="utf-8")
    )
    contracts = data["tool"]["importlinter"]["contracts"]
    matching = [c for c in contracts if "bidvector" in c.get("forbidden_modules", [])]
    assert matching, "bidvector 를 막는 forbidden 계약이 pyproject.toml 에 없다"
    contract = matching[0]
    assert contract["allow_indirect_imports"] is True
    # verifier r1 F-2 — `_generated`는 이제 열거 대상이 아니다(패키지 트리 밖이라 그 이름의
    # import 경로 자체가 없다, test_generated_not_tracked.py가 구조적 폐쇄를 확인한다).
    assert "ml_engine.contracts._generated" not in contract["forbidden_modules"]
    assert "ml_engine.contracts" not in contract["source_modules"]
