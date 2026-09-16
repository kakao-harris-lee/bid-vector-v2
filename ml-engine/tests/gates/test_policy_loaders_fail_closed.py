"""M5/5E-3 게이트 — D-5E3-1 의 회귀 방지. `ml_engine` 아래 모든 정책 로더(`*.policy`
서브모듈의 `load_*` 함수 중 `*Rejected` 결과 타입을 갖는 것)를 **기계 수집**해 문법이
깨진 YAML 에 결과 타입만 돌려주고 예외 0 임을 확인한다. 손으로 로더 이름을 나열하지
않는다 — 새 정책 로더가 생기면 이 게이트가 자동으로 수집 대상에 포함시킨다(우회 후보
(5)).

두 번째 게이트: `ml_engine` 안에서 `yaml.safe_load`/`yaml.load` 를 직접 부르는 곳이
`registry.policy` 하나뿐임을 소스 스캔으로 고정한다(우회 후보 (1) — 새 로더가 뿌리를
거치지 않고 `yaml.safe_load` 를 직접 부르면 첫 번째 게이트의 수집 대상이 아닐 수
있으므로, 이 스캔이 그 경로를 별도로 막는다)."""

from __future__ import annotations

import inspect
import pkgutil
import re
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


def _iter_fail_closed_loaders() -> list[tuple[str, object]]:
    """`policy` 서브모듈 각각에서 `load_*` 함수 중 반환 타입 주석에 `Rejected`가
    있는 것만 고른다 — `registry.policy.load_policy`(반환 타입이 단일 `Policy`,
    실패를 예외로 던지는 뿌리 그 자체)는 이 조건으로 자연히 제외된다. 이름 규약
    (`load_` 접두)과 타입 규약(`Rejected` 결과 유니온) 둘 다를 걸어, 뿌리 함수
    자체가 늘어나거나 이름이 바뀌어도 손 목록 없이 옳게 걸러진다."""
    loaders: list[tuple[str, object]] = []
    for module in _iter_policy_modules():
        for name, func in inspect.getmembers(module, inspect.isfunction):
            if func.__module__ != module.__name__:
                continue  # re-export 는 정의된 모듈에서만 센다(중복 수집 방지)
            if not name.startswith("load_"):
                continue
            signature = inspect.signature(func)
            return_annotation = str(signature.return_annotation)
            if "Rejected" not in return_annotation:
                continue
            loaders.append((f"{module.__name__}.{name}", func))
    return loaders


def test_at_least_four_fail_closed_policy_loaders_are_collected() -> None:
    """수집 수가 4 미만이면 기계 수집 자체가 깨졌다는 신호다(우회 후보 (5))."""
    loaders = _iter_fail_closed_loaders()
    names = sorted(name for name, _ in loaders)
    assert len(loaders) >= 4, f"수집된 로더가 4 미만: {names}"


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


_YAML_LOAD_CALL_PATTERN = re.compile(r"\byaml\s*\.\s*(safe_load|load)\s*\(")


def test_only_registry_policy_calls_yaml_safe_load_directly() -> None:
    """`ml_engine` 소스 전체에서 `yaml.safe_load`/`yaml.load` 를 직접 부르는 파일이
    `registry/policy.py` 하나뿐임을 소스 스캔으로 고정한다(우회 후보 (1)). 새 로더가
    뿌리를 거치지 않고 이 함수들을 직접 부르면 여기서 걸린다 — `_iter_fail_closed_
    loaders`의 수집 대상이 아니어도 이 게이트는 소스 텍스트만 보므로 놓치지 않는다."""
    package_root = Path(ml_engine.__file__).resolve().parent
    root_loader_path = package_root / "registry" / "policy.py"

    offenders: list[str] = []
    for path in package_root.rglob("*.py"):
        if path.resolve() == root_loader_path.resolve():
            continue
        text = path.read_text(encoding="utf-8")
        if _YAML_LOAD_CALL_PATTERN.search(text):
            offenders.append(str(path.relative_to(package_root)))

    assert not offenders, (
        "registry/policy.py 밖에서 yaml.safe_load/yaml.load 를 직접 호출한다: "
        f"{offenders}"
    )
