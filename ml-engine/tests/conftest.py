"""M2/2A — Python 쪽 생성 stub 을 pytest 세션마다 임시 디렉터리에 만든다(VCS 밖, D-M2-3 (a)).

단일 출처는 저장소 루트 `contracts/proto`다(Kotlin 쪽 `ml-contract` 와 같은 소스, ADR 0003 D-1
`.proto` 가 계약의 단일 출처). 여기서 만드는 것은 이 pytest 세션이 쓰는 임시 사본뿐이고
(`grpc_tools.protoc` — S-6, Gradle check 밖), 5A 가 `ml-engine` 패키지 구조를 완성할 때 이
생성 배선을 이어받는다(m2-prep.md D-M2-3).
"""

from __future__ import annotations

import sys
import tempfile
from collections.abc import Iterator
from pathlib import Path

import pytest
from grpc_tools import protoc

_REPO_ROOT = Path(__file__).resolve().parents[2]
_PROTO_ROOT = _REPO_ROOT / "contracts" / "proto"
_PROTO_FILES = (
    "bidvector/ml/v1/common.proto",
    "bidvector/ml/v1/error.proto",
)


@pytest.fixture(scope="session")
def generated_stub_path() -> Iterator[Path]:
    """`grpc_tools.protoc`로 `contracts/proto`를 임시 디렉터리에 생성하고 `sys.path`에 얹는다.
    생성물은 세션 종료 시 디렉터리째 삭제된다 — VCS 에도 리포지터리 안에도 남지 않는다."""
    with tempfile.TemporaryDirectory(prefix="bidvector-ml-contract-py-") as tmp:
        out_dir = Path(tmp)
        args = [
            "grpc_tools.protoc",
            f"--proto_path={_PROTO_ROOT}",
            f"--python_out={out_dir}",
            *(str(_PROTO_ROOT / proto_file) for proto_file in _PROTO_FILES),
        ]
        exit_code = protoc.main(args)
        if exit_code != 0:
            raise RuntimeError(f"grpc_tools.protoc 생성 실패(exit={exit_code}) — args={args}")

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
