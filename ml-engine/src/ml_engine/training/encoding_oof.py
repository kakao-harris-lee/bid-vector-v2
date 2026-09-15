"""Reuse: bid-vector/app/services/ml_training/award_rate_gbm.py@ed4b06c

`ml_engine.training.encoding_oof` — 두 인코딩을 이름으로 가른다(scope ④). 학습 행렬의
`agency_encoding` 열은 **폴드별 out-of-fold**(legacy `_out_of_fold_matrix_and_residuals`,
`:248-282`)로, artifact 에 싣는 표는 **전 구간 한 번**(legacy `build_feature_space(rows)`,
`:378`)으로 만든다. 한 함수로 합치지 않는다 — 합치면 학습 누수(전 구간 인코딩을 학습
행렬에) 또는 서빙 커버리지 소실(폴드 인코딩만 아티팩트에) 중 하나가 생긴다(조사 01 §2-4).

폴드는 무작위다(`training/folds.py`) — 시간 누수는 dataset cutoff·5C-2 창 경계가 막고,
이 폴드는 학습 구간 **안**의 인코딩 자기참조만 끊는다(D-5C-4, legacy 그대로).
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from types import MappingProxyType

import numpy as np

from ml_engine.features import (
    FEATURE_SCHEMA_V2,
    AwardRateFeatureSpace,
    AwardRateObservation,
    EncodingOutcome,
    EncodingPolicy,
    FeatureKind,
    MissingFact,
    NoObservations,
    Present,
    RowRejected,
    Vocabulary,
    build_agency_target_encoding,
)
from ml_engine.training.booster import BoosterLike, TrainerFailed, TrainerLike
from ml_engine.training.corpus import TrainingRow
from ml_engine.training.folds import fold_indices
from ml_engine.training.spec import LightGbmHyperparameters

_COLUMN_COUNT = len(FEATURE_SCHEMA_V2.columns)
_FEATURE_NAMES: tuple[str, ...] = tuple(
    column.name for column in FEATURE_SCHEMA_V2.columns
)
_CATEGORICAL_INDICES: tuple[int, ...] = tuple(
    index
    for index, column in enumerate(FEATURE_SCHEMA_V2.columns)
    if column.kind is FeatureKind.CATEGORICAL
)


@dataclass(frozen=True)
class OutOfFoldBuilt:
    """폴드별 OOF 로 조립한 학습 행렬·잔차 — `RowRejected` 행은 빠지고 회계로 남는다.
    `admitted_rows`는 같은 순서로 정렬돼 `full_corpus_feature_space`에 그대로 넘길 수
    있다(같은 admission 판정을 두 번 계산하지 않는다 — RowRejected 여부는 fold 와
    무관하다, base_amount/denominator_source 결측만 본다)."""

    matrix: np.ndarray
    labels: np.ndarray
    residuals: np.ndarray
    admitted_rows: tuple[TrainingRow, ...]
    rejected_rows: MappingProxyType[MissingFact, int]


@dataclass(frozen=True)
class OutOfFoldNoObservations:
    """어느 폴드의 fit-subset 관측이 0건(우회 후보 (17))."""

    fold_index: int


@dataclass(frozen=True)
class OutOfFoldTrainerFailed:
    """trainer 실패 또는 비유한 예측(우회 후보 (7)·(18))."""

    fold_index: int
    detail: str


type OutOfFoldOutcome = (
    OutOfFoldBuilt | OutOfFoldNoObservations | OutOfFoldTrainerFailed
)


def _vocabulary_from(values: Iterable[str]) -> Vocabulary:
    return Vocabulary(tuple(sorted(set(values))))


def _categories_vocabulary(rows: Sequence[TrainingRow]) -> Vocabulary:
    return _vocabulary_from(
        row.facts.category_code.value
        for row in rows
        if isinstance(row.facts.category_code, Present)
    )


def _denominator_sources_vocabulary(rows: Sequence[TrainingRow]) -> Vocabulary:
    return _vocabulary_from(
        row.facts.denominator_source.value
        for row in rows
        if isinstance(row.facts.denominator_source, Present)
    )


def _observations_for(rows: Iterable[TrainingRow]) -> list[AwardRateObservation]:
    """`agency_id`·`category_code`가 둘 다 `Present`인 행만 관측으로 쓴다 — 어느 한쪽이
    결측(wire Missing)이면 `(agency, category)` 키를 만들 수 없다(둘 다 `str` 필수인
    `AwardRateObservation`의 불변식)."""
    observations: list[AwardRateObservation] = []
    for row in rows:
        agency = row.facts.agency_id
        category = row.facts.category_code
        if isinstance(agency, Present) and isinstance(category, Present):
            observations.append(
                AwardRateObservation(
                    agency=agency.value, category=category.value, value=row.label.value
                )
            )
    return observations


@dataclass(frozen=True)
class _FoldUpdate:
    """한 폴드가 채운 held-out 위치들(빈 튜플 = 이 폴드가 아무것도 못 채움 — fit 관측
    없음/모든 held-out 행 거부, legacy `continue` 갈래 계승)."""

    admitted_indices: tuple[int, ...]
    row_values: tuple[tuple[float, ...], ...]
    residual_values: tuple[float, ...]
    missing_fact_rejections: dict[MissingFact, int]


@dataclass(frozen=True)
class _FittedFold:
    """fit-subset 학습이 끝난 부스터 + 그 폴드의 인코딩 공간(held-out 예측에 재사용)."""

    booster: BoosterLike
    space: AwardRateFeatureSpace


def _build_fit_matrix(
    fit_rows: list[TrainingRow], fold_space: AwardRateFeatureSpace
) -> tuple[np.ndarray, np.ndarray] | None:
    """`None` = 모든 fit 행이 `RowRejected`(legacy `if not fit_rows: continue`와 같은
    갈래)."""
    fit_values: list[tuple[float, ...]] = []
    fit_targets: list[float] = []
    for row in fit_rows:
        built = fold_space.build_row(row.facts)
        if isinstance(built, RowRejected):
            continue
        fit_values.append(built.values)
        fit_targets.append(row.label.value)
    if not fit_values:
        return None
    fit_matrix = np.array(fit_values, dtype=float).reshape(
        len(fit_values), _COLUMN_COUNT
    )
    return fit_matrix, np.array(fit_targets, dtype=float)


@dataclass(frozen=True)
class _FoldContext:
    """폴드마다 반복되는 불변 설정 한 벌 — 호출부의 인자 나열을 줄인다."""

    categories: Vocabulary
    denominator_sources: Vocabulary
    policy: EncodingPolicy
    trainer: TrainerLike
    hyperparameters: LightGbmHyperparameters
    num_boost_round: int
    seed: int


def _fit_fold(
    fit_rows: list[TrainingRow], fold_index: int, context: _FoldContext
) -> _FittedFold | OutOfFoldNoObservations | OutOfFoldTrainerFailed | None:
    """`None` = fit 관측은 있으나 학습 행렬이 비었다(`_build_fit_matrix` 참조)."""
    fit_encoding_outcome: EncodingOutcome = build_agency_target_encoding(
        _observations_for(fit_rows), policy=context.policy
    )
    if isinstance(fit_encoding_outcome, NoObservations):
        return OutOfFoldNoObservations(fold_index)

    fold_space = AwardRateFeatureSpace(
        categories=context.categories,
        denominator_sources=context.denominator_sources,
        agency_encoding=fit_encoding_outcome.encoding,
    )

    fit = _build_fit_matrix(fit_rows, fold_space)
    if fit is None:
        return None
    fit_matrix, fit_targets = fit

    trained: BoosterLike | TrainerFailed = context.trainer.train(
        fit_matrix,
        fit_targets,
        _FEATURE_NAMES,
        _CATEGORICAL_INDICES,
        context.hyperparameters,
        context.seed,
        context.num_boost_round,
    )
    if isinstance(trained, TrainerFailed):
        return OutOfFoldTrainerFailed(fold_index, trained.detail)
    return _FittedFold(booster=trained, space=fold_space)


def _predict_held_out(
    rows: Sequence[TrainingRow],
    held_out: np.ndarray,
    fold_index: int,
    fitted: _FittedFold,
) -> _FoldUpdate | OutOfFoldTrainerFailed:
    held_out_values: list[tuple[float, ...]] = []
    held_out_indices: list[int] = []
    missing_fact_rejections: dict[MissingFact, int] = {}
    for index in (int(value) for value in held_out.tolist()):
        built = fitted.space.build_row(rows[index].facts)
        if isinstance(built, RowRejected):
            missing_fact_rejections[built.reason] = (
                missing_fact_rejections.get(built.reason, 0) + 1
            )
            continue
        held_out_values.append(built.values)
        held_out_indices.append(index)
    if not held_out_values:
        return _FoldUpdate((), (), (), missing_fact_rejections)

    held_out_matrix = np.array(held_out_values, dtype=float).reshape(
        len(held_out_values), _COLUMN_COUNT
    )
    predictions = fitted.booster.predict(held_out_matrix)
    if not np.all(np.isfinite(predictions)):
        return OutOfFoldTrainerFailed(fold_index, "NON_FINITE_PREDICTION")

    targets = np.array(
        [rows[index].label.value for index in held_out_indices], dtype=float
    )
    residual_values = tuple((predictions - targets).tolist())
    return _FoldUpdate(
        tuple(held_out_indices),
        tuple(held_out_values),
        residual_values,
        missing_fact_rejections,
    )


def _run_fold(
    rows: Sequence[TrainingRow],
    held_out: np.ndarray,
    fold_index: int,
    context: _FoldContext,
) -> _FoldUpdate | OutOfFoldNoObservations | OutOfFoldTrainerFailed:
    """폴드 하나 처리 — (나머지 행 인코딩+학습, `_fit_fold`) → (held-out 예측·잔차,
    `_predict_held_out`)."""
    held_out_set = {int(index) for index in held_out.tolist()}
    fit_rows = [row for index, row in enumerate(rows) if index not in held_out_set]

    fitted = _fit_fold(fit_rows, fold_index, context)
    if fitted is None:
        return _FoldUpdate((), (), (), {})
    if isinstance(fitted, OutOfFoldNoObservations | OutOfFoldTrainerFailed):
        return fitted
    return _predict_held_out(rows, held_out, fold_index, fitted)


@dataclass
class _Accumulators:
    """폴드 순회가 채우는 가변 상태 한 벌 — 함수 인자 나열을 줄인다(내부 전용, 결과
    타입이 아니다)."""

    matrix: np.ndarray
    residuals: np.ndarray
    targets: np.ndarray
    admitted_mask: np.ndarray
    missing_fact_rejections: dict[MissingFact, int]


def _init_accumulators(rows: Sequence[TrainingRow], row_count: int) -> _Accumulators:
    return _Accumulators(
        matrix=np.zeros((row_count, _COLUMN_COUNT), dtype=float),
        residuals=np.zeros(row_count, dtype=float),
        targets=np.array([row.label.value for row in rows], dtype=float),
        admitted_mask=np.zeros(row_count, dtype=bool),
        missing_fact_rejections={},
    )


def _assemble_built(
    row_count: int, acc: _Accumulators, rows: Sequence[TrainingRow]
) -> OutOfFoldBuilt:
    admitted_indices = [index for index in range(row_count) if acc.admitted_mask[index]]
    empty_matrix = np.zeros((0, _COLUMN_COUNT), dtype=float)
    empty_vector = np.zeros(0, dtype=float)
    return OutOfFoldBuilt(
        matrix=acc.matrix[admitted_indices] if admitted_indices else empty_matrix,
        labels=acc.targets[admitted_indices] if admitted_indices else empty_vector,
        residuals=acc.residuals[admitted_indices] if admitted_indices else empty_vector,
        admitted_rows=tuple(rows[index] for index in admitted_indices),
        rejected_rows=MappingProxyType(acc.missing_fact_rejections),
    )


def _apply_fold_update(update: _FoldUpdate, acc: _Accumulators) -> None:
    for local_index, global_index in enumerate(update.admitted_indices):
        acc.matrix[global_index] = update.row_values[local_index]
        acc.admitted_mask[global_index] = True
        acc.residuals[global_index] = update.residual_values[local_index]
    for reason, count in update.missing_fact_rejections.items():
        acc.missing_fact_rejections[reason] = (
            acc.missing_fact_rejections.get(reason, 0) + count
        )


def out_of_fold_matrix_and_residuals(
    rows: Sequence[TrainingRow],
    *,
    folds: int,
    seed: int,
    policy: EncodingPolicy,
    trainer: TrainerLike,
    hyperparameters: LightGbmHyperparameters,
    num_boost_round: int,
) -> OutOfFoldOutcome:
    """한 번의 폴드 순회로 (a) 나머지 행 인코딩으로 조립한 학습 행렬과 (b) 그 폴드
    부스터의 out-of-fold 잔차를 함께 낸다(legacy `_out_of_fold_matrix_and_residuals`와
    같은 구조, 폴드 하나 처리는 `_run_fold`)."""
    row_count = len(rows)
    context = _FoldContext(
        categories=_categories_vocabulary(rows),
        denominator_sources=_denominator_sources_vocabulary(rows),
        policy=policy,
        trainer=trainer,
        hyperparameters=hyperparameters,
        num_boost_round=num_boost_round,
        seed=seed,
    )
    acc = _init_accumulators(rows, row_count)

    for fold_index, held_out in enumerate(
        fold_indices(row_count, folds=folds, seed=seed)
    ):
        update = _run_fold(rows, held_out, fold_index, context)
        if isinstance(update, OutOfFoldNoObservations | OutOfFoldTrainerFailed):
            return update
        _apply_fold_update(update, acc)

    return _assemble_built(row_count, acc, rows)


def full_corpus_feature_space(
    rows: Sequence[TrainingRow], policy: EncodingPolicy
) -> AwardRateFeatureSpace | NoObservations:
    """artifact 에 실을 전 구간 표(legacy `build_feature_space(rows)`, encoding 인자 없음
    → 전체 학습 구간으로 한 번). `rows`는 이미 admission 을 통과한 행(OOF 의
    `admitted_rows`)이어야 한다 — 이 함수는 admission 을 다시 판정하지 않는다."""
    encoding_outcome: EncodingOutcome = build_agency_target_encoding(
        _observations_for(rows), policy=policy
    )
    if isinstance(encoding_outcome, NoObservations):
        return encoding_outcome
    return AwardRateFeatureSpace(
        categories=_categories_vocabulary(rows),
        denominator_sources=_denominator_sources_vocabulary(rows),
        agency_encoding=encoding_outcome.encoding,
    )


__all__ = [
    "OutOfFoldBuilt",
    "OutOfFoldNoObservations",
    "OutOfFoldOutcome",
    "OutOfFoldTrainerFailed",
    "full_corpus_feature_space",
    "out_of_fold_matrix_and_residuals",
]
