"""M5/5A 게이트 — `serving` 전 모듈을 실제로 import한 뒤 `sys.modules`에 금지 모듈 부재를
단언한다. import-linter(정적 분석)는 함수 안 지연 import(`if ...: import sqlalchemy`)를
못 잡는다 — 이 test 는 실행 시점 관측이다(설계 검토 (1) 「serving 순수성」 ②③, 우회 (1))."""

from __future__ import annotations

import importlib
import pkgutil
import subprocess
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
    leaked = [
        name
        for name in ("ml_engine.training", "ml_engine.adapters")
        if name in sys.modules
    ]
    assert not leaked, f"serving import 가 금지 패키지를 끌어들였다: {leaked}"


def test_ml_engine_app_is_not_a_serving_purity_target() -> None:
    """M5/5E-1 — `ml_engine.app`(조립 근, training 을 끌어와도 되는 유일한 자리)은
    이 게이트의 대상이 **아니다**. `ml_engine.serving`을 import 하는 것만으로
    `ml_engine.app`이 끌려오지 않음을 확인한다 — `sys.modules`는 프로세스 전역이라
    같은 세션의 다른 test(`tests/app/**`)가 이미 `ml_engine.app`을 import 했을 수
    있으므로, 이 확인은 **격리된 서브프로세스**에서 한다(설계 검토 (0) 위협 모델
    경계 — `app`은 이 게이트가 방어하는 범위 밖임을 실측으로 못 박는다)."""
    script = (
        "import sys\n"
        "import ml_engine.serving\n"
        "import importlib, pkgutil\n"
        "prefix = ml_engine.serving.__name__ + '.'\n"
        "for m in pkgutil.walk_packages(ml_engine.serving.__path__, prefix):\n"
        "    importlib.import_module(m.name)\n"
        "assert 'ml_engine.app' not in sys.modules, sorted(sys.modules)\n"
        "print('OK')\n"
    )
    result = subprocess.run(
        [sys.executable, "-c", script], capture_output=True, text=True, timeout=30
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert "OK" in result.stdout
