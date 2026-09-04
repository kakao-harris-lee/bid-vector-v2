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

internal val ALL_ROUNDING_MODES =
    listOf(RoundingMode.HALF_UP, RoundingMode.HALF_DOWN, RoundingMode.HALF_EVEN, RoundingMode.DOWN, RoundingMode.UP)

internal fun resolvedPolicy(mode: RoundingMode): Resolution.Resolved<RoundingPolicy> {
    val policy = RoundingPolicy(MONEY_AXIS_SCALE_DIGITS, mode)
    return Resolution.Resolved(policy, PolicyVersion(EffectiveFrom.Initial, "test"))
}

internal fun base(
    won: Long,
    vat: VatTreatment = VatTreatment.INCLUSIVE,
    provenance: Provenance = Provenance.OperatorDeclared,
): BaseAmount = BaseAmount(won, Currency.KRW, vat, provenance)

internal val DECLARED_PROVENANCES =
    listOf(
        Provenance.Published(1),
        Provenance.DerivedFromOpening,
        Provenance.FilledFromBudgetKey("key"),
        Provenance.CopiedFromBaseAmount,
        Provenance.OperatorDeclared,
    )

internal val ALL_PROVENANCES = DECLARED_PROVENANCES + Provenance.Undeclared

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

    /**
     * Codex 1차 #3 — 이전 판은 `floor`(정수)에 `ε < 1원`을 더한 원시값만 생성해, 반올림
     * 뒤 값이 항상 그 **정수** `floor` 이상으로만 떨어졌다(`DOWN`이 최악이어도 정수부
     * `floor` 로 떨어지지 `floor` 미만으로는 못 간다) — 그래서 "예외 없음"이 곧 "하한
     * 이상"으로 보였다. 실제 반례는 `floor` **자신이 소수**일 때다(`data-dictionary.md`
     * §1.1 정의 ②가 요구하는 하한은 원 단위로 딱 떨어진다는 보장이 없다 — 하한율×기초금액
     * 같은 계산에서 나온다). `floor=1000.4`·`scale=0`·`DOWN` 이면 결과가 `1000`인데 이는
     * `1000.4` **미만**이다.
     *
     * **처리 방식 판단(evidence)**: clamp(하한으로 올림) 대신 `Unmeasurable`(새
     * `ReasonCode.ROUNDED_BELOW_FLOOR`)을 택했다 — `capability-map.md` DEC-02 acceptance가
     * "최종 추천가가 어느 제약에 binding됐는지가 결과에 실린다"를 요구하는데, 그 binding
     * 추적은 DEC-02 의 더 큰 산정 알고리즘(하한·상한·신뢰비율)의 몫이지 1B 의 순수 반올림
     * 함수가 가질 장치가 아니다. `roundedWith` 가 스스로 값을 하한으로 올려 버리면(clamp)
     * 그 값이 "왜 그 값인지"(원 계산값 그대로인지, clamp 로 올라간 값인지)를 잃어 DEC-02
     * 의 그 요구를 오히려 어길 위험이 있다 — 사유 있는 실패로 돌려주면 그 판단(clamp 할지,
     * 다른 값을 찾을지)을 DEC-02 알고리즘을 실제로 구현하는 slice 가 온전한 정보로 내릴
     * 수 있다.
     */
    @Test
    fun `Codex3 소수 하한 1000_4 에서 scale0 DOWN 은 하한 미만 값을 조용히 내지 않는다`() {
        val floor = BigDecimal("1000.4")
        val raw =
            UnroundedBidAmount(
                floor,
                Currency.KRW,
                VatTreatment.INCLUSIVE,
                Provenance.OperatorDeclared,
                base(1000L).export(),
            )

        val result = raw.roundedWith(resolvedPolicy(RoundingMode.DOWN), floor)

        result shouldBe Measurement.Unmeasurable(ReasonCode.ROUNDED_BELOW_FLOOR)
    }

    @Test
    fun `Codex3 하한을 안 주면 이전처럼 하한 미만도 성공한다 (기존 호출부 호환)`() {
        val floor = BigDecimal("1000.4")
        val raw =
            UnroundedBidAmount(
                floor,
                Currency.KRW,
                VatTreatment.INCLUSIVE,
                Provenance.OperatorDeclared,
                base(1000L).export(),
            )

        val result = raw.roundedWith(resolvedPolicy(RoundingMode.DOWN))

        result.shouldBeInstanceOf<Measurement.Measured<Derived<BidAmount>>>()
        result.value.value
            .export()
            .won shouldBe 1000L
    }

    /**
     * `P-2b` 정정(Codex 1차 #3) — 이전 판이 놓친 소수 하한 반례를 임의 mode 전역에서
     * 낸다. 원시값을 하한과 **정확히 같게**(Codex 반례와 같은 가장 빡빡한 경계) 두고,
     * `setScale` 오라클로 기대값을 직접 계산해 비교한다 — mode 별 반올림 방향을 하드코딩
     * 하지 않는다. **하한 검사를 지우는 mutant가 이 test 를 실패시킨다** — `DOWN`·
     * `HALF_DOWN`류가 하한 바로 아래로 접힐 때마다 `Unmeasurable` 을 기대하므로, 검사가
     * 없으면 그 자리에서 `Measured`(다른 값)가 나와 단언이 깨진다.
     */
    @Test
    fun `P-2b mode 가 무엇이든 반올림된 투찰가는 적용 하한 이상이거나 사유 있는 실패다 (DEC-02 무조건 acceptance)`() {
        runBlocking {
            checkAll(
                Arb.long(0L, 1_000_000_000L),
                Arb.long(1L, 999L),
                Arb.element(ALL_ROUNDING_MODES),
            ) { floorWon, floorFractionMil, mode ->
                // 하한 자신이 소수다 — 원 단위로 딱 떨어지지 않는 실제 적용 하한(하한율×기초금액
                // 같은 계산 결과)을 흉내낸다. 원시값은 그 하한과 정확히 같게 둔다(가장 빡빡한 경계).
                val floor = BigDecimal(floorWon).add(BigDecimal(floorFractionMil).movePointLeft(3))
                val raw =
                    UnroundedBidAmount(
                        floor,
                        Currency.KRW,
                        VatTreatment.INCLUSIVE,
                        Provenance.OperatorDeclared,
                        base(floorWon).export(),
                    )

                val result = raw.roundedWith(resolvedPolicy(mode), floor)

                val oracleScaled = floor.setScale(MONEY_AXIS_SCALE_DIGITS, mode)
                if (oracleScaled.compareTo(floor) >= 0) {
                    result.shouldBeInstanceOf<Measurement.Measured<Derived<BidAmount>>>()
                    val won =
                        result.value.value
                            .export()
                            .won
                    (BigDecimal(won).compareTo(floor) >= 0) shouldBe true
                } else {
                    result shouldBe Measurement.Unmeasurable(ReasonCode.ROUNDED_BELOW_FLOOR)
                }
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
