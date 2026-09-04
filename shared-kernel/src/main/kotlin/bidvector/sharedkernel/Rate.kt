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

/**
 * 사정률 — 예정가 / 기초금액. 축만 더하는 뉴타입이다(`data-dictionary.md` §1.4.2). 파생값이라
 * 생성자를 `internal`로 닫는다 — 유일한 생성 경로는 [assessmentRateAgainst]다(verifier r1 H-1).
 */
@ConsistentCopyVisibility
data class AssessmentRate internal constructor(
    val rate: Rate,
)

/** 낙찰률 — 낙찰가 / 기초금액. 유일한 생성 경로는 [awardRateAgainst]다(verifier r1 H-1). */
@ConsistentCopyVisibility
data class AwardRate internal constructor(
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

/**
 * 투찰율 — 투찰가 / 기초금액. 유일한 생성 경로는 [bidRateAgainst]다(verifier r1 H-1).
 *
 * **알려진 한계**: `BaseAmount.times(rate: BidRate)`(파생 투찰가 곱셈)의 입력도 `BidRate`라,
 * `BidRateOrigin.Recommended`(외부 추천 엔진이 낸 값)를 이 모듈 밖에서 구성할 공개 경로가
 * 지금 없다. 1B는 관측(`bidRateAgainst`)만 만들고 추천 입력 경로는 만들지 않았다 —
 * ML 추천을 받는 M2/M5가 그 경로(이름 있는 factory, `Rate.ofFraction`과 같은 형태)를
 * 열어야 한다.
 */
@ConsistentCopyVisibility
data class BidRate internal constructor(
    val rate: Rate,
    val origin: BidRateOrigin,
)
