"""RED — `ml_engine.adapters.dataset_files`(D-5C-8). `file://`만, 네트워크 0, 탈출 경로는
`resolve()` 뒤 scheme 검사만."""

from __future__ import annotations

from pathlib import Path

from ml_engine.adapters.dataset_files import (
    DatasetFiles,
    DatasetUnreadable,
    DatasetUnreadableReason,
    read_dataset_files,
)


def _write_dataset(directory: Path) -> None:
    (directory / "manifest.json").write_bytes(b'{"dataset_id": "ds-1"}')
    (directory / "rows.jsonl").write_bytes(b'{"label": 0.5}\n')


def test_read_dataset_files_round_trip(tmp_path: Path) -> None:
    _write_dataset(tmp_path)
    result = read_dataset_files(f"file://{tmp_path}")
    assert isinstance(result, DatasetFiles)
    assert result.manifest_bytes == b'{"dataset_id": "ds-1"}'
    assert result.rows_bytes == b'{"label": 0.5}\n'


def test_read_dataset_files_rejects_unsupported_scheme() -> None:
    result = read_dataset_files("s3://bucket/dataset")
    assert isinstance(result, DatasetUnreadable)
    assert result.reason == DatasetUnreadableReason.UNSUPPORTED_SCHEME


def test_read_dataset_files_rejects_missing_directory(tmp_path: Path) -> None:
    result = read_dataset_files(f"file://{tmp_path / 'does-not-exist'}")
    assert isinstance(result, DatasetUnreadable)
    assert result.reason == DatasetUnreadableReason.NOT_FOUND


def test_read_dataset_files_rejects_non_directory(tmp_path: Path) -> None:
    file_path = tmp_path / "not-a-dir"
    file_path.write_text("x")
    result = read_dataset_files(f"file://{file_path}")
    assert isinstance(result, DatasetUnreadable)
    assert result.reason == DatasetUnreadableReason.NOT_A_DIRECTORY


def test_read_dataset_files_rejects_missing_manifest(tmp_path: Path) -> None:
    (tmp_path / "rows.jsonl").write_bytes(b"")
    result = read_dataset_files(f"file://{tmp_path}")
    assert isinstance(result, DatasetUnreadable)
    assert result.reason == DatasetUnreadableReason.NOT_FOUND


def test_read_dataset_files_rejects_missing_rows(tmp_path: Path) -> None:
    (tmp_path / "manifest.json").write_bytes(b"{}")
    result = read_dataset_files(f"file://{tmp_path}")
    assert isinstance(result, DatasetUnreadable)
    assert result.reason == DatasetUnreadableReason.NOT_FOUND


def test_read_dataset_files_no_network_import() -> None:
    """이 모듈이 `requests`/`httpx`를 import하지 않는다는 구조적 확인(위협 모델 j)."""
    import ml_engine.adapters.dataset_files as module

    source = Path(module.__file__).read_text(encoding="utf-8")
    assert "requests" not in source
    assert "httpx" not in source
