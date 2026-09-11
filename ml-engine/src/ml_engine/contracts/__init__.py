"""ml_engine.contracts — 계약 재수출 경계, 유일한 통로 (D-5A-0 (b), 위치는 verifier r1
F-1·F-2 뒤 정정).

`contracts/proto`(저장소 루트, ADR 0003 D-1 단일 출처)의 생성 stub은 `grpc_tools.protoc`가
`ml-engine/.contracts-generated/`에 만든다 — **`ml_engine` 패키지 트리 밖**(VCS 밖,
`.gitignore`, `tools/generate_contracts.py` + `tests/conftest.py`가 세션마다 채운다). 이 모듈
**하나만** 그 생성물을 재수출한다.

경계는 두 층이다: ① `bidvector`(생성 stub 의 top-level 이름)를 다른 어떤 `ml_engine` 하위
패키지도 **직접** import할 수 없다 — `pyproject.toml`의 forbidden 계약(`allow_indirect_imports
= true`)이 CI 에서 강제하되, **이 모듈을 거친 간접 경로**(`from ml_engine.contracts import
common_pb2`)는 승인 통로로 허용한다(verifier r1 F-1 — 기본값은 간접 연쇄까지 막아 패키지가
빌 때만 초록이었다). ② 생성물이 `ml_engine` 패키지 트리 **밖**에 있으므로
`ml_engine.contracts._generated`나 그 하위를 가리키는 Python import 경로가 애초에 **존재하지
않는다**(verifier r1 F-2 — 트리 안에 있으면 grimp 그래프에 노드가 안 잡혀 forbidden 열거가
아무것도 못 걸렀다. 열거 대신 경로를 없앴다). `tests/gates/test_import_contracts.py`가 승인
통로(양성)·직접 import(계속 차단)를, `tests/gates/test_generated_not_tracked.py`가 구조적
폐쇄(`ModuleNotFoundError`)를 확인한다.

**구현 노트(설계 검토 문면과의 차이, 실측 근거)**: D-5A-0 (b) 원문은 상대 import
(`from ._generated.bidvector.ml.v1 import common_pb2, …`)를 적었으나, `protoc`가 내는 Python
코드는 형제 proto 를 **항상 절대 import**로 참조한다(예: `embedding_pb2.py`가
`from bidvector.ml.v1 import common_pb2 as …`) — 상대 nesting 은
`ModuleNotFoundError: No module named 'bidvector'`로 깨진다(실측, 2026-09-11). 그래서 이
모듈은 2A~2E 와 같은 관례(`sys.path`에 생성 디렉터리를 얹고 절대 import)를 쓴다 — **생성
위치·gitignore 경계·재수출 하나만 허용**이라는 D-5A-0 (b)의 의도는 그대로다, import 문
형태만 protoc 의 제약에 맞췄다.

이름 목록은 `contracts/proto`의 파일 여섯 개와 1:1이다 — `common`·`error`·`features`(메시지만,
service 없음) + `prediction`·`training`·`embedding`(각각 `*_pb2_grpc`도 생성됨,
`tests/gates/test_contracts_reexport.py`가 두 목록을 대조한다).

이 import 는 `.contracts-generated/`가 이미 채워져 있어야 성립한다(pytest 는 `conftest.py`가
collection 이전에 채운다). 정적 검사(mypy·ruff·import-linter)는 생성물의 실제 존재를 요구하지
않는다 — 생성물이 `src/ml_engine` 스캔 범위 밖이라 mypy·ruff exclude 대상 자체가 아니고,
import-linter(grimp)는 `bidvector`를 sqlalchemy 같은 external 이름으로만 다뤄 파일 존재
여부와 무관하다(실측 — 디렉터리가 아예 없어도 세그폴트하지 않는다, 이전 판 기록은 옛 상대
import 코드 기준이었다).
"""

from __future__ import annotations

import sys
from pathlib import Path

# 패키지 트리 밖(D-5A-0 (b) 정정) — `parents[3]`은 `.../ml-engine/src/ml_engine/contracts/
# __init__.py`에서 `ml-engine/`(0=contracts, 1=ml_engine, 2=src, 3=ml-engine).
_GENERATED_DIR = Path(__file__).resolve().parents[3] / ".contracts-generated"
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
