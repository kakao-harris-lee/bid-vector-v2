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
    """불확실성 폭의 출처(§6.5, wire `IntervalSource` 미러)."""

    CROSS_VALIDATION_RESIDUAL = "CROSS_VALIDATION_RESIDUAL"
    TIME_HOLDOUT_RESIDUAL = "TIME_HOLDOUT_RESIDUAL"


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
    (wire 에 없음, `OPEN-5D-DIAGNOSTICS-WIRE`)."""

    training_row_count: int
    segment_support: SegmentSupport
    shrinkage_weight: Decimal
    excluded_observations: int

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
