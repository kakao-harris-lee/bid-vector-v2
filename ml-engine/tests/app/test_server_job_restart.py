"""RED — 조립 근의 job 저널 배선(M6/6B-4 Q-4·Q-5). `_build_servicers` 가 저널에서
저장소를 복원하고, 고아를 처분하고, 대기 job 을 실행 대기열에 **다시 넣는** 것을 실
출하 정책 위에서 확인한다.

`server.run()`(SIGTERM 배선, main thread 전용)은 부르지 않는다 — `test_server.py`·
`test_server_prediction.py` 와 같은 관례로 조립 조각만 이어붙인다.

import 은 전부 top-level 이어야 한다(`tests/training/conftest.py` 의
`pytest_collection_finish` 가 `ml_engine.training.*` 를 `sys.modules` 에서 지우므로,
함수 안에서 import 하면 `isinstance` 가 조용히 거짓이 된다 — `test_server.py` 머리
주석의 함정).
"""

from __future__ import annotations

import time
from datetime import UTC, datetime
from pathlib import Path

import pytest

from ml_engine.app.server import (
    _JOB_JOURNAL_DIR_NAME,
    ConfigError,
    ServerConfig,
    ServingPolicy,
    _build_servicers,
    _job_journal,
    _preload,
    _preload_outcomes,
)
from ml_engine.serving import ReadinessGate
from ml_engine.training.jobs import journal_file
from ml_engine.training.jobs.journal import JobSubmission, JournalSnapshot
from ml_engine.training.jobs.journal_file import JobJournal
from ml_engine.training.jobs.pipeline import DetailCode
from ml_engine.training.jobs.state import JobEvent, JobRecord, JobState
from ml_engine.training.jobs.store import JobStore, Started
from ml_engine.training.spec import TRAINING_SPECS

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_INFERENCE_POLICY = _ML_ENGINE_ROOT / "policy" / "inference-v1.yaml"
_TRAINING_POLICY = _ML_ENGINE_ROOT / "policy" / "training-v1.yaml"
_EVALUATION_POLICY = _ML_ENGINE_ROOT / "policy" / "evaluation-v1.yaml"
_SERVING_POLICY = _ML_ENGINE_ROOT / "policy" / "serving-v1.yaml"

_NOW = datetime(2026, 1, 1, tzinfo=UTC)
_KNOWN_SPEC_VERSION = next(iter(TRAINING_SPECS))


def _config(
    tmp_path: Path, *, training_policy_path: Path | None = None
) -> ServerConfig:
    return ServerConfig(
        bind="127.0.0.1:0",
        inference_policy_path=_INFERENCE_POLICY,
        training_policy_path=training_policy_path or _TRAINING_POLICY,
        evaluation_policy_path=_EVALUATION_POLICY,
        serving_policy_path=_SERVING_POLICY,
        artifact_out_dir=tmp_path / "artifacts",
        code_version="sha-6b4-test",
    )


def _submission(spec_version: str) -> JobSubmission:
    return JobSubmission(
        # 실제로 없는 dataset — 재적재가 **일어났는지**를 보려는 것이므로 파이프라인은
        # dataset 판독에서 정직하게 실패해도 된다(종단에 도달하는 것이 관측값이다).
        dataset_uri=f"file://{_ML_ENGINE_ROOT / 'does-not-exist'}",
        manifest_checksum="a" * 64,
        training_spec_version=spec_version,
    )


def _seed_pending_job(
    config: ServerConfig, *, spec_version: str, running: bool = False
) -> str:
    """첫 기동이 남긴 저널을 만든다(= 재기동 직전 상태). `running=True` 면 워커가 집은
    뒤 프로세스가 죽은 자리 — 그 전이도 **같은 store 인스턴스**로 해야 저널에 남는다."""
    store = JobStore(journal=_job_journal(config), pending_queue_max=4)
    outcome = store.start_or_reuse(
        "idem-restart",
        "ds-restart",
        accepted_at=_NOW,
        submission=_submission(spec_version),
    )
    assert isinstance(outcome, Started)
    if running:
        assert isinstance(
            store.apply_transition(outcome.record.job_id, JobEvent.START, at=_NOW),
            JobRecord,
        )
    return outcome.record.job_id


def _assemble(config: ServerConfig) -> tuple[ReadinessGate, JobStore]:
    preloaded = _preload(config)
    gate = ReadinessGate.from_preload(_preload_outcomes(preloaded))
    assert isinstance(preloaded.serving, ServingPolicy)
    servicers, runner = _build_servicers(gate, preloaded.serving, preloaded, config)
    store = servicers.training_job._store  # 조립된 실물을 그대로 본다
    runner.shutdown(wait=True)
    return gate, store


def _wait_for_terminal(store: JobStore, job_id: str, *, timeout: float = 20.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        record = store.get(job_id)
        assert record is not None
        if record.state in (JobState.SUCCEEDED, JobState.FAILED, JobState.CANCELLED):
            return
        time.sleep(0.02)
    raise AssertionError(f"job {job_id} 가 시간 안에 종단에 도달하지 못함")


# ---- 저널 자리(D-6B4-2) ----


def _journal_files(config: ServerConfig) -> list[str]:
    """산출물 루트 아래 **실제로 생긴 파일**을 센다. `JobJournal` 은 경로를 밖에 내주지
    않으므로(vr H-2) 자리를 물어보는 대신 **쓰고 나서 디스크를 본다** — 앞 판의
    `journal.path.parent.parent == artifact_out_dir` 보다 강한 관측이다(그 단언은 같은
    상수 둘을 맞춰 보는 것이었고, 이것은 거동을 본다)."""
    root = config.artifact_out_dir
    return sorted(
        path.relative_to(root).as_posix() for path in root.rglob("*") if path.is_file()
    )


def _journal_file_path(config: ServerConfig) -> Path:
    """test 가 **파일을 심을** 때만 쓰는 자리 계산 — production 상수 둘을 그대로 잇는다."""
    return config.artifact_out_dir / _JOB_JOURNAL_DIR_NAME / journal_file._FILE_NAME


def test_journal_lives_under_the_artifact_out_dir(tmp_path: Path) -> None:
    """저널은 **job 산출물 루트 안**에 있다 — 산출물과 job 기록이 같은 볼륨에서 함께
    산다(그러지 않으면 재기동 뒤 `artifact.uri` 가 없는 파일을 가리킨다). 디렉터리 이름이
    `_` 로 시작해 job 디렉터리(`uuid4`)와 충돌할 수 없다."""
    config = _config(tmp_path)
    _assemble(config)
    assert _journal_files(config) == [f"{_JOB_JOURNAL_DIR_NAME}/jobs.json"]
    assert _JOB_JOURNAL_DIR_NAME.startswith("_")


def test_boot_writes_the_journal_even_with_no_jobs(tmp_path: Path) -> None:
    """볼륨·소유자 잘못을 **첫 job 제출까지 미루지 않는다** — 기동이 그 자리에 쓴다."""
    config = _config(tmp_path)
    assert not config.artifact_out_dir.exists()  # 기동 전에는 자리가 없다
    _assemble(config)
    assert _journal_files(config) != []


def test_unreadable_journal_refuses_to_boot(tmp_path: Path) -> None:
    """위협 모델 ⑤ — 깨진 저널에서 조용히 빈 저장소로 시작하지 않는다."""
    config = _config(tmp_path)
    broken = _journal_file_path(config)
    broken.parent.mkdir(parents=True)
    broken.write_text("깨진 저널", encoding="utf-8")

    preloaded = _preload(config)
    gate = ReadinessGate.from_preload(_preload_outcomes(preloaded))
    assert isinstance(preloaded.serving, ServingPolicy)
    with pytest.raises(ConfigError):
        _build_servicers(gate, preloaded.serving, preloaded, config)


# ---- 재기동 처분과 재적재 ----


def test_running_orphan_is_disposed_on_boot(tmp_path: Path) -> None:
    config = _config(tmp_path)
    job_id = _seed_pending_job(config, spec_version=_KNOWN_SPEC_VERSION, running=True)

    _gate, store = _assemble(config)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert record.failure is not None
    assert record.failure.detail_code == DetailCode.RUNNER_LOST_ON_RESTART.value


def test_pending_job_is_resumed_and_reaches_a_terminal_state(tmp_path: Path) -> None:
    """ACCEPTED 고아는 **재적재된다** — 조립 근이 저널의 제출 입력으로 파이프라인을
    다시 만들어 넣고, 워커가 집어 종단까지 간다(여기서는 dataset 이 없어 정직하게
    실패한다 — 재적재가 일어났다는 관측값은 「종단에 도달함」이다)."""
    config = _config(tmp_path)
    job_id = _seed_pending_job(config, spec_version=_KNOWN_SPEC_VERSION)

    preloaded = _preload(config)
    gate = ReadinessGate.from_preload(_preload_outcomes(preloaded))
    assert isinstance(preloaded.serving, ServingPolicy)
    servicers, runner = _build_servicers(gate, preloaded.serving, preloaded, config)
    store = servicers.training_job._store
    try:
        _wait_for_terminal(store, job_id)
    finally:
        runner.shutdown(wait=True)

    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert record.failure is not None
    # 재기동 처분(RUNNER_LOST_ON_RESTART)이 아니라 **실행된 뒤의** 실패다.
    assert record.failure.detail_code != DetailCode.RUNNER_LOST_ON_RESTART.value
    assert record.started_at is not None


def test_pending_job_with_unknown_spec_version_is_disposed(tmp_path: Path) -> None:
    """설계 검토 우회 (7) — 이전 버전 저널의 대기 job 을 영원히 대기로 남기지 않는다."""
    config = _config(tmp_path)
    job_id = _seed_pending_job(config, spec_version="award-rate-gbm-training-v0-gone")

    _gate, store = _assemble(config)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert record.failure is not None
    assert (
        record.failure.detail_code
        == DetailCode.TRAINING_SPEC_UNSUPPORTED_ON_RESTART.value
    )
    # vr L-3 — 같은 spec 재제출은 `UNSUPPORTED_TRAINING_SPEC`(비재시도)로 거부된다.
    # 참을 실으면 거짓말이었다(cr M-5 의 코드별 사상이 거짓으로 고정한다).
    assert record.failure.retryable is False


def test_not_ready_server_leaves_pending_jobs_pending(tmp_path: Path) -> None:
    """정책 preload 가 깨졌으면 job 을 돌리지 않는다(새 제출을 `MODEL_NOT_READY` 로
    거부하는 것과 같은 규율) — 대기 job 은 ACCEPTED 로 남아 **다음 기동**이 집는다.

    D-6B4-3 「영구 정체 금지」가 성립하는 근거는 **다음 기동이 유일한 READY 경로**라는
    것이고, 그 사실 자체는
    `tests/serving/test_readiness.py::test_not_ready_is_terminal_within_the_process`
    가 공개 전이 집합으로 잠근다 — 프로세스 안에 READY 로 가는 길이 생기면 거기서 먼저
    붉어지고, 그때 「그 시점에 재적재」 경로가 필요해진다.

    대기 job 은 집히지 않는 동안에도 **상한 계수에 든다**(대기 수는 저장소의 ACCEPTED
    개수이고 readiness 와 무관하다) — 아래 `pending_jobs()` 단언이 그 상태를 보인다."""
    broken_policy = tmp_path / "training-broken.yaml"
    broken_policy.write_text("version: [unclosed\n", encoding="utf-8")
    config = _config(tmp_path, training_policy_path=broken_policy)
    job_id = _seed_pending_job(config, spec_version=_KNOWN_SPEC_VERSION)

    _gate, store = _assemble(config)
    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.ACCEPTED
    assert [job.record.job_id for job in store.pending_jobs()] == [job_id]

    on_disk = _job_journal(config).read()
    assert isinstance(on_disk, JournalSnapshot)
    assert [entry.record.state for entry in on_disk.entries] == [JobState.ACCEPTED]


def test_second_boot_disposes_nothing_more(tmp_path: Path) -> None:
    """수렴 — 한 번 처분한 뒤 재재기동은 아무것도 더 처분하지 않는다."""
    config = _config(tmp_path)
    job_id = _seed_pending_job(config, spec_version="award-rate-gbm-training-v0-gone")
    _assemble(config)
    _gate, store = _assemble(config)

    record = store.get(job_id)
    assert record is not None
    assert record.state is JobState.FAILED
    assert store.pending_jobs() == ()
    assert isinstance(_job_journal(config), JobJournal)
