package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 축 7(트리거) — `CleanMigrationTest`(sizeGate 500줄, v2-지침서.md §5)에서 분리한 파일.
 * D-3D-6 여덟 축 중 트리거 하나만 다룬다(다른 일곱 축은 `CleanMigrationTest`).
 */
class CleanMigrationTriggerTest : PersistenceTestSupport() {
    /**
     * (표, 트리거 이름) 쌍 — M6/6B-1 verifier r1 MEDIUM-1(b) 시정. PostgreSQL 은 트리거
     * 이름을 표마다 독립으로 유일화한다 — 예전 `Set<String>`(전역 DISTINCT 이름)은 신설
     * 표가 **기존 표의 트리거 이름을 재사용**하면(예: `edit_session` 에 `guard_notice_status`
     * 라는 이름의 새 트리거) 이름 집합이 안 변해 놓쳤다(D-6B1-3 이 금지한 「session_version
     * 을 건드리는 트리거」가 바로 이 형태로 숨을 수 있었다).
     */
    private data class TriggerSpec(
        val table: String,
        val name: String,
    )

    // verifier r1 뒤 F-5로 3개 신설(opening_result 2·qualification_text 1), verifier r2
    // N-2 뒤 status 축 1개 추가.
    private val expectedTriggers =
        setOf(
            TriggerSpec("notice", "guard_notice_base_amount"),
            TriggerSpec("notice", "guard_notice_estimated_amount"),
            TriggerSpec("notice", "guard_notice_allocated_budget"),
            TriggerSpec("notice", "guard_notice_floor_rate"),
            TriggerSpec("notice", "guard_notice_status"),
            TriggerSpec("notice", "notice_revision_bump_trigger"),
            TriggerSpec("notice", "notice_audit_insert_trigger"),
            TriggerSpec("raw_observation", "raw_observation_append_only"),
            TriggerSpec("notice_audit", "notice_audit_append_only"),
            TriggerSpec("opening_result", "guard_opening_result_winning_rate"),
            TriggerSpec("opening_result", "guard_opening_result_derived_base_amount"),
            TriggerSpec("qualification_text", "guard_qualification_text_raw_text"),
            // M3/3E — 층 C 부모 fact 슬롯 가드(추가만, 스키마 스냅샷 래칫 예외 운영자 승인 2026-09-08).
            TriggerSpec("opening_result", "guard_opening_result_final_award_amount"),
            TriggerSpec("opening_result", "guard_opening_result_final_award_company_name"),
            TriggerSpec("opening_result", "guard_opening_result_participant_count"),
            TriggerSpec("opening_result", "guard_opening_result_progress_division"),
            TriggerSpec("opening_result", "guard_opening_result_planned_price"),
            TriggerSpec("opening_result", "guard_opening_result_opening_base_amount"),
            TriggerSpec("opening_result", "guard_opening_result_total_reserve_price_candidate_count"),
            TriggerSpec("opening_result", "guard_opening_result_actual_opening_at"),
            // M3/3E — 층 B 자식 표 가드.
            TriggerSpec("opening_reserve_price", "guard_opening_reserve_price_base_reserve_price"),
            TriggerSpec("opening_reserve_price", "guard_opening_reserve_price_is_drawn"),
            TriggerSpec("opening_reserve_price", "guard_opening_reserve_price_draw_count"),
            // M3/3F — 개찰완료 축 부모 슬롯 가드(추가만, 스키마 스냅샷 래칫 예외 D-3F-6).
            TriggerSpec("opening_result", "guard_opening_result_opening_rank_one"),
            TriggerSpec("opening_result", "guard_opening_result_draw_numbers"),
            // M6/6B-1(D-6B1-8, 등재) — `edit_session`(V8__edit_session.sql)은 트리거가
            // 0개다. session_version 은 애플리케이션이 싣고 DB 는 전제조건으로만 검사한다
            // (D-6B1-3) — 트리거로 값을 정하면 그 전제조건 자체가 무의미해진다. `edit_session`
            // 을 대상으로 하는 행이 이 목록에 하나도 없다는 것 자체가 그 부재의 등재다.
        )

    @Test
    fun `축7 트리거 목록이 (표, 이름) 쌍으로 기대와 같다`() {
        val actual = mutableSetOf<TriggerSpec>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT DISTINCT event_object_table, trigger_name FROM information_schema.triggers " +
                            "WHERE trigger_schema = 'public'",
                    ).use { rs ->
                        while (rs.next()) {
                            actual += TriggerSpec(rs.getString("event_object_table"), rs.getString("trigger_name"))
                        }
                    }
            }
        }
        actual shouldContainExactlyInAnyOrder expectedTriggers
    }

    // P-1(verifier r3, medium) — 동반 컬럼 목록이 트리거 정의(함수 인자) 한 자리에 있다.
    // 값 컬럼마다 그 축의 라벨 짝(통화·과세·provenance detail·origin 등) 전부가 여기 있어야
    // 한다 — 새 라벨 컬럼을 추가하고 이 목록에 넣는 것을 잊으면 이 test가 잡는다.
    private val expectedGuardArguments =
        mapOf(
            "guard_notice_base_amount" to
                listOf(
                    "base_amount_won",
                    "base_amount_provenance",
                    "base_amount_currency",
                    "base_amount_vat",
                    "base_amount_provenance_detail",
                ),
            "guard_notice_estimated_amount" to
                listOf(
                    "estimated_amount_won",
                    "estimated_amount_provenance",
                    "estimated_amount_currency",
                    "estimated_amount_vat",
                    "estimated_amount_provenance_detail",
                    "estimated_amount_source_key",
                ),
            "guard_notice_allocated_budget" to
                listOf("allocated_budget_won", "allocated_budget_provenance", "allocated_budget_provenance_detail"),
            "guard_notice_floor_rate" to
                listOf("floor_rate_fraction", "floor_rate_origin_kind", "floor_rate_origin_detail"),
            "guard_notice_status" to listOf("status"),
            // verifier r2 N-2 뒤 — V4(흡수본)가 넓힌 opening_result 가드 인자도 이 게이트가 본다.
            "guard_opening_result_final_award_amount" to
                listOf(
                    "final_award_amount_won",
                    "final_award_amount_currency",
                    "final_award_amount_provenance",
                    "final_award_amount_provenance_detail",
                ),
            "guard_opening_result_final_award_company_name" to listOf("final_award_company_name"),
            "guard_opening_result_participant_count" to listOf("participant_count"),
            "guard_opening_result_progress_division" to listOf("progress_division"),
            "guard_opening_result_planned_price" to
                listOf(
                    "planned_price_won",
                    "planned_price_currency",
                    "planned_price_provenance",
                    "planned_price_provenance_detail",
                ),
            "guard_opening_result_opening_base_amount" to
                listOf(
                    "opening_base_amount_won",
                    "opening_base_amount_currency",
                    "opening_base_amount_vat",
                    "opening_base_amount_provenance",
                    "opening_base_amount_provenance_detail",
                ),
            "guard_opening_result_total_reserve_price_candidate_count" to
                listOf("total_reserve_price_candidate_count"),
            "guard_opening_result_actual_opening_at" to listOf("actual_opening_at"),
            // M3/3F — 개찰완료 축 부모 슬롯(추가만). verifier r1 F-2 뒤 축별 관측 시각도 이
            // 트리거 인자에 실린다(F-1 뒤 축 단위 UPSERT 와 같은 자리, Sql.kt).
            "guard_opening_result_opening_rank_one" to
                listOf(
                    "opening_rank_one_kind",
                    "opening_rank_one_duplicate_count",
                    "opening_rank_one_bidder_name",
                    "opening_rank_one_bid_amount_won",
                    "opening_rank_one_bid_amount_currency",
                    "opening_rank_one_bid_rate_fraction",
                    "opening_rank_one_observed_at",
                ),
            "guard_opening_result_draw_numbers" to
                listOf(
                    "draw_numbers_kind",
                    "draw_numbers",
                    "draw_numbers_observed_at",
                    "draw_numbers_valid_range_max",
                ),
        )

    @Test
    fun `축7 부가 — 가드 트리거 인자(동반 컬럼)가 값 컬럼마다 라벨 짝을 전부 담는다`() {
        val actual = mutableMapOf<String, List<String>>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT tgname, pg_get_triggerdef(oid) AS def FROM pg_trigger " +
                            "WHERE tgrelid = ANY(ARRAY['notice'::regclass, 'opening_result'::regclass]) " +
                            "AND NOT tgisinternal " +
                            "AND tgname IN (${expectedGuardArguments.keys.joinToString(",") { "'$it'" }})",
                    ).use { rs ->
                        while (rs.next()) {
                            val name = rs.getString("tgname")
                            val def = rs.getString("def")
                            val args = Regex("'([^']*)'").findAll(def).map { it.groupValues[1] }.toList()
                            actual[name] = args
                        }
                    }
            }
        }
        actual shouldBe expectedGuardArguments
    }
}
