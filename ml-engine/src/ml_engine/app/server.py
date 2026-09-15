"""`ml_engine.app.server` — 서버 진입점(D-5E-7). `python -m ml_engine.app.server`로
띄운다(`[project.scripts]` 없음 — `-m` 진입으로 충분, pyproject.toml (b)). 환경 6 개를
읽어 설정 객체를 만들고(기본값 없음 — 빈 env = 부팅 거부), 정책 넷을 preload 해
readiness 를 정하고, 세 servicer 를 등록한 뒤 serve 한다. `SIGTERM` 은 readiness
`NOT_READY` → `server.stop(grace)` 순서로 종료한다(설계 검토 (1))."""

from __future__ import annotations

import logging
import os
import signal
import sys
from collections.abc import Callable, Mapping
from dataclasses import dataclass
from pathlib import Path
from types import FrameType
from typing import NoReturn

from ml_engine.app.pipeline import pipeline_factory
from ml_engine.evaluation.policy import EvaluationPolicy, load_evaluation_policy
from ml_engine.evaluation.policy import PolicyRejected as EvaluationPolicyRejected
from ml_engine.features import SUPPORTED_FEATURE_SCHEMAS
from ml_engine.inference.policy import InferencePolicy, load_inference_policy
from ml_engine.inference.policy import PolicyRejected as InferencePolicyRejected
from ml_engine.serving import (
    BidPredictionServicer,
    EmbeddingServicer,
    PreloadOutcome,
    Readiness,
    ReadinessGate,
    Servicers,
    ServingPolicy,
    build_server,
    load_serving_policy,
)
from ml_engine.serving import PolicyRejected as ServingPolicyRejected
from ml_engine.serving import shutdown as serving_shutdown
from ml_engine.training.booster import LightGbmTrainer
from ml_engine.training.jobs.pipeline import TrainingPipeline
from ml_engine.training.jobs.runner import JobRunner
from ml_engine.training.jobs.servicer import TrainingJobServicer
from ml_engine.training.jobs.store import InMemoryJobStore
from ml_engine.training.policy import PolicyRejected as TrainingPolicyRejected
from ml_engine.training.policy import TrainingPolicy, load_training_policy
from ml_engine.training.spec import TrainingSpec
from ml_engine.training.train import CodeVersion

_logger = logging.getLogger(__name__)


class ConfigError(Exception):
    """환경 값 미설정 — 조용한 기본값 없음(D-5E-7)."""


@dataclass(frozen=True)
class ServerConfig:
    bind: str
    inference_policy_path: Path
    training_policy_path: Path
    evaluation_policy_path: Path
    serving_policy_path: Path
    artifact_out_dir: Path
    code_version: str

    @classmethod
    def from_env(cls, env: Mapping[str, str] | None = None) -> ServerConfig:
        source: Mapping[str, str] = env if env is not None else os.environ

        def required(name: str) -> str:
            value = source.get(name)
            if not value:
                raise ConfigError(f"환경 변수 {name} 가 설정돼야 합니다.")
            return value

        return cls(
            bind=required("ML_ENGINE_BIND"),
            inference_policy_path=Path(required("ML_ENGINE_INFERENCE_POLICY")),
            training_policy_path=Path(required("ML_ENGINE_TRAINING_POLICY")),
            evaluation_policy_path=Path(required("ML_ENGINE_EVALUATION_POLICY")),
            serving_policy_path=Path(required("ML_ENGINE_SERVING_POLICY")),
            artifact_out_dir=Path(required("ML_ENGINE_ARTIFACT_OUT_DIR")),
            code_version=required("ML_ENGINE_CODE_VERSION"),
        )


@dataclass(frozen=True)
class _Preloaded:
    inference: InferencePolicy | InferencePolicyRejected
    training: TrainingPolicy | TrainingPolicyRejected
    evaluation: EvaluationPolicy | EvaluationPolicyRejected
    serving: ServingPolicy | ServingPolicyRejected


def _preload(config: ServerConfig) -> _Preloaded:
    return _Preloaded(
        inference=load_inference_policy(config.inference_policy_path),
        training=load_training_policy(config.training_policy_path),
        evaluation=load_evaluation_policy(config.evaluation_policy_path),
        serving=load_serving_policy(config.serving_policy_path),
    )


def _outcome(name: str, ok: bool, reason: str | None) -> PreloadOutcome:
    return PreloadOutcome(name=name, ok=ok, reason=None if ok else reason)


def _preload_outcomes(preloaded: _Preloaded) -> tuple[PreloadOutcome, ...]:
    inference_ok = isinstance(preloaded.inference, InferencePolicy)
    training_ok = isinstance(preloaded.training, TrainingPolicy)
    evaluation_ok = isinstance(preloaded.evaluation, EvaluationPolicy)
    serving_ok = isinstance(preloaded.serving, ServingPolicy)
    return (
        _outcome(
            "inference",
            inference_ok,
            None if inference_ok else preloaded.inference.reason,  # type: ignore[union-attr]
        ),
        _outcome(
            "training",
            training_ok,
            None if training_ok else preloaded.training.detail,  # type: ignore[union-attr]
        ),
        _outcome(
            "evaluation",
            evaluation_ok,
            None if evaluation_ok else preloaded.evaluation.detail,  # type: ignore[union-attr]
        ),
        _outcome(
            "serving",
            serving_ok,
            None if serving_ok else preloaded.serving.reason,  # type: ignore[union-attr]
        ),
    )


def _is_ready_check(gate: ReadinessGate) -> bool:
    return gate.snapshot().state is Readiness.READY


def _unreachable_pipeline_factory(spec: TrainingSpec) -> NoReturn:
    """training·evaluation·inference 정책 중 하나라도 preload 에 실패했을 때 주입되는
    자리표시자. `TrainingJobServicer.is_ready`가 그 경우 `MODEL_NOT_READY`로 먼저
    거부하므로 이 함수는 **호출되지 않아야 한다** — 호출되면 그 자체가 gate 배선 결함
    이라는 뜻이라 조용한 기본값 대신 예외를 낸다."""
    raise AssertionError(
        "정책 preload 실패로 NOT_READY 여야 하는데 pipeline_factory 가 호출됐다."
    )


def _training_pipeline_factory(
    preloaded: _Preloaded, config: ServerConfig
) -> Callable[[TrainingSpec], TrainingPipeline]:
    if not (
        isinstance(preloaded.training, TrainingPolicy)
        and isinstance(preloaded.evaluation, EvaluationPolicy)
        and isinstance(preloaded.inference, InferencePolicy)
    ):
        return _unreachable_pipeline_factory
    return pipeline_factory(
        training_policy=preloaded.training,
        evaluation_policy=preloaded.evaluation,
        maturity_window_days=preloaded.inference.maturity_window_days,
        trainer=LightGbmTrainer(),
        code_version=CodeVersion(config.code_version),
        artifact_out_dir=config.artifact_out_dir,
    )


def _build_servicers(
    gate: ReadinessGate,
    serving_policy: ServingPolicy,
    preloaded: _Preloaded,
    config: ServerConfig,
) -> Servicers:
    store = InMemoryJobStore()
    runner = JobRunner(store, max_workers=serving_policy.job_workers)
    training_job_servicer = TrainingJobServicer(
        store=store,
        runner=runner,
        is_ready=lambda: _is_ready_check(gate),
        pipeline_factory=_training_pipeline_factory(preloaded, config),
        dataset_uri_schemes=frozenset(serving_policy.dataset_uri_schemes),
        idempotency_key_max_chars=serving_policy.idempotency_key_max_chars,
    )
    prediction_servicer = BidPredictionServicer(
        gate, tuple(SUPPORTED_FEATURE_SCHEMAS.keys())
    )
    embedding_servicer = EmbeddingServicer(
        gate, text_max_chars=serving_policy.embedding_text_max_chars
    )
    return Servicers(
        prediction=prediction_servicer,
        embedding=embedding_servicer,
        training_job=training_job_servicer,
    )


def run(config: ServerConfig) -> None:
    """preload → readiness → servicer 등록 → serve → `SIGTERM` → graceful shutdown.
    serving 정책 자체가 깨지면(예: `max_workers` 를 모른다) 서버를 세울 방법이 없어
    부팅을 거부한다(`ConfigError`) — 그 밖 셋(inference·training·evaluation)의
    실패는 서버는 뜨되 `NOT_READY`로 반영된다(scope.md ②)."""
    preloaded = _preload(config)
    gate = ReadinessGate.from_preload(_preload_outcomes(preloaded))
    if not isinstance(preloaded.serving, ServingPolicy):
        _logger.error("serving 정책 로드 실패: %s", preloaded.serving)
        raise ConfigError(f"serving 정책 로드 실패: {preloaded.serving}")
    serving_policy = preloaded.serving

    servicers = _build_servicers(gate, serving_policy, preloaded, config)
    server = build_server(serving_policy, servicers)
    server.add_insecure_port(config.bind)
    server.start()
    _logger.info(
        "ml-engine serving 시작 — bind=%s readiness=%s", config.bind, gate.snapshot()
    )

    def _handle_sigterm(signum: int, frame: FrameType | None) -> None:
        _logger.info("SIGTERM 수신 — graceful shutdown 시작")
        serving_shutdown(
            gate, server, grace_seconds=serving_policy.shutdown_grace_seconds
        )

    signal.signal(signal.SIGTERM, _handle_sigterm)
    server.wait_for_termination()


def main() -> int:
    logging.basicConfig(level=logging.INFO)
    try:
        config = ServerConfig.from_env()
    except ConfigError as exc:
        _logger.error("부팅 거부 — %s", exc)
        return 1
    run(config)
    return 0


if __name__ == "__main__":
    sys.exit(main())
