"""Reuse: bid-vector/app/services/ml_training/award_rate_diagnostics.py@ed4b06c

`ml_engine.evaluation.diagnostics` — 게이트 판정의 **자기 진단**(scope ④): 검정력·베이스라인
커버리지·판정 안정성. legacy `:122-287`를 값 무변경으로 이식했다 — 세 가지가 실측에서
드러난 자리다: (1) 무측정과 실패는 다르다(seed 를 바꾸면 부호가 뒤집히는 창) (2) 유의성이
소수 행에 걸릴 수 있다(폴백 21행이 통과를 지고 있던 창) (3) 검정력은 표본 수가 아니라
효과 대비로 봐야 한다.

evaluation 층은 `ml_engine.training`을 모른다(layers) — 함수는 `FeatureFacts` 시퀀스 +
`np.ndarray`만 받는다.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

import numpy as np

from ml_engine.evaluation.baselines import BaselineSpec
from ml_engine.evaluation.scoring import improvement_ratio, rmse_bias_std, paired_t
from ml_engine.features import FeatureFacts, Present

COVERAGE_COVERED = "baseline-covered"
COVERAGE_FALLBACK = "baseline-fallback"


@dataclass(frozen=True)
class CoverageSplit:
    """베이스라인이 자기 그룹으로 예측한 행 / 전역 평균으로 떨어진 행의 성적."""

    segment: str
    row_count: int
    baseline_rmse: float
    model_rmse: float
    improvement_ratio: float
    paired_t: float


@dataclass(frozen=True)
class UnlearnedCell:
    """홀드아웃에는 있는데 학습 구간에 없던 베이스라인 셀(그 행 수)."""

    key: str
    row_count: int


@dataclass(frozen=True)
class CategoryCount:
    """한 구간의 공종 구성 — 창별 학습/평가 구성을 나란히 실어 레짐 단절을 드러낸다."""

    category: str
    row_count: int


@dataclass(frozen=True)
class StabilityTrial:
    """seed 하나에서 나온 게이트 성적 — 안정성 집계의 입력."""

    seed: int
    improvement_ratio: float
    paired_t: float
    passed: bool


@dataclass(frozen=True)
class StabilitySummary:
    """seed 를 바꿔가며 같은 창을 잰 결과. **판정을 믿어도 되는가**의 답."""

    trials: tuple[StabilityTrial, ...]
    passed_count: int
    sign_consistent: bool
    """모든 seed 에서 개선률 부호가 같은가. False 면 이 창은 방향조차 재지 못했다."""
    verdict_consistent: bool
    """모든 seed 에서 통과/실패가 같은가. **`sign_consistent`와 함께 읽어야 한다** —
    검정력 없는 창은 뒤집힐 힘이 없어 이 값을 공짜로 만족한다."""
    min_improvement_ratio: float
    max_improvement_ratio: float
    min_abs_paired_t: float
    max_abs_paired_t: float


def required_row_count(
    observed_paired_t: float, row_count: int, *, threshold: float
) -> int | None:
    """관측된 행당 효과가 유지된다면 유의성에 필요한 행 수. 모델이 베이스라인보다
    나쁘면(``t >= 0``) 또는 ``row_count <= 0``이면 `None`(0 으로 채우면 "표본이 충분했다"
    로 읽힌다)."""
    if observed_paired_t >= 0 or row_count <= 0:
        return None
    return int(np.ceil(row_count * (threshold / abs(observed_paired_t)) ** 2))


def minimum_detectable_improvement(
    model_predictions: np.ndarray,
    baseline_predictions: np.ndarray,
    targets: np.ndarray,
    *,
    threshold: float,
) -> float:
    """이 창이 유의하게 검출할 수 있었던 최소 RMSE 개선률(MDE). **검정력 50% 기준**을
    그대로 낸다(80% 로 읽으려면 약 1.33배 — 부풀린 수를 싣지 않는다, 조사 02 §1-5(c))."""
    differences = ((model_predictions - targets) ** 2) - (
        (baseline_predictions - targets) ** 2
    )
    baseline_mse = float(np.mean((baseline_predictions - targets) ** 2))
    if differences.size <= 1 or baseline_mse <= 0:
        return 0.0
    deviation = float(np.std(differences, ddof=1))
    detectable_mse_gap = threshold * deviation / np.sqrt(differences.size)
    detectable_model_mse = max(baseline_mse - detectable_mse_gap, 0.0)
    return improvement_ratio(
        float(np.sqrt(baseline_mse)), float(np.sqrt(detectable_model_mse))
    )


def coverage_splits(
    covered: np.ndarray,
    model_predictions: np.ndarray,
    baseline_predictions: np.ndarray,
    targets: np.ndarray,
) -> list[CoverageSplit]:
    """커버리지 마스크로 홀드아웃을 갈라 각각을 채점한다(재학습 없음). 빈 조각은
    생략한다(0 이 "재서 나온 0"이 아니라 "잴 것이 없었다"와 구별되게)."""
    scores: list[CoverageSplit] = []
    for name, mask in ((COVERAGE_COVERED, covered), (COVERAGE_FALLBACK, ~covered)):
        if not mask.any():
            continue
        segment_targets = targets[mask]
        baseline_rmse, _, _ = rmse_bias_std(baseline_predictions[mask], segment_targets)
        model_rmse, _, _ = rmse_bias_std(model_predictions[mask], segment_targets)
        scores.append(
            CoverageSplit(
                segment=name,
                row_count=int(mask.sum()),
                baseline_rmse=baseline_rmse,
                model_rmse=model_rmse,
                improvement_ratio=improvement_ratio(baseline_rmse, model_rmse),
                paired_t=paired_t(
                    model_predictions[mask], baseline_predictions[mask], segment_targets
                ),
            )
        )
    return scores


def unlearned_cells(
    spec: BaselineSpec,
    train_facts: Sequence[FeatureFacts],
    test_facts: Sequence[FeatureFacts],
) -> list[UnlearnedCell]:
    """홀드아웃에는 있는데 학습 구간에 없던(또는 `spec.min_count` 미만인) 베이스라인
    셀과 그 행 수(행 수 내림차순)."""
    learned: dict[str, int] = {}
    for facts in train_facts:
        key = spec.key(facts)
        learned[key] = learned.get(key, 0) + 1

    missing: dict[str, int] = {}
    for facts in test_facts:
        key = spec.key(facts)
        if learned.get(key, 0) < spec.min_count:
            missing[key] = missing.get(key, 0) + 1
    return [
        UnlearnedCell(key=key, row_count=count)
        for key, count in sorted(missing.items(), key=lambda item: (-item[1], item[0]))
    ]


def _category_key(facts: FeatureFacts) -> str:
    return facts.category_code.value if isinstance(facts.category_code, Present) else "unknown"


def category_counts(facts_seq: Sequence[FeatureFacts]) -> list[CategoryCount]:
    """공종 구성(행 수 내림차순) — 창별 레짐 단절이 리포트에서 보이게 한다."""
    totals: dict[str, int] = {}
    for facts in facts_seq:
        key = _category_key(facts)
        totals[key] = totals.get(key, 0) + 1
    return [
        CategoryCount(category=category, row_count=count)
        for category, count in sorted(totals.items(), key=lambda item: (-item[1], item[0]))
    ]


def summarize_stability(trials: Sequence[StabilityTrial]) -> StabilitySummary:
    """seed 별 성적을 안정성 요약으로. 빈 입력은 "일관"이 아니라 중립으로 둔다."""
    improvements = [trial.improvement_ratio for trial in trials]
    absolute_ts = [abs(trial.paired_t) for trial in trials]
    signs = {improvement > 0 for improvement in improvements}
    verdicts = {trial.passed for trial in trials}
    return StabilitySummary(
        trials=tuple(trials),
        passed_count=sum(1 for trial in trials if trial.passed),
        sign_consistent=len(signs) <= 1,
        verdict_consistent=len(verdicts) <= 1,
        min_improvement_ratio=min(improvements, default=0.0),
        max_improvement_ratio=max(improvements, default=0.0),
        min_abs_paired_t=min(absolute_ts, default=0.0),
        max_abs_paired_t=max(absolute_ts, default=0.0),
    )
