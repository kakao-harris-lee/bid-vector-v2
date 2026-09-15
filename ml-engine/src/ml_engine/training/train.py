"""Reuse: bid-vector/app/services/ml_training/award_rate_gbm.py@ed4b06c

`ml_engine.training.train` — 유일 학습 진입점(scope ⑥⑨, legacy `train_award_rate_gbm`).
legacy 는 최소 표본 게이트를 선언만 하고 걸지 않았다(조사 01 §8-2) — 이 함수가 처음
건다. 순서: corpus 승인(②) → 최소 표본 게이트(③) → out-of-fold 조립(④) → 전 구간
인코딩(④) → 최종 부스터 학습(⑥) → `TrainedArtifact` 재료 조립. 파일 쓰기는 하지 않는다
(`artifact_writer.py`가 한다).
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import StrEnum

from ml_engine.features import (
    FEATURE_SCHEMA_V2,
    SHIPPED_ENCODING_POLICY,
    AwardRateFeatureSpace,
    FeatureKind,
    FeatureManifest,
    ManifestAgencyMean,
    ManifestCategoryMean,
    ManifestColumn,
    NoObservations,
)
from ml_engine.training.booster import BoosterLike, TrainerFailed, TrainerLike
from ml_engine.training.corpus import (
    AdmittedCorpus,
    CorpusRejected,
    RejectedRowAccounting,
    admit_corpus,
    with_missing_fact_rejections,
)
from ml_engine.training.dataset import LoadedDataset
from ml_engine.training.encoding_oof import (
    OutOfFoldBuilt,
    OutOfFoldNoObservations,
    OutOfFoldTrainerFailed,
    full_corpus_feature_space,
    out_of_fold_matrix_and_residuals,
)
from ml_engine.training.policy import TrainingPolicy
from ml_engine.training.residual import residual_std as compute_residual_std
from ml_engine.training.spec import TrainingSpec, spec_checksum

_FEATURE_NAMES: tuple[str, ...] = tuple(
    column.name for column in FEATURE_SCHEMA_V2.columns
)
_CATEGORICAL_INDICES: tuple[int, ...] = tuple(
    index
    for index, column in enumerate(FEATURE_SCHEMA_V2.columns)
    if column.kind is FeatureKind.CATEGORICAL
)


class TrainingRejectionReason(StrEnum):
    """D-5C-11 — 2C `JobFailureCode`(6값)에 없는 사유는 `TRAINING_ERROR` +
    `detail_code`로 나른다(`checklist.md`의 매핑 표)."""

    INSUFFICIENT_TRAINING_ROWS = "INSUFFICIENT_TRAINING_ROWS"
    NO_OBSERVATIONS = "NO_OBSERVATIONS"
    ALL_ROWS_REJECTED = "ALL_ROWS_REJECTED"
    TRAINER_ERROR = "TRAINER_ERROR"


@dataclass(frozen=True)
class TrainingRejected:
    reason: TrainingRejectionReason
    detail: str


@dataclass(frozen=True)
class CodeVersion:
    """호출자가 문자열로 준다 — 이 모듈은 git 을 호출하지 않는다."""

    value: str

    def __post_init__(self) -> None:
        if not self.value or not self.value.strip():
            raise ValueError("code_version 은 비어 있거나 공백일 수 없습니다.")


@dataclass(frozen=True)
class ReproducibilityInfo:
    seed: int
    num_threads: int
    deterministic: bool


@dataclass(frozen=True)
class TrainedArtifact:
    """`write_artifact`의 유일 입력. `booster`는 아직 텍스트로 굳지 않았다 — writer 가
    `feature_name()` 검증 뒤 `booster_to_text`로 굳힌다."""

    booster: BoosterLike
    feature_manifest: FeatureManifest
    residual_std: float
    training_row_count: int
    sample_scope: str
    feed_origin_only: bool
    dataset_id: str
    code_version: str
    training_spec_version: str
    training_spec_checksum: str
    training_policy_version: str
    reproducibility: ReproducibilityInfo
    rejected_rows: RejectedRowAccounting


def _required_rows(policy: TrainingPolicy) -> int:
    return max(1, policy.min_training_rows)


def _admit_and_gate(
    dataset: LoadedDataset, policy: TrainingPolicy
) -> AdmittedCorpus | TrainingRejected:
    """corpus 승인(②) + 최소 표본 게이트(③) — **조기 거부**. `admit_corpus`는 wire
    `Missing`을 거부 사유로 보지 않으므로(구조적으로 유효한 fact), 이 게이트를 통과한
    행 수는 아직 실제 학습 행렬에 실릴 행 수가 아니다 — `build_row`(scope ⑤)가
    `base_amount`/`denominator_source` 결측 행을 추가로 떨어뜨린다. 그 뒤 재게이트는
    `_build_oof_and_space`가 한다(code-reviewer PR #13 HIGH-1)."""
    admitted = admit_corpus(dataset.raw_rows)
    if isinstance(admitted, CorpusRejected):
        return TrainingRejected(
            TrainingRejectionReason.ALL_ROWS_REJECTED, "EMPTY_INPUT"
        )
    if not admitted.rows:
        return TrainingRejected(
            TrainingRejectionReason.ALL_ROWS_REJECTED, "0 admitted rows"
        )

    required = _required_rows(policy)
    if len(admitted.rows) < required:
        return TrainingRejected(
            TrainingRejectionReason.INSUFFICIENT_TRAINING_ROWS,
            f"admitted={len(admitted.rows)} required={required}",
        )
    return admitted


@dataclass(frozen=True)
class _OofAndSpace:
    oof: OutOfFoldBuilt
    space: AwardRateFeatureSpace
    accounting: RejectedRowAccounting


def _oof_error(
    oof_outcome: OutOfFoldNoObservations | OutOfFoldTrainerFailed,
) -> TrainingRejected:
    if isinstance(oof_outcome, OutOfFoldNoObservations):
        return TrainingRejected(
            TrainingRejectionReason.NO_OBSERVATIONS, f"fold={oof_outcome.fold_index}"
        )
    return TrainingRejected(TrainingRejectionReason.TRAINER_ERROR, oof_outcome.detail)


def _build_oof_and_space(
    admitted: AdmittedCorpus,
    spec: TrainingSpec,
    policy: TrainingPolicy,
    trainer: TrainerLike,
) -> _OofAndSpace | TrainingRejected:
    """out-of-fold 조립(④) + 전 구간 인코딩(④) + 최소 표본 **재게이트**(③, code-reviewer
    PR #13 HIGH-1). `admit_corpus`를 통과한 행 수는 「구조적으로 유효한 행 수」일 뿐,
    `build_row`가 `base_amount`/`denominator_source` 결측으로 추가로 떨어뜨린 행은
    `oof_outcome.admitted_rows`에 반영된다 — scope ③ 「승인 행이 하한 미만」의 「승인
    행」은 이 행렬에 실제로 실린 행이지 admission 직후 행이 아니다."""
    oof_outcome = out_of_fold_matrix_and_residuals(
        admitted.rows,
        folds=spec.encoding_folds,
        seed=spec.seed,
        policy=SHIPPED_ENCODING_POLICY,
        trainer=trainer,
        hyperparameters=spec.hyperparameters,
        num_boost_round=spec.num_boost_round,
    )
    if isinstance(oof_outcome, OutOfFoldNoObservations | OutOfFoldTrainerFailed):
        return _oof_error(oof_outcome)

    full_accounting = with_missing_fact_rejections(
        admitted.rejected, oof_outcome.rejected_rows
    )
    if not oof_outcome.admitted_rows:
        return TrainingRejected(
            TrainingRejectionReason.ALL_ROWS_REJECTED, "0 rows built"
        )

    required = _required_rows(policy)
    if len(oof_outcome.admitted_rows) < required:
        return TrainingRejected(
            TrainingRejectionReason.INSUFFICIENT_TRAINING_ROWS,
            f"admitted={len(oof_outcome.admitted_rows)} required={required}",
        )

    full_space_outcome = full_corpus_feature_space(
        oof_outcome.admitted_rows, SHIPPED_ENCODING_POLICY
    )
    if isinstance(full_space_outcome, NoObservations):
        return TrainingRejected(TrainingRejectionReason.NO_OBSERVATIONS, "full-corpus")

    return _OofAndSpace(
        oof=oof_outcome, space=full_space_outcome, accounting=full_accounting
    )


def _build_feature_manifest(space: AwardRateFeatureSpace) -> FeatureManifest:
    encoding = space.agency_encoding
    return FeatureManifest(
        schema_version=FEATURE_SCHEMA_V2.version,
        columns=tuple(
            ManifestColumn(name=column.name, kind=column.kind.value)
            for column in FEATURE_SCHEMA_V2.columns
        ),
        categories=space.categories.values,
        denominator_sources=space.denominator_sources.values,
        agency_means=tuple(
            ManifestAgencyMean(agency=agency, category=category, mean=mean, count=count)
            for (agency, category), (mean, count) in encoding.agency_means.items()
        ),
        category_means=tuple(
            ManifestCategoryMean(category=category, mean=mean)
            for category, mean in encoding.category_means.items()
        ),
        global_mean=encoding.global_mean,
        agency_prior_strength=SHIPPED_ENCODING_POLICY.agency_prior_strength,
        category_prior_strength=SHIPPED_ENCODING_POLICY.category_prior_strength,
    )


def _assemble_trained_artifact(
    dataset: LoadedDataset,
    spec: TrainingSpec,
    policy: TrainingPolicy,
    code_version: CodeVersion,
    prepared: _OofAndSpace,
    booster: BoosterLike,
) -> TrainedArtifact:
    residual = compute_residual_std(prepared.oof.residuals, floor=spec.min_residual_std)
    return TrainedArtifact(
        booster=booster,
        feature_manifest=_build_feature_manifest(prepared.space),
        residual_std=residual,
        training_row_count=len(prepared.oof.admitted_rows),
        sample_scope=dataset.manifest.sample_scope,
        feed_origin_only=dataset.manifest.feed_origin_only,
        dataset_id=dataset.manifest.dataset_id,
        code_version=code_version.value,
        training_spec_version=spec.version,
        training_spec_checksum=spec_checksum(spec),
        training_policy_version=policy.version,
        reproducibility=ReproducibilityInfo(
            seed=spec.seed,
            num_threads=spec.hyperparameters.num_threads,
            deterministic=spec.hyperparameters.deterministic,
        ),
        rejected_rows=prepared.accounting,
    )


def train_award_rate_gbm(
    dataset: LoadedDataset,
    spec: TrainingSpec,
    policy: TrainingPolicy,
    trainer: TrainerLike,
    code_version: CodeVersion,
) -> TrainedArtifact | TrainingRejected:
    admitted = _admit_and_gate(dataset, policy)
    if isinstance(admitted, TrainingRejected):
        return admitted

    prepared = _build_oof_and_space(admitted, spec, policy, trainer)
    if isinstance(prepared, TrainingRejected):
        return prepared

    final_trained = trainer.train(
        prepared.oof.matrix,
        prepared.oof.labels,
        _FEATURE_NAMES,
        _CATEGORICAL_INDICES,
        spec.hyperparameters,
        spec.seed,
        spec.num_boost_round,
    )
    if isinstance(final_trained, TrainerFailed):
        return TrainingRejected(
            TrainingRejectionReason.TRAINER_ERROR, final_trained.detail
        )

    return _assemble_trained_artifact(
        dataset, spec, policy, code_version, prepared, final_trained
    )
