"""RED — ML-07 acceptance ④ 「게이트 임계를 CLI 플래그·환경변수·요청 파라미터로 완화할 수
없다(세 입력 경로 각각)」. 수정 전에는 그 확인이 **로더 시그니처 한 줄**
(`load_evaluation_policy` 의 매개변수가 `path` 하나)뿐이었다 — 그 한 줄은 「다른 자리에
완화 표면이 생기는 것」을 전혀 보지 못한다. `app/` 에 플래그를 하나 더하거나, 환경 변수를
읽거나, proto 에 필드를 하나 더해 임계를 받는 길이 세 방향으로 열려 있었다.

**모집단은 열거가 아니라 정책 키에서 생성한다** — `_SCALAR_KEYS` + 목록 접두가 유일 출처다.
정책 키를 더하면 세 경로의 감시 범위가 **같은 커밋에서** 자란다.

네 번째 축은 「로더를 아예 우회해 정책 객체를 손으로 짓는 길」이다: `src` 안에서
`EvaluationPolicy` 를 짓는 자리가 **하나**이고 그것이 `load_evaluation_policy` 안임을 잰다.
test 코드는 대상 밖이다(거부 test 를 쓰려면 직접 생성이 필요하다 — 그 자유를 남긴다)."""

from __future__ import annotations

import ast
from pathlib import Path
from typing import Final

from google.protobuf.descriptor import Descriptor, FileDescriptor

from ml_engine.contracts import (
    common_pb2,
    embedding_pb2,
    error_pb2,
    features_pb2,
    prediction_pb2,
    training_pb2,
)
from ml_engine.evaluation.policy import _INDEXED_LIST_PREFIXES, _SCALAR_KEYS

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_SRC = _ML_ENGINE_ROOT / "src" / "ml_engine"
_SHIPPED_POLICY = _ML_ENGINE_ROOT / "policy" / "evaluation-v1.yaml"

POLICY_KEY_NAMES: Final[frozenset[str]] = frozenset(_SCALAR_KEYS) | frozenset(
    _INDEXED_LIST_PREFIXES
)
"""정책 키 전수(스칼라 + 목록 접두) — 아래 네 축이 공유하는 모집단."""

_ENVIRONMENT_NAMES: Final[frozenset[str]] = frozenset({"environ", "getenv"})


def _src_paths() -> list[Path]:
    return sorted(
        path for path in _SRC.rglob("*.py") if "__pycache__" not in path.parts
    )


def _key(path: Path) -> str:
    return path.relative_to(_SRC).as_posix()


def _trees() -> list[tuple[Path, ast.Module]]:
    return [
        (path, ast.parse(path.read_text(encoding="utf-8"))) for path in _src_paths()
    ]


def _enclosing_functions(tree: ast.Module) -> dict[int, str]:
    """`id(node) -> 그 노드를 품은 가장 가까운 함수 이름`."""
    owner: dict[int, str] = {}
    for node in ast.walk(tree):
        if not isinstance(node, (ast.FunctionDef, ast.AsyncFunctionDef)):
            continue
        for inner in ast.walk(node):
            owner.setdefault(id(inner), node.name)
    return owner


def _called_name(node: ast.Call) -> str | None:
    func = node.func
    if isinstance(func, ast.Name):
        return func.id
    if isinstance(func, ast.Attribute):
        return func.attr
    return None


def _string_constants(node: ast.AST) -> list[str]:
    return [
        inner.value
        for inner in ast.walk(node)
        if isinstance(inner, ast.Constant) and isinstance(inner.value, str)
    ]


def _normalized(name: str) -> str:
    return name.lstrip("-").replace("-", "_").lower()


def _matched_keys(candidate: str) -> list[str]:
    """정책 키가 후보 이름 안에 들어 있으면 그 키들. 부분 일치로 재는 이유는 접두
    (`ML_ENGINE_`)·접미(`_flag`)를 붙여 같은 값을 받는 길을 함께 닫기 위해서다."""
    normalized = _normalized(candidate)
    return sorted(key for key in POLICY_KEY_NAMES if key in normalized)


def _environment_reading_trees() -> list[tuple[Path, ast.Module]]:
    """환경을 실제로 읽는 모듈만(`os.environ` · `getenv`). 환경 이름 후보를 **그 모듈
    안에서만** 찾는 이유는, 전 소스의 대문자 상수를 환경 이름으로 보면 enum 값
    (`MEASURED_BEFORE_EFFECTIVE_DATE` 등)까지 후보가 되어 술어가 자기 어휘를 고발하기
    때문이다 — 술어가 모든 것을 잡으면 아무것도 잡지 못한다."""
    return [
        (path, tree)
        for path, tree in _trees()
        if any(
            (isinstance(node, ast.Attribute) and node.attr in _ENVIRONMENT_NAMES)
            or (isinstance(node, ast.Name) and node.id in _ENVIRONMENT_NAMES)
            for node in ast.walk(tree)
        )
    ]


def test_policy_key_population_is_derived_from_the_loader_schema() -> None:
    """모집단이 비어 있거나 출하 파일과 어긋나면 아래 네 축이 전부 헛돈다 — 먼저 잰다."""
    assert len(POLICY_KEY_NAMES) == len(_SCALAR_KEYS) + len(_INDEXED_LIST_PREFIXES)
    assert "effective_date" in POLICY_KEY_NAMES
    shipped = _SHIPPED_POLICY.read_text(encoding="utf-8")
    missing = [key for key in POLICY_KEY_NAMES if key not in shipped]
    assert not missing, f"정책 키가 출하 파일에 없다: {missing}"


def test_evaluation_policy_is_constructed_only_inside_the_loader() -> None:
    """④ 로더 우회 — 손으로 조립해 임계를 비트는 길."""
    sites: set[tuple[str, str]] = set()
    for path, tree in _trees():
        owner = _enclosing_functions(tree)
        for node in ast.walk(tree):
            if isinstance(node, ast.Call) and _called_name(node) == "EvaluationPolicy":
                sites.add((_key(path), owner.get(id(node), "<module>")))
    assert sites == {("evaluation/policy.py", "load_evaluation_policy")}, sites


def test_no_cli_argument_is_named_after_a_policy_key() -> None:
    """① CLI — `add_argument` 의 옵션 문자열·`dest` 전수."""
    violations: list[str] = []
    seen = 0
    for path, tree in _trees():
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call) or _called_name(node) != "add_argument":
                continue
            seen += 1
            candidates = [
                value for argument in node.args for value in _string_constants(argument)
            ]
            candidates.extend(
                value
                for keyword in node.keywords
                if keyword.arg == "dest"
                for value in _string_constants(keyword.value)
            )
            violations.extend(
                f"{_key(path)}:{node.lineno} {candidate} -> {_matched_keys(candidate)}"
                for candidate in candidates
                if _matched_keys(candidate)
            )
    assert seen, "add_argument 를 하나도 찾지 못했다 — 스윕이 헛돈다"
    assert not violations, f"CLI 플래그가 정책 키를 받는다: {violations}"


def test_no_environment_variable_is_named_after_a_policy_key() -> None:
    """② 환경변수 — 환경을 읽는 모듈의 환경 이름 꼴(대문자+밑줄) 상수 전수."""
    violations: list[str] = []
    seen = 0
    trees = _environment_reading_trees()
    assert trees, "환경을 읽는 모듈을 찾지 못했다 — 스윕이 헛돈다"
    for path, tree in trees:
        for value in _string_constants(tree):
            if not value or value != value.upper() or " " in value:
                continue
            if not any(character.isalpha() for character in value):
                continue
            seen += 1
            if _matched_keys(value):
                violations.append(f"{_key(path)} {value} -> {_matched_keys(value)}")
    assert seen, "환경 이름 꼴 상수를 하나도 찾지 못했다 — 스윕이 헛돈다"
    assert not violations, f"환경 변수가 정책 키를 받는다: {violations}"


def test_evaluation_layer_never_reads_the_environment() -> None:
    """②′ — `evaluation/**` 은 환경을 **아예** 읽지 않는다. 이름 대조는 「키와 같은
    이름으로」만 막으므로, 판정 층 자체가 환경을 모르는 것을 따로 잠근다."""
    violations: list[str] = []
    for path, tree in _trees():
        if not _key(path).startswith("evaluation/"):
            continue
        for node in ast.walk(tree):
            if isinstance(node, ast.Name) and node.id in _ENVIRONMENT_NAMES:
                violations.append(f"{_key(path)}:{node.lineno} {node.id}")
            elif isinstance(node, ast.Attribute) and node.attr in _ENVIRONMENT_NAMES:
                violations.append(f"{_key(path)}:{node.lineno} .{node.attr}")
            elif isinstance(node, (ast.Import, ast.ImportFrom)):
                names = [alias.name for alias in node.names]
                root = getattr(node, "module", None) or ""
                if "os" in names or root == "os":
                    violations.append(f"{_key(path)}:{node.lineno} import os")
    assert not violations, f"evaluation 층이 환경을 읽는다: {violations}"


def _message_descriptors(descriptor: Descriptor) -> list[Descriptor]:
    nested: list[Descriptor] = [descriptor]
    for inner in descriptor.nested_types:
        nested.extend(_message_descriptors(inner))
    return nested


def _wire_field_names(files: tuple[FileDescriptor, ...]) -> list[str]:
    names: list[str] = []
    for file_descriptor in files:
        for message in file_descriptor.message_types_by_name.values():
            for descriptor in _message_descriptors(message):
                names.extend(field.name for field in descriptor.fields)
    return names


def test_no_wire_contract_field_is_named_after_a_policy_key() -> None:
    """③ 요청 파라미터 — 생성 descriptor 를 순회해 **실제 wire** 의 필드 이름 전수를 본다
    (proto 텍스트 grep 이 아니라 descriptor 라, 파일을 나누거나 메시지를 중첩해도 범위가
    줄지 않는다)."""
    files = (
        common_pb2.DESCRIPTOR,
        embedding_pb2.DESCRIPTOR,
        error_pb2.DESCRIPTOR,
        features_pb2.DESCRIPTOR,
        prediction_pb2.DESCRIPTOR,
        training_pb2.DESCRIPTOR,
    )
    names = _wire_field_names(files)
    assert len(names) > len(POLICY_KEY_NAMES), "필드를 거의 못 찾았다 — 스윕이 헛돈다"
    violations = [
        f"{name} -> {_matched_keys(name)}" for name in names if _matched_keys(name)
    ]
    assert not violations, f"wire 계약 필드가 정책 키를 받는다: {violations}"
