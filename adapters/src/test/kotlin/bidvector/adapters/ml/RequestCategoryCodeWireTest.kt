package bidvector.adapters.ml

import bidvector.procurement.BusinessCategory
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CanonicalizationOutcome
import bidvector.procurement.FieldConcept
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeCollected
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.procurement.canonicalize
import bidvector.sharedkernel.Resolution
import contract.bidvector.ml.v1.CategoryCodeFact
import contract.bidvector.ml.v1.MissingReason
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

private const val REFERENCE_DAY = "2026-09-24"
private const val SERVICE_CLASS_NUMBER = "81111500"
private const val SERVICE_CLASS_NAME = "정보시스템 개발 서비스"
private const val CONSTRUCTION_TYPE_NAME = "전기공사업"

/**
 * M6/6F-9 D-6F9-6 — 업무구분 수집이 ML 요청 wire 에 만드는 파급을 **이 slice 가 바꾸지 않고 잠근다**. 계약 문면이
 * 요구하는 두 가지다: (가) 용역 공고의 공공조달분류 번호가 요청 fact 에 **실린다** (나) 공사 공고는 결측을 유지한다.
 * `adapters/ml` main 코드는 건드리지 않는다 — 여기 있는 것은 잠금뿐이다.
 *
 * 값을 손으로 짓지 않고 **운영 필드 계약 → `canonicalize` → 공고 fact → wire** 를 한 줄로 잇는다(원시 키 문자열도
 * 계약에서 읽는다). 그래서 canonicalize 가 분류 번호를 코드 칸에 넣기를 멈춰도, 매핑 층이 그 값을 떨어뜨려도
 * 둘 다 이 test 가 RED 다. 기존 `RequestMappingTest` 는 업종 코드 축을 전혀 단언하지 않았다(verifier r1 F-2).
 */
class RequestCategoryCodeWireTest {
    private val policy: KonepsCollectionPolicyData =
        (KONEPS_COLLECTION_POLICY.resolve(LocalDate.parse(REFERENCE_DAY)) as Resolution.Resolved).value

    /** 원시 키는 코드에 박지 않고 그 개념의 운영 계약 행에서 읽는다(공고 목록 오퍼레이션 행). */
    private fun rawKeyOf(concept: FieldConcept): RawKey =
        RawKey(
            policy.fieldContracts
                .contractsFor(concept)
                .first { SourceEndpoint.NOTICE_LIST in it.presentIn }
                .rawName.name,
        )

    private fun collected(
        division: BusinessDivision,
        fields: Map<FieldConcept, String>,
    ): NoticeCollected {
        val identity =
            mapOf(
                rawKeyOf(FieldConcept.NOTICE_NUMBER) to "20260101001",
                rawKeyOf(FieldConcept.NOTICE_ROUND) to "000",
            )
        val observation =
            RawNoticeObservation.of(
                identity + fields.mapKeys { rawKeyOf(it.key) },
                SourceEndpoint.NOTICE_LIST,
                Instant.EPOCH,
                sourceDivision = division,
            )
        return canonicalize(observation, policy)
            .shouldBeInstanceOf<CanonicalizationOutcome.Normalized>()
            .command
    }

    private fun serviceNotice(): NoticeCollected =
        collected(
            BusinessDivision.SERVICE,
            mapOf(
                FieldConcept.PUBLIC_PROCUREMENT_CLASS_CODE to SERVICE_CLASS_NUMBER,
                FieldConcept.PUBLIC_PROCUREMENT_CLASS_NAME to SERVICE_CLASS_NAME,
            ),
        )

    private fun constructionNotice(): NoticeCollected =
        collected(BusinessDivision.CONSTRUCTION, mapOf(FieldConcept.MAIN_CONSTRUCTION_TYPE to CONSTRUCTION_TYPE_NAME))

    private fun wireCategoryFact(category: BusinessCategory?): CategoryCodeFact =
        mapRequest(
            request = testBidPredictionRequest().copy(businessCategory = category),
            requestId = "req-6f9",
            policy = testMlCallPolicy(),
            deadlinePolicyVersion = "deadline-test",
        ).features.categoryCode

    @Test
    fun `(가) 용역 공고의 공공조달분류 번호가 ML 요청 category_code fact 에 그대로 실린다`() {
        val notice = serviceNotice()
        val category = requireNotNull(notice.businessCategory) { "용역 공고의 업무구분 코드가 비어 있다" }

        val fact = wireCategoryFact(category)

        fact.factCase shouldBe CategoryCodeFact.FactCase.VALUE
        fact.value shouldBe SERVICE_CLASS_NUMBER
        fact.value shouldBe category.code.value
    }

    @Test
    fun `(나) 공사 공고는 업무구분 코드가 없어 category_code fact 가 결측으로 남는다 — 주공종이 코드로 실리지 않는다`() {
        val notice = constructionNotice()
        notice.businessCategory shouldBe null
        notice.mainConstructionType shouldNotBe null

        val fact = wireCategoryFact(notice.businessCategory)

        fact.factCase shouldBe CategoryCodeFact.FactCase.MISSING
        fact.missing shouldBe MissingReason.MISSING_REASON_UNKNOWN
        fact.value shouldBe ""
    }
}
