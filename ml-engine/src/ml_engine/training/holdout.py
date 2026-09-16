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

from collections.abc import Callable, Sequence
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
from ml_engine.training._holdout_fit import WindowSkip, WindowSuccess
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
    ACCOUNTING_MISMATCH = "ACCOUNTING_MISMATCH"
    """verifier r2 H-2r — `_unaccounted_row_count`가 음수로 나오면(회계 결함,
    도달해서는 안 되는 상태) `max(…, 0)`으로 접어 감추는 대신 실행 자체를 거부한다
    (결과 타입, raw 예외 아님)."""


@dataclass(frozen=True)
class HoldoutRejected:
    reason: HoldoutRejectionReason
    detail: str


@dataclass(frozen=True)
class HoldoutCancelled:
    """M5/5E-3 D-5E3-3 — `should_stop`이 창 처리 중 참이 되어 실행이 중단됐다(결과
    타입, 예외 아님). `completed_windows`는 중단 전에 **처리를 끝낸**(학습이
    실패해 건너뛴 창도 포함, 학습에 성공한 창만은 아니다) 창 수다 — 부분 보고서를
    조립하지 않는다(우회 후보 (4): 이 타입을 `PipelineFailed`로 매핑하면 FAILED 로
    위장하는 것이므로 호출부는 `PipelineCancelled`로만 옮긴다). verifier r1 LOW-1 —
    이전 문면은 「학습까지 끝난」이라 적어 실제 계수 대상(성공·실패 무관, 루프가
    처리한 창)과 어긋났다. 현재 production 소비자는 없다(`app/pipeline.py`가 이
    값을 버리고 `PipelineCancelled()`만 낸다)."""

    completed_windows: int


def _never_stop() -> bool:
    return False


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
    succeeded_windows: tuple[WeekMaturity, ...]
    """verifier r2 H-2r — `results`(`WindowResult`)는 원 `WeekMaturity`를 담지
    않아 회계 계산에 못 쓴다. 성공한 창 자체를 `results`와 같은 순서로 별도 보관해,
    `_unaccounted_row_count`가 `plan_selected`(skip 뒤에도 그대로인 계획 통과분)
    대신 이 **서로소** 집합을 쓰게 한다."""


def _exclusion_from_skip(
    window: WeekMaturity,
    skip: WindowSkip,
    ordered_rows: tuple[TrainingRow, ...],
    gate_stratum: str,
) -> WindowExclusion:
    """verifier r2 M-1r — `WindowSkip`의 구조화 필드(`build_split`이 이미 계산한
    buildable 수·dropped 사유별 계수)를 `WindowExclusion`으로 옮긴다(`_evaluate_
    windows`를 50줄 안에 두려는 분리, design ratchet)."""
    return WindowExclusion(
        window=window,
        reason=skip.reason,
        evaluation_row_count=len(
            indices_in_window(ordered_rows, window, stratum=gate_stratum)
        ),
        buildable_row_count=skip.buildable_row_count,
        dropped_rows=skip.dropped_rows,
    )


def _record_window_outcome(
    window: WeekMaturity,
    outcome_or_skip: WindowSuccess | WindowSkip,
    ordered_rows: tuple[TrainingRow, ...],
    gate_stratum: str,
    results: list[WindowResult],
    succeeded_windows: list[WeekMaturity],
    excluded: list[WindowExclusion],
) -> None:
    """창 하나의 처리 결과를 `results`/`excluded` 중 정확히 한쪽에 담는다 —
    `_evaluate_windows`를 design ratchet(50줄) 안에 두려는 분리(D-5E3-3 신설
    `should_stop` 확인이 그 여유를 다 썼다)."""
    if isinstance(outcome_or_skip, WindowSkip):
        excluded.append(
            _exclusion_from_skip(window, outcome_or_skip, ordered_rows, gate_stratum)
        )
        return
    results.append(outcome_or_skip.result)
    succeeded_windows.append(window)


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
    should_stop: Callable[[], bool],
) -> _WindowsOutcome | HoldoutCancelled:
    """창마다 `evaluate_one_window`를 불러 성공/실패를 가른다(실패는
    `TRAINING_REJECTED`로 흡수, design review (1)) — `results`/`excluded` 는
    서로소(H-2r). D-5E3-3: 학습 전 `should_stop()`을 확인해 참이면 `HoldoutCancelled`
    를 즉시 반환한다(부분 조립 없음, 우회 (3))."""
    results: list[WindowResult] = []
    succeeded_windows: list[WeekMaturity] = []
    excluded: list[WindowExclusion] = list(plan_excluded)
    for completed, window in enumerate(plan_selected):
        if should_stop():
            return HoldoutCancelled(completed_windows=completed)
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
        _record_window_outcome(
            window,
            outcome_or_skip,
            ordered_rows,
            evaluation_policy.gate_stratum,
            results,
            succeeded_windows,
            excluded,
        )
    excluded.sort(key=lambda item: item.window.start)
    return _WindowsOutcome(
        results=tuple(results),
        excluded=tuple(excluded),
        succeeded_windows=tuple(succeeded_windows),
    )


def _unaccounted_row_count(
    ordered_rows: tuple[TrainingRow, ...],
    succeeded_windows: tuple[WeekMaturity, ...],
    excluded: tuple[WindowExclusion, ...],
    gate_stratum: str,
    window_results: tuple[WindowResult, ...],
) -> int:
    """회계 불변식의 계측기 — 성공한 창 + 제외된 창(계획 단계·실행 단계 skip 전부,
    `excluded`가 이미 합쳐 담는다)의 합집합 밖 게이트 층 행 수(legacy
    `_unaccounted_row_count`와 같은 정의) **더하기** buildability 로 버려진 행
    (verifier r1 H-1 회계식). 창 소속(구조적 멤버십)만으로 「회계됨」을 선언하지
    않는다 — 창에 속했지만 채점되지 못한 행은 `WindowResult.dropped_rows`로 개별
    공시되는 동시에 이 합계에도 반영돼야, 「이 report 가 놓친 행」이라는 이 필드의
    원래 취지가 지켜진다.

    verifier r2 H-2r — 이전 시그니처는 `plan_selected`(계획 통과분, **실행 단계에서
    skip 된 창도 그대로 남아있음**)를 받아, 그 창이 `excluded`에도 다시 잡혀
    이중 계수되고 결과가 음수가 되면 `max(…, 0)`이 조용히 0 으로 접었다.
    `succeeded_windows`(성공한 창만, `excluded`와 서로소)로 바꿔 이중 계수 자체를
    없앤다 — 그래도 음수가 나오면 그것은 clamp 로 감출 값이 아니라
    `HoldoutRejected(ACCOUNTING_MISMATCH)`로 실행을 거부해야 할 신호다(clamp
    제거, 호출부가 부호를 검사한다)."""
    accounted = sum(
        len(indices_in_window(ordered_rows, window, stratum=gate_stratum))
        for window in (*succeeded_windows, *(item.window for item in excluded))
    )
    stratum_row_count = sum(1 for row in ordered_rows if row.stratum == gate_stratum)
    dropped_total = sum(
        item.row_count for result in window_results for item in result.dropped_rows
    )
    return stratum_row_count - accounted + dropped_total


def _unaccounted_or_reject(
    ordered_rows: tuple[TrainingRow, ...],
    evaluation_policy: EvaluationPolicy,
    windows_outcome: _WindowsOutcome,
) -> int | HoldoutRejected:
    """verifier r2 H-2r — `unaccounted_row_count`가 음수면(회계 결함, 서로소 집합
    정정 뒤에도 도달해서는 안 되는 상태) `HoldoutRejected`를 낸다(`max(…, 0)`
    제거의 짝 — clamp 대신 결과 타입). `_assemble_report`를 50줄 안에 두려고
    분리한 헬퍼(design ratchet)."""
    unaccounted = _unaccounted_row_count(
        ordered_rows,
        windows_outcome.succeeded_windows,
        windows_outcome.excluded,
        evaluation_policy.gate_stratum,
        windows_outcome.results,
    )
    if unaccounted < 0:
        return HoldoutRejected(
            HoldoutRejectionReason.ACCOUNTING_MISMATCH,
            f"unaccounted_row_count 가 음수입니다: {unaccounted}",
        )
    return unaccounted


def _assemble_report(
    dataset: LoadedDataset,
    spec: TrainingSpec,
    evaluation_policy: EvaluationPolicy,
    ordered_rows: tuple[TrainingRow, ...],
    windows_outcome: _WindowsOutcome,
) -> EvaluationReportV1 | HoldoutRejected:
    """verifier r3 LOW L-2r — `holdout_overlaps` 는 `windows_outcome.
    succeeded_windows`(서로소 집합)만 본다. 예전에는 `plan_selected`(계획
    통과분, 실행 단계 skip 된 창도 그대로 남음)를 받아 H-2r 이 정정한 회계와
    다른 창 집합을 겹침 측정에 썼다 — `plan_selected` 인자 자체를 없앴다."""
    unaccounted_or_reject = _unaccounted_or_reject(
        ordered_rows, evaluation_policy, windows_outcome
    )
    if isinstance(unaccounted_or_reject, HoldoutRejected):
        return unaccounted_or_reject
    unaccounted = unaccounted_or_reject
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
                ordered_rows,
                windows_outcome.succeeded_windows,
                stratum=evaluation_policy.gate_stratum,
            )
        ),
        unaccounted_row_count=unaccounted,
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
    *,
    should_stop: Callable[[], bool] = _never_stop,
) -> EvaluationReportV1 | HoldoutRejected | HoldoutCancelled:
    """유일 실행 진입점(scope ⑦, (2b) 표) — 창마다 5C-1 `train_award_rate_gbm`을 불러
    `EvaluationReportV1`을 조립한다. 임계·seed 목록·창 수를 낱개 인자로 받지 않는다 —
    전부 `evaluation_policy`/`spec`/`training_policy`에서만 온다.

    M5/5E-3 D-5E3-3 — `should_stop`(기본: 항상 거짓, 기존 호출자·test 무변경)이
    참이 되면 창 루프가 남은 창을 학습하지 않고 `HoldoutCancelled`를 낸다(창 단위
    취소 — 창 하나 안의 LightGBM 학습 자체를 중단하지는 않는다, 알려진 제한).
    `training.jobs`는 여기서 import 하지 않는다 — 취소 확인 함수는 호출부가
    콜러블로 주입한다(층 결합 회피)."""
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
        should_stop,
    )
    if isinstance(windows_outcome, HoldoutCancelled):
        return windows_outcome
    return _assemble_report(
        dataset, spec, evaluation_policy, ordered_rows, windows_outcome
    )
