#!/usr/bin/env python3
"""M6/6C — liveness 프로브(scope.md ③, D-6C-3). **서버가 RPC 를 받는지**만 잰다 —
`readiness`(정책 preload 상태)와 무관하다: `NOT_READY`(정책 preload 실패)여도 gRPC
서버 자체가 요청을 받아 응답하면 살아 있는 것이다(readiness.py 와 반대 축).

호출은 `GetModelMetadata`(조회 전용, 쓰기 RPC 아님 — scope.md (2b) 「닫는다」)다. 응답의
`result` oneof 가 `metadata`든 `failure`든 상관하지 않는다 — **응답을 받았다는 사실**만
본다. 응답을 못 받으면(연결 거부·타임아웃·기타 transport 오류) 죽은 것으로 본다.

종료 코드(readiness.py 와 의미가 다르다):
  0 — 살아 있음(RPC 응답을 받았다, 내용 무관)
  1 — 죽었음(transport 오류 — 연결 실패·타임아웃)
  2 — 프로브 자신의 설정 오류(ML_ENGINE_BIND 미설정 등, 표준 준비/생존 판정이 아니다)

표준 `grpc.health.v1` Health 서비스를 쓰지 않는다(D-6C-3) — 오케스트레이터 소비자가
아직 없고, 이 슬라이스는 두 프로브로 분리 의미만 세운다.
"""

from __future__ import annotations

import os
import sys
import uuid

import grpc

import bidvector.ml.v1.prediction_pb2 as prediction_pb2
import bidvector.ml.v1.prediction_pb2_grpc as prediction_pb2_grpc

# 헬스체크 전용 인프라 타임아웃 — 업무 정책 값이 아니다(shutdown_grace_seconds 같은
# `ServingPolicy` 데이터와 다른 축, compose 의 healthcheck interval/timeout 과 함께
# 튜닝하는 로컬 상수). 정책 파일로 외부화하지 않는다.
_RPC_TIMEOUT_SECONDS = 3.0


def _target_address() -> str:
    bind = os.environ.get("ML_ENGINE_BIND")
    if not bind:
        raise SystemExit("ML_ENGINE_BIND 가 설정되지 않았다 — 프로브 대상을 알 수 없다")
    # 프로브는 서버와 같은 컨테이너 네트워크 네임스페이스에서 돈다(Docker HEALTHCHECK) —
    # bind 의 포트만 취해 loopback 으로 붙는다.
    port = bind.rsplit(":", 1)[-1]
    return f"127.0.0.1:{port}"


def check_liveness(address: str) -> bool:
    request = prediction_pb2.GetModelMetadataRequest()
    request.envelope.request_id = str(uuid.uuid4())
    request.envelope.correlation_id = "liveness-probe"
    try:
        with grpc.insecure_channel(address) as channel:
            stub = prediction_pb2_grpc.BidPredictionServiceStub(channel)
            stub.GetModelMetadata(request, timeout=_RPC_TIMEOUT_SECONDS)
    except grpc.RpcError:
        return False
    return True


def main() -> int:
    try:
        address = _target_address()
    except SystemExit as exc:
        print(exc, file=sys.stderr)
        return 2
    alive = check_liveness(address)
    if not alive:
        print(f"liveness 실패 — {address} 에서 GetModelMetadata 응답을 받지 못했다", file=sys.stderr)
        return 1
    print(f"liveness OK — {address}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
