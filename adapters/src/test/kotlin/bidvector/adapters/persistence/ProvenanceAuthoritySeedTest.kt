package bidvector.adapters.persistence

import bidvector.procurement.IS_AUTHORITATIVE
import bidvector.procurement.ProvenanceKind
import io.kotest.matchers.maps.shouldContainExactly
import org.junit.jupiter.api.Test

/**
 * `provenance_authority`(V2 seed) ↔ `bidvector.procurement.IS_AUTHORITATIVE`가 같은 데이터인지
 * 대조한다(3D 설계 검토 「구현 지침」 — 코드↔SQL 이중 선언은 허용하되 일치 test 필수). 표에
 * 없는 값은 Kotlin·SQL 둘 다 기본이 비권위(false)라, DB 표에는 `ProvenanceKind` 전 값을
 * seed해 명시적으로 false도 적어 둔다(V2 migration) — 이 test는 그 여섯 값 전부를 대조한다.
 */
class ProvenanceAuthoritySeedTest : PersistenceTestSupport() {
    @Test
    fun `provenance_authority seed 는 IS_AUTHORITATIVE 와 완전히 같다`() {
        val fromDb = mutableMapOf<ProvenanceKind, Boolean>()
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT provenance, authoritative FROM provenance_authority").use { rs ->
                    while (rs.next()) {
                        fromDb[ProvenanceKind.valueOf(rs.getString("provenance"))] = rs.getBoolean("authoritative")
                    }
                }
            }
        }

        val expected = ProvenanceKind.entries.associateWith { kind -> IS_AUTHORITATIVE[kind] ?: false }

        fromDb shouldContainExactly expected
    }
}
