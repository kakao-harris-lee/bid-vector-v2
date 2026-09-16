#!/usr/bin/env python3
"""M6/6C — readiness 프로브(scope.md ③, D-6C-3). **`GetModelMetadata.readiness`
가 READY 인지**만 잰다(5E-1 `ReadinessGate` 실물 — 정책 넷 preload 전부 성공해야
READY, 5E-2 D-5E2-10) — 서버가 살아 있는지(liveness.py)와는 반대 축이다: 서버가
멀쩡히 응답해도 정책 preload 가 하나라도 실패하면 `NOT_READY`이고 이 프로브는
실패해야 한다(우회 (4)).

호출은 liveness.py 와 같은 조회 전용 RPC(`GetModelMetadata`, (2b) 「닫는다」)다 —
같은 요청을 보내되 **판정 축이 다르다**(liveness=응답 유무, readiness=응답 안의
readiness 필드 값).

종료 코드(liveness.py 와 의미가 다르다):
  0 — READY(정책 넷 전부 preload 성공)
  1 — NOT_READY 또는 LOADING, 또는 RPC 응답이 `failure`(envelope 위반 — 이 프로브
      자신은 항상 유효한 envelope 을 보내므로 정상 경로에서는 발생하지 않는다), 또는
      서버 자체가 응답하지 않음(liveness 도 실패라는 뜻 — readiness 는 liveness 를
      함의로 요구하지 않지만 응답 없는 서버는 READY 로 볼 근거가 없다)
  2 — 프로브 자신의 설정 오류(ML_ENGINE_BIND 미설정 등)

출하 정책은 READY 로 수렴해야 한다(5F-1 이 닫은 사실, scope.md ③) — 정상 운영에서
이 프로브가 계속 1 을 내면 6C 의 전제가 깨진 것이다.
"""

from __future__ import annotations

import os
import sys
import uuid

import grpc

import bidvector.ml.v1.prediction_pb2 as prediction_pb2
import bidvector.ml.v1.prediction_pb2_grpc as prediction_pb2_grpc

# liveness.py 와 같은 인프라 타임아웃(업무 정책 아님, 그 파일 주석 참고).
_RPC_TIMEOUT_SECONDS = 3.0

_READY = prediction_pb2.READINESS_READY


def _target_address() -> str:
    bind = os.environ.get("ML_ENGINE_BIND")
    if not bind:
        raise SystemExit("ML_ENGINE_BIND 가 설정되지 않았다 — 프로브 대상을 알 수 없다")
    port = bind.rsplit(":", 1)[-1]
    return f"127.0.0.1:{port}"


def check_readiness(address: str) -> bool:
    request = prediction_pb2.GetModelMetadataRequest()
    request.envelope.request_id = str(uuid.uuid4())
    request.envelope.correlation_id = "readiness-probe"
    try:
        with grpc.insecure_channel(address) as channel:
            stub = prediction_pb2_grpc.BidPredictionServiceStub(channel)
            response = stub.GetModelMetadata(request, timeout=_RPC_TIMEOUT_SECONDS)
    except grpc.RpcError:
        return False
    if response.WhichOneof("result") != "metadata":
        return False
    return bool(response.metadata.readiness == _READY)


def main() -> int:
    try:
        address = _target_address()
    except SystemExit as exc:
        print(exc, file=sys.stderr)
        return 2
    ready = check_readiness(address)
    if not ready:
        print(f"readiness 실패 — {address} 가 READY 가 아니다", file=sys.stderr)
        return 1
    print(f"readiness OK — {address}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
