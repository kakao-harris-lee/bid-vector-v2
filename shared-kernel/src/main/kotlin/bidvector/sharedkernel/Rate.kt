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
 *
 * `fraction`은 공개 읽기다(D-9, M1/1D) — 생성자는 그대로 `internal`로 닫혀 있다. 1B가 닫은
 * 것은 **구성**(임의 값이 `Rate`로 조립되는 경로)이고, 이미 정규화된 값의 읽기는 그
 * 불변식을 깨지 않는다 — `Money`의 [export]에 대응한다. `Comparable<Rate>`도 같은
 * 이유로 연다 — 두 `Rate`의 비교는 `fraction`의 정규화된 값 비교이고, `Money`처럼 basis가
 * 섞이는 위험이 없다(`Rate`는 basis를 나르지 않는다).
 */
@ConsistentCopyVisibility
data class Rate internal constructor(
    val fraction: BigDecimal,
) : Comparable<Rate> {
    init {
        require(fraction.signum() >= 0) { "Rate는 음수일 수 없다: $fraction" }
        require(fraction == normalized(fraction)) { "Rate는 정규화된 형태여야 한다: $fraction" }
    }

    override fun compareTo(other: Rate): Int = fraction.compareTo(other.fraction)

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
) {
    companion object {
        /**
         * 관측 사정률 재구성(D-1(a)·D-9, M1/1D) — 저장·전송에서 돌아온 실현 사정률 표본을
         * 다시 감싼다. **파생이 아니다** — [assessmentRateAgainst]처럼 `YegaAmount ÷
         * BaseAmount`에서 새로 계산하지 않는다(이름이 그 차이를 진술한다). 값이 어디서
         * 왔는지(관측인지 계산인지)는 호출부 책임이고, 이 factory는 형태만 연다.
         *
         * **경계(verifier r1 F-1)**: 새로 계산해 내는 파생값(예: [criticalAssessmentRate])의
         * 생성 경로가 아니다 — 파생값은 정책 version을 `DerivationRecord`에 실어
         * `Derived<AssessmentRate>`로 나가야 하고(decision 17), 이 factory는 그 결속을
         * 만들지 않는다. 같은 모듈의 파생 함수는 이 factory 대신 `internal` 생성자를
         * 직접 쓴다.
         */
        fun observed(rate: Rate): AssessmentRate = AssessmentRate(rate)
    }
}

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
 * 투찰율 — 투찰가 / 기초금액. 관측 경로는 [bidRateAgainst]다(verifier r1 H-1).
 *
 * **1D가 열었다**(D-1(a), M1/1D) — `BidRateOrigin.Recommended`(외부 추천 엔진이 낸 값)를
 * 이 모듈 밖에서 구성하는 이름 있는 factory가 [recommended]다. 1B는 관측만 만들고 추천
 * 입력 경로는 M2/M5가 열어야 한다고 예고했으나, 임계 사정률 계산(`criticalAssessmentRate`)이
 * 추천 투찰율을 입력으로 요구해 1D가 대신 연다.
 */
@ConsistentCopyVisibility
data class BidRate internal constructor(
    val rate: Rate,
    val origin: BidRateOrigin,
) {
    companion object {
        /** 추천 투찰율 입력(D-1(a)) — 값의 출처(추천 엔진)를 이름이 진술한다. */
        fun recommended(rate: Rate): BidRate = BidRate(rate, BidRateOrigin.Recommended)
    }
}
