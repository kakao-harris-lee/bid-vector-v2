"""RED — 저널 파일에 **실제로 쓰는 자리**의 닫힘(M6/6B-4 vr H-2 · cr M-1).

조립 경계 게이트(`test_job_store_assembly_boundary.py`)의 정적 술어는 생성 자리·import·
경로 출처를 **좁힌다**. 그러나 「파일계를 바꾸는 호출 자리 집합 등식」은 이름 목록이었고,
verifier r2 가 심은 여섯이 전부 그 목록을 지나갔다 — builtin `open(…, "w")` · `Path.open("w")`
· 비결합 `Path.replace(a, b)` · `staged.replace(target=…)` · private `_write_all` 재사용 ·
`subprocess.run(["mv", …])`. 파이썬에서 파일을 쓰는 가장 흔한 형태가 목록 밖에 남는 것이
이 축의 성질이다(이름을 더하면 다음 이름이 남는다).

그래서 판정을 **실행 시점**으로 옮긴다. `sys.addaudithook`(conftest 가 세션 내내 설치)은
철자와 무관하게 CPython 이 내는 이벤트를 받는다 — `open` 하나가 builtin·`io.open`·
`Path.open`·`os.open` 을 전부 덮고, `os.rename` 하나가 `os.replace`·`Path.replace`·
`Path.rename`·`shutil.move` 를 전부 덮는다. 저널 디렉터리를 겨눈 쓰기 이벤트의 호출 프레임이
`journal_file` 이 아니면 위반이다.

**닫는 것**: 이 세션이 **실제로 실행한** 경로. 조립 근으로 띄운 서버의 제출·폐기·취소·
재기동·크래시 경로가 여기서 돌고, 그 동안의 모든 저널 쓰기가 한 모듈에서 왔음을 센다.

**못 닫는 것**(알려진 제한):
- **다른 프로세스의 쓰기** — `subprocess` 로 넘긴 쓰기는 자식이 한다. 훅은 `subprocess.Popen`
  의 argv 에 저널 경로가 실린 것을 **의도**로 잡지만(아래 test), 자식 안에서 경로를 조립하면
  보이지 않는다.
- **어떤 test 도 실행하지 않는 코드 경로** — 실행 시점 판정의 성질이다. 그 축은 정적
  게이트가 「좁힘」으로 진다.
- 저널 파일을 **직접 편집하는 주체**는 애초에 비방어다(위협 모델).
"""

from __future__ import annotations

import importlib.util
import sys
import threading
import time
from datetime import UTC, datetime
from pathlib import Path

import pytest
from journal_audit import (  # type: ignore[import-not-found]
    JOURNAL_WRITE_AUDIT,
    JournalWriteAudit,
)

from ml_engine.app import server
from ml_engine.app.server import (
    _JOB_JOURNAL_DIR_NAME,
    ServerConfig,
    ServingPolicy,
    _build_servicers,
    _preload,
    _preload_outcomes,
)
from ml_engine.contracts import training_pb2
from ml_engine.serving import ReadinessGate
from ml_engine.training.jobs.state import JobState
from ml_engine.training.spec import TRAINING_SPECS

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[2]
_JOURNAL_WRITER_MODULE = "src/ml_engine/training/jobs/journal_file.py"
_KNOWN_SPEC_VERSION = next(iter(TRAINING_SPECS))


# ---- 훅이 실제로 켜져 있고 공허하지 않다 ----


def test_the_audit_hook_is_installed(
    journal_write_audit: JournalWriteAudit,
) -> None:
    """훅은 제거할 수 없으므로 설치 사실과 **기록 객체가 그 훅의 것**임을 센다."""
    assert sys.audit is not None
    assert journal_write_audit is JOURNAL_WRITE_AUDIT


@pytest.mark.parametrize(
    ("writer", "bucket"),
    [
        (_JOURNAL_WRITER_MODULE, "allowed"),
        ("src/ml_engine/app/server.py", "violations"),
        ("src/ml_engine/training/jobs/runner.py", "violations"),
        ("src/ml_engine/app/pipeline.py", "violations"),
        ("tests/training/test_jobs_journal.py", "from_tests"),
        ("", "unattributed"),
    ],
)
def test_the_classification_predicate_is_measurable(writer: str, bucket: str) -> None:
    """술어 자체를 단독으로 잰다 — writer 가 production 모듈이고 `journal_file` 이 아니면
    **위반**이다. 아래 실행 경로 test 는 이 술어를 실제 스택 위에서 돌린다."""
    audit = JournalWriteAudit()
    audit.record("open", "/tmp/x/_journal/jobs.json", writer)
    assert len(getattr(audit, bucket)) == 1
    others = {"allowed", "violations", "from_tests", "unattributed"} - {bucket}
    assert all(getattr(audit, name) == [] for name in others)


# ---- 실행 경로 — 조립 근으로 띄운 서버의 저널 쓰기는 전부 한 모듈에서 온다 ----


def _config(tmp_path: Path) -> ServerConfig:
    policy_dir = _ML_ENGINE_ROOT / "policy"
    return ServerConfig(
        bind="127.0.0.1:0",
        inference_policy_path=policy_dir / "inference-v1.yaml",
        training_policy_path=policy_dir / "training-v1.yaml",
        evaluation_policy_path=policy_dir / "evaluation-v1.yaml",
        serving_policy_path=policy_dir / "serving-v1.yaml",
        artifact_out_dir=tmp_path / "artifacts",
        code_version="sha-audit",
    )


class _BlockingPipeline:
    def __init__(self, release: threading.Event) -> None:
        self._release = release

    def run(self, dataset_ref: object, cancel_token: object) -> object:
        self._release.wait(timeout=20.0)
        raise AssertionError("이 test 는 파이프라인 결과를 쓰지 않는다")


def _request(key: str) -> training_pb2.StartTrainingRequest:
    request = training_pb2.StartTrainingRequest()
    request.envelope.request_id = f"req-{key}"
    request.envelope.correlation_id = f"corr-{key}"
    request.idempotency_key = key
    request.dataset.uri = f"file://{_ML_ENGINE_ROOT / 'does-not-exist'}"
    request.dataset.manifest_checksum = "a" * 64
    request.dataset.dataset_id = "ds-audit"
    request.training_spec_version = _KNOWN_SPEC_VERSION
    return request


def test_every_journal_write_on_the_live_paths_comes_from_the_journal_module(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    journal_write_audit: JournalWriteAudit,
) -> None:
    """조립 근으로 띄운 서버에서 **기동 쓰기 · 적재 · 상한 폐기 · 취소 · 재기동 처분**을
    실제로 돌리고, 그 동안 저널 디렉터리를 겨눈 쓰기가 전부 `journal_file` 에서 왔음을
    센다(공허하지 않음은 `allowed` 수로 보인다)."""
    release = threading.Event()
    monkeypatch.setattr(
        server,
        "pipeline_factory",
        lambda **_kwargs: lambda spec: _BlockingPipeline(release),
    )
    config = _config(tmp_path)
    before_allowed = len(journal_write_audit.allowed)
    before_violations = len(journal_write_audit.violations)

    preloaded = _preload(config)
    gate = ReadinessGate.from_preload(_preload_outcomes(preloaded))
    assert isinstance(preloaded.serving, ServingPolicy)
    limit = preloaded.serving.pending_queue_max
    servicers, runner = _build_servicers(gate, preloaded.serving, preloaded, config)
    store = servicers.training_job._store
    try:
        running = servicers.training_job.StartTraining(
            _request("audit-running"), context=None
        ).handle.job_id
        deadline = time.monotonic() + 10.0
        while time.monotonic() < deadline:
            record = store.get(running)
            assert record is not None
            if record.state is JobState.RUNNING:
                break
            time.sleep(0.01)

        waiting = [
            servicers.training_job.StartTraining(
                _request(f"audit-wait-{index}"), context=None
            ).handle.job_id
            for index in range(limit + 1)
        ]
        cancel = training_pb2.CancelTrainingJobRequest()
        cancel.envelope.request_id = "r"
        cancel.envelope.correlation_id = "c"
        cancel.job_id = waiting[-1]
        servicers.training_job.CancelTrainingJob(cancel, context=None)
    finally:
        release.set()
        runner.shutdown(wait=False)

    # 재기동 — 고아 처분과 재적재가 저널에 다시 쓴다.
    reloaded_servicers, reloaded_runner = _build_servicers(
        gate, preloaded.serving, preloaded, config
    )
    reloaded_runner.shutdown(wait=False)
    assert reloaded_servicers.training_job._store.get(running) is not None

    assert len(journal_write_audit.allowed) - before_allowed >= 5  # 공허하지 않다
    assert journal_write_audit.violations[before_violations:] == []
    assert {found.writer for found in journal_write_audit.allowed} == {
        _JOURNAL_WRITER_MODULE
    }


def test_no_journal_write_violation_in_the_whole_session(
    journal_write_audit: JournalWriteAudit,
) -> None:
    """세션 전체의 안전망 — 이 test 보다 **앞서** 돈 경로의 위반도 여기서 드러난다.
    (뒤에 도는 경로는 `pytest_sessionfinish` 가 세션 종료 코드로 잡는다.)"""
    assert journal_write_audit.violations == []


# ---- 표적 — verifier r2 의 여섯 갈래를 심어 훅이 잡는 것을 보인다 ----


_VARIANTS: dict[str, str] = {
    # F1 — builtin open
    "F1_builtin_open": """
def write(target, staged):
    with open(target, "w") as handle:
        handle.write("rogue")
""",
    # F2 — Path.open
    "F2_path_open": """
def write(target, staged):
    with target.open("w") as handle:
        handle.write("rogue")
""",
    # F3 — 비결합 Path.replace(위치 인자 둘)
    "F3_unbound_path_replace": """
from pathlib import Path


def write(target, staged):
    staged.write_bytes(b"rogue")
    Path.replace(staged, target)
""",
    # F4 — 키워드 target=
    "F4_keyword_target": """
def write(target, staged):
    staged.write_bytes(b"rogue")
    staged.replace(target=target)
""",
    # F5 — private writer 재사용(os.open + os.write)
    "F5_private_writer": """
import os


def write(target, staged):
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    try:
        os.write(descriptor, b"rogue")
    finally:
        os.close(descriptor)
""",
    # shutil 경유
    "F6_shutil_move": """
import shutil


def write(target, staged):
    staged.write_bytes(b"rogue")
    shutil.move(str(staged), str(target))
""",
}


def _load_variant(tmp_path: Path, name: str, source: str) -> object:
    """버릴 모듈 하나에 우회를 심어 import 한다 — production 트리를 건드리지 않는다."""
    path = tmp_path / f"{name}.py"
    path.write_text(source, encoding="utf-8")
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


@pytest.mark.parametrize(("name", "source"), sorted(_VARIANTS.items()))
def test_audit_hook_catches_each_second_writer_variant(
    tmp_path: Path,
    journal_write_audit: JournalWriteAudit,
    name: str,
    source: str,
) -> None:
    """정적 이름 목록이 전부 지나가게 했던 여섯 갈래 — 훅은 철자를 보지 않으므로 잡는다.

    심은 모듈은 `src/` 밖이라 production writer 로 분류되지 않는다. 그래서 분류 대신
    **이벤트가 포착되는지**(= 철자와 무관하게 CPython 이 알려 주는지)를 센다 — 그것이
    이 축의 닫힘 근거다. `writer` 가 production 모듈이면 위반으로 가는 것은 위 실행 경로
    test 가 보인다."""
    journal_dir = tmp_path / _JOB_JOURNAL_DIR_NAME
    journal_dir.mkdir()
    target = journal_dir / "jobs.json"
    staged = tmp_path / "staged"
    module = _load_variant(tmp_path, name, source)

    seen: list[str] = []
    probe = JournalWriteAudit()

    def _recording_hook(event: str, args: tuple[object, ...]) -> None:
        if event not in {"open", "os.rename", "shutil.move", "os.mkdir"}:
            return
        before = len(probe.unattributed) + len(probe.from_tests) + len(probe.violations)
        probe.hook(event, args)
        if (
            len(probe.unattributed) + len(probe.from_tests) + len(probe.violations)
            > before
        ):
            seen.append(event)

    sys.addaudithook(_recording_hook)
    module.write(target, staged)  # type: ignore[attr-defined]

    assert seen, f"{name}: 저널을 겨눈 쓰기 이벤트가 하나도 나오지 않았다"
    assert target.read_bytes()  # 통제군 — 실제로 썼다
    # 심은 모듈은 저장소 밖이라 production writer 가 아니다(미귀속). production 모듈에서
    # 같은 이벤트가 오면 위반이 되는 것은 위 술어 test 가 보인다.
    assert journal_write_audit.violations == []


def test_subprocess_is_a_known_limit_but_the_intent_is_visible(
    tmp_path: Path, journal_write_audit: JournalWriteAudit
) -> None:
    """F7 — `subprocess.run(["mv", staged, target])`. 쓰기는 **자식 프로세스**가 하므로
    훅이 그 `open`/`rename` 을 보지 못한다(알려진 제한). 보이는 것은 argv 에 저널 경로가
    실린 **자식 생성**이다 — 그것만으로도 위반으로 적는다."""
    journal_dir = tmp_path / _JOB_JOURNAL_DIR_NAME
    journal_dir.mkdir()
    target = journal_dir / "jobs.json"
    staged = tmp_path / "staged"
    staged.write_bytes(b"rogue")

    with journal_write_audit.capture() as captured:
        module = _load_variant(
            tmp_path,
            "F7_subprocess",
            """
import subprocess


def write(target, staged):
    subprocess.run(["mv", str(staged), str(target)], check=True)
""",
        )
        module.write(target, staged)  # type: ignore[attr-defined]

    assert target.read_bytes() == b"rogue"  # 자식이 실제로 썼다
    assert [found.event for found in captured] == ["subprocess.Popen"]
    assert _JOB_JOURNAL_DIR_NAME in captured[0].path
    assert journal_write_audit.subprocess_intents  # 의도로 적힌다
    assert journal_write_audit.violations == []  # 전역은 오염되지 않는다


def test_read_only_access_to_the_journal_is_not_a_write(tmp_path: Path) -> None:
    """과잉 금지 통제군 — 판독은 위반이 아니다(그렇지 않으면 게이트가 정상 코드를 막는다)."""
    journal_dir = tmp_path / _JOB_JOURNAL_DIR_NAME
    journal_dir.mkdir()
    target = journal_dir / "jobs.json"
    target.write_bytes(b"{}")

    audit = JournalWriteAudit()
    with audit.capture() as captured:
        audit.hook("open", (str(target), "r", 0))
        audit.hook("open", (str(target), "rb", 0))
        audit.hook("open", (str(target), None, 0))  # os.open(O_RDONLY)
    assert captured == []
    assert audit.allowed == []
    assert audit.from_tests == []


def test_paths_outside_a_journal_directory_are_not_watched(tmp_path: Path) -> None:
    """범위 통제군 — 저널 밖의 쓰기는 이 게이트의 대상이 아니다(산출물·스냅숏은 다른 축)."""
    audit = JournalWriteAudit()
    with audit.capture() as captured:
        audit.hook("open", (str(tmp_path / "artifact.bin"), "wb", 0))
        audit.hook("os.rename", (str(tmp_path / "a"), str(tmp_path / "b")))
    assert captured == []
    assert audit.allowed == []
    assert audit.from_tests == []


def test_timestamp_helper_is_unaffected() -> None:
    """훅이 세션 전역이라는 사실이 평범한 코드를 바꾸지 않는다는 최소 확인."""
    assert datetime.now(UTC).tzinfo is UTC
