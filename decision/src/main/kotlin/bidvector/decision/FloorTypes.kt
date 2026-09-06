package bidvector.decision

import bidvector.sharedkernel.AssessmentRate
import bidvector.sharedkernel.PolicyVersion
import java.math.BigDecimal

/**
 * 하한 미달 판정 불가 사유(§3.3, D-14) — `ReasonCode`(shared-kernel enum, decision 27 대상
 * 아님)와 다른 축이다. 인프라 실패는 이 어휘에 없다(예외로 낸다).
 */
sealed interface FloorUnmeasurableReason {
    data object FloorRateUnresolved : FloorUnmeasurableReason

    data object FloorModelNotApplicable : FloorUnmeasurableReason

    data object BidRateUnavailable : FloorUnmeasurableReason

    data class SampleInsufficient(
        val required: Int,
        val actual: Int,
    ) : FloorUnmeasurableReason
}

/**
 * 하한 미달 빈도 — 유리수만 나른다(D-7). 십진 렌더링은 이 슬라이스가 내지 않는다
 * (`OPEN-DIC-10`). `numerator`는 `[0, denominator]`를 벗어날 수 없다.
 */
data class Frequency(
    val numerator: Int,
    val denominator: Int,
) {
    init {
        require(denominator > 0) { "denominator는 양수여야 한다: $denominator" }
        require(numerator in 0..denominator) { "numerator는 [0, denominator] 범위여야 한다: $numerator/$denominator" }
    }
}

/** 임계 사정률의 알려진 편향 방향(§2.2 「알려진 편향」, `OPEN-DEC-06` 해소). */
sealed interface BiasDirection {
    data object Overestimates : BiasDirection

    data object Underestimates : BiasDirection

    data object Indeterminate : BiasDirection
}

/** 경계 등가의 미달 판정 정책(D-3, decision 28) — 초기값은 [StrictlyGreater]. */
sealed interface ShortfallComparison {
    data object StrictlyGreater : ShortfallComparison

    data object GreaterOrEqual : ShortfallComparison
}

/**
 * 표본 개연 범위(분모 필터, §2.2). 값은 정책 데이터다 — `floor-shortfall-005` note가
 * synthetic임을 명시하듯 이 타입도 값을 지어내지 않는다. `min`·`max` 경계는 포함이다.
 */
data class AssessmentBand(
    val min: BigDecimal,
    val max: BigDecimal,
) {
    init {
        require(min <= max) { "min은 max 이하여야 한다: min=$min max=$max" }
    }

    fun contains(fraction: BigDecimal): Boolean = fraction >= min && fraction <= max
}

/**
 * 하한 미달 집계 불변식(D-4) — `qualifiedDenominator = rawCount - outsideBand`(위협 모델
 * (f)), `shortfallNumerator`는 `[0, qualifiedDenominator]`를 벗어날 수 없다.
 */
data class ShortfallTally(
    val rawCount: Int,
    val outsideBand: Int,
    val shortfallNumerator: Int,
) {
    val qualifiedDenominator: Int = rawCount - outsideBand

    init {
        require(rawCount >= 0) { "rawCount는 음수일 수 없다: $rawCount" }
        require(outsideBand in 0..rawCount) { "outsideBand는 [0, rawCount] 범위여야 한다: $outsideBand/$rawCount" }
        require(shortfallNumerator in 0..qualifiedDenominator) {
            "shortfallNumerator는 [0, qualifiedDenominator] 범위여야 한다: $shortfallNumerator/$qualifiedDenominator"
        }
    }
}

/**
 * 하한 미달 빈도 판정(§3.3) — `Measured` 생성 경로를 [bidvector.decision.measureFloorShortfall]
 * 하나로 닫는다(`internal constructor`, 위협 모델 (2)) — `frequency=0/0` 같은 임의 조립을
 * 막는다. `Unmeasurable`은 값 생성자가 공개다(사유 있는 실패는 어디서든 만들 수 있다).
 */
sealed interface FloorShortfall {
    @ConsistentCopyVisibility
    data class Measured internal constructor(
        val frequency: Frequency,
        val criticalAssessmentRate: AssessmentRate,
        val band: AssessmentBand,
        val biasDirection: BiasDirection,
        val policyVersion: PolicyVersion,
    ) : FloorShortfall

    data class Unmeasurable(
        val reason: FloorUnmeasurableReason,
    ) : FloorShortfall
}

/** 판정 봉투(D-13) — 집계·임계·결과를 함께 나른다(감사 가능성). */
data class FloorShortfallJudgement(
    val tally: ShortfallTally,
    val critical: AssessmentRate,
    val result: FloorShortfall,
)
