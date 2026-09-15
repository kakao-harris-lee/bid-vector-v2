"""ml_engine.evaluation — 낙찰률 GBM 홀드아웃 평가 커널(M5/5C-2). 공개 표면 재수출
((2b) 값 획득 축 표가 전수) — 다른 패키지는 이 최상위 이름만 보고 하위 모듈을 직접
import 하지 않는다. 순수 커널(채점·베이스라인·세그먼트·진단·창 정책·판정·report 타입·
정책)만 여기 있다 — 창마다 학습을 부르는 실행기는 `ml_engine.training.holdout`(층
경계, import-linter `training > evaluation > features`)."""

from __future__ import annotations

from ml_engine.evaluation.policy import (
    SHIPPED_EVALUATION_POLICY_VERSION,
    EvaluationPolicy,
    PolicyRejected,
    PolicyRejectionReason,
    load_evaluation_policy,
    policy_checksum,
)

__all__ = [
    "SHIPPED_EVALUATION_POLICY_VERSION",
    "EvaluationPolicy",
    "PolicyRejected",
    "PolicyRejectionReason",
    "load_evaluation_policy",
    "policy_checksum",
]
