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


# ---- M-3 — SIGTERM 이 gRPC 서버뿐 아니라 JobRunner 도 닫는다 ----


def test_graceful_shutdown_sequence_stops_grpc_before_cancelling_jobs(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    """순서: `serving_shutdown`(readiness NOT_READY → server.stop) → `runner.
    cancel_all()` → `runner.shutdown(wait=False)`. `JobRunner.shutdown`이 production
    호출자가 0이었던 것(M-3)의 회귀 test."""
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


# ---- H-1(잔존) — `_preload`가 정책 넷 중 하나라도 문법 오류에서
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
    """H-1(잔존) — `load_serving_policy`는 이미 고쳐졌지만 같은
    `_preload`가 `load_inference_policy`(범위 밖, `inference/policy.py`)를 무방비로
    불러 문법 깨진 inference YAML 에서 `yaml.parser.ParserError`가 그대로 새 나가면
    `app.server.run()`이 처리되지 않은 예외로 죽는다. training·evaluation·serving
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


# ---- v H-2 — **env 가 하는 일은 승인된 산출물 사이에서 고르기**다 ----
#
# verifier 가 실측한 구멍: 완화한 정책 사본을 코드가 만들어 쓰거나
# `ML_ENGINE_EVALUATION_POLICY` 로 가리키면 전 게이트가 초록이었다. 승인을 묻는 자리는
# **운영자의 선택이 들어오는 자리**, 곧 이 부팅이다. 아래 셋이 그 자리의 세 갈래를 잰다 —
# 이름을 유지한 편집 사본 · 이름을 바꿔 단 사본 · 무편집 사본(음성 대조).

_SHIPPED_EVALUATION_POLICY = (
    Path(__file__).resolve().parents[2] / "policy" / "evaluation-v1.yaml"
)


def _env_pointing_at(evaluation_policy: Path) -> dict[str, str]:
    env = dict(_FULL_ENV)
    env["ML_ENGINE_EVALUATION_POLICY"] = str(evaluation_policy)
    return env


def _relaxed(text: str) -> str:
    relaxed = text.replace("paired_t_threshold: 2.58", "paired_t_threshold: 0.1")
    assert relaxed != text, "완화 치환이 아무것도 바꾸지 못했다"
    return relaxed


def test_preload_rejects_an_edited_copy_that_keeps_the_approved_version(
    tmp_path: Path,
) -> None:
    """verifier 가 실측한 그 경로 — 임계만 바꾸고 이름은 `evaluation-v1` 로 남긴 사본.
    내용 지문이 승인된 것과 다르므로 부팅이 받지 않는다."""
    edited = tmp_path / "evaluation-v1.yaml"
    edited.write_text(
        _relaxed(_SHIPPED_EVALUATION_POLICY.read_text(encoding="utf-8")),
        encoding="utf-8",
    )

    rejected = _preload(ServerConfig.from_env(_env_pointing_at(edited))).evaluation

    assert isinstance(rejected, EvaluationPolicyRejected), rejected
    assert "승인된 지문과 다르다" in rejected.detail


def test_preload_rejects_an_evaluation_policy_that_is_not_an_approved_artifact(
    tmp_path: Path,
) -> None:
    """이름을 바꿔 단 완화 사본 — 자기 파일과 값이 **같고**(출처 재검증을 지난다) 로더도
    지나지만, 승인 등록부에 없으므로 부팅이 받지 않는다."""
    renamed = tmp_path / "evaluation-v1-relaxed.yaml"
    renamed.write_text(
        _relaxed(
            _SHIPPED_EVALUATION_POLICY.read_text(encoding="utf-8").replace(
                "version: evaluation-v1", "version: evaluation-v1-relaxed"
            )
        ),
        encoding="utf-8",
    )

    rejected = _preload(ServerConfig.from_env(_env_pointing_at(renamed))).evaluation

    assert isinstance(rejected, EvaluationPolicyRejected), rejected
    assert "승인 등록부에 없는" in rejected.detail


def test_preload_accepts_an_unedited_approved_policy_from_any_path(
    tmp_path: Path,
) -> None:
    """**음성 대조** — 막는 것은 「다른 경로」가 아니라 「승인되지 않은 내용」이다.
    운영자가 승인된 산출물을 어디에 두고 가리키든 부팅은 통과한다."""
    copy = tmp_path / "somewhere-else.yaml"
    copy.write_text(
        _SHIPPED_EVALUATION_POLICY.read_text(encoding="utf-8"), encoding="utf-8"
    )

    loaded = _preload(ServerConfig.from_env(_env_pointing_at(copy))).evaluation

    assert not isinstance(loaded, EvaluationPolicyRejected), loaded
    assert loaded.version == "evaluation-v1"


# ---- v r3 H-3b = cr r3 H-1 — 추론 정책도 승인된 산출물이어야 한다 ----
#
# `inference-v1` 은 등록부에 있지만 **런타임에 그 등록부를 묻는 자리가 없었다**. 그래서
# `ML_ENGINE_INFERENCE_POLICY` 를 편집 사본으로 돌리면 코드 변경 0 으로 부팅이 통과했다
# (verifier probe1 S5·S6). `maturity.window_days` 는 `_maturities` → `build_weekly_maturity`
# → `plan_evaluation_windows` 로 흘러 **평가 창 선택**을, 따라서 승격 판정을 바꾼다.

_SHIPPED_INFERENCE_POLICY = (
    Path(__file__).resolve().parents[2] / "policy" / "inference-v1.yaml"
)


def _env_with_inference(inference_policy: Path) -> dict[str, str]:
    env = dict(_FULL_ENV)
    env["ML_ENGINE_INFERENCE_POLICY"] = str(inference_policy)
    return env


def _edited_inference_copy(path: Path, old: str, new: str) -> Path:
    text = _SHIPPED_INFERENCE_POLICY.read_text(encoding="utf-8")
    edited = text.replace(old, new)
    assert edited != text, f"치환이 아무것도 바꾸지 못했다: {old!r}"
    path.write_text(edited, encoding="utf-8")
    return path


@pytest.mark.parametrize(
    ("old", "new", "axis"),
    [
        # 평가 창 선택을 바꾼다 — 승격 판정 입력이다
        ("maturity.window_days: 7", "maturity.window_days: 1", "window_days"),
        # `OPEN-SET-10` 이 미결인 동안 미승인이어야 하는 칸을 켠다
        (
            'maturity.time_axis_substitute: "NOT_APPROVED"',
            'maturity.time_axis_substitute: "APPROVED"',
            "substitute",
        ),
    ],
)
def test_preload_rejects_an_edited_inference_policy(
    tmp_path: Path, old: str, new: str, axis: str
) -> None:
    """이름은 `inference-v1` 그대로다 — 거부를 만드는 것은 **내용 지문**이다."""
    edited = _edited_inference_copy(tmp_path / "inference-v1.yaml", old, new)

    rejected = _preload(ServerConfig.from_env(_env_with_inference(edited))).inference

    assert isinstance(rejected, InferencePolicyRejected), (axis, rejected)
    assert "승인된 지문과 다르다" in rejected.reason


def test_preload_rejects_an_inference_policy_under_an_unregistered_name(
    tmp_path: Path,
) -> None:
    edited = _edited_inference_copy(
        tmp_path / "inference-v1-tuned.yaml",
        "version: inference-v1",
        "version: inference-v1-tuned",
    )

    rejected = _preload(ServerConfig.from_env(_env_with_inference(edited))).inference

    assert isinstance(rejected, InferencePolicyRejected), rejected
    assert "승인 등록부에 없는" in rejected.reason


def test_preload_accepts_the_unedited_inference_policy_from_any_path(
    tmp_path: Path,
) -> None:
    """**음성 대조** — 무편집 사본은 경로가 달라도 통과한다."""
    copied = tmp_path / "elsewhere.yaml"
    copied.write_text(
        _SHIPPED_INFERENCE_POLICY.read_text(encoding="utf-8"), encoding="utf-8"
    )

    loaded = _preload(ServerConfig.from_env(_env_with_inference(copied))).inference

    assert not isinstance(loaded, InferencePolicyRejected), loaded
    assert loaded.version == "inference-v1"
