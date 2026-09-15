"""RED — `ml_engine.app.server.ServerConfig.from_env`(D-5E-7). 빈 env = 부팅 거부,
기본값 없음."""

from __future__ import annotations

from pathlib import Path

import pytest

from ml_engine.app.server import ConfigError, ServerConfig, _preload
from ml_engine.evaluation.policy import PolicyRejected as EvaluationPolicyRejected
from ml_engine.inference.policy import PolicyRejected as InferencePolicyRejected
from ml_engine.serving.policy import PolicyRejected as ServingPolicyRejected
from ml_engine.training.policy import PolicyRejected as TrainingPolicyRejected

# 위 네 `PolicyRejected` import 는 반드시 top-level 이어야 한다 — `ml_engine.app.server`
# 가 `ml_engine.training.policy` 를 전이 import 하는데, `tests/training/conftest.py`
# 의 `pytest_collection_finish`(collection 종료 직후 1회)가 `ml_engine.training.*` 를
# `sys.modules` 에서 지운다. test 함수 **안에서** 지역 import 하면 그 시점에 재-import
# 돼 `app.server` 모듈이 이미 참조 중인 클래스 객체와 다른 새 클래스 객체가 나와
# `isinstance` 가 조용히 항상 `False` 가 된다(5C-1 이 처음 발견한 함정, `tests/app`
# 도 `app` 이 training 을 전이 import 하니 같은 함정에 걸린다). top-level import 는
# collection 시점(그 hook 이 돌기 전)에 한 번 묶여 같은 객체를 공유한다.

_FULL_ENV = {
    "ML_ENGINE_BIND": "127.0.0.1:50051",
    "ML_ENGINE_INFERENCE_POLICY": "/tmp/inference-v1.yaml",
    "ML_ENGINE_TRAINING_POLICY": "/tmp/training-v1.yaml",
    "ML_ENGINE_EVALUATION_POLICY": "/tmp/evaluation-v1.yaml",
    "ML_ENGINE_SERVING_POLICY": "/tmp/serving-v1.yaml",
    "ML_ENGINE_ARTIFACT_OUT_DIR": "/tmp/artifacts",
    "ML_ENGINE_CODE_VERSION": "sha-test",
}


def test_from_env_with_full_env_succeeds() -> None:
    config = ServerConfig.from_env(_FULL_ENV)
    assert config.bind == "127.0.0.1:50051"
    assert str(config.inference_policy_path) == "/tmp/inference-v1.yaml"
    assert config.code_version == "sha-test"


@pytest.mark.parametrize("missing_key", list(_FULL_ENV))
def test_from_env_missing_any_key_is_rejected(missing_key: str) -> None:
    env = dict(_FULL_ENV)
    del env[missing_key]
    with pytest.raises(ConfigError):
        ServerConfig.from_env(env)


def test_from_env_empty_string_value_is_rejected() -> None:
    """빈 env = 부팅 거부 — 값이 키로 존재해도 빈 문자열이면 미설정과 같다."""
    env = dict(_FULL_ENV)
    env["ML_ENGINE_BIND"] = ""
    with pytest.raises(ConfigError):
        ServerConfig.from_env(env)


def test_from_env_defaults_to_empty_mapping_when_none_given(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("ML_ENGINE_BIND", raising=False)
    with pytest.raises(ConfigError):
        ServerConfig.from_env({})


# ---- M-3(verifier r1) — SIGTERM 이 gRPC 서버뿐 아니라 JobRunner 도 닫는다 ----


def test_graceful_shutdown_sequence_stops_grpc_before_cancelling_jobs(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """순서: `serving_shutdown`(readiness NOT_READY → server.stop) → `runner.
    cancel_all()` → `runner.shutdown(wait=False)`. `JobRunner.shutdown`이 production
    호출자가 0이었던 것(verifier M-3)의 회귀 test."""
    from ml_engine.app import server as server_module

    calls: list[str] = []

    def fake_serving_shutdown(
        gate: object, server: object, *, grace_seconds: float
    ) -> None:
        calls.append(f"serving_shutdown(grace={grace_seconds})")

    monkeypatch.setattr(server_module, "serving_shutdown", fake_serving_shutdown)

    class _FakeRunner:
        def cancel_all(self) -> None:
            calls.append("cancel_all")

        def shutdown(self, *, wait: bool) -> None:
            calls.append(f"shutdown(wait={wait})")

    server_module._graceful_shutdown_sequence(
        gate=object(),  # type: ignore[arg-type]
        server=object(),  # type: ignore[arg-type]
        runner=_FakeRunner(),  # type: ignore[arg-type]
        grace_seconds=7.0,
    )

    assert calls == [
        "serving_shutdown(grace=7.0)",
        "cancel_all",
        "shutdown(wait=False)",
    ]


# ---- H-1(verifier r2 잔존) — `_preload`가 정책 넷 중 하나라도 문법 오류에서
# 처리되지 않은 예외로 죽지 않는다 ----


_MALFORMED_YAML = "scenario.z: [unclosed\n"

_REJECTED_TYPE_BY_ENV_KEY = {
    "ML_ENGINE_INFERENCE_POLICY": "inference",
    "ML_ENGINE_TRAINING_POLICY": "training",
    "ML_ENGINE_EVALUATION_POLICY": "evaluation",
    "ML_ENGINE_SERVING_POLICY": "serving",
}


@pytest.mark.parametrize("policy_env_key", list(_REJECTED_TYPE_BY_ENV_KEY))
def test_preload_malformed_yaml_in_any_of_four_policies_does_not_raise(
    tmp_path: Path, policy_env_key: str
) -> None:
    """verifier r2 H-1(잔존) — `load_serving_policy`는 r1 에서 고쳐졌지만 같은
    `_preload`가 `load_inference_policy`(범위 밖, `inference/policy.py`)를 무방비로
    불러 문법 깨진 inference YAML 에서 `yaml.parser.ParserError`가 그대로 새 나가
    `app.server.run()`이 처리되지 않은 예외로 죽었다. training·evaluation·serving
    세 로더는 이미 `yaml.YAMLError`를 잡는다 — 이 test 는 정책 넷 전부를 한 번에
    회귀로 지킨다(호출부 `_preload`에서 inference 만 별도로 정규화, 로더 자체는
    범위 밖이라 고치지 않는다)."""
    rejected_type = {
        "inference": InferencePolicyRejected,
        "training": TrainingPolicyRejected,
        "evaluation": EvaluationPolicyRejected,
        "serving": ServingPolicyRejected,
    }[_REJECTED_TYPE_BY_ENV_KEY[policy_env_key]]

    malformed = tmp_path / "malformed.yaml"
    malformed.write_text(_MALFORMED_YAML)
    env = dict(_FULL_ENV)
    env[policy_env_key] = str(malformed)
    config = ServerConfig.from_env(env)

    preloaded = _preload(config)  # 예외를 던지면 이 줄에서 실패한다(비교 전에)

    result = {
        "inference": preloaded.inference,
        "training": preloaded.training,
        "evaluation": preloaded.evaluation,
        "serving": preloaded.serving,
    }[_REJECTED_TYPE_BY_ENV_KEY[policy_env_key]]
    assert isinstance(result, rejected_type)
