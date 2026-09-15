"""Reuse: bid-vector/app/services/ml_training/award_rate_scoring.py@ed4b06c

`ml_engine.evaluation.segments` — worst-segment 재채점(scope ③). legacy `SEGMENT_SPECS`
(`:84-90`)의 구조를 이었다: 홀드아웃을 축별로 **자르기만** 해서 재채점(재학습 없음).
축은 `category`와 `amount_band` 둘뿐이다 — legacy 의 `published_floor` 축은 **없다**
(D-5C2-9): `FeatureFacts`에 그 필드가 없고, legacy 에서도 백필 커버리지의 함수라 학습·
서빙 의미가 달랐다(F3 판단 기준 「축의 의미가 다르면 쓰지 않는다」).

`amount_band`는 베이스라인 축을 세그먼트로도 쓴다(신규 축, `.baselines.amount_band_key`
재사용) — legacy 는 세그먼트 표와 베이스라인 표의 금액대 키가 서로 달랐다(전자는 없음,
후자만 있었다). 축 목록은 정책(`segment_axes`)에서 오므로 코드 분기가 아니라 표 조회다.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass

import numpy as np

from ml_engine.evaluation.baselines import amount_band_key
from ml_engine.evaluation.policy import EvaluationPolicy
from ml_engine.evaluation.scoring import improvement_ratio, paired_t, rmse_bias_std
from ml_engine.features import FeatureFacts, Present


@dataclass(frozen=True)
class SegmentSpec:
    axis: str
    key: Callable[[FeatureFacts], str]


@dataclass(frozen=True)
class SegmentScore:
    """홀드아웃 한 세그먼트에서 베이스라인과 게이트 모델을 나란히 잰 성적. 예측은 전체
    예측 벡터를 **자르기만** 한 것이다."""

    axis: str
    segment: str
    row_count: int
    baseline_rmse: float
    baseline_bias: float
    model_rmse: float
    model_bias: float
    model_residual_std: float
    improvement_ratio: float
    """(베이스라인 − 모델) ÷ 베이스라인. **음수면 이 세그먼트에서 모델이 더 나쁘다.**"""
    paired_t: float


def _category_key(facts: FeatureFacts) -> str:
    return facts.category_code.value if isinstance(facts.category_code, Present) else "unknown"


_AXIS_KEY_BUILDERS: dict[str, Callable[[FeatureFacts, EvaluationPolicy], str]] = {
    "category": lambda facts, _policy: _category_key(facts),
    "amount_band": lambda facts, policy: amount_band_key(
        facts, policy.amount_band_edges
    ),
}


def segment_specs(policy: EvaluationPolicy) -> tuple[SegmentSpec, ...]:
    """정책 `segment_axes`가 선언한 축만 — 새 축은 코드 분기가 아니라 정책 값 한 줄."""
    return tuple(
        SegmentSpec(axis=axis, key=lambda facts, a=axis: _AXIS_KEY_BUILDERS[a](facts, policy))
        for axis in policy.segment_axes
    )


def _one_segment_score(
    axis: str,
    segment: str,
    selector: np.ndarray,
    targets: np.ndarray,
    baseline_predictions: np.ndarray,
    model_predictions: np.ndarray,
) -> SegmentScore:
    """세그먼트 하나의 성적 — 전체 예측 벡터를 **자르기만** 한다."""
    segment_targets = targets[selector]
    baseline_rmse, baseline_bias, _ = rmse_bias_std(
        baseline_predictions[selector], segment_targets
    )
    model_rmse, model_bias, model_std = rmse_bias_std(
        model_predictions[selector], segment_targets
    )
    return SegmentScore(
        axis=axis,
        segment=segment,
        row_count=int(selector.size),
        baseline_rmse=baseline_rmse,
        baseline_bias=baseline_bias,
        model_rmse=model_rmse,
        model_bias=model_bias,
        model_residual_std=model_std,
        improvement_ratio=improvement_ratio(baseline_rmse, model_rmse),
        paired_t=paired_t(
            model_predictions[selector], baseline_predictions[selector], segment_targets
        ),
    )


def segment_scores(
    facts_seq: Sequence[FeatureFacts],
    targets: np.ndarray,
    *,
    baseline_predictions: np.ndarray,
    model_predictions: np.ndarray,
    specs: tuple[SegmentSpec, ...],
) -> list[SegmentScore]:
    """선언된 축마다 홀드아웃을 쪼개 두 예측을 나란히 채점한다(재학습 없음)."""
    scores: list[SegmentScore] = []
    for spec in specs:
        groups: dict[str, list[int]] = {}
        for index, facts in enumerate(facts_seq):
            groups.setdefault(spec.key(facts), []).append(index)
        scores.extend(
            _one_segment_score(
                spec.axis,
                segment,
                np.array(indices, dtype=int),
                targets,
                baseline_predictions,
                model_predictions,
            )
            for segment, indices in sorted(groups.items())
        )
    return scores


def regressed_segments(scores: Sequence[SegmentScore]) -> list[SegmentScore]:
    """`improvement_ratio < 0 ∧ row_count > 1`(1행 세그먼트는 대응 t 가 성립하지 않아
    제외 — 진짜 신호를 희석한다), 행 수 내림차순."""
    return sorted(
        (score for score in scores if score.improvement_ratio < 0 and score.row_count > 1),
        key=lambda score: -score.row_count,
    )
