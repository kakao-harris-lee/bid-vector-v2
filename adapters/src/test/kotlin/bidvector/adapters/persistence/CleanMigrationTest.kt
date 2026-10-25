package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 — 빈 컨테이너에서 `V1__`부터 migrate한 스키마가 test가 기대하는 스키마와 같다(⑧,
 * D-3D-6). `PersistenceTestSupport` companion object의 `init` 블록이 이미 clean 컨테이너에
 * 전건 migrate를 실행했다.
 *
 * **개정** — 원판은 테이블·트리거 **이름 집합**만 봤다. D-3D-6이 고정한
 * 여덟 축(테이블·컬럼·타입·NOT NULL·UNIQUE·FK·트리거·CHECK)을 전부 대조한다 —
 * 실측한 변이(COL-06 항등식 CHECK 삭제·`NUMERIC(20,0)`→`NUMERIC(20,4)`·`notice_round` 형식
 * CHECK 삭제)가 각각 이 test들 중 하나 이상을 FAIL시킨다(수치·정밀도·CHECK 개수·CHECK
 * 본문 부분 문자열까지 본다).
 *
 * 축 2·3·4(컬럼)는 `CleanMigrationColumnTest`로, 축 7(트리거)은 `CleanMigrationTriggerTest`로,
 * 축 8(CHECK)은 `CleanMigrationCheckTest`로, 축 9(유효 권한 행렬)는
 * `CleanMigrationPrivilegeTest`로 분리했다(sizeGate 500줄 — 먼저 축7·8을,
 * 다음으로 축2·3·4를, 병합 뒤(두 slice가 각자 정당하게 늘린 합이
 * 501줄이 되어) 축9를 분리).
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
            // 스키마 스냅샷 래칫 예외(scope.md), 추가만.
            "opening_reserve_price",
            // 스키마 스냅샷 래칫 예외(D-4C2-2, 추가만). V6__outbox_inbox.sql.
            "outbox",
            "inbox",
            // 스키마 스냅샷 래칫 예외(D-6B1-8, 추가만). V8__edit_session.sql.
            "edit_session",
            // 스키마 스냅샷 래칫 예외(D-6F1-1, 추가만). V9__operator_strategy.sql.
            "operator_strategy",
            "operator_strategy_revision",
            // 스키마 스냅샷 래칫 예외(D-6F6-3, 추가만). V12__operator_profile.sql.
            "operator_profile",
            // 스키마 스냅샷 래칫 예외(D-6F5-4, 추가만). V13__notice_requirement.sql.
            "notice_requirement",
            "notice_requirement_row",
            // 스키마 스냅샷 래칫 예외(D-6A1-7, 추가만). V15__api_audit.sql.
            "api_request_audit",
            // 스키마 스냅샷 래칫 예외(M7/7D, 추가만). V20__account_browser_session.sql.
            "account",
            "browser_session",
            // 스키마 스냅샷 래칫 예외(M7/7B, 추가만). V21__bid_case.sql — 입찰 건 넷.
            "bid_case",
            "bid_case_transition",
            "bid_draft",
            "bid_record",
        )

    @Test
    fun `축1 테이블 목록이 기대와 같다`() {
        val actual = queryStrings("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
        actual shouldContainExactlyInAnyOrder expectedTables
    }

    // =========================================================================
    // 축 5 — UNIQUE(PK 밖의 UNIQUE 는 등재된 하나뿐이다)
    //
    // **공집합이 아니라 등재 집합이다**(M7/7D). V20 까지 이 축은 공집합이었고 그 문장은 「유일성은
    // 전부 PK 하나가 진다」였다 — `account.login_name` 이 그 예외다: 계정 표는 R-2 로 **다수 계정을
    // 전제**하므로 PK(식별자)와 로그인 식별자가 다른 축이고, 둘째 계정이 같은 로그인 이름을 갖는
    // 상태는 표 층에서 불가능해야 한다.
    //
    // UNIQUE **인덱스**로 두면 이 질의(`table_constraints`)에 안 잡혀 축이 조용히 통과한다 —
    // 그 길을 고르지 않고 제약으로 두고 이 목록에 등재한다.
    // =========================================================================
    private val expectedUniqueConstraints =
        setOf(
            "account_login_name_key",
            // M7/7B — 건은 (계정, 공고, 종류) 하나당 하나다(§6.1 「건은 하나다」). 나머지 셋은
            // append-only 표의 순번 유일성이고, 전이 쪽은 **낙관적 잠금의 둘째 겹**이다
            // (같은 판에 두 전이가 들어오면 여기서 걸린다).
            "bid_case_account_id_notice_number_notice_round_bid_kind_key",
            // 자식 셋의 복합 FK 대상(계약 D-7B-3).
            "bid_case_id_account_key",
            "bid_case_transition_case_id_case_version_key",
            // 기록의 복합 FK 대상(계약 D-7B-4).
            "bid_draft_id_case_account_key",
            "bid_draft_case_id_draft_seq_key",
            "bid_record_case_id_record_seq_key",
        )

    @Test
    fun `축5 UNIQUE — PK 밖의 UNIQUE 제약은 등재된 하나뿐이다`() {
        val uniqueOutsidePk =
            queryStrings(
                "SELECT constraint_name FROM information_schema.table_constraints " +
                    "WHERE table_schema='public' AND constraint_type='UNIQUE'",
            )
        uniqueOutsidePk shouldBe expectedUniqueConstraints
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
            // 층 B 자식 표(추가만).
            "opening_reserve_price" to setOf("notice_number", "notice_round", "reserve_price_sequence"),
            // 애플리케이션이 발급하는 TEXT PK(추가만, D-4C2-2 — 시퀀스를 만들지
            // 않는다, V6__outbox_inbox.sql).
            "outbox" to setOf("entry_id"),
            "inbox" to setOf("idempotency_key"),
            // 애플리케이션이 발급하는 TEXT PK(추가만, D-6B1-8). V8__edit_session.sql.
            "edit_session" to setOf("id"),
            // 싱글턴 고정 키(operator_strategy)·도메인 개정 번호(operator_strategy_revision).
            // 둘 다 시퀀스를 만들지 않는다(D-6F1-5, D-6F1-1 추가만).
            "operator_strategy" to setOf("id"),
            "operator_strategy_revision" to setOf("revision"),
            // 싱글턴 고정 키(D-6F6-3, 추가만). V12__operator_profile.sql.
            "operator_profile" to setOf("id"),
            // 헤더는 공고 복합키, 행은 그 위에 자연 키(serialNo)를 더한다(D-6F5-4).
            "notice_requirement" to setOf("notice_number", "notice_round"),
            "notice_requirement_row" to setOf("notice_number", "notice_round", "serial_no"),
            // 애플리케이션이 발급하지 않는다(GENERATED ALWAYS AS IDENTITY, D-6A1-7).
            "api_request_audit" to setOf("id"),
            // M7/7D — 계정은 IDENTITY, 세션은 **쿠키 값의 해시**가 키다(평문을 담는 열이 없다).
            "account" to setOf("id"),
            "browser_session" to setOf("session_digest"),
            // M7/7B — 넷 다 IDENTITY 다(애플리케이션이 식별자를 발급하지 않는다).
            "bid_case" to setOf("id"),
            "bid_case_transition" to setOf("id"),
            "bid_draft" to setOf("id"),
            "bid_record" to setOf("id"),
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

    /** 복합 FK 의 cross product — 이 질의는 열 순서를 보존하지 않아 열 수의 제곱만큼 관측된다(위 주석). */
    private fun compositeForeignKeys(
        table: String,
        columns: List<String>,
        foreignTable: String,
        foreignColumns: List<String>,
    ): Array<FkSpec> =
        columns
            .flatMap { column -> foreignColumns.map { FkSpec(table, column, foreignTable, it) } }
            .toTypedArray()

    /** 자식 표 하나의 FK 다섯 — 계정 단일 FK + (건, 계정) 복합 FK 의 cross product 넷. */
    private fun childOwnerForeignKeys(table: String): Array<FkSpec> =
        arrayOf(
            FkSpec(table, "account_id", "account", "id"),
            FkSpec(table, "case_id", "bid_case", "id"),
            FkSpec(table, "case_id", "bid_case", "account_id"),
            FkSpec(table, "account_id", "bid_case", "id"),
            FkSpec(table, "account_id", "bid_case", "account_id"),
        )

    private val expectedForeignKeys =
        setOf(
            FkSpec("notice", "observation_key", "raw_observation", "observation_key"),
            FkSpec("opening_result", "observation_key", "raw_observation", "observation_key"),
            FkSpec("qualification_text", "observation_key", "raw_observation", "observation_key"),
            // 층 B 자식 표(추가만).
            FkSpec("opening_reserve_price", "observation_key", "raw_observation", "observation_key"),
            // 복합(2컬럼) FK는 이 질의(constraint_name join만, 컬럼 순서 미보존)에서 cross
            // product 4행으로 관측된다(실측, PostgreSQL 알려진 특성 — 스키마 결함이 아니다).
            FkSpec("opening_reserve_price", "notice_number", "opening_result", "notice_number"),
            FkSpec("opening_reserve_price", "notice_number", "opening_result", "notice_round"),
            FkSpec("opening_reserve_price", "notice_round", "opening_result", "notice_number"),
            FkSpec("opening_reserve_price", "notice_round", "opening_result", "notice_round"),
            // 행 표가 헤더 표를 참조한다(D-6F5-4, ON DELETE CASCADE).
            // 복합(2컬럼) FK라 같은 cross product 넷이 나온다(위 opening_reserve_price와 같은 이유).
            FkSpec("notice_requirement_row", "notice_number", "notice_requirement", "notice_number"),
            FkSpec("notice_requirement_row", "notice_number", "notice_requirement", "notice_round"),
            FkSpec("notice_requirement_row", "notice_round", "notice_requirement", "notice_number"),
            FkSpec("notice_requirement_row", "notice_round", "notice_requirement", "notice_round"),
            // M7/7D — 세션은 계정을 참조한다(단일 컬럼 FK, CASCADE 없음: 계정을 지우는 경로가
            // 아직 없고, 생기면 세션 폐기가 그 경로의 일이다).
            FkSpec("browser_session", "account_id", "account", "id"),
            // M7/7B — 사용자 소유 표 넷이 전부 계정을 참조한다(R-2). 자식 셋은 건을 참조하고,
            // 기록은 기본값으로 삼은 초안을 참조한다(소급 확인이면 null).
            //
            // **`notice` 를 참조하는 FK 가 이 목록에 없는 것이 설계다** — 6B-3 이 `notice` 를
            // Purge(TimeOnly) 로 두므로 FK 를 걸면 파기 sweep 이 실패하거나(RESTRICT) 운영자의
            // 투찰 기록이 공고와 함께 사라진다(CASCADE). 공고 키와 스냅샷은 사본이다(V21 머리말).
            FkSpec("bid_case", "account_id", "account", "id"),
            // 자식 셋은 건을 **(건, 계정) 복합 FK** 로 참조한다(계약 D-7B-3) — 자식 행의 계정이 부모 건의
            // 소유자와 같다는 것을 구조가 진다. 복합 FK 라 위와 같은 cross product 넷이 나온다.
            *childOwnerForeignKeys("bid_case_transition"),
            *childOwnerForeignKeys("bid_draft"),
            *childOwnerForeignKeys("bid_record"),
            // 기록 → 초안은 (초안, 건, 계정) 복합 FK 다(계약 D-7B-4) — 열 셋이라 cross product 아홉이 나온다.
            *compositeForeignKeys(
                "bid_record",
                listOf("draft_id", "case_id", "account_id"),
                "bid_draft",
                listOf("id", "case_id", "account_id"),
            ),
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
    // 분리했다(sizeGate 500줄, 재분리).

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
