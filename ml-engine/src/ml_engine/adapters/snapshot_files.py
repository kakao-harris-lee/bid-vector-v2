"""`ml_engine.adapters.snapshot_files` — 6G 스냅숏 파일 읽기(D-6G-2). 5C
`dataset_files` 와 같은 갈래다: **이 모듈은 바이트만 만들고 뜻은 모른다** — 판독은
`ml_engine.evaluation.backtest.snapshot.load_snapshot` 이 한다.

`file://` 경로만 지원한다(네트워크 0). 스냅숏은 디렉터리 하나(`manifest.json` +
`rows.jsonl` + `sample-list.tsv`, v5)이고, **저장소 밖**에 둔다(`~/.local/bid-vector-snapshots/<id>/`,
data-extract §7 · ADR 0010 D-8) — 그래서 이 함수가 받는 경로는 저장소 안이 아니다.

**URI 의 퍼센트 인코딩을 푼다**(M6/6G-2c D-6G2c-22, `OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`
종결). `Path.as_uri()` 는 공백·비ASCII 를 `%XX` 로 인코딩하고 `urlparse(...).path` 는 그것을
풀지 않는다 — 앞 판은 그 문자열을 그대로 경로로 써서 공백이나 한글이 든 디렉터리의 스냅숏을
`NOT_FOUND` 로 세웠다. 푸는 함수는 `urllib.parse.unquote` 다: `urllib.request.url2pathname`
은 POSIX 에서 같은 함수이지만 `urlopen`·opener 기계와 `http.client` 를 함께 들여온다
(D-6G2e-23 ① — adapters 도 HTTP 모듈을 들이지 않는다).

디코딩은 판독을 **느슨하게 하지 않는다**: 없는 경로는 여전히 `NOT_FOUND` 이고 파일 셋 대조도
그대로다. 바뀌는 것은 「어느 경로를 보는가」뿐이다.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum
from pathlib import Path
from urllib.parse import unquote, urlparse


@dataclass(frozen=True)
class SnapshotFiles:
    manifest_bytes: bytes
    rows_bytes: bytes
    sample_list_bytes: bytes
    """v4(D-6G-39) — 표본 목록이 **파일**이 됐다. 판독이 이 바이트의 sha256 을
    manifest 와 대조하므로 다시 렌더링하지 않고 바이트 그대로 나른다."""


class SnapshotUnreadableReason(StrEnum):
    UNSUPPORTED_SCHEME = "UNSUPPORTED_SCHEME"
    INVALID_PATH = "INVALID_PATH"
    """디코딩한 경로를 운영체제가 경로로 받지 않는다(M6/6G-2c 수정 r1, verifier F-5).

    `%00` 이 든 URI 가 그 자리다 — 푼 뒤에 널 바이트가 섞여 `resolve()` 가 `ValueError` 를
    던진다. 앞 판은 문자 그대로 읽어 `NOT_FOUND` 였고, 디코딩을 넣은 뒤로는 **닫힌 어휘 밖**
    예외가 호출자에게 샜다. 거부는 그대로이되 이름이 있어야 「왜 못 읽었는지 모르는 입력」이
    생기지 않는다."""

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
    try:
        directory = Path(unquote(parsed.path)).resolve()
    except ValueError as exc:
        # 널 바이트처럼 운영체제가 경로로 받지 않는 바이트 — 닫힌 사유로 돌려준다(F-5).
        # 문면에 경로를 싣지 않는다(예외 문면은 바이트를 그대로 담을 수 있다).
        return SnapshotUnreadable(
            SnapshotUnreadableReason.INVALID_PATH, type(exc).__name__
        )
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
