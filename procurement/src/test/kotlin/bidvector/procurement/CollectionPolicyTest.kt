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
        is Resolution.Resolved -> {
            resolution.value
        }

        is Resolution.NotApplicable -> {
            error("KONEPS_COLLECTION_POLICY 가 $REFERENCE_DATE 에 해석되지 않는다: ${resolution.reason}")
        }
    }

/**
 * 운영 정책 인스턴스와 승인 표(`policy-values.md` §6)의 일치 대조 — 옮겨 적기 오류 방지
 * (team-lead 지시, 3A 잔여 일괄 ①). 표 항목 수와 대표 키 몇 개의 값을 대조한다.
 */
class CollectionPolicyTest {
    @Test
    fun `필드 계약은 승인된 채택분 스물세 개만 등재한다 — 미확정 칸은 인스턴스화하지 않는다`() {
        RESOLVED_POLICY.fieldContracts.contracts
            .map { it.rawName.name }
            .toSet() shouldBe
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
                // v2-defect 018 수정(3A 잔여 일괄 verifier r3 전) — D-3A-8, §5.5.
                "cnstrtnAbltyEvlAmtList",
                // P-9 승인(3B-2, 2026-09-08) — 개찰 축 12행. `bidwinnrBizno`는 P-10 (a) 로
                // 저장하지 않아 등재되지 않는다(13행 중 12행만 인스턴스화).
                "sucsfbidAmt",
                "sucsfbidRate",
                "bidwinnrNm",
                "rlOpengDt",
                "prtcptCnum",
                "fnlSucsfDate",
                "plnprc",
                "bsisPlnprc",
                "compnoRsrvtnPrceSno",
                "drwtYn",
                "progrsDivCdNm",
                "opengCorpInfo",
            )
    }

    @Test
    fun `개찰 축 금액 개념은 각자 basis 를 갖는다 — sucsfbidAmt AWARD, plnprc YEGA, bsisPlnprc 는 basis 미확정(P-9)`() {
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("sucsfbidAmt"))!!.basis shouldBe Basis.AWARD
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("plnprc"))!!.basis shouldBe Basis.YEGA
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bsisPlnprc"))!!.basis shouldBe null
    }

    @Test
    fun `sucsfbidRate 는 기존 WINNING_RATE 토큰을 재사용한다 — 새 토큰을 짓지 않는다`() {
        val contract = RESOLVED_POLICY.fieldContracts.contractFor(RawKey("sucsfbidRate"))!!

        contract.concept shouldBe FieldConcept.WINNING_RATE
        contract.scale shouldBe FieldScale.PERCENT
        contract.unit shouldBe FieldUnit.PERCENT
        contract.expectedRange shouldBe null
    }

    @Test
    fun `셈 축(참가업체수·복수예가순번)은 COUNT 스케일이고 unit 은 NONE 이다 — P-9 ②`() {
        listOf("prtcptCnum", "compnoRsrvtnPrceSno").forEach { key ->
            val contract = RESOLVED_POLICY.fieldContracts.contractFor(RawKey(key))!!
            contract.scale shouldBe FieldScale.COUNT
            contract.unit shouldBe FieldUnit.NONE
        }
    }

    @Test
    fun `rlOpengDt 는 낙찰 목록과 예비가격 상세 양쪽에 있다 — presentIn 이 오퍼레이션 군을 구별한다(P-9 ④)`() {
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("rlOpengDt"))!!.presentIn shouldBe
            setOf(SourceEndpoint.OPENING_AWARD_LIST, SourceEndpoint.RESERVE_PRICE_DETAIL)
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("opengCorpInfo"))!!.presentIn shouldBe
            setOf(SourceEndpoint.OPENING_RESULT_LIST)
    }

    @Test
    fun `bidwinnrBizno 는 어떤 개찰 축 행에도 등재되지 않는다 — P-10 (a), 저장하지 않는 값은 계약을 두지 않는다`() {
        RESOLVED_POLICY.fieldContracts.contractFor(RawKey("bidwinnrBizno")) shouldBe null
    }

    @Test
    fun `시공능력평가금액목록은 캐럿 구분 DELIMITED_LIST 다 — policy-values md 1-5, v2-defect 018 회귀 가드`() {
        val contract = RESOLVED_POLICY.fieldContracts.contractFor(RawKey("cnstrtnAbltyEvlAmtList"))!!

        contract.scale shouldBe FieldScale.DELIMITED_LIST
        contract.listComponentSeparator shouldBe '^'
        contract.unit shouldBe FieldUnit.NONE
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
        val categories = RESOLVED_POLICY.resultCodeCategories
        categories.size shouldBe 16
        categories.first { it.code == "03" }.category shouldBe ResultCodeCategory.NO_DATA
        categories.first { it.code == "08" }.category shouldBe ResultCodeCategory.INPUT_ERROR
        categories.first { it.code == "22" }.category shouldBe ResultCodeCategory.QUOTA_EXCEEDED
        categories.first { it.code == "30" }.category shouldBe ResultCodeCategory.NOT_RETRYABLE
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

    @Test
    fun `업무구분명 문서 열거 어휘는 물품_용역_공사_외자 넷이다 — policy-values md 1-5, v2-defect 016 회귀 가드`() {
        RESOLVED_POLICY.businessCategoryDocumentedLabels shouldBe
            DocumentedVocabulary(listOf("물품", "용역", "공사", "외자"))
    }

    @Test
    fun `일시 패턴은 문서 authoritative 형식(공백 구분자) 하나다 — policy-values md 1-4, v2-defect 026 회귀 가드`() {
        RESOLVED_POLICY.dateTimePatterns shouldBe listOf(DateTimePatternId.KONEPS_SPACE_DELIMITED_19)
    }
}
