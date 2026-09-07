package bidvector.procurement

import bidvector.sharedkernel.Basis
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

private val REFERENCE_DATE: LocalDate = LocalDate.of(2026, 9, 7)

private val RESOLVED_POLICY: KonepsCollectionPolicyData =
    when (val resolution = KONEPS_COLLECTION_POLICY.resolve(REFERENCE_DATE)) {
        is Resolution.Resolved -> resolution.value
        is Resolution.NotApplicable -> error("KONEPS_COLLECTION_POLICY 가 $REFERENCE_DATE 에 해석되지 않는다: ${resolution.reason}")
    }

/**
 * 운영 정책 인스턴스와 승인 표(`policy-values.md` §6)의 일치 대조 — 옮겨 적기 오류 방지
 * (team-lead 지시, 3A 잔여 일괄 ①). 표 항목 수와 대표 키 몇 개의 값을 대조한다.
 */
class CollectionPolicyTest {
    @Test
    fun `필드 계약은 승인된 채택분 열 개만 등재한다 — 미확정 칸은 인스턴스화하지 않는다`() {
        RESOLVED_POLICY.fieldContracts.contracts.map { it.rawName.name }.toSet() shouldBe
            setOf(
                "bidNtceNo",
                "bidNtceOrd",
                "bssamt",
                "asignBdgtAmt",
                "bdgtAmt",
                "presmptPrce",
                "sucsfbidLwltRate",
                "bidClseDt",
                "opengDt",
                "bsnsDivNm",
            )
    }

    @Test
    fun `presmptPrce 는 과세 제외·원화 단위다 — policy-values md 1-1`() {
        val contract = RESOLVED_POLICY.fieldContracts.contractFor(RawKey("presmptPrce"))!!

        contract.vatTreatment shouldBe VatTreatment.EXCLUSIVE
        contract.unit shouldBe FieldUnit.WON
        contract.basis shouldBe Basis.ESTIMATED
    }

    @Test
    fun `asignBdgtAmt·bdgtAmt 는 과세 미선언(UNKNOWN)이고 배정예산 basis 다 — 1-1`() {
        val fields = listOf("asignBdgtAmt", "bdgtAmt").map { RESOLVED_POLICY.fieldContracts.contractFor(RawKey(it))!! }

        fields.forEach { contract ->
            contract.vatTreatment shouldBe VatTreatment.UNKNOWN
            contract.basis shouldBe Basis.ALLOCATED_BUDGET
            contract.provenanceTemplate shouldBe FieldProvenanceTemplate.FILLED_FROM_BUDGET_KEY
        }
    }

    @Test
    fun `bssamt 가 문서로 서는 유일한 기초금액 raw 키다 — bssAmt·bssAmtPurcnstcst 는 등재되지 않는다`() {
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bssamt"))?.basis shouldBe Basis.BASE_AMOUNT
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bssAmt")) shouldBe null
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bssAmtPurcnstcst")) shouldBe null
    }

    @Test
    fun `기초금액 해석 순서는 bssamt 를 앞세우고 배정예산 폴백 둘을 잇는다 — P-3`() {
        RESOLVED_POLICY.baseAmountResolutionOrder shouldBe
            listOf(RawKey("bssamt"), RawKey("asignBdgtAmt"), RawKey("bdgtAmt"))
        RESOLVED_POLICY.estimatedPriceResolutionOrder shouldBe listOf(RawKey("presmptPrce"))
    }

    @Test
    fun `resultCode 범주는 16 코드를 담고 03 은 NO_DATA 다 — P-4`() {
        RESOLVED_POLICY.resultCodeCategories.size shouldBe 16
        RESOLVED_POLICY.resultCodeCategories.first { it.code == "03" }.category shouldBe ResultCodeCategory.NO_DATA
        RESOLVED_POLICY.resultCodeCategories.first { it.code == "08" }.category shouldBe ResultCodeCategory.INPUT_ERROR
        RESOLVED_POLICY.resultCodeCategories.first { it.code == "22" }.category shouldBe ResultCodeCategory.QUOTA_EXCEEDED
        RESOLVED_POLICY.resultCodeCategories.first { it.code == "30" }.category shouldBe ResultCodeCategory.NOT_RETRYABLE
    }

    @Test
    fun `일시 두 필드는 ASSUME_KST 규칙을 갖는다 — P-2`() {
        listOf("bidClseDt", "opengDt").forEach { key ->
            RESOLVED_POLICY.fieldContracts.contractFor(RawKey(key))!!.sourceZone shouldBe SourceZoneRuleId.ASSUME_KST
        }
    }

    @Test
    fun `조회 가치 게이트는 24h_48h 잠정값을 담는다 — P-5`() {
        RESOLVED_POLICY.detailFetchGates shouldBe DetailFetchGates(ageGateHours = 24, recheckGateHours = 48)
    }
}
