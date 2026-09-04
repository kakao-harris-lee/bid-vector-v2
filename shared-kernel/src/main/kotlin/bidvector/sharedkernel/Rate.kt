package bidvector.sharedkernel

import java.math.BigDecimal

private const val PERCENT_DIVISOR_VALUE = 100L
private val PERCENT_DIVISOR = BigDecimal(PERCENT_DIVISOR_VALUE)

private fun normalized(value: BigDecimal): BigDecimal =
    if (value.signum() == 0) BigDecimal.ZERO else value.stripTrailingZeros()

/**
 * 율 — fraction 하나만 나른다. percent 입력은 어댑터에서만 변환하고, 값 크기로 단위를
 * 추측하는 경로를 도메인 안에 두지 않는다(`ADR 0002` D-4). 밴드 상한을 여기 두지 않는다 —
 * `require(fraction <= ONE)`류는 `0.875`와 `87.5`를 크기로 가르는 것과 같은 판정이고, 그것이
 * D-4가 도메인에서 금지한 경로다. 하한만(음수 없음) 둔다.
 *
 * 값 동등은 이 정규화된 표현 위의 구조적 `equals`와 일치한다(운영자 결정 2026-09-04, A5 —
 * `compareTo(other) == 0`과 같은 결과를 정규화 후 생성으로 얻는다).
 */
@ConsistentCopyVisibility
data class Rate internal constructor(
    internal val fraction: BigDecimal,
) {
    init {
        require(fraction.signum() >= 0) { "Rate는 음수일 수 없다: $fraction" }
        require(fraction == normalized(fraction)) { "Rate는 정규화된 형태여야 한다: $fraction" }
    }

    companion object {
        fun ofFraction(value: BigDecimal): Rate = Rate(normalized(value))

        fun ofPercent(value: BigDecimal): Rate = Rate(normalized(value.divide(PERCENT_DIVISOR)))
    }
}

/** 사정률 — 예정가 / 기초금액. 축만 더하는 뉴타입이다(`data-dictionary.md` §1.4.2). */
data class AssessmentRate(
    val rate: Rate,
)

/** 낙찰률 — 낙찰가 / 기초금액. */
data class AwardRate(
    val rate: Rate,
)

/** `FloorRate`의 출처 — 게시값과 법정 하한율 표를 섞지 않는다(`data-dictionary.md` §1.4.2). */
sealed interface FloorRateOrigin {
    data class NoticeValue(
        val noticeRevision: Int,
    ) : FloorRateOrigin

    data class StatutoryTable(
        val effectiveFrom: EffectiveFrom,
    ) : FloorRateOrigin
}

/** 낙찰하한율 — 예정가격 기준 율. */
data class FloorRate(
    val rate: Rate,
    val origin: FloorRateOrigin,
)

/** `BidRate`의 자리 — 관측값과 추천값을 한 값으로 합치지 않는다(`data-dictionary.md` §1.4.2). */
sealed interface BidRateOrigin {
    data object ObservedFromSamples : BidRateOrigin

    data object Recommended : BidRateOrigin
}

/** 투찰율 — 투찰가 / 기초금액. */
data class BidRate(
    val rate: Rate,
    val origin: BidRateOrigin,
)
