package bidvector.adapters.persistence

import bidvector.adapters.event.OutboxGateValues
import bidvector.adapters.event.insertOutboxRowDirectly
import bidvector.procurement.CollectionAccounting
import bidvector.procurement.CollectionReferenceDate
import bidvector.procurement.CollectionRunMeta
import bidvector.procurement.SourceEndpoint
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.Instant
import java.time.LocalDate

/**
 * M9/OPS-10 — 「모든 수집·실행 행이 자기를 만든 릴리스 식별자를 갖는다」를 실 DB 로 잰다.
 *
 * **두 갈래를 함께 잰다.**
 * - `collection_run`·`outbox` 는 **자기 열**로 답한다(V27) — 그 둘에는 관측을 가리키는 열이
 *   없어 되짚을 경로가 아예 없었다. 쓰기 경로가 값을 싣는지, 싣지 않은 새 행이 거부되는지,
 *   적힌 값을 지울 수 없는지를 잰다.
 * - `notice`·`opening_result`·`qualification_text`·`opening_reserve_price` 는 `observation_key`
 *   (NOT NULL FK) → `raw_observation.release_sha`(NOT NULL) **조인으로 도달한다**. 아래 test 가
 *   고정하는 것은 그 **도달성**이고(열이 nullable 로 바뀌거나 FK 가 사라지면 먼저 붉어진다),
 *   조인이 답하는 것은 「그 **원문을 수집한** 릴리스」다 — 승격 러너가 쓰는 행에서는 그것이
 *   「이 행을 **쓴** 릴리스」와 다를 수 있다(V27 머리말 · `OPEN-M9-PROMOTED-ROW-RELEASE`).
 */
class ExecutionRowReleaseTest : PersistenceTestSupport() {
    @Test
    fun `수집 회계 행은 자기를 만든 릴리스를 싣는다`() {
        JdbcCollectionRunStore(dataSource(), TEST_RELEASE).record(accounting(), meta())

        selectOne("SELECT release_sha FROM collection_run") shouldBe TEST_RELEASE_SHA
    }

    @Test
    fun `릴리스 없는 새 회계 행은 트리거가 거부한다`() {
        val failure =
            shouldThrow<SQLException> {
                execute(
                    "INSERT INTO collection_run (reference_date, source_endpoint, started_at, finished_at, " +
                        "received, normalized, duplicate, dropped, drop_reasons, pages_fetched, truncated, " +
                        "unknown_fields) VALUES (DATE '2026-01-01', 'NOTICE_LIST', now(), now(), 0, 0, 0, 0, " +
                        "'{}'::jsonb, 0, false, 0)",
                )
            }

        failure.message.orEmpty() shouldContain "릴리스 식별자를 요구한다"
        selectOne("SELECT count(*)::text FROM collection_run") shouldBe "0"
    }

    /**
     * **outbox 의 INSERT 관문**(verifier M-1) — V27 은 표 둘에 같은 트리거를 걸지만 거부를 재는
     * 자리는 `collection_run` 하나였다. 출하 경로는 `JdbcOutboxPort.register` 하나라 오늘의 거동
     * 결함은 아니지만, 재지 않으면 outbox 쪽 트리거를 `BEFORE UPDATE` 로 좁히는 변이가 초록으로
     * 지나간다(변이 `m9_outbox_rel_updateonly` 실측).
     */
    @Test
    fun `릴리스 없는 새 outbox 행은 트리거가 거부한다`() {
        val failure =
            shouldThrow<SQLException> {
                insertOutboxRowDirectly(
                    dataSource(),
                    entryId = "x-outbox-norelease",
                    idempotencyKey = "x-outbox-norelease-key",
                    gate = OutboxGateValues(releaseSha = null),
                )
            }

        failure.message.orEmpty() shouldContain "릴리스 식별자를 요구한다"
        selectOne("SELECT count(*)::text FROM outbox") shouldBe "0"
    }

    @Test
    fun `적힌 릴리스는 지울 수 없다 — 회계 행`() {
        JdbcCollectionRunStore(dataSource(), TEST_RELEASE).record(accounting(), meta())

        val failure =
            shouldThrow<SQLException> { execute("UPDATE collection_run SET release_sha = NULL") }

        failure.message.orEmpty() shouldContain "한 번만 적는다"
        selectOne("SELECT release_sha FROM collection_run") shouldBe TEST_RELEASE_SHA
    }

    /**
     * **바꿔치기도 막는다**(cr M-1). 앞 판은 지우기만 막아 값 A → 값 B 가 두 검사를 모두 지났다 —
     * 「어느 릴리스가 이 행을 만들었는가」가 OPS-10 의 전부인데 그 값을 나중에 다른 값으로 덮는
     * 길이 저장 층에 열려 있었다. 같은 slice 가 같은 표의 종단 사유에는 그 길을 닫아 두었으므로
     * (V26 의 「한 번만 적는다」) 비대칭을 없애는 쪽으로 닫는다.
     *
     * **`outbox` 가 특히 그렇다** — 앱 역할이 UPDATE 권한을 **실제로 가진** 유일한 표다
     * (`collection_run` 은 V2 가 SELECT·INSERT 만 준다).
     */
    @Test
    fun `적힌 릴리스는 바꿔치울 수도 없다 — 두 표 전수`() {
        JdbcCollectionRunStore(dataSource(), TEST_RELEASE).record(accounting(), meta())
        insertOutboxRowDirectly(
            dataSource(),
            entryId = "x-swap",
            idempotencyKey = "x-swap-key",
            gate = OutboxGateValues(),
        )

        listOf("collection_run", "outbox").forEach { table ->
            shouldThrow<SQLException> {
                execute("UPDATE $table SET release_sha = 'swapped-release'")
            }.message.orEmpty() shouldContain "한 번만 적는다"
            selectOne("SELECT release_sha FROM $table") shouldBe TEST_RELEASE_SHA
        }
    }

    /**
     * **`outbox` 의 UPDATE 갈래**(cr M-2). 함수 본문의 UPDATE 갈래는 `collection_run` test 가 덮지만
     * **트리거 이벤트는 표마다 따로**다 — `BEFORE INSERT OR UPDATE` 를 `BEFORE INSERT` 로 좁히는
     * 변이는 이 test 가 없으면 아무것도 붉히지 않는다(v r3 가 찾은 반대 방향 변이의 거울이다).
     */
    @Test
    fun `적힌 릴리스는 지울 수 없다 — outbox 행`() {
        insertOutboxRowDirectly(
            dataSource(),
            entryId = "x-erase",
            idempotencyKey = "x-erase-key",
            gate = OutboxGateValues(),
        )

        val failure =
            shouldThrow<SQLException> { execute("UPDATE outbox SET release_sha = NULL") }

        failure.message.orEmpty() shouldContain "한 번만 적는다"
        selectOne("SELECT release_sha FROM outbox") shouldBe TEST_RELEASE_SHA
    }

    @Test
    fun `빈 문자열 릴리스는 CHECK 가 거부한다`() {
        val failure =
            shouldThrow<SQLException> {
                execute(
                    "INSERT INTO collection_run (reference_date, source_endpoint, started_at, finished_at, " +
                        "received, normalized, duplicate, dropped, drop_reasons, pages_fetched, truncated, " +
                        "unknown_fields, release_sha) VALUES (DATE '2026-01-01', 'NOTICE_LIST', now(), now(), " +
                        "0, 0, 0, 0, '{}'::jsonb, 0, false, 0, '')",
                )
            }

        failure.message.orEmpty() shouldContain "collection_run_release_sha_check"
    }

    /**
     * 값 객체가 거부하는 것 — 공백과 과도한 길이. 「경계로 처리」로 적고 넘기지 않고 실측한다
     * (설계 검토 (2b) 표). **저장 층과 겹치지 않는다**: V27 CHECK 는 빈 문자열만 막고 공백
     * 하나(`' '`)·과도한 길이는 통과하므로, 그 둘을 막는 자리는 이 타입 **하나**다.
     */
    @Test
    fun `릴리스 식별자는 공백과 과도한 길이를 거부한다`() {
        shouldThrow<IllegalArgumentException> { ReleaseIdentifier("") }
        shouldThrow<IllegalArgumentException> { ReleaseIdentifier("   ") }
        shouldThrow<IllegalArgumentException> { ReleaseIdentifier("x".repeat(ReleaseIdentifier.MAX_LENGTH + 1)) }
        ReleaseIdentifier("x".repeat(ReleaseIdentifier.MAX_LENGTH)).value.length shouldBe
            ReleaseIdentifier.MAX_LENGTH
    }

    /**
     * 조인 갈래의 **구조적 전제** — 네 canonical 표가 릴리스를 조인으로 답할 수 있다는 것은
     * ① `observation_key` 가 NOT NULL 이고 ② 그 열이 `raw_observation` 을 가리키는 FK 이고
     * ③ `raw_observation.release_sha` 가 NOT NULL 이라는 세 사실이다. 하나라도 깨지면 V27 이
     * 열을 베끼지 않은 결정의 근거가 사라진다.
     */
    @Test
    fun `canonical 네 표의 릴리스는 observation_key 조인으로 도달한다 — 전제 셋`() {
        // **증분으로 읽는다**(`CleanMigration*Test` 와 같은 관례, 레인 간 래칫이다): 기준선 넷
        // (V1 셋 + V4 하나). `raw_observation` 에 FK 를 더하는 레인은 이 줄을 고치면서 **그 표의
        // 릴리스 도달 경로도 함께 판단해야 한다** — 이 단언이 붉어지는 것이 그 판단을 부르는 자리다.
        val joined = listOf("notice", "opening_result", "qualification_text", "opening_reserve_price")

        joined.forEach { table ->
            selectOne(
                "SELECT is_nullable FROM information_schema.columns " +
                    "WHERE table_schema = 'public' AND table_name = '$table' AND column_name = 'observation_key'",
            ) shouldBe "NO"
        }
        // **열 쌍까지** 본다 — 표 단위로만 재면 「어느 열이 `raw_observation` 을 가리키는가」가
        // 잠기지 않아, `observation_key` 가 아닌 다른 열의 FK 로도 이 단언이 초록이 된다.
        referencingColumnsOfRawObservation() shouldContainExactlyInAnyOrder
            joined.map { "$it.observation_key -> raw_observation.observation_key" }
        selectOne(
            "SELECT is_nullable FROM information_schema.columns WHERE table_schema = 'public' " +
                "AND table_name = 'raw_observation' AND column_name = 'release_sha'",
        ) shouldBe "NO"
    }

    /** `자식표.열 -> raw_observation.열` 문장 — 표 이름만이 아니라 **열 쌍**을 낸다. */
    private fun referencingColumnsOfRawObservation(): List<String> {
        val tables = mutableListOf<String>()
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT DISTINCT child.relname || '.' || ca.attname || ' -> raw_observation.' || pa.attname " +
                        "FROM pg_constraint c " +
                        "JOIN pg_class child ON child.oid = c.conrelid " +
                        "JOIN pg_class parent ON parent.oid = c.confrelid " +
                        "JOIN unnest(c.conkey, c.confkey) AS k(child_att, parent_att) ON true " +
                        "JOIN pg_attribute ca ON ca.attrelid = c.conrelid AND ca.attnum = k.child_att " +
                        "JOIN pg_attribute pa ON pa.attrelid = c.confrelid AND pa.attnum = k.parent_att " +
                        "WHERE c.contype = 'f' AND parent.relname = 'raw_observation'",
                ).use { statement ->
                    statement.executeQuery().use { rs ->
                        while (rs.next()) tables += rs.getString(1)
                    }
                }
        }
        return tables
    }

    private fun selectOne(sql: String): String? =
        dataSource().connection.use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
            }
        }

    private fun execute(sql: String) =
        dataSource().connection.use { connection -> connection.createStatement().use { it.execute(sql) } }

    private fun accounting(): CollectionAccounting =
        CollectionAccounting(
            received = 0,
            normalized = 0,
            duplicate = 0,
            dropped = 0,
            dropReasons = emptyMap(),
            sourceTotal = null,
            pagesFetched = 0,
            truncated = false,
            unknownFields = 0,
        )

    private fun meta(): CollectionRunMeta =
        CollectionRunMeta(
            referenceDate = CollectionReferenceDate(LocalDate.parse("2026-01-01")),
            source = SourceEndpoint.NOTICE_LIST,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            finishedAt = Instant.parse("2026-01-01T00:01:00Z"),
        )
}
