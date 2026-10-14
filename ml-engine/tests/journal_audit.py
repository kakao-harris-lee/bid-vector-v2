"""저널 쓰기 감사 훅의 **술어와 기록**(M6/6B-4 vr H-2 · cr M-1).

설치는 `tests/conftest.py` 가 한다(감사 훅은 제거할 수 없어 세션에 한 번). 이 모듈을 따로
둔 이유는 이름의 유일성이다 — `import conftest` 는 `tests/training/conftest.py` 로도 해석돼
모호하다(실측: `ImportError: cannot import name … from 'conftest'`).
"""

from __future__ import annotations

import contextlib
import os
import sys
import sysconfig
from collections.abc import Iterator
from dataclasses import dataclass
from pathlib import Path
from types import FrameType

_ML_ENGINE_ROOT = Path(__file__).resolve().parents[1]

WHOLE_SESSION_AUDIT_TEST = "test_no_journal_write_violation_in_the_whole_session"

# ---- 저널 쓰기 감사 훅(M6/6B-4 vr H-2 · cr M-1) ----
#
# 「저널 파일에 쓰는 자리는 `journal_file` 모듈 하나」는 **정적 이름 목록으로 닫히지
# 않는다.** verifier r2 가 심은 여섯(builtin `open(…, "w")` · `Path.open("w")` · 비결합
# `Path.replace(a, b)` · `staged.replace(target=…)` · private `_write_all` 재사용 ·
# `subprocess.run(["mv", …])`)이 스물 넘는 이름을 든 집합 등식을 전부 지나갔다. 가장 흔한
# 쓰기 형태가 목록 밖에 남는 것이 이 축의 성질이다.
#
# 그래서 판정을 **실행 시점**으로 옮긴다. CPython 의 감사 훅은 철자와 무관하게 이벤트를
# 낸다(`open` 은 builtin·`io.open`·`Path.open`·`os.open` 전부, `os.rename` 은 `os.replace`·
# `Path.replace`·`Path.rename`·`shutil.move` 전부). 세션 동안 **저널 디렉터리를 겨눈 모든
# 쓰기 이벤트**의 호출 프레임을 보고, production 모듈 중 `journal_file` 이 아닌 자리에서
# 왔으면 위반으로 적는다.
#
# **무엇을 닫고 무엇을 못 닫는가**(정직하게): 닫는 것은 **이 세션이 실제로 실행한 경로**다.
# 못 닫는 것은 ① 다른 프로세스의 쓰기(`subprocess` — 훅은 `subprocess.Popen` 을 보지만 쓰기
# 자체는 자식이 한다. 그래서 argv 에 저널 경로가 실린 자식 생성은 **의도**로 적는다)
# ② 어떤 test 도 실행하지 않는 코드 경로. 정적 축은 조립 경계 게이트가 「좁힘」으로 지고,
# 「닫힘」을 주장하는 것은 이 훅뿐이다.

_JOURNAL_DIR_NAME = "_journal"
_AUDITED_EVENTS = frozenset(
    {
        "open",
        "os.rename",
        "os.replace",
        "os.remove",
        "os.unlink",
        "os.truncate",
        "os.link",
        "os.symlink",
        "os.mkdir",
        "shutil.move",
        "shutil.copyfile",
        "shutil.copytree",
        "shutil.rmtree",
        "subprocess.Popen",
    }
)
_JOURNAL_WRITER_MODULE = "src/ml_engine/training/jobs/journal_file.py"
_PRODUCTION_ROOT = str(_ML_ENGINE_ROOT / "src")
_TESTS_ROOT = str(_ML_ENGINE_ROOT / "tests")
_WRITE_MODE_CHARS = frozenset("wxa+")
_WRITE_FLAGS = os.O_WRONLY | os.O_RDWR | os.O_CREAT | os.O_TRUNC | os.O_APPEND


@dataclass(frozen=True)
class JournalWriteEvent:
    """저널 디렉터리를 겨눈 쓰기 이벤트 하나."""

    event: str
    path: str
    writer: str
    """호출 프레임 중 **표준 라이브러리 밖 첫 자리**(저장소 상대 경로) — 비면 미귀속."""


class JournalWriteAudit:
    """감사 훅의 기록. `violations` 는 production 모듈 중 저널 writer 가 아닌 자리에서 온
    쓰기다. `capture()` 안에서 일어난 위반은 전역 목록이 아니라 그 지역 목록으로 간다
    (우회를 심어 보는 test 가 세션 전체를 오염시키지 않게)."""

    def __init__(self) -> None:
        self.violations: list[JournalWriteEvent] = []
        self.allowed: list[JournalWriteEvent] = []
        self.from_tests: list[JournalWriteEvent] = []
        self.unattributed: list[JournalWriteEvent] = []
        self.subprocess_intents: list[JournalWriteEvent] = []
        self._captured: list[JournalWriteEvent] | None = None

    @contextlib.contextmanager
    def capture(self) -> Iterator[list[JournalWriteEvent]]:
        local: list[JournalWriteEvent] = []
        previous = self._captured
        self._captured = local
        try:
            yield local
        finally:
            self._captured = previous

    def _record_violation(self, found: JournalWriteEvent) -> None:
        if self._captured is None:
            self.violations.append(found)
        else:
            self._captured.append(found)

    def hook(self, event: str, args: tuple[object, ...]) -> None:
        if event not in _AUDITED_EVENTS:
            return
        try:
            self._classify(event, args)
        except (TypeError, ValueError, AttributeError, OSError, IndexError):
            # 훅에서 예외가 새면 **감사 대상 연산 자체가 깨진다**. 그래서 삼키되, 분류 실패를
            # 조용히 넘기지 않고 **위반으로** 적는다(`capture()` 와 무관하게 전역 목록으로 —
            # 분류가 깨진 이벤트를 통과시키면 게이트가 조용히 비어 버린다, fail-closed).
            # cr L-6 ③ — 앞 주석은 「미귀속으로 적는다」였고 코드와 어긋났다.
            self.violations.append(
                JournalWriteEvent(event=event, path="<분류 실패>", writer="")
            )

    def _classify(self, event: str, args: tuple[object, ...]) -> None:
        path = _journal_path_in(event, args)
        if path is None or not _is_write(event, args):
            return
        self.record(event, path, _writer_frame())

    def record(self, event: str, path: str, writer: str) -> JournalWriteEvent:
        """분류 — **writer 를 인자로 받는다**(훅이 프레임에서 계산한 값이든, test 가 준
        값이든 같은 판정이다. 그래서 술어가 단독으로 측정 가능하다).

        - `subprocess.Popen` → 자식이 쓰므로 귀속할 수 없다. argv 에 저널 경로가 실린
          사실만으로 **위반**이고 의도 목록에도 적는다.
        - production 모듈(`src/` 아래) → `journal_file` 이면 허용, 아니면 **위반**.
        - test 모듈(`tests/` 아래) → 대상 아님(깨진 저널을 심는 test 헬퍼가 있다).
        - 저장소 밖(동적으로 만든 모듈 등) → 미귀속. production writer 가 아니므로 위반이
          아니지만 따로 센다."""
        found = JournalWriteEvent(event=event, path=path, writer=writer)
        if event == "subprocess.Popen":
            self.subprocess_intents.append(found)
            self._record_violation(found)
            return found
        if writer == _JOURNAL_WRITER_MODULE:
            self.allowed.append(found)
            return found
        if writer.startswith("tests/"):
            self.from_tests.append(found)
            return found
        if not writer:
            self.unattributed.append(found)
            return found
        self._record_violation(found)
        return found


def _as_text(value: object) -> str | None:
    if isinstance(value, str):
        return value
    if isinstance(value, bytes):
        return value.decode("utf-8", "replace")
    if isinstance(value, os.PathLike):
        return os.fspath(value) if isinstance(os.fspath(value), str) else None
    return None


def _journal_path_in(event: str, args: tuple[object, ...]) -> str | None:
    """인자 중 저널 디렉터리 아래를 가리키는 첫 경로. `subprocess.Popen` 은 argv 를 본다."""
    candidates: list[object] = list(args)
    if (
        event == "subprocess.Popen"
        and len(args) > 1
        and isinstance(args[1], (list, tuple))
    ):
        candidates.extend(args[1])
    for candidate in candidates:
        text = _as_text(candidate)
        if text is not None and f"{_JOURNAL_DIR_NAME}/" in f"{text}/":
            return text
    return None


def _is_write(event: str, args: tuple[object, ...]) -> bool:
    """`open` 만 의도를 가른다(판독은 대상이 아니다). 나머지 이벤트는 전부 변경이다."""
    if event != "open":
        return True
    mode = args[1] if len(args) > 1 else None
    if isinstance(mode, str):
        return bool(_WRITE_MODE_CHARS & set(mode))
    flags = args[2] if len(args) > 2 else 0
    return isinstance(flags, int) and bool(flags & _WRITE_FLAGS)


def _writer_frame() -> str:
    """표준 라이브러리·이 파일 밖 **첫 프레임**의 저장소 상대 경로. `shutil.move` 처럼
    이벤트가 stdlib 안에서 발생해도 호출자를 가리킨다."""
    frame: FrameType | None = sys._getframe(1)  # 훅이 호출자를 알 유일한 수단
    while frame is not None:
        filename = frame.f_code.co_filename
        if not filename.startswith(_STDLIB_PREFIXES) and filename != __file__:
            if filename.startswith(_PRODUCTION_ROOT):
                return str(Path(filename).relative_to(_ML_ENGINE_ROOT))
            if filename.startswith(_TESTS_ROOT):
                return str(Path(filename).relative_to(_ML_ENGINE_ROOT))
            return ""
        frame = frame.f_back
    return ""


_STDLIB_PREFIXES = tuple(
    {
        path
        for path in (
            sysconfig.get_paths().get("stdlib"),
            sysconfig.get_paths().get("purelib"),
            sysconfig.get_paths().get("platlib"),
            str(Path(contextlib.__file__).parent),
        )
        if path
    }
)

JOURNAL_WRITE_AUDIT = JournalWriteAudit()
