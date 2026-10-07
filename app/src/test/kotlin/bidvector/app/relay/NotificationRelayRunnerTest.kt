package bidvector.app.relay

import bidvector.adapters.relay.NotificationRelayRun
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.sharedkernel.Resolution
import bidvector.workflow.notification.Channel
import bidvector.workflow.notification.NOTIFICATION_DELIVERY_POLICY
import bidvector.workflow.notification.NotificationDeliveryPolicyData
import bidvector.workflow.notification.RelayTarget
import bidvector.workflow.notification.RuntimeEnvironment
import bidvector.workflow.strategy.OperatorId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.DefaultApplicationArguments
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.LocalDate
import javax.sql.DataSource

private const val RUN_LIMIT = 7
private const val POSTGRES_IMAGE = "postgres:16.4"

/**
 * 러너 **본문**을 직접 돌린다(cr L-2·L-4) — 앞 판은 `run()` 을 어느 test 도 부르지 않아
 * 「종료 코드가 `terminate` 로 그 값으로 간다」와 「예외 경로가 사유 토큰을 남긴다」가 둘 다
 * 미측정이었고, `RelayExitCode.FAILED` 는 **아무 코드도 만들지 않는 죽은 열거 값**이었다.
 *
 * 선례는 `CollectionRunnerTest` 다 — fake 로그·종료로 `run(DefaultApplicationArguments())`
 * 를 그대로 돌린다.
 *
 * **성공 경로도 여기서 잰다(R2-L-1, cr R-5).** `NotificationRelayRun` 은 `open` 이 아니라
 * 대역을 만들 수 없어, 앞 판은 예외 경로만 재고 「보고서 → 종료 코드 → `terminate`」 사슬을
 * `RelayExitCodeTest`(사상만) 와 `RelayDatabaseTest`(use case 만) 로 나눠 두었다 — 러너가 그
 * 둘을 **잇는다**는 것은 어느 쪽도 재지 않았다. 지금은 실 PostgreSQL 위에서 억제 환경으로
 * 돌려 `[4]` 와 마침 줄을 함께 잰다.
 *
 * 표가 필요 없는 이유: 억제 판정은 임대 획득·guard 확인 **뒤·claim 앞**이라 이 run 은
 * advisory lock 과 `SELECT 1` 만 돌린다. 그래서 Flyway 를 돌리지 않는다.
 */
class NotificationRelayRunnerTest {
    companion object {
        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer(DockerImageName.parse(POSTGRES_IMAGE))
                .withDatabaseName("bidvector_relay_runner_test")
                .withUsername("bidvector_admin")
                .withPassword("bidvector_test_only")
                .also { it.start() }

        @JvmStatic
        @AfterAll
        fun stopContainer() {
            postgres.stop()
        }

        /** 임대만 쓰는 연결 — 표가 없어도 `pg_try_advisory_lock` 은 돈다. */
        fun containerDataSource(): DataSource =
            PGSimpleDataSource().apply {
                setUrl(postgres.jdbcUrl)
                user = postgres.username
                password = postgres.password
            }
    }

    private class RecordingLog : CollectionLog {
        val written = mutableListOf<String>()

        override fun write(line: String) {
            written += line
        }
    }

    private class RecordingTermination : CollectionTermination {
        val codes = mutableListOf<Int>()

        override fun terminate(exitCode: Int) {
            codes += exitCode
        }
    }

    /**
     * 예외 경로 — **Spring 에 던지지 않고** 사유 토큰과 `FAILED`(1) 를 낸다. `DataSource` 가
     * 연결 불가라 relay 는 임대 획득에서 `SQLException` 을 낸다.
     */
    @Test
    fun `relay 가 던지면 사유 토큰을 남기고 FAILED 1 로 종료한다`() {
        val log = RecordingLog()
        val termination = RecordingTermination()
        val runner = NotificationRelayRunner(unreachableRelayRun(), RUN_LIMIT, log, termination)

        runner.run(DefaultApplicationArguments())

        termination.codes shouldBe listOf(RelayExitCode.FAILED.value)
        val lines = log.written.joinToString("\n")
        lines shouldContain "relay start limit=$RUN_LIMIT"
        lines shouldContain "relay failed cause="
    }

    /**
     * 성공 경로(R2-L-1) — 억제 환경의 run 은 `Skipped(EnvironmentSuppressed)` 이고 러너가
     * 그것을 **`[4]` 로 옮긴다.** 종료 코드 목록이 정확히 한 칸인 것도 단언한다(두 번 종료를
     * 부르는 구현이 있으면 붉어진다).
     */
    @Test
    fun `억제 환경의 run 은 ENV_SUPPRESSED 4 로 끝나고 마침 줄을 남긴다`() {
        val log = RecordingLog()
        val termination = RecordingTermination()
        val runner = NotificationRelayRunner(suppressedRelayRun(containerDataSource()), RUN_LIMIT, log, termination)

        runner.run(DefaultApplicationArguments())

        termination.codes shouldBe listOf(RelayExitCode.ENV_SUPPRESSED.value)
        val lines = log.written.joinToString("\n")
        lines shouldContain "relay start limit=$RUN_LIMIT"
        lines shouldContain "exit=${RelayExitCode.ENV_SUPPRESSED.value}"
        log.written.none { it.startsWith("relay failed") } shouldBe true
    }

    /**
     * 정제된 원인 코드만 나간다 — 원 예외 메시지(접속 문자열·호스트)는 로그에 없다. SQL 예외는
     * 클래스 이름 + SQLSTATE 5자리까지다.
     */
    @Test
    fun `실패 줄은 접속 상세를 싣지 않는다`() {
        val log = RecordingLog()
        val runner = NotificationRelayRunner(unreachableRelayRun(), RUN_LIMIT, log, RecordingTermination())

        runner.run(DefaultApplicationArguments())

        val failureLine = log.written.single { it.startsWith("relay failed") }
        failureLine shouldContain "sqlState="
        failureLine shouldNotContain UNREACHABLE_HOST
        failureLine shouldNotContain "jdbc:"
    }
}

private const val UNREACHABLE_HOST = "127.0.0.1"
private const val UNREACHABLE_PORT = 1

/**
 * 연결이 되지 않는 `DataSource` 로 만든 production 조립 — 조립 자체는 질의를 돌리지 않으므로
 * 생성은 성공하고, `relay()` 의 첫 질의(임대 획득)에서 던진다. 그 던짐이 이 test 의 입력이다.
 */
private fun unreachableRelayRun(): NotificationRelayRun =
    NotificationRelayRun(
        dataSource = unreachableDataSource(),
        target = RelayTarget(OperatorId("runner-test-owner"), Channel.Telegram),
        environment = RuntimeEnvironment.Staging,
        policy = relayRunnerTestPolicy(),
    )

/**
 * 실 DB 위의 production 조립 — 환경이 `Staging` 이라 정책표가 `DryRun` 을 붙이고, relay 는
 * 임대를 쥔 뒤 **claim 전에** 억제로 끝난다. 발송 축을 바꿔치울 필요가 없다(한 행도 집지
 * 않으므로 sender 가 불리지 않는다).
 */
private fun suppressedRelayRun(dataSource: DataSource): NotificationRelayRun =
    NotificationRelayRun(
        dataSource = dataSource,
        target = RelayTarget(OperatorId("runner-test-owner"), Channel.Telegram),
        environment = RuntimeEnvironment.Staging,
        policy = relayRunnerTestPolicy(),
    )

private fun unreachableDataSource(): PGSimpleDataSource =
    PGSimpleDataSource().apply {
        setServerNames(arrayOf(UNREACHABLE_HOST))
        setPortNumbers(intArrayOf(UNREACHABLE_PORT))
        databaseName = "no-such-db"
        user = "no-such-user"
        connectTimeout = 1
        loginTimeout = 1
    }

/** 환경이 `Live` 가 아니어도 임대 획득이 **먼저**이므로 예외 경로에 닿는다. */
private fun relayRunnerTestPolicy(): NotificationDeliveryPolicyData {
    val resolution = NOTIFICATION_DELIVERY_POLICY.resolve(LocalDate.of(2026, 10, 7))
    check(resolution is Resolution.Resolved) { "정책이 해소되지 않았다: $resolution" }
    return resolution.value
}
