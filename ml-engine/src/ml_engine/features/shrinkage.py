"""Reuse: bid-vector/app/domain/assessment_shrinkage.py@ed4b06c

`ml_engine.features.shrinkage` — pseudo-count 수축 원시 연산 둘(D-5B-6, K5 원시 연산 자리 —
5D `assessment_shrinkage` 상당 커널이 이 모듈을 import 해 재사용한다, 중복 금지).

가져온 것은 `pseudo_count_weight`·`shrink_toward` 둘뿐이다. legacy 파일의 3계층
`resolve_assessment_posterior`(사정률 축 전용 조립 — 분산 혼합·`AssessmentPosterior` 등)는
가져오지 않았다 — 그것은 사정률 도메인의 고유 조립이고, 5B가 필요한 것은 그 밑에 깔린
가중치·수축 산술 두 함수뿐이다(낙찰률 인코딩은 `features/encoding.py`가 이 두 함수만으로
2단 수축을 직접 조립한다).
"""

from __future__ import annotations


def pseudo_count_weight(sample_count: int, prior_strength: float) -> float:
    """자기 가중치 ``n/(n+κ)`` — pseudo-count 수축 강도의 단일 출처."""
    return sample_count / (sample_count + prior_strength)


def shrink_toward(
    observed_mean: float,
    sample_count: int,
    *,
    prior_mean: float,
    prior_strength: float,
) -> tuple[float, float]:
    """pseudo-count 수축 한 단계: ``(n·x̄ + κ·μ)/(n + κ)`` 와 자기 가중치 ``n/(n+κ)``."""
    weight = pseudo_count_weight(sample_count, prior_strength)
    return (observed_mean * weight) + (prior_mean * (1.0 - weight)), weight
