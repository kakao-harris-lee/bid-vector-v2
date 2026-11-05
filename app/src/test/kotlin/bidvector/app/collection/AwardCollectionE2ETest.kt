package bidvector.app.collection

import bidvector.procurement.NoticeId
import bidvector.workflow.result.ReconciliationBlocker
import bidvector.workflow.result.ReconciliationVerdict
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test

/**
 * M9/award-ingest E2E — 낙찰 목록 스윕 → 원문 적재 → **고치지 않은** 승격 → canonical 낙찰 칸 →
 * SET-01 대사. 출하 조립을 mock KONEPS 로 기동해 끝에서 끝으로 잰다(실 조달청 호출 0).
 *
 * 잠그는 것: ① 걷는 축이 개찰일시(`inqryDiv=3`)이고 적재 endpoint 가 낙찰 목록이다 ② 계약 미등재
 * 식별자(사업자번호·대표자명)가 원문에 남지 않는다 ③ 승격이 같은 공고의 낙찰자 상호·낙찰금액·
 * 낙찰률을 채운다 ④ 그 값으로 SET-01 대사가 `WINNER_NAME_NOT_PUBLISHED` 를 벗어난다 ⑤ 승인 상한을
 * 다 쓰면 멈추고 미완을 낸다 ⑥ 로그·실행 상태에 서비스 키도 상호도 없다.
 */
class AwardCollectionE2ETest {
    companion object {
        private val e2e = AwardCollectionE2EHarness()

        @AfterAll
        @JvmStatic
        fun stop() {
            e2e.stop()
        }
    }

    @Test
    fun `낙찰 목록을 개찰일시 축으로 걷어 원문을 적재하고 계약 미등재 식별자는 남기지 않는다`() {
        e2e.freshRunStateDirs()
        val nonce = newE2ENonce()

        val (exitCodes, mock) = e2e.bootAward(nonce)

        exitCodes shouldContainExactly listOf(0)
        // 업무 둘 × 개찰일 하나 = 목록 슬롯 둘. 한 쪽에 전부 오므로 호출도 둘이다.
        mock.listCalls.size shouldBe 2
        mock.requestedInquiryDivisions.toSet() shouldBe setOf(AWARD_LIST_INQUIRY_DIVISION)
        awardRawRows(nonce) shouldBe AWARD_NOTICES_PER_SLOT * 2
        // 낙찰 축 밖의 원문은 이 실행이 만들지 않는다 — 상세 축을 부를 길이 없다는 것의 값이다.
        otherRawRows(nonce) shouldBe 0

        val stored = awardPayloadText(nonce)
        // 양성 대조 — 상호는 **적재된다**(그래서 부재 단언이 「아무것도 안 봤다」가 아니다).
        stored shouldContain AWARD_WINNER_NAME
        stored shouldNotContain MOCK_WINNER_BUSINESS_NUMBER
        stored shouldNotContain MOCK_WINNER_REPRESENTATIVE

        e2e.capturedLog() shouldContain "award-collection finished"
        e2e.capturedEverything() shouldNotContain e2e.serviceKeySentinel
        e2e.capturedEverything() shouldNotContain AWARD_WINNER_NAME
        e2e.runStateText() shouldNotContain e2e.serviceKeySentinel
    }

    @Test
    fun `승격이 같은 공고의 낙찰 칸을 채우고 SET-01 대사가 WINNER_NAME_NOT_PUBLISHED 를 벗어난다`() {
        e2e.freshRunStateDirs()
        val nonce = newE2ENonce()
        val id = e2e.constructionNoticeId(nonce)
        e2e.seedOpeningAxisBeforeAward(id)
        e2e.confirmSample(listOf(id))

        // ① 낙찰 목록 전: 개찰 축만 승격됐고 낙찰자 상호가 없다 — 오늘 production 의 답이다.
        e2e.bootPromote() shouldContainExactly listOf(0)
        awardCompanyNameOf(id) shouldBe null
        e2e.verdictOf(id, AWARD_WINNER_NAME) shouldBe
            ReconciliationVerdict.Undecidable(ReconciliationBlocker.WINNER_NAME_NOT_PUBLISHED)

        // ② 낙찰 목록 수집 — 원문만 적재한다.
        e2e.bootAward(nonce).first shouldContainExactly listOf(0)
        awardCompanyNameOf(id) shouldBe null

        // ③ **고치지 않은** 승격 러너가 같은 행의 낙찰 칸을 채운다.
        e2e.bootPromote() shouldContainExactly listOf(0)
        awardCompanyNameOf(id) shouldBe AWARD_WINNER_NAME
        canonicalNumber(id, "final_award_amount_won") shouldBe FIRST_AWARD_AMOUNT
        canonicalNumber(id, "winning_rate_fraction") shouldBe FIRST_WINNING_RATE_FRACTION

        // ④ 대사가 상호를 **실제로** 본다 — 같은 상호면 낙찰, 다른 상호면 패찰(둘 다 판정 불가가 아니다).
        e2e.verdictOf(id, AWARD_WINNER_NAME) shouldBe ReconciliationVerdict.Won
        e2e.verdictOf(id, OTHER_OPERATOR_NAME) shouldBe ReconciliationVerdict.Lost
    }

    @Test
    fun `총 상한을 다 쓰면 멈추고 미완으로 끝낸다`() {
        e2e.freshRunStateDirs()
        val nonce = newE2ENonce()

        // 상한 둘을 1 로 준다 — 총 상한이 일 상한보다 작은 구성은 기동 자체가 거부된다(형제 갈래와 같은
        // 가드). 판정 순서가 총 상한을 먼저 보므로 둘째 호출은 `TOTAL` 로 거부된다.
        val (exitCodes, _) =
            e2e.bootAward(
                nonce,
                mapOf(
                    "bidvector.award-collection.calls-per-day" to "1",
                    "bidvector.award-collection.calls-total" to "1",
                ),
            )

        exitCodes shouldContainExactly listOf(2)
        e2e.capturedLog() shouldContain "award-collection halted budgetLimit=TOTAL"
        awardRawRows(nonce) shouldBeLessThan AWARD_NOTICES_PER_SLOT * 2
    }

    /**
     * 이 실행의 행만 센다 — 세 test 가 한 컨테이너를 쓰므로 endpoint 만으로 세면 앞 test 의 적재가
     * 함께 들어온다(계수 단언이 조용히 커진다).
     */
    private fun awardRawRows(nonce: String): Int =
        e2e.query(
            "SELECT count(*) FROM raw_observation WHERE source_endpoint = 'OPENING_AWARD_LIST' " +
                "AND payload LIKE '%$nonce%'",
        ) { it.getInt(1) }

    private fun otherRawRows(nonce: String): Int =
        e2e.query(
            "SELECT count(*) FROM raw_observation WHERE source_endpoint <> 'OPENING_AWARD_LIST' " +
                "AND payload LIKE '%$nonce%'",
        ) { it.getInt(1) }

    /** 적재된 낙찰 축 원문 전문 — 정본(`payload`)과 계약 투영(`payload_fields`) 둘 다 본다. */
    private fun awardPayloadText(nonce: String): String =
        e2e.query(
            "SELECT coalesce(string_agg(payload || ' ' || payload_fields::text, ' '), '') " +
                "FROM raw_observation WHERE source_endpoint = 'OPENING_AWARD_LIST' AND payload LIKE '%$nonce%'",
        ) { it.getString(1) }

    private fun awardCompanyNameOf(id: NoticeId): String? = canonicalColumn(id, "final_award_company_name")

    private fun canonicalColumn(
        id: NoticeId,
        column: String,
    ): String? = canonicalValue(id, column) { it.getString(1) }

    /** 수치 칸은 **값으로** 비교한다 — 원문 문자열로 비교하면 단언이 DB 의 소수 자릿수 표기에 묶인다. */
    private fun canonicalNumber(
        id: NoticeId,
        column: String,
    ): String? = canonicalValue(id, column) { it.getBigDecimal(1)?.stripTrailingZeros()?.toPlainString() }

    private fun <T> canonicalValue(
        id: NoticeId,
        column: String,
        read: (java.sql.ResultSet) -> T,
    ): T =
        e2e.query(
            "SELECT $column FROM opening_result WHERE notice_number = '${id.number.value}' " +
                "AND notice_round = '${id.round.value}'",
            read,
        )
}

/** 낙찰 목록군의 개찰일시 축 — 이 값이 요청에 실려야 낙찰 목록을 걷은 것이다. */
private const val AWARD_LIST_INQUIRY_DIVISION = "3"

/** mock 의 첫 공사 공고 값 — 금액·낙찰률이 행마다 달라 행이 섞이면 이 단언이 붉어진다. */
private const val FIRST_AWARD_AMOUNT = "1201000000"

/** 낙찰률은 퍼센트로 와서 분수로 선다 — 88.00000% → 0.88. */
private const val FIRST_WINNING_RATE_FRACTION = "0.88"

private const val OTHER_OPERATOR_NAME = "SYN-다른업체-이름"
