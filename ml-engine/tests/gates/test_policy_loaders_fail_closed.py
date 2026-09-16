"""M5/5E-3 게이트 — D-5E3-1 의 회귀 방지, D-5E3-6(verifier r1 HIGH-1) 로 구조 강화.

`ml_engine` 아래 모든 정책 로더(`*.policy` 서브모듈의 `load_*` 함수)를 **이름 규약
하나만으로** 기계 수집해 문법이 깨진 YAML 에 결과 타입만 돌려주고 예외 0 임을
확인한다. 손으로 로더 이름을 나열하지 않는다 — 새 정책 로더가 생기면 이 게이트가
자동으로 수집 대상에 포함시킨다(우회 후보 (5)).

D-5E3-6 이전 판은 반환 타입 **주석 문자열**에 `Rejected` 가 있는지로 걸렀는데,
verifier r1 이 반환 주석을 타입 별칭으로 적은 다섯째 로더로 그 필터를 조용히
피해갔다(수집에서 빠진 로더는 「예외 0」 확인 대상에서도 빠진다). 이번 판은 그
필터를 없애고 **이름 규약(`load_` 접두, 모듈에 정의됨, 뿌리 함수는 이름으로
명시 제외)만** 쓴다 — `test_collected_loader_set_equals_independent_enumeration`
이 서로 다른 내부 경로(inspect vs `vars()`+`callable`)로 두 번 계산해 같은
집합이 나오는지 대조하므로, 이 파일에 다시 숨은 이차 필터가 끼어들면 그 즉시
드러난다.

「yaml 을 직접 부르는 곳이 `registry.policy` 하나뿐」이라는 우회 후보 (1) 은 이제
텍스트 스캔이 아니라 `ml-engine/pyproject.toml` 의 import-linter `forbidden` 계약
(S-4, `yaml 을 직접 import 하는 곳은 registry.policy 하나`)이 진다 — 그 계약은 AST
의 모든 import 문을 대상으로 삼아 별칭 import·`from yaml import`·형제 진입점을
전부 잡는다(verifier r1 이 텍스트 스캔에서 실측한 우회 넷 전부). 이 파일에 있던
정규식 기반 소스 스캔 test 는 **삭제**했다 — 같은 불변식을 검사 텍스트보다 훨씬
튼튼하게 잡는 계약이 이미 있는데 정규식 스캔을 남겨 두면 두 겹이 서로 다른 말을
할 뿐 값이 없다(둘 다 유지하지 않는다는 D-5E3-6 ③의 선택)."""

from __future__ import annotations

import ast
import inspect
import pkgutil
import tomllib
from importlib import import_module
from pathlib import Path
from types import ModuleType

import ml_engine

_ROOT_LOADER_MODULE = "ml_engine.registry.policy"
_ROOT_LOADER_FUNC = "load_policy"

_MALFORMED_YAML = "scenario.z: [unclosed\n"

_YAML_IMPORT_CONTRACT_FORBIDDEN_MODULE = "yaml"


def _iter_policy_modules() -> list[ModuleType]:
    """`ml_engine` 아래 이름이 `policy`인 서브모듈을 전부 찾는다(pkgutil 순회 —
    손으로 패키지 이름을 나열하지 않는다)."""
    modules: list[ModuleType] = []
    prefix = ml_engine.__name__ + "."
    for module_info in pkgutil.walk_packages(ml_engine.__path__, prefix=prefix):
        if module_info.name.rsplit(".", 1)[-1] != "policy":
            continue
        modules.append(import_module(module_info.name))
    return modules


def _is_root(module_name: str, func_name: str) -> bool:
    """뿌리 함수(`registry.policy.load_policy`, 예외를 던지는 계약) 명시 제외 —
    반환 타입 등 다른 신호로 판정하지 않는다(D-5E3-6, HIGH-1 이 문제 삼은 바로
    그 판정 방식)."""
    return module_name == _ROOT_LOADER_MODULE and func_name == _ROOT_LOADER_FUNC


def _load_prefixed_functions(module: ModuleType) -> dict[str, object]:
    """모듈에 **정의된**(재수출 아님) `load_` 접두 함수 전부 — 이름 규약 하나만
    건다. `inference/policy.py`가 `from ml_engine.registry.policy import
    load_policy`(별칭 없이)로 뿌리를 재수출하므로, `__module__`이 이 모듈
    자신인 것만 센다(그러지 않으면 뿌리가 `inference.policy`에도 잡혀 이중
    수집된다)."""
    result: dict[str, object] = {}
    for name, func in inspect.getmembers(module, inspect.isfunction):
        if not name.startswith("load_"):
            continue
        if func.__module__ != module.__name__:
            continue
        result[name] = func
    return result


def _enumerate_load_functions_independently(module: ModuleType) -> set[str]:
    """`_load_prefixed_functions`와 **다른 내부 경로**(`inspect.getmembers`+
    `inspect.isfunction` 대신 `vars()`+`callable`)로 같은 이름 규약을 다시
    계산한다 — 두 구현이 갈리면 한쪽에 숨은 필터가 있다는 신호다."""
    names: set[str] = set()
    for name, value in vars(module).items():
        if not name.startswith("load_"):
            continue
        if not callable(value):
            continue
        if getattr(value, "__module__", None) != module.__name__:
            continue
        names.add(name)
    return names


def _iter_fail_closed_loaders() -> list[tuple[str, object]]:
    """정책 모듈마다 `_load_prefixed_functions`를 걷어 뿌리만 이름으로 제외한다."""
    loaders: list[tuple[str, object]] = []
    for module in _iter_policy_modules():
        for name, func in _load_prefixed_functions(module).items():
            if _is_root(module.__name__, name):
                continue
            loaders.append((f"{module.__name__}.{name}", func))
    return loaders


def test_every_non_root_policy_module_has_at_least_one_loader() -> None:
    """정책 모듈마다 로더 ≥1(하한 `>= 4` 폐지, D-5E3-6 ② — 모듈이 늘어도 이
    불변은 「그 모듈 자신에 로더가 있는가」로 표류하지 않는다)."""
    empty: list[str] = []
    for module in _iter_policy_modules():
        count = sum(
            1
            for name in _load_prefixed_functions(module)
            if not _is_root(module.__name__, name)
        )
        if module.__name__ == _ROOT_LOADER_MODULE:
            continue  # 뿌리 모듈 자신은 fail-closed 로더를 0 개 갖는 것이 계약
        if count < 1:
            empty.append(module.__name__)
    assert not empty, f"로더가 하나도 없는 정책 모듈: {empty}"


def test_collected_loader_set_equals_independent_enumeration() -> None:
    """`_iter_fail_closed_loaders`의 수집 결과와, 다른 내부 경로로 다시 계산한
    집합이 정확히 같아야 한다 — 반환 타입 주석 같은 숨은 이차 필터가 조용히
    끼어들면(verifier r1 HIGH-1) 이 test 가 그 차이를 드러낸다."""
    collected = {name for name, _ in _iter_fail_closed_loaders()}
    independent = {
        f"{module.__name__}.{name}"
        for module in _iter_policy_modules()
        for name in _enumerate_load_functions_independently(module)
        if not _is_root(module.__name__, name)
    }
    assert collected == independent


def test_root_loader_itself_is_not_collected_as_fail_closed() -> None:
    """`registry.policy.load_policy` 는 예외를 던지는 뿌리다 — 결과 타입 로더
    집합에 섞이면 이 게이트가 뿌리 자체에 「예외 0」을 요구하게 되어 D-5E3-1 계약
    (뿌리는 `PolicyError` 를 던진다)과 모순된다."""
    names = {name for name, _ in _iter_fail_closed_loaders()}
    assert f"{_ROOT_LOADER_MODULE}.{_ROOT_LOADER_FUNC}" not in names


def test_every_collected_loader_rejects_malformed_yaml_without_raising(
    tmp_path: Path,
) -> None:
    """수집된 로더 각각에 문법 깨진 YAML 을 주면 예외 없이 결과 타입(이름에
    `Rejected`가 들어간 dataclass)만 돌아온다."""
    malformed = tmp_path / "malformed.yaml"
    malformed.write_text(_MALFORMED_YAML, encoding="utf-8")

    loaders = _iter_fail_closed_loaders()
    assert loaders, "수집된 로더가 없다 — _iter_fail_closed_loaders 자체를 점검하라"

    failures: list[str] = []
    for qualified_name, func in loaders:
        try:
            result = func(malformed)
        except Exception as exc:  # noqa: BLE001 — 게이트: 예외 자체가 실패 신호
            failures.append(f"{qualified_name} 가 예외를 던졌다: {exc!r}")
            continue
        if type(result).__name__.find("Rejected") == -1:
            failures.append(
                f"{qualified_name} 가 Rejected 류가 아닌 값을 돌려줬다: {result!r}"
            )
    assert not failures, "\n".join(failures)


def _pyproject_toml_path() -> Path:
    """`ml_engine` 패키지에서 세 단계 위 — `ml-engine/pyproject.toml`."""
    return Path(ml_engine.__file__).resolve().parents[2] / "pyproject.toml"


def _yaml_ignore_import_source_modules() -> list[str]:
    """`pyproject.toml`을 **직접 읽어** import-linter 계약 중 `forbidden_modules`
    에 `yaml`이 있는 것을 찾고, 그 `ignore_imports`에서 `-> yaml`로 끝나는 항목의
    소스 모듈을 뽑는다(손 목록 아님, D-5E3-6 ④). 계약 자체가 바뀌면(예외가
    늘거나 줄면) 이 함수도 같이 바뀐다 — pyproject.toml 과 다른 말을 할 수 없다."""
    data = tomllib.loads(_pyproject_toml_path().read_text(encoding="utf-8"))
    contracts = data.get("tool", {}).get("importlinter", {}).get("contracts", [])
    modules: list[str] = []
    for contract in contracts:
        if _YAML_IMPORT_CONTRACT_FORBIDDEN_MODULE not in contract.get(
            "forbidden_modules", []
        ):
            continue
        for entry in contract.get("ignore_imports", []):
            source, _, target = entry.partition("->")
            if target.strip() == _YAML_IMPORT_CONTRACT_FORBIDDEN_MODULE:
                modules.append(source.strip())
    return modules


def _yaml_exception_modules_besides_root() -> list[str]:
    """예외 모듈 중 뿌리(`registry.policy`, 실제 파싱 지점)를 뺀 나머지 —
    out_of_scope 가 강제하는 「자기 `except yaml.YAMLError` 절만 가진」 셋."""
    return [
        name
        for name in _yaml_ignore_import_source_modules()
        if name != _ROOT_LOADER_MODULE
    ]


def _yaml_reference_summary(module_name: str) -> tuple[set[str], set[str], bool]:
    """모듈 소스를 AST 로 읽어 (1) `yaml.<attr>` 속성 접근 이름 집합 (2)
    `yaml.<attr>(...)` 형태로 **호출된** 속성 이름 집합 (3) `from yaml import ...`
    존재 여부를 낸다. 이름 규약(`load_` 접두)과 무관하게 모듈 전체를 훑으므로
    verifier r2 MEDIUM-1(비-`load_` 이름의 새 로더)이 여기서는 통하지 않는다."""
    module = import_module(module_name)
    tree = ast.parse(Path(module.__file__).read_text(encoding="utf-8"))
    attrs: set[str] = set()
    called_attrs: set[str] = set()
    has_from_import = False
    for node in ast.walk(tree):
        if isinstance(node, ast.ImportFrom) and node.module == "yaml":
            has_from_import = True
        if (
            isinstance(node, ast.Attribute)
            and isinstance(node.value, ast.Name)
            and node.value.id == "yaml"
        ):
            attrs.add(node.attr)
        if (
            isinstance(node, ast.Call)
            and isinstance(node.func, ast.Attribute)
            and isinstance(node.func.value, ast.Name)
            and node.func.value.id == "yaml"
        ):
            called_attrs.add(node.func.attr)
    return attrs, called_attrs, has_from_import


def test_exception_modules_reference_only_yaml_yamlerror() -> None:
    """D-5E3-6 ④(verifier r2 MEDIUM-1) — `pyproject.toml`의 `ignore_imports` 예외
    모듈(뿌리 제외) 은 `yaml.YAMLError` 타입 참조 **하나만** 허용한다. `yaml.
    safe_load` 같은 파싱 호출이나 `from yaml import ...` 는 이름이 `load_` 접두가
    아니어도(예: `read_training_policy_v2`) 이 test 가 이름 규약과 무관하게
    잡는다 — `_load_prefixed_functions`(이름 규약 기반 수집)와 이 test(AST 기반
    전수 스캔)는 서로 다른 방어선이다."""
    modules = _yaml_exception_modules_besides_root()
    assert modules, "예외 모듈이 없다 — pyproject.toml 의 ignore_imports 를 점검하라"

    failures: list[str] = []
    for module_name in modules:
        attrs, called_attrs, has_from_import = _yaml_reference_summary(module_name)
        if attrs - {"YAMLError"}:
            failures.append(
                f"{module_name} 가 YAMLError 외 yaml 속성을 참조한다: {sorted(attrs)}"
            )
        if called_attrs:
            failures.append(
                f"{module_name} 가 yaml.<속성>(...) 형태로 호출한다: "
                f"{sorted(called_attrs)}"
            )
        if has_from_import:
            failures.append(f"{module_name} 가 `from yaml import ...` 를 쓴다")
    assert not failures, "\n".join(failures)
