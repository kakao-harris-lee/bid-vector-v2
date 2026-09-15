"""`ml_engine.inference.engine` — 서빙 진입점(scope.md ④, D-5D2-1 (b)).

엔진은 **분포 단독**이다 — legacy `price_prediction/orchestration.py`는 predictor
선택·폴백(`_select_predictor`·`_run_predictor`의 `except Exception → historical`)이
전부라 D-5D2-1 (b) 결정 아래 재활용 대상이 아니다(이 모듈은 신규 조립, reuse.md 대상
아님). GBM `predict_bid_rates`(`predict.py`)는 이 모듈이 **import하지 않는다**(연결
없음이 코드로 보인다, 위협 모델 우회 후보 (4)) — `ml_engine.registry`도 쓰지 않는다
(import-linter, `engine.py`는 registry 없이 조립기·정책·결과 타입만으로 완결된다).
선택 축·폴백이 없다(ADR 0001 D-6, ML-05 — 게이트 미통과인 GBM 은 이 진입점 밖에 머문다).
"""

from __future__ import annotations

from ml_engine.contracts import prediction_pb2
from ml_engine.inference.distribution import DistributionRequest, predict_distribution
from ml_engine.inference.policy import InferencePolicy
from ml_engine.inference.results import KernelResult, Unmeasurable

# 엔진 상수 — 정책 파일이 아니라 코드에 둔다(D-5D2-2, 엔진이 하나라 선택 축 자체가 없다).
ENGINE = "DISTRIBUTION"


def serve_bid_rates(
    request: prediction_pb2.CalculateOptimalBidRequest, policy: InferencePolicy
) -> KernelResult:
    """유일 진입점 — wire 요청을 `DistributionRequest`로 검증한 뒤 분포 엔진
    (`predict_distribution`) 하나로 조립한다. 실패는 전부 `Unmeasurable`(결과 타입)."""
    distribution_request = DistributionRequest.from_proto(request)
    if isinstance(distribution_request, Unmeasurable):
        return distribution_request
    return predict_distribution(distribution_request, policy)
