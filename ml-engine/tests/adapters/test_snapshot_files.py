"""M6/6G-2c D-6G2c-22 — `ml_engine.adapters.snapshot_files` 의 URI 해석.

판독기가 `file:` URI 의 **퍼센트 인코딩을 푼다**(`OPEN-6G2E-SNAPSHOT-READER-URI-DECODE`
종결). 여기서 재는 것은 둘이다:

1. 공백·한글이 든 디렉터리의 스냅숏을 **찾는다**(앞 판은 `%XX` 를 문자 그대로 경로로 써서
   `NOT_FOUND` 로 섰다).
2. 디코딩이 판독을 **느슨하게 하지 않았다** — 없는 경로는 여전히 `NOT_FOUND` 이고, 파일 셋
   대조와 scheme 경계도 그대로다. 「고치면서 열렸다」가 되지 않는다는 음성 대조다.

끝까지 도는 판(CLI exit 0)은 `tests/app/test_backtest_cli.py` 가 잰다 — 이쪽은 판독기 단위다.
"""

from __future__ import annotations

from pathlib import Path

from ml_engine.adapters.snapshot_files import (
    SnapshotFiles,
    SnapshotUnreadable,
    SnapshotUnreadableReason,
    read_snapshot_files,
)

_MANIFEST = b'{"schema_version": "snapshot-v5"}'
_ROWS = b'{"notice": {}}\n'
_SAMPLE_LIST = b"ab\tSERVICE\t2026-W25\n"


def _write_snapshot(directory: Path) -> Path:
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "manifest.json").write_bytes(_MANIFEST)
    (directory / "rows.jsonl").write_bytes(_ROWS)
    (directory / "sample-list.tsv").write_bytes(_SAMPLE_LIST)
    return directory


def _spaced(root: Path) -> Path:
    """공백과 한글이 든 디렉터리 — `as_uri()` 가 둘 다 `%XX` 로 인코딩한다."""
    return root / "snap dir" / "스냅숏"


def test_a_spaced_and_hangul_path_is_read_through_its_uri(tmp_path: Path) -> None:
    """D-6G2c-22 — 퍼센트 인코딩된 URI 로 바이트 셋이 온다.

    `as_uri()` 가 실제로 인코딩했는지 먼저 단언한다 — 인코딩이 없으면 이 판은 디코딩을
    재지 않고 지나간다(ASCII·공백 없는 `tmp_path` 로는 그 자리가 안 돌았다)."""
    directory = _write_snapshot(_spaced(tmp_path))
    uri = directory.as_uri()
    assert "%20" in uri, uri
    result = read_snapshot_files(uri)
    assert isinstance(result, SnapshotFiles), result
    assert result.manifest_bytes == _MANIFEST
    assert result.rows_bytes == _ROWS
    assert result.sample_list_bytes == _SAMPLE_LIST


def test_a_percent_encoded_path_that_does_not_exist_is_still_not_found(
    tmp_path: Path,
) -> None:
    """**음성 대조** — 디코딩이 거부를 풀지 않았다. 없는 경로는 여전히 `NOT_FOUND` 다."""
    missing = _spaced(tmp_path) / "없는 스냅숏"
    result = read_snapshot_files(missing.as_uri())
    assert isinstance(result, SnapshotUnreadable), result
    assert result.reason is SnapshotUnreadableReason.NOT_FOUND


def test_a_spaced_directory_missing_one_file_is_not_found(tmp_path: Path) -> None:
    """파일 셋 대조도 그대로다 — 셋 중 하나가 없으면 전체가 선다(디코딩과 무관)."""
    directory = _write_snapshot(_spaced(tmp_path))
    (directory / "sample-list.tsv").unlink()
    result = read_snapshot_files(directory.as_uri())
    assert isinstance(result, SnapshotUnreadable), result
    assert result.reason is SnapshotUnreadableReason.NOT_FOUND


def test_a_spaced_path_that_is_a_file_is_not_a_directory(tmp_path: Path) -> None:
    """디렉터리 경계도 그대로다 — 디코딩 뒤 가리키는 것이 파일이면 거부한다."""
    path = _spaced(tmp_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(_MANIFEST)
    result = read_snapshot_files(path.as_uri())
    assert isinstance(result, SnapshotUnreadable), result
    assert result.reason is SnapshotUnreadableReason.NOT_A_DIRECTORY


def test_a_path_the_os_refuses_gets_a_closed_reason(tmp_path: Path) -> None:
    """verifier r1 F-5 — `%00` 이 **닫힌 사유**로 돌아온다(예외가 아니다).

    디코딩을 넣기 전에는 `%00` 이 문자 그대로라 `NOT_FOUND` 였다. 푼 뒤로는 널 바이트가 섞여
    `resolve()` 가 `ValueError` 를 던졌고, 그것은 닫힌 어휘 **밖**이라 호출자에게 샌다. 거부는
    그대로이되 이름이 있어야 「왜 못 읽었는지 모르는 입력」이 생기지 않는다.

    문면에 경로를 싣지 않는다 — 예외 문면은 그 바이트를 그대로 담을 수 있다."""
    del tmp_path
    result = read_snapshot_files("file:///tmp/a%00b")
    assert isinstance(result, SnapshotUnreadable), result
    assert result.reason is SnapshotUnreadableReason.INVALID_PATH
    assert "\x00" not in result.detail and "/tmp" not in result.detail, result.detail


def test_scheme_and_host_boundaries_are_unchanged(tmp_path: Path) -> None:
    """scheme 경계도 그대로다. `file://<host>/…` 은 host 를 조용히 무시할 위험이라
    fail-closed 이고, 그 판정은 디코딩 **앞**에 있다."""
    directory = _write_snapshot(_spaced(tmp_path))
    for uri in (f"s3://bucket{directory}", f"file://host{directory}"):
        result = read_snapshot_files(uri)
        assert isinstance(result, SnapshotUnreadable), (uri, result)
        assert result.reason is SnapshotUnreadableReason.UNSUPPORTED_SCHEME, uri
