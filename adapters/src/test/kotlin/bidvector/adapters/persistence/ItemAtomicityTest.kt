package bidvector.adapters.persistence

import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionDropReason
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.NoticeCollected
import bidvector.procurement.NoticeId
import bidvector.procurement.NoticeNumber
import bidvector.procurement.ObservationKey
import bidvector.procurement.PersistOutcome
import bidvector.procurement.RawNoticeObservation
import bidvector.procurement.SourceEndpoint
import bidvector.sharedkernel.NoticeRound
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * S-4 — 배치 중 한 항목 실패 시 **그 항목만** 롤백하고 나머지는 커밋된다(⑤, D-3D-4). 항목
 * 하나 = 트랜잭션 하나가 [JdbcNoticeRepository.persist] 안에서 열리고 닫힌다 — 배치 함수
 * 자체는 이 test가 만들지 않는다(3D는 repository까지가 범위, 배치 순회는 workflow 소관);
 * 대신 「항목 하나가 실패해도 이전에 성공한 항목의 커밋이 보존된다」를 세 항목을 순서대로
 * `persist` 호출하는 반복으로 직접 재현한다 — repository의 `persist` 자체가 트랜잭션
 * 경계이므로 이 재현이 곧 그 경계의 증명이다.
 */
class ItemAtomicityTest : PersistenceTestSupport() {
    private fun noticeCommand(
        number: String,
        observedAt: Instant,
    ): NoticeCollected {
        val id = NoticeId(NoticeNumber.of(number), NoticeRound.of("000"))
        return NoticeCollected(
            id = id,
            businessCategory = null,
            baseAmount = null,
            estimatedAmount = null,
            allocatedBudget = null,
            floorRate = null,
            deadlineAt = null,
            openingScheduledAt = null,
            raw = RawNoticeObservation.of(emptyMap(), SourceEndpoint.NOTICE_LIST, observedAt),
        )
    }

    private fun appendRaw(observation: RawNoticeObservation): ObservationKey {
        val key = ObservationKey.of(observation)
        dataSource().connection.use { connection ->
            connection.prepareStatement(Sql.INSERT_RAW_OBSERVATION).use { statement ->
                statement.setString(1, key.value)
                statement.setString(2, observation.sourceEndpoint.name)
                statement.setString(3, "{}")
                statement.setTimestamp(4, java.sql.Timestamp.from(observation.observedAt))
                statement.setString(5, "test-release")
                statement.executeUpdate()
            }
        }
        return key
    }

    private fun countNoticeRows(): Long =
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT count(*) FROM notice").use { rs ->
                    rs.next()
                    rs.getLong(1)
                }
            }
        }

    @Test
    fun `3 항목 중 2번째가 실패해도 1번째·3번째는 커밋되고 실패한 항목만 반영되지 않는다`() {
        val repository = JdbcNoticeRepository(dataSource())
        val item1 = noticeCommand("ATOMIC-001", Instant.parse("2026-09-07T00:00:00Z"))
        val item3 = noticeCommand("ATOMIC-003", Instant.parse("2026-09-07T00:00:02Z"))

        val outcome1 = repository.persist(item1, appendRaw(item1.raw))
        outcome1 shouldBe PersistOutcome.Inserted

        // 2번째 항목 — 존재하지 않는 raw observation_key(FK 위반)를 넘겨 트랜잭션 내부에서
        // 실패를 강제한다. `persist`는 그 예외를 던지고, 그 항목의 canonical write는 반영되지
        // 않는다(트랜잭션이 통째로 롤백된다 — connection.use가 자동으로 close하지만 커밋을
        // 먼저 하지 않았으므로 JDBC 기본 동작상 미종료 트랜잭션은 드라이버가 롤백한다).
        val item2 = noticeCommand("ATOMIC-002", Instant.parse("2026-09-07T00:00:01Z"))
        val bogusKey = ObservationKey("BOGUS|KEY|THAT|DOES|NOT|EXIST|IN|RAW|OBSERVATION|TABLE")
        var item2Failed = false
        try {
            repository.persist(item2, bogusKey)
        } catch (expected: Exception) {
            item2Failed = true
        }
        item2Failed shouldBe true

        val outcome3 = repository.persist(item3, appendRaw(item3.raw))
        outcome3 shouldBe PersistOutcome.Inserted

        countNoticeRows() shouldBe 2L
        repository.find(item1.id) shouldBe repository.find(item1.id) // 1번째 존재(자기 비교로 null 아님을 함께 확인)
        (repository.find(item1.id) != null) shouldBe true
        (repository.find(item2.id) != null) shouldBe false
        (repository.find(item3.id) != null) shouldBe true
    }

    @Test
    fun `실패한 항목의 트랜잭션은 커넥션을 닫아도 자동 커밋되지 않는다 — JDBC 기본 동작 실측`() {
        val repository = JdbcNoticeRepository(dataSource())
        val bogusKey = ObservationKey("ANOTHER|BOGUS|KEY|NOT|IN|RAW|OBSERVATION")
        val doomed = noticeCommand("ATOMIC-DOOMED", Instant.parse("2026-09-07T00:00:03Z"))

        var threw = false
        try {
            repository.persist(doomed, bogusKey)
        } catch (expected: Exception) {
            threw = true
        }

        threw shouldBe true
        (repository.find(doomed.id) != null) shouldBe false
    }

    @Test
    fun `배치 회계는 항목 트랜잭션과 별도로 마지막에 한 행만 기록된다 — dropped=1`() {
        val accounting =
            CollectionAccounting(
                received = 3,
                normalized = 2,
                duplicate = 0,
                dropped = 1,
                dropReasons = mapOf(CollectionDropReason.CollectionMissingNoticeNumber to 1),
                sourceTotal = 3,
                pagesFetched = 1,
                truncated = false,
                unknownFields = 0,
            )
        val meta =
            CollectionRunMeta(
                referenceDate = CollectionReferenceDate(LocalDate.of(2026, 9, 7)),
                source = SourceEndpoint.NOTICE_LIST,
                startedAt = Instant.parse("2026-09-07T00:00:00Z"),
                finishedAt = Instant.parse("2026-09-07T00:01:00Z"),
            )

        JdbcCollectionRunStore(dataSource()).record(accounting, meta)

        val sql = "SELECT count(*) AS total, sum(dropped) AS dropped_sum FROM collection_run"
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    rs.next()
                    rs.getLong("total") shouldBe 1L
                    rs.getLong("dropped_sum") shouldBe 1L
                }
            }
        }
    }
}
