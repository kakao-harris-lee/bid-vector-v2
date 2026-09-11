#!/usr/bin/env python3
"""Reuse: bid-vector/scripts/_design_ratchet_scan.py@ed4b06c

설계 래칫 — 함수 길이·파일 크기·`dict[str, Any]` 약한 경계를 AST 로 센다(ML-11.2).

legacy(`bid-vector/scripts/_design_ratchet_scan.py`)의 약한 경계 판정(`is_weak_annotation`)과
LOC 밴드 계산을 그대로 가져왔다. **가져오지 않은 것**(`reports/evidence/m5/5a/reuse.md` 「수행한
수정」): baseline 파일 비교·delta 출력·클론 탐지(`_design_ratchet_clones.py`)·`json` 직접 호출·
`ENVIRONMENT` sniff·Celery 미검증 payload 지표 — legacy 저장소 고유 관심사이고 ml-engine 에는
Celery·직접 `json` 호출 정책이 없다. `pydantic.StrictModel` 데이터 계약도 `dataclasses`로
바꿨다(ml-engine 은 pydantic 을 의존하지 않는다). 가장 큰 차이: **baseline allowance 모델을
버렸다**(D-5A-3) — legacy 는 "감소는 항상 통과, 증가만 차단"이지만 여기는 **위반 0 또는
`pyproject.toml [tool.design-ratchet] allowlist`의 정확 경로 등재**만 허용한다(와일드카드 거부).

사용::

    uv run python tools/design_ratchet.py         # 위반 있으면 exit 1
"""

from __future__ import annotations

import argparse
import ast
import sys
import tomllib
from dataclasses import dataclass
from pathlib import Path

_REPO_ROOT = Path(__file__).resolve().parent.parent
_PYPROJECT_PATH = _REPO_ROOT / "pyproject.toml"

_WEAK_BOUNDARY_ANNOTATIONS = frozenset(
    {"dict", "Dict", "Any", "Mapping", "MutableMapping", "object"}
)
_UNION_WRAPPER_ANNOTATIONS = frozenset({"Optional", "Union"})
_CONTAINER_ANNOTATIONS = frozenset(
    {
        "list",
        "List",
        "set",
        "Set",
        "frozenset",
        "FrozenSet",
        "tuple",
        "Tuple",
        "Sequence",
        "Iterable",
    }
)
_METRIC_NAMES: tuple[str, ...] = (
    "functions_over_soft_limit",
    "file_loc_band",
    "dict_boundary_functions",
)


@dataclass(frozen=True)
class FileMetrics:
    functions_over_soft_limit: int = 0
    file_loc_band: int = 0
    dict_boundary_functions: int = 0

    def is_clean(self) -> bool:
        return not any(getattr(self, metric) for metric in _METRIC_NAMES)


@dataclass(frozen=True)
class RatchetConfig:
    function_soft_limit_lines: int
    file_loc_soft_limit: int
    target_dirs: tuple[str, ...]
    allowlist: frozenset[tuple[str, str]]  # (path, metric)


def load_config(pyproject_path: Path = _PYPROJECT_PATH) -> RatchetConfig:
    data = tomllib.loads(pyproject_path.read_text(encoding="utf-8"))
    section = data.get("tool", {}).get("design-ratchet", {})
    allowlist_entries = section.get("allowlist", [])
    allowlist: set[tuple[str, str]] = set()
    for entry in allowlist_entries:
        path = entry["path"]
        metric = entry["metric"]
        if any(char in path for char in "*?[]"):
            raise ValueError(
                f"allowlist 경로는 정확 경로만 허용됩니다(와일드카드 거부): {path!r}"
            )
        allowlist.add((path, metric))
    return RatchetConfig(
        function_soft_limit_lines=section["function_soft_limit_lines"],
        file_loc_soft_limit=section["file_loc_soft_limit"],
        target_dirs=tuple(section["target_dirs"]),
        allowlist=frozenset(allowlist),
    )


def _annotation_name(node: ast.expr | None) -> str | None:
    if isinstance(node, ast.Name):
        return node.id
    if isinstance(node, ast.Subscript):
        return _annotation_name(node.value)
    if isinstance(node, ast.Attribute):
        return node.attr
    return None


def _subscript_arguments(node: ast.expr | None) -> list[ast.expr]:
    if not isinstance(node, ast.Subscript):
        return []
    sliced = node.slice
    return list(sliced.elts) if isinstance(sliced, ast.Tuple) else [sliced]


def is_weak_annotation(node: ast.expr | None) -> bool:
    """검증되지 않는 `dict`/`Any` 경계인가(legacy `_design_ratchet_scan.is_weak_annotation`
    과 같은 판정 — union/컨테이너 래퍼를 벗겨 멤버 단위로 재귀 판정한다)."""
    if isinstance(node, ast.BinOp) and isinstance(node.op, ast.BitOr):
        return is_weak_annotation(node.left) or is_weak_annotation(node.right)
    name = _annotation_name(node)
    if name in _UNION_WRAPPER_ANNOTATIONS or name in _CONTAINER_ANNOTATIONS:
        return any(is_weak_annotation(arg) for arg in _subscript_arguments(node))
    if name not in _WEAK_BOUNDARY_ANNOTATIONS:
        return False
    if not isinstance(node, ast.Subscript):
        return True
    arguments = _subscript_arguments(node)
    return bool(arguments) and is_weak_annotation(arguments[-1])


_FunctionNode = ast.FunctionDef | ast.AsyncFunctionDef


def _all_arguments(node: _FunctionNode) -> list[ast.arg]:
    arguments = [*node.args.posonlyargs, *node.args.args, *node.args.kwonlyargs]
    arguments.extend(
        extra for extra in (node.args.vararg, node.args.kwarg) if extra is not None
    )
    return arguments


def _has_weak_boundary(node: _FunctionNode) -> bool:
    if is_weak_annotation(node.returns):
        return True
    return any(
        is_weak_annotation(argument.annotation) for argument in _all_arguments(node)
    )


def _function_line_span(node: _FunctionNode) -> int:
    return (node.end_lineno or node.lineno) - node.lineno + 1


def file_loc_band(source: str, soft_limit: int) -> int:
    """`soft_limit`줄 초과분을 25줄 밴드로 센다(legacy `file_loc_band`와 같은 판정)."""
    loc = len(source.splitlines())
    if loc <= soft_limit:
        return 0
    return -(-loc // 25)  # ceil division


def scan_source(source: str, config: RatchetConfig) -> FileMetrics:
    tree = ast.parse(source)
    functions = [
        node
        for node in ast.walk(tree)
        if isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef))
    ]
    return FileMetrics(
        functions_over_soft_limit=sum(
            1
            for node in functions
            if _function_line_span(node) > config.function_soft_limit_lines
        ),
        file_loc_band=file_loc_band(source, config.file_loc_soft_limit),
        dict_boundary_functions=sum(
            1 for node in functions if _has_weak_boundary(node)
        ),
    )


def scan_repo(root: Path, config: RatchetConfig) -> dict[str, FileMetrics]:
    """위반 있는 파일만 담는다(`{root 상대 posix 경로: FileMetrics}`)."""
    report: dict[str, FileMetrics] = {}
    for target_dir in config.target_dirs:
        for path in sorted((root / target_dir).rglob("*.py")):
            if "__pycache__" in path.parts:
                continue
            relative = path.relative_to(root).as_posix()
            metrics = scan_source(path.read_text(encoding="utf-8"), config)
            if not metrics.is_clean():
                report[relative] = metrics
    return report


def unallowed_violations(
    report: dict[str, FileMetrics], allowlist: frozenset[tuple[str, str]]
) -> list[tuple[str, str, int]]:
    """allowlist 에 없는 (경로, 지표, 카운트) 목록."""
    violations: list[tuple[str, str, int]] = []
    for path, metrics in sorted(report.items()):
        for metric in _METRIC_NAMES:
            count = getattr(metrics, metric)
            if count and (path, metric) not in allowlist:
                violations.append((path, metric, count))
    return violations


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="설계 래칫 — 위반 0 또는 명시 allowlist(D-5A-3)."
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="위반을 검사한다(현재 유일한 모드 — 항상 검사한다).",
    )
    return parser


def _run_check() -> int:
    """exit 0(위반 없음)·1(위반 있음). 설정 오류(와일드카드·누락 키)는 호출부가 2 로 뗀다."""
    config = load_config()
    report = scan_repo(_REPO_ROOT, config)
    violations = unallowed_violations(report, config.allowlist)
    if not violations:
        print(f"설계 래칫 위반 없음 (대상 {len(report)}개 파일 중 allowlist 밖 위반 0)")
        return 0
    print(f"설계 래칫 위반 {len(violations)}건:")
    for path, metric, count in violations:
        print(f"  {metric}: {path} = {count}")
    print(
        "정당한 경우 pyproject.toml [tool.design-ratchet] allowlist 에 정확 경로로 등재하고 사유를 기록하세요."
    )
    return 1


def main(argv: list[str] | None = None) -> int:
    _build_parser().parse_args(argv)
    try:
        return _run_check()
    except (ValueError, KeyError) as exc:
        print(f"설계 래칫 설정 오류: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
