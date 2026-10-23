package bidvector.adapters.bidding

import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.bidding.BidAmountOrigin
import bidvector.bidding.BidCommand
import bidvector.bidding.BidKind
import bidvector.bidding.BidState
import bidvector.bidding.BidStateTag
import bidvector.bidding.BidSubmission
import bidvector.bidding.EvidenceRef
import bidvector.bidding.NoticeKey
import bidvector.bidding.SkipReason
import bidvector.bidding.SubmittedBidAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.EffectiveFrom
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.PolicyVersion
import bidvector.sharedkernel.Resolution
import bidvector.sharedkernel.VatTreatment
import bidvector.workflow.access.AccountId
import bidvector.workflow.bidding.BidActor
import bidvector.workflow.bidding.BidCaseWorkflow
import bidvector.workflow.bidding.BidCommandResult
import bidvector.workflow.bidding.EvidenceRetentionPolicyData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/**
 * **소유 경계를 실 DB 위에서 잰다** — 이 축은 fake 로 증명되지 않는다.
 *
 * `BidCaseWorkflowTest` 는 「포트가 주체를 받는다」까지이고(fake 가 스스로 필터를 건다), 여기는
 * 「**질의가 실제로 계정으로 좁혀지는가**」다. SQL 한 문장에서 `account_id = ?` 를 빼면 fake
 * test 는 전부 초록인 채 이 test 만 붉어진다 — 그래서 둘 다 필요하다(계약 산출물 7).
 *
 * **use case 를 지나 쓴다.** 저장 포트가 받는 `AppliedTransition` 은 `workflow` 의
 * `internal constructor` 라 이 모듈에서 **만들 수 없다** — 그 폐쇄가 바로 「전이 use case 밖에서
 * 건 상태를 바꾸는 경로 0」이고, 이 test 가 그것을 컴파일 층에서 확인하는 자리이기도 하다.
 * 그래서 여기서는 진짜 조립(use case + JDBC 저장소)을 세워 끝까지 돌린다.
 *
 * 권한·트리거 축은 `bidvector_app` 역할로 전환해(`appConnection`) 요청 경로의 권한으로 쳐 본다 —
 * 관리자 연결로 재면 GRANT 가 무엇을 막는지 보이지 않는다.
 */
class BidCaseOwnershipTest : PersistenceTestSupport() {
    private val now = Instant.parse("2026-10-21T04:00:00Z")
    private val store = JdbcBidCaseStore(dataSource())
    private val deadlines = JdbcNoticeDeadlineQuery(dataSource())

    private val workflow =
        BidCaseWorkflow(
            cases = store,
            deadlines = deadlines,
            snapshots = JdbcNoticeSnapshotQuery(dataSource()),
            clock = { now },
            retentionPolicy =
                Resolution.Resolved(
                    EvidenceRetentionPolicyData(Period.ofYears(3)),
                    PolicyVersion(EffectiveFrom.Initial, "test"),
                ),
        )

    private fun account(login: String): AccountId =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, " +
                        "failed_attempts) VALUES (?, 'x', ?, ?, 0) RETURNING id",
                ).use { statement ->
                    statement.setString(1, login)
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.executeQuery().use { rs ->
                        rs.next()
                        AccountId(rs.getLong(1))
                    }
                }
        }

    private fun notice(suffix: String) = NoticeKey("R26BK-$suffix", NoticeRound.of("000"))

    private fun submission(evidence: EvidenceRef? = null) =
        BidSubmission(
            amount =
                SubmittedBidAmount(
                    1_000_000L,
                    Currency.KRW,
                    VatTreatment.INCLUSIVE,
                    BidAmountOrigin.OperatorEntered,
                ),
            submittedAt = now,
            drawNumbers = listOf(3, 11),
            evidence = evidence,
            memo = null,
        )

    // --- 소유 경계 -------------------------------------------------------------

    @Test
    fun `남의 건은 id 를 알아도 읽히지 않는다`() {
        val mine = account("own-a")
        val other = account("own-b")
        val opened = workflow.open(other, notice("own-1"), BidKind.REAL)

        workflow.read(mine, opened.case.id).shouldBeNull()
        workflow.list(mine, BidKind.REAL, null).shouldBeEmpty()
        workflow.timeline(mine, opened.case.id).shouldBeNull()
        store.load(mine, opened.case.id).shouldBeNull()
        store.timeline(mine, opened.case.id).shouldBeEmpty()
    }

    @Test
    fun `남의 건에는 전이가 쓰이지 않는다`() {
        val mine = account("own-c")
        val other = account("own-d")
        val opened = workflow.open(other, notice("own-2"), BidKind.REAL)

        workflow.command(
            mine,
            opened.case.id,
            BidCommand.Skip(SkipReason("남의 건")),
            BidActor.User(mine),
        ) shouldBe BidCommandResult.CaseNotFound

        // 주인이 보기엔 아무 일도 없었다 — 생성 전이 하나뿐이다.
        store.load(other, opened.case.id)!!.state shouldBe BidState.Watching
        store.timeline(other, opened.case.id).size shouldBe 1
    }

    @Test
    fun `목록은 종류로 갈린다 — 페이퍼와 실투찰이 한 목록에 없다`() {
        val mine = account("own-e")
        workflow.open(mine, notice("own-3"), BidKind.REAL)
        workflow.open(mine, notice("own-3"), BidKind.PAPER)

        workflow.list(mine, BidKind.REAL, null).map { it.case.kind } shouldBe listOf(BidKind.REAL)
        workflow.list(mine, BidKind.PAPER, null).map { it.case.kind } shouldBe listOf(BidKind.PAPER)
    }

    @Test
    fun `같은 공고 같은 종류를 두 번 담아도 건은 하나다`() {
        val mine = account("own-f")
        val first = workflow.open(mine, notice("own-4"), BidKind.REAL)
        val second = workflow.open(mine, notice("own-4"), BidKind.REAL)
        second.case.id shouldBe first.case.id
        workflow.list(mine, BidKind.REAL, null).size shouldBe 1
    }

    // --- 전이가 남기는 것 -------------------------------------------------------

    @Test
    fun `전이가 상태와 타임라인을 함께 남긴다`() {
        val mine = account("own-g")
        val opened = workflow.open(mine, notice("own-5"), BidKind.REAL)

        workflow
            .command(mine, opened.case.id, BidCommand.Skip(SkipReason("면허 불일치")), BidActor.User(mine))
            .shouldBeInstanceOf<BidCommandResult.Processed>()

        val reloaded = store.load(mine, opened.case.id)!!
        reloaded.state shouldBe BidState.Skipped(SkipReason("면허 불일치"))
        reloaded.caseVersion shouldBe 1

        val timeline = store.timeline(mine, opened.case.id)
        timeline.size shouldBe 2
        timeline.first().fromState.shouldBeNull()
        timeline.last().fromState shouldBe BidStateTag.WATCHING
        timeline.last().toState shouldBe BidStateTag.SKIPPED
        timeline.last().command shouldBe "SKIP"
        timeline.last().reason shouldBe "면허 불일치"
        timeline.last().retroactive shouldBe false
    }

    @Test
    fun `소급 확인이 기록과 소급 표시를 남긴다`() {
        val mine = account("own-h")
        val opened = workflow.open(mine, notice("own-6"), BidKind.REAL)

        workflow.command(mine, opened.case.id, BidCommand.MarkDeadlinePassed, BidActor.User(mine))
        workflow.command(
            mine,
            opened.case.id,
            BidCommand.RecordBid(submission(EvidenceRef("doc-1")), evidenceRetainUntil = null),
            BidActor.User(mine),
        )

        store.load(mine, opened.case.id)!!.state shouldBe BidState.BidRecorded
        store.timeline(mine, opened.case.id).last().retroactive shouldBe true

        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT retroactive, amount_provenance, chosen_option_ref, draw_numbers, " +
                        "evidence_retain_until FROM bid_record WHERE case_id = ?",
                ).use { statement ->
                    statement.setLong(1, opened.case.id.value)
                    statement.executeQuery().use { rs ->
                        rs.next()
                        rs.getBoolean("retroactive") shouldBe true
                        // 오늘 들어오는 금액은 전부 운영자 입력이다(메뉴 값 위장 금지).
                        rs.getString("amount_provenance") shouldBe "OPERATOR_ENTERED"
                        rs.getString("chosen_option_ref").shouldBeNull()
                        (rs.getArray("draw_numbers").array as Array<*>).toList() shouldBe listOf(3, 11)
                        // R-5 3년 — 제출 시각(KST 2026-10-21) 기준.
                        rs.getDate("evidence_retain_until").toLocalDate() shouldBe LocalDate.of(2029, 10, 21)
                    }
                }
        }
    }

    @Test
    fun `초안 수정은 새 행을 쌓고 앞 스냅샷을 바꾸지 않는다`() {
        val mine = account("own-i")
        val key = notice("own-7")
        seedNotice(key)
        val opened = workflow.open(mine, key, BidKind.REAL)

        workflow
            .draft(mine, opened.case.id, amount(1_000_000L), now, BidActor.User(mine))
            .shouldBeInstanceOf<BidCommandResult.Processed>()
        workflow
            .draft(mine, opened.case.id, amount(2_000_000L), now, BidActor.User(mine))
            .shouldBeInstanceOf<BidCommandResult.Processed>()

        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT draft_seq, amount_won, snapshot_notice_revision, amount_provenance " +
                        "FROM bid_draft WHERE case_id = ? ORDER BY draft_seq",
                ).use { statement ->
                    statement.setLong(1, opened.case.id.value)
                    statement.executeQuery().use { rs ->
                        rs.next()
                        rs.getInt("draft_seq") shouldBe 0
                        rs.getLong("amount_won") shouldBe 1_000_000L
                        // 스냅샷은 **서버가 뜬 공고의 판 번호**다 — 요청이 실어 온 값이 아니다.
                        rs.getLong("snapshot_notice_revision") shouldBe 1L
                        rs.getString("amount_provenance") shouldBe "OPERATOR_ENTERED"
                        rs.next()
                        rs.getInt("draft_seq") shouldBe 1
                        rs.getLong("amount_won") shouldBe 2_000_000L
                        rs.next() shouldBe false
                    }
                }
        }
    }

    @Test
    fun `공고를 뜰 수 없으면 초안을 만들지 않는다 — 빈 스냅샷을 지어내지 않는다`() {
        val mine = account("own-m")
        // 공고를 심지 않는다(6B-3 파기 뒤와 같은 상태).
        val opened = workflow.open(mine, notice("own-11"), BidKind.REAL)

        workflow.draft(mine, opened.case.id, amount(1_000_000L), now, BidActor.User(mine)) shouldBe
            BidCommandResult.NoticeSnapshotUnavailable
        store.load(mine, opened.case.id)!!.state shouldBe BidState.Watching
    }

    private fun amount(won: Long) =
        SubmittedBidAmount(won, Currency.KRW, VatTreatment.INCLUSIVE, BidAmountOrigin.OperatorEntered)

    /** 공고 한 건 — `notice` 는 `raw_observation` 을 FK 로 참조하므로 그 행이 먼저다. */
    private fun seedNotice(key: NoticeKey) {
        val observationKey = "obs-${key.noticeNumber}"
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO raw_observation (observation_key, source_endpoint, payload, " +
                        "payload_fields, observed_at, release_sha, inserted_at) " +
                        "VALUES (?, 'NOTICE_LIST', '{}', '{}'::jsonb, ?, 'test', ?)",
                ).use { statement ->
                    statement.setString(1, observationKey)
                    statement.setTimestamp(2, Timestamp.from(now))
                    statement.setTimestamp(3, Timestamp.from(now))
                    statement.executeUpdate()
                }
            connection
                .prepareStatement(
                    "INSERT INTO notice (notice_number, notice_round, status, observation_key, " +
                        "deadline_at, created_at, updated_at) VALUES (?, ?, 'OPEN', ?, ?, ?, ?)",
                ).use { statement ->
                    statement.setString(1, key.noticeNumber)
                    statement.setString(2, key.noticeRound.value)
                    statement.setString(3, observationKey)
                    statement.setTimestamp(4, Timestamp.from(now.plusSeconds(1800)))
                    statement.setTimestamp(5, Timestamp.from(now))
                    statement.setTimestamp(6, Timestamp.from(now))
                    statement.executeUpdate()
                }
        }
    }

    // --- 권한·트리거(append-only) ----------------------------------------------

    /**
     * 세 표에 **행이 있어야** 트리거 층이 잰다 — `FOR EACH ROW` 트리거는 0 행 UPDATE·DELETE 에서
     * 불리지 않는다. 앞 판은 건만 열고 초안·기록을 심지 않아, 그 두 표에서는 권한 층만 재고 있었다
     * (변이 ⑪g 실측: GRANT 만 열면 0 행 UPDATE 가 「성공」해 붉었다). 이제 행을 심고 그 수를 먼저
     * 단언한다 — 표가 비면 이 test 가 재는 것이 바뀌므로 그 사실이 먼저 붉다.
     */
    @Test
    fun `앱 역할은 타임라인 초안 기록을 고치거나 지울 수 없다`() {
        val mine = account("own-j")
        val key = notice("own-8")
        seedNotice(key)
        val opened = workflow.open(mine, key, BidKind.REAL)
        workflow
            .draft(mine, opened.case.id, amount(1_000_000L), now, BidActor.User(mine))
            .shouldBeInstanceOf<BidCommandResult.Processed>()
        workflow.command(
            mine,
            opened.case.id,
            BidCommand.RecordBid(submission(EvidenceRef("doc-j")), evidenceRetainUntil = null),
            BidActor.User(mine),
        )
        listOf("bid_case_transition", "bid_draft", "bid_record").forEach { table ->
            withClue(table) { rowCount(table, opened.case.id.value) shouldBeGreaterThan 0 }
        }

        val statements =
            listOf(
                "UPDATE bid_case_transition SET reason = 'tampered' WHERE case_id = ?",
                "DELETE FROM bid_case_transition WHERE case_id = ?",
                "UPDATE bid_draft SET amount_won = 1 WHERE case_id = ?",
                "DELETE FROM bid_draft WHERE case_id = ?",
                "UPDATE bid_record SET amount_won = 1 WHERE case_id = ?",
                "DELETE FROM bid_record WHERE case_id = ?",
            )
        assertAllRejectedAsApp(statements, opened.case.id.value)
    }

    @Test
    fun `앱 역할은 건의 소유자와 대상 공고를 바꿀 수 없다`() {
        val mine = account("own-k")
        val opened = workflow.open(mine, notice("own-9"), BidKind.REAL)

        val statements =
            listOf(
                "UPDATE bid_case SET account_id = account_id + 1 WHERE id = ?",
                "UPDATE bid_case SET notice_number = 'moved' WHERE id = ?",
                "UPDATE bid_case SET notice_round = '001' WHERE id = ?",
                "UPDATE bid_case SET bid_kind = 'PAPER' WHERE id = ?",
                "DELETE FROM bid_case WHERE id = ?",
            )
        assertAllRejectedAsApp(statements, opened.case.id.value)
    }

    private fun assertAllRejectedAsApp(
        statements: List<String>,
        caseId: Long,
    ) {
        for (sql in statements) {
            appConnection().use { connection ->
                val attempt =
                    runCatching {
                        connection.prepareStatement(sql).use { statement ->
                            statement.setLong(1, caseId)
                            statement.executeUpdate()
                        }
                    }
                withClue(sql) { attempt.isFailure shouldBe true }
                connection.rollback()
            }
        }
    }

    private fun rowCount(
        table: String,
        caseId: Long,
    ): Int =
        dataSource().connection.use { connection ->
            connection.prepareStatement("SELECT count(*) FROM $table WHERE case_id = ?").use { statement ->
                statement.setLong(1, caseId)
                statement.executeQuery().use { rs ->
                    rs.next()
                    rs.getInt(1)
                }
            }
        }

    private inline fun withClue(
        clue: String,
        block: () -> Unit,
    ) {
        io.kotest.assertions.withClue(clue, block)
    }
}
