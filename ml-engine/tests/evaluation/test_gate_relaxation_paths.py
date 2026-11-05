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
import dataclasses
import importlib
from pathlib import Path
from typing import Final

import pytest
from google.protobuf.descriptor import Descriptor, FileDescriptor

from ml_engine.contracts import (
    common_pb2,
    embedding_pb2,
    error_pb2,
    features_pb2,
    prediction_pb2,
    training_pb2,
)
from ml_engine.evaluation.policy import (
    _INDEXED_LIST_PREFIXES,
    _SCALAR_KEYS,
    EvaluationPolicy,
    load_evaluation_policy,
)
from ml_engine.evaluation.policy_source import (
    PolicySourceMismatch,
    verify_policy_source,
)
from tests.ast_sites import (
    bound_names,
    called_name,
    construction_sites,
    src_key,
    src_trees,
)

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_SRC = _ML_ENGINE_ROOT / "src" / "ml_engine"
_SHIPPED_POLICY = _ML_ENGINE_ROOT / "policy" / "evaluation-v1.yaml"

POLICY_KEY_NAMES: Final[frozenset[str]] = frozenset(_SCALAR_KEYS) | frozenset(
    _INDEXED_LIST_PREFIXES
)
"""정책 키 전수(스칼라 + 목록 접두) — 아래 네 축이 공유하는 모집단."""

_ENVIRONMENT_NAMES: Final[frozenset[str]] = frozenset({"environ", "getenv"})


def _string_constants(node: ast.AST) -> list[str]:
    """그 노드 아래의 모든 문자열 상수 — 이 게이트 고유라 공용 보조에 올리지 않는다."""
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


def _environment_readingsrc_trees() -> list[tuple[Path, ast.Module]]:
    """환경을 실제로 읽는 모듈만(`os.environ` · `getenv`). 환경 이름 후보를 **그 모듈
    안에서만** 찾는 이유는, 전 소스의 대문자 상수를 환경 이름으로 보면 enum 값
    (`MEASURED_BEFORE_EFFECTIVE_DATE` 등)까지 후보가 되어 술어가 자기 어휘를 고발하기
    때문이다 — 술어가 모든 것을 잡으면 아무것도 잡지 못한다."""
    return [(path, tree) for path, tree in src_trees() if _reads_environment(tree)]


def _environment_bound_names(tree: ast.Module) -> set[str]:
    """그 모듈에서 환경 접근을 가리키는 이름 전수 — 원래 이름 + `as` 별칭(cr LOW).
    `from os import environ as _e` 뒤 `_e[...]` 가 앞 판의 구멍이었다(④ 축에서 M8b 가 닫은
    바로 그 별칭 구멍이 ② 축에만 남아 있었다)."""
    names: set[str] = set()
    for base in _ENVIRONMENT_NAMES:
        names |= bound_names(tree, base)
    return names


def _reads_environment(tree: ast.Module) -> bool:
    watched = _environment_bound_names(tree)
    for node in ast.walk(tree):
        if isinstance(node, ast.Attribute) and node.attr in watched:
            return True
        if isinstance(node, ast.Name) and node.id in watched:
            return True
    return False


def test_policy_key_population_is_derived_from_the_loader_schema() -> None:
    """모집단이 비어 있거나 출하 파일과 어긋나면 아래 네 축이 전부 헛돈다 — 먼저 잰다."""
    assert len(POLICY_KEY_NAMES) == len(_SCALAR_KEYS) + len(_INDEXED_LIST_PREFIXES)
    assert "effective_date" in POLICY_KEY_NAMES
    shipped = _SHIPPED_POLICY.read_text(encoding="utf-8")
    missing = [key for key in POLICY_KEY_NAMES if key not in shipped]
    assert not missing, f"정책 키가 출하 파일에 없다: {missing}"


def test_evaluation_policy_is_constructed_only_inside_the_loader() -> None:
    """④ 로더 우회 — 손으로 조립해 임계를 비트는 길. 이름 해석(별칭·재대입 한 단계)은
    공용 보조가 한다(cr M-6 — 두 게이트가 **같은 본문**을 쓴다)."""
    assert construction_sites("EvaluationPolicy") == {
        ("evaluation/policy.py", "load_evaluation_policy")
    }


def test_no_cli_argument_is_named_after_a_policy_key() -> None:
    """① CLI — `add_argument` 의 옵션 문자열·`dest` 전수."""
    violations: list[str] = []
    seen = 0
    for path, tree in src_trees():
        for node in ast.walk(tree):
            if not isinstance(node, ast.Call) or called_name(node) != "add_argument":
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
                f"{src_key(path)}:{node.lineno} {candidate} -> {_matched_keys(candidate)}"
                for candidate in candidates
                if _matched_keys(candidate)
            )
    assert seen, "add_argument 를 하나도 찾지 못했다 — 스윕이 헛돈다"
    assert not violations, f"CLI 플래그가 정책 키를 받는다: {violations}"


def test_no_environment_variable_is_named_after_a_policy_key() -> None:
    """② 환경변수 — 환경을 읽는 모듈의 환경 이름 꼴(대문자+밑줄) 상수 전수."""
    violations: list[str] = []
    seen = 0
    trees = _environment_readingsrc_trees()
    assert trees, "환경을 읽는 모듈을 찾지 못했다 — 스윕이 헛돈다"
    for path, tree in trees:
        for value in _string_constants(tree):
            if not value or value != value.upper() or " " in value:
                continue
            if not any(character.isalpha() for character in value):
                continue
            seen += 1
            if _matched_keys(value):
                violations.append(f"{src_key(path)} {value} -> {_matched_keys(value)}")
    assert seen, "환경 이름 꼴 상수를 하나도 찾지 못했다 — 스윕이 헛돈다"
    assert not violations, f"환경 변수가 정책 키를 받는다: {violations}"


def test_evaluation_layer_never_reads_the_environment() -> None:
    """②b — `evaluation/**` 은 환경을 **아예** 읽지 않는다. 이름 대조는 「키와 같은
    이름으로」만 막으므로, 판정 층 자체가 환경을 모르는 것을 따로 잠근다."""
    violations: list[str] = []
    for path, tree in src_trees():
        if not src_key(path).startswith("evaluation/"):
            continue
        watched = _environment_bound_names(tree)
        for node in ast.walk(tree):
            if isinstance(node, ast.Name) and node.id in watched:
                violations.append(f"{src_key(path)}:{node.lineno} {node.id}")
            elif isinstance(node, ast.Attribute) and node.attr in watched:
                violations.append(f"{src_key(path)}:{node.lineno} .{node.attr}")
            elif isinstance(node, (ast.Import, ast.ImportFrom)):
                # **뿌리로 비교한다**(cr LOW) — 앞 판의 `"os" in names or root == "os"` 는
                # `import os.path`(names=["os.path"])·`from os.path import join`
                # (root="os.path")를 지나갔다.
                roots = {alias.name.split(".")[0] for alias in node.names}
                module = getattr(node, "module", None) or ""
                if "os" in roots or module.split(".")[0] == "os":
                    violations.append(f"{src_key(path)}:{node.lineno} import os")
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


# ── ⑤ 완화된 값을 **만드는 형태**를 닫았는가 (v H-1) ──────────────────────────
# 앞 판의 ④ 축은 「생성 **호출 이름**이 로더 안에 하나」였고 세 형태를 전부 지나갔다
# (verifier 실측). 아래 셋은 그 형태 각각이 지금 **실패하는지**를 직접 잰다 — 이름 패턴을
# 넓히는 대신 `InitVar` 생성 토큰이 「기존 객체에서 베껴 올 자리」를 없앤 결과다.


def _shipped_policy() -> EvaluationPolicy:
    loaded = load_evaluation_policy(_SHIPPED_POLICY)
    assert isinstance(loaded, EvaluationPolicy), loaded
    return loaded


def test_dataclasses_replace_cannot_relax_a_threshold() -> None:
    policy = _shipped_policy()
    with pytest.raises((ValueError, TypeError)):
        dataclasses.replace(policy, paired_t_threshold=1.0)


def test_rebuilding_from_vars_cannot_relax_a_threshold() -> None:
    """`type(p)(**{**vars(p), …})` — `InitVar` 는 `vars()` 에 없으므로 토큰이 빠진다."""
    policy = _shipped_policy()
    assert "parsed" not in vars(policy)
    assert "parsed" not in {field.name for field in dataclasses.fields(policy)}
    with pytest.raises(TypeError):
        type(policy)(**{**vars(policy), "paired_t_threshold": 1.0})


def test_dynamically_imported_class_cannot_relax_a_threshold() -> None:
    """`getattr(importlib.import_module(…), "Evaluation"+"Policy")(…)` — 이름을 쪼개
    만들어도 생성에 토큰이 필요하다(AST 가 보지 못하는 경로를 **형태**가 막는다)."""
    policy = _shipped_policy()
    module = importlib.import_module("ml_engine.evaluation.policy")
    constructed = getattr(module, "Evaluation" + "Policy")
    with pytest.raises(TypeError):
        constructed(**{**vars(policy), "paired_t_threshold": 1.0})


def test_source_verification_catches_a_forged_construction_token() -> None:
    """토큰 타입 이름을 **동적으로 훔쳐** 위조한 객체도 출처 재검증에서 드러난다 —
    AST 가 보지 못하는 마지막 경로를 **값 대조**가 막는 자리(겹 둘째)."""
    policy = _shipped_policy()
    module = importlib.import_module("ml_engine.evaluation.policy")
    token = getattr(module, "_Parsed" + "PolicyFile")
    forged = module.EvaluationPolicy(
        parsed=token(digest=policy.source.digest),
        **{**vars(policy), "paired_t_threshold": 1.0},
    )
    assert forged.paired_t_threshold == 1.0  # 만들어지기는 한다
    assert isinstance(verify_policy_source(forged), PolicySourceMismatch)
    assert isinstance(verify_policy_source(policy), EvaluationPolicy)


def test_no_src_module_outside_the_policy_module_imports_its_privates() -> None:
    """생성 토큰을 **정적으로** 가져가는 길 — `ImportFrom` 의 `name` 은 `as` 별칭과
    무관하게 원래 이름이라 이 대조는 별칭으로 열리지 않는다. 동적 절취(`getattr`)는 이
    층이 아니라 위 출처 재검증이 받는다."""
    violations: list[str] = []
    for path, tree in src_trees():
        if src_key(path) == "evaluation/policy.py":
            continue
        for node in ast.walk(tree):
            if not isinstance(node, ast.ImportFrom):
                continue
            if node.module != "ml_engine.evaluation.policy":
                continue
            violations.extend(
                f"{src_key(path)}:{node.lineno} {alias.name}"
                for alias in node.names
                if alias.name.startswith("_")
            )
    assert not violations, f"정책 모듈의 private 이름을 src 가 가져간다: {violations}"
