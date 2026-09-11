"""M5/5A — `tools/generate_contracts.py`(conftest 와 CLI 가 공유하는 생성 함수, D-5A-0 (b))로
두 가지를 한다.

(1) module 레벨에서 **즉시**(pytest collection 이전) `ml-engine/.contracts-generated/`
(패키지 트리 **밖**, VCS 밖 — verifier r1 F-2 뒤 정정, gitignore)에 `contracts/proto`
전체(6개)를 생성한다 — `ml_engine.contracts` 재수출(`src/ml_engine/contracts/__init__.py`)이
이 자리를 `sys.path`에 얹고 절대 import하므로, 어느 test 파일이든 module 최상단에서
`ml_engine.contracts`를 import할 수 있고 pytest 는 conftest.py 를 그 형제 test 파일들보다
항상 먼저 로드하므로 여기서 미리 채운다.

(2) M2/2A 가 쓰던 session fixture(`common_pb2`·`error_pb2`, 임시 디렉터리 + `sys.path` 삽입)는
그대로 남긴다 — 생성 로직만 공유 함수로 바꿨을 뿐 동작은 같다. 2B~2E 는 각자 독립 module-scope
fixture로 생성하므로(이 파일을 건드리지 않는다) 회귀 없음.
"""

from __future__ import annotations

import sys
import tempfile
from collections.abc import Iterator
from pathlib import Path

import pytest

from tools.generate_contracts import generate

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[1]
# 패키지 트리 밖(D-5A-0 (b) 정정, verifier r1 F-2) — `src/ml_engine/contracts/` 안이 아니라
# `ml-engine/` 바로 아래라 `ml_engine.contracts.<하위>` 라는 Python import 경로 자체가 없다.
_CONTRACTS_GENERATED_DIR = _ML_ENGINE_ROOT / ".contracts-generated"
_PROTO_FILES_2A = (
    "bidvector/ml/v1/common.proto",
    "bidvector/ml/v1/error.proto",
)

_CONTRACTS_GENERATED_DIR.mkdir(parents=True, exist_ok=True)
generate(_CONTRACTS_GENERATED_DIR)


@pytest.fixture(scope="session")
def generated_stub_path() -> Iterator[Path]:
    """`common.proto`·`error.proto`만 임시 디렉터리에 생성하고 `sys.path`에 얹는다(2A 관례
    그대로 — 이 fixture 를 쓰는 round-trip test 는 `.contracts-generated`가 아니라 이 임시
    사본을 쓴다). 생성물은 세션 종료 시 디렉터리째 삭제된다."""
    with tempfile.TemporaryDirectory(prefix="bidvector-ml-contract-py-") as tmp:
        out_dir = Path(tmp)
        generate(out_dir, proto_files=_PROTO_FILES_2A)
        sys.path.insert(0, str(out_dir))
        try:
            yield out_dir
        finally:
            sys.path.remove(str(out_dir))


@pytest.fixture(scope="session")
def common_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import common_pb2 as module

    return module


@pytest.fixture(scope="session")
def error_pb2(generated_stub_path: Path):
    from bidvector.ml.v1 import error_pb2 as module

    return module
