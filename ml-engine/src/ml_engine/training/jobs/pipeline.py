"""`ml_engine.training.jobs.pipeline` — `TrainingPipeline` Protocol + 결과 타입 +
D-5E-11 매핑 표(5C-1 `TrainingRejected`·`DatasetRejected`·`DatasetUnreadable`, 5C-2
`HoldoutRejected` → `JobFailureCode`+`DetailCode`). **실물 조립은 `ml_engine.app.pipeline`**
(조립 근, D-5E-1) — 이 모듈은 계약(Protocol)과 매핑 표만 가진다.

`DetailCode`는 닫힌 어휘(StrEnum) — 자유 문자열(`str(exc)`)을 `detail_code`에 담지
않는다(설계 검토 (1) 「자유 문자열」). 값은 5C-1/5C-2 의 거부 사유 enum 이름을 그대로
잇거나(재사용), 이 slice 가 처음 만드는 사유(파이프라인 실행 자체의 실패)를 더한다.
"""

from __future__ import annotations

import threading
from dataclasses import dataclass
from enum import StrEnum
from typing import Protocol

from ml_engine.adapters.dataset_files import DatasetUnreadableReason
from ml_engine.training.dataset import DatasetRejectionReason
from ml_engine.training.holdout import HoldoutRejectionReason
from ml_engine.training.jobs.state import JobFailureCode
from ml_engine.training.train import TrainingRejectionReason


class DetailCode(StrEnum):
    """닫힌 어휘 — 5C-1/5C-2 거부 사유 + 이 slice 가 신설하는 사유(D-5E-4·⑦ 우회 (9)(13))."""

    # DatasetUnreadableReason(adapters) 계승
    UNSUPPORTED_SCHEME = "UNSUPPORTED_SCHEME"
    NOT_FOUND = "NOT_FOUND"
    NOT_A_DIRECTORY = "NOT_A_DIRECTORY"
    SETTLEMENTS_ABSENT = "SETTLEMENTS_ABSENT"
    # DatasetRejectionReason(training.dataset) 계승
    CHECKSUM_MISMATCH = "CHECKSUM_MISMATCH"
    UNREADABLE = "UNREADABLE"
    SCHEMA_UNSUPPORTED = "SCHEMA_UNSUPPORTED"
    ID_MISMATCH = "ID_MISMATCH"
    ROWS_CHECKSUM_MISMATCH = "ROWS_CHECKSUM_MISMATCH"
    SETTLEMENTS_CHECKSUM_MISMATCH = "SETTLEMENTS_CHECKSUM_MISMATCH"
    # TrainingRejectionReason(training.train) 계승
    INSUFFICIENT_TRAINING_ROWS = "INSUFFICIENT_TRAINING_ROWS"
    NO_OBSERVATIONS = "NO_OBSERVATIONS"
    ALL_ROWS_REJECTED = "ALL_ROWS_REJECTED"
    TRAINER_ERROR = "TRAINER_ERROR"
    # HoldoutRejectionReason(training.holdout) 계승
    EMPTY_SIDE = "EMPTY_SIDE"
    INVALID_MATURITY_INPUT = "INVALID_MATURITY_INPUT"
    ACCOUNTING_MISMATCH = "ACCOUNTING_MISMATCH"
    # 이 slice 신설 — 파이프라인 실행 자체의 실패(우회 (9)(13))
    OUTPUT_DIR_NOT_EMPTY = "OUTPUT_DIR_NOT_EMPTY"
    RUNNER_CRASHED = "RUNNER_CRASHED"
    ARTIFACT_WRITE_REJECTED = "ARTIFACT_WRITE_REJECTED"
    REPORT_CANONICALIZATION_REJECTED = "REPORT_CANONICALIZATION_REJECTED"


@dataclass(frozen=True)
class DatasetRefInput:
    """`build_training_pipeline`이 만든 파이프라인의 `run()`이 받는 dataset 참조 —
    2C `DatasetReference`(`uri`·`manifest_checksum`·`dataset_id`) 그대로."""

    uri: str
    manifest_checksum: str
    dataset_id: str


@dataclass(frozen=True)
class ArtifactRefValue:
    """`PipelineOutcome.artifact` — `ArtifactReference`(state.py) 조립에 필요한 성분."""

    uri: str
    release_id: str
    artifact_checksum: str
    feature_schema_version: str
    code_version: str
    dataset_id: str
    manifest_schema_version: str


@dataclass(frozen=True)
class EvaluationRefValue:
    """`PipelineOutcome.evaluation` — `EvaluationReportReference`(state.py) 조립에 필요한
    성분."""

    uri: str
    checksum: str
    report_schema_version: str


@dataclass(frozen=True)
class PipelineOutcome:
    """파이프라인 성공 산출물."""

    artifact: ArtifactRefValue
    evaluation: EvaluationRefValue


@dataclass(frozen=True)
class PipelineFailed:
    """파이프라인 실패 — `JobFailure`(state.py)로 옮기는 재료."""

    code: JobFailureCode
    detail_code: DetailCode


@dataclass(frozen=True)
class PipelineCancelled:
    """단계 경계에서 취소가 확인됐다 — `failure` 없이 `CANCELLED`로 전이한다(2C 조합
    불변식)."""


class CancelToken:
    """`threading.Event` 래퍼(설계 검토 (1) 「취소 존중」) — 파이프라인이 단계 경계마다
    `is_cancelled()`를 확인한다."""

    def __init__(self) -> None:
        self._event = threading.Event()

    def cancel(self) -> None:
        self._event.set()

    def is_cancelled(self) -> bool:
        return self._event.is_set()


class TrainingPipeline(Protocol):
    """실물은 `ml_engine.app.pipeline.build_training_pipeline`이 조립한다(D-5E-1) —
    이 Protocol 은 `training.jobs.runner`가 의존하는 계약일 뿐이다."""

    def run(
        self, dataset_ref: DatasetRefInput, cancel_token: CancelToken
    ) -> PipelineOutcome | PipelineFailed | PipelineCancelled: ...


def map_dataset_unreadable(
    reason: DatasetUnreadableReason,
) -> tuple[JobFailureCode, DetailCode]:
    """D-5E-11 매핑 — 파일을 못 읽음(scheme·경로 문제)은 전부 `DATASET_UNREADABLE`."""
    return JobFailureCode.DATASET_UNREADABLE, DetailCode(reason.value)


def map_dataset_rejected(
    reason: DatasetRejectionReason,
) -> tuple[JobFailureCode, DetailCode]:
    """D-5E-11 매핑 — checksum 불일치는 `DATASET_CHECKSUM_MISMATCH`, 그 밖은
    `DATASET_UNREADABLE`(구조·스키마 문제)."""
    if reason in (
        DatasetRejectionReason.CHECKSUM_MISMATCH,
        DatasetRejectionReason.ROWS_CHECKSUM_MISMATCH,
        DatasetRejectionReason.SETTLEMENTS_CHECKSUM_MISMATCH,
    ):
        return JobFailureCode.DATASET_CHECKSUM_MISMATCH, DetailCode(reason.value)
    return JobFailureCode.DATASET_UNREADABLE, DetailCode(reason.value)


def map_training_rejected(
    reason: TrainingRejectionReason,
) -> tuple[JobFailureCode, DetailCode]:
    """D-5E-11 매핑 — 2C 6값에 없는 사유이므로 `TRAINING_ERROR` + `detail_code`."""
    return JobFailureCode.TRAINING_ERROR, DetailCode(reason.value)


def map_holdout_rejected(
    reason: HoldoutRejectionReason,
) -> tuple[JobFailureCode, DetailCode]:
    """D-5E-11 매핑 — 2C 6값에 없는 사유이므로 `EVALUATION_ERROR` + `detail_code`."""
    return JobFailureCode.EVALUATION_ERROR, DetailCode(reason.value)
