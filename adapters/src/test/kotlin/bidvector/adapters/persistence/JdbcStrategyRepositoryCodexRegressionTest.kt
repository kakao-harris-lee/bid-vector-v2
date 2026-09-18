package bidvector.adapters.persistence

import bidvector.adapters.strategy.JdbcStrategyRepository
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.sql.SQLException

/**
 * [JdbcStrategyRepositoryTest]에서 분리(sizeGate, 파일 500줄 한도) — Codex 심판
 * request_changes(HIGH·MEDIUM, M6/6F-1 D-6F1-9·D-6F1-10) 회귀 중 **원시 SQL로 도메인
 * 경로(`validate()`)를 우회하는** 자리만 여기 모은다. `workflow`를 거치는 경계값 왕복
 * test(정상 케이스)는 원본 파일에 남아 있다 — 이 파일은 저장 계층 자체의 방어(V9 CHECK·
 * `getTextList`)만 겨눈다.
 *
 * `testPolicy()`는 [JdbcStrategyRepositoryTest.kt]의 `internal` fixture 를 그대로
 * 재사용한다(중복 금지, 같은 파일의 KDoc 참고).
 */
class JdbcStrategyRepositoryCodexRegressionTest : PersistenceTestSupport() {
    /**
     * `operator_strategy` 싱글턴 행을 원시 SQL로 심는다 — 도메인 경로(`validate()`)를
     * 우회해 저장 계층 자체의 방어(CHECK·`getTextList`)만 겨눈다.
     */
    private fun insertRawStrategy(
        minBudgetWon: BigDecimal,
        focusRegionTerms: String = "'{}'::text[]",
    ) {
        dataSource().connection.use { connection ->
            connection
                .prepareStatement(
                    "INSERT INTO operator_strategy (id, revision, focus_categories, focus_region_terms, " +
                        "exclude_region_terms, required_keyword_terms, exclude_keyword_terms, " +
                        "min_budget_won, min_budget_currency, min_budget_vat, min_budget_provenance) " +
                        "VALUES (1, 0, '{}'::text[], $focusRegionTerms, '{}'::text[], '{}'::text[], '{}'::text[], " +
                        "?, 'KRW', 'INCLUSIVE', 'OPERATOR_DECLARED')",
                ).use { statement ->
                    statement.setBigDecimal(1, minBudgetWon)
                    statement.executeUpdate()
                }
        }
    }

    // Codex 심판 HIGH 회귀 — V9 의 won 범위 CHECK 가 Long 상한 초과 값의 저장 자체를 막는다.
    @Test
    fun `Codex 심판 HIGH 회귀 — 예산 won 이 Long 상한을 넘으면 저장이 거부된다`() {
        val overflow = BigDecimal("9223372036854775808") // Long.MAX_VALUE + 1
        shouldThrow<SQLException> { insertRawStrategy(overflow) }
    }

    // Codex 심판 MEDIUM 회귀 — 배열 원소의 NULL 이 조용히 사라지지 않고 load 가 크게 실패한다.
    @Test
    fun `Codex 심판 MEDIUM 회귀 — 배열 원소에 NULL 이 있으면 load 가 크게 실패한다`() {
        insertRawStrategy(BigDecimal.TEN, "ARRAY['서울', NULL]::text[]")
        val repository = JdbcStrategyRepository(dataSource(), testPolicy())

        shouldThrow<IllegalStateException> { repository.load() }
    }

    /**
     * verifier r4 MEDIUM-3 — [dropMinBudgetWonCheck]가 뗀 CHECK 를 매 test 뒤 되건다.
     * 컨테이너가 class 간 공유([PersistenceTestSupport]의 `@BeforeEach` TRUNCATE 는 DDL 을
     * 되돌리지 않는다)라, 여기서 빼먹으면 뒤따르는 test 가 그 CHECK 없이 실행돼 오염된다
     * (verifier 가 실제로 겪은 함정). 멱등 — 이 test class 의 다른 test 는 CHECK 를 떼지
     * 않으므로 그때는 조용히 아무 일도 하지 않는다.
     */
    @AfterEach
    fun restoreMinBudgetWonCheckIfDropped() {
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                val exists =
                    statement
                        .executeQuery(
                            "SELECT 1 FROM pg_constraint WHERE conrelid = 'operator_strategy'::regclass " +
                                "AND conname = 'operator_strategy_min_budget_won_check'",
                        ).use { it.next() }
                if (!exists) {
                    // CHECK 를 되걸기 전에 그 test 가 심은 위반 행을 먼저 비운다 — 남아 있으면
                    // ADD CONSTRAINT 자체가 그 위반으로 실패한다. 다음 @BeforeEach 의 TRUNCATE
                    // 와 겹치는 정리이지만, 이 자리에서 하지 않으면 CHECK 복구가 실패한다.
                    statement.execute("TRUNCATE TABLE operator_strategy RESTART IDENTITY")
                    statement.execute(
                        "ALTER TABLE operator_strategy ADD CONSTRAINT operator_strategy_min_budget_won_check " +
                            "CHECK (min_budget_won IS NULL OR min_budget_won BETWEEN 0 AND 9223372036854775807)",
                    )
                }
            }
        }
    }

    /** [restoreMinBudgetWonCheckIfDropped]가 되걸 대상 — CHECK 를 런타임에 뗀다. */
    private fun dropMinBudgetWonCheck() {
        dataSource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "ALTER TABLE operator_strategy DROP CONSTRAINT operator_strategy_min_budget_won_check",
                )
            }
        }
    }

    // verifier r4 MEDIUM-3 — CHECK 를 우회해도 코드 층(longValueExact)이 단독으로 막는가.
    // D-6F1-9 의 「둘 다 있어야 닫힌다」에서 코드 층이 실제로 방어선인지 겨눈다(CHECK 가 있으면
    // 범위 밖 값이 코드 층까지 갈 길이 없어, 그 층만 도는 test 가 따로 필요하다).
    @Test
    fun `verifier r4 MEDIUM-3 회귀 — CHECK 를 우회해도 코드 층이 예산 won 오버플로를 막는다`() {
        dropMinBudgetWonCheck()
        // Codex 원 재현값 — toLong()이 이 값을 예외 없이 양수 5로 접는다(원 결함과 동일 입력).
        insertRawStrategy(BigDecimal("18446744073709551621"))

        val repository = JdbcStrategyRepository(dataSource(), testPolicy())
        shouldThrow<IllegalStateException> { repository.load() }
    }
}
