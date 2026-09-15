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
from ml_engine.evaluation.policy import (
    SHIPPED_EVALUATION_POLICY_VERSION,
    EvaluationPolicy,
    PolicyRejected,
    PolicyRejectionReason,
    load_evaluation_policy,
    policy_checksum,
)
from ml_engine.evaluation.scoring import improvement_ratio, paired_t, rmse_bias_std
from ml_engine.evaluation.segments import (
    SegmentScore,
    SegmentSpec,
    regressed_segments,
    segment_scores,
    segment_specs,
)

__all__ = [
    "GATE_BASELINE_TABLE_NAMES",
    "SHIPPED_EVALUATION_POLICY_VERSION",
    "BaselineSpec",
    "EvaluationPolicy",
    "PolicyRejected",
    "PolicyRejectionReason",
    "SegmentScore",
    "SegmentSpec",
    "amount_band_key",
    "baseline_specs",
    "group_mean_predictions",
    "improvement_ratio",
    "load_evaluation_policy",
    "paired_t",
    "policy_checksum",
    "regressed_segments",
    "rmse_bias_std",
    "segment_scores",
    "segment_specs",
]
