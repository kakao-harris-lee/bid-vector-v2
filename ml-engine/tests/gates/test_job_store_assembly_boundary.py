"""RED — job 저장소·저널의 **조립 경계**(M6/6B-4 설계 검토 (2b) 「경계로 처리」 행의
실측 목록). 새 public 표면이 밖에 허락하는 것을 구조로 좁힌다:

- `JobJournal(경로)` — 임의 디렉터리에 저널을 쓸 수 있다. 그래서 **조립 근에서만** 만들고
  경로는 `ServerConfig` 필드에서만 온다.
- `JobStore(...)` — 저널 없는 저장소가 생길 수 있다. `journal` 이 필수 인자라 타입으로
  이미 닫혀 있고, 그 위에 **생성 자리**를 `load_job_store` 하나로 좁힌다.
- **둘째 writer** — 저장소 잠금 밖에서 저널 파일이 바뀌면 상태와 디스크가 갈라진다
  (우회 (9)와 같은 축).

**vr H-2 / cr M-4 — 앞 판의 술어는 호출식의 「이름」에 걸려 있어 규약을 형식적으로 지키는
스타일 하나로 열렸다**(실측: 별칭 import `JobJournal as _J` · `from os import replace as
_swap` · `Path(a).replace(b)` · 모듈 상수 경유 리터럴 다섯이 모두 초록). 이 판의 술어는
셋 모두 **구조**다:

1. **심볼 해석** — 호출식의 이름이 아니라 그 이름이 가리키는 **정규화된 출처**
   (`ml_engine.training.jobs.journal_file.JobJournal`)로 센다. 별칭·속성 접근·`from`
   import 가 같은 답을 낸다.
2. **닫힌 import 집합** — 저널 **파일 모듈**을 import 할 수 있는 모듈 자체를 집합 등식으로
   닫는다(import-linter 계약을 쓸 수 없는 자리의 같은 층 — `pyproject.toml` 은 이 slice
   in_scope 밖이다). 이름 하나를 더하려면 **이 게이트를 고쳐야** 한다.
3. **경로를 밖에 내주지 않는다** — `JobJournal` 의 공개 표면 집합 등식(`path` property
   없음). 경로 객체를 얻을 수 없으면 둘째 writer 를 조립할 수 없다.

**경계 밖 — 코드로 막지 않고 알려진 제한으로 적는다**(vr L-5, 실측 목록):

- **동적 접근** — `importlib.import_module("…journal_file").JobJournal(...)` ·
  `getattr(module, "JobJournal")`. grimp 도 AST 도 import 문과 호출식을 읽는다.
- **재수출의 모듈 속성 경유** — `import ml_engine.training.jobs.store as m` 뒤
  `m.JobJournal(...)` + `# type: ignore[attr-defined]`. `modules_binding` 은 `ImportFrom` 의
  이름만 보고, 생성 자리 해석은 그 호출을 `…store.JobJournal` 이라는 **다른 이름**으로 본다
  (vr M-3 실측: ruff·mypy·lint-imports·게이트 전부 초록). `from …store import JobJournal`
  형태는 닫혀 있다 → `OPEN-6B4-ASSEMBLY-BOUNDARY-HARDENING`.
- **지역 재바인딩** — `factory = JobStore; factory(...)`. 생성 자리 술어는 호출식의 출처를
  해석하지, 값의 흐름을 따라가지 않는다.
- **`type()` 경유** — `type(store)(journal=…, pending_queue_max=10**9)`. 이름이 등장하지
  않는다.
- **private 속성 접근** — `store._journal` 로 이미 만들어진 저널 객체를 꺼내는 길. 위
  둘과 묶여야 쓸모가 있고(저장소 인스턴스를 쥐어야 한다), production 에 그 자리는 없다.
- **저널 파일을 직접 편집하는 주체** — 애초에 비방어다(위협 모델).

**이 파일의 정적 술어는 「닫음」이 아니라 「좁힘」이다**: 위 다섯은 정적으로 닫히지 않는다.
저널 파일에 **실제로 쓰는 자리**의 닫힘은 정적 이름 목록이 아니라 실행 시점 감사 훅
(`tests/gates/test_journal_write_audit.py`)이 진다 — 그 파일이 무엇을 닫고 무엇을 못 닫는지
적는다.
"""

from __future__ import annotations

import ast
from functools import cache
from pathlib import Path

import grimp
import pytest

from ml_engine.training.jobs.journal_file import JobJournal

_SRC_ROOT = Path(__file__).resolve().parents[2] / "src" / "ml_engine"

_ROOT_PACKAGE = "ml_engine"
_JOURNAL_FILE_MODULE = "ml_engine.training.jobs.journal_file"
_JOURNAL_CLASS = f"{_JOURNAL_FILE_MODULE}.JobJournal"
_STORE_CLASS = "ml_engine.training.jobs.store.JobStore"
_SERVER_MODULE = "app/server.py"
_JOURNAL_FILE_SOURCE = "training/jobs/journal_file.py"

_JOURNAL_CONSTRUCTION_SITES = frozenset({_SERVER_MODULE})
_STORE_CONSTRUCTION_SITES = frozenset({"training/jobs/store.py"})
_JOURNAL_FILE_IMPORTERS = frozenset({"training/jobs/store.py", _SERVER_MODULE})
# 이름을 자기 이름 공간에 들이는 모듈(정의 포함) — 재수출 경유 획득을 닫는다(cr L-1).
_JOURNAL_NAME_BINDERS = frozenset(
    {_JOURNAL_FILE_SOURCE, "training/jobs/store.py", _SERVER_MODULE}
)
_STORE_NAME_BINDERS = frozenset(
    {
        "training/jobs/store.py",
        "training/jobs/runner.py",
        "training/jobs/servicer.py",
        _SERVER_MODULE,
    }
)

# 저널이 사는 자리(`artifact_out_dir` 아래)에 닿을 수 있는 모듈들 — 이 범위 안에서
# 파일계를 바꾸는 호출 자리를 닫는다. `adapters/`·`registry/` 는 산출물·스냅숏이라는 다른
# 축이고 저널 경로를 알 방법이 없다(그 축은 이 slice 의 위협 모델 밖).
_FS_SCOPE_PREFIXES = ("training/jobs/", "app/")
_FS_MUTATION_SITES = frozenset(
    {
        _JOURNAL_FILE_SOURCE,
        # 선재 — backtest 산출물 디렉터리 생성(저널과 무관). 자리가 늘면 이 집합이 붉어진다.
        "app/backtest_cli.py",
    }
)

# 파일계를 바꾸는 모듈 수준 함수(심볼 해석 뒤의 정규 이름).
_FS_MUTATION_FUNCTIONS = frozenset(
    {
        "os.replace",
        "os.rename",
        "os.renames",
        "os.link",
        "os.symlink",
        "os.remove",
        "os.unlink",
        "os.removedirs",
        "os.rmdir",
        "os.truncate",
        "os.ftruncate",
        "os.mkdir",
        "os.makedirs",
        "os.open",
        "os.write",
        "os.writev",
        "shutil.move",
        "shutil.copy",
        "shutil.copy2",
        "shutil.copyfile",
        "shutil.copyfileobj",
        "shutil.copytree",
        "shutil.rmtree",
    }
)

# `Path` 류 메서드 — 수신자의 타입을 AST 로는 모르므로 이름으로 센다. 다른 타입과 이름이
# 겹치지 않는 것들은 **인자 모양을 보지 않는다**(`write_text(data, encoding=…)` 처럼
# 키워드를 붙여 비껴가는 길을 남기지 않는다).
_FS_MUTATION_METHODS_ANY_SHAPE = frozenset(
    {
        "unlink",
        "write_bytes",
        "write_text",
        "touch",
        "mkdir",
        "rmdir",
        "symlink_to",
        "hardlink_to",
    }
)
# 이름이 겹치는 둘만 모양으로 가른다: `Path.replace(target)`·`Path.rename(target)` 은
# 위치 인자 **하나**, `str.replace("a", "b")` 는 둘, `datetime.replace(tzinfo=…)` 는
# 키워드만, `dataclasses.replace(x, …)` 는 심볼 해석으로 모듈 함수다.
_FS_MUTATION_METHODS_ONE_ARG = frozenset({"replace", "rename"})
_RESOLVED_MODULE_PREFIXES = frozenset({"dataclasses", "os", "shutil", "os.path"})


# ---- 심볼 해석 ----


def module_dotted(relative: str) -> str:
    """`training/jobs/store.py` → `ml_engine.training.jobs.store`."""
    stem = relative.removesuffix(".py").removesuffix("/__init__")
    return "ml_engine." + stem.replace("/", ".")


def import_bindings(tree: ast.AST, own_module: str | None = None) -> dict[str, str]:
    """모듈의 지역 이름 → **정규화된 출처**. `import a.b as z`·`from a.b import X as y`
    를 전부 같은 모양으로 눕힌다 — 별칭이 술어를 비껴가지 못한다.

    `own_module` 이 있으면 **그 모듈이 직접 정의한** 최상위 클래스·함수도 같은 모양으로
    눕힌다(`store.py` 안의 `JobStore(...)` 가 자기 자신의 정규 이름으로 해석된다 —
    정의한 자리를 import 한 자리와 같은 술어로 센다)."""
    bound: dict[str, str] = {}
    if own_module is not None and isinstance(tree, ast.Module):
        for statement in tree.body:
            if isinstance(
                statement, (ast.ClassDef, ast.FunctionDef, ast.AsyncFunctionDef)
            ):
                bound[statement.name] = f"{own_module}.{statement.name}"
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            for alias in node.names:
                if alias.asname is not None:
                    bound[alias.asname] = alias.name
                else:
                    root = alias.name.split(".")[0]
                    bound[root] = root
        elif isinstance(node, ast.ImportFrom) and node.module is not None:
            for alias in node.names:
                bound[alias.asname or alias.name] = f"{node.module}.{alias.name}"
    return bound


def resolve_callee(node: ast.expr, bound: dict[str, str]) -> str | None:
    """호출식 → 정규 이름. 속성 사슬(`a.b.C`)은 뿌리를 바인딩으로 치환해 잇는다."""
    parts: list[str] = []
    current: ast.expr = node
    while isinstance(current, ast.Attribute):
        parts.append(current.attr)
        current = current.value
    if not isinstance(current, ast.Name):
        return None
    parts.append(bound.get(current.id, current.id))
    return ".".join(reversed(parts))


def _sources() -> tuple[tuple[str, ast.Module], ...]:
    return tuple(
        (
            path.relative_to(_SRC_ROOT).as_posix(),
            ast.parse(path.read_text(encoding="utf-8")),
        )
        for path in sorted(_SRC_ROOT.rglob("*.py"))
        if "__pycache__" not in path.parts and "_generated" not in path.parts
    )


def construction_sites(
    tree: ast.AST, qualified: str, bound: dict[str, str] | None = None
) -> list[ast.Call]:
    bindings = import_bindings(tree) if bound is None else bound

    return [
        node
        for node in ast.walk(tree)
        if isinstance(node, ast.Call)
        and resolve_callee(node.func, bindings) == qualified
    ]


def files_constructing(qualified: str) -> frozenset[str]:
    return frozenset(
        module
        for module, tree in _sources()
        if construction_sites(
            tree, qualified, import_bindings(tree, module_dotted(module))
        )
    )


@cache
def _import_graph() -> grimp.ImportGraph:
    """**grimp** 의 import 그래프(import-linter 의 의존 — 이미 설치돼 있다).

    vr H-1 — 앞 판은 손으로 짠 해석기로 import 문을 읽어 `ImportFrom.level`(상대 import)을
    버렸다. `from .journal_file import JobJournal as RogueJournal` 하나로 생성 자리 술어와
    닫힌 import 집합이 **둘 다** 열렸다(ruff·format·mypy·lint-imports·게이트 전부 초록).
    grimp 는 상대 import 를 절대 이름으로 정규화하므로 그 틈이 구조적으로 닫힌다 — 모듈
    앨리어스(`import … as _jf`)도 같은 간선으로 본다."""
    return grimp.build_graph(_ROOT_PACKAGE)


def modules_importing(target_module: str) -> frozenset[str]:
    """`target_module` 을 **직접** import 하는 모듈 집합(grimp). 상대·앨리어스·`from`
    형태가 모두 같은 답을 낸다."""
    return frozenset(
        module.removeprefix(f"{_ROOT_PACKAGE}.").replace(".", "/") + ".py"
        for module in _import_graph().find_modules_that_directly_import(target_module)
    )


def modules_binding(name: str) -> frozenset[str]:
    """그 이름을 자기 이름 공간에 **들이는** 모듈 집합(import 또는 정의).

    cr L-1 — 심볼 해석은 한 홉이라 `from …store import JobJournal as _J`(저장소가 자기
    이름 공간에 들인 이름의 재수출)는 생성 자리 집합에도 닫힌 import 집합에도 들어오지
    않았다. 실제로 막던 것은 `mypy --strict` 의 `implicit_reexport=False` 이고, 그것은
    `# type: ignore[attr-defined]` 한 줄로 지나간다(`type: ignore` 예산 게이트 없음).
    이 집합 등식은 그 플래그에 기대지 않는다."""
    found: set[str] = set()
    for module, tree in _sources():
        for node in ast.walk(tree):
            if (
                isinstance(node, ast.ImportFrom)
                and any(alias.name == name for alias in node.names)
            ) or (isinstance(node, ast.ClassDef) and node.name == name):
                found.add(module)
    return frozenset(found)


def fs_mutation_sites(trees: tuple[tuple[str, ast.Module], ...]) -> frozenset[str]:
    found: set[str] = set()
    for module, tree in trees:
        bound = import_bindings(tree)
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call):
                continue
            resolved = resolve_callee(node.func, bound)
            if resolved in _FS_MUTATION_FUNCTIONS:
                found.add(module)
                continue
            if (
                resolved is not None
                and "." in resolved
                and resolved.rsplit(".", 1)[0] in _RESOLVED_MODULE_PREFIXES
            ):
                # 모듈 함수로 해석됐고 금지 집합 밖이다(`dataclasses.replace` 등).
                continue
            if not isinstance(node.func, ast.Attribute):
                continue
            if node.func.attr in _FS_MUTATION_METHODS_ANY_SHAPE or (
                node.func.attr in _FS_MUTATION_METHODS_ONE_ARG
                and len(node.args) == 1
                and not node.keywords
            ):
                found.add(module)
    return frozenset(found)


# ---- 경계 ----


def test_job_journal_is_constructed_only_in_the_assembly_root() -> None:
    assert files_constructing(_JOURNAL_CLASS) == _JOURNAL_CONSTRUCTION_SITES


def test_job_store_is_constructed_only_by_the_loader() -> None:
    assert files_constructing(_STORE_CLASS) == _STORE_CONSTRUCTION_SITES


def test_only_the_assembly_root_and_the_store_import_the_journal_file_module() -> None:
    """닫힌 import 집합 — 저널 **파일** 모듈을 아는 모듈이 둘뿐이다. 별칭으로 가져오려면
    먼저 import 해야 하고, 그 import 가 여기서 붉어진다. 판정은 **grimp 의 import
    그래프**이므로 상대 import·모듈 앨리어스가 같은 답을 낸다(vr H-1)."""
    assert modules_importing(_JOURNAL_FILE_MODULE) == _JOURNAL_FILE_IMPORTERS


def test_the_journal_and_store_names_are_bound_only_where_expected() -> None:
    """재수출 경유 획득을 닫는다(cr L-1) — `from …store import JobJournal` 처럼 **한 홉
    건너** 이름을 얻는 길은 import 그래프의 대상 모듈이 `store` 라서 위 집합에 안 걸린다.
    이름을 들이는 모듈 자체를 집합 등식으로 센다(mypy 의 재수출 플래그에 기대지 않는다)."""
    assert modules_binding("JobJournal") == _JOURNAL_NAME_BINDERS
    assert modules_binding("JobStore") == _STORE_NAME_BINDERS


def test_filesystem_mutations_near_the_journal_happen_in_one_place() -> None:
    """둘째 writer 축 — 저널에 닿을 수 있는 범위에서 파일계를 바꾸는 자리를 닫는다.
    이름 하나(`os.replace`)가 아니라 **집합**이고, 심볼 해석이라 별칭도 같이 센다."""
    scoped = tuple(
        (module, tree)
        for module, tree in _sources()
        if module.startswith(_FS_SCOPE_PREFIXES)
    )
    assert scoped, "범위가 비었다 — 경로 접두가 낡았다"
    assert fs_mutation_sites(scoped) == _FS_MUTATION_SITES


def test_the_journal_does_not_expose_its_path(tmp_path: Path) -> None:
    """경로 객체를 밖에 내주지 않는다(vr H-2) — `path` property 가 있으면 어느 모듈이든
    그 경로에 직접 쓸 수 있었다. 공개 표면을 **집합 등식**으로 닫는다(이름 하나 금지가
    아니다 — 새 접근자가 생기면 이름과 무관하게 붉어진다).

    cr L-2 — 클래스 dict 만 보면 `__init__` 에서 `self.path = self._path` 로 **인스턴스
    속성**을 내주는 길이 남는다. 인스턴스를 만들어 그 쪽 표면까지 센다."""
    assert {name for name in vars(JobJournal) if not name.startswith("_")} == {
        "read",
        "write",
    }
    instance = JobJournal(tmp_path / "journal")
    assert {name for name in vars(instance) if not name.startswith("_")} == set()
    assert {name for name in dir(instance) if not name.startswith("_")} == {
        "read",
        "write",
    }


def test_the_package_root_does_not_re_export_constructible_classes() -> None:
    """패키지 뿌리 재수출은 `from ml_engine.training.jobs import JobJournal` 이라는
    **둘째 획득 경로**였다(앞 판의 G1·G4 가 그 길로 갔다). 생성 가능한 둘을 뺀다."""
    from ml_engine.training import jobs

    assert "JobJournal" not in jobs.__all__
    assert "JobStore" not in jobs.__all__
    assert not hasattr(jobs, "JobJournal")
    assert not hasattr(jobs, "JobStore")


# ---- 값 축 — 경로는 `ServerConfig` 필드에서만 온다(cr M-4) ----


def _server_config_fields(tree: ast.Module) -> frozenset[str]:
    """`ServerConfig` 의 필드 이름을 **기계로** 뽑는다(손 목록 금지 — 필드가 바뀌면 같이
    바뀐다)."""
    for node in ast.walk(tree):
        if isinstance(node, ast.ClassDef) and node.name == "ServerConfig":
            return frozenset(
                statement.target.id
                for statement in node.body
                if isinstance(statement, ast.AnnAssign)
                and isinstance(statement.target, ast.Name)
            )
    raise AssertionError("app/server.py 에서 ServerConfig 를 찾지 못했다")


def _module_string_constants(tree: ast.Module) -> dict[str, str]:
    constants: dict[str, str] = {}
    for statement in tree.body:
        target: ast.expr | None = None
        if isinstance(statement, ast.AnnAssign):
            target = statement.target
        elif isinstance(statement, ast.Assign) and len(statement.targets) == 1:
            target = statement.targets[0]
        if (
            isinstance(target, ast.Name)
            and isinstance(statement, (ast.AnnAssign, ast.Assign))
            and isinstance(statement.value, ast.Constant)
            and isinstance(statement.value.value, str)
        ):
            constants[target.id] = statement.value.value
    return constants


def path_provenance_violations(tree: ast.Module) -> list[str]:
    """`JobJournal(...)` 인자식의 **출처**를 본다. 다음 셋 중 하나라도 어긋나면 위반:

    ① 설정 뿌리 — 인자식 안에 `<파라미터>.<ServerConfig 필드>` 가 있어야 한다(모듈 상수
       하나를 끼워 넣은 G5 가 여기서 걸린다).
    ② 조립 상수 — 그 밖의 이름은 모듈 수준 문자열 상수여야 하고, 그 값은 **상대 경로
       한 조각**이어야 한다(`"/tmp/rogue"` 같은 절대 경로는 위반).
    ③ 인라인 리터럴 금지.
    """
    config_fields = _server_config_fields(tree)
    constants = _module_string_constants(tree)
    violations: list[str] = []
    for function in ast.walk(tree):
        if not isinstance(function, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        parameters = {argument.arg for argument in function.args.args}
        for call in construction_sites(function, _JOURNAL_CLASS, import_bindings(tree)):
            arguments = [*call.args, *(keyword.value for keyword in call.keywords)]
            nodes = [node for argument in arguments for node in ast.walk(argument)]
            rooted = any(
                isinstance(node, ast.Attribute)
                and node.attr in config_fields
                and isinstance(node.value, ast.Name)
                and node.value.id in parameters
                for node in nodes
            )
            if not rooted:
                violations.append(f"{function.name}: 설정 뿌리 없음")
            for node in nodes:
                if isinstance(node, ast.Constant) and isinstance(node.value, str):
                    violations.append(f"{function.name}: 인라인 리터럴 {node.value!r}")
                if isinstance(node, ast.Name) and node.id not in parameters:
                    value = constants.get(node.id)
                    if value is None:
                        continue  # 호출 가능 이름(`Path` 등) — 문자열 상수가 아니다
                    if "/" in value or value in {".", "..", ""}:
                        violations.append(f"{function.name}: 조각 아님 {value!r}")
    return violations


def test_the_journal_path_comes_from_a_server_config_field() -> None:
    assert path_provenance_violations(dict(_sources())[_SERVER_MODULE]) == []


# ---- 음성 대조 — 스캐너가 실제로 심어 둔 우회를 본다 ----


@pytest.mark.parametrize(
    "source",
    [
        # 앞 판이 놓친 별칭 경로(G1·G4)
        f"from {_JOURNAL_FILE_MODULE} import JobJournal as _J\nj = _J(p)\n",
        f"import {_JOURNAL_FILE_MODULE} as _m\nj = _m.JobJournal(p)\n",
        "import ml_engine.training.jobs.journal_file\n"
        "j = ml_engine.training.jobs.journal_file.JobJournal(p)\n",
        f"from {_JOURNAL_FILE_MODULE} import JobJournal\n"
        "def f():\n    return [JobJournal(q) for q in qs]\n",
    ],
)
def test_scanner_finds_aliased_construction(source: str) -> None:
    assert construction_sites(ast.parse(source), _JOURNAL_CLASS)


def test_scanner_ignores_a_same_named_class_from_elsewhere() -> None:
    """심볼 해석의 값 — 같은 이름이라도 **다른 출처**면 이 경계의 대상이 아니다."""
    source = "from somewhere.else_ import JobJournal\nj = JobJournal(p)\n"
    assert not construction_sites(ast.parse(source), _JOURNAL_CLASS)


def test_scanner_ignores_the_name_in_comments_and_strings() -> None:
    source = '# JobJournal(p) 는 조립 근에서만 만든다\nname = "JobJournal(p)"\n'
    assert not construction_sites(ast.parse(source), _JOURNAL_CLASS)


@pytest.mark.parametrize(
    "source",
    [
        "from os import replace as _swap\n_swap(a, b)\n",  # G3
        "import os as _o\n_o.rename(a, b)\n",
        "from pathlib import Path\nPath(a).replace(b)\n",  # G2
        "p.rename(b)\n",
        "p.write_bytes(data)\n",
        'p.write_text(data, encoding="utf-8")\n',  # 키워드를 붙여도 센다
        "p.mkdir(parents=True, exist_ok=True)\n",
        "import shutil as _s\n_s.move(a, b)\n",
    ],
)
def test_scanner_finds_injected_second_writers(source: str) -> None:
    assert fs_mutation_sites((("probe.py", ast.parse(source)),)) == {"probe.py"}


@pytest.mark.parametrize(
    "source",
    [
        'name = text.replace("a", "b")\n',  # str.replace — 위치 인자 둘
        "aware = value.replace(tzinfo=UTC)\n",  # datetime.replace — 키워드만
        "import dataclasses\nnext_ = dataclasses.replace(entry, record=record)\n",
        "import os.path\nhead = os.path.join(a, b)\n",
    ],
)
def test_scanner_does_not_flag_unrelated_replace_calls(source: str) -> None:
    """과잉 금지의 통제군 — 이 넷이 걸리면 게이트가 정상 코드를 막는다."""
    assert fs_mutation_sites((("probe.py", ast.parse(source)),)) == frozenset()


@pytest.mark.parametrize(
    ("extra_constant", "expected"),
    [
        # G5 — 모듈 상수 경유 절대 경로(앞 판은 리터럴 술어가 초록이었다)
        ('_ROGUE: Final[str] = "/tmp/rogue"', ("설정 뿌리 없음", "조각 아님")),
        # 상대 한 조각이어도 설정 뿌리가 없으면 위반이다
        ('_ROGUE: Final[str] = "_journal"', ("설정 뿌리 없음",)),
    ],
)
def test_provenance_scanner_rejects_a_rogue_path(
    extra_constant: str, expected: tuple[str, ...]
) -> None:
    """조립 근의 인자식을 바꿔치운 문면에서 술어가 **다른 답**을 낸다(값 축 실측)."""
    original = (_SRC_ROOT / _SERVER_MODULE).read_text(encoding="utf-8")
    mutated = original.replace(
        "    return JobJournal(config.artifact_out_dir / _JOB_JOURNAL_DIR_NAME)",
        "    return JobJournal(_ROGUE)",
    ).replace(
        '_JOB_JOURNAL_DIR_NAME: Final[str] = "_journal"',
        f'_JOB_JOURNAL_DIR_NAME: Final[str] = "_journal"\n{extra_constant}',
    )
    assert mutated != original
    violations = path_provenance_violations(ast.parse(mutated))
    for fragment in expected:
        assert any(fragment in violation for violation in violations), (
            fragment,
            violations,
        )


def test_the_boundary_sets_are_not_vacuous() -> None:
    """집합 등식이 「아무도 아무것도 안 한다」로 성립하지 않는다 — 각 집합의 원소가
    실제로 그 일을 한다."""
    sources = dict(_sources())
    assert construction_sites(sources[_SERVER_MODULE], _JOURNAL_CLASS)
    store_module = "training/jobs/store.py"
    assert construction_sites(
        sources[store_module],
        _STORE_CLASS,
        import_bindings(sources[store_module], module_dotted(store_module)),
    )
    assert fs_mutation_sites(
        ((_JOURNAL_FILE_SOURCE, sources[_JOURNAL_FILE_SOURCE]),)
    ) == {_JOURNAL_FILE_SOURCE}
