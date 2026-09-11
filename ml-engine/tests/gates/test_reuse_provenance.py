"""M5/5A 게이트 — ADR 0009 D-6 재활용 출처 **양방향** 대조(S-7 을 pytest 안에서도 확인,
verifier r1 F-3)."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]


def _run(*extra_args: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, "tools/reuse_provenance_check.py", *extra_args],
        cwd=_ML_ENGINE_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )


def test_reuse_provenance_check_passes_on_real_evidence() -> None:
    result = _run()
    assert result.returncode == 0, result.stdout + result.stderr


def test_reuse_provenance_check_fails_on_mismatched_evidence() -> None:
    result = _run("--evidence", "tests/gates/fixtures/reuse-mismatch.md")
    assert result.returncode != 0


def test_reuse_provenance_check_fails_when_evidence_claims_missing_pointer() -> None:
    """verifier r1 F-3 — 역방향. `reuse.md` 행이 있는데 그 모듈 docstring 에 `Reuse:`
    포인터가 없으면(또는 지워지면) 실패해야 한다. `generate_contracts.py`는 신규 작성이라
    실제로 포인터가 없다 — 이 fixture 가 그 모듈에 가짜 evidence 행을 붙인다."""
    result = _run("--evidence", "tests/gates/fixtures/reuse-claims-fake-pointer.md")
    assert result.returncode != 0
    assert "포인터가 없다" in result.stdout
