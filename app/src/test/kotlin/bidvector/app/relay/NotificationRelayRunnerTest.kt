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
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.DefaultApplicationArguments
import java.time.LocalDate

private const val RUN_LIMIT = 7

/**
 * 러너 **본문**을 직접 돌린다(cr L-2·L-4) — 앞 판은 `run()` 을 어느 test 도 부르지 않아
 * 「종료 코드가 `terminate` 로 그 값으로 간다」와 「예외 경로가 사유 토큰을 남긴다」가 둘 다
 * 미측정이었고, `RelayExitCode.FAILED` 는 **아무 코드도 만들지 않는 죽은 열거 값**이었다.
 *
 * 선례는 `CollectionRunnerTest` 다 — fake 로그·종료로 `run(DefaultApplicationArguments())`
 * 를 그대로 돌린다. `NotificationRelayRun` 은 `open` 이 아니라 상속으로 대역을 만들 수 없어
 * **실패만 거동으로** 잴 수 있다(연결 불가 `DataSource` 로 예외 경로). 성공 경로의 종료 코드
 * 매핑은 `RelayExitCodeTest` 가, production 조립의 실 DB 거동은 `RelayDatabaseTest` 가 든다.
 */
class NotificationRelayRunnerTest {
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
