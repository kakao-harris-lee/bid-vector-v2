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
    provenance: Provenance = Provenance.OperatorDeclared,
): BaseAmount = BaseAmount(won, Currency.KRW, vat, provenance)

private val DECLARED_PROVENANCES =
    listOf(
        Provenance.Published(1),
        Provenance.DerivedFromOpening,
        Provenance.FilledFromBudgetKey("key"),
        Provenance.CopiedFromBaseAmount,
        Provenance.OperatorDeclared,
    )

private val ALL_PROVENANCES = DECLARED_PROVENANCES + Provenance.Undeclared

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
    fun `M-3 원소 하나뿐이면 그 값 그대로가 합이다`() {
        sumOfBaseAmounts(listOf(Fact.Known(base(500L)))) shouldBe Fact.Known(500L)
    }

    @Test
    fun `M-3 원소가 하나뿐이어도 그 vatTreatment 가 Unknown 이면 합은 부재다`() {
        // seenVat 이 null 로 시작하므로 첫 원소는 이전 원소와 비교할 대상이 없다 — 그렇다고
        // Unknown 이 전건을 그냥 통과해서는 안 된다(verifier r1 M-3). 이전에는 seenVat==null
        // 분기가 current.vatTreatment 자체를 보지 않아 이 케이스가 Fact.Known 으로 샜다.
        sumOfBaseAmounts(
            listOf(Fact.Known(base(500L, vat = VatTreatment.UNKNOWN))),
        ) shouldBe Fact.Absent(ReasonCode.VAT_TREATMENT_MISMATCH)
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
    fun `M-1 임의 mode·유효 scale 에서 roundedWith 는 예외를 누출하지 않는다`() {
        // scale 후보에서 -1 을 뺐다(verifier r2 M-6) — 음수 scaleDigits 는 이제
        // RoundingPolicy 생성 시점에 거부된다(PolicyTest 의 M-6 test 가 그 자리를 잰다).
        // 여기서 검증하는 것은 "유효한 정책이 어떤 원시값을 만나도 roundedWith 가 예외를
        // 던지지 않는다"이지 "정책 자체의 정의역"이 아니다 — 두 층을 섞지 않는다.
        runBlocking {
            checkAll(
                Arb.long(-1_000_000_000_000L, 1_000_000_000_000L),
                Arb.element(RoundingMode.entries),
                Arb.element(listOf(0, 1, 2)),
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
    fun `B9 YegaAmount 는 vatTreatment 를 Unknown 으로 고정 선언한다 — 다른 값을 넣을 수단이 없다`() {
        val yega = YegaAmount(1_000_000L, Currency.KRW, Provenance.Published(1))

        yega.vatTreatment shouldBe VatTreatment.UNKNOWN
    }

    @Test
    fun `B9 AllocatedBudget·AwardAmount 도 vatTreatment 를 Unknown 으로 고정 선언한다`() {
        val allocated = AllocatedBudget(500_000L, Currency.KRW, Provenance.OperatorDeclared)
        val award = AwardAmount(950_000L, Currency.KRW, Provenance.OperatorDeclared)

        allocated.vatTreatment shouldBe VatTreatment.UNKNOWN
        award.vatTreatment shouldBe VatTreatment.UNKNOWN
    }

    @Test
    fun `B9 YegaAmount 는 항상 Unknown 이라 사정률이 어떤 기초금액에도 Unmeasurable 이다`() {
        runBlocking {
            checkAll(Arb.long(1L, 10_000_000L), Arb.element(VatTreatment.entries)) { won, baseVat ->
                val yega = YegaAmount(1_000_000L, Currency.KRW, Provenance.Published(1))
                val theBase = base(won, vat = baseVat)

                val result = yega.assessmentRateAgainst(theBase, resolvedPolicy(RoundingMode.HALF_UP))

                result.shouldBeInstanceOf<Measurement.Unmeasurable>()
            }
        }
    }

    @Test
    fun `B9 AwardAmount 도 항상 Unknown 이라 낙찰률이 어떤 기초금액에도 Unmeasurable 이다`() {
        runBlocking {
            checkAll(Arb.long(1L, 10_000_000L), Arb.element(VatTreatment.entries)) { won, baseVat ->
                val award = AwardAmount(900_000L, Currency.KRW, Provenance.OperatorDeclared)
                val theBase = base(won, vat = baseVat)

                val result = award.awardRateAgainst(theBase, resolvedPolicy(RoundingMode.HALF_UP))

                result.shouldBeInstanceOf<Measurement.Unmeasurable>()
            }
        }
    }

    /**
     * B9 이후 [YegaAmount.assessmentRateAgainst]가 항상 `Unmeasurable`이 되면서, B11(파생 율이
     * 입력 fact 를 되짚는다)의 필드 단언을 낼 유일한 예제였던 「같은 vat 이면 사정률이 정상
     * 산출된다」가 구조적으로 불가능해졌다 — `bidRateAgainst`(vat 제약이 없는 `BidAmount` 축)로
     * 옮겨 B11 커버리지를 보존한다. `bidRateAgainst` 자체는 이전까지 어떤 test 도 부르지 않던
     * 자리라 이 test 가 그 공백도 함께 닫는다.
     */
    @Test
    fun `같은 vat 이면 투찰율이 정상 산출되고 B11 입력 fact 를 되짚는다`() {
        val theBase = base(1_000_000L, vat = VatTreatment.INCLUSIVE)
        val rate = BidRate(Rate.ofFraction(BigDecimal("0.955")), BidRateOrigin.Recommended)
        val rounded = (theBase * rate).roundedWith(resolvedPolicy(RoundingMode.HALF_UP))
        rounded.shouldBeInstanceOf<Measurement.Measured<Derived<BidAmount>>>()
        val bidAmount = rounded.value.value

        val result = bidAmount.bidRateAgainst(theBase, BidRateOrigin.Recommended, resolvedPolicy(RoundingMode.HALF_UP))

        result.shouldBeInstanceOf<Measurement.Measured<Derived<BidRate>>>()
        result.value.derivedFrom.inputs shouldBe listOf(bidAmount.export(), theBase.export())
    }

    /**
     * Codex 1차 #1 — `v2-지침서.md` §4.1 "provenance가 없거나 모르는 값은 추측하지 않고
     * 거부 또는 `Unmeasurable`로 반환한다"를 산술·파생 성공 경계(`times`→`roundedWith`,
     * `divideForRate`, `sumOfBaseAmounts`)에 건다. `Provenance.Undeclared`가 그 "모르는
     * 값"의 명시적 표현이다(`data-dictionary.md` §5.1 sealed 정의의 여섯째 variant).
     * `OPEN-DIC-06`(어댑터 write 경로가 `Undeclared`를 거부하는가)과는 다른 축이다 — 그
     * 결정은 **수집 시점** 수용 여부이고, 여기서 막는 것은 **이미 도메인에 들어온 값의
     * 계산** 이다. 값이 같아도 provenance 가 다른 fact 를 구분 못 하는 문제(Codex #4)와도
     * 다른 축 — 여기는 "구분"이 아니라 "출처 모름을 계산에 쓰지 않는다"이다.
     */
    @Test
    fun `Codex1 Undeclared provenance 인 기초금액은 times 로 만든 투찰가가 항상 Unmeasurable 이다`() {
        val undeclaredBase = base(1_000_000L, provenance = Provenance.Undeclared)
        val rate = BidRate(Rate.ofFraction(BigDecimal("0.955")), BidRateOrigin.Recommended)

        val result = (undeclaredBase * rate).roundedWith(resolvedPolicy(RoundingMode.HALF_UP))

        result shouldBe Measurement.Unmeasurable(ReasonCode.UNDECLARED_PROVENANCE)
    }

    @Test
    fun `Codex1 임의 mode·금액에서 Undeclared 기초금액은 항상 Unmeasurable 이다`() {
        runBlocking {
            checkAll(Arb.long(1L, 1_000_000_000L), Arb.element(ALL_ROUNDING_MODES)) { won, mode ->
                val undeclaredBase = base(won, provenance = Provenance.Undeclared)
                val rate = BidRate(Rate.ofFraction(BigDecimal("0.955")), BidRateOrigin.Recommended)

                val result = (undeclaredBase * rate).roundedWith(resolvedPolicy(mode))

                result shouldBe Measurement.Unmeasurable(ReasonCode.UNDECLARED_PROVENANCE)
            }
        }
    }

    /**
     * `YegaAmount`는 B9(운영자 결정)로 `vatTreatment`가 항상 `UNKNOWN` 고정이라
     * `assessmentRateAgainst`는 provenance 와 무관하게 이미 `VAT_TREATMENT_MISMATCH`로
     * 항상 `Unmeasurable`이다(B9 「사실상 죽은 경로」, `scope.md` `OPEN-DIC-04` 참고).
     * 여기서 증명하는 것은 「Undeclared 가 그 실패 사유를 가린다」— `divideForRate`가
     * vat 검사보다 provenance 검사를 먼저 하므로, provenance 가 Undeclared 면 vat 상태와
     * 무관하게 `UNDECLARED_PROVENANCE`가 나온다(둘 다 실패해도 사유는 하나만 낸다).
     * "declared 면 성공" 대조는 vat 축이 이미 막혀 있어 이 함수로는 못 낸다 — 그 대조는
     * vat 제약이 없는 [bidRateAgainst] 로 아래에서 낸다.
     */
    @Test
    fun `Codex1 분자·분모 어느 쪽이 Undeclared 여도 사정률은 Unmeasurable 이다`() {
        val declaredYega = YegaAmount(1_100_000L, Currency.KRW, Provenance.Published(1))
        val declaredBase = base(1_000_000L, vat = VatTreatment.INCLUSIVE)
        val undeclaredYega = YegaAmount(1_100_000L, Currency.KRW, Provenance.Undeclared)
        val undeclaredBase = base(1_000_000L, vat = VatTreatment.INCLUSIVE, provenance = Provenance.Undeclared)

        val numeratorUndeclared =
            undeclaredYega.assessmentRateAgainst(declaredBase, resolvedPolicy(RoundingMode.HALF_UP))
        val denominatorUndeclared =
            declaredYega.assessmentRateAgainst(undeclaredBase, resolvedPolicy(RoundingMode.HALF_UP))

        numeratorUndeclared shouldBe Measurement.Unmeasurable(ReasonCode.UNDECLARED_PROVENANCE)
        denominatorUndeclared shouldBe Measurement.Unmeasurable(ReasonCode.UNDECLARED_PROVENANCE)
    }

    @Test
    fun `Codex1 Undeclared AwardAmount 는 낙찰률을 Unmeasurable 로 막는다`() {
        val undeclaredAward = AwardAmount(950_000L, Currency.KRW, Provenance.Undeclared)
        val declaredBase = base(1_000_000L, vat = VatTreatment.INCLUSIVE)

        val result = undeclaredAward.awardRateAgainst(declaredBase, resolvedPolicy(RoundingMode.HALF_UP))

        result shouldBe Measurement.Unmeasurable(ReasonCode.UNDECLARED_PROVENANCE)
    }

    @Test
    fun `Codex1 Undeclared 기초금액은 투찰율도 Unmeasurable 로 막는다`() {
        val theBase = base(1_000_000L, vat = VatTreatment.INCLUSIVE, provenance = Provenance.Undeclared)
        val rate = BidRate(Rate.ofFraction(BigDecimal("0.955")), BidRateOrigin.Recommended)
        val rounded =
            (base(1_000_000L, vat = VatTreatment.INCLUSIVE) * rate).roundedWith(resolvedPolicy(RoundingMode.HALF_UP))
        rounded.shouldBeInstanceOf<Measurement.Measured<Derived<BidAmount>>>()
        val bidAmount = rounded.value.value

        val result = bidAmount.bidRateAgainst(theBase, BidRateOrigin.Recommended, resolvedPolicy(RoundingMode.HALF_UP))

        result shouldBe Measurement.Unmeasurable(ReasonCode.UNDECLARED_PROVENANCE)
    }

    /**
     * `BidAmount`·`BaseAmount` 축은 B9 의 vat 고정을 받지 않아(둘 다 `vatTreatment`가
     * 자유 파라미터다) "declared 면 성공, Undeclared 면 실패"를 같은 test 로 대조할 수
     * 있는 유일한 파생 율 경로다 — 여기서 team-lead 가 요구한 property(임의 provenance
     * 조합에서 Undeclared 하나라도 있으면 실패)를 낸다. `BidAmount`는 `internal` 생성자지만
     * 이 test 가 같은 모듈(shared-kernel) 안이라 직접 생성할 수 있다(H-1 이 막는 것은
     * 모듈 밖 생성이다).
     */
    @Test
    fun `Codex1 임의 provenance 조합에서 Undeclared 하나라도 있으면 투찰율은 Unmeasurable 이고 아니면 정상 산출된다`() {
        runBlocking {
            checkAll(
                Arb.element(ALL_PROVENANCES),
                Arb.element(ALL_PROVENANCES),
            ) { bidProvenance, baseProvenance ->
                val bidAmount = BidAmount(955_000L, Currency.KRW, VatTreatment.INCLUSIVE, bidProvenance)
                val theBase = base(1_000_000L, vat = VatTreatment.INCLUSIVE, provenance = baseProvenance)

                val result =
                    bidAmount.bidRateAgainst(theBase, BidRateOrigin.Recommended, resolvedPolicy(RoundingMode.HALF_UP))

                val eitherUndeclared = bidProvenance == Provenance.Undeclared || baseProvenance == Provenance.Undeclared
                if (eitherUndeclared) {
                    result shouldBe Measurement.Unmeasurable(ReasonCode.UNDECLARED_PROVENANCE)
                } else {
                    result.shouldBeInstanceOf<Measurement.Measured<Derived<BidRate>>>()
                }
            }
        }
    }

    @Test
    fun `Codex1 Undeclared 원소가 있으면 sumOfBaseAmounts 는 사유 있는 부재다`() {
        sumOfBaseAmounts(
            listOf(Fact.Known(base(500L, provenance = Provenance.Undeclared))),
        ) shouldBe Fact.Absent(ReasonCode.UNDECLARED_PROVENANCE)

        sumOfBaseAmounts(
            listOf(
                Fact.Known(base(100L)),
                Fact.Known(base(200L, provenance = Provenance.Undeclared)),
                Fact.Known(base(300L)),
            ),
        ) shouldBe Fact.Absent(ReasonCode.UNDECLARED_PROVENANCE)
    }

    @Test
    fun `Codex1 임의 목록에서 Undeclared 원소 하나라도 있으면 합은 항상 Absent 다`() {
        runBlocking {
            checkAll(
                Arb.element(ALL_PROVENANCES),
                Arb.element(ALL_PROVENANCES),
                Arb.long(0L, 1_000_000L),
                Arb.long(0L, 1_000_000L),
            ) { firstProvenance, secondProvenance, firstWon, secondWon ->
                val result =
                    sumOfBaseAmounts(
                        listOf(
                            Fact.Known(base(firstWon, provenance = firstProvenance)),
                            Fact.Known(base(secondWon, provenance = secondProvenance)),
                        ),
                    )

                val eitherUndeclared =
                    firstProvenance == Provenance.Undeclared || secondProvenance == Provenance.Undeclared
                if (eitherUndeclared) {
                    result shouldBe Fact.Absent(ReasonCode.UNDECLARED_PROVENANCE)
                } else {
                    result shouldBe Fact.Known(firstWon + secondWon)
                }
            }
        }
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
