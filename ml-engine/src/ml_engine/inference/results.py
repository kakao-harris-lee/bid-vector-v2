"""`ml_engine.inference.results` — 추론 결과 타입(신규, scope.md ③④·D-5D-2).

`KernelResult = Success | Unmeasurable` — 실패는 예외가 아니라 이 결과 타입으로 나른다
(위협 모델 (a)). `UnmeasurableReason`은 wire `error.proto`의 3값을 그대로 미러하고
(추가 없음, D-5D-2), 세분 사유는 `UnmeasurableDetail`(닫힌 `StrEnum`)로 `detail_code`에
싣는다 — 두 사유가 합쳐지는 것(위협 모델 (b))을 enum 조합으로 막는다.

`Candidate.bid_rate > 0`은 `__post_init__`에서 강제한다(우회 후보 (1) — `Unmeasurable`
대신 후보를 0률로 접는 경로를 구성상 닫는다). `weight`·`bid_rate`는 전부 `Decimal`이라
결과 타입에 float 필드가 없다(D-5D-1, 설계 검토 (1) 「Decimal 경계」 행).
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal
from enum import StrEnum
from typing import NewType


class CandidateLabel(StrEnum):
    """후보 라벨 축 — 순서는 `Success.candidates`가 고정한다(CONSERVATIVE·BASE·AGGRESSIVE)."""

    CONSERVATIVE = "CONSERVATIVE"
    BASE = "BASE"
    AGGRESSIVE = "AGGRESSIVE"


class UnmeasurableReason(StrEnum):
    """wire `error.proto` `UnmeasurableReason`의 3값 미러(UNSPECIFIED 제외, D-5D-2 —
    값 추가 없음). 세분 사유는 `UnmeasurableDetail`이 나른다."""

    INSUFFICIENT_SAMPLES = "INSUFFICIENT_SAMPLES"
    UNTRAINED_SEGMENT = "UNTRAINED_SEGMENT"
    FEATURE_ABSENT = "FEATURE_ABSENT"


class UnmeasurableDetail(StrEnum):
    """닫힌 세분 사유 — `Unmeasurable.detail_code`(wire, D-5D-2)로 나갈 값. 새 사유가
    필요하면 이 enum 에 추가하고 wire `detail_code`는 자유 문자열이라 M2 호환에 영향 없다."""

    NEVER_TRAINED = "NEVER_TRAINED"
    SHALLOW_SEGMENT = "SHALLOW_SEGMENT"
    NON_FINITE_INPUT = "NON_FINITE_INPUT"
    DEGENERATE_VARIANCE = "DEGENERATE_VARIANCE"
    TOO_FEW_DRAWS = "TOO_FEW_DRAWS"
    ROW_REJECTED = "ROW_REJECTED"
    # verifier r1 L-2 — K5 global 표본 0 은 추첨(K6) 축의 `TOO_FEW_DRAWS`와 다른 사유다
    # (위협 모델 (b) 「두 사유 합침」의 약한 형태였다 — 재사용하지 않는다).
    NO_GLOBAL_SAMPLES = "NO_GLOBAL_SAMPLES"
    # M5/5D-2 — `distribution_availability`(D-5D2-7) 전용 세분 사유 둘. GBM 축의
    # `SHALLOW_SEGMENT`(공종 학습 행 부족)와는 다른 축(공고 관측 행 수·비율 표본 수)이라
    # 재사용하지 않는다 — 사유가 섞이면 "왜 측정 불가인가"가 응답만으로 구별되지 않는다
    # (scope.md ③).
    TOO_FEW_OBSERVATIONS = "TOO_FEW_OBSERVATIONS"
    TOO_FEW_RATIO_SAMPLES = "TOO_FEW_RATIO_SAMPLES"


@dataclass(frozen=True)
class Unmeasurable:
    """도메인 결과 층(ADR 0010 D-3)의 「측정 불가」 — 재시도 대상이 아니다."""

    reason: UnmeasurableReason
    detail: UnmeasurableDetail


@dataclass(frozen=True)
class Candidate:
    """후보 한 건 — `bid_rate > 0`을 구성 시점에 강제한다(우회 후보 (1))."""

    label: CandidateLabel
    bid_rate: Decimal
    weight: Decimal
    weight_policy_version: str

    def __post_init__(self) -> None:
        if not isinstance(self.bid_rate, Decimal):
            raise TypeError("bid_rate 는 Decimal 이어야 한다(float 누출 금지, D-5D-1)")
        if self.bid_rate <= 0:
            raise ValueError(
                f"Candidate.bid_rate 는 0보다 커야 한다: {self.bid_rate!r}"
            )
        if not isinstance(self.weight, Decimal):
            raise TypeError("weight 는 Decimal 이어야 한다(float 누출 금지, D-5D-1)")


# 가격 적합도 — 확률 자리에 대입되면 타입 체커가 거부한다(OPEN-ML-03, 승계).
PriceFitness = NewType("PriceFitness", Decimal)


class IntervalSource(StrEnum):
    """불확실성 폭의 출처(§6.5, wire `IntervalSource` 미러). `POSTERIOR_PREDICTIVE`는
    분포 엔진 전용 내부 값이다(D-5D2-8) — GBM 축 잔차 기반 두 값(`CROSS_VALIDATION_
    RESIDUAL`·`TIME_HOLDOUT_RESIDUAL`) 중 어느 것도 사후예측분산 기반 불확실성의 출처를
    정확히 나타내지 않는다. wire `IntervalSource`에는 아직 대응 값이 없다(값을 지어내
    wire 값으로 위장하지 않는다, `OPEN-5D2-INTERVAL-SOURCE-WIRE` — 2F/5E 매핑 대기)."""

    CROSS_VALIDATION_RESIDUAL = "CROSS_VALIDATION_RESIDUAL"
    TIME_HOLDOUT_RESIDUAL = "TIME_HOLDOUT_RESIDUAL"
    POSTERIOR_PREDICTIVE = "POSTERIOR_PREDICTIVE"


@dataclass(frozen=True)
class Uncertainty:
    """불확실성 성분 셋(§6.5) — 합성 `confidence`는 여기 없다(이식하지 않음)."""

    sample_size: int
    dispersion: Decimal
    estimate_margin: Decimal
    interval_source: IntervalSource

    def __post_init__(self) -> None:
        if not isinstance(self.dispersion, Decimal):
            raise TypeError("dispersion 은 Decimal 이어야 한다(D-5D-1)")
        if not isinstance(self.estimate_margin, Decimal):
            raise TypeError("estimate_margin 은 Decimal 이어야 한다(D-5D-1)")


class SegmentSupport(StrEnum):
    """diagnostics 의 세그먼트 지지 근거(wire `SegmentSupport` 미러)."""

    DIRECT = "DIRECT"
    PARENT_CATEGORY = "PARENT_CATEGORY"
    GLOBAL = "GLOBAL"


@dataclass(frozen=True)
class Diagnostics:
    """진단 — `shrinkage_weight`·`excluded_observations`는 Python 결과 타입에만 실린다
    (wire 에 없음, `OPEN-5D-DIAGNOSTICS-WIRE`). `agency_sample_count`·
    `agency_sample_below_threshold`는 M5/5D-2 추가(golden `ml-kernel-011`, ML-04 ②) —
    기관 표본이 `policy.assessment_agency_sample_threshold` 미만이면 수축 가중치가
    응답 근거에 실린다는 acceptance 를 구조화 값으로 나른다. GBM 경로(`predict.py`)는
    기관 표본 축을 쓰지 않아 항상 `0`/`False`다(알려진 제한, checklist.md)."""

    training_row_count: int
    segment_support: SegmentSupport
    shrinkage_weight: Decimal
    excluded_observations: int
    agency_sample_count: int
    agency_sample_below_threshold: bool

    def __post_init__(self) -> None:
        if not isinstance(self.shrinkage_weight, Decimal):
            raise TypeError("shrinkage_weight 는 Decimal 이어야 한다(D-5D-1)")


@dataclass(frozen=True)
class Success:
    """추론 성공 — 후보 정확히 3(라벨 순서 고정)."""

    candidates: tuple[Candidate, Candidate, Candidate]
    fitness: PriceFitness
    uncertainty: Uncertainty
    diagnostics: Diagnostics

    def __post_init__(self) -> None:
        if len(self.candidates) != 3:
            raise ValueError("candidates 는 정확히 3건이어야 한다")
        expected_labels = (
            CandidateLabel.CONSERVATIVE,
            CandidateLabel.BASE,
            CandidateLabel.AGGRESSIVE,
        )
        actual_labels = tuple(candidate.label for candidate in self.candidates)
        if actual_labels != expected_labels:
            raise ValueError(
                f"candidates 순서는 {expected_labels!r}로 고정된다: {actual_labels!r}"
            )


type KernelResult = Success | Unmeasurable


@dataclass(frozen=True)
class DistributionRelease:
    """분포 엔진 전용 내부 release 식별(D-5D2-5) — 아티팩트 없는 엔진이라 wire
    `ModelRelease`(release_id·artifact_checksum·dataset_id 필수)를 채울 값이 없다.
    이 타입은 `Success.release`에 대응하지 않는다(wire 매핑 없음, `OPEN-5D2-RELEASE-
    FOR-DISTRIBUTION` — 5E/2F 가 wire `ModelRelease`로 어떻게 옮길지 결정한다). `method`는
    항상 `"reserve-draw-distribution"`(자유 문자열 상수, enum 아님 — wire 축 없음)."""

    policy_version: str
    code_version: str
    method: str
