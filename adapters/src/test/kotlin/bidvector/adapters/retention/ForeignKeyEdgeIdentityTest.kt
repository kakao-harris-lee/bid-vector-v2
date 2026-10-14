package bidvector.adapters.retention

import bidvector.adapters.persistence.PersistenceTestSupport
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * FK 간선의 **동일성 키**(PR #73 P7) — 제약 이름은 표마다 유일할 뿐 **스키마 안에서 유일하지
 * 않다**. 같은 이름의 FK 가 두 표에 있으면 이름을 키로 삼은 집계가 간선 둘을 하나로 접고,
 * 접히는 쪽은 **먼저 읽힌 간선**이다 — 그 간선이 사라지면 그 자식이 부모를 막지 못하고
 * (설계 검토 우회 6 의 가드가 없는 것과 같다) 삭제 순서에서도 그 변이 빠진다. 되돌릴 수 없는
 * 경로라 「오늘 그런 이름이 없다」로 두지 않고 키 자체를 `pg_constraint.oid` 로 옮겼다.
 *
 * **복합 FK 를 한쪽에 둔 이유**: 이름이 같은 두 제약의 행이 섞여 읽히면 `ORDER BY` 가 이름
 * 기준인 동안 열 쌍의 순서까지 어긋난다 — 자식 `c1`·`c2` 가 부모 `k1`·`k2` 와 짝을 잘못 맺는다. 같은
 * test 가 그 축도 잠근다.
 *
 * **자기 컨테이너를 띄운다**(cr L-3 과 같은 비용 기록): 이 test 가 심는 네 표는 스키마 정본이
 * 아니고, 공유 클러스터에 넣으면 표 집합 장부(`CleanMigrationTest` 축1 · 레지스트리 집합 등식)가
 * 그 표들을 보고 붉어진다. 마이그레이션은 돌리지 않는다 — 읽는 질의가 `pg_constraint` 뿐이라
 * 빈 DB 에 네 표만 있으면 충분하고, 그래서 이 컨테이너는 공유 컨테이너와 **짧게만** 겹친다.
 */
class ForeignKeyEdgeIdentityTest {
    @Test
    fun `같은 이름의 FK 가 표 둘에 있으면 간선도 둘이다 — 열 쌍 순서까지`() {
        PostgreSQLContainer(DockerImageName.parse(PersistenceTestSupport.POSTGRES_IMAGE))
            .withDatabaseName("fk_identity")
            .withUsername("fk_owner")
            .withPassword("fk_test_only")
            .use { container ->
                container.start()
                val source =
                    PGSimpleDataSource().apply {
                        setUrl(container.jdbcUrl)
                        user = container.username
                        password = container.password
                    }
                source.connection.use { connection ->
                    connection.createStatement().use { it.execute(SAME_NAME_FK_SCHEMA) }

                    val edges = readForeignKeyEdges(connection)

                    edges.size shouldBe 2
                    val single = edges.single { it.child == "child_single" }
                    single.parent shouldBe "parent_single"
                    single.cascade shouldBe true
                    single.columnPairs shouldContainExactly listOf("single_id" to "id")
                    val composite = edges.single { it.child == "child_composite" }
                    composite.parent shouldBe "parent_composite"
                    composite.cascade shouldBe false
                    composite.columnPairs shouldContainExactly listOf("c1" to "k1", "c2" to "k2")
                }
            }
    }
}

/** 두 자식이 **같은 제약 이름**을 쓴다 — PostgreSQL 은 제약 이름을 표 안에서만 유일하게 본다. */
private val SAME_NAME_FK_SCHEMA =
    """
    CREATE TABLE parent_single (id int PRIMARY KEY);
    CREATE TABLE parent_composite (k1 int, k2 int, PRIMARY KEY (k1, k2));
    CREATE TABLE child_single (
        id int PRIMARY KEY,
        single_id int,
        CONSTRAINT fk_shared_name FOREIGN KEY (single_id) REFERENCES parent_single (id) ON DELETE CASCADE
    );
    CREATE TABLE child_composite (
        id int PRIMARY KEY,
        c1 int,
        c2 int,
        CONSTRAINT fk_shared_name FOREIGN KEY (c1, c2) REFERENCES parent_composite (k1, k2)
    );
    """.trimIndent()
