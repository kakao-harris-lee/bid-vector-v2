"""`ml_engine.adapters.snapshot_files` — 6G 스냅숏 파일 읽기(D-6G-2). 5C
`dataset_files` 와 같은 갈래다: **이 모듈은 바이트만 만들고 뜻은 모른다** — 판독은
`ml_engine.evaluation.backtest.snapshot.load_snapshot` 이 한다.

`file://` 경로만 지원한다(네트워크 0). 스냅숏은 디렉터리 하나(`manifest.json` +
`rows.jsonl` + `sample-list.tsv`, v5)이고, **저장소 밖**에 둔다(`~/.local/bid-vector-snapshots/<id>/`,
data-extract §7 · ADR 0010 D-8) — 그래서 이 함수가 받는 경로는 저장소 안이 아니다.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from urllib.parse import urlparse


@dataclass(frozen=True)
class SnapshotFiles:
    manifest_bytes: bytes
    rows_bytes: bytes
    sample_list_bytes: bytes
    """v4(D-6G-39) — 표본 목록이 **파일**이 됐다. 판독이 이 바이트의 sha256 을
    manifest 와 대조하므로 다시 렌더링하지 않고 바이트 그대로 나른다."""


class SnapshotUnreadableReason(StrEnum):
    UNSUPPORTED_SCHEME = "UNSUPPORTED_SCHEME"
    NOT_FOUND = "NOT_FOUND"
    NOT_A_DIRECTORY = "NOT_A_DIRECTORY"


@dataclass(frozen=True)
class SnapshotUnreadable:
    reason: SnapshotUnreadableReason
    detail: str


def read_snapshot_files(uri: str) -> SnapshotFiles | SnapshotUnreadable:
    """`file:///<dir>` 의 `manifest.json`·`rows.jsonl`·`sample-list.tsv` **셋**을
    읽는다(하나라도 없으면 `NOT_FOUND` 로 전체가 선다). 다른 scheme 과
    `file://<host>/…`(비표준, host 를 조용히 무시할 위험)은 fail-closed — 5C
    `read_dataset_files` 가 PR #13 리뷰에서 닫은 것과 같은 경계."""
    parsed = urlparse(uri)
    if parsed.scheme != "file":
        return SnapshotUnreadable(
            SnapshotUnreadableReason.UNSUPPORTED_SCHEME, parsed.scheme
        )
    if parsed.netloc:
        return SnapshotUnreadable(
            SnapshotUnreadableReason.UNSUPPORTED_SCHEME, f"file://{parsed.netloc}/…"
        )
    directory = Path(parsed.path).resolve()
    if not directory.exists():
        return SnapshotUnreadable(SnapshotUnreadableReason.NOT_FOUND, str(directory))
    if not directory.is_dir():
        return SnapshotUnreadable(
            SnapshotUnreadableReason.NOT_A_DIRECTORY, str(directory)
        )
    manifest_path = directory / "manifest.json"
    rows_path = directory / "rows.jsonl"
    sample_list_path = directory / "sample-list.tsv"
    for path in (manifest_path, rows_path, sample_list_path):
        if not path.is_file():
            return SnapshotUnreadable(SnapshotUnreadableReason.NOT_FOUND, str(path))
    return SnapshotFiles(
        manifest_bytes=manifest_path.read_bytes(),
        rows_bytes=rows_path.read_bytes(),
        sample_list_bytes=sample_list_path.read_bytes(),
    )
