"""게이트 test 공용 AST 보조 — **자리는 하나다**(cr M-6).

`_trees` · `_bound_names` · `_enclosing_functions` 가 두 게이트 파일에 **같은 본문으로**
복제돼 있었다(별칭 보강 주석까지). 이 저장소는 같은 종류의 중복을 이미 한 번 접었다
(D-6G2c-21 ③ 「앞 판은 이 열거가 로더마다 한 벌씩 있었고 두 벌이 조용히 갈릴 수 있었다」).
다음 변이 라운드가 한쪽만 단단하게 만들면 **다른 게이트는 고쳐지지 않은 채 초록**이다.

여기 있는 것은 소스 전수 순회와 「그 타입을 가리키는 이름」 해석뿐이고, 각 게이트가 **무엇을
세는지**(어느 타입·어느 모듈)는 인자로 받는다.
"""

from __future__ import annotations

import ast
from pathlib import Path

ML_ENGINE_SRC = Path(__file__).resolve().parent.parent / "src" / "ml_engine"
"""`ml-engine/src/ml_engine` — 게이트들이 전수 순회하는 뿌리."""


def src_paths(root: Path = ML_ENGINE_SRC) -> list[Path]:
    return sorted(
        path for path in root.rglob("*.py") if "__pycache__" not in path.parts
    )


def src_key(path: Path, root: Path = ML_ENGINE_SRC) -> str:
    return path.relative_to(root).as_posix()


def src_trees(root: Path = ML_ENGINE_SRC) -> list[tuple[Path, ast.Module]]:
    return [
        (path, ast.parse(path.read_text(encoding="utf-8"))) for path in src_paths(root)
    ]


def enclosing_functions(tree: ast.Module) -> dict[int, str]:
    """`id(node) -> 그 노드를 품은 가장 가까운 함수 이름`."""
    owner: dict[int, str] = {}
    for node in ast.walk(tree):
        if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        for inner in ast.walk(node):
            owner.setdefault(id(inner), node.name)
    return owner


def called_name(node: ast.Call) -> str | None:
    func = node.func
    if isinstance(func, ast.Name):
        return func.id
    if isinstance(func, ast.Attribute):
        return func.attr
    return None


def bound_names(tree: ast.Module, target: str) -> set[str]:
    """그 모듈 안에서 `target` 이름을 가리키는 이름 전수 — 원래 이름 + `as` 별칭 +
    `_P = Target` 재대입.

    **측정으로 더한 층이다**(M9/ml-lifecycle 변이 M8b): 호출 이름만 보던 앞 판은
    `from … import EvaluationPolicy as _P` 뒤 `_P(...)` 를 지나갔다 — 별칭 하나가 문이었다.

    **남은 한계를 문면에 둔다**: 재대입은 **한 단계**만 따라간다(`_A = T; _B = _A` 연쇄는
    못 본다). 그 잔여 경로도 src 를 편집해야 성립하고, 새 임계 **값**은 숫자 리터럴 게이트가,
    새 입력 **경로**는 완화 경로 세 축이, 생성 자체는 `InitVar` 생성 토큰과 출처 재검증이
    막는다 — 이 층은 그 위의 보조다."""
    names = {target}
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom):
            names.update(
                alias.asname or alias.name
                for alias in node.names
                if alias.name == target
            )
        elif isinstance(node, ast.Import):
            names.update(
                alias.asname or alias.name.rsplit(".", 1)[-1]
                for alias in node.names
                if alias.name.rsplit(".", 1)[-1] == target
            )
    for node in ast.walk(tree):
        if (
            isinstance(node, ast.Assign)
            and isinstance(node.value, ast.Name)
            and node.value.id in names
        ):
            names.update(item.id for item in node.targets if isinstance(item, ast.Name))
    return names


def construction_sites(target: str, root: Path = ML_ENGINE_SRC) -> set[tuple[str, str]]:
    """`target` 타입을 **짓는** 자리 전수 — `(소스 상대경로, 그 자리를 품은 함수 이름)`.
    이름은 모듈마다 `bound_names` 로 해석하므로 별칭·재대입 한 단계가 함께 잡힌다."""
    sites: set[tuple[str, str]] = set()
    for path, tree in src_trees(root):
        owner = enclosing_functions(tree)
        watched = bound_names(tree, target)
        for node in ast.walk(tree):
            if isinstance(node, ast.Call) and called_name(node) in watched:
                sites.add((src_key(path, root), owner.get(id(node), "<module>")))
    return sites
