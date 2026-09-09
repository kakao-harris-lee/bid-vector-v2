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
        )

    @Test
    fun `축1 테이블 목록이 기대와 같다`() {
        val actual = queryStrings("SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'")
        actual shouldContainExactlyInAnyOrder expectedTables
    }

    // =========================================================================
    // 축 2·3·4 — 컬럼 존재·타입·NOT NULL(information_schema.columns 한 질의로 셋을 함께 본다)
    // =========================================================================
    private data class ColumnSpec(
        val table: String,
        val column: String,
        val dataType: String,
        val nullable: Boolean,
    )

    // sizeGate(함수 50줄)는 본문 있는 선언만 잰다 — 「값을 담을 뿐인 프로퍼티 초기화식」은
    // 그 축이 아니다(size-policy.properties). buildList{} 람다 하나로 몰지 않고 테이블별
    // 평범한 리스트 초기화 + `+` 연결로 나눈 것은 그 예외를 그대로 쓴 것이다(함수 아님).
    private val rawObservationColumns =
        listOf(
            ColumnSpec("raw_observation", "observation_key", "text", false),
            ColumnSpec("raw_observation", "source_endpoint", "text", false),
            // F-7 운영자 결정 — 원문 전체는 TEXT(재직렬화 없이), 등재분 투영은 별도 JSONB.
            ColumnSpec("raw_observation", "payload", "text", false),
            ColumnSpec("raw_observation", "payload_fields", "jsonb", false),
            ColumnSpec("raw_observation", "observed_at", "timestamp with time zone", false),
            ColumnSpec("raw_observation", "release_sha", "text", false),
            ColumnSpec("raw_observation", "inserted_at", "timestamp with time zone", false),
        )

    private val provenanceAuthorityColumns =
        listOf(
            ColumnSpec("provenance_authority", "provenance", "text", false),
            ColumnSpec("provenance_authority", "authoritative", "boolean", false),
        )

    private val noticeColumns =
        listOf(
            ColumnSpec("notice", "notice_number", "text", false),
            ColumnSpec("notice", "notice_round", "text", false),
            ColumnSpec("notice", "status", "text", false),
            ColumnSpec("notice", "business_category_code", "text", true),
            ColumnSpec("notice", "business_category_label", "text", true),
            ColumnSpec("notice", "base_amount_won", "numeric", true),
            ColumnSpec("notice", "base_amount_currency", "text", true),
            ColumnSpec("notice", "base_amount_vat", "text", true),
            ColumnSpec("notice", "base_amount_provenance", "text", true),
            ColumnSpec("notice", "base_amount_provenance_detail", "text", true),
            ColumnSpec("notice", "estimated_amount_won", "numeric", true),
            ColumnSpec("notice", "estimated_amount_currency", "text", true),
            ColumnSpec("notice", "estimated_amount_vat", "text", true),
            ColumnSpec("notice", "estimated_amount_provenance", "text", true),
            ColumnSpec("notice", "estimated_amount_provenance_detail", "text", true),
            ColumnSpec("notice", "estimated_amount_source_key", "text", true),
            ColumnSpec("notice", "allocated_budget_won", "numeric", true),
            ColumnSpec("notice", "allocated_budget_provenance", "text", true),
            ColumnSpec("notice", "allocated_budget_provenance_detail", "text", true),
            ColumnSpec("notice", "floor_rate_fraction", "numeric", true),
            ColumnSpec("notice", "floor_rate_origin_kind", "text", true),
            ColumnSpec("notice", "floor_rate_origin_detail", "text", true),
            ColumnSpec("notice", "deadline_at", "timestamp with time zone", true),
            ColumnSpec("notice", "revision", "bigint", false),
            ColumnSpec("notice", "observation_key", "text", false),
            ColumnSpec("notice", "created_at", "timestamp with time zone", false),
            ColumnSpec("notice", "updated_at", "timestamp with time zone", false),
        )

    private val noticeAuditColumns =
        listOf(
            ColumnSpec("notice_audit", "id", "bigint", false),
            ColumnSpec("notice_audit", "notice_number", "text", false),
            ColumnSpec("notice_audit", "notice_round", "text", false),
            ColumnSpec("notice_audit", "revision", "bigint", false),
            ColumnSpec("notice_audit", "observation_key", "text", false),
            ColumnSpec("notice_audit", "reason", "text", false),
            ColumnSpec("notice_audit", "previous_row", "jsonb", false),
            ColumnSpec("notice_audit", "recorded_at", "timestamp with time zone", false),
        )

    private val rejectedWriteColumns =
        listOf(
            ColumnSpec("rejected_write", "id", "bigint", false),
            ColumnSpec("rejected_write", "notice_number", "text", false),
            ColumnSpec("rejected_write", "notice_round", "text", false),
            ColumnSpec("rejected_write", "observation_key", "text", false),
            ColumnSpec("rejected_write", "reason", "text", false),
            ColumnSpec("rejected_write", "attempted_value", "jsonb", true),
            ColumnSpec("rejected_write", "recorded_at", "timestamp with time zone", false),
        )

    private val openingResultColumns =
        listOf(
            ColumnSpec("opening_result", "notice_number", "text", false),
            ColumnSpec("opening_result", "notice_round", "text", false),
            ColumnSpec("opening_result", "winning_rate_fraction", "numeric", true),
            ColumnSpec("opening_result", "derived_base_amount_won", "numeric", true),
            ColumnSpec("opening_result", "derived_base_amount_currency", "text", true),
            ColumnSpec("opening_result", "derived_base_amount_vat", "text", true),
            ColumnSpec("opening_result", "observed_at", "timestamp with time zone", false),
            ColumnSpec("opening_result", "revision", "bigint", false),
            ColumnSpec("opening_result", "observation_key", "text", false),
            ColumnSpec("opening_result", "created_at", "timestamp with time zone", false),
            ColumnSpec("opening_result", "updated_at", "timestamp with time zone", false),
            // M3/3E — 층 C fact 슬롯(추가만, 스키마 스냅샷 래칫 예외 운영자 승인 2026-09-08).
            ColumnSpec("opening_result", "final_award_amount_won", "numeric", true),
            ColumnSpec("opening_result", "final_award_amount_currency", "text", true),
            ColumnSpec("opening_result", "final_award_company_name", "text", true),
            ColumnSpec("opening_result", "participant_count", "integer", true),
            ColumnSpec("opening_result", "progress_division", "text", true),
            ColumnSpec("opening_result", "planned_price_won", "numeric", true),
            ColumnSpec("opening_result", "planned_price_currency", "text", true),
            ColumnSpec("opening_result", "opening_base_amount_won", "numeric", true),
            ColumnSpec("opening_result", "opening_base_amount_currency", "text", true),
            ColumnSpec("opening_result", "opening_base_amount_vat", "text", true),
            ColumnSpec("opening_result", "total_reserve_price_candidate_count", "integer", true),
            ColumnSpec("opening_result", "actual_opening_at", "timestamp with time zone", true),
            // verifier r1 H-1 뒤(V5) — provenance 왕복(추가만).
            ColumnSpec("opening_result", "final_award_amount_provenance", "text", true),
            ColumnSpec("opening_result", "final_award_amount_provenance_detail", "text", true),
            ColumnSpec("opening_result", "planned_price_provenance", "text", true),
            ColumnSpec("opening_result", "planned_price_provenance_detail", "text", true),
            ColumnSpec("opening_result", "opening_base_amount_provenance", "text", true),
            ColumnSpec("opening_result", "opening_base_amount_provenance_detail", "text", true),
            // M3/3F — 개찰완료 축 부모 슬롯(추가만, 스키마 스냅샷 래칫 예외 D-3F-6).
            ColumnSpec("opening_result", "opening_rank_one_kind", "text", true),
            ColumnSpec("opening_result", "opening_rank_one_duplicate_count", "integer", true),
            ColumnSpec("opening_result", "opening_rank_one_bidder_name", "text", true),
            ColumnSpec("opening_result", "opening_rank_one_bid_amount_won", "numeric", true),
            ColumnSpec("opening_result", "opening_rank_one_bid_amount_currency", "text", true),
            ColumnSpec("opening_result", "opening_rank_one_bid_rate_fraction", "numeric", true),
            ColumnSpec("opening_result", "opening_rank_one_price_evaluation_score", "text", true),
            ColumnSpec("opening_result", "opening_rank_one_technical_evaluation_score", "text", true),
            ColumnSpec("opening_result", "opening_rank_one_technical_evaluation_nature_score", "text", true),
            ColumnSpec("opening_result", "opening_rank_one_total_evaluation_amount_score", "text", true),
            ColumnSpec("opening_result", "draw_numbers_kind", "text", true),
            ColumnSpec("opening_result", "draw_numbers", "ARRAY", true),
        )

    // M3/3E — 층 B 자식 표(D-3E-2 (a), 스키마 스냅샷 래칫 예외 운영자 승인 2026-09-08).
    private val openingReservePriceColumns =
        listOf(
            ColumnSpec("opening_reserve_price", "notice_number", "text", false),
            ColumnSpec("opening_reserve_price", "notice_round", "text", false),
            ColumnSpec("opening_reserve_price", "reserve_price_sequence", "text", false),
            ColumnSpec("opening_reserve_price", "base_reserve_price_won", "numeric", true),
            ColumnSpec("opening_reserve_price", "base_reserve_price_currency", "text", true),
            ColumnSpec("opening_reserve_price", "is_drawn", "boolean", true),
            ColumnSpec("opening_reserve_price", "draw_count", "integer", true),
            ColumnSpec("opening_reserve_price", "observed_at", "timestamp with time zone", false),
            ColumnSpec("opening_reserve_price", "revision", "bigint", false),
            ColumnSpec("opening_reserve_price", "observation_key", "text", false),
            ColumnSpec("opening_reserve_price", "created_at", "timestamp with time zone", false),
            ColumnSpec("opening_reserve_price", "updated_at", "timestamp with time zone", false),
        )

    private val qualificationTextColumns =
        listOf(
            ColumnSpec("qualification_text", "notice_number", "text", false),
            ColumnSpec("qualification_text", "notice_round", "text", false),
            ColumnSpec("qualification_text", "raw_text", "text", false),
            ColumnSpec("qualification_text", "observed_at", "timestamp with time zone", false),
            ColumnSpec("qualification_text", "revision", "bigint", false),
            ColumnSpec("qualification_text", "observation_key", "text", false),
            ColumnSpec("qualification_text", "created_at", "timestamp with time zone", false),
            ColumnSpec("qualification_text", "updated_at", "timestamp with time zone", false),
        )

    private val collectionRunColumns =
        listOf(
            ColumnSpec("collection_run", "id", "bigint", false),
            ColumnSpec("collection_run", "reference_date", "date", false),
            ColumnSpec("collection_run", "source_endpoint", "text", false),
            ColumnSpec("collection_run", "started_at", "timestamp with time zone", false),
            ColumnSpec("collection_run", "finished_at", "timestamp with time zone", false),
            ColumnSpec("collection_run", "received", "integer", false),
            ColumnSpec("collection_run", "normalized", "integer", false),
            ColumnSpec("collection_run", "duplicate", "integer", false),
            ColumnSpec("collection_run", "dropped", "integer", false),
            ColumnSpec("collection_run", "drop_reasons", "jsonb", false),
            ColumnSpec("collection_run", "source_total", "integer", true),
            ColumnSpec("collection_run", "pages_fetched", "integer", false),
            ColumnSpec("collection_run", "truncated", "boolean", false),
            ColumnSpec("collection_run", "unknown_fields", "integer", false),
            ColumnSpec("collection_run", "truncation_cause", "text", true),
            ColumnSpec("collection_run", "quota_exceeded", "integer", false),
            ColumnSpec("collection_run", "backoff_skipped", "integer", false),
            ColumnSpec("collection_run", "inserted_at", "timestamp with time zone", false),
        )

    private val expectedColumns =
        rawObservationColumns + provenanceAuthorityColumns + noticeColumns + noticeAuditColumns +
            rejectedWriteColumns + openingResultColumns + qualificationTextColumns + collectionRunColumns +
            openingReservePriceColumns

    @Test
    fun `축2·3·4 컬럼 존재·타입·NOT NULL 이 기대와 같다`() {
        val actual = mutableListOf<ColumnSpec>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT table_name, column_name, data_type, is_nullable FROM information_schema.columns " +
                            "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                    ).use { rs ->
                        while (rs.next()) {
                            actual +=
                                ColumnSpec(
                                    rs.getString("table_name"),
                                    rs.getString("column_name"),
                                    rs.getString("data_type"),
                                    rs.getString("is_nullable") == "YES",
                                )
                        }
                    }
            }
        }

        actual shouldContainExactlyInAnyOrder expectedColumns
    }

    /** D-3D-5(정수 원) — 금액 `_won` 컬럼은 정확히 `NUMERIC(20,0)`. verifier B②(20,4로 확대)를 여기서 잡는다. */
    @Test
    fun `축3 부가 — 금액 won 컬럼은 정확히 NUMERIC(20,0)이다`() {
        val wonColumns =
            listOf(
                "notice.base_amount_won",
                "notice.estimated_amount_won",
                "notice.allocated_budget_won",
                "opening_result.derived_base_amount_won",
                // M3/3E — 층 B·C 신규 won 컬럼(추가만).
                "opening_result.final_award_amount_won",
                "opening_result.planned_price_won",
                "opening_result.opening_base_amount_won",
                "opening_reserve_price.base_reserve_price_won",
                // M3/3F — 개찰완료 축 부모 슬롯(추가만).
                "opening_result.opening_rank_one_bid_amount_won",
            )
        for (qualified in wonColumns) {
            val (table, column) = qualified.split(".")
            val (precision, scale) = numericPrecisionScale(table, column)
            precision shouldBe 20
            scale shouldBe 0
        }
    }

    /** floor_rate_fraction·winning_rate_fraction은 자리수 제약 없는 `NUMERIC`(재선언 없음). */
    @Test
    fun `축3 부가 — rate fraction 컬럼은 정밀도·스케일을 재선언하지 않은 NUMERIC이다`() {
        val fractionColumns =
            listOf(
                "notice.floor_rate_fraction",
                "opening_result.winning_rate_fraction",
                // M3/3F — 개찰완료 축 부모 슬롯(추가만).
                "opening_result.opening_rank_one_bid_rate_fraction",
            )
        for (qualified in fractionColumns) {
            val (table, column) = qualified.split(".")
            val (precision, scale) = numericPrecisionScale(table, column)
            precision shouldBe null
            scale shouldBe null
        }
    }

    private fun numericPrecisionScale(
        table: String,
        column: String,
    ): Pair<Int?, Int?> =
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "SELECT numeric_precision, numeric_scale FROM information_schema.columns " +
                        "WHERE table_schema='public' AND table_name = ? AND column_name = ?",
                ).use { statement ->
                    statement.setString(1, table)
                    statement.setString(2, column)
                    statement.executeQuery().use { rs ->
                        rs.next()
                        val precision = rs.getInt("numeric_precision").takeUnless { rs.wasNull() }
                        val scale = rs.getInt("numeric_scale").takeUnless { rs.wasNull() }
                        precision to scale
                    }
                }
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
