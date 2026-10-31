package bidvector.adapters.result

import bidvector.adapters.bidding.BidCaseDbFixture
import bidvector.adapters.notice.ReadOnlyWitness
import bidvector.adapters.notice.witnessedDataSource
import bidvector.adapters.snapshot.jsonbKeyOf
import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidCaseId
import bidvector.bidding.BidCommand
import bidvector.bidding.BidKind
import bidvector.bidding.BidMemo
import bidvector.bidding.BidStateTag
import bidvector.bidding.NoticeKey
import bidvector.procurement.NOTICE_NUMBER_RAW_KEY
import bidvector.procurement.NOTICE_ROUND_RAW_KEY
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.settlement.AwardWinnerTradeName
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.BidActor
import bidvector.workflow.opening.NoticeOpeningFacts
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import javax.sql.DataSource

/** 표지 상호 — 개찰 사실 경로 어디에도 이 글자가 나오면 안 된다(D-7C5B-5, 값 수준 단언). */
private const val SENTINEL_WINNER = "SENTINEL-WINNER-DO-NOT-EXPORT"

/** 표지 메모·증빙·건너뜀 사유 — 투찰 기록 경로에 나오면 안 된다. */
private const val SENTINEL_MEMO = "SENTINEL-MEMO-DO-NOT-EXPORT"

private const val HOUR = 3600L

/**
 * 결과 조회 어댑터 셋이 **무엇을 읽고, 무엇을 읽지 않고, 쓰지 않는가**(실제 PostgreSQL).
 *
 * 판정은 DB 없이 `ReadMyBidResultsTest` 가 전수로 잰다 — 여기서 재는 것은 질의의 **열 집합**과
 * **경계**다.
 *
 * 실측되는 우회: ① 낙찰자 상호가 개찰 사실 경로에 오르지 않는다 · ④ 다른 계정의 건이 섞이지
 * 않는다 · ⑦ 메모·증빙·건너뜀 사유를 읽지 않는다 · ⑧ 페이퍼가 섞이지 않는다 · 쓰기 0.
 */
class JdbcMyBidResultViewTest : BidCaseDbFixture() {
    private fun recordedBidView(source: DataSource = dataSource()) = JdbcRecordedBidView(source)

    private fun openingFactsView(source: DataSource = dataSource()) = JdbcBatchOpeningFactsView(source)

    private fun winnerNameView(source: DataSource = dataSource()) = JdbcAwardWinnerNameView(source)

    private fun noticeId(key: NoticeKey) = NoticeId(NoticeNumber.of(key.noticeNumber), key.noticeRound)

    /** 건 하나를 열고 투찰을 기록한다 — 출하 전이 경로를 그대로 쓴다(손으로 INSERT 하지 않는다). */
    private fun recordBid(
        account: AccountId,
        key: NoticeKey,
        kind: BidKind = BidKind.REAL,
        won: Long = 1_000_000L,
        memo: BidMemo? = null,
        draft: Boolean = true,
    ): BidCaseId {
        val workflow = workflow()
        val caseId =
            workflow
                .open(account, key, kind)
                .view.case.id
        // 초안은 공고 스냅샷을 요구한다 — 공고 행을 못 찾는 건은 초안 없이 바로 기록한다
        // (전이표가 「관심」에서 RECORD_BID 를 연다).
        if (draft) workflow.draft(account, caseId, amount(won), now, BidActor.User(account))
        workflow.command(
            account,
            caseId,
            BidCommand.RecordBid(submission().copy(amount = amount(won), memo = memo), null),
            BidActor.User(account),
        )
        return caseId
    }

    /** 개찰 결과 축 **원문** 한 행 — 계약 등재 키로 심는다(손 리터럴이 아니다). */
    private fun seedOpeningAxisRaw(
        rawNumber: String,
        rawRound: String,
        masked: Boolean = false,
    ) {
        val fields = """{"${jsonbKeyOf(
            NOTICE_NUMBER_RAW_KEY,
        )}":"$rawNumber","${jsonbKeyOf(NOTICE_ROUND_RAW_KEY)}":"$rawRound"}"""
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO raw_observation (observation_key, source_endpoint, payload, payload_fields, " +
                        "observed_at, release_sha, inserted_at) VALUES (?, 'OPENING_RESULT_LIST', '{}', " +
                        "?::jsonb, ?, 'test', ?)",
                ).use { statement ->
                    statement.setString(1, "obs-rawaxis-$rawNumber-$rawRound")
                    statement.setString(2, fields)
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.setTimestamp(4, Timestamp.from(now))
                    statement.executeUpdate()
                }
            // **적재는 표지를 찍지 못한다**(DB 트리거) — 마스킹은 보존 작업의 UPDATE 로만 선다.
            if (masked) {
                connection
                    .prepareStatement("UPDATE raw_observation SET pii_masked_at = now() WHERE observation_key = ?")
                    .use { statement ->
                        statement.setString(1, "obs-rawaxis-$rawNumber-$rawRound")
                        statement.executeUpdate()
                    }
            }
        }
    }

    private fun seedOpeningResult(
        key: NoticeKey,
        winner: String?,
        progress: String = "개찰완료",
    ) {
        val observationKey = "obs-open-${key.noticeNumber}"
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO raw_observation (observation_key, source_endpoint, payload, payload_fields, " +
                        "observed_at, release_sha, inserted_at) VALUES (?, 'OPENING_RESULT_LIST', '{}', " +
                        "'{}'::jsonb, ?, 'test', ?)",
                ).use { statement ->
                    statement.setString(1, observationKey)
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.executeUpdate()
                }
            connection
                .prepareStatement(
                    "INSERT INTO opening_result (notice_number, notice_round, observed_at, observation_key, " +
                        "progress_division, final_award_company_name) VALUES (?, ?, ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, key.noticeNumber)
                    statement.setString(2, key.noticeRound.value)
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.setString(4, observationKey)
                    statement.setString(5, progress)
                    statement.setString(6, winner)
                    statement.executeUpdate()
                }
        }
    }

    // ------------------------------------------------------------- 열 집합

    /**
     * 질의가 **실제로 읽은** 열을 `ResultSetMetaData` 로 거둬 선언 목록과 등식으로 잠근다 —
     * 열을 더하면 여기가 먼저 깨진다(응답 DTO 에 칸이 없는 것만으로는 막히지 않는다).
     */
    @Test
    fun `투찰 기록 질의가 읽는 열이 선언 목록과 같다`() {
        val mine = account("cols-a")
        val key = notice("COLS-A")
        seedNotice(key, now.plusSeconds(HOUR))
        recordBid(mine, key)

        columnsOf(MyBidResultSql.SELECT_RECORDED_BIDS) { statement ->
            statement.setLong(1, mine.value)
            statement.setString(2, MyBidResultSql.REAL_BID_KIND)
        } shouldContainExactly MyBidResultSql.RECORDED_BID_COLUMNS
    }

    /**
     * **나머지 세 질의도 같은 등식으로 잠근다**(r1 CR M-3) — 앞 판은 투찰 기록 하나에만 걸려
     * 있었고, canonical 질의의 방어는 문자열 두 개의 부재 검사뿐이라 **다른 이름의** 업체 식별
     * 열이 지역 접두로 들어오면 잡지 못했다.
     */
    @Test
    fun `공고 · 상호 · canonical 질의가 읽는 열이 선언 목록과 같다`() {
        fun numbers(statement: java.sql.PreparedStatement) =
            statement.setArray(1, statement.connection.createArrayOf("text", arrayOf("ANY-NOTICE")))

        columnsOf(MyBidResultSql.SELECT_NOTICES, ::numbers) shouldContainExactly MyBidResultSql.NOTICE_COLUMNS
        columnsOf(MyBidResultSql.SELECT_WINNER_NAMES, ::numbers) shouldContainExactly
            MyBidResultSql.WINNER_NAME_COLUMNS
        columnsOf(MyBidResultSql.SELECT_CANONICAL, ::numbers) shouldContainExactly
            MyBidResultSql.CANONICAL_COLUMNS
    }

    /**
     * 메모·증빙·추첨번호·건너뜀 사유는 **읽는 자리에 없다**(D-7C5B-5). 선언 목록과 문장을 함께
     * 보고, 메모를 **실제로 심어** 그 글자가 조회 산출에 없음을 값으로 잰다 — 목록만 보면
     * 「목록 밖의 다른 경로로 읽는」 변경을 놓친다.
     */
    @Test
    fun `투찰 기록 질의가 메모와 증빙과 건너뜀 사유를 읽지 않는다`() {
        val forbidden = listOf("memo", "evidence_ref", "evidence_retain_until", "draw_numbers", "skip_reason")
        withClue("개인정보·자유 텍스트 열이 선언 목록에 들어왔다") {
            MyBidResultSql.RECORDED_BID_COLUMNS.none { it in forbidden } shouldBe true
        }
        forbidden.forEach { column -> MyBidResultSql.SELECT_RECORDED_BIDS shouldNotContain column }

        val mine = account("memo-a")
        val key = notice("MEMO-A")
        seedNotice(key, now.plusSeconds(HOUR))
        recordBid(mine, key, memo = BidMemo(SENTINEL_MEMO))

        val found = recordedBidView().recordedBids(mine)
        found.shouldNotBeEmpty()
        found.toString() shouldNotContain SENTINEL_MEMO
    }

    /** 개찰 사실 질의의 열 목록은 7C⑤-a 의 것 그대로다 — **상호 두 열이 없다**. */
    @Test
    fun `개찰 사실 질의가 상호 열을 읽지 않는다`() {
        MyBidResultSql.SELECT_CANONICAL shouldNotContain "final_award_company_name"
        MyBidResultSql.SELECT_CANONICAL shouldNotContain "opening_rank_one_bidder_name"
        MyBidResultSql.CANONICAL_COLUMNS.none { it.contains("company_name") || it.contains("bidder_name") } shouldBe
            true
    }

    // --------------------------------------------------------------- 경계

    @Test
    fun `다른 계정의 투찰 기록은 보이지 않는다`() {
        val mine = account("own-a")
        val theirs = account("own-b")
        val mineKey = notice("OWN-A")
        val theirsKey = notice("OWN-B")
        seedNotice(mineKey, now.plusSeconds(HOUR))
        seedNotice(theirsKey, now.plusSeconds(HOUR))
        val mineCase = recordBid(mine, mineKey)
        recordBid(theirs, theirsKey)

        recordedBidView().recordedBids(mine).map { it.caseId } shouldContainExactly listOf(mineCase)
    }

    @Test
    fun `페이퍼 건의 투찰 기록은 목록에 없다`() {
        val mine = account("paper-a")
        val realKey = notice("PAPER-REAL")
        val paperKey = notice("PAPER-PAPER")
        seedNotice(realKey, now.plusSeconds(HOUR))
        seedNotice(paperKey, now.plusSeconds(HOUR))
        val realCase = recordBid(mine, realKey, kind = BidKind.REAL)
        recordBid(mine, paperKey, kind = BidKind.PAPER)

        recordedBidView().recordedBids(mine).map { it.caseId } shouldContainExactly listOf(realCase)
    }

    /** 기록은 append-only 라 소급 정정이 행을 더한다 — 조회가 내는 것은 **마지막 하나**다. */
    @Test
    fun `같은 건에 기록이 둘이면 마지막 하나만 낸다`() {
        val mine = account("last-a")
        val key = notice("LAST-A")
        seedNotice(key, now.plusSeconds(HOUR))
        val caseId = recordBid(mine, key, won = 1_000_000L)
        now = now.plusSeconds(HOUR)
        workflow().command(
            mine,
            caseId,
            BidCommand.RecordBid(submission().copy(amount = amount(2_222_222L), memo = null), null),
            BidActor.User(mine),
        )

        val found = recordedBidView().recordedBids(mine)
        found.size shouldBe 1
        found.single().amount.won shouldBe 2_222_222L
    }

    @Test
    fun `투찰 금액이 통화와 과세와 출처를 함께 낸다`() {
        val mine = account("money-a")
        val key = notice("MONEY-A")
        seedNotice(key, now.plusSeconds(HOUR))
        recordBid(mine, key, won = 1_234_567L)

        val amount = recordedBidView().recordedBids(mine).single().amount
        amount.won shouldBe 1_234_567L
        amount.currency shouldBe Currency.KRW
        amount.vatTreatment shouldBe VatTreatment.INCLUSIVE
        amount.origin.shouldBeInstanceOf<BidAmountOrigin.OperatorEntered>()
    }

    @Test
    fun `상태 꼬리표를 그대로 낸다`() {
        val mine = account("state-a")
        val key = notice("STATE-A")
        seedNotice(key, now.plusSeconds(HOUR))
        recordBid(mine, key)

        recordedBidView().recordedBids(mine).single().recordedState shouldBe BidStateTag.BID_RECORDED
    }

    /**
     * **원문만 있고 승격이 없으면 「승격 전」이다**(r1 CR M-2) — 이 접기에 양성 test 가 없었다.
     * 다른 test 는 canonical 이 있거나 공고 행이 없어 그 질의가 0 행으로만 돌았다.
     *
     * 원문의 공고번호는 **온 그대로**라 표기가 제각각이다(소문자·공백). SQL 등식으로 맞대면
     * 조용히 빗나가 승격을 기다리는 공고가 「수집 전」으로 나가고 화면이 **틀린 사유**를 보인다
     * — 7C⑤-a 가 그 결함을 한 번 겪었다. 그래서 표기를 일부러 다르게 심는다.
     */
    @Test
    fun `canonical 이 없고 원문만 있으면 개찰 축 관측으로 잡는다 — 표기가 달라도`() {
        val key = notice("RAWAXIS-A")
        seedNotice(key, now.plusSeconds(HOUR))
        seedOpeningAxisRaw(key.noticeNumber.lowercase().replace("-", "  "), key.noticeRound.value)

        val facts = openingFactsView().lookupAll(setOf(noticeId(key)))
        val present = facts[noticeId(key)].shouldBeInstanceOf<NoticeOpeningFacts.Present>()
        present.canonical.shouldBeNull()
        withClue("원문이 있으면 「승격 전」이고 「수집 전」이 아니다") {
            present.openingAxisObserved shouldBe true
        }
    }

    /**
     * **마스킹된 원문은 세지 않는다**(r2 v L-2) — 업체 식별 개념이 지워진 뒤라 개찰 결과로
     * 승격될 수 없고, 세어 주면 그 공고가 영영 「승격 전」으로 남아 일시적이라던 상태가 영구
     * 오답이 된다. 술어는 7C⑤-a 와 같은 상수이지만 **묶음 질의에서만 빼는** 변이가 여기 없으면
     * 지나간다.
     */
    @Test
    fun `마스킹된 원문은 개찰 축 관측으로 세지 않는다`() {
        val key = notice("RAWAXIS-C")
        seedNotice(key, now.plusSeconds(HOUR))
        seedOpeningAxisRaw(key.noticeNumber, key.noticeRound.value, masked = true)

        val facts = openingFactsView().lookupAll(setOf(noticeId(key)))
        val present = facts[noticeId(key)].shouldBeInstanceOf<NoticeOpeningFacts.Present>()
        withClue("마스킹된 행을 세면 그 공고가 영영 「승격 전」으로 남는다") {
            present.openingAxisObserved shouldBe false
        }
    }

    /** 음성 대조 — 차수가 어긋난 원문은 같은 공고가 아니다(짝으로 맞춘다). */
    @Test
    fun `차수가 어긋난 원문은 개찰 축 관측으로 세지 않는다`() {
        val key = notice("RAWAXIS-B")
        seedNotice(key, now.plusSeconds(HOUR))
        seedOpeningAxisRaw(key.noticeNumber, "999")

        val facts = openingFactsView().lookupAll(setOf(noticeId(key)))
        val present = facts[noticeId(key)].shouldBeInstanceOf<NoticeOpeningFacts.Present>()
        present.openingAxisObserved shouldBe false
    }

    // ------------------------------------------------ 개찰 사실 · 낙찰자 상호

    @Test
    fun `개찰 사실 조회가 공고 부재와 canonical 을 가른다`() {
        val present = notice("FACTS-A")
        val absent = notice("FACTS-Z")
        seedNotice(present, now.plusSeconds(HOUR))
        seedOpeningResult(present, winner = SENTINEL_WINNER, progress = "유찰")

        val facts = openingFactsView().lookupAll(setOf(noticeId(present), noticeId(absent)))
        facts[noticeId(absent)] shouldBe NoticeOpeningFacts.NoticeAbsent
        val found = facts[noticeId(present)].shouldBeInstanceOf<NoticeOpeningFacts.Present>()
        requireNotNull(found.canonical).progressLabel shouldBe "유찰"
    }

    /** **표지 글자가 개찰 사실 어디에도 없다** — 상호 열을 읽지 않는다는 것을 값으로 잰다. */
    @Test
    fun `개찰 사실에 낙찰자 상호가 실리지 않는다`() {
        val key = notice("SENTINEL-A")
        seedNotice(key, now.plusSeconds(HOUR))
        seedOpeningResult(key, winner = SENTINEL_WINNER)

        val facts = openingFactsView().lookupAll(setOf(noticeId(key)))
        facts.toString() shouldNotContain SENTINEL_WINNER
    }

    /** 상호를 읽는 **유일한 자리** — 그 값은 `AwardWinnerTradeName` 으로만 나온다. */
    @Test
    fun `낙찰자 상호 조회가 공개와 미공개를 가른다`() {
        val published = notice("WIN-A")
        val blank = notice("WIN-B")
        seedNotice(published, now.plusSeconds(HOUR))
        seedNotice(blank, now.plusSeconds(HOUR))
        seedOpeningResult(published, winner = "주식회사 가상해양기술")
        seedOpeningResult(blank, winner = null)

        val names = winnerNameView().winnerNames(setOf(noticeId(published), noticeId(blank)))
        names[noticeId(published)] shouldBe AwardWinnerTradeName.Published("주식회사 가상해양기술")
        names[noticeId(blank)] shouldBe AwardWinnerTradeName.NotPublished
    }

    /** `AwardWinnerTradeName` 은 `toString()` 이 가려져 있다 — 로그·예외 문면으로도 새지 않는다. */
    @Test
    fun `낙찰자 상호 값은 자기 문면에 원문을 싣지 않는다`() {
        val key = notice("WIN-C")
        seedNotice(key, now.plusSeconds(HOUR))
        seedOpeningResult(key, winner = SENTINEL_WINNER)

        winnerNameView().winnerNames(setOf(noticeId(key))).toString() shouldNotContain SENTINEL_WINNER
    }

    @Test
    fun `공고 집합이 비면 질의를 돌리지 않는다`() {
        openingFactsView().lookupAll(emptySet()) shouldBe emptyMap()
        winnerNameView().winnerNames(emptySet()) shouldBe emptyMap()
    }

    /**
     * **투찰 기록이 있는 건의 공고번호는 `notice` 행과 정확히 같다** — 전이표가 그렇게 만든다:
     * `RECORD_BID` 는 「투찰 예정 · 투찰 기록됨 · 마감 지남」에서만 열리고, 그 셋에 닿으려면
     * 초안(공고 스냅샷 질의)이나 마감 사본(공고 마감 질의)이 공고 행을 **정확히** 찾아야 한다.
     *
     * 그래서 조회가 canonical 표를 정확 일치로 묻는 것이 성립한다. 이 전제가 깨지면 여기가
     * 먼저 붉는다 — 조회가 조용히 「공고 부재」를 내기 전에.
     */
    @Test
    fun `투찰 기록이 있는 건은 공고 행과 번호가 정확히 같다`() {
        val mine = account("exact-a")
        val key = notice("EXACT-A")
        seedNotice(key, now.plusSeconds(HOUR))
        recordBid(mine, key)

        val stored = recordedBidView().recordedBids(mine).single()
        val id = NoticeId(NoticeNumber.of(stored.notice.noticeNumber), stored.notice.noticeRound)
        withClue("정규화가 저장된 번호를 바꾸면 canonical 정확 일치가 빗나간다") {
            id.number.value shouldBe stored.notice.noticeNumber
        }
        openingFactsView().lookupAll(setOf(id))[id].shouldBeInstanceOf<NoticeOpeningFacts.Present>()
    }

    // ---------------------------------------------------------------- 쓰기 0

    /**
     * 셋 다 **읽기 전용 트랜잭션** 안에서만 문장을 돌린다 — 면제는 트랜잭션을 여는 첫 문장 하나다
     * (`ReadOnlyWitness` 의 등식 면제).
     */
    @Test
    fun `조회 셋이 읽기 전용 트랜잭션 밖에서 문장을 돌리지 않는다`() {
        val mine = account("ro-a")
        val key = notice("RO-A")
        seedNotice(key, now.plusSeconds(HOUR))
        recordBid(mine, key)
        seedOpeningResult(key, winner = SENTINEL_WINNER)

        // 증인의 면제는 **첫 문장 하나**라 조회마다 따로 세운다(한 증인에 셋을 묶으면 둘째·셋째
        // 조회가 연 트랜잭션이 위반으로 잡혀, 재려던 것이 아니라 harness 를 재게 된다).
        witnessing { recordedBidView(it).recordedBids(mine) }
        witnessing { openingFactsView(it).lookupAll(setOf(noticeId(key))) }
        witnessing { winnerNameView(it).winnerNames(setOf(noticeId(key))) }
    }

    /** 조회 전후로 네 표의 행 수가 **변하지 않는다**. 음성 대조로 이 계수가 실제로 움직임을 보인다. */
    @Test
    fun `조회가 행을 더하거나 지우지 않는다`() {
        val mine = account("nowrite-a")
        val key = notice("NOWRITE-A")
        seedNotice(key, now.plusSeconds(HOUR))
        recordBid(mine, key)
        seedOpeningResult(key, winner = SENTINEL_WINNER)

        val before = rowCounts()
        recordedBidView().recordedBids(mine)
        openingFactsView().lookupAll(setOf(noticeId(key)))
        winnerNameView().winnerNames(setOf(noticeId(key)))
        rowCounts() shouldBe before

        // 음성 대조 — 계수가 쓰기에는 실제로 반응한다(전부 0 을 세고 있던 것이 아니다).
        now = now.plusSeconds(HOUR)
        workflow().command(
            mine,
            recordedBidView().recordedBids(mine).single().caseId,
            BidCommand.RecordBid(submission().copy(amount = amount(3_333_333L), memo = null), null),
            BidActor.User(mine),
        )
        withClue("쓰기 뒤에도 계수가 같다면 이 test 는 아무것도 재지 않는다") {
            (rowCounts() == before) shouldBe false
        }
    }

    /** 조회 하나를 증인 아래에서 돌리고 「읽기 전용 밖 문장 0 · 질의 ≥1」을 잰다. */
    private fun witnessing(read: (DataSource) -> Unit) {
        val witness = ReadOnlyWitness()
        read(witnessedDataSource(dataSource(), witness))
        witness.queries().shouldNotBeEmpty()
        witness.statementsOutsideReadOnly() shouldContainExactly emptyList()
    }

    private fun rowCounts(): Map<String, Long> =
        listOf("bid_case", "bid_case_transition", "bid_record", "opening_result").associateWith { table ->
            dataSource().connection.use { connection ->
                connection.prepareStatement("SELECT count(*) FROM $table").use { statement ->
                    statement.executeQuery().use { rows ->
                        rows.next()
                        rows.getLong(1)
                    }
                }
            }
        }

    private fun columnsOf(
        sql: String,
        bind: (java.sql.PreparedStatement) -> Unit,
    ): List<String> =
        dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                bind(statement)
                statement.executeQuery().use { rows ->
                    val metadata = rows.metaData
                    (1..metadata.columnCount).map { metadata.getColumnLabel(it) }
                }
            }
        }
}
