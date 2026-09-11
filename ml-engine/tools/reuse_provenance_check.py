#!/usr/bin/env python3
"""ADR 0009 D-6 의 두 자리 대조 — 이식 모듈 docstring 의 출처 포인터와
`reports/evidence/m5/*/reuse.md`가 **같은 모듈에 대해 같은 원본 경로·기준 commit**을
말하는지 검사한다(D-6.1 #4 술어: (ㄱ) 있음 — 양쪽에 기록이 있다, (ㄴ) 같음 — 값이 같다).

형태(D-6.2, 5A 가 정한다): 이식 모듈은 docstring **첫 줄**에 `Reuse: <원본 경로>@<commit>`을
적는다. `reuse.md`는 마크다운 표로 `module`·`original_path`·`commit` 열을 갖는다(순서 무관,
헤더 이름으로 찾는다). `module` 값은 이 저장소 루트 기준 POSIX 상대경로(조인 키).

대상 집합(D-6.1 #5): `ml-engine/src/ml_engine`·`ml-engine/tools` 아래 `.py` 중 docstring
첫 줄이 `Reuse:` 패턴에 맞는 파일만(그 밖은 이식이 아니라 신규 작성 — 대상이 아니다).
"""

from __future__ import annotations

import argparse
import ast
import re
import sys
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parents[2]
_TARGET_DIRS = ("ml-engine/src/ml_engine", "ml-engine/tools")
_REUSE_LINE_PATTERN = re.compile(
    r"^Reuse:\s*(?P<path>\S+)@(?P<commit>[0-9a-f]{7,40})\s*$"
)
_SEPARATOR_CELL = re.compile(r"^:?-{3,}:?$")


def find_reuse_pointers(
    repo_root: Path, target_dirs: tuple[str, ...] = _TARGET_DIRS
) -> dict[str, tuple[str, str]]:
    """이식 모듈(docstring 첫 줄이 `Reuse: ...`)의 `{모듈 식별자: (원본 경로, commit)}`."""
    pointers: dict[str, tuple[str, str]] = {}
    for target_dir in target_dirs:
        for path in sorted((repo_root / target_dir).rglob("*.py")):
            if "__pycache__" in path.parts or "_generated" in path.parts:
                continue
            tree = ast.parse(path.read_text(encoding="utf-8"))
            docstring = ast.get_docstring(tree, clean=False)
            if not docstring:
                continue
            match = _REUSE_LINE_PATTERN.match(docstring.splitlines()[0])
            if not match:
                continue
            module_id = path.relative_to(repo_root).as_posix()
            pointers[module_id] = (match.group("path"), match.group("commit"))
    return pointers


def _is_separator_row(cells: list[str]) -> bool:
    return bool(cells) and all(_SEPARATOR_CELL.match(cell) for cell in cells)


def parse_reuse_table(text: str) -> dict[str, tuple[str, str]]:
    """마크다운 표의 `module`·`original_path`·`commit` 열을 `{module: (path, commit)}`로."""
    rows = [
        [cell.strip() for cell in stripped.strip("|").split("|")]
        for stripped in (line.strip() for line in text.splitlines())
        if stripped.startswith("|")
    ]
    data_rows = [row for row in rows if not _is_separator_row(row)]
    if not data_rows:
        return {}
    header = [cell.lower() for cell in data_rows[0]]
    if not {"module", "original_path", "commit"} <= set(header):
        return {}
    module_idx, path_idx, commit_idx = (
        header.index(name) for name in ("module", "original_path", "commit")
    )
    result: dict[str, tuple[str, str]] = {}
    for row in data_rows[1:]:
        if len(row) <= max(module_idx, path_idx, commit_idx):
            continue
        result[row[module_idx]] = (row[path_idx], row[commit_idx])
    return result


def check(repo_root: Path, evidence_paths: list[Path]) -> list[str]:
    """`{docstring 포인터} ↔ {evidence 기록}` 대조 위반 설명 목록(빈 목록 = 위반 0)."""
    pointers = find_reuse_pointers(repo_root)
    evidence: dict[str, tuple[str, str]] = {}
    for evidence_path in evidence_paths:
        evidence.update(parse_reuse_table(evidence_path.read_text(encoding="utf-8")))

    violations: list[str] = []
    for module_id, (path, commit) in sorted(pointers.items()):
        if module_id not in evidence:
            violations.append(
                f"{module_id}: evidence 에 기록 없음(docstring 은 {path}@{commit})"
            )
            continue
        evidence_path, evidence_commit = evidence[module_id]
        if (evidence_path, evidence_commit) != (path, commit):
            violations.append(
                f"{module_id}: docstring({path}@{commit}) != evidence({evidence_path}@{evidence_commit})"
            )
    return violations


def _default_evidence_paths(repo_root: Path) -> list[Path]:
    return sorted((repo_root / "reports" / "evidence" / "m5").glob("*/reuse.md"))


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="ADR 0009 D-6 재활용 출처 두 자리 대조"
    )
    parser.add_argument(
        "--evidence",
        action="append",
        type=Path,
        default=None,
        help="reuse.md 대체 경로(반복 가능). 생략하면 reports/evidence/m5/*/reuse.md 전체.",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    evidence_paths = (
        args.evidence if args.evidence else _default_evidence_paths(_REPO_ROOT)
    )
    violations = check(_REPO_ROOT, evidence_paths)
    if not violations:
        print("재활용 출처 두 자리 일치 — 위반 0")
        return 0
    print(f"재활용 출처 불일치 {len(violations)}건:")
    for violation in violations:
        print(f"  {violation}")
    return 1


if __name__ == "__main__":
    sys.exit(main())
