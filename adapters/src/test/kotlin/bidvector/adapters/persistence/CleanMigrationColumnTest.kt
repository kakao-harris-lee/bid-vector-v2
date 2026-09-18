package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 축 2·3·4(컬럼 존재·타입·NOT NULL) — `CleanMigrationTest`(sizeGate 500줄, v2-지침서.md
 * §5)에서 분리한 파일 — `CleanMigrationTriggerTest`(축 7)·`CleanMigrationCheckTest`(축 8)와
 * 같은 이유·같은 전례다(M4/4C-2에서 재분리 — outbox·inbox 컬럼이 더해지며 원판이 500줄을
 * 넘었다). D-3D-6 여덟 축 중 컬럼 셋만 다룬다(다른 다섯 축은 `CleanMigrationTest`).
 */
class CleanMigrationColumnTest : PersistenceTestSupport() {
    /**
     * `hasDefault` — M6/6B-1 verifier r1 MEDIUM-1(a) 시정. 이름·타입·NOT NULL 삼중항만으로는
     * `session_version INTEGER NOT NULL DEFAULT 0`(D-6B1-3 이 금지한 바로 그 것)이 초록으로
     * 지났다. 정확한 DEFAULT 식 문자열이 아니라 **있고 없음**만 잰다 — 식 문자열은 Postgres
     * 버전·캐스트 표기가 갈릴 수 있어 이름 재작성만으로 오탐이 날 수 있다(예: `1` vs
     * `1::bigint`). 기존 행은 전부 `hasDefault` 를 안 적어 Kotlin 기본값 `false` 를 쓴다 —
     * 실제로 DEFAULT 가 있는 21개 컬럼만 `true` 로 명시했다(추가만, 기존 표 기대치는 그대로).
     */
    private data class ColumnSpec(
        val table: String,
        val column: String,
        val dataType: String,
        val nullable: Boolean,
        val hasDefault: Boolean = false,
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
            ColumnSpec("raw_observation", "inserted_at", "timestamp with time zone", false, true),
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
            // M3/3H-1 D-3H-4 — 발주기관 넷(추가만, V7). provenance 컬럼 없음(D-3H-3).
            ColumnSpec("notice", "demand_agency_code", "text", true),
            ColumnSpec("notice", "demand_agency_name", "text", true),
            ColumnSpec("notice", "notice_agency_code", "text", true),
            ColumnSpec("notice", "notice_agency_name", "text", true),
            ColumnSpec("notice", "deadline_at", "timestamp with time zone", true),
            ColumnSpec("notice", "revision", "bigint", false, true),
            ColumnSpec("notice", "observation_key", "text", false),
            ColumnSpec("notice", "created_at", "timestamp with time zone", false, true),
            ColumnSpec("notice", "updated_at", "timestamp with time zone", false, true),
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
            ColumnSpec("notice_audit", "recorded_at", "timestamp with time zone", false, true),
        )

    private val rejectedWriteColumns =
        listOf(
            ColumnSpec("rejected_write", "id", "bigint", false),
            ColumnSpec("rejected_write", "notice_number", "text", false),
            ColumnSpec("rejected_write", "notice_round", "text", false),
            ColumnSpec("rejected_write", "observation_key", "text", false),
            ColumnSpec("rejected_write", "reason", "text", false),
            ColumnSpec("rejected_write", "attempted_value", "jsonb", true),
            ColumnSpec("rejected_write", "recorded_at", "timestamp with time zone", false, true),
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
            ColumnSpec("opening_result", "revision", "bigint", false, true),
            ColumnSpec("opening_result", "observation_key", "text", false),
            ColumnSpec("opening_result", "created_at", "timestamp with time zone", false, true),
            ColumnSpec("opening_result", "updated_at", "timestamp with time zone", false, true),
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
            // verifier r1 F-2 뒤 — 축별 관측 시각(3E OpeningReservePriceRow.observedAt 과 같은 자리).
            ColumnSpec("opening_result", "opening_rank_one_observed_at", "timestamp with time zone", true),
            ColumnSpec("opening_result", "draw_numbers_kind", "text", true),
            ColumnSpec("opening_result", "draw_numbers", "ARRAY", true),
            ColumnSpec("opening_result", "draw_numbers_observed_at", "timestamp with time zone", true),
            ColumnSpec("opening_result", "draw_numbers_valid_range_max", "integer", true),
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
            ColumnSpec("opening_reserve_price", "revision", "bigint", false, true),
            ColumnSpec("opening_reserve_price", "observation_key", "text", false),
            ColumnSpec("opening_reserve_price", "created_at", "timestamp with time zone", false, true),
            ColumnSpec("opening_reserve_price", "updated_at", "timestamp with time zone", false, true),
        )

    private val qualificationTextColumns =
        listOf(
            ColumnSpec("qualification_text", "notice_number", "text", false),
            ColumnSpec("qualification_text", "notice_round", "text", false),
            ColumnSpec("qualification_text", "raw_text", "text", false),
            ColumnSpec("qualification_text", "observed_at", "timestamp with time zone", false),
            ColumnSpec("qualification_text", "revision", "bigint", false, true),
            ColumnSpec("qualification_text", "observation_key", "text", false),
            ColumnSpec("qualification_text", "created_at", "timestamp with time zone", false, true),
            ColumnSpec("qualification_text", "updated_at", "timestamp with time zone", false, true),
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
            ColumnSpec("collection_run", "quota_exceeded", "integer", false, true),
            ColumnSpec("collection_run", "backoff_skipped", "integer", false, true),
            ColumnSpec("collection_run", "inserted_at", "timestamp with time zone", false, true),
        )

    // M4/4C-2 — outbox·inbox(추가만, D-4C2-2). 어휘·필드는 V6__outbox_inbox.sql 그대로.
    private val outboxColumns =
        listOf(
            ColumnSpec("outbox", "entry_id", "text", false),
            ColumnSpec("outbox", "event_id", "text", false),
            ColumnSpec("outbox", "aggregate_id", "text", false),
            ColumnSpec("outbox", "aggregate_version", "bigint", false),
            ColumnSpec("outbox", "occurred_at", "timestamp with time zone", false),
            ColumnSpec("outbox", "correlation_id", "text", false),
            ColumnSpec("outbox", "causation_id", "text", true),
            ColumnSpec("outbox", "idempotency_key", "text", false),
            ColumnSpec("outbox", "actor_kind", "text", true),
            ColumnSpec("outbox", "actor_detail", "text", true),
            ColumnSpec("outbox", "payload_type", "text", false),
            ColumnSpec("outbox", "payload", "text", false),
            ColumnSpec("outbox", "state", "text", false),
            ColumnSpec("outbox", "inserted_at", "timestamp with time zone", false, true),
        )

    private val inboxColumns =
        listOf(
            ColumnSpec("inbox", "idempotency_key", "text", false),
            ColumnSpec("inbox", "processed_at", "timestamp with time zone", false, true),
        )

    // M6/6B-1 — V8__edit_session.sql(추가만, D-6B1-8). state_payload·last_command 는
    // JSON 텍스트(nullable — EXPIRED 는 payload 없음, 세션 시작 직후는 command 없음).
    private val editSessionColumns =
        listOf(
            ColumnSpec("edit_session", "id", "text", false),
            ColumnSpec("edit_session", "operator_id", "text", false),
            ColumnSpec("edit_session", "state", "text", false),
            ColumnSpec("edit_session", "state_payload", "text", true),
            ColumnSpec("edit_session", "expires_at", "timestamp with time zone", false),
            ColumnSpec("edit_session", "session_version", "integer", false),
            ColumnSpec("edit_session", "last_command", "text", true),
            ColumnSpec("edit_session", "created_at", "timestamp with time zone", false, true),
        )

    // M6/6F-1 — 전략 영속(추가만, D-6F1-1). 두 표가 감시·임계·상한 열 형태를 공유한다
    // (operator_strategy = 싱글턴 현재 값, operator_strategy_revision = 개정 이력). 감시 규칙
    // 다섯 축은 `StrategyDraft`가 항상 `List<String>`(빈 목록이 「규칙 없음」)이라 NOT NULL —
    // 나머지(예산·점수·상한)만 진짜 nullable이다.
    private val operatorStrategyNotNullArrayColumns =
        listOf(
            "focus_categories" to "ARRAY",
            "focus_region_terms" to "ARRAY",
            "exclude_region_terms" to "ARRAY",
            "required_keyword_terms" to "ARRAY",
            "exclude_keyword_terms" to "ARRAY",
        )

    private val operatorStrategyNullableColumns =
        listOf(
            "min_budget_won" to "numeric",
            "min_budget_currency" to "text",
            "min_budget_vat" to "text",
            "min_budget_provenance" to "text",
            "min_budget_provenance_detail" to "text",
            "max_budget_won" to "numeric",
            "max_budget_currency" to "text",
            "max_budget_vat" to "text",
            "max_budget_provenance" to "text",
            "max_budget_provenance_detail" to "text",
            "minimum_match_score" to "numeric",
            "minimum_probability_score" to "numeric",
            "bid_now_threshold" to "numeric",
            "review_threshold" to "numeric",
            "candidate_limit" to "integer",
        )

    private fun operatorStrategySharedColumns(table: String): List<ColumnSpec> =
        operatorStrategyNotNullArrayColumns.map { (name, type) -> ColumnSpec(table, name, type, false) } +
            operatorStrategyNullableColumns.map { (name, type) -> ColumnSpec(table, name, type, true) }

    private val operatorStrategyColumns =
        listOf(
            ColumnSpec("operator_strategy", "id", "smallint", false),
            ColumnSpec("operator_strategy", "revision", "integer", false),
            ColumnSpec("operator_strategy", "updated_at", "timestamp with time zone", false, true),
        ) + operatorStrategySharedColumns("operator_strategy")

    private val operatorStrategyRevisionColumns =
        listOf(
            ColumnSpec("operator_strategy_revision", "revision", "integer", false),
            ColumnSpec("operator_strategy_revision", "applied_at", "timestamp with time zone", false, true),
        ) + operatorStrategySharedColumns("operator_strategy_revision")

    // M6/6F-5-a — 자격 요건 영속(추가만, D-6F5-4). 헤더(notice_requirement)는 공고당 한 행,
    // 행(notice_requirement_row)은 `RequirementRow`(Parsed·Unparsable) 왕복 — PARSED만
    // group_no·source_field·license_names를 채운다(nullable, UNPARSABLE은 항상 NULL).
    private val noticeRequirementColumns =
        listOf(
            ColumnSpec("notice_requirement", "notice_number", "text", false),
            ColumnSpec("notice_requirement", "notice_round", "text", false),
            ColumnSpec("notice_requirement", "status", "text", false),
            ColumnSpec("notice_requirement", "created_at", "timestamp with time zone", false, true),
            ColumnSpec("notice_requirement", "updated_at", "timestamp with time zone", false, true),
        )

    private val noticeRequirementRowColumns =
        listOf(
            ColumnSpec("notice_requirement_row", "notice_number", "text", false),
            ColumnSpec("notice_requirement_row", "notice_round", "text", false),
            ColumnSpec("notice_requirement_row", "serial_no", "text", false),
            ColumnSpec("notice_requirement_row", "kind", "text", false),
            ColumnSpec("notice_requirement_row", "group_no", "text", true),
            ColumnSpec("notice_requirement_row", "source_field", "text", true),
            ColumnSpec("notice_requirement_row", "license_names", "ARRAY", true),
            ColumnSpec("notice_requirement_row", "created_at", "timestamp with time zone", false, true),
        )

    private val expectedColumns =
        rawObservationColumns + provenanceAuthorityColumns + noticeColumns + noticeAuditColumns +
            rejectedWriteColumns + openingResultColumns + qualificationTextColumns + collectionRunColumns +
            openingReservePriceColumns + outboxColumns + inboxColumns + editSessionColumns +
            operatorStrategyColumns + operatorStrategyRevisionColumns +
            noticeRequirementColumns + noticeRequirementRowColumns

    @Test
    fun `축2·3·4 컬럼 존재·타입·NOT NULL 이 기대와 같다`() {
        val actual = mutableListOf<ColumnSpec>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT table_name, column_name, data_type, is_nullable, column_default " +
                            "FROM information_schema.columns " +
                            "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'",
                    ).use { rs ->
                        while (rs.next()) {
                            actual +=
                                ColumnSpec(
                                    rs.getString("table_name"),
                                    rs.getString("column_name"),
                                    rs.getString("data_type"),
                                    rs.getString("is_nullable") == "YES",
                                    rs.getString("column_default") != null,
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
                // M6/6F-1 — 전략 예산 한계 둘(추가만, D-6F1-1).
                "operator_strategy.min_budget_won",
                "operator_strategy.max_budget_won",
                "operator_strategy_revision.min_budget_won",
                "operator_strategy_revision.max_budget_won",
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
}
