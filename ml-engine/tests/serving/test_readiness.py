"""RED — `ml_engine.serving.readiness`(scope.md ②, 설계 검토 (1) 「정책 없이 못 뜸」)."""

from __future__ import annotations

import pytest

from ml_engine.serving.readiness import PreloadOutcome, Readiness, ReadinessGate


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


def test_mark_ready_transitions_to_ready() -> None:
    gate = ReadinessGate.from_preload([PreloadOutcome(name="x", ok=False, reason="r")])
    assert gate.snapshot().state is Readiness.NOT_READY
    gate.mark_ready()
    assert gate.snapshot().state is Readiness.READY
    assert gate.snapshot().reasons == ()


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
