"""M5/5A 게이트 — 생성 stub 은 VCS 밖이어야 한다(D-5A-0 (b), 설계 검토 (1) 「생성물 유출」,
우회 (8))."""

from __future__ import annotations

import subprocess
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parents[3]


def test_generated_dir_has_no_tracked_files_except_gitkeep() -> None:
    result = subprocess.run(
        ["git", "ls-files", "ml-engine/src/ml_engine/contracts/_generated"],
        cwd=_REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    )
    tracked = [line for line in result.stdout.splitlines() if line and not line.endswith(".gitkeep")]
    assert not tracked, f"생성 stub 이 커밋됐다: {tracked}"
