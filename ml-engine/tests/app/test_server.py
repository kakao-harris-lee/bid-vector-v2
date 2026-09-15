"""RED — `ml_engine.app.server.ServerConfig.from_env`(D-5E-7). 빈 env = 부팅 거부,
기본값 없음."""

from __future__ import annotations

import pytest

from ml_engine.app.server import ConfigError, ServerConfig

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
