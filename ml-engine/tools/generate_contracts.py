#!/usr/bin/env python3
"""`contracts/proto`(단일 출처, ADR 0003 D-1)를 `grpc_tools.protoc`로 생성하는 배선 하나.

`ml-engine/tests/conftest.py`(pytest 세션)와 이 파일을 CLI 로 부르는 CI/개발자 양쪽이
**같은 함수**(`generate`)를 쓴다 — M2/2A 가 만든 conftest 안의 protoc 호출을 5A 가 이어받아
한 자리로 모은다(scope.md ①, D-5A-0 (b)).

이 스크립트는 `ml-engine/` 안에서만 쓰는 개발 도구다(`[project.dependencies]` 밖, 배포되는
`ml_engine` 패키지의 일부가 아니다) — `ml_engine` 런타임 코드는 이 모듈을 import하지 않는다.
"""

from __future__ import annotations

import argparse
import sys
from collections.abc import Sequence
from pathlib import Path

import grpc_tools
from grpc_tools import protoc

_REPO_ROOT = Path(__file__).resolve().parents[2]
PROTO_ROOT = _REPO_ROOT / "contracts" / "proto"
# `training.proto`가 well-known type(`google/protobuf/timestamp.proto`)을 쓴다(D-2C-5) —
# grpc_tools 는 buf 와 달리 well-known type 을 자동으로 찾지 못해 배포에 번들된 `_proto`
# 사본을 별도 --proto_path 로 준다(2C·2D·2E 와 같은 관례).
WELL_KNOWN_TYPES_ROOT = Path(grpc_tools.__file__).resolve().parent / "_proto"

# `contracts/proto/bidvector/ml/v1/*.proto` 전체(6개) — 2A~2E 가 각 test 파일에서 부분
# 집합만 생성하던 것을 5A 가 전체 목록으로 통일한다. `ml_engine.contracts` 재수출 이름
# 목록과 이 목록이 1:1 이다(`tests/gates/test_contracts_reexport.py`가 대조).
PROTO_FILES: tuple[str, ...] = (
    "bidvector/ml/v1/common.proto",
    "bidvector/ml/v1/error.proto",
    "bidvector/ml/v1/features.proto",
    "bidvector/ml/v1/prediction.proto",
    "bidvector/ml/v1/training.proto",
    "bidvector/ml/v1/embedding.proto",
)


def generate(out_dir: Path, *, proto_files: Sequence[str] = PROTO_FILES) -> None:
    """`proto_files`를 `out_dir`에 생성한다(`--python_out` + `--grpc_python_out`).

    `out_dir`는 이미 존재해야 한다(호출자가 만든다) — 이 함수는 디렉터리를 만들지 않는다.
    """
    args = [
        "grpc_tools.protoc",
        f"--proto_path={PROTO_ROOT}",
        f"--proto_path={WELL_KNOWN_TYPES_ROOT}",
        f"--python_out={out_dir}",
        f"--grpc_python_out={out_dir}",
        *(str(PROTO_ROOT / proto_file) for proto_file in proto_files),
    ]
    exit_code = protoc.main(args)
    if exit_code != 0:
        raise RuntimeError(
            f"grpc_tools.protoc 생성 실패(exit={exit_code}) — args={args}"
        )


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="contracts/proto 를 grpc_tools.protoc 로 지정 디렉터리에 생성한다."
    )
    parser.add_argument("out_dir", type=Path, help="생성 대상 디렉터리(없으면 만든다)")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    args.out_dir.mkdir(parents=True, exist_ok=True)
    generate(args.out_dir)
    print(f"생성 완료: {args.out_dir}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
