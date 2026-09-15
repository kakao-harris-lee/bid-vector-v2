"""ml_engine.evaluation — 낙찰률 GBM 홀드아웃 평가 커널(M5/5C-2). 공개 표면 재수출
((2b) 값 획득 축 표가 전수) — 다른 패키지는 이 최상위 이름만 보고 하위 모듈을 직접
import 하지 않는다. 순수 커널(채점·베이스라인·세그먼트·진단·창 정책·판정·report 타입·
정책)만 여기 있다 — 창마다 학습을 부르는 실행기는 `ml_engine.training.holdout`(층
경계, import-linter `training > evaluation > features`)."""

from __future__ import annotations

from ml_engine.evaluation.baselines import (
    GATE_BASELINE_TABLE_NAMES,
    BaselineSpec,
    amount_band_key,
    baseline_specs,
    group_mean_predictions,
)
from ml_engine.evaluation.diagnostics import (
    COVERAGE_COVERED,
    COVERAGE_FALLBACK,
    CategoryCount,
    CoverageSplit,
    StabilitySummary,
    StabilityTrial,
    UnlearnedCell,
    category_counts,
    coverage_splits,
    minimum_detectable_improvement,
    required_row_count,
    summarize_stability,
    unlearned_cells,
)
from ml_engine.evaluation.policy import (
    SHIPPED_EVALUATION_POLICY_VERSION,
    EvaluationPolicy,
    PolicyRejected,
    PolicyRejectionReason,
    load_evaluation_policy,
    policy_checksum,
)
from ml_engine.evaluation.scoring import improvement_ratio, paired_t, rmse_bias_std
from ml_engine.evaluation.windows import (
    HoldoutOverlap,
    InvalidMaturityInput,
    StratifiedRow,
    WeekMaturity,
    WindowExclusion,
    WindowExclusionReason,
    WindowPlan,
    holdout_overlaps,
    indices_in_window,
    plan_evaluation_windows,
)
from ml_engine.evaluation.segments import (
    SegmentScore,
    SegmentSpec,
    regressed_segments,
    segment_scores,
    segment_specs,
)

__all__ = [
    "COVERAGE_COVERED",
    "COVERAGE_FALLBACK",
    "GATE_BASELINE_TABLE_NAMES",
    "SHIPPED_EVALUATION_POLICY_VERSION",
    "BaselineSpec",
    "CategoryCount",
    "CoverageSplit",
    "EvaluationPolicy",
    "HoldoutOverlap",
    "InvalidMaturityInput",
    "PolicyRejected",
    "PolicyRejectionReason",
    "SegmentScore",
    "SegmentSpec",
    "StabilitySummary",
    "StabilityTrial",
    "StratifiedRow",
    "UnlearnedCell",
    "WeekMaturity",
    "WindowExclusion",
    "WindowExclusionReason",
    "WindowPlan",
    "amount_band_key",
    "baseline_specs",
    "category_counts",
    "coverage_splits",
    "group_mean_predictions",
    "holdout_overlaps",
    "improvement_ratio",
    "indices_in_window",
    "load_evaluation_policy",
    "minimum_detectable_improvement",
    "paired_t",
    "plan_evaluation_windows",
    "policy_checksum",
    "regressed_segments",
    "required_row_count",
    "rmse_bias_std",
    "segment_scores",
    "segment_specs",
    "summarize_stability",
    "unlearned_cells",
]
