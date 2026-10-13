"""RED — job 저장소·저널의 **조립 경계**(M6/6B-4 설계 검토 (2b) 「경계로 처리」 행의
실측 목록). 새 public 표면 둘이 밖에 허락하는 것을 구조로 좁힌다:

- `JobJournal(경로)` — 임의 디렉터리에 저널을 쓸 수 있다. 그래서 **조립 근에서만** 만들고
  경로는 설정에서만 온다(문자열 리터럴 경로 0).
- `JobStore(...)` — 저널 없는 저장소가 생길 수 있다. `journal` 이 필수 인자라 타입으로
  이미 닫혀 있고, 그 위에 **생성 자리**를 `load_job_store` 하나로 좁힌다.
- 원자 교체(`os.replace`)는 저널 모듈 한 자리뿐이다 — 둘째 writer 가 생기면 저장소 잠금
  밖에서 파일이 바뀔 수 있다(우회 (9)와 같은 축).

판정은 **AST** 로 한다(grep 이 아니다 — 주석·문자열에 같은 이름이 있어도, 줄바꿈으로
쪼개도 같은 답이 나온다). 음성 대조를 함께 둬 스캐너가 실제로 생성을 본다는 것을 보인다.
"""

from __future__ import annotations

import ast
from pathlib import Path

import pytest

_SRC_ROOT = Path(__file__).resolve().parents[2] / "src" / "ml_engine"

_JOURNAL_CONSTRUCTION_SITES = frozenset({"app/server.py"})
_STORE_CONSTRUCTION_SITES = frozenset({"training/jobs/store.py"})
_ATOMIC_REPLACE_SITES = frozenset({"training/jobs/journal.py"})


def _sources() -> dict[str, ast.Module]:
    return {
        path.relative_to(_SRC_ROOT).as_posix(): ast.parse(
            path.read_text(encoding="utf-8")
        )
        for path in sorted(_SRC_ROOT.rglob("*.py"))
        if "__pycache__" not in path.parts and "_generated" not in path.parts
    }


def _called_name(node: ast.Call) -> str | None:
    if isinstance(node.func, ast.Name):
        return node.func.id
    if isinstance(node.func, ast.Attribute):
        return node.func.attr
    return None


def construction_sites(tree: ast.Module, name: str) -> list[ast.Call]:
    return [
        node
        for node in ast.walk(tree)
        if isinstance(node, ast.Call) and _called_name(node) == name
    ]


def _files_constructing(name: str) -> frozenset[str]:
    return frozenset(
        module for module, tree in _sources().items() if construction_sites(tree, name)
    )


def _replace_sites() -> frozenset[str]:
    found: set[str] = set()
    for module, tree in _sources().items():
        for node in ast.walk(tree):
            if (
                isinstance(node, ast.Call)
                and isinstance(node.func, ast.Attribute)
                and node.func.attr == "replace"
                and isinstance(node.func.value, ast.Name)
                and node.func.value.id == "os"
            ):
                found.add(module)
    return frozenset(found)


# ---- 경계 ----


def test_job_journal_is_constructed_only_in_the_assembly_root() -> None:
    assert _files_constructing("JobJournal") == _JOURNAL_CONSTRUCTION_SITES


def test_job_store_is_constructed_only_by_the_loader() -> None:
    assert _files_constructing("JobStore") == _STORE_CONSTRUCTION_SITES


def test_atomic_replace_happens_in_one_place() -> None:
    assert _replace_sites() == _ATOMIC_REPLACE_SITES


def test_journal_path_never_comes_from_a_literal() -> None:
    """경로는 설정에서만 온다 — 생성 인자 식 안에 문자열 리터럴이 있으면 그 자리가
    저장소 밖에서 정해지지 않는다는 뜻이다(이미지·볼륨과 어긋날 수 있다)."""
    for module, tree in _sources().items():
        for call in construction_sites(tree, "JobJournal"):
            literals = [
                node
                for argument in (*call.args, *(kw.value for kw in call.keywords))
                for node in ast.walk(argument)
                if isinstance(node, ast.Constant) and isinstance(node.value, str)
            ]
            assert not literals, (module, [node.value for node in literals])


# ---- 음성 대조 — 스캐너가 실제로 생성을 본다 ----


@pytest.mark.parametrize(
    "source",
    [
        "from x import JobJournal\nj = JobJournal(Path('/tmp/anywhere'))\n",
        "import x\nj = x.JobJournal(p)\n",
        "def f():\n    return [JobJournal(p) for p in ps]\n",
    ],
)
def test_scanner_finds_injected_construction(source: str) -> None:
    assert construction_sites(ast.parse(source), "JobJournal")


def test_scanner_ignores_the_name_in_comments_and_strings() -> None:
    """grep 과 달라지는 자리 — 주석·문자열의 같은 이름은 생성이 아니다."""
    source = '# JobJournal(p) 는 조립 근에서만 만든다\nname = "JobJournal(p)"\n'
    assert not construction_sites(ast.parse(source), "JobJournal")


def test_literal_scanner_sees_a_hardcoded_path() -> None:
    tree = ast.parse("j = JobJournal(Path('/var/lib/jobs'))\n")
    call = construction_sites(tree, "JobJournal")[0]
    literals = [
        node
        for argument in call.args
        for node in ast.walk(argument)
        if isinstance(node, ast.Constant) and isinstance(node.value, str)
    ]
    assert [node.value for node in literals] == ["/var/lib/jobs"]
