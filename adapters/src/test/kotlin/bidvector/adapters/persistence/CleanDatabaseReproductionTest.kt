package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldBeEmpty
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.io.File
import java.util.Properties
import javax.sql.DataSource

/**
 * 완료 조건 1·6B ①(scope.md) — **빈** 컨테이너에 마이그레이션 전건을 적용한 뒤 카탈로그
 * (표·PK/UNIQUE·인덱스·트리거)를 손으로 선언한 기대치와 대조한다. 기대치는
 * `config/quality/schema-baseline.properties`(매직값을 test 밖으로, v2-지침서.md §5).
 *
 * **자기 증명 금지(설계 검토 우회 (2))** — 기대치를 생성된 스키마에서 자동 추출하지
 * 않는다. 손으로 적고 카탈로그와 다르면 이 test 가 붉어진다.
 *
 * **트리거 목록이 핵심 방어선(우회 (1))** — V2·V3 의 append-only·provenance 가드가
 * 여기 없으면 새 마이그레이션이 그것을 깨도 이 test 는 초록이다.
 *
 * [PersistenceTestSupport]의 공유 컨테이너를 쓰지 않는다 — 그 컨테이너는 이미 마이그레이션이
 * 적용된 채 재사용돼(companion object, JVM 단일 인스턴스) "빈 컨테이너" 조건을 만족하지
 * 못한다. 이 test 전용 컨테이너를 새로 띄운다.
 */
@Testcontainers(disabledWithoutDocker = false)
class CleanDatabaseReproductionTest {
    @Test
    fun `빈 컨테이너에 마이그레이션 전건을 적용하면 표·PK UNIQUE·인덱스·트리거가 기대치와 같다`() {
        val dataSource = freshlyMigratedDataSource()
        val baseline = SchemaBaseline.load()

        dataSource.queryTables() shouldBeSetEqualTo baseline.tables

        val mismatches = mutableListOf<String>()
        for (table in baseline.tables) {
            checkAxis(mismatches, "pk-unique", table, dataSource.queryPkUniqueConstraints(table), baseline.pkUniqueOf(table))
            checkAxis(mismatches, "indexes", table, dataSource.queryIndexes(table), baseline.indexesOf(table))
            checkAxis(mismatches, "triggers", table, dataSource.queryTriggers(table), baseline.triggersOf(table))
        }
        mismatches.shouldBeEmpty()
    }
}

private infix fun Set<String>.shouldBeSetEqualTo(expected: Set<String>) {
    if (this != expected) {
        error("실제 표=$this, 기대 표=$expected (config/quality/schema-baseline.properties 의 tables 와 대조)")
    }
}

private fun checkAxis(
    mismatches: MutableList<String>,
    axis: String,
    table: String,
    actual: Set<String>,
    expected: Set<String>,
) {
    if (actual != expected) {
        mismatches += "$axis[$table] actual=$actual expected=$expected"
    }
}

private const val CLEAN_DB_IMAGE = "postgres:16.4" // PersistenceTestSupport.POSTGRES_IMAGE 와 같은 값을 유지한다(6C 고정 태그).

private fun freshlyMigratedDataSource(): DataSource {
    val container =
        PostgreSQLContainer(DockerImageName.parse(CLEAN_DB_IMAGE))
            .withDatabaseName("bidvector_clean")
            .withUsername("bidvector_clean_admin")
            .withPassword("bidvector_clean_only")
    container.start()
    val dataSource =
        PGSimpleDataSource().apply {
            setUrl(container.jdbcUrl)
            user = container.username
            password = container.password
        }
    Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
    return dataSource
}

private fun DataSource.queryStringColumn(
    sql: String,
    vararg params: String,
): Set<String> =
    connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            params.forEachIndexed { index, value -> statement.setString(index + 1, value) }
            statement.executeQuery().use { rs ->
                val result = mutableSetOf<String>()
                while (rs.next()) result += rs.getString(1)
                result
            }
        }
    }

private fun DataSource.queryTables(): Set<String> =
    queryStringColumn("SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'")

private fun DataSource.queryIndexes(table: String): Set<String> =
    queryStringColumn("SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = ?", table)

private fun DataSource.queryPkUniqueConstraints(table: String): Set<String> =
    queryStringColumn(
        """
        SELECT conname FROM pg_constraint
        WHERE connamespace = 'public'::regnamespace AND contype IN ('p', 'u') AND conrelid = ?::regclass
        """,
        table,
    )

private fun DataSource.queryTriggers(table: String): Set<String> =
    queryStringColumn(
        "SELECT tgname FROM pg_trigger WHERE NOT tgisinternal AND tgrelid = ?::regclass",
        table,
    )

/** [config/quality/schema-baseline.properties]의 손 선언 값 — 자동 추출 금지(우회 (2)). */
private class SchemaBaseline private constructor(
    private val properties: Properties,
) {
    val tables: Set<String> = properties.commaSet("tables")

    fun pkUniqueOf(table: String): Set<String> = properties.commaSet("pk-unique.$table")

    fun indexesOf(table: String): Set<String> = properties.commaSet("indexes.$table")

    fun triggersOf(table: String): Set<String> = properties.commaSet("triggers.$table")

    companion object {
        fun load(): SchemaBaseline {
            val file = File("../config/quality/schema-baseline.properties")
            check(file.isFile) { "스키마 기대치 파일을 찾지 못했다: ${file.absolutePath}" }
            val properties = Properties()
            file.inputStream().use(properties::load)
            return SchemaBaseline(properties)
        }
    }
}

private fun Properties.commaSet(key: String): Set<String> =
    getProperty(key, "").split(",").map(String::trim).filter(String::isNotEmpty).toSet()
