"""ml_engine.training — 낙찰률 GBM 학습 커널(M5/5C-1). 공개 표면 재수출(scope.md ①~⑫가
전수) — 다른 패키지(serving·registry 등)는 이 최상위 이름만 보고 하위 모듈을 직접
import하지 않는다."""

from __future__ import annotations

from ml_engine.training.artifact_writer import ArtifactBytes, write_artifact
from ml_engine.training.booster import (
    BoosterLike,
    LightGbmTrainer,
    TrainerFailed,
    TrainerLike,
    booster_to_text,
)
from ml_engine.training.corpus import (
    AdmittedCorpus,
    AwardRateLabel,
    CorpusRejected,
    LabelRejected,
    LabelRejectionReason,
    RejectedRowAccounting,
    TrainingRow,
    admit_corpus,
    admit_label,
    with_missing_fact_rejections,
)
from ml_engine.training.dataset import (
    DatasetManifestV1,
    DatasetReference,
    DatasetRejected,
    DatasetRejectionReason,
    LoadedDataset,
    RawTrainingRow,
    load_dataset,
)
from ml_engine.training.encoding_oof import (
    OutOfFoldBuilt,
    OutOfFoldNoObservations,
    OutOfFoldOutcome,
    OutOfFoldTrainerFailed,
    full_corpus_feature_space,
    out_of_fold_matrix_and_residuals,
)
from ml_engine.training.folds import fold_indices
from ml_engine.training.policy import (
    SHIPPED_TRAINING_POLICY_VERSION,
    PolicyRejected,
    PolicyRejectionReason,
    TrainingPolicy,
    load_training_policy,
)
from ml_engine.training.release import ReleaseIdentity, derive_release_id
from ml_engine.training.residual import residual_std
from ml_engine.training.spec import (
    TRAINING_SPECS,
    LightGbmHyperparameters,
    TrainingSpec,
    UnsupportedTrainingSpec,
    resolve_training_spec,
    spec_checksum,
)
from ml_engine.training.train import (
    CodeVersion,
    ReproducibilityInfo,
    TrainedArtifact,
    TrainingRejected,
    TrainingRejectionReason,
    train_award_rate_gbm,
)

__all__ = [
    "SHIPPED_TRAINING_POLICY_VERSION",
    "TRAINING_SPECS",
    "AdmittedCorpus",
    "ArtifactBytes",
    "AwardRateLabel",
    "BoosterLike",
    "CodeVersion",
    "CorpusRejected",
    "DatasetManifestV1",
    "DatasetReference",
    "DatasetRejected",
    "DatasetRejectionReason",
    "LabelRejected",
    "LabelRejectionReason",
    "LightGbmHyperparameters",
    "LightGbmTrainer",
    "LoadedDataset",
    "OutOfFoldBuilt",
    "OutOfFoldNoObservations",
    "OutOfFoldOutcome",
    "OutOfFoldTrainerFailed",
    "PolicyRejected",
    "PolicyRejectionReason",
    "RawTrainingRow",
    "RejectedRowAccounting",
    "ReleaseIdentity",
    "ReproducibilityInfo",
    "TrainedArtifact",
    "TrainerFailed",
    "TrainerLike",
    "TrainingPolicy",
    "TrainingRejected",
    "TrainingRejectionReason",
    "TrainingRow",
    "TrainingSpec",
    "UnsupportedTrainingSpec",
    "admit_corpus",
    "admit_label",
    "booster_to_text",
    "derive_release_id",
    "fold_indices",
    "full_corpus_feature_space",
    "load_dataset",
    "load_training_policy",
    "out_of_fold_matrix_and_residuals",
    "residual_std",
    "resolve_training_spec",
    "spec_checksum",
    "train_award_rate_gbm",
    "with_missing_fact_rejections",
    "write_artifact",
]
