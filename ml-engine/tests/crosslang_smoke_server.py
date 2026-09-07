"""M2/2D S-6 — 교차 언어 socket 스모크의 Python 쪽 서버. `tools/contract-crosslang-smoke.sh`
가 이 파일을 별도 프로세스로 실행한다(`python crosslang_smoke_server.py <host:port>`).

**test 파일이 아니다** — `test_*.py` 명명 관례를 따르지 않아 pytest 가 수집하지 않는다
(`ml-engine/tests/**`는 in_scope 이지만 pytest 수집 대상일 필요는 없다). `contracts/proto`를
임시 디렉터리에 생성해 쓰고(D-M2-3 (a)와 같은 관례) 실제 TCP 소켓에 `grpc.server()`를 띄운다
— 요청만으로 답한다는 것(DB 조회 없음)을 형태로 보인다(fake 는 애초에 DB 를 갖지 않는다).
"""

from __future__ import annotations

import sys
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import grpc
import grpc_tools
from grpc_tools import protoc

_REPO_ROOT = Path(__file__).resolve().parents[2]
_GRPC_TOOLS_WELL_KNOWN_TYPES_ROOT = Path(grpc_tools.__file__).resolve().parent / "_proto"
_PROTO_ROOT = _REPO_ROOT / "contracts" / "proto"
_PREDICTION_TESTDATA_ROOT = _REPO_ROOT / "contracts" / "testdata" / "prediction"
_PROTO_FILES = (
    "bidvector/ml/v1/common.proto",
    "bidvector/ml/v1/error.proto",
    "bidvector/ml/v1/features.proto",
    "bidvector/ml/v1/prediction.proto",
    "bidvector/ml/v1/training.proto",
)


def _generate_stub(out_dir: Path) -> None:
    args = [
        "grpc_tools.protoc",
        f"--proto_path={_PROTO_ROOT}",
        f"--proto_path={_GRPC_TOOLS_WELL_KNOWN_TYPES_ROOT}",
        f"--python_out={out_dir}",
        f"--grpc_python_out={out_dir}",
        *(str(_PROTO_ROOT / proto_file) for proto_file in _PROTO_FILES),
    ]
    exit_code = protoc.main(args)
    if exit_code != 0:
        raise RuntimeError(f"grpc_tools.protoc 생성 실패(exit={exit_code}) — args={args}")


def _build_servicers(prediction_pb2, prediction_pb2_grpc, training_pb2, training_pb2_grpc):
    success_bytes = (_PREDICTION_TESTDATA_ROOT / "calculate_optimal_bid_response_success.binpb").read_bytes()

    class _PredictionServicer(prediction_pb2_grpc.BidPredictionServiceServicer):
        def CalculateOptimalBid(self, request, context):  # noqa: N802
            response = prediction_pb2.CalculateOptimalBidResponse()
            response.ParseFromString(success_bytes)
            return response

        def GetModelMetadata(self, request, context):  # noqa: N802
            raise NotImplementedError("smoke 는 CalculateOptimalBid 만 부른다")

    class _TrainingServicer(training_pb2_grpc.TrainingJobServiceServicer):
        def StartTraining(self, request, context):  # noqa: N802
            response = training_pb2.StartTrainingResponse()
            response.handle.job_id = "crosslang-smoke-job-0"
            response.handle.state = training_pb2.JOB_STATE_ACCEPTED
            return response

        def GetTrainingJob(self, request, context):  # noqa: N802
            raise NotImplementedError("smoke 는 StartTraining 만 부른다")

        def CancelTrainingJob(self, request, context):  # noqa: N802
            raise NotImplementedError("smoke 는 StartTraining 만 부른다")

    return _PredictionServicer(), _TrainingServicer()


def main(address: str) -> None:
    with tempfile.TemporaryDirectory(prefix="bidvector-crosslang-smoke-py-") as tmp:
        out_dir = Path(tmp)
        _generate_stub(out_dir)
        sys.path.insert(0, str(out_dir))

        from bidvector.ml.v1 import prediction_pb2, prediction_pb2_grpc, training_pb2, training_pb2_grpc

        prediction_servicer, training_servicer = _build_servicers(
            prediction_pb2, prediction_pb2_grpc, training_pb2, training_pb2_grpc
        )

        server = grpc.server(ThreadPoolExecutor(max_workers=4))
        prediction_pb2_grpc.add_BidPredictionServiceServicer_to_server(prediction_servicer, server)
        training_pb2_grpc.add_TrainingJobServiceServicer_to_server(training_servicer, server)
        server.add_insecure_port(address)
        server.start()
        print(f"READY {address}", flush=True)
        server.wait_for_termination()


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "127.0.0.1:50099")
