package bidvector.sharedkernel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

private val ALL_ROUNDING_MODES =
    listOf(RoundingMode.HALF_UP, RoundingMode.HALF_DOWN, RoundingMode.HALF_EVEN, RoundingMode.DOWN, RoundingMode.UP)

private fun resolvedPolicy(mode: RoundingMode): Resolution.Resolved<RoundingPolicy> {
    val policy = RoundingPolicy(MONEY_AXIS_SCALE_DIGITS, mode)
    return Resolution.Resolved(policy, PolicyVersion(EffectiveFrom.Initial, "test"))
}

private fun base(
    won: Long,
    vat: VatTreatment = VatTreatment.INCLUSIVE,
): BaseAmount = BaseAmount(won, Currency.KRW, vat, Provenance.OperatorDeclared)

class ArithmeticTest {
    @Test
    fun `P-3c sameKnownVat 는 Unknown 곱하기 Unknown 을 통과시키지 않는다`() {
        runBlocking {
            checkAll(Arb.element(VatTreatment.entries)) { vat ->
                val same = sameKnownVat(vat, vat)
                val expected = vat != VatTreatment.UNKNOWN
                same shouldBe expected
            }
        }
    }

    @Test
    fun `P-2a 반올림 결과는 원 단위 정수 Money 가 된다`() {
        runBlocking {
            checkAll(Arb.long(1L, 1_000_000_000L), Arb.element(ALL_ROUNDING_MODES)) { won, mode ->
                val rate = BidRate(Rate.ofFraction(BigDecimal("0.955")), BidRateOrigin.Recommended)

                val result = (base(won) * rate).roundedWith(resolvedPolicy(mode))

                result.shouldBeInstanceOf<Measurement.Measured<Derived<BidAmount>>>()
            }
        }
    }

    @Test
    fun `P-2b mode 가 무엇이든 반올림된 투찰가는 적용 하한 이상이다 (DEC-02 무조건 acceptance)`() {
        runBlocking {
            checkAll(
                Arb.long(0L, 1_000_000_000L),
                Arb.long(0L, 999L),
                Arb.element(ALL_ROUNDING_MODES),
            ) { floor, epsilonWon, mode ->
                // 하한 바로 위 잔차 구간(floor + ε, ε < 1원)을 집중 생성한다.
                val unrounded = BigDecimal(floor).add(BigDecimal(epsilonWon).movePointLeft(3))

                val raw =
                    UnroundedBidAmount(
                        unrounded,
                        Currency.KRW,
                        VatTreatment.INCLUSIVE,
                        Provenance.OperatorDeclared,
                        base(floor).export(),
                    )
                val result = raw.roundedWith(resolvedPolicy(mode))

                val roundedWon =
                    when (result) {
                        is Measurement.Measured -> {
                            val bidAmount = result.value.value
                            bidAmount.export().won
                        }

                        is Measurement.Unmeasurable -> {
                            error("overflow 는 이 생성 범위에서 발생하지 않는다: $result")
                        }
                    }

                (roundedWon >= floor) shouldBe true
            }
        }
    }

    @Test
    fun `P-4 부재가 섞이면 합은 부재다 — 빈 목록도 0원이 아니라 부재다`() {
        sumOfBaseAmounts(emptyList()) shouldBe Fact.Absent(ReasonCode.EMPTY_INPUT)

        sumOfBaseAmounts(listOf(Fact.Known(base(0L)), Fact.Known(base(0L)))) shouldBe Fact.Known(0L)

        sumOfBaseAmounts(
            listOf(Fact.Known(base(100L)), Fact.Absent(ReasonCode.UNIT_NOT_DECLARED), Fact.Known(base(200L))),
        ) shouldBe Fact.Absent(ReasonCode.UNIT_NOT_DECLARED)
    }

    @Test
    fun `P-4 전부 Known 이면 정확한 합을 낸다`() {
        runBlocking {
            checkAll(Arb.long(0L, 1_000_000L), Arb.long(0L, 1_000_000L)) { first, second ->
                val result = sumOfBaseAmounts(listOf(Fact.Known(base(first)), Fact.Known(base(second))))

                result shouldBe Fact.Known(first + second)
            }
        }
    }

    @Test
    fun `P-5 overflow 는 조용히 감기지 않고 사유 있는 실패를 낸다 (A4, O-1)`() {
        val overflowing =
            listOf(
                Fact.Known(base(Long.MAX_VALUE)),
                Fact.Known(base(1L)),
            )

        sumOfBaseAmounts(overflowing) shouldBe Fact.Absent(ReasonCode.AMOUNT_OVERFLOW)
    }

    @Test
    fun `M-1 UNNECESSARY 모드에서 반올림이 필요한 값은 overflow 가 아닌 사유로 Unmeasurable 이 된다`() {
        val raw =
            UnroundedBidAmount(
                BigDecimal("100.5"),
                Currency.KRW,
                VatTreatment.INCLUSIVE,
                Provenance.OperatorDeclared,
                base(100L).export(),
            )
        val policy = RoundingPolicy(MONEY_AXIS_SCALE_DIGITS, RoundingMode.UNNECESSARY)
        val resolved = Resolution.Resolved(policy, PolicyVersion(EffectiveFrom.Initial, "test"))

        // setScale(0, UNNECESSARY) 자체가 ArithmeticException 을 던진다 — overflow 가 아니다.
        // runCatching 밖에 있으면 이 호출이 예외를 그대로 던져 테스트가 에러로 죽는다.
        raw.roundedWith(resolved) shouldBe Measurement.Unmeasurable(ReasonCode.ROUNDING_NOT_REPRESENTABLE)
    }

    @Test
    fun `M-1 임의 mode·scale 에서 roundedWith 는 예외를 누출하지 않는다`() {
        runBlocking {
            checkAll(
                Arb.long(-1_000_000_000_000L, 1_000_000_000_000L),
                Arb.element(RoundingMode.entries),
                Arb.element(listOf(0, 1, 2, -1)),
            ) { unscaledLong, mode, scale ->
                val raw =
                    UnroundedBidAmount(
                        BigDecimal.valueOf(unscaledLong, 3),
                        Currency.KRW,
                        VatTreatment.INCLUSIVE,
                        Provenance.OperatorDeclared,
                        base(0L).export(),
                    )
                val policy = RoundingPolicy(scale, mode)
                val resolved = Resolution.Resolved(policy, PolicyVersion(EffectiveFrom.Initial, "test"))

                // 예외가 누출되면 이 호출 자체가 테스트를 에러로 죽인다 — 반환이 오는 것 자체가 단언이다.
                val result = raw.roundedWith(resolved)

                result.shouldBeInstanceOf<Measurement<Derived<BidAmount>>>()
            }
        }
    }

    @Test
    fun `P-5 반올림 경로도 overflow 를 Unmeasurable 로 바꾼다`() {
        val huge =
            UnroundedBidAmount(
                BigDecimal("99999999999999999999"),
                Currency.KRW,
                VatTreatment.INCLUSIVE,
                Provenance.OperatorDeclared,
                base(1L).export(),
            )

        huge.roundedWith(resolvedPolicy(RoundingMode.HALF_UP)) shouldBe
            Measurement.Unmeasurable(ReasonCode.AMOUNT_OVERFLOW)
    }

    @Test
    fun `vatTreatment 가 다르거나 Unknown 이면 율 계산이 Unmeasurable 을 낸다 (P-3c, B9)`() {
        val yega = YegaAmount(1_000_000L, Currency.KRW, VatTreatment.EXCLUSIVE, Provenance.Published(1))
        val mismatchedBase = base(500_000L, vat = VatTreatment.INCLUSIVE)
        val unknownBase = base(500_000L, vat = VatTreatment.UNKNOWN)
        val yegaUnknown = YegaAmount(1_000_000L, Currency.KRW, VatTreatment.UNKNOWN, Provenance.Published(1))

        val mismatched = yega.assessmentRateAgainst(mismatchedBase, resolvedPolicy(RoundingMode.HALF_UP))
        val bothUnknown = yegaUnknown.assessmentRateAgainst(unknownBase, resolvedPolicy(RoundingMode.HALF_UP))

        mismatched.shouldBeInstanceOf<Measurement.Unmeasurable>()
        bothUnknown.shouldBeInstanceOf<Measurement.Unmeasurable>()
    }

    @Test
    fun `같은 vat 이면 사정률이 정상 산출된다`() {
        val yega = YegaAmount(1_100_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.Published(1))
        val base = base(1_000_000L, vat = VatTreatment.INCLUSIVE)

        val result = yega.assessmentRateAgainst(base, resolvedPolicy(RoundingMode.HALF_UP))

        result.shouldBeInstanceOf<Measurement.Measured<Derived<AssessmentRate>>>()
        val fraction = result.value.value.rate.fraction
        fraction.compareTo(BigDecimal("1.1")) shouldBe 0

        // B11 — 파생 율은 입력 fact(예정가·기초금액) 의 AmountRecord 를 되짚는다.
        result.value.derivedFrom.inputs shouldBe listOf(yega.export(), base.export())
    }

    @Test
    fun `EffectiveFrom Initial 이 RoundingPolicy 배관에도 쓰인다`() {
        val policyVersion = resolvedPolicy(RoundingMode.HALF_UP).version

        policyVersion.effectiveFrom shouldBe EffectiveFrom.Initial
    }

    @Test
    fun `공고 기준일이 있는 정책 version 배관도 확인한다`() {
        val version = PolicyVersion(EffectiveFrom.On(LocalDate.of(2026, 9, 4)), "test")

        version.source shouldBe "test"
    }
}
