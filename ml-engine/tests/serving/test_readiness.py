"""RED — `ml_engine.serving.readiness`(scope.md ②, 설계 검토 (1) 「정책 없이 못 뜸」)."""

from __future__ import annotations

import inspect
from collections.abc import Callable
from typing import Any

import pytest

from ml_engine.serving.readiness import PreloadOutcome, Readiness, ReadinessGate


def _required_parameter_count(attribute: Callable[..., Any]) -> int:
    """기본값 없는 매개변수 수 — 인자 없이 부를 수 있는지 판정한다."""
    parameters = inspect.signature(attribute).parameters.values()
    return sum(
        1
        for parameter in parameters
        if parameter.default is inspect.Parameter.empty
        and parameter.kind
        in (
            inspect.Parameter.POSITIONAL_ONLY,
            inspect.Parameter.POSITIONAL_OR_KEYWORD,
            inspect.Parameter.KEYWORD_ONLY,
        )
    )


def test_all_preload_success_is_ready() -> None:
    gate = ReadinessGate.from_preload(
        [PreloadOutcome(name="a", ok=True), PreloadOutcome(name="b", ok=True)]
    )
    snapshot = gate.snapshot()
    assert snapshot.state is Readiness.READY
    assert snapshot.reasons == ()


def test_one_preload_failure_is_not_ready_with_reason() -> None:
    gate = ReadinessGate.from_preload(
        [
            PreloadOutcome(name="inference", ok=True),
            PreloadOutcome(name="training", ok=False, reason="bad yaml"),
        ]
    )
    snapshot = gate.snapshot()
    assert snapshot.state is Readiness.NOT_READY
    assert any("training" in reason for reason in snapshot.reasons)


def test_preload_outcome_failure_requires_reason() -> None:
    with pytest.raises(ValueError):
        PreloadOutcome(name="x", ok=False)


def test_mark_ready_is_not_a_public_method() -> None:
    """M-5 — `mark_ready()`(인자 없이 READY 로 만드는 메서드)는 production
    호출자가 0이라 지웠다. 상태를 READY 로 만드는 유일한 경로는 `from_preload`뿐이다."""
    gate = ReadinessGate.from_preload([PreloadOutcome(name="x", ok=True)])
    assert gate.snapshot().state is Readiness.READY
    assert not hasattr(gate, "mark_ready")


def test_not_ready_is_terminal_within_the_process() -> None:
    """M6/6B-4 D-6B4-3 — **NOT_READY 는 이 프로세스의 종단이다.** 공개 표면 어느 것으로도
    READY 로 돌아가지 않고, READY 를 만드는 것은 기동 시점의 `from_preload` 하나뿐이다
    (M-5 가 `mark_ready()` 를 지운 사유: 「정책은 재로드하지 않는다 — 재시작만 preload 를
    다시 돈다」).

    **이 사실에 기대는 것**: 조립 근이 NOT_READY 로 뜬 서버에서 대기(ACCEPTED) job 을
    집지 않고 그대로 둔다(`tests/app/test_server_job_restart.py` 의 NOT_READY test).
    「영구 정체 금지」가 성립하는 근거는 **다음 기동**이 유일한 READY 경로라는 것이다 —
    프로세스 안에 READY 로 가는 길이 생기면 그 시점에 재적재하는 경로가 **필요해진다.**
    그래서 이 test 는 이름 하나(`mark_ready`)가 아니라 **공개 전이 집합**을 닫는다:
    다른 이름으로 되살려도(`reload_policies`·`retry_preload` 등) 여기가 붉어진다. 거동
    쪽은 **인자 없이 부를 수 있는 공개 메서드 전수**를 돌려 그중 어느 것도 NOT_READY 를
    READY 로 바꾸지 않음을 잰다(집합 등식이 「새 메서드가 몰래 생기는」 쪽을 막고, 이 루프가
    「이미 있는 것 중 하나가 바꾼다」 쪽을 막는다)."""
    public_surface = {name for name in dir(ReadinessGate) if not name.startswith("_")}
    assert public_surface == {
        "from_preload",  # 기동 1회 판정 — READY 를 만드는 유일한 자리(새 인스턴스)
        "snapshot",  # 읽기
        "mark_not_ready",  # READY → NOT_READY
        "begin_shutdown",  # READY|NOT_READY → NOT_READY(종료 순서의 첫 단계)
    }

    gate = ReadinessGate.from_preload(
        [PreloadOutcome(name="training", ok=False, reason="bad yaml")]
    )
    assert gate.snapshot().state is Readiness.NOT_READY

    needs_arguments: list[str] = []
    for name in sorted(public_surface):
        attribute = getattr(gate, name)
        if not callable(attribute):
            continue
        if _required_parameter_count(attribute):
            needs_arguments.append(name)
            continue
        attribute()
        assert gate.snapshot().state is Readiness.NOT_READY, name

    # 인자를 받는 것들은 손으로 부른다 — 그 목록도 단언해 새 이름이 조용히 끼지 않게 한다.
    assert needs_arguments == ["from_preload", "mark_not_ready"]
    gate.mark_not_ready("여전히 준비되지 않음")
    assert gate.snapshot().state is Readiness.NOT_READY
    assert (
        ReadinessGate.from_preload(
            [PreloadOutcome(name="training", ok=False, reason="bad yaml")]
        )
        .snapshot()
        .state
        is Readiness.NOT_READY
    )


def test_mark_not_ready_sets_reason() -> None:
    gate = ReadinessGate.from_preload([PreloadOutcome(name="x", ok=True)])
    gate.mark_not_ready("shutting down for maintenance")
    snapshot = gate.snapshot()
    assert snapshot.state is Readiness.NOT_READY
    assert snapshot.reasons == ("shutting down for maintenance",)


def test_begin_shutdown_sets_not_ready() -> None:
    gate = ReadinessGate.from_preload([PreloadOutcome(name="x", ok=True)])
    assert gate.snapshot().state is Readiness.READY
    gate.begin_shutdown()
    assert gate.snapshot().state is Readiness.NOT_READY
