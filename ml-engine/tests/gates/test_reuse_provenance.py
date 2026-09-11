"""M5/5A 게이트 — ADR 0009 D-6 재활용 출처 두 자리 대조(S-7 을 pytest 안에서도 확인)."""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]


def test_reuse_provenance_check_passes_on_real_evidence() -> None:
    result = subprocess.run(
        [sys.executable, "tools/reuse_provenance_check.py"],
        cwd=_ML_ENGINE_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode == 0, result.stdout + result.stderr


def test_reuse_provenance_check_fails_on_mismatched_evidence() -> None:
    result = subprocess.run(
        [
            sys.executable,
            "tools/reuse_provenance_check.py",
            "--evidence",
            "tests/gates/fixtures/reuse-mismatch.md",
        ],
        cwd=_ML_ENGINE_ROOT,
        capture_output=True,
        text=True,
        check=False,
    )
    assert result.returncode != 0
