package bidvector.adapters.evaluation

import bidvector.adapters.notificationinbox.JdbcNotificationAudience
import bidvector.adapters.notificationinbox.JdbcNotificationInbox
import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.adapters.qualification.PinnedLicenseGate
import bidvector.decision.MlUnavailableReason
import bidvector.decision.PublishedFloorExclusion
import bidvector.decision.UnitScore
import bidvector.decision.Verdict
import bidvector.procurement.Notice
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.qualification.LicenseVerdict
import bidvector.sharedkernel.NoticeRound
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.CandidateEvaluation
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.PredictionEvidence
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.Clock
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private val NOW: Instant = Instant.parse("2026-11-06T00:00:00Z")
private const val NOTICE_NUMBER = "M9OPS0001"

/** 0.9 를 내는 ML — 아래 전략의 `bidNowThreshold`(0.5)를 넘겨 판정을 `BidNow` 로 보낸다. */
private class AlwaysBidNowMl : MlAnalysisPort {
    override suspend fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome =
        MlAnalysisOutcome.Analyzed(
            priorityScore = UnitScore(BigDecimal("0.9")),
            probabilityScore = null,
            matchedScore = null,
            evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided),
            floorExclusion = PublishedFloorExclusion.IMPLAUSIBLE,
        )
}

/**
 * **NOTI-01 — 평가 커밋 조립이 실 DB 에 알림함 1건을 남긴다**(M9/ops-code).
 *
 * `InboxRecordingNotificationsTest`(workflow)가 재는 것은 **순서와 계수**이고, 이 자리가 재는
 * 것은 **production 조립이 그 감싸기를 실제로 쓰는가**다. 둘을 가른 이유: 앞 판의 결함 계열이
 * 「커널은 맞는데 조립이 그것을 안 쓴다」였다(D-6F10 R1-H-1 — 알림 port 를 Recording 으로
 * 바꿔치워도 240 클래스가 초록이었다). 그래서 조립을 **직접 세워** 실 DB 로 돌린다.
 *
 * 바꿔치우는 것은 ML port 하나다(판정이 `BidNow` 에 닿게 하는 fake). 나머지는 production
 * 어댑터·커널 그대로다 — 후보 원천은 **실 공고 행을 읽는** `JdbcCandidateSource` 이고 알림함
 * 쓰기·청중 읽기도 실 어댑터다.
 */
class EvaluationCommitInboxProductionTest : PersistenceTestSupport() {
    private val clock = Clock { NOW }

    // 표 비우기는 `PersistenceTestSupport` 의 `@BeforeEach` 가 이미 진다(`notification_inbox`·
    // `account`·`outbox` 전부 그 목록에 있다) — 둘째 비우기를 두지 않는다.

    @Test
    fun `가치 게이트를 통과한 후보가 운영자 알림함에 NEW_CANDIDATE 한 건을 남긴다`() {
        val account = insertAccount("ops-code-operator")
        val notice = insertNotice(NOTICE_NUMBER)

        val results = runBlocking { commitRun().evaluate() }

        results.single().shouldBeBidNow()
        val rows = inboxRows()
        rows shouldHaveSize 1
        rows.single() shouldBe
            InboxRow(account.value, "NEW_CANDIDATE", notice.number.value, detail = null, bidKind = "NONE")
        // 채널 축도 함께 남는다 — 두 축이 **같은 판정 하나**에서 나왔다는 사실이다.
        outboxCount() shouldBe 1
    }

    /**
     * 계정이 없으면(부트스트랩 전) 기록 0 건이고 **실패가 아니다** — 평가는 정상으로 끝나고
     * 채널 축은 그대로 남는다. 팀장 승인 판단: 청중은 `account` 전수이고 빈 전수가 정상이다.
     */
    @Test
    fun `청중이 비면 알림함 기록이 0 건이고 평가는 정상으로 끝난다`() {
        insertNotice(NOTICE_NUMBER)

        val results = runBlocking { commitRun().evaluate() }

        results.single().shouldBeBidNow()
        inboxRows().shouldBeEmpty()
        outboxCount() shouldBe 1
    }

    /**
     * **멱등**(NOTI-10 의 PK) — 같은 공고로 두 번 돌려도 알림함 행이 늘지 않는다. 채널 축은
     * 늘어난다(이 경로는 이중 요청을 막지 않는다, D-6F7-6) — 두 축의 **다른 성질**이다.
     */
    @Test
    fun `같은 공고로 두 번 돌려도 알림함 행은 하나다`() {
        insertAccount("ops-code-operator")
        insertNotice(NOTICE_NUMBER)

        runBlocking { commitRun().evaluate() }
        runBlocking { commitRun().evaluate() }

        inboxRows() shouldHaveSize 1
        outboxCount() shouldBe 2
    }

    /** 판정이 `BidNow` 가 아니면 **왜 아닌지**를 실패 메시지에 싣는다(단계·사유). */
    private fun CandidateEvaluation.shouldBeBidNow() {
        val reached =
            this as? CandidateEvaluation.Reached
                ?: error("판정이 사다리에 닿지 못했다: ${(this as CandidateEvaluation.NotReached).let { it.stage to it.reason }}")
        (reached.verdict is Verdict.BidNow) shouldBe true
    }

    private fun commitRun(): EvaluationCommitRun =
        EvaluationCommitRun(
            dataSource = dataSource(),
            strategies = PinnedStrategyRepository(bidNowStrategy()),
            candidateSource = JdbcCandidateSource(dataSource(), clock, CANDIDATE_CAP),
            watchSubjects = NoticeWatchSubjectPort(),
            licenseGate = PinnedLicenseGate(LicenseVerdict.Eligible(emptySet())),
            mlAnalysis = AlwaysBidNowMl(),
            capacity = RequestCapacityPort(0, MAX_ACTIVE_BIDS),
            correlationIds = CorrelationIdFactory { CorrelationId("m9-ops-code-1") },
            clock = clock,
            analysisBudget = null,
            notificationInbox = JdbcNotificationInbox(dataSource()),
            audience = JdbcNotificationAudience(dataSource()),
        )

    private fun bidNowStrategy() =
        when (
            val result =
                validate(
                    StrategyDraft(
                        // 감시 게이트는 **규칙이 하나도 없으면 통과가 아니다**(`WatchVerdict.NoGate`
                        // 를 통과로 재해석하지 않는다, D-1). 그래서 규칙 하나를 둔다 — 이 공고에
                        // 맞지 않는 제외 키워드라 제외가 걸리지 않고 그 축이 통과로 접힌다.
                        excludeKeywordTerms = listOf("이-공고에-없는-말"),
                        bidNowThreshold = BigDecimal("0.5"),
                        reviewThreshold = BigDecimal("0.3"),
                        maxActiveBids = MAX_ACTIVE_BIDS,
                    ),
                    StrategyRevision(1),
                    STRATEGY_POLICY.resolve(LocalDate.ofInstant(NOW, ZoneOffset.UTC))
                        as Resolution.Resolved<StrategyPolicyData>,
                )
        ) {
            is StrategyValidation.Valid -> result.strategy
            is StrategyValidation.Invalid -> error("test fixture 는 항상 Valid 여야 한다: ${result.violations}")
        }

    private fun insertAccount(loginName: String): AccountId =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, failed_attempts) " +
                        "VALUES (?, 'ops-code-fixture-hash', now(), now(), 0) RETURNING id",
                ).use { statement ->
                    statement.setString(1, loginName)
                    statement.executeQuery().use { rows ->
                        rows.next()
                        AccountId(rows.getLong("id"))
                    }
                }
        }

    /** 후보가 될 공고 한 행 — `JdbcCandidateSourceTest` 와 같은 조립이다(마감은 미래). */
    private fun insertNotice(number: String): NoticeId {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                NOW,
            )
        val key = appendRawObservation(observation)
        val command =
            NoticeCollected(
                id = id,
                businessCategory = null,
                baseAmount = null,
                estimatedAmount = null,
                allocatedBudget = null,
                floorRate = null,
                deadlineAt = NOW.plusSeconds(86_400),
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        return id
    }

    private data class InboxRow(
        val accountId: Long,
        val kind: String,
        val noticeNumber: String,
        val detail: String?,
        /**
         * **투찰 종류 축**(M9/paper-bid V25) — 새 후보는 건이 생기기 **전**의 소식이라 `NONE` 이다.
         * 열을 읽는 이유: 커널의 `require` 와 저장 층의 짝 CHECK 가 같은 짝을 두 겹으로 막지만,
         * 「production 경로가 실제로 그 값을 **쓴다**」는 이 열을 되읽어야 나온다.
         */
        val bidKind: String,
    )

    private fun inboxRows(): List<InboxRow> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT account_id, kind, notice_number, detail_code, bid_kind FROM notification_inbox " +
                        "ORDER BY account_id, kind",
                ).use { statement ->
                    statement.executeQuery().use { rows ->
                        val read = mutableListOf<InboxRow>()
                        while (rows.next()) {
                            read +=
                                InboxRow(
                                    rows.getLong("account_id"),
                                    rows.getString("kind"),
                                    rows.getString("notice_number"),
                                    rows.getString("detail_code"),
                                    rows.getString("bid_kind"),
                                )
                        }
                        read
                    }
                }
        }

    private fun outboxCount(): Int =
        dataSource().connection.use { connection ->
            connection.prepareStatement("SELECT count(*) AS n FROM outbox").use { statement ->
                statement.executeQuery().use { rows ->
                    rows.next()
                    rows.getInt("n")
                }
            }
        }
}

private const val CANDIDATE_CAP = 10
private const val MAX_ACTIVE_BIDS = 5
