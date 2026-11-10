package bidvector.adapters.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.sql.Connection

/**
 * **제약 문면이 덤프 왕복에서 변하지 않는가.** `tools/db-rehearsal.sh` 의 복원 등식은
 * `pg_get_constraintdef` 를 양변으로 쓴다. 그 문면이 **자기 자신을 다시 파싱한 결과**와 다르면
 * 제약은 그대로인데 복원본의 문면만 달라져 등식이 붉어진다. 2026-11-08 CI `container` job 이
 * V26 의 행 값 비교(`(state, terminal_reason) IN ((...), ...)`)에서 그것을 실측했다 — 파서는 그
 * 문법을 **왼쪽으로 깊게 중첩된** OR 로 저장하고, 덤프 텍스트를 다시 읽을 때는 같은 사슬을
 * **평평하게** 접는다. 진리표는 양쪽이 같으므로 거동 test 는 전부 초록인 채다.
 *
 * 여기서는 **덤프 없이** 같은 성질을 잰다. 제약의 렌더링을 **같은 표에 그대로 다시 세워**(탐침
 * 이름만 다르게) 두 렌더링이 같은지 본다 — 다르면 그 제약은 고정점이 아니고, 복원은 반드시 다른
 * 문면을 낸다. 리허설이 지는 주장과 같은 것을 **Kotlin `check` 에서** 말한다는 점이 이 test 의
 * 값이다: 리허설은 CI 뒤쪽 비싼 `container` job 에 있어, 마이그레이션을 쓴 사람이 그 답을 보기까지
 * 한 회차가 통째로 걸린다.
 *
 * 대상은 CHECK 뿐이다 — 접힐 식 트리를 담는 유일한 종류다. PK 는 한 표에 둘을 세울 수 없고,
 * FK·UNIQUE 의 렌더링에는 다시 접힐 식이 없다.
 *
 * 쓰기는 **전부 되돌린다**: 탐침은 매 반복에서 지워지고 트랜잭션 자체가 ROLLBACK 이다.
 */
class CheckConstraintRenderingFixpointTest : PersistenceTestSupport() {
    @Test
    fun `스키마의 모든 CHECK 문면이 자기 렌더링의 고정점이다 — 덤프 왕복 불변`() {
        notFixpointConstraints() shouldBe emptyList()
    }

    private data class CheckConstraint(
        val table: String,
        val name: String,
        val definition: String,
    )

    private fun notFixpointConstraints(): List<String> {
        val broken = mutableListOf<String>()
        dataSource().connection.use { connection ->
            connection.autoCommit = false
            try {
                catalog(connection).forEach { constraint ->
                    if (renderProbe(connection, constraint) != constraint.definition) {
                        broken += "${constraint.table}.${constraint.name}"
                    }
                }
            } finally {
                connection.rollback()
            }
        }
        return broken
    }

    /**
     * 탐침을 세우고 그 렌더링을 읽은 뒤 **같은 반복 안에서** 지운다 — 이름 하나를 돌려 쓰므로
     * 다음 제약이 같은 자리를 쓴다. 표 이름도 본문도 카탈로그가 준 값이고(사용자 입력이 닿는
     * 자리가 아니다), `regclass` 가 필요한 따옴표를 이미 붙여 돌려준다. `NOT VALID` 로 남은
     * 제약은 렌더링이 그 꼬리를 달고 오며, 그 문면 그대로 다시 세워진다 — 특례가 필요 없다.
     */
    private fun renderProbe(
        connection: Connection,
        constraint: CheckConstraint,
    ): String {
        connection.createStatement().use { statement ->
            statement.execute("ALTER TABLE ${constraint.table} ADD CONSTRAINT $PROBE ${constraint.definition}")
        }
        val rendered =
            connection
                .prepareStatement(
                    "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ? AND conrelid = ?::regclass",
                ).use { statement ->
                    statement.setString(1, PROBE)
                    statement.setString(2, constraint.table)
                    statement.executeQuery().use { rs ->
                        check(rs.next()) { "탐침 제약이 서지 않았다: ${constraint.table}" }
                        rs.getString(1)
                    }
                }
        connection.createStatement().use { statement ->
            statement.execute("ALTER TABLE ${constraint.table} DROP CONSTRAINT $PROBE")
        }
        return rendered
    }

    /**
     * 목록이 비면 **그 사실로** 실패한다 — 질의가 아무것도 집지 못한 채 초록이 되는 자리를 막는다
     * (개수 자체는 `CleanMigrationCheckTest` 의 표별 셈이 정본이라 여기서 다시 고정하지 않는다).
     */
    private fun catalog(connection: Connection): List<CheckConstraint> {
        val constraints = mutableListOf<CheckConstraint>()
        connection
            .prepareStatement(
                "SELECT conrelid::regclass::text AS table_name, conname, pg_get_constraintdef(oid) AS def " +
                    "FROM pg_constraint " +
                    "WHERE connamespace = 'public'::regnamespace AND contype = 'c' AND conrelid <> 0 " +
                    "ORDER BY table_name, conname",
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    while (rs.next()) {
                        constraints +=
                            CheckConstraint(
                                table = rs.getString("table_name"),
                                name = rs.getString("conname"),
                                definition = rs.getString("def"),
                            )
                    }
                }
            }
        check(constraints.isNotEmpty()) { "public 스키마에 CHECK 제약이 하나도 없다 — 질의가 아무것도 재지 않는다" }
        return constraints
    }

    private companion object {
        const val PROBE = "check_rendering_fixpoint_probe"
    }
}
