package bidvector.adapters.evaluation

import bidvector.procurement.Agency
import bidvector.procurement.AgencyName
import bidvector.procurement.BusinessCategory
import bidvector.procurement.BusinessDivision
import bidvector.procurement.CategoryCode
import bidvector.procurement.CategoryLabel
import bidvector.procurement.MainConstructionType
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeTitle
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
import bidvector.procurement.ServiceDivision
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.assembleFullScopeText
import bidvector.strategy.assembleKeywordScopeText
import bidvector.workflow.evaluation.WatchSubjectOutcome
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [NoticeWatchSubjectPort] — `WatchSubjectPort`의 첫 production 구현(D-6F4W-2~4). `Notice`가
 * 이미 나르는 열(title·businessCategory·agency 둘·baseAmount)만 읽는다 — DB 재조회 없음(D-6F4W-1
 * ⓐ 만 닫는다, 값의 정확성은 `OPEN-6F4-TITLE-INGEST`).
 */
class NoticeWatchSubjectPortTest {
    private val port = NoticeWatchSubjectPort()

    @Test
    fun `모든 필드가 있으면 조립 함수 그대로의 값을 실은 WatchSubject 를 Found 로 낸다`() {
        val notice =
            testNotice(
                title = "정보시스템 유지보수 용역",
                categoryCode = "0411",
                categoryLabel = "기술용역",
                demandAgencyName = "해양수산부",
                noticeAgencyName = "국가정보자원관리원",
                baseAmountWon = 1_000_000_000L,
            )

        val outcome = port.subjectFor(notice)

        val expectedKeyword = assembleKeywordScopeText("정보시스템 유지보수 용역", "기술용역")
        val expectedFull =
            assembleFullScopeText("정보시스템 유지보수 용역", "기술용역", "해양수산부", "국가정보자원관리원")
        outcome shouldBe
            WatchSubjectOutcome.Found(
                bidvector.strategy.WatchSubject(
                    categories = setOf(bidvector.strategy.CategoryCode("0411")),
                    keywordText = expectedKeyword,
                    fullText = expectedFull,
                    baseAmount = Fact.Known(notice.baseAmount!!.amount),
                ),
            )
    }

    @Test
    fun `공고명이 없으면 keywordText 는 공종만 남긴다`() {
        val notice = testNotice(title = null, categoryCode = "0411", categoryLabel = "기술용역")

        val outcome = port.subjectFor(notice) as WatchSubjectOutcome.Found

        outcome.subject.keywordText shouldBe assembleKeywordScopeText(null, "기술용역")
    }

    @Test
    fun `공종 라벨이 없으면 keywordText 는 공고명만 남긴다`() {
        val notice = testNotice(title = "정보시스템 유지보수 용역", categoryCode = "0411", categoryLabel = null)

        val outcome = port.subjectFor(notice) as WatchSubjectOutcome.Found

        outcome.subject.keywordText shouldBe assembleKeywordScopeText("정보시스템 유지보수 용역", null)
    }

    @Test
    fun `공종 자체가 없으면 categories 는 빈 집합이고 keywordText 는 공고명만 남긴다`() {
        val notice = testNotice(title = "정보시스템 유지보수 용역", categoryCode = null, categoryLabel = null)

        val outcome = port.subjectFor(notice) as WatchSubjectOutcome.Found

        outcome.subject.categories shouldBe emptySet()
        outcome.subject.keywordText shouldBe assembleKeywordScopeText("정보시스템 유지보수 용역", null)
    }

    @Test
    fun `기관명이 둘 다 없으면 fullText 는 keywordText 와 같은 조각만 담는다`() {
        val notice =
            testNotice(
                title = "정보시스템 유지보수 용역",
                categoryCode = "0411",
                categoryLabel = "기술용역",
                demandAgencyName = null,
                noticeAgencyName = null,
            )

        val outcome = port.subjectFor(notice) as WatchSubjectOutcome.Found

        outcome.subject.fullText shouldBe assembleFullScopeText("정보시스템 유지보수 용역", "기술용역", null, null)
    }

    @Test
    fun `기초금액이 없으면 Fact Absent EMPTY_INPUT 이다`() {
        val notice = testNotice(baseAmountWon = null)

        val outcome = port.subjectFor(notice) as WatchSubjectOutcome.Found

        outcome.subject.baseAmount shouldBe Fact.Absent(ReasonCode.EMPTY_INPUT)
    }

    @Test
    fun `기초금액이 있으면 Fact Known 으로 그 금액을 그대로 싣는다`() {
        val notice = testNotice(baseAmountWon = 500_000_000L)

        val outcome = port.subjectFor(notice) as WatchSubjectOutcome.Found

        outcome.subject.baseAmount shouldBe Fact.Known(notice.baseAmount!!.amount)
    }

    /** D-6F9-4 — 관심 업종 집합은 네 값(대분류·용역구분·분류 코드·주공종) 중 있는 것만, 커널이 조립한다. */
    @Test
    fun `categories 는 대분류·용역구분·분류 코드·주공종의 있는 값이 각자 원소다`() {
        val notice =
            testNotice(
                categoryCode = "81111500",
                categoryLabel = "정보시스템 개발 서비스",
                businessDivision = BusinessDivision.SERVICE,
                serviceDivision = "기술용역",
                mainConstructionType = "전기공사업",
            )

        val outcome = port.subjectFor(notice) as WatchSubjectOutcome.Found

        outcome.subject.categories shouldBe
            setOf(
                bidvector.strategy.CategoryCode("용역"),
                bidvector.strategy.CategoryCode("기술용역"),
                bidvector.strategy.CategoryCode("81111500"),
                bidvector.strategy.CategoryCode("전기공사업"),
            )
    }

    @Test
    fun `categories 는 값 하나만 있으면 그 원소 하나뿐이다 — 네 칸이 서로의 자리로 새지 않는다`() {
        fun categoriesOf(notice: Notice): Set<bidvector.strategy.CategoryCode> =
            (port.subjectFor(notice) as WatchSubjectOutcome.Found).subject.categories

        fun noCategory(
            businessDivision: BusinessDivision? = null,
            serviceDivision: String? = null,
            mainConstructionType: String? = null,
        ) = testNotice(
            categoryCode = null,
            categoryLabel = null,
            businessDivision = businessDivision,
            serviceDivision = serviceDivision,
            mainConstructionType = mainConstructionType,
        )

        categoriesOf(noCategory(businessDivision = BusinessDivision.CONSTRUCTION)) shouldBe
            setOf(bidvector.strategy.CategoryCode("공사"))
        categoriesOf(noCategory(serviceDivision = "일반용역")) shouldBe
            setOf(bidvector.strategy.CategoryCode("일반용역"))
        categoriesOf(noCategory(mainConstructionType = "토목공사업")) shouldBe
            setOf(bidvector.strategy.CategoryCode("토목공사업"))
        categoriesOf(testNotice(categoryCode = "0411", categoryLabel = null)) shouldBe
            setOf(bidvector.strategy.CategoryCode("0411"))
    }

    @Test
    fun `공사 공고 — 키워드 텍스트의 공종 조각은 주공종명이고 categories 에 코드는 없다`() {
        val notice =
            testNotice(
                title = "청사 전기 설비 공사",
                categoryCode = null,
                categoryLabel = null,
                businessDivision = BusinessDivision.CONSTRUCTION,
                mainConstructionType = "전기공사업",
            )

        val subject = (port.subjectFor(notice) as WatchSubjectOutcome.Found).subject

        subject.keywordText shouldBe assembleKeywordScopeText("청사 전기 설비 공사", "전기공사업")
        subject.fullText shouldBe assembleFullScopeText("청사 전기 설비 공사", "전기공사업", "해양수산부", "국가정보자원관리원")
        subject.categories shouldBe
            setOf(bidvector.strategy.CategoryCode("공사"), bidvector.strategy.CategoryCode("전기공사업"))
    }

    @Test
    fun `용역 공고 — 키워드 텍스트의 공종 조각은 공공조달분류명이고 용역구분은 섞이지 않는다`() {
        val notice =
            testNotice(
                title = "정보시스템 개발",
                categoryCode = "81111500",
                categoryLabel = "정보시스템 개발 서비스",
                businessDivision = BusinessDivision.SERVICE,
                serviceDivision = "기술용역",
            )

        val subject = (port.subjectFor(notice) as WatchSubjectOutcome.Found).subject

        subject.keywordText shouldBe assembleKeywordScopeText("정보시스템 개발", "정보시스템 개발 서비스")
        subject.keywordText.value.contains("기술용역") shouldBe false
    }

    @Test
    fun `이 어댑터는 항상 Found 만 낸다 — Unavailable 은 표현할 수 없다`() {
        val notice = testNotice(title = null, categoryCode = null, categoryLabel = null, baseAmountWon = null)

        val outcome = port.subjectFor(notice)

        (outcome is WatchSubjectOutcome.Found) shouldBe true
    }
}

private val TEST_NOW: Instant = Instant.parse("2026-09-23T00:00:00Z")

private fun testNotice(
    number: String = "20260101001",
    title: String? = "정보시스템 유지보수 용역",
    categoryCode: String? = "0411",
    categoryLabel: String? = "기술용역",
    demandAgencyName: String? = "해양수산부",
    noticeAgencyName: String? = "국가정보자원관리원",
    baseAmountWon: Long? = 1_000_000_000L,
    businessDivision: BusinessDivision? = null,
    serviceDivision: String? = null,
    mainConstructionType: String? = null,
): Notice {
    val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
    val observation =
        RawNoticeObservation.of(
            mapOf(RawKey("bidNtceNo") to number, RawKey("bidNtceOrd") to "000"),
            SourceEndpoint.NOTICE_LIST,
            TEST_NOW,
        )
    return Notice.collected(
        NoticeCollected(
            id = id,
            businessCategory =
                categoryCode?.let { code ->
                    BusinessCategory(CategoryCode.of(code), categoryLabel?.let(::CategoryLabel))
                },
            baseAmount =
                baseAmountWon?.let { won ->
                    ResolvedBaseAmount.Direct.of(
                        won,
                        Currency.KRW,
                        VatTreatment.INCLUSIVE,
                        Provenance.Published(id.round),
                    )
                },
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = null,
            deadlineAt = null,
            openingScheduledAt = null,
            raw = observation,
            demandAgency = demandAgencyName?.let { Agency(code = null, name = AgencyName.of(it)) },
            noticeAgency = noticeAgencyName?.let { Agency(code = null, name = AgencyName.of(it)) },
            title = title?.let(NoticeTitle::of),
            businessDivision = businessDivision,
            serviceDivision = serviceDivision?.let(ServiceDivision::of),
            mainConstructionType = mainConstructionType?.let(MainConstructionType::of),
        ),
    )
}
