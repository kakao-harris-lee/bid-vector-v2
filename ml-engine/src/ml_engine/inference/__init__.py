"""ml_engine.inference — model predict adapter·순수 커널 이식(5D, scope.md D-M5-9 (a) —
반사 KDE·곡선 빌더는 도달 경로가 생길 때). 수학만 이식한다 — 업무 법정 하한·자격·최종
결정은 여기 없다(milestone-5.md 범위 밖).

공개 표면 재수출 — 이 패키지의 최상위 이름만 보고 하위 모듈(`ml_engine.inference.results`
등)을 직접 import하지 않는다(5B `features/__init__.py`와 같은 관례).
"""

from __future__ import annotations

from ml_engine.inference.assessment import (
    AssessmentPosterior,
    AssessmentProvenance,
    AssessmentSample,
    CleanAssessmentSample,
    LevelObservation,
    LevelWeights,
    admit_clean,
    aggregate_level_observation,
    resolve_assessment_posterior,
)
from ml_engine.inference.maturity import (
    MaturityWindow,
    NoObservation,
    Observed,
    SettlementObservation,
    build_weekly_maturity,
    resolve_maturity,
    week_start_utc,
)
from ml_engine.inference.policy import (
    SHIPPED_INFERENCE_POLICY_VERSION,
    InferencePolicy,
    PolicyRejected,
    load_inference_policy,
)
from ml_engine.inference.predict import (
    Available,
    BoosterLike,
    predict_bid_rates,
    segment_availability,
)
from ml_engine.inference.reserve_draw import (
    DrawMeanDistribution,
    draw_mean_moments,
    exact_draw_mean_distribution,
)
from ml_engine.inference.results import (
    Candidate,
    CandidateLabel,
    Diagnostics,
    IntervalSource,
    PriceFitness,
    SegmentSupport,
    Success,
    Uncertainty,
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)
from ml_engine.inference.scenario import build_scenario_candidates, resolve_uncertainty

__all__ = [
    "SHIPPED_INFERENCE_POLICY_VERSION",
    "AssessmentPosterior",
    "AssessmentProvenance",
    "AssessmentSample",
    "Available",
    "BoosterLike",
    "Candidate",
    "CandidateLabel",
    "CleanAssessmentSample",
    "Diagnostics",
    "DrawMeanDistribution",
    "InferencePolicy",
    "IntervalSource",
    "LevelObservation",
    "LevelWeights",
    "MaturityWindow",
    "NoObservation",
    "Observed",
    "PolicyRejected",
    "PriceFitness",
    "SegmentSupport",
    "SettlementObservation",
    "Success",
    "Uncertainty",
    "Unmeasurable",
    "UnmeasurableDetail",
    "UnmeasurableReason",
    "admit_clean",
    "aggregate_level_observation",
    "build_scenario_candidates",
    "build_weekly_maturity",
    "draw_mean_moments",
    "exact_draw_mean_distribution",
    "load_inference_policy",
    "predict_bid_rates",
    "resolve_assessment_posterior",
    "resolve_maturity",
    "resolve_uncertainty",
    "segment_availability",
    "week_start_utc",
]
