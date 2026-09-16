"""`ml_engine.adapters.dataset_files` — dataset 파일 읽기(D-5C-8, scope ①). `file://`
경로만 지원한다 — 네트워크 0, `import-linter` forbidden 계약이 이 패키지에서 DB/HTTP
를 막는다(⑫는 `training`만 겨눈다 — `adapters`는 그 forbidden 밖이지만 이 모듈 자체가
파일시스템만 만진다). dataset 은 디렉터리 하나(`manifest.json` + `rows.jsonl`).
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from urllib.parse import urlparse


@dataclass(frozen=True)
class DatasetFiles:
    manifest_bytes: bytes
    rows_bytes: bytes
    settlements_bytes: bytes | None = None
    """M5/5E-1 D-5E-4 — `settlements.jsonl`(세 번째 파일, 선택적으로 읽는다: 있으면
    채우고 없으면 `None`). **파일 부재를 여기서 거부하지 않는다** —
    `tests/adapters/test_dataset_files.py`(out_of_scope, 무편집)의 기존 round-trip
    test 가 이 파일 없이 성공을 기대한다. "정산 관측 없이는 job 실패"라는 요구는
    이 파일이 아니라 `ml_engine.app.pipeline`(D-5E-4 를 실제로 강제하는 자리)이 진다."""


class DatasetUnreadableReason(StrEnum):
    UNSUPPORTED_SCHEME = "UNSUPPORTED_SCHEME"
    NOT_FOUND = "NOT_FOUND"
    NOT_A_DIRECTORY = "NOT_A_DIRECTORY"


@dataclass(frozen=True)
class DatasetUnreadable:
    reason: DatasetUnreadableReason
    detail: str


def read_dataset_files(uri: str) -> DatasetFiles | DatasetUnreadable:
    """`file://<dir>`의 `manifest.json`·`rows.jsonl` 두 파일을 읽는다. 다른 scheme 은
    fail-closed(D-5C-8 — scheme 확정은 `OPEN-2C-DATASET-URI-SCHEME`, 이 slice 는
    `file://`만). 경로 탈출(`..`)은 `resolve()` 뒤 scheme 검사만 한다 — 권한 검증은
    M6 소관(scope out_of_scope)."""
    parsed = urlparse(uri)
    if parsed.scheme != "file":
        return DatasetUnreadable(
            DatasetUnreadableReason.UNSUPPORTED_SCHEME, parsed.scheme
        )
    if parsed.netloc:
        # code-reviewer PR #13 MEDIUM-2 — `file://<host>/path`(비표준 file URI, host
        # 부분이 있음)는 host 를 조용히 무시하고 `path`만 읽어버릴 위험이 있다.
        # `file:///abs/path`(host 없음)만 허용한다 — 이 slice 는 `file://` 지원을
        # 이 형태로 문서화했다(D-5C-8).
        return DatasetUnreadable(
            DatasetUnreadableReason.UNSUPPORTED_SCHEME, f"file://{parsed.netloc}/…"
        )

    directory = Path(parsed.path).resolve()
    if not directory.exists():
        return DatasetUnreadable(DatasetUnreadableReason.NOT_FOUND, str(directory))
    if not directory.is_dir():
        return DatasetUnreadable(
            DatasetUnreadableReason.NOT_A_DIRECTORY, str(directory)
        )

    manifest_path = directory / "manifest.json"
    rows_path = directory / "rows.jsonl"
    if not manifest_path.is_file():
        return DatasetUnreadable(DatasetUnreadableReason.NOT_FOUND, str(manifest_path))
    if not rows_path.is_file():
        return DatasetUnreadable(DatasetUnreadableReason.NOT_FOUND, str(rows_path))

    # D-5E-4 — `settlements.jsonl`은 선택적으로 읽는다(있으면만). 「없으면 job 실패」
    # 강제는 `ml_engine.app.pipeline`의 몫이다(이 함수의 계약을 넓히지 않는다).
    settlements_path = directory / "settlements.jsonl"
    settlements_bytes = (
        settlements_path.read_bytes() if settlements_path.is_file() else None
    )

    return DatasetFiles(
        manifest_bytes=manifest_path.read_bytes(),
        rows_bytes=rows_path.read_bytes(),
        settlements_bytes=settlements_bytes,
    )
