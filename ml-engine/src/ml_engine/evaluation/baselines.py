"""Reuse: bid-vector/app/services/ml_training/award_rate_scoring.py@ed4b06c

`ml_engine.evaluation.baselines` — 베이스라인 표 + 커버리지 마스크(scope ②). legacy
`BASELINE_SPECS`(`:60-73`)를 값이 아니라 **구조**로 이었다 — 다섯 spec 은 선언 데이터고
(분기가 아니라 표 한 줄), `key`·`min_count`는 `EvaluationPolicy`에서만 온다(D-5C2-3,
`OPEN-5C-BUDGET-BAND-SOURCE` 종결 — 금액대 경계가 legacy 업무 모듈 private import 에서
정책 데이터로 이동했다).

evaluation 층은 `ml_engine.training`을 알지 못한다(import-linter layers) — 그래서 이
모듈의 함수는 `TrainingRow` 가 아니라 `FeatureFacts`(5B) 시퀀스 + 라벨/타깃
`np.ndarray`를 받는다. 창마다 학습을 부르는 실행기(`ml_engine.training.holdout`)가
`TrainingRow.facts`/`.label.value`를 풀어 여기로 넘긴다.
"""

from __future__ import annotations

from collections.abc import Callable, Sequence
from dataclasses import dataclass
from typing import Final

import numpy as np

from ml_engine.evaluation.policy import EvaluationPolicy
from ml_engine.features import FeatureFacts, Present

GATE_BASELINE_TABLE_NAMES: Final[tuple[str, ...]] = (
    "global_mean",
    "category",
    "amount_band",
    "category_x_band",
    "agency",
)


@dataclass(frozen=True)
class BaselineSpec:
    """그룹 평균 베이스라인 하나 — 이름, 그룹 키, 최소 표본."""

    name: str
    key: Callable[[FeatureFacts], str]
    min_count: int = 1


def _category_key(facts: FeatureFacts) -> str:
    return (
        facts.category_code.value
        if isinstance(facts.category_code, Present)
        else "unknown"
    )


def _agency_key(facts: FeatureFacts) -> str:
    return facts.agency_id.value if isinstance(facts.agency_id, Present) else "unknown"


def amount_band_key(facts: FeatureFacts, edges: Sequence[float]) -> str:
    """정책 `amount_band_edges`(오름차순, 양수) 로 나눈 금액대 이름. 기초금액이
    결측이면(build_row 가 이미 그런 행을 걸렀어야 하지만, 이 층은 그 보장을 모른다)
    `"unknown"`."""
    if not isinstance(facts.base_amount, Present):
        return "unknown"
    amount = facts.base_amount.value
    for index, edge in enumerate(edges):
        if amount < edge:
            return f"band_{index}"
    return f"band_{len(edges)}"


def baseline_specs(policy: EvaluationPolicy) -> tuple[BaselineSpec, ...]:
    """다섯 베이스라인 표(선언 데이터) — 금액대 경계·게이트 최소 표본은 정책에서."""

    def _amount_band(facts: FeatureFacts) -> str:
        return amount_band_key(facts, policy.amount_band_edges)

    def _category_x_band(facts: FeatureFacts) -> str:
        return f"{_category_key(facts)}|{_amount_band(facts)}"

    return (
        BaselineSpec(name="global_mean", key=lambda facts: ""),
        BaselineSpec(name="category", key=_category_key),
        BaselineSpec(name="amount_band", key=_amount_band),
        BaselineSpec(name="category_x_band", key=_category_x_band),
        BaselineSpec(
            name="agency",
            key=_agency_key,
            min_count=policy.agency_baseline_min_count,
        ),
    )


def group_mean_predictions(
    spec: BaselineSpec,
    train_facts: Sequence[FeatureFacts],
    train_labels: np.ndarray,
    test_facts: Sequence[FeatureFacts],
    *,
    global_mean: float,
) -> tuple[np.ndarray, np.ndarray]:
    """그룹 평균 예측과 **행별 커버리지 마스크**.

    비율이 아니라 마스크를 내는 이유: 커버리지가 낮을 때 "어느 행이 폴백이었는가"를 알아야
    게이트의 유의성이 폴백 소수 행에 걸려 있는지 분해할 수 있다(`.diagnostics`).
    """
    totals: dict[str, tuple[float, int]] = {}
    for facts, label in zip(train_facts, train_labels, strict=True):
        key = spec.key(facts)
        total, count = totals.get(key, (0.0, 0))
        totals[key] = (total + float(label), count + 1)

    predictions: list[float] = []
    covered: list[bool] = []
    for facts in test_facts:
        key = spec.key(facts)
        total, count = totals.get(key, (0.0, 0))
        is_covered = count >= spec.min_count
        predictions.append(total / count if is_covered else global_mean)
        covered.append(is_covered)
    return np.array(predictions, dtype=float), np.array(covered, dtype=bool)
