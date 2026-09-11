"""ml_engine.contracts — 계약 재수출 경계, 유일한 통로 (D-5A-0 (b)).

`contracts/proto`(저장소 루트, ADR 0003 D-1 단일 출처)의 생성 stub은 `grpc_tools.protoc`가
`ml_engine/contracts/_generated/`에 만든다(VCS 밖 — `.gitignore`, `tools/generate_contracts.py`
+ `tests/conftest.py`가 세션마다 채운다). 이 모듈 **하나만** 그 생성물을 재수출한다 — 다른
어떤 `ml_engine` 하위 패키지도 `bidvector.*`나 `ml_engine.contracts._generated.*`를 직접
import할 수 없다(`pyproject.toml` `[tool.importlinter]` forbidden 계약이 CI 에서 강제,
`tests/gates/test_import_contracts.py` 양성 대조).

**구현 노트(설계 검토 문면과의 차이, 실측 근거)**: D-5A-0 (b) 원문은
`from ._generated.bidvector.ml.v1 import common_pb2, …`(상대 import)를 적었으나, `protoc`가
내는 Python 코드는 형제 proto 를 **항상 절대 import**로 참조한다(예:
`embedding_pb2.py`가 `from bidvector.ml.v1 import common_pb2 as …`) — 이는 `_generated`
아래로 상대 nesting 을 하면 `ModuleNotFoundError: No module named 'bidvector'`로 깨진다(실측,
2026-09-11). 그래서 이 모듈은 2A~2E 와 같은 관례(`sys.path`에 생성 디렉터리를 얹고 절대
import)를 쓴다 — **생성 위치·gitignore 경계·재수출 하나만 허용**이라는 D-5A-0 (b)의 의도는
그대로다, import 문 형태만 protoc 의 제약에 맞췄다.

이름 목록은 `contracts/proto`의 파일 여섯 개와 1:1이다 — `common`·`error`·`features`(메시지만,
service 없음) + `prediction`·`training`·`embedding`(각각 `*_pb2_grpc`도 생성됨,
`tests/gates/test_contracts_reexport.py`가 두 목록을 대조한다).

이 import 는 `ml_engine/contracts/_generated/`가 이미 채워져 있어야 성립한다(pytest 는
`conftest.py`가 collection 이전에 채운다). 정적 검사(mypy·ruff·import-linter)는 생성물의
실제 존재를 요구하지 않는다 — `pyproject.toml`의 `[[tool.mypy.overrides]]`와
`extend-exclude`가 그 경계를 명시한다. `_generated/` 디렉터리 자체는 `.gitkeep`으로 항상
존재한다 — 디렉터리가 아예 없으면 import-linter(grimp)가 이 재수출 import 를 정적 분석하다
세그폴트한다(실측, `.gitignore` 참고).
"""

from __future__ import annotations

import sys
from pathlib import Path

_GENERATED_DIR = Path(__file__).resolve().parent / "_generated"
if str(_GENERATED_DIR) not in sys.path:
    sys.path.insert(0, str(_GENERATED_DIR))

from bidvector.ml.v1 import (  # noqa: E402 — sys.path 준비 뒤에만 성립하는 import
    common_pb2,
    embedding_pb2,
    embedding_pb2_grpc,
    error_pb2,
    features_pb2,
    prediction_pb2,
    prediction_pb2_grpc,
    training_pb2,
    training_pb2_grpc,
)

__all__ = [
    "common_pb2",
    "embedding_pb2",
    "embedding_pb2_grpc",
    "error_pb2",
    "features_pb2",
    "prediction_pb2",
    "prediction_pb2_grpc",
    "training_pb2",
    "training_pb2_grpc",
]
