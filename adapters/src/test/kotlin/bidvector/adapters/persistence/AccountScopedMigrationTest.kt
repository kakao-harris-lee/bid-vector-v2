package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.MigrationVersion
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.Connection
import javax.sql.DataSource

/** V28 직전까지 적용한다(V27 포함) — 그 다음 파일이 이 test 의 대상이다. */
private const val BEFORE_ACCOUNT_SCOPE_MIGRATION = "27"

/** 이 test 의 대상. */
private const val ACCOUNT_SCOPE_MIGRATION = "28"

/** V28 이 계정 칸을 다는 네 표 — 세 갈래 전부에서 같은 집합을 본다. */
private val SCOPED_TABLES =
    listOf("operator_strategy", "operator_strategy_revision", "operator_profile", "edit_session")

/**
 * **V28 의 이관 규칙을 갈래별로 잰다**(M15/15A) — 계정 0 · 1 · 2 이상.
 *
 * 왜 자기 컨테이너인가: 공유 하네스([PersistenceTestSupport])는 `init` 에서 마이그레이션을 **끝까지**
 * 돌린다. 「V27 까지만 적용한 DB 에 행을 심고 V28 을 돌린다」는 그 위에서 만들 수 없다 —
 * `MigrationLockTimeoutTest` · `ForeignKeyEdgeIdentityTest` 가 같은 사유로 같은 선택을 했다.
 * 갈래마다 컨테이너를 새로 띄운다(한 컨테이너에서 둘을 재면 앞 갈래의 `account` 행이 뒤 갈래의
 * `min(id)` 를 정한다).
 *
 * **이관 규칙**: 싱글턴 행은 **가장 작은 `account.id`** 에 붙는다. 계정이 0 이면 소유자가 없으므로
 * 그 행은 **버린다** — `account_id` 가 `NOT NULL` 이 되고, 남겨 둔들 어느 계정도 읽지 못한다
 * (질의 술어가 `account_id = ?` 하나다). 그 선택은 되돌릴 수 없어 slice 계약의 알려진 제한 1 이다.
 */
class AccountScopedMigrationTest {
    @Test
    fun `계정이 둘이면 싱글턴 행이 가장 작은 계정에 붙는다`() {
        withDatabaseAt(BEFORE_ACCOUNT_SCOPE_MIGRATION) { source ->
            val accounts = source.connection.use { it.insertAccounts("owner-a", "owner-b") }
            source.connection.use { it.seedSingletonRows() }

            migrateTo(source, ACCOUNT_SCOPE_MIGRATION)

            source.connection.use { connection ->
                SCOPED_TABLES.forEach { table ->
                    connection.accountIdsOf(table) shouldContainExactly listOf(accounts.min())
                }
            }
        }
    }

    @Test
    fun `계정이 하나면 싱글턴 행이 그 계정에 붙는다`() {
        withDatabaseAt(BEFORE_ACCOUNT_SCOPE_MIGRATION) { source ->
            val accounts = source.connection.use { it.insertAccounts("only-owner") }
            source.connection.use { it.seedSingletonRows() }

            migrateTo(source, ACCOUNT_SCOPE_MIGRATION)

            source.connection.use { connection ->
                SCOPED_TABLES.forEach { table ->
                    connection.accountIdsOf(table) shouldContainExactly listOf(accounts.single())
                }
            }
        }
    }

    /**
     * 계정이 0 이면 마이그레이션은 **성공하고 행을 버린다**. 실패시키지 않는 이유: migrate 는 기동의
     * 부수효과라 실패가 곧 기동 정지이고, 남길 수도 없다(`NOT NULL` + 읽을 주체 없음).
     */
    @Test
    fun `계정이 0 이면 싱글턴 행은 버려지고 마이그레이션은 성공한다`() {
        withDatabaseAt(BEFORE_ACCOUNT_SCOPE_MIGRATION) { source ->
            source.connection.use { it.seedSingletonRows() }

            migrateTo(source, ACCOUNT_SCOPE_MIGRATION)

            source.connection.use { connection ->
                SCOPED_TABLES.forEach { table -> connection.accountIdsOf(table) shouldContainExactly emptyList() }
            }
        }
    }

    /** 행이 하나도 없는 DB 에서도 같은 마이그레이션이 돈다 — 깨끗한 설치의 갈래다. */
    @Test
    fun `빈 DB 에서 V28 은 아무 행도 만들지 않는다`() {
        withDatabaseAt(BEFORE_ACCOUNT_SCOPE_MIGRATION) { source ->
            source.connection.use { it.insertAccounts("empty-owner") }

            migrateTo(source, ACCOUNT_SCOPE_MIGRATION)

            source.connection.use { connection ->
                SCOPED_TABLES.forEach { table -> connection.accountIdsOf(table) shouldContainExactly emptyList() }
            }
        }
    }

    /**
     * **이관 뒤의 모양**: 네 표의 `account_id` 는 `NOT NULL` 이고 `account` 를 가리킨다. 싱글턴 키
     * (`id SMALLINT CHECK (id = 1)`)는 두 표에서 사라진다 — 남아 있으면 둘째 계정의 저장이 그 CHECK
     * 에 걸린다.
     */
    @Test
    fun `이관 뒤 네 표의 account_id 는 NOT NULL 이고 싱글턴 키는 사라진다`() {
        withDatabaseAt(ACCOUNT_SCOPE_MIGRATION) { source ->
            source.connection.use { connection ->
                SCOPED_TABLES.forEach { table -> connection.isNullable(table, "account_id") shouldBe false }
                connection.hasColumn("operator_strategy", "id") shouldBe false
                connection.hasColumn("operator_profile", "id") shouldBe false
            }
        }
    }
}

/** 컨테이너 하나를 띄워 [version] 까지 적용한 DB 를 [body] 에 넘긴다 — 갈래마다 새 컨테이너다. */
private fun withDatabaseAt(
    version: String,
    body: (DataSource) -> Unit,
) {
    PostgreSQLContainer(DockerImageName.parse(PersistenceTestSupport.POSTGRES_IMAGE))
        .withDatabaseName("account_scope_migration")
        .withUsername("bidvector_admin")
        .withPassword("bidvector_test_only")
        .use { container ->
            container.start()
            val source =
                PGSimpleDataSource().apply {
                    setUrl(container.jdbcUrl)
                    user = container.username
                    password = container.password
                }
            migrateTo(source, version)
            body(source)
        }
}

private fun migrateTo(
    source: DataSource,
    version: String,
) {
    Flyway
        .configure()
        .dataSource(source)
        .locations("classpath:db/migration")
        .target(MigrationVersion.fromVersion(version))
        .load()
        .migrate()
}

private fun Connection.insertAccounts(vararg logins: String): List<Long> =
    logins.map { login ->
        prepareStatement(
            "INSERT INTO account (login_name, passphrase_hash, created_at, updated_at, failed_attempts) " +
                "VALUES (?, 'fixture-hash', now(), now(), 0) RETURNING id",
        ).use { statement ->
            statement.setString(1, login)
            statement.executeQuery().use { rows ->
                rows.next()
                rows.getLong("id")
            }
        }
    }

/** V27 시점의 싱글턴 행 네 벌 — 열 이름과 필수 값은 V8·V9·V12 가 정한 그대로다. */
private fun Connection.seedSingletonRows() {
    createStatement().use { statement ->
        statement.execute(
            """
            INSERT INTO operator_strategy (
                id, focus_categories, focus_region_terms, exclude_region_terms,
                required_keyword_terms, exclude_keyword_terms, revision
            ) VALUES (1, '{}', '{}', '{}', '{서울}', '{}', 3);
            INSERT INTO operator_strategy_revision (
                revision, focus_categories, focus_region_terms, exclude_region_terms,
                required_keyword_terms, exclude_keyword_terms
            ) VALUES (3, '{}', '{}', '{}', '{서울}', '{}');
            INSERT INTO operator_profile (id, business_types, licenses_declared, license_names, region_terms)
                VALUES (1, '{}', true, '{합성면허}', '{}');
            INSERT INTO edit_session (
                id, operator_id, state, state_payload, expires_at, session_version
            ) VALUES ('fixture-session', 'operator', 'EXPIRED', NULL, now() + interval '1 hour', 0);
            """.trimIndent(),
        )
    }
}

private fun Connection.accountIdsOf(table: String): List<Long> =
    createStatement().use { statement ->
        statement.executeQuery("SELECT account_id FROM $table ORDER BY account_id").use { rows ->
            val ids = mutableListOf<Long>()
            while (rows.next()) ids += rows.getLong("account_id")
            ids
        }
    }

private fun Connection.isNullable(
    table: String,
    column: String,
): Boolean =
    prepareStatement(
        "SELECT is_nullable FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
    ).use { statement ->
        statement.setString(1, table)
        statement.setString(2, column)
        statement.executeQuery().use { rows ->
            require(rows.next()) { "$table.$column 열이 없다" }
            rows.getString("is_nullable") == "YES"
        }
    }

private fun Connection.hasColumn(
    table: String,
    column: String,
): Boolean =
    prepareStatement(
        "SELECT 1 FROM information_schema.columns " +
            "WHERE table_schema = 'public' AND table_name = ? AND column_name = ?",
    ).use { statement ->
        statement.setString(1, table)
        statement.setString(2, column)
        statement.executeQuery().use { it.next() }
    }
