"""M5/5C-1 — 모듈 캐시 격리. 5A `tests/gates/test_serving_purity.py`는 `sys.modules`를
전역으로 관측해 `serving`이 금지 모듈을 끌어들이지 않았는지 확인한다(5A 소유 파일,
편집하지 않는다). `pytest tests -q`로 전체 스위트를 한 프로세스에서 돌리면 이 디렉터리의
test 가 남긴 `ml_engine.adapters` 캐시가 그 관측을 오염시킬 수 있다 — 이 test 가 끝날 때마다
그 흔적을 지워 5A 게이트가 이 slice 의 신규 test 존재와 무관하게 원래 의미(“serving 자신이
끌어들였는가”)를 유지하게 한다."""

from __future__ import annotations

import sys
from collections.abc import Iterator

import pytest


def _unload_ml_engine_adapters() -> None:
    for name in list(sys.modules):
        if name == "ml_engine.adapters" or name.startswith("ml_engine.adapters."):
            del sys.modules[name]


def pytest_collection_finish(session: pytest.Session) -> None:
    _unload_ml_engine_adapters()


@pytest.fixture(autouse=True)
def _unload_ml_engine_adapters_after_test() -> Iterator[None]:
    yield
    _unload_ml_engine_adapters()
