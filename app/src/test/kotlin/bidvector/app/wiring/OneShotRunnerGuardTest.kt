package bidvector.app.wiring

import bidvector.app.relay.NotificationRelayRunner
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.util.function.Supplier
import javax.sql.DataSource

/** relay 러너를 켜는 최소 설정 — 값 자체는 중요하지 않다(기동이 거부되지 않을 만큼만). */
private val RELAY_ON =
    arrayOf(
        "bidvector.relay.mode=once",
        "bidvector.relay.environment=development",
        "bidvector.relay.owner=relay-operator",
        "bidvector.relay.channel=telegram",
        "bidvector.relay.claim-limit=50",
    )

/** 평가 커밋 러너를 켜는 최소 설정. */
private val COMMIT_ON =
    arrayOf(
        "bidvector.evaluation.mode=once",
        "bidvector.evaluation.candidate-cap=100",
        "bidvector.evaluation.commit.current-active-bids=0",
    )

/**
 * **PR #63 finding 1** — 일회 러너 둘을 한 프로세스에 켜면 **기동이 실패한다.**
 *
 * 왜 이것이 결함이었나: 일회 러너는 끝나면 종료 코드를 내고 JVM 을 끝낸다. 둘을 켜면 먼저
 * 끝난 쪽이 JVM 을 끝내고 나머지는 **조용히 안 돈다** — cron 은 둘을 돌렸다고 믿는다. 이 guard
 * 가 그 배포를 기동 자리에서 막는다.
 *
 * `EvaluationCommitWiring` 의 협력자 빈(전략 저장소·후보 원천·ML port 등)을 **주지 않는다** —
 * guard 는 빈 **정의**를 세므로 러너를 하나도 만들지 않고 실패한다. 그 사실이 이 test 가
 * 협력자 없이 서는 이유이고, 동시에 「생성 효과가 실패 전에 일어나지 않는다」의 증거다.
 */
class OneShotRunnerGuardTest {
    private fun refresh(vararg properties: String): Throwable? {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(OneShotRunnerGuard::class.java, RelayWiring::class.java, EvaluationCommitWiring::class.java)
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        return try {
            context.refresh()
            null
        } catch (
            @Suppress("TooGenericExceptionCaught") thrown: Exception,
        ) {
            thrown
        } finally {
            context.close()
        }
    }

    @Test
    fun `일회 러너 둘을 함께 켜면 기동에 실패한다`() {
        val failure = refresh(*RELAY_ON, *COMMIT_ON)

        failure shouldNotBe null
        val trace = failure!!.stackTraceToString()
        trace shouldContain "일회 러너는 한 프로세스에 하나만"
        // 이름을 싣는다 — 어느 둘이 켜졌는지가 조치의 입력이다.
        trace shouldContain "2개"
    }

    @Test
    fun `relay 하나만 켜면 기동한다`() {
        refresh(*RELAY_ON) shouldBe null
    }

    @Test
    fun `아무 러너도 켜지 않으면 기동한다 — guard 는 늘 올라오지만 아무것도 하지 않는다`() {
        refresh() shouldBe null
    }

    /**
     * guard 가 **러너의 종류를 모른다**는 것 — `ApplicationRunner` 하나를 손으로 더하면 relay
     * 하나와 합쳐 둘이 되어 같은 자리에서 걸린다. 뒤에 어느 레인이 러너를 더해도 이 guard 가
     * 받는다(수집 레인 코드를 건드리지 않고 그 조합까지 막는 근거다).
     */
    @Test
    fun `종류를 모른다 — 임의의 ApplicationRunner 하나로도 둘이 된다`() {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*RELAY_ON).applyTo(context)
        context.register(OneShotRunnerGuard::class.java, RelayWiring::class.java)
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        context.registerBean(ApplicationRunner::class.java, Supplier { ApplicationRunner { } })

        val failure =
            try {
                context.refresh()
                null
            } catch (
                @Suppress("TooGenericExceptionCaught") thrown: Exception,
            ) {
                thrown
            } finally {
                context.close()
            }

        failure shouldNotBe null
        failure!!.stackTraceToString() shouldContain "일회 러너는 한 프로세스에 하나만"
    }

    /** relay 하나만 켠 배포에서 러너 빈이 실제로 하나다 — 위 셋의 전제가 맞는지 재확인. */
    @Test
    fun `relay 하나만 켜면 러너 빈이 하나다`() {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*RELAY_ON).applyTo(context)
        context.register(OneShotRunnerGuard::class.java, RelayWiring::class.java)
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        context.refresh()

        try {
            context.getBeanNamesForType(NotificationRelayRunner::class.java).size shouldBe 1
            context.getBeanNamesForType(ApplicationRunner::class.java).size shouldBe 1
        } finally {
            context.close()
        }
    }
}
