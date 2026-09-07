package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 축 7(트리거) — `CleanMigrationTest`(sizeGate 500줄, v2-지침서.md §5)에서 분리한 파일.
 * D-3D-6 여덟 축 중 트리거 하나만 다룬다(다른 일곱 축은 `CleanMigrationTest`).
 */
class CleanMigrationTriggerTest : PersistenceTestSupport() {
    // verifier r1 뒤 F-5로 3개 신설(opening_result 2·qualification_text 1), verifier r2
    // N-2 뒤 status 축 1개 추가.
    private val expectedTriggers =
        setOf(
            "guard_notice_base_amount",
            "guard_notice_estimated_amount",
            "guard_notice_allocated_budget",
            "guard_notice_floor_rate",
            "guard_notice_status",
            "notice_revision_bump_trigger",
            "notice_audit_insert_trigger",
            "raw_observation_append_only",
            "notice_audit_append_only",
            "guard_opening_result_winning_rate",
            "guard_opening_result_derived_base_amount",
            "guard_qualification_text_raw_text",
        )

    @Test
    fun `축7 트리거 목록이 기대와 같다`() {
        val actual =
            queryStrings(
                "SELECT DISTINCT trigger_name FROM information_schema.triggers WHERE trigger_schema = 'public'",
            )
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
        )

    @Test
    fun `축7 부가 — 가드 트리거 인자(동반 컬럼)가 값 컬럼마다 라벨 짝을 전부 담는다`() {
        val actual = mutableMapOf<String, List<String>>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT tgname, pg_get_triggerdef(oid) AS def FROM pg_trigger " +
                            "WHERE tgrelid = 'notice'::regclass AND NOT tgisinternal " +
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
