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
