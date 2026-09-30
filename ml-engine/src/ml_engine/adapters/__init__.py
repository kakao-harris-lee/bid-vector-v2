"""ml_engine.adapters — training 쪽 storage adapter(5C). `serving`은 이 패키지를 import할
수 없다(pyproject.toml import-linter forbidden 계약).
"""

from __future__ import annotations

from ml_engine.adapters.artifact_files import (
    ArtifactFileRefs,
    ArtifactWriteRejected,
    write_artifact_files,
)
from ml_engine.adapters.dataset_files import (
    DatasetFiles,
    DatasetUnreadable,
    DatasetUnreadableReason,
    read_dataset_files,
)
from ml_engine.adapters.snapshot_files import (
    SnapshotFiles,
    SnapshotUnreadable,
    SnapshotUnreadableReason,
    read_snapshot_files,
)

__all__ = [
    "ArtifactFileRefs",
    "ArtifactWriteRejected",
    "DatasetFiles",
    "DatasetUnreadable",
    "DatasetUnreadableReason",
    "SnapshotFiles",
    "SnapshotUnreadable",
    "SnapshotUnreadableReason",
    "read_dataset_files",
    "read_snapshot_files",
    "write_artifact_files",
]
