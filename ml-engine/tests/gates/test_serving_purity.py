"""M5/5A 게이트 — `serving` 전 모듈을 실제로 import한 뒤 `sys.modules`에 금지 모듈 부재를
단언한다. import-linter(정적 분석)는 함수 안 지연 import(`if ...: import sqlalchemy`)를
못 잡는다 — 이 test 는 실행 시점 관측이다(설계 검토 (1) 「serving 순수성」 ②③, 우회 (1))."""

from __future__ import annotations

import importlib
import pkgutil
import sys

import ml_engine.serving

_FORBIDDEN_MODULE_NAMES = ("sqlalchemy", "psycopg", "requests", "httpx", "celery")


def _import_every_serving_module() -> None:
    package = ml_engine.serving
    prefix = package.__name__ + "."
    for module_info in pkgutil.walk_packages(package.__path__, prefix):
        importlib.import_module(module_info.name)


def test_importing_all_of_serving_does_not_pull_in_forbidden_modules() -> None:
    _import_every_serving_module()
    leaked = [name for name in _FORBIDDEN_MODULE_NAMES if name in sys.modules]
    assert not leaked, f"serving import 가 금지 모듈을 끌어들였다: {leaked}"


def test_ml_engine_training_and_adapters_not_pulled_in_by_serving() -> None:
    """serving 은 `training`·`adapters`도 모른다(pyproject.toml forbidden 계약과 같은 대상,
    여기서는 실행 시점으로 재확인)."""
    _import_every_serving_module()
    leaked = [name for name in ("ml_engine.training", "ml_engine.adapters") if name in sys.modules]
    assert not leaked, f"serving import 가 금지 패키지를 끌어들였다: {leaked}"
