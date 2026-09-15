"""Reuse: bid-vector/app/services/ml_training/award_rate_holdout.py@ed4b06c

`ml_engine.training.holdout` — 홀드아웃 실행기(scope ⑦, training 층, 유일 공개
진입점). legacy `evaluate_award_rate_holdout`/`build_backtest_report`의 오케스트레이션을
이었다: 창마다 `_split_at_window` → 5C-1 `train_award_rate_gbm` 2회(전 층
`train_rows`/게이트 층 `gate_train`) → `evaluation/*` 순수 커널로 채점·진단·판정 →
`EvaluationReportV1`.

창 하나의 실행 세부(분할·학습은 `_holdout_fit.py`, 채점·안정성 sweep 은
`_holdout_window.py`)는 형제 파일 둘(비공개 내부 모듈)에 있다 — 설계 래칫
`file_loc_soft_limit`(500줄) 준수를 위한 분리이고, `pyproject.toml`
`[tool.design-ratchet]` allowlist 편집(팀장 소관, 「필요가 생기면 멈추고 보고」
지시)을 피하는 대안이다. checklist.md 「계약과 어긋나 판단이 필요했던 자리」에 등재.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass
from enum import StrEnum

from ml_engine.evaluation import (
    CategoryCount,
    EvaluationPolicy,
    EvaluationReportV1,
    GateOutcome,
    Promotion,
    StratumCount,
    WeekMaturity,
    WindowExclusion,
    WindowResult,
    category_counts,
    derive_promotion,
    holdout_overlaps,
    indices_in_window,
    plan_evaluation_windows,
)
from ml_engine.evaluation import (
    policy_checksum as evaluation_policy_checksum,
)
from ml_engine.evaluation.windows import InvalidMaturityInput
from ml_engine.training._holdout_fit import WindowSkip
from ml_engine.training._holdout_window import evaluate_one_window
from ml_engine.training.booster import TrainerLike
from ml_engine.training.corpus import CorpusRejected, TrainingRow, admit_corpus
from ml_engine.training.dataset import LoadedDataset
from ml_engine.training.policy import TrainingPolicy
from ml_engine.training.spec import TrainingSpec, spec_checksum
from ml_engine.training.train import CodeVersion


class HoldoutRejectionReason(StrEnum):
    """실행 전체를 거부하는 사유(창 단위 실패는 `WindowExclusionReason.TRAINING_REJECTED`
    로 흡수된다 — 이 enum 은 실행 자체가 성립하지 않는 경우만)."""

    EMPTY_SIDE = "EMPTY_SIDE"
    INVALID_MATURITY_INPUT = "INVALID_MATURITY_INPUT"


@dataclass(frozen=True)
class HoldoutRejected:
    reason: HoldoutRejectionReason
    detail: str


def _corpus_profile(
    rows: tuple[TrainingRow, ...],
) -> tuple[str | None, str | None, tuple[StratumCount, ...], tuple[CategoryCount, ...]]:
    if not rows:
        return None, None, (), ()
    opened_ats = [row.opened_at for row in rows]
    strata: dict[str, int] = {}
    for row in rows:
        strata[row.stratum] = strata.get(row.stratum, 0) + 1
    stratum_counts = tuple(
        StratumCount(stratum=name, row_count=count)
        for name, count in sorted(strata.items())
    )
    categories = tuple(category_counts([row.facts for row in rows]))
    return (
        min(opened_ats).isoformat(),
        max(opened_ats).isoformat(),
        stratum_counts,
        categories,
    )


@dataclass(frozen=True)
class _WindowsOutcome:
    results: tuple[WindowResult, ...]
    excluded: tuple[WindowExclusion, ...]


def _evaluate_windows(
    plan_selected: tuple[WeekMaturity, ...],
    plan_excluded: tuple[WindowExclusion, ...],
    ordered_rows: tuple[TrainingRow, ...],
    dataset: LoadedDataset,
    spec: TrainingSpec,
    training_policy: TrainingPolicy,
    evaluation_policy: EvaluationPolicy,
    trainer: TrainerLike,
    code_version: CodeVersion,
) -> _WindowsOutcome:
    """선택된 창마다 `evaluate_one_window`를 불러 성공/실패를 가른다 — 실패는 창
    제외 목록(`TRAINING_REJECTED`)으로 흡수한다(design review (1))."""
    results: list[WindowResult] = []
    excluded: list[WindowExclusion] = list(plan_excluded)
    for window in plan_selected:
        outcome_or_skip = evaluate_one_window(
            window,
            ordered_rows,
            dataset,
            spec,
            training_policy,
            evaluation_policy,
            trainer,
            code_version,
        )
        if isinstance(outcome_or_skip, WindowSkip):
            excluded.append(
                WindowExclusion(
                    window=window,
                    reason=outcome_or_skip.reason,
                    evaluation_row_count=len(
                        indices_in_window(
                            ordered_rows, window, stratum=evaluation_policy.gate_stratum
                        )
                    ),
                )
            )
            continue
        results.append(outcome_or_skip.result)
    excluded.sort(key=lambda item: item.window.start)
    return _WindowsOutcome(results=tuple(results), excluded=tuple(excluded))


def _unaccounted_row_count(
    ordered_rows: tuple[TrainingRow, ...],
    plan_selected: tuple[WeekMaturity, ...],
    plan_excluded: tuple[WindowExclusion, ...],
    gate_stratum: str,
    window_results: tuple[WindowResult, ...],
) -> int:
    """회계 불변식의 계측기 — 선택+제외 창의 합집합 밖 게이트 층 행 수(legacy
    `_unaccounted_row_count`와 같은 정의) **더하기** buildability 로 버려진 행
    (verifier r1 H-1 회계식 정정). 창 소속(구조적 멤버십)만으로 「회계됨」을
    선언하지 않는다 — 창에 속했지만 채점되지 못한 행은 `WindowResult.dropped_rows`
    로 개별 공시되는 동시에 이 합계에도 반영돼야, 「이 report 가 놓친 행」이라는
    이 필드의 원래 취지가 지켜진다. 피드 출처 모드에서 dropped 가 0 이면 legacy 와
    같은 값(0)이 나온다."""
    accounted = sum(
        len(indices_in_window(ordered_rows, window, stratum=gate_stratum))
        for window in (*plan_selected, *(item.window for item in plan_excluded))
    )
    stratum_row_count = sum(1 for row in ordered_rows if row.stratum == gate_stratum)
    dropped_total = sum(
        item.row_count for result in window_results for item in result.dropped_rows
    )
    return max(stratum_row_count - accounted + dropped_total, 0)


def _assemble_report(
    dataset: LoadedDataset,
    spec: TrainingSpec,
    evaluation_policy: EvaluationPolicy,
    ordered_rows: tuple[TrainingRow, ...],
    plan_selected: tuple[WeekMaturity, ...],
    windows_outcome: _WindowsOutcome,
) -> EvaluationReportV1:
    opened_first, opened_last, strata, categories = _corpus_profile(ordered_rows)
    latest = windows_outcome.results[-1] if windows_outcome.results else None
    latest_outcome: GateOutcome | None = latest.outcome if latest is not None else None
    promotion: Promotion = derive_promotion(
        latest_outcome, window_start=latest.window_start if latest is not None else None
    )
    return EvaluationReportV1(
        report_schema_version="evaluation-report-v1",
        policy_version=evaluation_policy.version,
        policy_checksum=evaluation_policy_checksum(evaluation_policy),
        training_spec_version=spec.version,
        training_spec_checksum=spec_checksum(spec),
        feature_schema_version=dataset.manifest.feature_schema_version,
        dataset_id=dataset.manifest.dataset_id,
        corpus_row_count=len(ordered_rows),
        corpus_opened_at_first=opened_first,
        corpus_opened_at_last=opened_last,
        feed_origin_only=dataset.manifest.feed_origin_only,
        corpus_strata=strata,
        corpus_categories=categories,
        windows=windows_outcome.results,
        excluded_windows=windows_outcome.excluded,
        holdout_overlaps=tuple(
            holdout_overlaps(
                ordered_rows, plan_selected, stratum=evaluation_policy.gate_stratum
            )
        ),
        unaccounted_row_count=_unaccounted_row_count(
            ordered_rows,
            plan_selected,
            windows_outcome.excluded,
            evaluation_policy.gate_stratum,
            windows_outcome.results,
        ),
        stability_seed_count=len(evaluation_policy.stability_seeds),
        promotion=promotion,
    )


def run_holdout(
    dataset: LoadedDataset,
    maturities: Sequence[WeekMaturity],
    spec: TrainingSpec,
    training_policy: TrainingPolicy,
    evaluation_policy: EvaluationPolicy,
    trainer: TrainerLike,
    code_version: CodeVersion,
) -> EvaluationReportV1 | HoldoutRejected:
    """유일 실행 진입점(scope ⑦, (2b) 표) — 창마다 5C-1 `train_award_rate_gbm`을 불러
    `EvaluationReportV1`을 조립한다. 임계·seed 목록·창 수를 낱개 인자로 받지 않는다 —
    전부 `evaluation_policy`/`spec`/`training_policy`에서만 온다."""
    admitted = admit_corpus(dataset.raw_rows)
    if isinstance(admitted, CorpusRejected) or not admitted.rows:
        return HoldoutRejected(HoldoutRejectionReason.EMPTY_SIDE, "빈 코퍼스")

    ordered_rows = tuple(sorted(admitted.rows, key=lambda row: row.opened_at))

    plan = plan_evaluation_windows(ordered_rows, maturities, evaluation_policy)
    if isinstance(plan, InvalidMaturityInput):
        return HoldoutRejected(
            HoldoutRejectionReason.INVALID_MATURITY_INPUT, plan.detail
        )

    windows_outcome = _evaluate_windows(
        plan.selected,
        plan.excluded,
        ordered_rows,
        dataset,
        spec,
        training_policy,
        evaluation_policy,
        trainer,
        code_version,
    )
    return _assemble_report(
        dataset, spec, evaluation_policy, ordered_rows, plan.selected, windows_outcome
    )
