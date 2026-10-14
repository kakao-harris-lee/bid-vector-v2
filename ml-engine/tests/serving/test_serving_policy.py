"""RED — `ml_engine.serving.policy`(D-5E-6). 출하 `policy/serving-v1.yaml` 로드 +
값 불변식 위반 다섯."""

from __future__ import annotations

from pathlib import Path

import pytest

from ml_engine.serving.policy import PolicyRejected, ServingPolicy, load_serving_policy

_SHIPPED_POLICY_PATH = (
    Path(__file__).resolve().parents[2] / "policy" / "serving-v1.yaml"
)

_VALID_YAML = """
version: serving-v1
max_workers: 8
max_concurrent_rpcs: 32
shutdown_grace_seconds: 10
job_workers: 1
pending_queue_max: 4
idempotency_key_max_chars: 256
embedding_text_max_chars: 4000
dataset_uri_schemes.0: file
"""


def _write(tmp_path: Path, text: str) -> Path:
    path = tmp_path / "serving.yaml"
    path.write_text(text)
    return path


def test_shipped_policy_loads() -> None:
    result = load_serving_policy(_SHIPPED_POLICY_PATH)
    assert isinstance(result, ServingPolicy)
    assert result.version == "serving-v1"
    assert result.dataset_uri_schemes == ("file",)


def test_shipped_pending_queue_max_is_the_approved_value(tmp_path: Path) -> None:
    """M6/6B-4 B-2 — 출하 값은 **4**다(`policy-values.md` §1, 잠정). 이 값이 바뀌면
    여기서 먼저 붉어진다 — 값을 바꾸려면 승인 표를 같이 고쳐야 한다는 뜻이다.
    `tests/training/test_jobs_store.py::test_shipped_policy_value_reaches_the_queue`
    가 이 값이 **실제 폐기 시점**을 정하는 것까지 잇는다."""
    result = load_serving_policy(_SHIPPED_POLICY_PATH)
    assert isinstance(result, ServingPolicy)
    assert result.pending_queue_max == 4


def test_valid_policy_round_trips(tmp_path: Path) -> None:
    result = load_serving_policy(_write(tmp_path, _VALID_YAML))
    assert isinstance(result, ServingPolicy)
    assert result.max_workers == 8
    assert result.max_concurrent_rpcs == 32
    assert result.shutdown_grace_seconds == 10
    assert result.job_workers == 1
    assert result.pending_queue_max == 4
    assert result.idempotency_key_max_chars == 256
    assert result.embedding_text_max_chars == 4000


@pytest.mark.parametrize(
    "override",
    [
        "max_workers: 0",
        "max_concurrent_rpcs: 0",
        "shutdown_grace_seconds: -1",
        "job_workers: 0",
        "pending_queue_max: 0",
        "pending_queue_max: -1",
        "idempotency_key_max_chars: 0",
        "embedding_text_max_chars: 0",
    ],
)
def test_invalid_scalar_values_are_rejected(tmp_path: Path, override: str) -> None:
    key = override.split(":", 1)[0]
    lines = [
        line for line in _VALID_YAML.strip().splitlines() if not line.startswith(key)
    ]
    lines.append(override)
    result = load_serving_policy(_write(tmp_path, "\n".join(lines)))
    assert isinstance(result, PolicyRejected)


def test_missing_dataset_uri_schemes_is_rejected(tmp_path: Path) -> None:
    lines = [
        line
        for line in _VALID_YAML.strip().splitlines()
        if not line.startswith("dataset_uri_schemes")
    ]
    result = load_serving_policy(_write(tmp_path, "\n".join(lines)))
    assert isinstance(result, PolicyRejected)


def test_uppercase_scheme_is_rejected(tmp_path: Path) -> None:
    lines = [
        line
        for line in _VALID_YAML.strip().splitlines()
        if not line.startswith("dataset_uri_schemes")
    ]
    lines.append("dataset_uri_schemes.0: FILE")
    result = load_serving_policy(_write(tmp_path, "\n".join(lines)))
    assert isinstance(result, PolicyRejected)


def test_duplicate_schemes_are_rejected(tmp_path: Path) -> None:
    lines = [
        line
        for line in _VALID_YAML.strip().splitlines()
        if not line.startswith("dataset_uri_schemes")
    ]
    lines.append("dataset_uri_schemes.0: file")
    lines.append("dataset_uri_schemes.1: file")
    result = load_serving_policy(_write(tmp_path, "\n".join(lines)))
    assert isinstance(result, PolicyRejected)


def test_unknown_key_is_rejected(tmp_path: Path) -> None:
    text = _VALID_YAML + "\nunknown_key: 1\n"
    result = load_serving_policy(_write(tmp_path, text))
    assert isinstance(result, PolicyRejected)


@pytest.mark.parametrize("missing_key", ["job_workers", "pending_queue_max"])
def test_missing_required_key_is_rejected(tmp_path: Path, missing_key: str) -> None:
    lines = [
        line
        for line in _VALID_YAML.strip().splitlines()
        if not line.startswith(missing_key)
    ]
    result = load_serving_policy(_write(tmp_path, "\n".join(lines)))
    assert isinstance(result, PolicyRejected)


def test_malformed_yaml_syntax_is_rejected_not_raised(tmp_path: Path) -> None:
    """H-1 — 문법이 깨진 YAML(닫히지 않은 flow sequence)은 `yaml.YAMLError`
    를 새지 않고 `PolicyRejected`여야 한다(세 번째 재발 — training/policy.py
    HIGH-2·evaluation/policy.py 가 이미 같은 구멍을 막았다)."""
    path = _write(tmp_path, "max_workers: [unclosed\n")
    result = load_serving_policy(path)
    assert isinstance(result, PolicyRejected)


def test_app_server_boots_not_ready_not_crashes_on_malformed_serving_yaml(
    tmp_path: Path,
) -> None:
    """`load_serving_policy`가 정직하게 거부해야 `app.server.run()`이 처리되지 않은
    예외로 죽지 않고 `ConfigError`로 정직하게 부팅을 거부한다(scope ②)."""
    from ml_engine.app.server import ConfigError, ServerConfig, run

    malformed = _write(tmp_path, "max_workers: [unclosed\n")
    env = {
        "ML_ENGINE_BIND": "127.0.0.1:0",
        "ML_ENGINE_INFERENCE_POLICY": str(tmp_path / "does-not-matter.yaml"),
        "ML_ENGINE_TRAINING_POLICY": str(tmp_path / "does-not-matter.yaml"),
        "ML_ENGINE_EVALUATION_POLICY": str(tmp_path / "does-not-matter.yaml"),
        "ML_ENGINE_SERVING_POLICY": str(malformed),
        "ML_ENGINE_ARTIFACT_OUT_DIR": str(tmp_path / "artifacts"),
        "ML_ENGINE_CODE_VERSION": "sha-test",
    }
    config = ServerConfig.from_env(env)
    with pytest.raises(ConfigError):
        run(config)
