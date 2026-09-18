package bidvector.adapters.evaluation

import bidvector.adapters.persistence.JdbcNoticeRepository
import bidvector.adapters.persistence.PersistenceTestSupport
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.NoticeStatus
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawKey
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import bidvector.workflow.strategy.Clock
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * [JdbcCandidateSource] — 상태 집합·마감 경계·순서·상한 초과 실패·복원 왕복(scope.md
 * in_scope 목록). 상태 집합의 **정의**는 `CandidateStatusSetTest`가 별도로 잰다 — 여기서는
 * DB에 실제로 여섯 상태를 다 심어 질의가 그 정의대로 걸러내는지만 본다.
 */
class JdbcCandidateSourceTest : PersistenceTestSupport() {
    private val now = Instant.parse("2026-09-18T00:00:00Z")
    private val clock = Clock { now }

    private fun source(cap: Int = 10) = JdbcCandidateSource(dataSource(), clock, cap)

    private fun insertNotice(
        number: String,
        deadline: Instant?,
        status: NoticeStatus = NoticeStatus.Open,
        round: String = "000",
    ): NoticeId {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of(round))
        val observation =
            RawNoticeObservation.of(
                mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                SourceEndpoint.NOTICE_LIST,
                now,
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
                deadlineAt = deadline,
                openingScheduledAt = null,
                raw = observation,
            )
        JdbcNoticeRepository(dataSource()).persist(command, key) shouldBe PersistOutcome.Inserted
        if (status != NoticeStatus.Open) setStatus(id, status)
        return id
    }

    /**
     * insert는 항상 `status="Open"`이다(production 경로) — 다른 종단 상태는 test 전용 직접
     * SQL로 만든다. `guard_notice_status` 트리거(V2)가 status 축 변경에 **새
     * observation_key**를 요구해(신선도 가드) 같은 값을 재사용하면 거부된다 — raw 관측을
     * 하나 더 남기고 그 key로 갱신한다. `ObservationKeyDerivation`은 등재 필드
     * (`bidNtceNo`·`bidNtceOrd`)·`sourceEndpoint`·`observedAt`의 해시라 insert 때 쓴
     * 관측과 필드가 같으면 같은 키가 나온다 — `observedAt`을 하나 미뤄 실제로 새 키를
     * 만든다.
     */
    private fun setStatus(
        id: NoticeId,
        status: NoticeStatus,
    ) {
        val freshKey =
            appendRawObservation(
                RawNoticeObservation.of(
                    mapOf(RawKey("bidNtceNo") to id.number.value, RawKey("bidNtceOrd") to id.round.value),
                    SourceEndpoint.NOTICE_LIST,
                    now.plusSeconds(1),
                ),
            )
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "UPDATE notice SET status = ?, observation_key = ? WHERE notice_number = ? AND notice_round = ?",
                ).use { statement ->
                    statement.setString(1, status.name)
                    statement.setString(2, freshKey.value)
                    statement.setString(3, id.number.value)
                    statement.setString(4, id.round.value)
                    statement.executeUpdate()
                }
        }
    }

    @Test
    fun `Open 과 Renoticed 만 후보다 — 나머지 네 상태는 제외된다`() {
        val future = now.plusSeconds(3600)
        val open = insertNotice("STATUS-OPEN", future, NoticeStatus.Open)
        val renoticed = insertNotice("STATUS-RENOTICED", future, NoticeStatus.Renoticed)
        insertNotice("STATUS-CLOSED", future, NoticeStatus.Closed)
        insertNotice("STATUS-AWARDED", future, NoticeStatus.Awarded)
        insertNotice("STATUS-FAILED", future, NoticeStatus.Failed)
        insertNotice("STATUS-CANCELLED", future, NoticeStatus.Cancelled)

        val candidateIds = source().openCandidates().map { it.id }.toSet()

        candidateIds shouldBe setOf(open, renoticed)
    }

    @Test
    fun `마감이 now 와 같으면 후보가 아니다 — 반개구간`() {
        insertNotice("DEADLINE-EQUAL", now)

        source().openCandidates() shouldBe emptyList()
    }

    @Test
    fun `마감이 지난 공고는 후보가 아니다`() {
        insertNotice("DEADLINE-PAST", now.minusSeconds(1))

        source().openCandidates() shouldBe emptyList()
    }

    @Test
    fun `마감이 없는 공고는 후보가 아니다`() {
        insertNotice("DEADLINE-NULL", null)

        source().openCandidates() shouldBe emptyList()
    }

    @Test
    fun `마감이 미래인 공고는 후보다`() {
        val id = insertNotice("DEADLINE-FUTURE", now.plusSeconds(1))

        source().openCandidates().map { it.id } shouldBe listOf(id)
    }

    @Test
    fun `순서는 마감 오름차순, 그 다음 공고번호, 그 다음 차수다`() {
        val far = insertNotice("ORDER-FAR", now.plusSeconds(3000))
        val near = insertNotice("ORDER-NEAR", now.plusSeconds(1000))
        val sameDeadlineA = insertNotice("ORDER-SAME-A", now.plusSeconds(2000))
        val sameDeadlineB = insertNotice("ORDER-SAME-B", now.plusSeconds(2000))

        val orderedIds = source().openCandidates().map { it.id }

        orderedIds shouldBe listOf(near, sameDeadlineA, sameDeadlineB, far)
    }

    @Test
    fun `상한을 넘으면 조용히 자르지 않고 크게 실패한다`() {
        val future = now.plusSeconds(3600)
        insertNotice("CAP-1", future)
        insertNotice("CAP-2", future)
        insertNotice("CAP-3", future)

        val exception = shouldThrow<CandidateCapExceededException> { source(cap = 2).openCandidates() }

        exception.cap shouldBe 2
    }

    @Test
    fun `cap 을 넘지 않으면 성공한다`() {
        val future = now.plusSeconds(3600)
        insertNotice("CAP-OK-1", future)
        insertNotice("CAP-OK-2", future)

        source(cap = 2).openCandidates() shouldHaveSize 2
    }

    @Test
    fun `복원된 Notice 는 저장한 마감·상태를 그대로 낸다 — 왕복`() {
        val deadline = Instant.parse("2026-10-01T09:00:00Z")
        val id = insertNotice("ROUNDTRIP-001", deadline)

        val restored = source().openCandidates().single { it.id == id }

        restored.status shouldBe NoticeStatus.Open
        restored.deadlineAt shouldBe deadline
    }
}
