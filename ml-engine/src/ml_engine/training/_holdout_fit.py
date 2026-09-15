"""Reuse: bid-vector/app/services/ml_training/award_rate_holdout.py@ed4b06c

`ml_engine.training._holdout_fit` — 창 분할 + GBM 두 변형 학습(scope ⑦ 세부). `holdout.py`
의 비공개 내부 모듈이다(public 표면은 `run_holdout`/`HoldoutRejected`뿐) — scope.md 는
파일 하나를 계획했으나 설계 래칫 `file_loc_soft_limit`(500줄) 준수를 위해 창 단위 실행
로직을 형제 파일 여럿으로 분리했다(`pyproject.toml` allowlist 편집은 팀장 소관이라 대신
분리를 택했다 — checklist.md 「계약과 어긋나 판단이 필요했던 자리」).

evaluation 층은 `TrainedArtifact.booster`/`feature_manifest`를 모른다(layers) — 이
모듈이 `feature_manifest`에서 `AwardRateFeatureSpace`를 재구성해 5B `build_row`로
홀드아웃 행렬을 만들고 `booster.predict(matrix)`를 직접 부른다(D-5C2-7).
"""

from __future__ import annotations

import dataclasses
from collections.abc import Sequence
from dataclasses import dataclass

import numpy as np

from ml_engine.evaluation import (
    EvaluationPolicy,
    ModelScore,
    WeekMaturity,
    WindowExclusionReason,
    WindowResult,
    indices_in_window,
    rmse_bias_std,
)
from ml_engine.features import (
    AgencyTargetEncoding,
    AwardRateFeatureSpace,
    FeatureFacts,
    FeatureManifest,
    Present,
    RowRejected,
    Vocabulary,
)
from ml_engine.training.booster import TrainerLike
from ml_engine.training.corpus import TrainingRow
from ml_engine.training.dataset import LoadedDataset, RawTrainingRow
from ml_engine.training.policy import TrainingPolicy
from ml_engine.training.spec import TrainingSpec
from ml_engine.training.train import (
    CodeVersion,
    TrainedArtifact,
    TrainingRejected,
    train_award_rate_gbm,
)


def feature_space_from_manifest(manifest: FeatureManifest) -> AwardRateFeatureSpace:
    """`TrainedArtifact.feature_manifest`에서 홀드아웃 예측에 쓸 `AwardRateFeatureSpace`를
    재구성한다(D-5C2-7) — 재적합이 아니라 학습이 이미 만든 표(카테고리 어휘·분모 출처
    어휘·기관 인코딩 평균)를 그대로 되살린다."""
    agency_means = {
        (entry.agency, entry.category): (entry.mean, entry.count)
        for entry in manifest.agency_means
    }
    category_means = {entry.category: entry.mean for entry in manifest.category_means}
    return AwardRateFeatureSpace(
        categories=Vocabulary(manifest.categories),
        denominator_sources=Vocabulary(manifest.denominator_sources),
        agency_encoding=AgencyTargetEncoding(
            agency_means=agency_means,
            category_means=category_means,
            global_mean=manifest.global_mean,
        ),
    )


def is_buildable(facts: FeatureFacts) -> bool:
    """build_row 가 행 자체를 거부하지 않을 조건 — base_amount·denominator_source 가
    wire Missing 이 아니면 어떤 공간으로도 행을 만들 수 있다(space 무관)."""
    return isinstance(facts.base_amount, Present) and isinstance(
        facts.denominator_source, Present
    )


def matrix_for(space: AwardRateFeatureSpace, rows: Sequence[TrainingRow]) -> np.ndarray:
    """`rows`는 이미 `is_buildable`을 통과한 것으로 가정한다(`RowRejected`가 새면
    프로그래밍 불변식 위반 — 방어적 assert, 도메인 예외 아님)."""
    values: list[tuple[float, ...]] = []
    for row in rows:
        built = space.build_row(row.facts)
        if isinstance(built, RowRejected):
            raise AssertionError(
                f"buildable 로 판정된 행이 build_row 에서 거부됨: {built.reason}"
            )
        values.append(built.values)
    return np.array(values, dtype=float)


def sub_dataset(
    dataset: LoadedDataset, raw_rows: tuple[RawTrainingRow, ...]
) -> LoadedDataset:
    """`raw_rows` 부분집합의 새 `LoadedDataset` — `dataset_id`는 그대로, `row_count`만
    갱신한다(`train_award_rate_gbm`은 `rows_checksum`을 재검증하지 않는다)."""
    manifest = dataclasses.replace(dataset.manifest, row_count=len(raw_rows))
    return LoadedDataset(manifest=manifest, raw_rows=raw_rows)


def stability_seed_order(
    headline_seed: int, policy: EvaluationPolicy
) -> tuple[int, ...]:
    """seed 재채점 순서 — 헤드라인 seed 를 맨 앞에(D-5C2-5, legacy `_stability_seeds`)."""
    return (
        headline_seed,
        *(seed for seed in policy.stability_seeds if seed != headline_seed),
    )


@dataclass(frozen=True)
class WindowSuccess:
    result: WindowResult


@dataclass(frozen=True)
class WindowSkip:
    reason: WindowExclusionReason
    detail: str


@dataclass(frozen=True)
class Split:
    """평가 창 하나에 대한 out-of-time 분할 한 벌(legacy `_HoldoutSplit`과 같은 자리)."""

    gate_train: list[TrainingRow]
    train_rows_all: list[TrainingRow]
    usable_test_rows: list[TrainingRow]
    gate_train_raw: tuple[RawTrainingRow, ...]
    train_rows_raw: tuple[RawTrainingRow, ...]
    targets: np.ndarray
    usable_facts: list[FeatureFacts]
    gate_train_facts: list[FeatureFacts]
    gate_train_labels: np.ndarray
    gate_train_mean: float


def build_split(
    window: WeekMaturity,
    ordered_rows: tuple[TrainingRow, ...],
    dataset: LoadedDataset,
    gate_stratum: str,
) -> Split | WindowSkip:
    """`_split_at_window`(legacy) — 경계 동시각은 평가측(`indices_in_window`가 이미
    `[start, end)` 반개구간을 지킨다), 학습은 `opened_at < window.start`."""
    gate_test_all = [
        ordered_rows[index]
        for index in indices_in_window(ordered_rows, window, stratum=gate_stratum)
    ]
    gate_train = [
        row
        for row in ordered_rows
        if row.stratum == gate_stratum and row.opened_at < window.start
    ]
    train_rows_all = [row for row in ordered_rows if row.opened_at < window.start]
    if not gate_train or not gate_test_all:
        # 창 정책이 이미 NO_TRAINING_ROWS·INSUFFICIENT_EVALUATION_ROWS 로 걸렀어야
        # 하므로 도달은 방어선이다(조용한 0건 평가 금지, 이중 방어).
        return WindowSkip(WindowExclusionReason.TRAINING_REJECTED, "EMPTY_SIDE")

    usable_test_rows = [row for row in gate_test_all if is_buildable(row.facts)]
    if not usable_test_rows:
        return WindowSkip(
            WindowExclusionReason.TRAINING_REJECTED,
            "평가 행렬을 만들 수 있는 행이 없음",
        )

    gate_train_labels = np.array([row.label.value for row in gate_train])
    return Split(
        gate_train=gate_train,
        train_rows_all=train_rows_all,
        usable_test_rows=usable_test_rows,
        gate_train_raw=tuple(
            row
            for row in dataset.raw_rows
            if row.stratum == gate_stratum and row.opened_at < window.start
        ),
        train_rows_raw=tuple(
            row for row in dataset.raw_rows if row.opened_at < window.start
        ),
        targets=np.array([row.label.value for row in usable_test_rows]),
        usable_facts=[row.facts for row in usable_test_rows],
        gate_train_facts=[row.facts for row in gate_train],
        gate_train_labels=gate_train_labels,
        gate_train_mean=float(np.mean(gate_train_labels)),
    )


@dataclass(frozen=True)
class ModelFit:
    trained_all: TrainedArtifact
    predictions_all: np.ndarray
    model_score: ModelScore
    conservative_score: ModelScore | None


def _fit_conservative_variant(
    dataset: LoadedDataset,
    split: Split,
    spec: TrainingSpec,
    training_policy: TrainingPolicy,
    trainer: TrainerLike,
    code_version: CodeVersion,
) -> ModelScore | None:
    """게이트 층(`gate_train`)만으로 학습한 보수 변형 — 참고용, 판정에는 쓰지 않는다.
    학습이 실패해도 창 전체를 막지 않고 `None`으로 생략한다(알려진 제한)."""
    trained_gate = train_award_rate_gbm(
        sub_dataset(dataset, split.gate_train_raw),
        spec,
        training_policy,
        trainer,
        code_version,
    )
    if isinstance(trained_gate, TrainingRejected):
        return None
    space_gate = feature_space_from_manifest(trained_gate.feature_manifest)
    predictions_gate = trained_gate.booster.predict(
        matrix_for(space_gate, split.usable_test_rows)
    )
    rmse_g, bias_g, std_g = rmse_bias_std(predictions_gate, split.targets)
    return ModelScore(
        name="gbm_gate_stratum_only", rmse=rmse_g, bias=bias_g, residual_std=std_g
    )


def fit_models(
    dataset: LoadedDataset,
    split: Split,
    spec: TrainingSpec,
    training_policy: TrainingPolicy,
    evaluation_policy: EvaluationPolicy,
    trainer: TrainerLike,
    code_version: CodeVersion,
) -> ModelFit | WindowSkip:
    """창마다 5C-1 `train_award_rate_gbm` 2회 — 전 층(`gbm_all_strata`, 게이트 후보)과
    게이트 층(`gbm_gate_stratum_only`, 보수 변형·참고용)."""
    trained_all = train_award_rate_gbm(
        sub_dataset(dataset, split.train_rows_raw),
        spec,
        training_policy,
        trainer,
        code_version,
    )
    if isinstance(trained_all, TrainingRejected):
        return WindowSkip(
            WindowExclusionReason.TRAINING_REJECTED, trained_all.reason.value
        )

    space_all = feature_space_from_manifest(trained_all.feature_manifest)
    predictions_all = trained_all.booster.predict(
        matrix_for(space_all, split.usable_test_rows)
    )
    model_rmse, model_bias, model_std = rmse_bias_std(predictions_all, split.targets)

    conservative_score = _fit_conservative_variant(
        dataset, split, spec, training_policy, trainer, code_version
    )

    return ModelFit(
        trained_all=trained_all,
        predictions_all=predictions_all,
        model_score=ModelScore(
            name=evaluation_policy.gate_model,
            rmse=model_rmse,
            bias=model_bias,
            residual_std=model_std,
        ),
        conservative_score=conservative_score,
    )
