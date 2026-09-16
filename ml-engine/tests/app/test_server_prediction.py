"""S-12b 자동화(scope.md ⑧, 5E-1 알려진 제한 9 해소). 실 TCP socket 위에서
`ml_engine.app.server`가 조립한 서버에 실 gRPC client 로 접속해 두 경로를 확인한다:

  - 출하 정책(`policy/inference-v1.yaml` 그대로) → READY + `promoted` →
    `CalculateOptimalBid` Success → `promoted == success.release`(id·checksum),
    후보율 전부 (0, 1] 안(M5/5F-1 값 변경 이후 — D-5F1-3 반전, 이전엔 `assessment.
    agency_sample_threshold` 미선언으로 이 경로가 NOT_READY 였다, `OPEN-5D2-
    POLICY-VALUES`).
  - (N-2) 정책 넷 중 하나만 깨져도 두 RPC 가 동시에 NOT_READY.

`server.run()`(SIGTERM 핸들러 등록, main thread 전용)을 직접 부르지 않는다 — 이
test 는 `app.server`의 조립 조각(`_preload`·`_preload_outcomes`·`_build_servicers`)과
`serving.build_server`를 그대로 이어붙여 실 socket 을 연다(`test_server.py`가 이미
같은 비공개 이름을 참조하는 관례를 따른다)."""

from __future__ import annotations

from decimal import Decimal
from pathlib import Path

import grpc
import pytest
import yaml

from ml_engine.app.server import (
    ServerConfig,
    ServingPolicy,
    _build_servicers,
    _prediction_runtime,
    _preload,
    _preload_outcomes,
)
from ml_engine.contracts import error_pb2, prediction_pb2, prediction_pb2_grpc
from ml_engine.serving import (
    BidPredictionServicer,
    Readiness,
    ReadinessGate,
    build_server,
)

_REPO_ROOT = Path(__file__).resolve().parents[3]
_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_SHIPPED_INFERENCE_POLICY = _ML_ENGINE_ROOT / "policy" / "inference-v1.yaml"
_TRAINING_POLICY = _ML_ENGINE_ROOT / "policy" / "training-v1.yaml"
_EVALUATION_POLICY = _ML_ENGINE_ROOT / "policy" / "evaluation-v1.yaml"
_SERVING_POLICY = _ML_ENGINE_ROOT / "policy" / "serving-v1.yaml"
_TESTDATA_REQUEST = (
    _REPO_ROOT
    / "contracts"
    / "testdata"
    / "prediction"
    / "calculate_optimal_bid_request.binpb"
)

# `_policy_support.py`(tests/inference)와 같은 placeholder — `OPEN-5D2-POLICY-VALUES`
# 값 결정이 아니다. tests/app 은 tests/inference 의 비공개 helper 를 import 하지 않는다
# (디렉터리 경계를 넘지 않는다는 관례, tests/serving/test_kotlin_rules_parity.py 와 동일).
_AGENCY_SAMPLE_THRESHOLD_PLACEHOLDER = 1
# testdata 의 유일한 완비 competition_sample(예비가격 추첨 관측 포함)을 복제해
# `reserve.min_reserve_records: 8`을 채운다 — 값을 새로 짓지 않는다(같은 authoritative
# 표본의 반복, test_kotlin_rules_parity.py 와 같은 근거).
_MIN_RESERVE_RECORDS_FOR_SUCCESS = 10


def _completed_case_inference_policy_path(tmp_path: Path) -> Path:
    raw_values = dict(
        yaml.safe_load(_SHIPPED_INFERENCE_POLICY.read_text(encoding="utf-8"))
    )
    raw_values.setdefault(
        "assessment.agency_sample_threshold", _AGENCY_SAMPLE_THRESHOLD_PLACEHOLDER
    )
    path = tmp_path / "inference-v1-completed-case.yaml"
    path.write_text(yaml.safe_dump(raw_values), encoding="utf-8")
    return path


def _config(*, inference_policy_path: Path, artifact_out_dir: Path) -> ServerConfig:
    return ServerConfig(
        bind="127.0.0.1:0",
        inference_policy_path=inference_policy_path,
        training_policy_path=_TRAINING_POLICY,
        evaluation_policy_path=_EVALUATION_POLICY,
        serving_policy_path=_SERVING_POLICY,
        artifact_out_dir=artifact_out_dir,
        code_version="sha-s12b-test",
    )


class _RunningServer:
    """`server.start()`~`server.stop()` 사이만 감싼다 — `server.run()`(SIGTERM 배선,
    main thread 전용)은 부르지 않는다(이 test 는 워커 스레드에서 돈다)."""

    def __init__(self, config: ServerConfig) -> None:
        preloaded = _preload(config)
        gate = ReadinessGate.from_preload(_preload_outcomes(preloaded))
        assert isinstance(preloaded.serving, ServingPolicy), (
            f"serving 정책 로드 실패(이 test 의 전제 위반): {preloaded.serving!r}"
        )
        servicers, runner = _build_servicers(gate, preloaded.serving, preloaded, config)
        server = build_server(preloaded.serving, servicers)
        port = server.add_insecure_port(config.bind)
        server.start()
        self.gate = gate
        self._server = server
        self._runner = runner
        self.channel = grpc.insecure_channel(f"127.0.0.1:{port}")
        self.stub = prediction_pb2_grpc.BidPredictionServiceStub(self.channel)

    def close(self) -> None:
        self.channel.close()
        self._server.stop(None).wait()
        self._runner.shutdown(wait=False)


def _metadata_request() -> prediction_pb2.GetModelMetadataRequest:
    request = prediction_pb2.GetModelMetadataRequest()
    request.envelope.request_id = "req-s12b-meta"
    request.envelope.correlation_id = "corr-s12b-meta"
    return request


def _success_calc_request() -> prediction_pb2.CalculateOptimalBidRequest:
    request = prediction_pb2.CalculateOptimalBidRequest()
    request.ParseFromString(_TESTDATA_REQUEST.read_bytes())
    request.envelope.base.request_id = "req-s12b-calc"
    request.envelope.base.correlation_id = "corr-s12b-calc"
    request.envelope.feature_schema_version = "award-rate-features-v2"
    request.envelope.model_release_selector.ClearField("exact_release")
    request.envelope.model_release_selector.latest_promoted.SetInParent()
    complete_sample = request.competition_samples[0]
    del request.competition_samples[:]
    for _ in range(_MIN_RESERVE_RECORDS_FOR_SUCCESS):
        sample = request.competition_samples.add()
        sample.CopyFrom(complete_sample)
    return request


# ---- (A/B 병합, M5/5F-1) 출하 정책 그대로 — READY, Success, promoted 일치 ----
#
# D-5F1-3 반전 — 5F-1 이전엔 출하 `policy/inference-v1.yaml`에
# `assessment.agency_sample_threshold`가 없어 이 경로가 영원히 NOT_READY 였다((B)
# 옛 test 가 그 사실을 고정했다). 5F-1 이 그 키(잠정값 10)와 `scenario.clamp_max
# 1.0`을 채운 뒤로는 **출하 정책 그대로 gate 가 READY 다** — 값 변경만으로 여기까지
# 온 것이 이 slice 의 전제(scope.md ④)라, 이 test 가 반대 방향(값이 빠지면 다시
# NOT_READY)으로 그 사실을 고정한다. (A)(완성 case 정책 — `_completed_case_
# inference_policy_path`로 temp 사본을 읽던 test)는 이제 출하 정책과 결과가
# 동일해져 중복이므로 이 test 하나로 합친다(판단 등재, `_completed_case_
# inference_policy_path` 자체는 아래 N-2 test 가 "정상 정책 하나" 자리로 계속 쓴다).


def test_shipped_policy_serves_success_matching_promoted(tmp_path: Path) -> None:
    """5F-1 값 변경(clamp_max 1.0·agency_sample_threshold 10) 이후 출하 정책
    그대로 READY + `promoted` + `CalculateOptimalBid` Success 이고
    `promoted == success.release`(id·checksum)."""
    config = _config(
        inference_policy_path=_SHIPPED_INFERENCE_POLICY,
        artifact_out_dir=tmp_path / "artifacts-shipped",
    )
    running = _RunningServer(config)
    try:
        assert running.gate.snapshot().state is Readiness.READY

        metadata_response = running.stub.GetModelMetadata(_metadata_request())
        assert metadata_response.WhichOneof("result") == "metadata"
        assert metadata_response.metadata.readiness == prediction_pb2.READINESS_READY
        assert metadata_response.metadata.HasField("promoted")
        promoted = metadata_response.metadata.promoted

        calc_response = running.stub.CalculateOptimalBid(_success_calc_request())
        assert calc_response.WhichOneof("result") == "success"
        assert calc_response.success.release.release_id == promoted.release_id
        assert (
            calc_response.success.release.artifact_checksum
            == promoted.artifact_checksum
        )
        # scope.md ⑤ — 정책 clamp_max ≤ 1 이 출하 경로에서도 후보율 계약(D-2B-8)을
        # 지킨다: Success 로 나온 이상 세 후보 전부 (0, 1] 안이다(wire 층
        # `_to_rate_or_none`이 그 밖을 이미 `MappingRejected`로 막는다).
        for candidate in calc_response.success.candidates:
            rate = Decimal(candidate.bid_rate.fraction)
            assert Decimal("0") < rate <= Decimal("1")
    finally:
        running.close()


# ---- (N-2, verifier r2) 정책 넷 중 하나만 깨져도 조립 근 가드가 gate 와 같이 떨어진다 ----


class _ActiveContext:
    def is_active(self) -> bool:
        return True


_MALFORMED_YAML = "scenario.z: [unclosed\n"


@pytest.mark.parametrize(
    "broken_policy", ["inference", "training", "evaluation", "serving"]
)
def test_single_broken_policy_makes_runtime_none_and_both_rpcs_not_ready(
    tmp_path: Path, broken_policy: str
) -> None:
    """verifier r2 N-2 — `app/server.py::_prediction_runtime`의 `_preload_outcomes`
    가드(R-H1) 자체는 r1 라운드에 test 가 없었다(변이로 가드를 지워도 930 전부
    초록이었다 — `_validate`의 gate 확인이 사용자 가시 거동을 이미 가리기 때문).
    정책 넷을 하나씩만 깨뜨려 그 가드가 실제로 `None`을 내는지, 그리고 두 RPC
    (`GetModelMetadata`·`CalculateOptimalBid`)가 동시에 미준비로 답하는지 직접
    확인한다(실 socket 이 아니라 servicer 직접 호출 — 이 test 의 관심은 배선
    로직이지 wire 형식이 아니다)."""
    broken_path = tmp_path / f"{broken_policy}-broken.yaml"
    broken_path.write_text(_MALFORMED_YAML)
    paths = {
        "inference": _completed_case_inference_policy_path(tmp_path),
        "training": _TRAINING_POLICY,
        "evaluation": _EVALUATION_POLICY,
        "serving": _SERVING_POLICY,
    }
    paths[broken_policy] = broken_path

    config = ServerConfig(
        bind="127.0.0.1:0",
        inference_policy_path=paths["inference"],
        training_policy_path=paths["training"],
        evaluation_policy_path=paths["evaluation"],
        serving_policy_path=paths["serving"],
        artifact_out_dir=tmp_path / "artifacts-n2",
        code_version="sha-n2-test",
    )
    preloaded = _preload(config)
    outcomes = _preload_outcomes(preloaded)
    assert sum(1 for outcome in outcomes if not outcome.ok) == 1

    gate = ReadinessGate.from_preload(outcomes)
    assert gate.snapshot().state is Readiness.NOT_READY

    runtime = _prediction_runtime(preloaded, config)
    assert runtime is None

    servicer = BidPredictionServicer(gate, ("award-rate-features-v2",), runtime)

    metadata_response = servicer.GetModelMetadata(_metadata_request(), context=None)
    assert metadata_response.metadata.readiness == prediction_pb2.READINESS_NOT_READY
    assert not metadata_response.metadata.HasField("promoted")

    calc_response = servicer.CalculateOptimalBid(
        _success_calc_request(), _ActiveContext()
    )
    assert calc_response.WhichOneof("result") == "failure"
    assert calc_response.failure.code == error_pb2.FAILURE_CODE_MODEL_NOT_READY
    assert calc_response.failure.detail_code == "SERVER_NOT_READY"
