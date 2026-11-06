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
import copy
import dataclasses
import hashlib
import importlib
from pathlib import Path, PosixPath
from types import SimpleNamespace
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
    PolicySource,
    load_evaluation_policy,
    policy_checksum,
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
from tests.policy_fixture import policy_yaml_text

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


# ── ⑥ 승인된 산출물로만 돈다 (v H-2) ────────────────────────────────────────
# ⑤ 의 두 겹은 「객체가 자기 파일과 같은가」까지만 묻는다. 「그 파일을 누가 승인했는가」는
# 묻지 않아, 완화한 사본을 파일로 만들어 쓰면 전 게이트가 초록이었다(verifier 실측).
# 아래 둘은 그 축의 **자리**를 집합 등식으로 잠근다 — 호출 자리가 하나 더 생기면 붉다.


def test_the_assembly_root_uses_the_approval_requiring_loader() -> None:
    """승인을 **묻지 않는** 로더를 부르는 자리 전수 — `evaluation/policy_source.py` 안
    둘뿐이다(재검증의 재읽기 · 승인 요구 로더 자신). `app/**` 이 그 로더를 직접 부르면
    운영자가 가리킨 미등록 사본으로 부팅이 서므로 이 등식이 깨진다.

    이름은 모듈마다 `bound_names` 로 해석하므로 `as` 별칭·재대입 한 단계가 함께 잡힌다."""
    assert construction_sites("load_evaluation_policy") == {
        ("evaluation/policy_source.py", "verify_policy_source"),
        ("evaluation/policy_source.py", "load_approved_evaluation_policy"),
    }


def test_the_gate_runs_from_exactly_one_product_site() -> None:
    """cr/v M-3 — 출처 재검증이 **제품 경로 하나**에 있다는 문면은 그 경로가 하나일 때만
    참이다. `run_holdout` 을 부르는 src 자리가 하나 더 생기면 재검증 없이 게이트가 돌 수
    있다 — 그 가능성을 집합 등식으로 닫는다(문면이 아니라 계수)."""
    assert construction_sites("run_holdout") == {("app/pipeline.py", "_run_holdout")}


# ── ⑦ ⑤의 겹 **안쪽 검사 각각**을 잠근다 (v r2 M-1) ─────────────────────────
# ⑤ 는 「세 형태가 실패한다」를 재고, 그 실패를 **무엇이** 만드는지는 재지 않았다. 그래서
# 생성 토큰의 두 검사와 재검증의 파일-바이트 축을 각각 무력화해도 전건이 초록이었다
# (verifier 실측 넷). 아래는 각 검사를 **단독으로** 겨눈다 — 하나를 지우면 하나가 붉다.

_UNREGISTERED_POLICY_VERSION: Final[str] = "test-policy-source-axis-v1"
"""이 절의 정책 version — 승인 등록부 **밖**이다.

생성 토큰의 두 검사(아래 둘)는 `EvaluationPolicy.__post_init__` 에 있고 승인과 무관하므로
미등록 version 으로 재는 것이 맞다 — 등록 version 을 쓰면 토큰 축을 재려는 입력이 승인
축과 뒤섞인다. **재검증의 바이트 축**은 반대로 승인된 산출물이 필요하고(그 test 가 따로
사유를 적는다), 그 차이가 이 상수를 쓰는 자리와 쓰지 않는 자리를 가른다."""


def _written_policy(path: Path, **overrides: object) -> EvaluationPolicy:
    path.write_text(
        policy_yaml_text(version=_UNREGISTERED_POLICY_VERSION, **overrides),
        encoding="utf-8",
    )
    loaded = load_evaluation_policy(path)
    assert isinstance(loaded, EvaluationPolicy), loaded
    return loaded


def test_a_token_shaped_object_is_not_a_construction_token(tmp_path: Path) -> None:
    """검사 1(`isinstance`) — **지문이 맞는** 가짜 토큰을 넘긴다. 지문이 맞으므로 검사 2 는
    통과하고, 거부를 만드는 것은 타입 검사 하나다."""
    policy = _written_policy(tmp_path / "policy.yaml")
    module = importlib.import_module("ml_engine.evaluation.policy")
    look_alike = SimpleNamespace(digest=policy.source.digest)
    with pytest.raises(ValueError, match="생성 토큰"):
        module.EvaluationPolicy(parsed=look_alike, **vars(policy))


def test_a_token_from_another_file_is_rejected(tmp_path: Path) -> None:
    """검사 2(토큰 지문 == source 지문) — **진짜 토큰**을 다른 파일에서 가져온다. 타입
    검사는 통과하므로 거부를 만드는 것은 지문 등식 하나다."""
    policy = _written_policy(tmp_path / "policy.yaml")
    other = _written_policy(tmp_path / "other.yaml", max_origins=7)
    assert other.source.digest != policy.source.digest
    module = importlib.import_module("ml_engine.evaluation.policy")
    token = getattr(module, "_Parsed" + "PolicyFile")
    with pytest.raises(ValueError, match="다른 파일의 토큰"):
        module.EvaluationPolicy(
            parsed=token(digest=other.source.digest), **vars(policy)
        )


def test_source_verification_catches_a_file_changed_after_the_load(
    tmp_path: Path,
) -> None:
    """검사 3(파일 바이트 등식) — 로드 뒤 파일에 **주석 한 줄**을 더한다. 값은 그대로라
    `policy_checksum` 등식은 성립하고, 거부를 만드는 것은 바이트 축 하나다. 그 축이 없으면
    실행이 공시한 지문이 더는 그 파일의 것이 아닌데도 통과한다.

    입력은 **승인된 산출물의 무편집 사본**이다(v r3 H-3a 뒤): 정책의 **자기 주장**이
    승인돼 있어야 승인 축이 통과하고, 그래야 이 test 가 재는 것이 바이트 축 하나로 남는다.
    미승인 정책을 쓰면 승인 축이 먼저 거부해 바이트 축 제거 변이가 조용히 초록이 된다."""
    path = tmp_path / "evaluation-v1.yaml"
    path.write_text(_SHIPPED_POLICY.read_text(encoding="utf-8"), encoding="utf-8")
    policy = load_evaluation_policy(path)
    assert isinstance(policy, EvaluationPolicy), policy
    path.write_text(
        f"# 로드 뒤에 더한 줄\n{path.read_text(encoding='utf-8')}", encoding="utf-8"
    )

    reloaded = load_evaluation_policy(path)
    assert isinstance(reloaded, EvaluationPolicy), reloaded
    assert policy_checksum(reloaded) == policy_checksum(policy)  # 값은 같다

    mismatch = verify_policy_source(policy)
    assert isinstance(mismatch, PolicySourceMismatch), mismatch
    assert "파일 바이트" in mismatch.detail


def test_the_gate_policy_is_injected_from_exactly_one_assembly_site() -> None:
    """승인이 **조립 근 하나**에서만 물어지므로, 그 근을 거치지 않고 파이프라인에 정책을
    넣는 자리가 있으면 승인 축이 비어 버린다. 사슬 전체를 집합 등식으로 닫는다:
    env → 승인 요구 로더 → `_preload` → 이 한 자리 → `run_holdout` 한 자리."""
    assert construction_sites("build_training_pipeline") == {
        ("app/pipeline.py", "pipeline_factory")
    }


# ── ⑧ 게이트에 **닿은 값**이 승인된 산출물의 것인가 (v r3 H-3a) ──────────────
# ⑥ 은 승인을 조립 근에서 한 번 물었고, ⑦ 의 재검증은 「객체 == 자기 파일」만 물었다.
# 그래서 부팅 **뒤에** 객체를 바꿔 끼우고 그 객체가 가리킬 **내용이 일치하는 미승인 사본**을
# 디스크에 만들어 두면 두 겹이 다 열렸다(verifier MX1b: 전 게이트 초록, t=0.1 이 게이트에 닿음).
# 호출 자리 집합 등식은 이것을 보지 못한다 — MX1b 는 호출 자리를 하나도 늘리지 않는다.


def _tuned_copy_on_disk(directory: Path, *, version: str, threshold: str) -> Path:
    """출하 정책의 **임계와 이름을 바꾼 사본**을 디스크에 둔다 — 자기 자신과는 일치한다."""
    text = (
        _SHIPPED_POLICY.read_text(encoding="utf-8")
        .replace("paired_t_threshold: 2.58", f"paired_t_threshold: {threshold}")
        .replace("version: evaluation-v1", f"version: {version}")
    )
    path = directory / "tuned.yaml"
    path.write_text(text, encoding="utf-8")
    return path


def _forged_pointing_at(policy: EvaluationPolicy, path: Path) -> EvaluationPolicy:
    """`copy.copy` + `object.__setattr__` — frozen dataclass 도 이렇게는 바뀐다. 생성 토큰을
    거치지 않으므로 `__post_init__` 이 돌지 않는다."""
    text = path.read_text(encoding="utf-8")
    loaded = load_evaluation_policy(path)
    assert isinstance(loaded, EvaluationPolicy), loaded
    forged = copy.copy(policy)
    object.__setattr__(forged, "paired_t_threshold", loaded.paired_t_threshold)
    object.__setattr__(forged, "version", loaded.version)
    object.__setattr__(
        forged,
        "source",
        PolicySource(
            path=path, digest=hashlib.sha256(text.encode("utf-8")).hexdigest()
        ),
    )
    return forged


def test_source_verification_rejects_a_consistent_but_unapproved_copy(
    tmp_path: Path,
) -> None:
    """MX1b — 객체와 파일이 **서로 일치하는데** 그 파일을 아무도 승인하지 않았다. 값 등식과
    바이트 등식이 둘 다 성립하므로(아래 두 단언) 거부를 만들 수 있는 것은 **승인 축**뿐이다."""
    tuned_path = _tuned_copy_on_disk(
        tmp_path, version="evaluation-v1-tuned", threshold="0.1"
    )
    forged = _forged_pointing_at(_shipped_policy(), tuned_path)
    reloaded = load_evaluation_policy(tuned_path)
    assert isinstance(reloaded, EvaluationPolicy), reloaded

    assert forged.paired_t_threshold == 0.1  # 완화가 실제로 들어 있다
    assert reloaded.source.digest == forged.source.digest  # 바이트 등식 성립
    assert policy_checksum(reloaded) == policy_checksum(forged)  # 값 등식 성립

    assert isinstance(verify_policy_source(forged), PolicySourceMismatch)


def test_source_verification_rejects_a_tuned_copy_that_keeps_the_approved_name(
    tmp_path: Path,
) -> None:
    """같은 공격의 다른 꼴 — 이름을 `evaluation-v1` 로 남긴다. 그러면 version 은 등록돼 있고
    거부를 만드는 것은 **지문 불일치**다(같은 승인 축의 둘째 갈래)."""
    tuned_path = _tuned_copy_on_disk(tmp_path, version="evaluation-v1", threshold="0.1")
    forged = _forged_pointing_at(_shipped_policy(), tuned_path)
    assert forged.version == "evaluation-v1"
    assert isinstance(verify_policy_source(forged), PolicySourceMismatch)


def test_source_verification_still_accepts_the_approved_artifact(
    tmp_path: Path,
) -> None:
    """**음성 대조** — 승인된 산출물은 경로가 달라도 통과한다(막는 것은 「미승인」이지
    「사본」이 아니다)."""
    copy_path = tmp_path / "elsewhere.yaml"
    copy_path.write_text(_SHIPPED_POLICY.read_text(encoding="utf-8"), encoding="utf-8")
    loaded = load_evaluation_policy(copy_path)
    assert isinstance(loaded, EvaluationPolicy), loaded
    assert isinstance(verify_policy_source(loaded), EvaluationPolicy)


# ── ⑨ 게이트가 **소비하는 값**이 승인 파일에서 나왔는가 (v r4 H-4) ──────────
# ⑧ 의 세 축은 객체의 **직렬화**(`policy_checksum` = canonical JSON)를 한 시점에 비교한다.
# 그런데 게이트는 그 값을 **거동**으로 소비한다(`-policy.paired_t_threshold` 같은 산술·비교).
# 직렬화와 거동을 갈라 놓을 수 있으면 세 축이 전부 통과한다(verifier F6·F7·F8):
#   F6 `__class__` 교체 — `policy_checksum` 프레임 안에서만 승인값을 돌려주는 property
#   F7 `float` 하위 클래스 — `repr`·json·`==` 는 2.58, `__neg__` 는 -0.1 (checksum 동일)
#   F8 verify 통과 **뒤** 같은 객체를 제자리 변경
# 처방은 축을 더하는 것이 아니라 **값의 출처를 바꾸는 것**이다 — 재검증이 승인 파일에서
# 로더가 **다시 만든** 객체를 돌려주고, 게이트는 그것을 쓴다.


class _LenientThreshold(float):
    """F7 — 직렬화는 승인값, 부정은 완화값(게이트가 쓰는 연산)."""

    def __neg__(self) -> float:
        return -0.1


def _forged_behavior(policy: EvaluationPolicy) -> EvaluationPolicy:
    forged = copy.copy(policy)
    object.__setattr__(
        forged, "paired_t_threshold", _LenientThreshold(policy.paired_t_threshold)
    )
    return forged


def test_a_forged_behavior_passes_all_three_serialization_axes() -> None:
    """전제 고정 — F7 은 세 축을 **전부 통과한다**(승인·바이트·값). 그래서 아래 처방이
    축을 더하는 것이 아니라 **출처를 바꾸는 것**이어야 한다."""
    approved = _shipped_policy()
    forged = _forged_behavior(approved)

    assert policy_checksum(forged) == policy_checksum(approved)  # 값 축 통과
    assert forged.source.digest == approved.source.digest  # 승인·바이트 축 통과
    assert repr(forged.paired_t_threshold) == repr(approved.paired_t_threshold)
    assert -forged.paired_t_threshold == -0.1  # 그런데 거동이 다르다
    assert -approved.paired_t_threshold == -2.58


def test_verification_returns_a_policy_rebuilt_from_the_approved_file() -> None:
    """처방 — 재검증이 돌려주는 것은 **로더가 승인 파일에서 만든 객체**다. 호출자가 넘긴
    객체가 아니므로, 직렬화와 거동을 갈라 놓은 위조도 게이트에 닿지 않는다.

    **조용한 갈아끼움이 아니다**: 승인·바이트·값 세 등식이 **성립한 뒤에만** 일어난다 —
    어긋나면 여전히 fail-closed 다. 즉 판정에 쓰이는 값은 같고, 그 값이 **어디서 왔는지**가
    고정된다."""
    approved = _shipped_policy()
    checked = verify_policy_source(_forged_behavior(approved))

    assert isinstance(checked, EvaluationPolicy), checked
    assert checked is not approved
    behaviour = -checked.paired_t_threshold
    assert behaviour == -2.58  # 거동이 승인값이다


def test_the_rebuilt_policy_carries_only_exact_primitive_types() -> None:
    """재구성 객체가 **정확한 원시 타입**만 담는다 — 하위 클래스가 섞이면 직렬화와 거동이
    다시 갈릴 수 있다. `type(...) is float` 로 본다(`isinstance` 는 하위 클래스를 받는다)."""
    checked = verify_policy_source(_forged_behavior(_shipped_policy()))
    assert isinstance(checked, EvaluationPolicy), checked

    assert type(checked.paired_t_threshold) is float
    assert type(checked.maturity_threshold) is float
    assert type(checked.min_evaluation_rows) is int
    assert type(checked.max_origins) is int
    assert type(checked.agency_baseline_min_count) is int
    assert type(checked.version) is str
    assert all(type(seed) is int for seed in checked.stability_seeds)
    assert all(type(edge) is float for edge in checked.amount_band_edges)
    assert all(type(axis) is str for axis in checked.segment_axes)


def test_mutating_the_callers_object_after_verification_does_not_reach_the_gate() -> (
    None
):
    """F8 — verify 뒤 호출자 객체를 제자리 변경해도 재구성 객체는 영향받지 않는다."""
    approved = _shipped_policy()
    caller_object = copy.copy(approved)
    checked = verify_policy_source(caller_object)
    assert isinstance(checked, EvaluationPolicy), checked

    object.__setattr__(caller_object, "paired_t_threshold", 0.1)

    assert caller_object.paired_t_threshold == 0.1
    assert checked.paired_t_threshold == 2.58


# ── ⑩ 승인 판정이 **호출자 객체가 통제할 수 없는 자리**에서 나온다 (v r5 H-5) ──
# ⑨ 는 게이트가 쓰는 객체를 로더 출력으로 바꿨다. 그런데 **어느 파일에서 재구성하는가**와
# **무엇과 비교하는가**가 여전히 호출자 객체에서 왔고, 재검증이 그 셋을 **거동**으로 소비했다:
#   N1 `source.digest` 를 거짓 `__eq__`/`__ne__` 를 가진 `str` 하위 클래스로 — 파이썬이 반사
#      연산을 먼저 부르므로 승인 축·바이트 축 비교를 둘 다 이긴다. `source.path` 는 완화 파일.
#   N2 `source.path` 를 `PosixPath` 하위 클래스로 — `read_text` 가 완화 텍스트를 돌려주는데
#      그 텍스트의 `encode` 는 **승인 바이트**를 돌려준다(지문은 승인본, 값은 완화본).
#   N6 `source` 를 **읽을 때마다 다른 값**을 주는 property 로(첫 읽기 승인·이후 완화).
#      모든 칸이 **정확 타입**이라 「타입 정확성」만으로는 닫히지 않는다.
# 처방은 안전 성질을 **호출자 객체에서 떼는 것**이다 — 재구성한 정책 **자신**의 `version` 과
# `digest`(로더가 만든 진짜 `str`)로 승인을 한 번 더 묻는다.


class _AlwaysEqual(str):
    """비교를 **항상 같다**로 만드는 `str` 하위 클래스 — 반사 연산이 우선이라 왼쪽이 진짜
    `str` 이어도 이 클래스의 `__eq__`/`__ne__` 가 이긴다."""

    def __eq__(self, other: object) -> bool:
        return True

    def __ne__(self, other: object) -> bool:
        return False

    __hash__ = str.__hash__


class _LyingText(str):
    """`encode` 가 **다른 바이트**를 돌려주는 `str` 하위 클래스."""

    def __init__(self, _value: str) -> None:
        self._encoded = b""

    def with_encoded(self, encoded: bytes) -> _LyingText:
        self._encoded = encoded
        return self

    def encode(self, *args: object, **kwargs: object) -> bytes:  # type: ignore[override]
        return self._encoded


class _LyingPath(PosixPath):
    """`read_text` 가 **거짓말하는 텍스트**를 돌려주는 경로 하위 클래스."""

    _text: _LyingText

    def with_text(self, text: _LyingText) -> _LyingPath:
        self._text = text
        return self

    def read_text(self, *args: object, **kwargs: object) -> str:  # type: ignore[override]
        return self._text


def _relaxed_file(directory: Path) -> Path:
    text = _SHIPPED_POLICY.read_text(encoding="utf-8").replace(
        "paired_t_threshold: 2.58", "paired_t_threshold: 0.1"
    )
    path = directory / "relaxed.yaml"
    path.write_text(text, encoding="utf-8")
    return path


_RELAXED_THRESHOLD: Final[float] = 0.1


def _with_source(policy: EvaluationPolicy, source: PolicySource) -> EvaluationPolicy:
    """`source` 를 바꾸고 임계도 **완화 파일과 같게** 맞춘다.

    값을 맞추는 것이 공격의 일부다: 그래야 값 축(`policy_checksum` 등식)이 재구성 대상
    파일과 **일치해** 통과한다. 맞추지 않으면 값 축이 먼저 거부해, 이 test 들이 재려는
    축(어느 파일에서 재구성하는가)에 닿지 못한다."""
    forged = copy.copy(policy)
    object.__setattr__(forged, "source", source)
    object.__setattr__(forged, "paired_t_threshold", _RELAXED_THRESHOLD)
    return forged


def test_a_digest_that_always_compares_equal_cannot_pick_the_rebuild_file(
    tmp_path: Path,
) -> None:
    """N1 — 주장 지문이 **모든 비교를 이기므로** 승인 축·바이트 축이 둘 다 통과한다. 그러면
    재구성 대상 파일을 호출자가 고른 것이 된다. 승인을 **재구성 객체 자신**에게 물어야
    닫힌다(그 비교의 양쪽이 로더가 만든 진짜 `str` 이다)."""
    approved = _shipped_policy()
    forged = _with_source(
        approved,
        PolicySource(
            path=_relaxed_file(tmp_path),
            digest=_AlwaysEqual(approved.source.digest),
        ),
    )
    # 전제 — 그 지문은 무엇과 비교해도 같다고 답하고(승인·바이트 축), 값은 완화 파일과
    # **같다**(값 축). 즉 세 축이 전부 통과하는 입력이다.
    assert forged.source.digest == "아무 값"
    rebuilt_from = load_evaluation_policy(Path(str(forged.source.path)))
    assert isinstance(rebuilt_from, EvaluationPolicy), rebuilt_from
    assert policy_checksum(rebuilt_from) == policy_checksum(forged)

    checked = verify_policy_source(forged)
    assert isinstance(checked, PolicySourceMismatch), checked


def test_a_path_whose_text_lies_about_its_bytes_cannot_pick_the_rebuild_file(
    tmp_path: Path,
) -> None:
    """N2 — 경로 하위 클래스가 **완화 텍스트**를 돌려주면서 그 텍스트의 `encode` 로 **승인
    지문**을 만든다. 뿌리 로더는 「파싱한 그 텍스트」에서 지문을 내므로 파일 교체 창은 없지만,
    **그 텍스트가 거짓말하면** 지문과 값이 갈린다."""
    approved = _shipped_policy()
    relaxed = _relaxed_file(tmp_path)
    lying = _LyingPath(relaxed).with_text(
        _LyingText(relaxed.read_text(encoding="utf-8")).with_encoded(
            _SHIPPED_POLICY.read_text(encoding="utf-8").encode("utf-8")
        )
    )
    forged = _with_source(
        approved, PolicySource(path=lying, digest=approved.source.digest)
    )

    checked = verify_policy_source(forged)
    assert isinstance(checked, PolicySourceMismatch), checked


def test_a_source_that_changes_between_reads_cannot_pick_the_rebuild_file(
    tmp_path: Path,
) -> None:
    """N6 — `source` 를 읽을 때마다 다른 값을 준다(첫 읽기 승인 출처·이후 완화 파일). 모든
    칸이 **정확 타입**이라 타입 단언으로는 닫히지 않는다 — 안전 성질이 호출자 객체에 걸려
    있는 것이 문제이기 때문이다."""
    approved = _shipped_policy()
    relaxed_policy = load_evaluation_policy(_relaxed_file(tmp_path))
    assert isinstance(relaxed_policy, EvaluationPolicy), relaxed_policy
    assert type(relaxed_policy.source.path) is PosixPath
    assert type(relaxed_policy.source.digest) is str

    reads: list[int] = []

    class _Shifty(EvaluationPolicy):
        @property
        def source(self) -> PolicySource:
            reads.append(1)
            return approved.source if len(reads) == 1 else relaxed_policy.source

    forged = copy.copy(approved)
    object.__setattr__(forged, "paired_t_threshold", _RELAXED_THRESHOLD)
    forged.__dict__.pop("source", None)
    object.__setattr__(forged, "__class__", _Shifty)

    checked = verify_policy_source(forged)
    assert isinstance(checked, PolicySourceMismatch), checked


def test_an_approved_copy_at_another_path_still_passes(tmp_path: Path) -> None:
    """**음성 대조** — 막는 것은 「미승인 내용」이지 「사본」이 아니다. 무편집 사본은 경로가
    달라도 통과하고, 돌려주는 객체는 그 사본에서 재구성된다."""
    copied = tmp_path / "elsewhere.yaml"
    copied.write_text(_SHIPPED_POLICY.read_text(encoding="utf-8"), encoding="utf-8")
    loaded = load_evaluation_policy(copied)
    assert isinstance(loaded, EvaluationPolicy), loaded

    checked = verify_policy_source(loaded)
    assert isinstance(checked, EvaluationPolicy), checked
    assert checked.paired_t_threshold == 2.58


def test_the_rebuild_reads_through_exact_stdlib_types(tmp_path: Path) -> None:
    """재구성의 **입력**이 정확 타입으로 정규화된다 — 경로 하위 클래스의 메서드가 읽기에
    끼어들 자리가 없다. `str()` 과 `Path()` 가 각각 정확 타입을 낸다는 것도 함께 잰다."""
    relaxed = _relaxed_file(tmp_path)
    lying = _LyingPath(relaxed).with_text(_LyingText("x").with_encoded(b"x"))

    assert type(str(lying)) is str
    assert type(Path(str(lying))) is PosixPath
    assert Path(str(lying)) == relaxed
