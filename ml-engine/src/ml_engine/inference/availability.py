"""Reuse: bid-vector/app/ai/predictors/distribution.py@ed4b06c
`ReserveDrawDistributionPredictor.check_availability`(관측 행 수·비율 표본 수 임계 둘)

`ml_engine.inference.availability` — 분포 엔진 가용성 게이트(scope.md ③, D-5D2-7).
legacy `check_availability`는 최소 표본 임계 둘(`PRICE_PREDICTION_DISTRIBUTION_MIN_
RESERVE_RECORDS`·`PRICE_PREDICTION_DISTRIBUTION_MIN_BID_RATIO_SAMPLES`)을 **오직
availability 조회 경로에서만** 검사했다 — `_estimate_distribution`(실제 추정)은 그 임계를
다시 보지 않고 표본이 비어 있는지만 확인해서, predictor 를 직접 호출하면 표본 1건으로도
추정이 나왔다(01_digest.md §1 「predictor 를 직접 호출하면 표본 1건으로도 추정이 나온다」).

`distribution_availability`는 **가용성 조회와 조립기(`distribution.py`)가 같은 함수
객체를 호출**하게 해 그 결함을 구조적으로 막는다(ML-02 ② 유추, `predict.
segment_availability`와 같은 패턴 — GBM 축의 `SHALLOW_SEGMENT`와는 다른 세분 사유를 쓴다,
D-5D-2 「사유가 섞이면 구별 안 됨」). `Available`은 `predict.py`의 마커를 그대로 재사용한다
(값 없는 마커 타입을 중복 정의하지 않는다)."""

from __future__ import annotations

from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.predict import Available
from ml_engine.inference.results import (
    Unmeasurable,
    UnmeasurableDetail,
    UnmeasurableReason,
)


def distribution_availability(
    *, observation_count: int, ratio_sample_count: int, policy: InferencePolicy
) -> Available | Unmeasurable:
    """관측 행 수가 `policy.reserve_min_reserve_records` 미만이면 `TOO_FEW_OBSERVATIONS`,
    비율 표본 수가 `policy.bid_ratio_min_samples` 미만이면 `TOO_FEW_RATIO_SAMPLES`(둘 다
    미달이면 관측 행 수 사유가 먼저다 — legacy 관문 순서 그대로). 임계는 `>=`(경계 포함)."""
    if observation_count < policy.reserve_min_reserve_records:
        return Unmeasurable(
            UnmeasurableReason.INSUFFICIENT_SAMPLES,
            UnmeasurableDetail.TOO_FEW_OBSERVATIONS,
        )
    if ratio_sample_count < policy.bid_ratio_min_samples:
        return Unmeasurable(
            UnmeasurableReason.INSUFFICIENT_SAMPLES,
            UnmeasurableDetail.TOO_FEW_RATIO_SAMPLES,
        )
    return Available()
