package bidvector.app.collection

import bidvector.procurement.NoticeId
import bidvector.workflow.result.ReconciliationBlocker
import bidvector.workflow.result.ReconciliationVerdict
import io.kotest.matchers.collections.shouldContainExactly
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
        // **하한을 못박는다**(cr L-2) — `<` 만 두면 0 에서도 초록이라 「첫 슬롯은 들어왔고 둘째가
        // 거부됐다」를 가리지 못한다. 한 슬롯치 정확히가 상한 판정 순서까지 고정한다.
        awardRawRows(nonce) shouldBe AWARD_NOTICES_PER_SLOT
    }

    /**
     * cr L-1 — 쪽을 나눠 걷는 측정이 **wire 에서** 없었다(scripted port 단위 test 만 있었다).
     * 쪽 크기를 주면 한 슬롯이 두 쪽으로 오고, 두 쪽을 끝까지 걷되 같은 쪽을 다시 부르지 않는다.
     */
    @Test
    fun `쪽을 나눠 걷고 두 쪽을 끝까지 적재한다 — 같은 쪽을 다시 부르지 않는다`() {
        e2e.freshRunStateDirs()
        val nonce = newE2ENonce()

        val (exitCodes, mock) = e2e.bootAward(nonce, pageSize = AWARD_PAGE_SIZE)

        exitCodes shouldContainExactly listOf(0)
        // 업무마다 쪽 둘(3 행을 2+1 로) — 요청 수가 그 걷기의 증거다(응답 에코가 아니다).
        mock.listCalls.size shouldBe 2 * PAGES_PER_SLOT
        mock.requestCount() shouldBe 2 * PAGES_PER_SLOT
        // 두 쪽의 행이 **모두** 적재된다(둘째 쪽을 빠뜨리면 이 계수가 줄고, 같은 쪽을 다시 부르면 늘었다).
        awardRawRows(nonce) shouldBe AWARD_NOTICES_PER_SLOT * 2
        e2e.capturedLog() shouldContain "truncatedSlots=0"
    }

    /**
     * cr H-1 회귀 — **잘린 슬롯이 있으면 종료 코드가 0 이 아니다.** 2쪽을 비재시도 코드로 거절하면
     * 그 슬롯은 1쪽만 받고 접히고(의도된 거동), 실행은 다음 슬롯으로 간다. 그때 0 이 나가면 runbook 의
     * 「0 = 다음 조각」이 운영자를 넘기고 이 갈래에는 슬롯 이어 돌기가 없어 그 날의 원문이 영영 빠진다.
     */
    @Test
    fun `슬롯이 잘리면 멈추지 않고도 미완으로 끝낸다 — 그 조각을 다시 돌려야 한다`() {
        e2e.freshRunStateDirs()
        val nonce = newE2ENonce()

        val (exitCodes, mock) =
            e2e.bootAward(nonce, pageSize = AWARD_PAGE_SIZE, rejectAfterFirstPageWith = NOT_RETRYABLE_RESULT_CODE)

        exitCodes shouldContainExactly listOf(2)
        // 멈춘 것이 아니다 — 두 슬롯 다 돌고 둘 다 잘렸다.
        e2e.capturedLog() shouldContain "truncatedSlots=2"
        e2e.capturedLog() shouldNotContain "award-collection halted"
        // 거절은 재시도를 태우지 않는다 — 업무마다 요청 둘(1쪽 + 거절된 2쪽).
        mock.requestCount() shouldBe 2 * PAGES_PER_SLOT
        // 1쪽의 행만 남는다(둘째 쪽은 받지 못했다).
        awardRawRows(nonce) shouldBe AWARD_PAGE_SIZE * 2
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

/** 쪽 크기 둘 — 업무당 공고 셋이 2 + 1 로 갈린다(쪽 둘). */
private const val AWARD_PAGE_SIZE = 2

private const val PAGES_PER_SLOT = 2

/** 게이트웨이 거절 코드 — 비재시도 축이라 백오프를 태우지 않고 즉시 절단으로 온다. */
private const val NOT_RETRYABLE_RESULT_CODE = "30"

private const val OTHER_OPERATOR_NAME = "SYN-다른업체-이름"
