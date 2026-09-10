package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 — 빈 컨테이너에서 `V1__`부터 migrate한 스키마가 test가 기대하는 스키마와 같다(⑧,
 * D-3D-6). `PersistenceTestSupport` companion object의 `init` 블록이 이미 clean 컨테이너에
 * 전건 migrate를 실행했다.
 *
 * **verifier r1 F-3 뒤 개정** — 원판은 테이블·트리거 **이름 집합**만 봤다. D-3D-6이 고정한
 * 여덟 축(테이블·컬럼·타입·NOT NULL·UNIQUE·FK·트리거·CHECK)을 전부 대조한다 — verifier가
 * 실측한 변이(COL-06 항등식 CHECK 삭제·`NUMERIC(20,0)`→`NUMERIC(20,4)`·`notice_round` 형식
 * CHECK 삭제)가 각각 이 test들 중 하나 이상을 FAIL시킨다(수치·정밀도·CHECK 개수·CHECK
 * 본문 부분 문자열까지 본다).
 *
 * 축 2·3·4(컬럼)는 `CleanMigrationColumnTest`로, 축 7(트리거)은 `CleanMigrationTriggerTest`로,
 * 축 8(CHECK)은 `CleanMigrationCheckTest`로 분리했다(sizeGate 500줄 — M3/3E에서 축7·8을,
 * M4/4C-2에서 축2·3·4를 분리).
 */
class CleanMigrationTest : PersistenceTestSupport() {
    // =========================================================================
    // 축 1 — 테이블
    // =========================================================================
    private val expectedTables =
        setOf(
            "raw_observation",
            "provenance_authority",
            "notice",
            "notice_audit",
            "rejected_write",
            "opening_result",
            "qualification_text",
            "collection_run",
            "flyway_schema_history",
            // M3/3E — 스키마 스냅샷 래칫 예외(운영자 승인 2026-09-08, scope.md), 추가만.
            "opening_reserve_price",
            // M4/4C-2 — 스키마 스냅샷 래칫 예외(D-4C2-2, 추가만). V6__outbox_inbox.sql.
            "outbox",
            "inbox",
        )

    @Test
    fun `축1 테이블 목록이 기대와 같다`() {
        val actual = queryStrings("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
        actual shouldContainExactlyInAnyOrder expectedTables
    }

    // =========================================================================
    // 축 5 — UNIQUE(PK 밖의 별도 UNIQUE는 없다 — 전부 PK 하나로 유일성을 진다)
    // =========================================================================
    @Test
    fun `축5 UNIQUE — PK 밖의 별도 UNIQUE 제약은 없다`() {
        val uniqueOutsidePk =
            queryStrings(
                "SELECT constraint_name FROM information_schema.table_constraints " +
                    "WHERE table_schema='public' AND constraint_type='UNIQUE'",
            )
        uniqueOutsidePk shouldBe emptySet()
    }

    // =========================================================================
    // 축 5b — PRIMARY KEY 컬럼 집합
    // =========================================================================
    private val expectedPrimaryKeys =
        mapOf(
            "raw_observation" to setOf("observation_key"),
            "provenance_authority" to setOf("provenance"),
            "notice" to setOf("notice_number", "notice_round"),
            "notice_audit" to setOf("id"),
            "rejected_write" to setOf("id"),
            "opening_result" to setOf("notice_number", "notice_round"),
            "qualification_text" to setOf("notice_number", "notice_round"),
            "collection_run" to setOf("id"),
            // M3/3E — 층 B 자식 표(추가만).
            "opening_reserve_price" to setOf("notice_number", "notice_round", "reserve_price_sequence"),
            // M4/4C-2 — 애플리케이션이 발급하는 TEXT PK(추가만, D-4C2-2 — 시퀀스를 만들지
            // 않는다, V6__outbox_inbox.sql).
            "outbox" to setOf("entry_id"),
            "inbox" to setOf("idempotency_key"),
        )

    @Test
    fun `축5b PRIMARY KEY 컬럼 집합이 기대와 같다`() {
        val actual = mutableMapOf<String, MutableSet<String>>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT tc.table_name, kcu.column_name FROM information_schema.table_constraints tc " +
                            "JOIN information_schema.key_column_usage kcu " +
                            "  ON tc.constraint_name = kcu.constraint_name AND tc.table_schema = kcu.table_schema " +
                            "WHERE tc.table_schema='public' AND tc.constraint_type='PRIMARY KEY' " +
                            "  AND tc.table_name <> 'flyway_schema_history'",
                    ).use { rs ->
                        while (rs.next()) {
                            actual.getOrPut(rs.getString("table_name")) { mutableSetOf() } +=
                                rs.getString("column_name")
                        }
                    }
            }
        }
        actual shouldBe expectedPrimaryKeys
    }

    // =========================================================================
    // 축 6 — FOREIGN KEY
    // =========================================================================
    private data class FkSpec(
        val table: String,
        val column: String,
        val foreignTable: String,
        val foreignColumn: String,
    )

    private val expectedForeignKeys =
        setOf(
            FkSpec("notice", "observation_key", "raw_observation", "observation_key"),
            FkSpec("opening_result", "observation_key", "raw_observation", "observation_key"),
            FkSpec("qualification_text", "observation_key", "raw_observation", "observation_key"),
            // M3/3E — 층 B 자식 표(추가만).
            FkSpec("opening_reserve_price", "observation_key", "raw_observation", "observation_key"),
            // 복합(2컬럼) FK는 이 질의(constraint_name join만, 컬럼 순서 미보존)에서 cross
            // product 4행으로 관측된다(실측, PostgreSQL 알려진 특성 — 스키마 결함이 아니다).
            FkSpec("opening_reserve_price", "notice_number", "opening_result", "notice_number"),
            FkSpec("opening_reserve_price", "notice_number", "opening_result", "notice_round"),
            FkSpec("opening_reserve_price", "notice_round", "opening_result", "notice_number"),
            FkSpec("opening_reserve_price", "notice_round", "opening_result", "notice_round"),
        )

    @Test
    fun `축6 FOREIGN KEY 목록이 기대와 같다`() {
        val actual = mutableSetOf<FkSpec>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT tc.table_name, kcu.column_name, " +
                            "ccu.table_name AS foreign_table, ccu.column_name AS foreign_column " +
                            "FROM information_schema.table_constraints tc " +
                            "JOIN information_schema.key_column_usage kcu " +
                            "  ON tc.constraint_name = kcu.constraint_name " +
                            "JOIN information_schema.constraint_column_usage ccu " +
                            "  ON tc.constraint_name = ccu.constraint_name " +
                            "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema='public'",
                    ).use { rs ->
                        while (rs.next()) {
                            actual +=
                                FkSpec(
                                    rs.getString("table_name"),
                                    rs.getString("column_name"),
                                    rs.getString("foreign_table"),
                                    rs.getString("foreign_column"),
                                )
                        }
                    }
            }
        }
        actual shouldContainExactlyInAnyOrder expectedForeignKeys
    }

    // 축 7(트리거)은 `CleanMigrationTriggerTest`로, 축 8(CHECK)은 `CleanMigrationCheckTest`로
    // 분리했다(sizeGate 500줄, M3/3E에서 재분리).

    @Test
    fun `flyway validate 가 통과한다 — migration 이력과 파일이 어긋나지 않는다`() {
        val flyway =
            org.flywaydb.core.Flyway
                .configure()
                .dataSource(dataSource())
                .locations("classpath:db/migration")
                .load()

        // validate()가 예외를 던지지 않으면 통과 — 별도 assertion 없이 호출 자체가 단언이다.
        flyway.validate()
        true shouldBe true
    }

    @Test
    fun `애플리케이션 역할 bidvector_app 은 provenance_authority 를 SELECT 만 할 수 있다`() {
        val grantedPrivileges =
            queryStrings(
                "SELECT privilege_type FROM information_schema.role_table_grants " +
                    "WHERE grantee = 'bidvector_app' AND table_name = 'provenance_authority'",
            )
        grantedPrivileges shouldContainExactlyInAnyOrder setOf("SELECT")
    }

    @Test
    fun `애플리케이션 역할 bidvector_app 은 notice_audit 에 INSERT 권한이 없다 — F-6`() {
        val grantedPrivileges =
            queryStrings(
                "SELECT privilege_type FROM information_schema.role_table_grants " +
                    "WHERE grantee = 'bidvector_app' AND table_name = 'notice_audit'",
            )
        grantedPrivileges shouldContainExactlyInAnyOrder setOf("SELECT")
    }

    /**
     * verifier r1 H-1 시정 — 설계 검토 (2b) 「V6 테이블 자체 | GRANT 목록을 test 가 대조」의
     * 실측이 없었다. 값 자체는 V6와 같지만(`SELECT, INSERT, UPDATE`), 이 test가 없으면
     * `GRANT ALL PRIVILEGES`(= 커밋됐지만 아직 배달 안 된 항목을 지울 수 있는
     * DELETE·TRUNCATE 획득, 위협 모델 방어 (b))로 되돌려도 `:adapters:test`가 전건
     * 초록이었다(mutation 실측, `reports/evidence/m4/4c2/commands.md`).
     */
    @Test
    fun `애플리케이션 역할 bidvector_app 은 outbox 를 SELECT INSERT UPDATE 만 할 수 있다`() {
        val grantedPrivileges =
            queryStrings(
                "SELECT privilege_type FROM information_schema.role_table_grants " +
                    "WHERE grantee = 'bidvector_app' AND table_name = 'outbox'",
            )
        grantedPrivileges shouldContainExactlyInAnyOrder setOf("SELECT", "INSERT", "UPDATE")
    }

    @Test
    fun `애플리케이션 역할 bidvector_app 은 inbox 를 SELECT INSERT 만 할 수 있다`() {
        val grantedPrivileges =
            queryStrings(
                "SELECT privilege_type FROM information_schema.role_table_grants " +
                    "WHERE grantee = 'bidvector_app' AND table_name = 'inbox'",
            )
        grantedPrivileges shouldContainExactlyInAnyOrder setOf("SELECT", "INSERT")
    }

    private fun queryStrings(sql: String): Set<String> {
        val actual = mutableSetOf<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(sql).use { rs ->
                    while (rs.next()) actual += rs.getString(1)
                }
            }
        }
        return actual
    }
}
