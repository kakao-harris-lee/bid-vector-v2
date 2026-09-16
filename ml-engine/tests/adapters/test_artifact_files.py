"""RED — `ml_engine.adapters.artifact_files.write_artifact_files`(D-5E-5). verifier r1
M-6 — 직접 단위 test 가 0건이었다: 덮어쓰기 금지 불변식과 「부분 산출물이 남지 않는다」
주장(첫 파일 성공 뒤 둘째 파일 실패) 둘 다 변이로 보호되지 않았다."""

from __future__ import annotations

from pathlib import Path

import pytest

from ml_engine.adapters.artifact_files import (
    ArtifactFileRefs,
    ArtifactWriteRejected,
    write_artifact_files,
)


def test_writes_both_files_and_returns_matching_file_uris(tmp_path: Path) -> None:
    job_dir = tmp_path / "job-1"
    result = write_artifact_files(job_dir, b"artifact-bytes", b"report-bytes")
    assert isinstance(result, ArtifactFileRefs)
    assert result.artifact_uri == (job_dir / "artifact.json").resolve().as_uri()
    assert result.report_uri == (job_dir / "report.json").resolve().as_uri()
    assert (job_dir / "artifact.json").read_bytes() == b"artifact-bytes"
    assert (job_dir / "report.json").read_bytes() == b"report-bytes"


def test_existing_empty_directory_is_rejected_not_overwritten(tmp_path: Path) -> None:
    """덮어쓰기 금지 — 디렉터리가 이미 있으면 **비어 있어도** 거부한다(설계 검토
    우회 (9))."""
    job_dir = tmp_path / "job-1"
    job_dir.mkdir()
    result = write_artifact_files(job_dir, b"artifact-bytes", b"report-bytes")
    assert isinstance(result, ArtifactWriteRejected)
    assert list(job_dir.iterdir()) == []


def test_existing_directory_with_files_is_rejected_files_untouched(
    tmp_path: Path,
) -> None:
    job_dir = tmp_path / "job-1"
    job_dir.mkdir()
    (job_dir / "artifact.json").write_bytes(b"pre-existing")
    result = write_artifact_files(job_dir, b"new-artifact-bytes", b"new-report-bytes")
    assert isinstance(result, ArtifactWriteRejected)
    # 기존 파일이 새 바이트로 덮이지 않았다 — 거부는 디렉터리 생성 단계에서 일어나
    # 파일 쓰기 자체를 시도하지 않는다.
    assert (job_dir / "artifact.json").read_bytes() == b"pre-existing"
    assert not (job_dir / "report.json").exists()


def test_second_file_write_failure_leaves_no_partial_artifact(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    """「부분 산출물이 남지 않는다」 실제 경로 — `artifact.json` 쓰기 성공 뒤
    `report.json` 쓰기가 실패하면(디스크가 가득 찬 것과 같은 상황을 흉내) 첫 파일도
    포함해 디렉터리 전체를 지운다. verifier r1 M-6 이 지목한 미검증 경로(이전 판은
    고아 `artifact.json`이 남을 수 있었다)."""
    job_dir = tmp_path / "job-1"
    original_write_bytes = Path.write_bytes
    call_count = {"n": 0}

    def failing_second_write(self: Path, data: bytes) -> int:
        call_count["n"] += 1
        if call_count["n"] == 2:
            raise OSError("disk full(시뮬레이션)")
        return original_write_bytes(self, data)

    monkeypatch.setattr(Path, "write_bytes", failing_second_write)

    result = write_artifact_files(job_dir, b"artifact-bytes", b"report-bytes")

    assert isinstance(result, ArtifactWriteRejected)
    assert not job_dir.exists()
