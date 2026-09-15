"""`ml_engine.serving.readiness` — `Readiness` 상태 기계(scope.md ②, 설계 검토 (1)).
`LOADING → READY | NOT_READY`, 종료 시 `NOT_READY`가 `server.stop`보다 먼저(설계 검토 (1)
「종료 순서」). `serving`은 `training`·`inference`(간접은 허용, D-5E-1 예외 없음 — 실제로는
`inference`만 계층상 허용) 구체 정책 타입을 알지 못하므로(forbidden 계약), preload
결과는 `PreloadOutcome`(이름 + 성공 여부 + 사유)으로 **정규화해 받는다** — 조립 근
(`ml_engine.app`)이 각 정책 로더의 결과 타입을 이 형태로 바꿔 넘긴다.
"""

from __future__ import annotations

import threading
from collections.abc import Sequence
from dataclasses import dataclass
from enum import StrEnum


class Readiness(StrEnum):
    LOADING = "LOADING"
    READY = "READY"
    NOT_READY = "NOT_READY"


@dataclass(frozen=True)
class ReadinessSnapshot:
    state: Readiness
    reasons: tuple[str, ...]


@dataclass(frozen=True)
class PreloadOutcome:
    """정책 하나의 preload 결과 — 조립 근이 각 정책 로더의 결과 타입(`PolicyRejected`
    변종들, 모듈마다 다른 타입)을 이 공통 형태로 정규화한다."""

    name: str
    ok: bool
    reason: str | None = None

    def __post_init__(self) -> None:
        if not self.name:
            raise ValueError("name 은 비어 있을 수 없습니다.")
        if not self.ok and not self.reason:
            raise ValueError("실패한 preload 는 reason 이 있어야 합니다.")


class ReadinessGate:
    """정책 preload 결과로 상태를 정한다(설계 검토 (1) 「정책 없이 못 뜸」). 전이는
    `mark_ready`·`mark_not_ready`·`begin_shutdown`뿐 — 상태 직접 대입 경로가 없다
    ((2b) 표)."""

    def __init__(self, snapshot: ReadinessSnapshot) -> None:
        self._lock = threading.Lock()
        self._snapshot = snapshot

    @classmethod
    def from_preload(cls, outcomes: Sequence[PreloadOutcome]) -> ReadinessGate:
        """하나라도 실패하면 `NOT_READY(사유들)`, 전부 성공이면 `READY`. 조용히
        기본값으로 뜨지 않는다."""
        failed = tuple(
            f"{outcome.name}:{outcome.reason}" for outcome in outcomes if not outcome.ok
        )
        state = Readiness.NOT_READY if failed else Readiness.READY
        return cls(ReadinessSnapshot(state=state, reasons=failed))

    def snapshot(self) -> ReadinessSnapshot:
        with self._lock:
            return self._snapshot

    def mark_ready(self) -> None:
        with self._lock:
            self._snapshot = ReadinessSnapshot(state=Readiness.READY, reasons=())

    def mark_not_ready(self, reason: str) -> None:
        if not reason:
            raise ValueError("reason 은 비어 있을 수 없습니다.")
        with self._lock:
            self._snapshot = ReadinessSnapshot(
                state=Readiness.NOT_READY, reasons=(reason,)
            )

    def begin_shutdown(self) -> None:
        """종료 시퀀스의 첫 단계 — `server.stop`보다 먼저 호출돼야 한다(`serving.grpc`
        `shutdown()`이 순서를 강제한다)."""
        with self._lock:
            self._snapshot = ReadinessSnapshot(
                state=Readiness.NOT_READY, reasons=("SHUTTING_DOWN",)
            )
