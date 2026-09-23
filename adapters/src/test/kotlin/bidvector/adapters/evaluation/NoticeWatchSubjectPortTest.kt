package bidvector.adapters.evaluation

import bidvector.procurement.Agency
import bidvector.procurement.AgencyName
import bidvector.procurement.BusinessCategory
import bidvector.procurement.CategoryCode
import bidvector.procurement.CategoryLabel
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeTitle
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.ResolvedBaseAmount
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
        ),
    )
}
