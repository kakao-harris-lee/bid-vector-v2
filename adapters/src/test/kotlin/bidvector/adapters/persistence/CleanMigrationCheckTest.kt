package bidvector.adapters.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * S-5 축 8(CHECK) — `CleanMigrationTest`(sizeGate 500줄, v2-지침서.md §5)에서 분리한 파일 —
 * `CleanMigrationTriggerTest`(축 7)와 같은 이유·같은 전례다. D-3D-6 여덟 축 중 CHECK 하나만
 * 다룬다(다른 일곱 축은 `CleanMigrationTest`).
 *
 * **M3/3E — 층 B·C 신규 CHECK(추가만, 스키마 스냅샷 래칫 예외 운영자 승인 2026-09-08)** —
 * `opening_result`가 5(3E 착수 시점 V1)에서 11·14(provenance 페어 3, verifier r1 H-1 뒤)로,
 * `opening_reserve_price`(신규 자식 표)가 6으로 는다. **verifier r1 L-6** — 이 문단의
 * 「14」는 3E 가 개발 중 거쳐 간 초안 파일 이름 `V5`(→흡수 뒤 최종적으로 `V4` 로 push 됨)
 * 시절의 셈이다 — 지금 저장소에 파일 `V5`는 없다(3E 는 `V4` 하나만 남겼다). 아래 3F 문단의
 * `V5`는 **그 흡수와 무관한, 3F 자신의 신규 마이그레이션 파일**(`V5__opening_complete_axis
 * .sql`)이다 — 이름만 같고 다른 파일을 가리킨다.
 *
 * **M3/3F — 개찰완료 축 부모 슬롯 신규 CHECK(추가만, 스키마 스냅샷 래칫 예외 D-3F-6)** —
 * `V5__opening_complete_axis.sql`이 `opening_result`의 CHECK 를 14 에서 24 로 늘린다
 * (opening_rank_one·draw_numbers 두 축의 enum 둘·값 페어 셋·최소값 하나 + verifier r1
 * F-2 뒤 관측 시각 페어 둘 + F-3 뒤 `OUT_OF_RANGE`↔총예가건수 요구 하나).
 */
class CleanMigrationCheckTest : PersistenceTestSupport() {
    private val expectedCheckCountByTable =
        mapOf(
            "collection_run" to 13,
            "notice" to 12,
            "notice_audit" to 1,
            "opening_result" to 24,
            "provenance_authority" to 1,
            "qualification_text" to 3,
            // F-7 운영자 결정 — payload 가 TEXT 로 바뀌며 `payload <> ''` CHECK 가 하나 늘었다.
            "raw_observation" to 4,
            "rejected_write" to 1,
            "opening_reserve_price" to 6,
        )

    @Test
    fun `축8 CHECK 개수가 테이블별로 기대와 같다`() {
        val actual = mutableMapOf<String, Int>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT conrelid::regclass::text AS table_name, count(*) AS n " +
                            "FROM pg_constraint WHERE contype='c' AND connamespace = 'public'::regnamespace " +
                            "GROUP BY conrelid::regclass::text",
                    ).use { rs ->
                        while (rs.next()) actual[rs.getString("table_name")] = rs.getInt("n")
                    }
            }
        }
        actual shouldBe expectedCheckCountByTable
    }

    @Test
    fun `축8 부가 — COL-06 항등식·H-3 결합식·notice_round 형식 CHECK 가 본문에 살아 있다`() {
        val checkBodies = queryCheckBodies()
        checkBodies.any { it.contains("received = ((normalized + duplicate) + dropped))") } shouldBe true
        checkBodies.any { it.contains("truncated = (truncation_cause IS NOT NULL))") } shouldBe true
        checkBodies.any { it.contains("notice_round ~ '^[0-9]{3}\$'") } shouldBe true
    }

    private fun queryCheckBodies(): List<String> {
        val bodies = mutableListOf<String>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement
                    .executeQuery(
                        "SELECT pg_get_constraintdef(oid) AS def FROM pg_constraint " +
                            "WHERE contype='c' AND connamespace = 'public'::regnamespace",
                    ).use { rs ->
                        while (rs.next()) bodies += rs.getString("def")
                    }
            }
        }
        return bodies
    }
}
