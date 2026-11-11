package bidvector.adapters.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.sql.Connection
import java.sql.SQLException

/**
 * **스키마 문면이 덤프 왕복에서 변하지 않는가.** `tools/db-rehearsal.sh` 의 복원 등식은 카탈로그가
 * 렌더링한 정의를 양변으로 쓴다. 그 문면이 **자기 자신을 다시 파싱한 결과**와 다르면 객체는
 * 그대로인데 복원본의 문면만 달라져 등식이 붉어진다. 2026-11-08 CI `container` job 이 V26 의 행 값
 * 비교(`(state, terminal_reason) IN ((...), ...)`)에서 그것을 실측했다 — 파서는 그 문법을
 * **왼쪽으로 깊게 중첩된** OR 로 저장하고, 덤프 텍스트를 다시 읽을 때는 같은 사슬을 **평평하게**
 * 접는다. 진리표는 양쪽이 같으므로 거동 test 는 전부 초록인 채다.
 *
 * 여기서는 **덤프 없이** 같은 성질을 잰다. 객체의 렌더링을 **같은 표에 그대로 다시 세워**(탐침
 * 이름만 다르게) 두 렌더링이 같은지 본다 — 다르면 그것은 고정점이 아니고, 복원은 반드시 다른
 * 문면을 낸다. 리허설이 지는 주장과 같은 것을 **Kotlin `check` 에서** 말한다는 점이 이 test 의
 * 값이다: 리허설은 CI 뒤쪽 비싼 `container` job 에 있어, 마이그레이션을 쓴 사람이 그 답을 보기까지
 * 한 회차가 통째로 걸린다.
 *
 * **모집단은 복원 지문이 비교하는 집합에서 온다**(verifier r10 M-1 · cr r10 L-5). `tools/db-backup.sh`
 * 의 지문 질의는 제약을 **종류로 가리지 않고** 트리거는 **정의 전문**(`WHEN` 절 포함)을 담는다.
 * 그래서 여기서도 **접힐 식을 담을 수 있는 종류 전부**를 돈다: CHECK 본문 · EXCLUDE 의 `WHERE`
 * 술어 · 트리거의 `WHEN` 술어. 앞 판은 CHECK 하나였고 KDoc 이 그것을 「접힐 식 트리를 담는 유일한
 * 종류」라고 적었는데, 탐침이 그 문장을 반증했다. PK·FK·UNIQUE 는 여전히 모집단 밖이다 — 렌더링에
 * 다시 접힐 식이 없고, PK 는 한 표에 둘을 세울 수도 없다.
 *
 * **오늘 스키마에 EXCLUDE 와 트리거 `WHEN` 은 0 이다.** 모집단만 넓히면 「넓혔지만 아무것도 재지
 * 않는다」가 되므로 종류마다 접힐 식을 **심어 잡히는지**를 상설 양성 대조로 함께 둔다(아래 둘째
 * test). 새 종류가 스키마에 들어오는 순간 첫째 test 가 그것을 함께 돈다.
 *
 * 클래스 이름이 CHECK 만 가리키는 것은 **유래**다 — `V26__outbox_terminal_reason.sql` 의 주석이 이
 * 이름을 적고 있고 적용된 마이그레이션은 고치지 않는다.
 *
 * 쓰기는 **전부 되돌린다**: 탐침마다 savepoint 로 되돌리고 트랜잭션 자체가 ROLLBACK 이다.
 */
class CheckConstraintRenderingFixpointTest : PersistenceTestSupport() {
    @Test
    fun `스키마의 제약·트리거 문면이 자기 렌더링의 고정점이다 — 덤프 왕복 불변`() {
        inRolledBackTransaction { notFixpointObjects(it) } shouldBe emptyList()
    }

    /**
     * **양성 대조** — 종류 셋에 같은 접힐 식(행 값 비교)을 심고 **셋 다** 잡히는지 본다. 모집단에서
     * 한 종류를 빼면 그 줄이 사라져 이 test 가 붉어진다. 집합 등식으로 적는 것은 반대쪽도 재기
     * 위해서다: 탐침이 멀쩡한 객체까지 집으면 심은 셋 말고 다른 줄이 나와 역시 붉어진다.
     */
    @Test
    fun `종류마다 접힐 식을 심으면 그 셋만 잡힌다 — CHECK·EXCLUDE·트리거 WHEN`() {
        val reported =
            inRolledBackTransaction { connection ->
                plantFoldingExpressions(connection)
                notFixpointObjects(connection).map { it.id }
            }

        reported.toSet() shouldBe
            setOf(
                "$CONTROL_TABLE.$CONTROL_CHECK",
                "$CONTROL_TABLE.$CONTROL_EXCLUDE",
                "$CONTROL_TABLE.$CONTROL_TRIGGER",
            )
    }

    /** 고정점이 아닌 객체 하나 — **기대와 관측 렌더링을 함께 싣는다**(cr r10 L-4). */
    private data class Finding(
        val id: String,
        val expected: String,
        val observed: String,
    ) {
        override fun toString(): String = "$id\n    기대: $expected\n    관측: $observed"
    }

    /**
     * 탐침 하나 — 무엇을 세우고(`create`) 무엇과 같아야 하며(`expected`) 어디서 다시 읽는가.
     * `create` 가 없으면 **그 사실이 소견**이다(`unavailable` 이 사유를 든다).
     */
    private class Probe(
        val id: String,
        val expected: String,
        val create: String?,
        val unavailable: String = "",
        val readBack: (Connection) -> String?,
    )

    private fun <T> inRolledBackTransaction(body: (Connection) -> T): T =
        dataSource().connection.use { connection ->
            connection.autoCommit = false
            try {
                body(connection)
            } finally {
                connection.rollback()
            }
        }

    /**
     * 목록이 비면 **그 사실로** 실패한다 — 질의가 아무것도 집지 못한 채 초록이 되는 자리를 막는다
     * (개수 자체는 `CleanMigrationCheckTest` 의 표별 셈이 정본이라 여기서 다시 고정하지 않는다).
     */
    private fun notFixpointObjects(connection: Connection): List<Finding> {
        val probes = constraintProbes(connection) + triggerProbes(connection)
        check(probes.isNotEmpty()) { "public 스키마에 제약도 트리거도 없다 — 질의가 아무것도 재지 않는다" }
        return probes.mapNotNull { findingFor(connection, it) }
    }

    /**
     * 탐침을 세우고 그 렌더링을 읽은 뒤 **savepoint 로 되돌린다**. savepoint 가 없으면 첫 실패가
     * 트랜잭션을 실패 상태로 밀어 뒤 탐침 전부가 「진행 중인 트랜잭션이 실패했다」 하나로 뭉개진다
     * (cr r10 L-3). 실패 자체도 진단이므로 그 줄을 목록에 싣는다 — 조용히 건너뛰면 모집단이 줄어든
     * 것과 구별되지 않는다.
     *
     * 표 이름도 본문도 카탈로그가 준 값이고(사용자 입력이 닿는 자리가 아니다), `regclass` 가 필요한
     * 따옴표를 이미 붙여 돌려준다. `NOT VALID` 로 남은 제약은 렌더링이 그 꼬리를 달고 오며 그 문면
     * 그대로 다시 세워진다 — 특례가 필요 없다.
     */
    private fun findingFor(
        connection: Connection,
        probe: Probe,
    ): Finding? {
        val statementText = probe.create ?: return Finding(probe.id, probe.expected, probe.unavailable)
        val savepoint = connection.setSavepoint(PROBE)
        return try {
            connection.createStatement().use { it.execute(statementText) }
            when (val rendered = probe.readBack(connection)) {
                probe.expected -> null
                null -> Finding(probe.id, probe.expected, "탐침이 카탈로그에 서지 않았다")
                else -> Finding(probe.id, probe.expected, rendered)
            }
        } catch (failure: SQLException) {
            Finding(probe.id, probe.expected, "탐침 실패(${failure.sqlState}) ${firstLineOf(failure.message)}")
        } finally {
            connection.rollback(savepoint)
            connection.releaseSavepoint(savepoint)
        }
    }

    /** CHECK 와 EXCLUDE — 전자는 본문이, 후자는 `WHERE` 술어가 접힐 식을 담는다. */
    private fun constraintProbes(connection: Connection): List<Probe> =
        catalogRows(connection, CONSTRAINT_CATALOG).map { row ->
            Probe(
                id = "${row.owner}.${row.name}",
                expected = row.definition,
                create = "ALTER TABLE ${row.owner} ADD CONSTRAINT $PROBE ${row.definition}",
                readBack = { probed -> renderedDefinition(probed, CONSTRAINT_RENDERING, row.owner) },
            )
        }

    /**
     * 트리거 — 정의 전문에서 **이름 토큰만** 탐침 이름으로 바꿔 다시 세운다. 바꿔 끼울 자리를 찾지
     * 못하면(이름이 따옴표로 렌더링되는 판) **그 사실을 소견으로 낸다** — 원본 이름 그대로 세우면
     * 이름 충돌로 실패하고, 조용히 건너뛰면 모집단에서 빠진 것과 구별되지 않는다.
     */
    private fun triggerProbes(connection: Connection): List<Probe> =
        catalogRows(connection, TRIGGER_CATALOG).map { row ->
            val renamed = row.definition.replaceFirst("TRIGGER ${row.name} ", "TRIGGER $PROBE ")
            Probe(
                id = "${row.owner}.${row.name}",
                expected = renamed,
                create = renamed.takeIf { it != row.definition },
                unavailable = "정의가 이름을 그대로 담지 않아 탐침 이름을 끼울 수 없다",
                readBack = { probed -> renderedDefinition(probed, TRIGGER_RENDERING, row.owner) },
            )
        }

    private fun renderedDefinition(
        connection: Connection,
        sql: String,
        owner: String,
    ): String? =
        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, PROBE)
            statement.setString(2, owner)
            statement.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    /**
     * 접힐 식을 **종류마다** 심는다. 행 값 비교가 그 형태인 이유는 위 클래스 KDoc 이 든다 — V26 이
     * CI `container` job 에서 실측한 바로 그 식이다. 표·함수·트리거 전부 이 트랜잭션 안에서만
     * 살고(PostgreSQL 은 DDL 도 트랜잭션이다) 호출자가 ROLLBACK 한다.
     */
    private fun plantFoldingExpressions(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute(
                "CREATE TABLE $CONTROL_TABLE (a INT NOT NULL, b INT NOT NULL, " +
                    "CONSTRAINT $CONTROL_CHECK CHECK ((a, b) $FOLDED_IN_LIST))",
            )
            statement.execute(
                "ALTER TABLE $CONTROL_TABLE ADD CONSTRAINT $CONTROL_EXCLUDE " +
                    "EXCLUDE USING btree (a WITH =) WHERE ((a, b) $FOLDED_IN_LIST)",
            )
            statement.execute(
                "CREATE FUNCTION $CONTROL_FUNCTION() RETURNS trigger LANGUAGE plpgsql " +
                    "AS 'BEGIN RETURN NEW; END;'",
            )
            statement.execute(
                "CREATE TRIGGER $CONTROL_TRIGGER BEFORE INSERT ON $CONTROL_TABLE FOR EACH ROW " +
                    "WHEN ((NEW.a, NEW.b) $FOLDED_IN_LIST) EXECUTE FUNCTION $CONTROL_FUNCTION()",
            )
        }
    }

    private data class CatalogRow(
        val owner: String,
        val name: String,
        val definition: String,
    )

    private fun catalogRows(
        connection: Connection,
        sql: String,
    ): List<CatalogRow> {
        val rows = mutableListOf<CatalogRow>()
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    rows += CatalogRow(rs.getString("owner"), rs.getString("name"), rs.getString("def"))
                }
            }
        }
        return rows
    }

    private companion object {
        const val PROBE = "check_rendering_fixpoint_probe"

        /** `contype` 둘만 — 접힐 식을 담는 종류다(`'c'` CHECK · `'x'` EXCLUDE). */
        const val CONSTRAINT_CATALOG =
            "SELECT conrelid::regclass::text AS owner, conname AS name, pg_get_constraintdef(oid) AS def " +
                "FROM pg_constraint " +
                "WHERE connamespace = 'public'::regnamespace AND contype IN ('c', 'x') AND conrelid <> 0 " +
                "ORDER BY owner, name"

        /** 복원 지문의 트리거 질의와 같은 모집단 — 내부 트리거(FK 가 심는 것)만 뺀다. */
        const val TRIGGER_CATALOG =
            "SELECT c.relname AS owner, t.tgname AS name, pg_get_triggerdef(t.oid) AS def " +
                "FROM pg_trigger t " +
                "JOIN pg_class c ON c.oid = t.tgrelid " +
                "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE NOT t.tgisinternal AND n.nspname = 'public' " +
                "ORDER BY owner, name"

        const val CONSTRAINT_RENDERING =
            "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ? AND conrelid = ?::regclass"

        const val TRIGGER_RENDERING =
            "SELECT pg_get_triggerdef(oid) FROM pg_trigger WHERE tgname = ? AND tgrelid = ?::regclass"

        /**
         * 행 값 비교의 목록 — 행 형태 둘(열 그대로 · `NEW.` 접두)이 같은 목록을 쓴다.
         *
         * **쌍이 셋인 것이 이 값의 전부다.** 행 값 비교는 쌍마다 OR 를 하나 더 얹는 **왼쪽 깊은**
         * 트리로 저장되는데, 쌍이 둘이면 그 트리가 인자 둘의 OR 하나여서 렌더링이 이미 평평하다 —
         * 쌍 둘로 심은 양성 대조는 **조용히 공허해진다**(실측: 둘은 종류 셋 모두 고정점, 셋부터
         * 모두 아니다. V26 이 걸린 자리는 쌍 일곱이었다).
         */
        const val FOLDED_IN_LIST = "IN ((1, 2), (3, 4), (5, 6))"

        const val CONTROL_TABLE = "rendering_fixpoint_control"
        const val CONTROL_CHECK = "rendering_fixpoint_control_check"
        const val CONTROL_EXCLUDE = "rendering_fixpoint_control_exclude"
        const val CONTROL_TRIGGER = "rendering_fixpoint_control_trigger"
        const val CONTROL_FUNCTION = "rendering_fixpoint_control_noop"
    }
}

/** 예외 문면의 첫 줄 — PostgreSQL 은 뒤에 `Where:`·`Detail:` 를 여러 줄로 붙인다. */
private fun firstLineOf(message: String?): String = message.orEmpty().substringBefore('\n')
