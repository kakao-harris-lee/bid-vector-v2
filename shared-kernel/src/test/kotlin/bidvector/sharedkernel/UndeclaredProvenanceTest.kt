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

/**
 * Codex 1차 #1 — `v2-지침서.md` §4.1 "provenance가 없거나 모르는 값은 추측하지 않고 거부
 * 또는 `Unmeasurable`로 반환한다"의 산술·파생 성공 경계(`times`→`roundedWith`·
 * `divideForRate`·`sumOfBaseAmounts`) 적용을 모은다. `ArithmeticTest.kt` 에서 크기 한도
 * (`v2-지침서.md` §5, 500줄)로 옮겼다 — 주제가 뚜렷이 갈린다(입력 provenance 전건)는
 * 것이 분리 근거이지 크기 회피용 기계적 분할이 아니다. `base`·`resolvedPolicy`·
 * `ALL_ROUNDING_MODES`·`ALL_PROVENANCES` 는 `ArithmeticTest.kt` 의 `internal` 선언을
 * 그대로 쓴다(같은 모듈 test 소스셋 — 중복 선언하지 않는다).
 */
class UndeclaredProvenanceTest {
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
        val declaredYega = YegaAmount(1_100_000L, Currency.KRW, Provenance.Published(NoticeRound.of("001")))
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
}
