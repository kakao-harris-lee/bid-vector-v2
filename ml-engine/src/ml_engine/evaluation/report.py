"""Reuse: bid-vector/app/services/ml_training/award_rate_backtest_report.py@ed4b06c

`ml_engine.evaluation.report` — evaluation report + 승격 측정(scope ⑧). legacy
`BacktestReport`(공시 전용 조립, `:70-160`)를 이었다. 여기서 계산하지 않는다 — 창마다
잰 결과를 한 문서로 편다.

`Promotion`(신규 — legacy `gate_passed_at_latest_window`/`_all_origins` bool 쌍)은
**latest-window 하나**의 `GateOutcome`에서만 파생된다(D-5C2-6, 2C ⑧ 「참조는 입력일
뿐 승격이 아니다」) — `all_origins` 판정은 창별 결과(`windows`)를 통해 진단으로만
남는다. canonical JSON 은 5B `features/manifest.py::canonical_json`과 같은 규칙(키
정렬·구분자 `(",", ":")`·`allow_nan=False`)이지만 대상 타입이 달라 **새로** 쓴다(5B
파일은 편집하지 않는다, 설계 검토 (4))."""

from __future__ import annotations

import hashlib
import json
import math
from dataclasses import dataclass

from ml_engine.evaluation.diagnostics import (
    CategoryCount,
    CoverageSplit,
    StabilitySummary,
    StabilityTrial,
    UnlearnedCell,
)
from ml_engine.evaluation.segments import SegmentScore
from ml_engine.evaluation.verdict import (
    GateOutcome,
    NotEvaluable,
    NotEvaluableReason,
    Passed,
)
from ml_engine.evaluation.windows import HoldoutOverlap, WindowExclusion
from ml_engine.features import CanonicalizationRejected, MissingFact, NonFiniteValue

type _JsonValue = (
    str | int | float | bool | list[_JsonValue] | dict[str, _JsonValue] | None
)
"""재귀 JSON 값 — named alias 를 써 설계 래칫의 약한 경계 판정(`dict`/`Any` 함수
경계)을 피한다(5C-1 `artifact_writer.py::_JsonValue`와 같은 근거 — training 층
private 별칭을 evaluation 이 import 하지 않고 같은 패턴으로 새로 선언한다)."""


@dataclass(frozen=True)
class ModelScore:
    """베이스라인·GBM 변형 공용 성적 행. `coverage`는 베이스라인에만(그룹 평균이 자기
    셀로 예측한 비율) — GBM 행은 `None`."""

    name: str
    rmse: float
    bias: float
    residual_std: float
    coverage: float | None = None


@dataclass(frozen=True)
class StratumCount:
    stratum: str
    row_count: int


@dataclass(frozen=True)
class DroppedRowCount:
    """verifier r1 H-1 — 창 안 구조적 행 중 `build_row`가 만들 수 없어(base_amount·
    denominator_source 가 wire `Missing`) 채점에서 빠진 행의 사유별 계수. `reason`은
    5B `MissingFact`(evaluation 이 이미 `features` 층을 아는 것과 같은 경계 — training
    층의 `RejectedRowAccounting`을 직접 담지 않는다, layers)."""

    reason: MissingFact
    row_count: int


@dataclass(frozen=True)
class WindowResult:
    """한 평가 창에서의 전체 비교 + 게이트 판정(legacy `AwardRateHoldoutReport`).
    창별 `release_id`를 싣는다 — 5C-1 결정적 파생이라 재현성 확인에 쓰인다."""

    window_start: str
    window_end: str
    window_maturity: float | None
    """`None`이면 그 창의 `opened_count == 0`이었다(측정 불가, D-5C2-2)."""
    window_opened_count: int
    window_settled_count: int
    cutoff_at: str
    train_row_count: int
    gate_train_row_count: int
    gate_test_row_count: int
    train_mean: float
    test_mean: float
    test_std: float
    train_categories: tuple[CategoryCount, ...]
    test_categories: tuple[CategoryCount, ...]
    baselines: tuple[ModelScore, ...]
    models: tuple[ModelScore, ...]
    segments: tuple[SegmentScore, ...]
    coverage: tuple[CoverageSplit, ...]
    unlearned_baseline_cells: tuple[UnlearnedCell, ...]
    dropped_rows: tuple[DroppedRowCount, ...]
    """창 안 구조적 행 중 buildability 로 버려진 행(verifier r1 H-1) —
    `gate_test_row_count`(채점된 행)와 합치면 창의 구조적 행 수가 된다."""
    outcome: GateOutcome
    stability: StabilitySummary
    release_id: str


@dataclass(frozen=True)
class Promotable:
    """`Passed`에서만 생성된다(`derive_promotion`이 유일 생성 경로)."""

    window_start: str


@dataclass(frozen=True)
class NotPromotable:
    reasons: tuple[str, ...]


@dataclass(frozen=True)
class PromotionNotEvaluable:
    """`GateOutcome.NotEvaluable`의 사유를 그대로 옮긴다 — 「측정이 없다」를
    「승격 불가」와 구별한다."""

    reason: NotEvaluableReason


type Promotion = Promotable | NotPromotable | PromotionNotEvaluable


def derive_promotion(
    latest: GateOutcome | None, *, window_start: str | None
) -> Promotion:
    """latest-window 하나의 `GateOutcome`에서 승격 측정을 파생한다(D-5C2-6). `latest`가
    `None`이면(성숙 창이 하나도 없었다) `NO_EVALUABLE_WINDOW`."""
    if latest is None:
        return PromotionNotEvaluable(NotEvaluableReason.NO_EVALUABLE_WINDOW)
    if isinstance(latest, NotEvaluable):
        return PromotionNotEvaluable(latest.reason)
    if isinstance(latest, Passed):
        if window_start is None:
            raise ValueError("Passed 판정에는 window_start 가 있어야 합니다.")
        return Promotable(window_start=window_start)
    return NotPromotable(reasons=("gate_failed",))


@dataclass(frozen=True)
class EvaluationReportV1:
    """평가 실행 전체의 결과(scope ⑧). `policy_checksum`·`training_spec_checksum`을
    실어 「어떤 정책·spec 으로 판정했는지」가 report 자체에 남는다(위조 방어를 report
    로 옮긴 자리, 설계 검토 (2b))."""

    report_schema_version: str
    policy_version: str
    policy_checksum: str
    training_spec_version: str
    training_spec_checksum: str
    feature_schema_version: str
    dataset_id: str
    corpus_row_count: int
    corpus_opened_at_first: str | None
    corpus_opened_at_last: str | None
    feed_origin_only: bool
    corpus_strata: tuple[StratumCount, ...]
    corpus_categories: tuple[CategoryCount, ...]
    windows: tuple[WindowResult, ...]
    excluded_windows: tuple[WindowExclusion, ...]
    holdout_overlaps: tuple[HoldoutOverlap, ...]
    unaccounted_row_count: int
    """어느 평가 창에도, 제외 회계에도 잡히지 않은 평가 층 행 수 — 회계 불변식(피드
    출처 모드에서는 0)."""
    stability_seed_count: int
    """0 이면 안정성을 재지 않은 실행이다 — D-5C2-5 는 이 값이 항상 >0 이도록 강제한다
    (끄는 인자가 없다)."""
    promotion: Promotion


def _gate_outcome_json(outcome: GateOutcome) -> _JsonValue:
    if isinstance(outcome, NotEvaluable):
        return {
            "kind": "not_evaluable",
            "reason": outcome.reason.value,
            "required_row_count": outcome.required_row_count,
        }
    kind = "passed" if isinstance(outcome, Passed) else "failed"
    return {
        "kind": kind,
        "baseline_rmse": outcome.baseline_rmse,
        "model_rmse": outcome.model_rmse,
        "improvement_ratio": outcome.improvement_ratio,
        "paired_t": outcome.paired_t,
        "min_detectable_improvement": outcome.min_detectable_improvement,
        "required_row_count": outcome.required_row_count,
    }


def _promotion_json(promotion: Promotion) -> _JsonValue:
    if isinstance(promotion, Promotable):
        return {"kind": "promotable", "window_start": promotion.window_start}
    if isinstance(promotion, NotPromotable):
        return {"kind": "not_promotable", "reasons": list(promotion.reasons)}
    return {"kind": "not_evaluable", "reason": promotion.reason.value}


def _stability_trial_json(trial: StabilityTrial) -> _JsonValue:
    return {
        "seed": trial.seed,
        "improvement_ratio": trial.improvement_ratio,
        "paired_t": trial.paired_t,
        "passed": trial.passed,
    }


def _stability_json(summary: StabilitySummary) -> _JsonValue:
    return {
        "trials": [_stability_trial_json(trial) for trial in summary.trials],
        "passed_count": summary.passed_count,
        "sign_consistent": summary.sign_consistent,
        "verdict_consistent": summary.verdict_consistent,
        "min_improvement_ratio": summary.min_improvement_ratio,
        "max_improvement_ratio": summary.max_improvement_ratio,
        "min_abs_paired_t": summary.min_abs_paired_t,
        "max_abs_paired_t": summary.max_abs_paired_t,
    }


def _category_counts_json(items: tuple[CategoryCount, ...]) -> list[_JsonValue]:
    return [{"category": item.category, "row_count": item.row_count} for item in items]


def _model_score_json(score: ModelScore) -> _JsonValue:
    return {
        "name": score.name,
        "rmse": score.rmse,
        "bias": score.bias,
        "residual_std": score.residual_std,
        "coverage": score.coverage,
    }


def _segment_score_json(score: SegmentScore) -> _JsonValue:
    return {
        "axis": score.axis,
        "segment": score.segment,
        "row_count": score.row_count,
        "baseline_rmse": score.baseline_rmse,
        "baseline_bias": score.baseline_bias,
        "model_rmse": score.model_rmse,
        "model_bias": score.model_bias,
        "model_residual_std": score.model_residual_std,
        "improvement_ratio": score.improvement_ratio,
        "paired_t": score.paired_t,
    }


def _coverage_split_json(split: CoverageSplit) -> _JsonValue:
    return {
        "segment": split.segment,
        "row_count": split.row_count,
        "baseline_rmse": split.baseline_rmse,
        "model_rmse": split.model_rmse,
        "improvement_ratio": split.improvement_ratio,
        "paired_t": split.paired_t,
    }


def _unlearned_cell_json(cell: UnlearnedCell) -> _JsonValue:
    return {"key": cell.key, "row_count": cell.row_count}


def _dropped_row_count_json(item: DroppedRowCount) -> _JsonValue:
    return {"reason": item.reason.value, "row_count": item.row_count}


def _window_result_json(window: WindowResult) -> _JsonValue:
    return {
        "window_start": window.window_start,
        "window_end": window.window_end,
        "window_maturity": window.window_maturity,
        "window_opened_count": window.window_opened_count,
        "window_settled_count": window.window_settled_count,
        "cutoff_at": window.cutoff_at,
        "train_row_count": window.train_row_count,
        "gate_train_row_count": window.gate_train_row_count,
        "gate_test_row_count": window.gate_test_row_count,
        "train_mean": window.train_mean,
        "test_mean": window.test_mean,
        "test_std": window.test_std,
        "train_categories": _category_counts_json(window.train_categories),
        "test_categories": _category_counts_json(window.test_categories),
        "baselines": [_model_score_json(item) for item in window.baselines],
        "models": [_model_score_json(item) for item in window.models],
        "segments": [_segment_score_json(item) for item in window.segments],
        "coverage": [_coverage_split_json(item) for item in window.coverage],
        "unlearned_baseline_cells": [
            _unlearned_cell_json(item) for item in window.unlearned_baseline_cells
        ],
        "dropped_rows": [_dropped_row_count_json(item) for item in window.dropped_rows],
        "outcome": _gate_outcome_json(window.outcome),
        "stability": _stability_json(window.stability),
        "release_id": window.release_id,
    }


def _window_exclusion_json(exclusion: WindowExclusion) -> _JsonValue:
    window = exclusion.window
    return {
        "window_start": window.start.isoformat(),
        "window_end": window.end.isoformat(),
        "window_opened_count": window.opened_count,
        "window_settled_count": window.settled_count,
        "reason": exclusion.reason.value,
        "evaluation_row_count": exclusion.evaluation_row_count,
    }


def _holdout_overlap_json(overlap: HoldoutOverlap) -> _JsonValue:
    return {
        "first_start": overlap.first_start.isoformat(),
        "second_start": overlap.second_start.isoformat(),
        "row_count": overlap.row_count,
    }


def _stratum_counts_json(items: tuple[StratumCount, ...]) -> list[_JsonValue]:
    return [{"stratum": item.stratum, "row_count": item.row_count} for item in items]


def _report_as_json_value(report: EvaluationReportV1) -> _JsonValue:
    return {
        "report_schema_version": report.report_schema_version,
        "policy_version": report.policy_version,
        "policy_checksum": report.policy_checksum,
        "training_spec_version": report.training_spec_version,
        "training_spec_checksum": report.training_spec_checksum,
        "feature_schema_version": report.feature_schema_version,
        "dataset_id": report.dataset_id,
        "corpus_row_count": report.corpus_row_count,
        "corpus_opened_at_first": report.corpus_opened_at_first,
        "corpus_opened_at_last": report.corpus_opened_at_last,
        "feed_origin_only": report.feed_origin_only,
        "corpus_strata": _stratum_counts_json(report.corpus_strata),
        "corpus_categories": _category_counts_json(report.corpus_categories),
        "windows": [_window_result_json(window) for window in report.windows],
        "excluded_windows": [
            _window_exclusion_json(item) for item in report.excluded_windows
        ],
        "holdout_overlaps": [
            _holdout_overlap_json(item) for item in report.holdout_overlaps
        ],
        "unaccounted_row_count": report.unaccounted_row_count,
        "stability_seed_count": report.stability_seed_count,
        "promotion": _promotion_json(report.promotion),
    }


def _first_non_finite_path(value: _JsonValue, path: str) -> str | None:
    """직렬화 전 유한성 선검사(5B `manifest.py::_first_non_finite_path`와 같은 방식) —
    `json.dumps(allow_nan=False)`의 `ValueError`는 어느 필드인지 말하지 않는다."""
    if isinstance(value, float):
        return None if math.isfinite(value) else path
    if isinstance(value, dict):
        for key, item in value.items():
            found = _first_non_finite_path(item, f"{path}.{key}")
            if found is not None:
                return found
        return None
    if isinstance(value, list):
        for index, item in enumerate(value):
            found = _first_non_finite_path(item, f"{path}[{index}]")
            if found is not None:
                return found
        return None
    return None


def canonical_report_bytes(
    report: EvaluationReportV1,
) -> bytes | CanonicalizationRejected:
    """키 정렬·구분자 `(",", ":")`·`allow_nan=False`(5B `canonical_json`과 같은 규칙,
    대상 타입이 달라 새 함수). `NaN`/`Infinity`가 섞이면 결과 타입으로 거부(우회 후보
    (18) — 빈 세그먼트 mean 등에서 비유한 값이 실릴 수 있다)."""
    payload = _report_as_json_value(report)
    non_finite_path = _first_non_finite_path(payload, "$")
    if non_finite_path is not None:
        return CanonicalizationRejected(NonFiniteValue(non_finite_path))
    return json.dumps(
        payload, sort_keys=True, separators=(",", ":"), allow_nan=False
    ).encode("utf-8")


def report_checksum(report: EvaluationReportV1) -> str | CanonicalizationRejected:
    """sha256 hex(소문자 64자), 또는 `canonical_report_bytes`의 거부 전파."""
    canonical = canonical_report_bytes(report)
    if isinstance(canonical, CanonicalizationRejected):
        return canonical
    return hashlib.sha256(canonical).hexdigest()
