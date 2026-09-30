package bidvector.adapters.snapshot

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.AttemptOutcome
import bidvector.procurement.AxisConclusion
import bidvector.procurement.BusinessDivision
import bidvector.procurement.KONEPS_COLLECTION_POLICY
import bidvector.procurement.KonepsCollectionPolicyData
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.workflow.collection.NoticeKeyHash
import bidvector.workflow.collection.SampleList
import bidvector.workflow.collection.SampleScope
import bidvector.workflow.collection.SampleStratum
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * **cr r4 ⑧ — 추출 계수의 배선.** 문턱 판(`JdbcSnapshotSourceSampleTest`)과 대역을 나눠 쓰고, 재는
 * 것이 다르다: 저쪽은 「무엇이 행이 되는가」이고 이쪽은 「그 수가 어느 칸으로 가는가」다.
 */
class JdbcSnapshotSourceCountsTest : SnapshotSourceTestBase() {
    /**
     * **cr r4 ⑧ — 계수 여덟이 제 칸으로 간다.** `SnapshotExtraction` 의 계수는 전부 `Int` 라 두 칸을
     * 맞바꾼 편집이 컴파일을 지나고, 판독은 **사유가 뒤바뀐** 스냅숏을 받는다(행이 줄지 않으므로 어느
     * 항등식도 걸리지 않는다). 호출부를 명명 인자로 두는 것은 읽는 사람을 위한 것이고, 배선을 **잠그는
     * 것은 이 판**이다: 여덟 값이 서로 다른 수로 **동시에** 서므로 어느 둘을 바꿔도 붉어진다.
     *
     * 사유별 판을 하나씩 두는 것으로는 부족하다 — 그 판들은 다른 계수가 전부 0 이라, 0 과 0 을 바꾼
     * 맞바꾸기가 그대로 지나간다(같은 이유로 D-6G2d-34 가 로그 줄에서 값을 잠갔다).
     */
    @Test
    fun `계수 여덟이 제 칸으로 간다 — 서로 다른 수로 동시에`() {
        val rowNotices = syntheticNumbers(1..2)
        val withoutDetail = syntheticNumbers(11..14)
        val withoutCanonical = syntheticNumbers(21..25)
        val unfinished = syntheticNumbers(31..36)
        val outside = syntheticNumbers(41..47)
        val unusable = syntheticNumbers(51..58)
        val sample = sampleOf(*(rowNotices + withoutDetail + withoutCanonical + unfinished).toTypedArray())

        rowNotices.forEach { number ->
            persistCanonical(number, listObservation(number))
            serviceAxes.forEach { observe(number, it) }
        }
        // 첫 행이 소수 금액 **셋**(예정가격·개찰 기초금액·투찰금액)과 반쪽 A **하나**를 나른다.
        observe(
            rowNotices[0],
            SourceEndpoint.RESERVE_PRICE_DETAIL,
            fields = mapOf("plnprc" to "1250000000.5", "bssamt" to "1239999999.5"),
        )
        observe(rowNotices[0], SourceEndpoint.OPENING_COMPLETE, fields = mapOf("bidprcAmt" to "1100000000.5"))
        observe(
            rowNotices[0],
            SourceEndpoint.BID_PRICE_FORMULA_A,
            fields = mapOf("bidPrceCalclAOpenDt" to "2026-06-10 09:00:00"),
        )
        withoutCanonical.forEach { observe(it, SourceEndpoint.OPENING_COMPLETE) }
        unfinished.forEach { number ->
            persistCanonical(number, listObservation(number))
            observe(number, SourceEndpoint.OPENING_COMPLETE)
        }
        outside.forEach { observe(it, SourceEndpoint.OPENING_COMPLETE) }
        // 차수가 서지 않는 행은 키를 갖지 못한다 — 어느 표본에도 속하지 않아 항등식 밖이다.
        unusable.forEach { observe(it, SourceEndpoint.OPENING_COMPLETE, round = "1") }

        val extraction = extract(sample, conclusionsWithUnfinished(sample, unfinished))

        extraction.rows shouldHaveSize 2
        extraction.fractionalAmounts shouldBe 3
        extraction.sampledWithoutDetail shouldBe 4
        extraction.skippedWithoutNotice shouldBe 5
        extraction.incompleteAxis shouldBe 6
        extraction.observedOutsideSample shouldBe 7
        extraction.unusableRawRows shouldBe 8
        extraction.incompleteAValues shouldBe 1
    }
}
