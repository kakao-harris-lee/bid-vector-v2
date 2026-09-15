"""M5/5A 게이트 — import-linter 실제 계약 실행 + 양성 대조(설계 검토 (5)-2, RED 목록 1번,
verifier r1 F-1 수정 포함).

`tests/gates/fixtures/bad_serving/`·`bad_contracts_bypass/`(둘 다 독립 미니 프로젝트, 각자
`pyproject.toml`)가 **실제로 실패**함을 증명한다 — 계약이 문서에만 있고 아무것도 안 거르는
상태가 아님을 실행으로 확인한다(우회 (1)·(7) 방어). `good_serving/`은 그 반대 방향(F-1) —
승인 통로의 **간접** 연쇄가 실제로 열려 있는지 증명한다. 「막아야 할 것이 막힘」과 「열어야
할 것이 열림」을 짝으로 둔다(M4/4D-1 init≠gate 교훈과 같은 축).
"""

from __future__ import annotations

import shutil
import subprocess
import sys
import tempfile
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


def _read_features_forbidden_contract() -> dict[str, object]:
    """실제 `pyproject.toml`에서 `features`→DB/HTTP forbidden 계약 하나를 읽는다(verifier
    r1 H-3 — 이 함수가 실패하면 아래 두 test 가 **먼저** 이 자리에서 붉어진다, 실제 계약이
    지워졌을 때 양성 대조가 그 사실과 무관하게 계속 통과하는 것을 막는다)."""
    data = tomllib.loads(
        (_ML_ENGINE_ROOT / "pyproject.toml").read_text(encoding="utf-8")
    )
    contracts = data["tool"]["importlinter"]["contracts"]
    matching = [
        contract
        for contract in contracts
        if contract.get("type") == "forbidden"
        and "ml_engine.features" in contract.get("source_modules", [])
        and "sqlalchemy" in contract.get("forbidden_modules", [])
    ]
    assert matching, (
        "features 를 겨눈 DB forbidden 계약이 실제 pyproject.toml 에 없다(⑫)"
    )
    return matching[0]


def _render_contract_block(contract: dict[str, object]) -> str:
    """`contract`(파싱된 dict)를 fixture 전용 `[[tool.importlinter.contracts]]` 텍스트로
    되쓴다. 이 slice 는 새 TOML writer 의존성을 더하지 않는다(편집 규율) — 이 계약의 값
    형태(문자열 리스트 셋)만 다루는 최소 렌더러다."""

    def _array(values: object) -> str:
        return "[" + ", ".join(f'"{value}"' for value in values) + "]"

    lines = [
        "",
        "[[tool.importlinter.contracts]]",
        f"name = {contract['name']!r}",
        'type = "forbidden"',
        f"source_modules = {_array(contract['source_modules'])}",
        f"forbidden_modules = {_array(contract['forbidden_modules'])}",
        "",
    ]
    return "\n".join(lines)


def test_real_pyproject_has_features_forbidden_contract() -> None:
    """verifier r1 H-3(a) — 5A `test_forbidden_contract_forbids_bidvector_with_indirect_imports_allowed`
    와 같은 패턴. 실제 계약의 존재·형태를 tomllib 로 직접 단언한다(⑫, D-5C-13)."""
    contract = _read_features_forbidden_contract()
    for expected_source in (
        "ml_engine.features",
        "ml_engine.training",
        "ml_engine.evaluation",
        "ml_engine.registry",
    ):
        assert expected_source in contract["source_modules"]
    for expected_forbidden in (
        "sqlalchemy",
        "psycopg",
        "requests",
        "httpx",
        "celery",
        "ml_engine.inference",
        "ml_engine.serving",
    ):
        assert expected_forbidden in contract["forbidden_modules"]
    assert "ml_engine.adapters" not in contract["source_modules"]  # D-5C-13


def test_bad_features_db_fixture_is_broken_by_lint_imports() -> None:
    """M5/5C-1 ⑫(D-5C-13, verifier r1 H-3(b)) — `bad_features_db/`의 checked-in
    `pyproject.toml`은 계약을 갖지 않는다(스캐폴드뿐). 이 test 가 실제 `pyproject.toml`에서
    계약을 읽어 **임시 사본**에 덧쓴 뒤 실행한다 — 실제 계약을 지우면
    `_read_features_forbidden_contract`의 assert 에서 이 test 부터 먼저 붉어진다(위 (a)와
    같은 실패 지점을 공유 — 「계약 없음」이 두 test 모두를 붉힌다)."""
    contract = _read_features_forbidden_contract()
    with tempfile.TemporaryDirectory(prefix="bad-features-db-") as tmp:
        tmp_path = Path(tmp)
        shutil.copytree(
            _FIXTURES_ROOT / "bad_features_db", tmp_path, dirs_exist_ok=True
        )
        with (tmp_path / "pyproject.toml").open("a", encoding="utf-8") as handle:
            handle.write(_render_contract_block(contract))
        result = _run_lint_imports(tmp_path)
    assert result.returncode != 0
    assert "BROKEN" in result.stdout
    assert "sqlalchemy" in result.stdout


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
