"""M5/5A 게이트 — `serving` 전 모듈을 실제로 import한 뒤 `sys.modules`에 금지 모듈 부재를
단언한다. import-linter(정적 분석)는 함수 안 지연 import(`if ...: import sqlalchemy`)를
못 잡는다 — 이 test 는 실행 시점 관측이다(설계 검토 (1) 「serving 순수성」 ②③, 우회 (1))."""

from __future__ import annotations

import subprocess
import sys

_IMPORT_ALL_SERVING = (
    "import ml_engine.serving\n"
    "import importlib, pkgutil\n"
    "prefix = ml_engine.serving.__name__ + '.'\n"
    "for m in pkgutil.walk_packages(ml_engine.serving.__path__, prefix):\n"
    "    importlib.import_module(m.name)\n"
)


def _assert_in_isolated_subprocess(assertion: str) -> None:
    """verifier r2 관찰 — 이 파일 세 test 전부 `sys.modules`(프로세스 전역)를 읽는다.
    이전 판은 셋 중 하나(`ml_engine.app` 확인)만 서브프로세스로 격리했다 — 나머지
    둘은 in-process 로 `sys.modules`를 봐서, 같은 pytest 세션에서 **먼저 실행된
    무관한 test**(예: `tests/app/**`가 `ml_engine.app`을 top-level import 해
    `ml_engine.training`을 전이 import)가 이미 남긴 흔적을 "serving 이 끌어들인
    것"으로 오판할 수 있었다(실측 — `tests/app/test_server.py`와 이 파일만 골라
    돌리면 재현된다. `tests/training/conftest.py`의 정리 hook 은 그 디렉터리가
    collection 에 실릴 때만 발동해, 더 좁은 선택에서는 발동하지 않는다). 셋 다
    매번 새 서브프로세스에서 돌려 세션 전역 오염과 완전히 무관하게 만든다."""
    script = "import sys\n" + _IMPORT_ALL_SERVING + assertion + "print('OK')\n"
    result = subprocess.run(
        [sys.executable, "-c", script], capture_output=True, text=True, timeout=30
    )
    assert result.returncode == 0, result.stdout + result.stderr
    assert "OK" in result.stdout


def test_importing_all_of_serving_does_not_pull_in_forbidden_modules() -> None:
    assertion = (
        "forbidden = ('sqlalchemy', 'psycopg', 'requests', 'httpx', 'celery')\n"
        "leaked = [n for n in forbidden if n in sys.modules]\n"
        "assert not leaked, 'serving import 가 금지 모듈을 끌어들였다: ' + repr(leaked)\n"
    )
    _assert_in_isolated_subprocess(assertion)


def test_ml_engine_training_and_adapters_not_pulled_in_by_serving() -> None:
    """serving 은 `training`·`adapters`도 모른다(pyproject.toml forbidden 계약과 같은 대상,
    여기서는 실행 시점으로 재확인)."""
    assertion = (
        "leaked = [n for n in ('ml_engine.training', 'ml_engine.adapters')"
        " if n in sys.modules]\n"
        "assert not leaked, 'serving import 가 금지 패키지를 끌어들였다: ' + repr(leaked)\n"
    )
    _assert_in_isolated_subprocess(assertion)


def test_ml_engine_app_is_not_a_serving_purity_target() -> None:
    """M5/5E-1 — `ml_engine.app`(조립 근, training 을 끌어와도 되는 유일한 자리)은
    이 게이트의 대상이 **아니다**. `ml_engine.serving`을 import 하는 것만으로
    `ml_engine.app`이 끌려오지 않음을 확인한다(설계 검토 (0) 위협 모델 경계 —
    `app`은 이 게이트가 방어하는 범위 밖임을 실측으로 못 박는다)."""
    assertion = "assert 'ml_engine.app' not in sys.modules, sorted(sys.modules)\n"
    _assert_in_isolated_subprocess(assertion)
