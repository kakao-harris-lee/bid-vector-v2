"""M5/5C-1 — 모듈 캐시 격리(`tests/adapters/conftest.py`와 같은 사유). 이 디렉터리의
test 가 끝날 때마다 `ml_engine.training` 흔적을 지워 5A `test_serving_purity.py`(전역
`sys.modules` 관측, 편집하지 않는다)가 이 slice 의 신규 test 존재와 무관하게 원래
의미를 유지하게 한다.

`pytest_collection_finish`가 추가로 필요한 이유(실측) — pytest 는 **collection 단계**에서
전체 스위트의 test 모듈을 전부 import 한다(실행 순서와 무관, `tests/training` 이 디렉터리
알파벳상 `tests/gates` 뒤라도 collection 자체는 먼저 끝난다). 그 collection import 가
이미 `ml_engine.training` 을 `sys.modules` 에 심어 놓으므로, 실행이 `tests/gates`
차례에 이르면 이 디렉터리의 test 함수는 아직 한 번도 실행되지 않았어도(따라서 아래
per-test teardown 도 아직 발동 전) 오염이 이미 존재한다. `pytest_collection_finish` 는
collection 이 끝난 직후·모든 test 실행 전에 한 번 더 정리해 이 간극을 닫는다."""

from __future__ import annotations

import sys
from collections.abc import Iterator

import pytest


def _unload_ml_engine_training() -> None:
    for name in list(sys.modules):
        if name == "ml_engine.training" or name.startswith("ml_engine.training."):
            del sys.modules[name]


def pytest_collection_finish(session: pytest.Session) -> None:
    _unload_ml_engine_training()


@pytest.fixture(autouse=True)
def _unload_ml_engine_training_after_test() -> Iterator[None]:
    yield
    _unload_ml_engine_training()
