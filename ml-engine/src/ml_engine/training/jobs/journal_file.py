"""`ml_engine.training.jobs.journal_file` — 저널 **파일 하나**의 소유자(M6/6B-4 Q-4).

`journal.py` 와 나누는 선은 **파일 접근**이다: 그 모듈은 값 타입과 canonical JSON 코덱만
가지고(파일을 모른다), 이 모듈만 `os.open`·`os.replace`·`fsync` 를 부른다. 저널 파일에
쓰는 자리를 한 모듈에 가두는 것이 「둘째 writer」 방어의 구조적 근거다(vr H-2) — 경로는
밖에 내주지 않는다(`path` property 없음, 조립 근이 설정에서 주고 저장소가 들고만 있다).

원자 교체(tmp → `fsync` → `os.replace` → 디렉터리 `fsync`)로 갈아 끼운다 — 쓰기 도중
크래시는 「이전 상태 또는 새 상태」만 남긴다(위협 모델 ⑤).

**판독 실패는 조용한 빈 저장소가 아니다.** 「파일이 없다」(`JournalAbsent`, 첫 기동)와
「읽었지만 뜻을 모른다」(`JournalFailed` + 닫힌 사유)를 다른 타입으로 돌려준다 — 호출자
(`store.load_job_store` → 조립 근)가 후자에서 기동을 거부한다.
"""

from __future__ import annotations

import contextlib
import logging
import os
from pathlib import Path
from typing import Final

from ml_engine.training.jobs.journal import (
    JournalAbsent,
    JournalFailed,
    JournalFailureReason,
    JournalSnapshot,
    JournalWritten,
    decode_snapshot,
    encode_snapshot,
)

_logger = logging.getLogger(__name__)

_FILE_NAME: Final[str] = "jobs.json"
_TMP_SUFFIX: Final[str] = ".tmp"


class JobJournal:
    """저널 파일 하나를 소유한다. 경로는 **조립 근이 설정에서** 준다(설계 검토 (2b) —
    이 클래스를 조립 근 밖에서 만들지 않는다, `tests/gates` 가 정적으로 센다).

    **경로를 밖에 내주지 않는다**(vr H-2) — `path` property 가 있던 자리다. 경로 객체를
    얻을 수 있으면 어느 모듈이든 저장소 잠금 밖에서 그 파일에 쓸 수 있었다(둘째 writer).
    공개 표면은 `read`·`write` 둘이고 그 집합 등식을 게이트가 센다."""

    def __init__(self, directory: Path) -> None:
        self._directory = directory
        self._path = directory / _FILE_NAME

    def read(self) -> JournalSnapshot | JournalAbsent | JournalFailed:
        """「없음」·「깨짐」·스냅숏 셋으로 답한다. tmp 파일은 판독 대상이 아니다(원자
        교체의 중간물이라 이전 크래시의 부분 쓰기가 남아 있을 수 있다)."""
        try:
            payload = self._path.read_bytes()
        except FileNotFoundError:
            return JournalAbsent()
        except IsADirectoryError:
            return JournalFailed(
                JournalFailureReason.NOT_A_FILE, "저널 경로가 디렉터리다"
            )
        except OSError as exc:
            return JournalFailed(
                JournalFailureReason.READ_FAILED, f"저널을 읽지 못했다: {exc}"
            )
        return decode_snapshot(payload)

    def write(self, snapshot: JournalSnapshot) -> JournalWritten | JournalFailed:
        """원자 교체 — tmp 에 전부 쓰고 `fsync`, `os.replace` 로 갈아 끼운 뒤 디렉터리
        까지 `fsync`. 어느 단계에서 끊겨도 기존 파일은 온전하다. **`os.replace` 가
        경계다**(vr M-1): 그 앞에서 끊기면 아무것도 바뀌지 않았으므로 `JournalFailed`,
        그 뒤에서 끊기면 **이미 바뀌었으므로** `JournalWritten(durable=False)` 다."""
        payload = encode_snapshot(snapshot)
        if isinstance(payload, JournalFailed):
            return payload
        temporary = self._path.with_name(self._path.name + _TMP_SUFFIX)
        try:
            self._directory.mkdir(parents=True, exist_ok=True)
            _write_all(temporary, payload)
            os.replace(temporary, self._path)
        except OSError as exc:
            with contextlib.suppress(OSError):
                temporary.unlink()
            return JournalFailed(
                JournalFailureReason.WRITE_FAILED, f"저널을 쓰지 못했다: {exc}"
            )
        try:
            _fsync_directory(self._directory)
        except OSError as exc:
            _logger.error(
                "저널 교체는 일어났으나 디렉터리 내구화를 확인하지 못했다 — %s", exc
            )
            return JournalWritten(byte_count=len(payload), durable=False)
        return JournalWritten(byte_count=len(payload), durable=True)


def _write_all(path: Path, payload: bytes) -> None:
    descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    try:
        offset = 0
        while offset < len(payload):
            offset += os.write(descriptor, payload[offset:])
        os.fsync(descriptor)
    finally:
        os.close(descriptor)


def _fsync_directory(directory: Path) -> None:
    descriptor = os.open(directory, os.O_RDONLY)
    try:
        os.fsync(descriptor)
    finally:
        os.close(descriptor)
