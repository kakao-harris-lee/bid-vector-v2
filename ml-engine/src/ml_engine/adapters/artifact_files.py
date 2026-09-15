"""`ml_engine.adapters.artifact_files` — D-5E-5 job 산출물 파일 저장. `file://` 만
(object storage 는 6C). `job_dir`는 이 함수가 만든다 — **존재하면 거부**한다(덮어쓰기
금지, 설계 검토 우회 (9)) — 부분 산출물이 남지 않는다: 디렉터리 생성 자체가 실패하면
아무 파일도 쓰지 않고, 두 파일 중 **하나만** 쓰고 나머지가 실패해도(예: 디스크 가득
참) 그 디렉터리를 통째로 지운다(verifier r1 M-6 — 이전 판은 이 경로를 다루지 않아
`artifact.json`만 남는 고아 상태가 가능했다).
"""

from __future__ import annotations

import shutil
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class ArtifactFileRefs:
    artifact_uri: str
    report_uri: str


@dataclass(frozen=True)
class ArtifactWriteRejected:
    detail: str


def write_artifact_files(
    job_dir: Path, artifact_bytes: bytes, report_bytes: bytes
) -> ArtifactFileRefs | ArtifactWriteRejected:
    """`job_dir/{artifact.json, report.json}`를 쓰고 `file://` uri 둘을 낸다. `job_dir`
    가 이미 있으면(빈 디렉터리라도) 거부 — 호출자(`ml_engine.app.pipeline`)가 job 마다
    고유한 경로(`<out_dir>/<job_id>`)를 준다는 전제."""
    try:
        job_dir.mkdir(parents=True, exist_ok=False)
    except FileExistsError:
        return ArtifactWriteRejected(f"디렉터리가 이미 존재합니다: {job_dir}")

    artifact_path = job_dir / "artifact.json"
    report_path = job_dir / "report.json"
    try:
        artifact_path.write_bytes(artifact_bytes)
        report_path.write_bytes(report_bytes)
    except OSError as exc:
        # M-6 — 둘 중 하나만 쓰고 실패하면(디스크 가득 참 등) 방금 만든 디렉터리를
        # 통째로 지운다 — 고아 `artifact.json`만 남기지 않는다.
        shutil.rmtree(job_dir, ignore_errors=True)
        return ArtifactWriteRejected(f"파일 쓰기 실패(디렉터리 정리함): {exc}")

    return ArtifactFileRefs(
        artifact_uri=artifact_path.resolve().as_uri(),
        report_uri=report_path.resolve().as_uri(),
    )
