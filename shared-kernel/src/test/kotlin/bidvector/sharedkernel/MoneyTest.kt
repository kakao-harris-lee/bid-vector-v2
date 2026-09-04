package bidvector.sharedkernel

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private fun sampleBaseAmount(
    won: Long = 1_000_000L,
    vat: VatTreatment = VatTreatment.INCLUSIVE,
): BaseAmount = BaseAmount(won, Currency.KRW, vat, Provenance.OperatorDeclared)

class MoneyTest {
    @Test
    fun `다섯 성분을 갖춘 값만 Money 다`() {
        val base = sampleBaseAmount(won = 12_345L)

        base.export() shouldBe
            AmountRecord(12_345L, Currency.KRW, Basis.BASE_AMOUNT, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared)
    }

    @Test
    fun `basis 는 타입이 내는 파생값이라 copy 로 바꿀 수 없다`() {
        val base = sampleBaseAmount()

        // BaseAmount.copy() 의 named-argument 목록에 basis 가 없다 — 컴파일 자체가 그 계약이다.
        val copied = base.copy(won = 999L)

        copied.basis shouldBe Basis.BASE_AMOUNT
    }

    @Test
    fun `음수 금액은 구성상 거부된다 (E-2, 조사 A M-9)`() {
        assertThrows<IllegalArgumentException> {
            sampleBaseAmount(won = -1L)
        }
    }

    @Test
    fun `서로 다른 basis 금액은 같은 타입이 아니라 컴파일이 안 된다`() {
        val base = sampleBaseAmount()
        val estimated = EstimatedAmount(1_000_000L, Currency.KRW, VatTreatment.EXCLUSIVE, Provenance.Published(1))

        // `Money` 상위 타입에 이항 연산이 없어 f(a: Money, b: Money) 로 basis 를 섞는 시그니처가
        // 존재하지 않는다 — 여기서는 각 타입이 자기 축의 compareKnownVat 만 받음을 확인한다
        // (Codex 1차 #2 — 공개 Comparable 을 없앤 뒤의 유일한 비교 경로).
        compareKnownVat(base, base) shouldBe Fact.Known(0)
        compareKnownVat(estimated, estimated) shouldBe Fact.Known(0)
    }

    /**
     * Codex 1차 #2 — 여섯 타입의 `compareTo`가 `won`만 비교해 `VAT` `UNKNOWN`과 `INCLUSIVE`
     * 금액도 정렬되고 동일 금액이면 `VAT`가 달라도 0을 냈다. 공개 `Comparable` 을 없애고
     * `sameKnownVat` 전건을 건 `compareKnownVat`로 대체한다 — 다른 `VAT`는 비교 자체가
     * 거부된다.
     *
     * **`Measurement` 대신 `Fact`를 반환한다** — 비교는 정책 version 을 소비하지 않는다
     * (`sumOfBaseAmounts`가 이미 같은 이유로 `Fact`를 쓴다). `Measurement.Measured`가
     * 요구하는 `policyVersion`/`sampleSize`는 비교에 자연스러운 입력이 없어 지어내는
     * 것이 되므로(매직 넘버 금지 원칙과 같은 성질), Codex 전달문이 든 "Unmeasurable"
     * 대신 `Fact.Absent`로 낸다 — `VAT_TREATMENT_MISMATCH` 사유는 그대로다.
     */
    @Test
    fun `Codex2 같은 known vat 이면 compareKnownVat 는 won 비교 결과를 낸다`() {
        val lower = sampleBaseAmount(won = 1_000L, vat = VatTreatment.INCLUSIVE)
        val higher = sampleBaseAmount(won = 2_000L, vat = VatTreatment.INCLUSIVE)

        compareKnownVat(lower, higher) shouldBe Fact.Known(-1)
        compareKnownVat(higher, lower) shouldBe Fact.Known(1)
        compareKnownVat(lower, lower) shouldBe Fact.Known(0)
    }

    @Test
    fun `Codex2 같은 금액이라도 vat 가 다르면 compareKnownVat 는 거부한다`() {
        val inclusive = sampleBaseAmount(won = 1_000_000L, vat = VatTreatment.INCLUSIVE)
        val exclusive = sampleBaseAmount(won = 1_000_000L, vat = VatTreatment.EXCLUSIVE)

        compareKnownVat(inclusive, exclusive) shouldBe Fact.Absent(ReasonCode.VAT_TREATMENT_MISMATCH)
    }

    @Test
    fun `Codex2 UNKNOWN 대 UNKNOWN 은 compareKnownVat 가 거부한다`() {
        val firstUnknown = YegaAmount(1_000_000L, Currency.KRW, Provenance.OperatorDeclared)
        val secondUnknown = YegaAmount(2_000_000L, Currency.KRW, Provenance.OperatorDeclared)

        compareKnownVat(firstUnknown, secondUnknown) shouldBe Fact.Absent(ReasonCode.VAT_TREATMENT_MISMATCH)
    }

    @Test
    fun `Codex2 임의 같은 vat 금액 쌍은 compareKnownVat 가 won 순서와 일치한다`() {
        runBlocking {
            checkAll(Arb.long(0L, 1_000_000_000_000L), Arb.long(0L, 1_000_000_000_000L)) { leftWon, rightWon ->
                val left = sampleBaseAmount(won = leftWon, vat = VatTreatment.INCLUSIVE)
                val right = sampleBaseAmount(won = rightWon, vat = VatTreatment.INCLUSIVE)

                compareKnownVat(left, right) shouldBe Fact.Known(leftWon.compareTo(rightWon))
            }
        }
    }

    @Test
    fun `P-3b 어느 값 조합에서도 basis 가 다른 금액은 비교·대입 경로가 없다`() {
        // `runBlocking { checkAll(…) }` 를 식 본문(=)으로 쓰면 함수 반환형이 `PropertyContext`로
        // 추론돼 JUnit Jupiter 가 이 메서드를 test 로 인식하지 못하고 조용히 건너뛴다(실측:
        // javap 로 반환형 확인, `TEST-*.xml` 의 testcase 4/6 만 등재). 블록 본문으로 Unit 을 고정한다.
        runBlocking {
            checkAll(
                Arb.long(0L, 1_000_000_000_000L),
                Arb.long(0L, 1_000_000_000_000L),
            ) { baseWon, estimatedWon ->
                val base = sampleBaseAmount(won = baseWon)
                val estimated =
                    EstimatedAmount(estimatedWon, Currency.KRW, VatTreatment.EXCLUSIVE, Provenance.Undeclared)

                // 두 타입에 공통 상위의 compareTo/이항 연산이 없어 `base > estimated` 류의 표현
                // 자체가 이 파일 밖에서 컴파일되지 않는다. 런타임으로 확인할 수 있는 것은
                // export() 가 서로 다른 basis 를 정확히 보고한다는 것뿐이다.
                base.export().basis shouldBe Basis.BASE_AMOUNT
                estimated.export().basis shouldBe Basis.ESTIMATED
            }
        }
    }
}
