package bidvector.sharedkernel

import java.math.BigDecimal
import java.math.MathContext

private const val RATE_DIVISION_PRECISION = 20
private val RATE_DIVISION_CONTEXT = MathContext(RATE_DIVISION_PRECISION)

/**
 * `vatTreatment` 산술 전건 — 같고, `UNKNOWN`이 아니어야 한다. `a == b`만 쓰면 `UNKNOWN` 둘이
 * 통과한다(설계 검토 §5 L-9의 반례) — 이 함수가 그 전건을 한 곳에 둔다.
 */
internal fun sameKnownVat(
    left: VatTreatment,
    right: VatTreatment,
): Boolean = left == right && left != VatTreatment.UNKNOWN

/**
 * `v2-지침서.md` §4.1 — "provenance가 없거나 모르는 값은 추측하지 않고 거부 또는
 * `Unmeasurable`로 반환한다." `Provenance.Undeclared`가 그 "모르는 값"의 명시적
 * 표현이다(Codex 1차 #1). 산술·파생 성공 경계 전건의 유일한 자리다.
 */
private fun hasDeclaredProvenance(provenance: Provenance): Boolean = provenance != Provenance.Undeclared

/**
 * 반올림 이전의 파생 투찰가. 반올림 이전 단계에서는 `BigDecimal`을 쓴다(`ADR 0002` §3 A-4).
 * `BidAmount`로 가는 유일한 멤버가 [roundedWith]다. 입력 `BaseAmount`의 [AmountRecord]를
 * 잡아 두는 이유는 [Derived]가 되짚을 입력 fact 참조가 필요해서다(B11).
 */
class UnroundedBidAmount internal constructor(
    internal val raw: BigDecimal,
    private val currency: Currency,
    private val vatTreatment: VatTreatment,
    private val provenance: Provenance,
    private val baseInput: AmountRecord,
) {
    /**
     * `setScale` 자체가 던질 수 있다(`RoundingMode.UNNECESSARY`가 반올림을 요구하는 값을
     * 받으면) — overflow와는 다른 실패라 별도 `ReasonCode`로 잡는다(verifier r1 M-1).
     * overflow는 `Math.*Exact`와 동등한 성질로 잡는다 — `longValueExact()`가 범위·소수부를
     * 함께 잰다(A4). 반올림 결과가 음수면(이론상만 — `times()`의 두 입력이 모두 비음수라
     * 실제 경로에서는 나오지 않는다) `BidAmount.init`의 예외가 아니라 사유 있는 실패로 낸다.
     * 입력 `BaseAmount`의 `provenance`가 `Undeclared`면 다른 검사보다 먼저 막는다
     * (`v2-지침서.md` §4.1, Codex 1차 #1) — "출처를 모른다"는 값 오염이 계산에 들어가지
     * 않는다.
     *
     * `floor`(적용 하한, Codex 1차 #3) — 준다면 반올림 결과가 그 미만일 때
     * `ROUNDED_BELOW_FLOOR`로 막는다. 하한 자신이 소수일 수 있다(하한율×기초금액 같은
     * 계산에서 나온다) — `1000.4`를 `scale=0`·`DOWN`으로 내리면 `1000`이고 이는 `1000.4`
     * 미만이다(`data-dictionary.md` §1.1 정의 ②·`capability-map.md` DEC-02 무조건
     * acceptance). 기본값 `null`은 이 축을 아직 모르는 기존 호출부와 호환된다 — 하한을
     * 아는 호출부만 이 보증을 켠다.
     */
    fun roundedWith(
        policy: Resolution.Resolved<RoundingPolicy>,
        floor: BigDecimal? = null,
    ): Measurement<Derived<BidAmount>> {
        val scaling = runCatching { raw.setScale(policy.value.scaleDigits, policy.value.mode) }
        return when {
            !hasDeclaredProvenance(provenance) -> Measurement.Unmeasurable(ReasonCode.UNDECLARED_PROVENANCE)
            scaling.isFailure -> Measurement.Unmeasurable(ReasonCode.ROUNDING_NOT_REPRESENTABLE)
            else -> extractWon(scaling.getOrThrow(), policy, floor)
        }
    }

    /**
     * `longValueExact()`가 던지는 이유는 둘이다 — ① 소수 자리가 남음(호출부가 자리수 0이
     * 아닌 `scaleDigits`를 준 경우) ② 크기가 `Long` 범위를 벗어남. **정확한 사유를 낸다**
     * (verifier r1 M-1) — 둘 다 `AMOUNT_OVERFLOW`로 뭉치면 소수 자리 문제가 overflow로
     * 오라벨된다.
     */
    private fun extractWon(
        scaled: BigDecimal,
        policy: Resolution.Resolved<RoundingPolicy>,
        floor: BigDecimal?,
    ): Measurement<Derived<BidAmount>> {
        val hasFraction = scaled.signum() != 0 && scaled.stripTrailingZeros().scale() > 0
        return when {
            hasFraction -> Measurement.Unmeasurable(ReasonCode.ROUNDING_NOT_REPRESENTABLE)
            else -> extractLongValue(scaled, policy, floor)
        }
    }

    private fun extractLongValue(
        scaled: BigDecimal,
        policy: Resolution.Resolved<RoundingPolicy>,
        floor: BigDecimal?,
    ): Measurement<Derived<BidAmount>> {
        val extraction = runCatching { scaled.longValueExact() }
        return when {
            extraction.isFailure -> {
                Measurement.Unmeasurable(ReasonCode.AMOUNT_OVERFLOW)
            }

            extraction.getOrThrow() < 0L -> {
                Measurement.Unmeasurable(ReasonCode.NEGATIVE_AMOUNT)
            }

            floor != null && BigDecimal(extraction.getOrThrow()) < floor -> {
                Measurement.Unmeasurable(ReasonCode.ROUNDED_BELOW_FLOOR)
            }

            else -> {
                measured(extraction.getOrThrow(), policy.version)
            }
        }
    }

    private fun measured(
        won: Long,
        policyVersion: PolicyVersion,
    ): Measurement<Derived<BidAmount>> {
        val bidAmount = BidAmount(won, currency, vatTreatment, provenance)
        val derivation = DerivationRecord(inputs = listOf(baseInput), policyVersion = policyVersion)
        return Measurement.Measured(
            value = Derived(bidAmount, derivation),
            sampleSize = 1,
            policyVersion = policyVersion,
        )
    }
}

/** `BaseAmount × BidRate = BidAmount`만 — 다른 조합은 오버로드가 없어 컴파일되지 않는다. */
operator fun BaseAmount.times(rate: BidRate): UnroundedBidAmount =
    UnroundedBidAmount(
        raw = BigDecimal(amount).multiply(rate.rate.fraction),
        currency = currency,
        vatTreatment = vatTreatment,
        provenance = provenance,
        baseInput = export(),
    )

/**
 * provenance 검사를 vat·overflow 검사보다 먼저 한다(Codex 1차 #1) — 값을 어디서
 * 얻었는지 모르면 그 값이 다른 값과 vat·0-나눗셈 조건을 만족하는지 자체가 의미 없다.
 * 그래서 여러 실패가 동시에 걸려도 `UNDECLARED_PROVENANCE`가 먼저 나온다.
 */
private fun divideForRate(
    numerator: Long,
    numeratorVat: VatTreatment,
    numeratorProvenance: Provenance,
    denominator: Long,
    denominatorVat: VatTreatment,
    denominatorProvenance: Provenance,
    policy: Resolution.Resolved<RoundingPolicy>,
): Measurement<BigDecimal> =
    when {
        !hasDeclaredProvenance(numeratorProvenance) || !hasDeclaredProvenance(denominatorProvenance) -> {
            Measurement.Unmeasurable(ReasonCode.UNDECLARED_PROVENANCE)
        }

        !sameKnownVat(numeratorVat, denominatorVat) -> {
            Measurement.Unmeasurable(ReasonCode.VAT_TREATMENT_MISMATCH)
        }

        denominator == 0L -> {
            Measurement.Unmeasurable(ReasonCode.EMPTY_INPUT)
        }

        else -> {
            val quotient = BigDecimal(numerator).divide(BigDecimal(denominator), RATE_DIVISION_CONTEXT)
            Measurement.Measured(value = quotient, sampleSize = 1, policyVersion = policy.version)
        }
    }

/** 입력 둘(분자·분모)의 [AmountRecord]를 [DerivationRecord]에 실어 되짚을 수 있게 한다(B11). */
private fun <T> asRate(
    ratio: Measurement<BigDecimal>,
    inputs: List<AmountRecord>,
    wrap: (Rate) -> T,
): Measurement<Derived<T>> =
    when (ratio) {
        is Measurement.Measured -> {
            val rate = Rate.ofFraction(ratio.value)
            val derivation = DerivationRecord(inputs, ratio.policyVersion)
            Measurement.Measured(Derived(wrap(rate), derivation), ratio.sampleSize, ratio.policyVersion)
        }

        is Measurement.Unmeasurable -> {
            ratio
        }
    }

/** 사정률 = 예정가 / 기초금액. */
fun YegaAmount.assessmentRateAgainst(
    base: BaseAmount,
    policy: Resolution.Resolved<RoundingPolicy>,
): Measurement<Derived<AssessmentRate>> {
    val ratio = divideForRate(amount, vatTreatment, provenance, base.amount, base.vatTreatment, base.provenance, policy)
    return asRate(ratio, listOf(export(), base.export()), ::AssessmentRate)
}

/** 낙찰률 = 낙찰가 / 기초금액. */
fun AwardAmount.awardRateAgainst(
    base: BaseAmount,
    policy: Resolution.Resolved<RoundingPolicy>,
): Measurement<Derived<AwardRate>> {
    val ratio = divideForRate(amount, vatTreatment, provenance, base.amount, base.vatTreatment, base.provenance, policy)
    return asRate(ratio, listOf(export(), base.export()), ::AwardRate)
}

/** 투찰율 = 투찰가 / 기초금액. `origin`은 관측값/추천값을 값으로는 못 가르는 자리라 인자로 받는다. */
fun BidAmount.bidRateAgainst(
    base: BaseAmount,
    origin: BidRateOrigin,
    policy: Resolution.Resolved<RoundingPolicy>,
): Measurement<Derived<BidRate>> {
    val ratio = divideForRate(amount, vatTreatment, provenance, base.amount, base.vatTreatment, base.provenance, policy)
    return asRate(ratio, listOf(export(), base.export())) { rate -> BidRate(rate, origin) }
}

/** [sumOfBaseAmounts]가 목록을 접으며 나르는 중간 상태 — `Absent`가 나오면 이후 입력을 무시한다. */
private sealed interface SumState {
    data class Accumulating(
        val total: Long,
        val vat: VatTreatment?,
    ) : SumState

    data class Failed(
        val reason: ReasonCode,
    ) : SumState
}

private fun combine(
    state: SumState,
    entry: Fact<BaseAmount>,
): SumState =
    when {
        state is SumState.Failed -> state
        entry is Fact.Absent -> SumState.Failed(entry.reason)
        else -> accumulate(state as SumState.Accumulating, (entry as Fact.Known).value)
    }

/**
 * `seenVat == null`(첫 원소)이라고 전건을 건너뛰면 안 된다 — 이전 원소가 없을 뿐, 이
 * 원소 자체의 `vatTreatment`가 `UNKNOWN`이면 그 자체로 실패다(verifier r1 M-3, 단일
 * `UNKNOWN` 원소가 `Known`으로 새던 결함). `provenance` 검사를 `vat` 검사보다 먼저
 * 한다(Codex 1차 #1과 같은 순서 원칙 — `divideForRate` 참고) — 출처를 모르는 값은
 * vat 일관성을 따지기 전에 이미 계산에 못 쓴다.
 */
private fun accumulate(
    state: SumState.Accumulating,
    current: BaseAmount,
): SumState {
    val seenVat = state.vat
    val provenanceOk = hasDeclaredProvenance(current.provenance)
    val vatOk =
        when (seenVat) {
            null -> current.vatTreatment != VatTreatment.UNKNOWN
            else -> sameKnownVat(seenVat, current.vatTreatment)
        }
    val next =
        if (provenanceOk && vatOk) runCatching { Math.addExact(state.total, current.amount) }.getOrNull() else null
    return when {
        !provenanceOk -> SumState.Failed(ReasonCode.UNDECLARED_PROVENANCE)
        !vatOk -> SumState.Failed(ReasonCode.VAT_TREATMENT_MISMATCH)
        next == null -> SumState.Failed(ReasonCode.AMOUNT_OVERFLOW)
        else -> SumState.Accumulating(next, current.vatTreatment)
    }
}

/**
 * `BaseAmount` 목록의 합산 규칙 넷 — 전부 `Known`이면 `Known(합)`(overflow는 `AMOUNT_OVERFLOW`),
 * 하나라도 `Absent`면 그 사유를 그대로 전파, `vatTreatment`가 갈리면 `VAT_TREATMENT_MISMATCH`,
 * **빈 목록은 `Absent(EMPTY_INPUT)`다 — `Known(0원)`이 아니다.** 이 넷째 규칙은 승인 문면이
 * 직접 말하지 않는 자리라 1B가 형태로 정한다(값 결정이 아니므로 `OPEN`이 아니다).
 *
 * 원 단위 `Long`을 나르므로 공개하지 않는다 — `internal`.
 */
internal fun sumOfBaseAmounts(amounts: List<Fact<BaseAmount>>): Fact<Long> {
    val initial: SumState =
        if (amounts.isEmpty()) SumState.Failed(ReasonCode.EMPTY_INPUT) else SumState.Accumulating(0L, vat = null)
    return when (val result = amounts.fold(initial, ::combine)) {
        is SumState.Accumulating -> Fact.Known(result.total)
        is SumState.Failed -> Fact.Absent(result.reason)
    }
}
