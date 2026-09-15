"""RED — `ml_engine.training.jobs.pipeline`(D-5E-11 매핑 표 + `CancelToken`)."""

from __future__ import annotations

from ml_engine.adapters.dataset_files import DatasetUnreadableReason
from ml_engine.training.dataset import DatasetRejectionReason
from ml_engine.training.holdout import HoldoutRejectionReason
from ml_engine.training.jobs.pipeline import (
    CancelToken,
    DetailCode,
    JobFailureCode,
    map_dataset_rejected,
    map_dataset_unreadable,
    map_holdout_rejected,
    map_training_rejected,
)
from ml_engine.training.train import TrainingRejectionReason


def test_cancel_token_starts_uncancelled() -> None:
    token = CancelToken()
    assert token.is_cancelled() is False


def test_cancel_token_cancel_is_observed() -> None:
    token = CancelToken()
    token.cancel()
    assert token.is_cancelled() is True


def test_map_dataset_unreadable_covers_every_reason() -> None:
    for reason in DatasetUnreadableReason:
        code, detail = map_dataset_unreadable(reason)
        assert code is JobFailureCode.DATASET_UNREADABLE
        assert detail.value == reason.value


def test_map_dataset_rejected_checksum_reasons_map_to_checksum_mismatch() -> None:
    for reason in (
        DatasetRejectionReason.CHECKSUM_MISMATCH,
        DatasetRejectionReason.ROWS_CHECKSUM_MISMATCH,
    ):
        code, _detail = map_dataset_rejected(reason)
        assert code is JobFailureCode.DATASET_CHECKSUM_MISMATCH


def test_map_dataset_rejected_other_reasons_map_to_unreadable() -> None:
    for reason in (
        DatasetRejectionReason.UNREADABLE,
        DatasetRejectionReason.SCHEMA_UNSUPPORTED,
        DatasetRejectionReason.ID_MISMATCH,
    ):
        code, _detail = map_dataset_rejected(reason)
        assert code is JobFailureCode.DATASET_UNREADABLE


def test_map_dataset_rejected_settlements_checksum_mismatch() -> None:
    code, detail = map_dataset_rejected(
        DatasetRejectionReason.SETTLEMENTS_CHECKSUM_MISMATCH
    )
    assert code is JobFailureCode.DATASET_CHECKSUM_MISMATCH
    assert detail is DetailCode.SETTLEMENTS_CHECKSUM_MISMATCH


def test_map_training_rejected_covers_every_reason() -> None:
    for reason in TrainingRejectionReason:
        code, detail = map_training_rejected(reason)
        assert code is JobFailureCode.TRAINING_ERROR
        assert detail.value == reason.value


def test_map_holdout_rejected_covers_every_reason() -> None:
    for reason in HoldoutRejectionReason:
        code, detail = map_holdout_rejected(reason)
        assert code is JobFailureCode.EVALUATION_ERROR
        assert detail.value == reason.value
