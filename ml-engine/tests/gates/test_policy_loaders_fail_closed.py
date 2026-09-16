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

import inspect
import pkgutil
from importlib import import_module
from pathlib import Path
from types import ModuleType

import ml_engine

_ROOT_LOADER_MODULE = "ml_engine.registry.policy"
_ROOT_LOADER_FUNC = "load_policy"

_MALFORMED_YAML = "scenario.z: [unclosed\n"


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
