package bidvector.app.wiring

import bidvector.adapters.evaluation.EvaluationCommitRuns
import bidvector.adapters.persistence.ReleaseIdentifier
import bidvector.app.evaluation.EvaluationCommitRunner
import bidvector.app.http.SequentialCorrelationIdFactory
import bidvector.app.http.TestStrategyRepository
import bidvector.app.http.freshStrategy
import bidvector.procurement.Notice
import bidvector.qualification.LicenseVerdict
import bidvector.sharedkernel.AccountId
import bidvector.sharedkernel.Resolution
import bidvector.strategy.STRATEGY_POLICY
import bidvector.strategy.StrategyDraft
import bidvector.strategy.StrategyPolicyData
import bidvector.strategy.StrategyRevision
import bidvector.strategy.StrategyValidation
import bidvector.strategy.validate
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.util.function.Supplier
import javax.sql.DataSource

/** M9/OPS-10 — 배선 test 가 꽂는 릴리스 식별자(값은 중요하지 않고 **있다는 사실**이 중요하다). */
private val WIRING_TEST_RELEASE = ReleaseIdentifier("wiring-test-release")

/** 배선 test 가 run 을 요구하는 계정(M15/15A) — 저장소 대역이 계정을 보지 않으므로 값은 아무거나다. */
private val WIRING_TEST_ACCOUNT = AccountId(1)

private val COMMIT_NOW: Instant = Instant.parse("2026-10-06T00:00:00Z")

/**
 * D-6F10-18 ⑧·D-6F10-20 — 평가 커밋 배선의 켜짐 조건과 fail-closed.
 *
 * `EvaluationProperties`(prefix `bidvector.evaluation`)가 **무편집**이라는 사실이 여기서
 * 측정된다: `mode` 는 그 타입이 선언하지 않고 `@ConditionalOnProperty` 가 Environment 에서
 * 직접 읽는다(수집 선례). 그래서 `candidate-cap` 만 대고 `mode` 를 대지 않으면 이 배선이
 * 올라오지 않는다.
 */
class EvaluationCommitWiringTest {
    private class Booted(
        val context: AnnotationConfigApplicationContext,
        val failure: Throwable?,
    )

    private fun boot(
        vararg properties: String,
        strategyRepository: StrategyRepository = TestStrategyRepository(strategyWithCap()),
    ): Booted {
        val context = AnnotationConfigApplicationContext()
        TestPropertyValues.of(*properties).applyTo(context)
        context.register(EvaluationCommitWiring::class.java)
        context.registerBean(DataSource::class.java, Supplier { PGSimpleDataSource() })
        // M9/OPS-10 — 행을 쓰는 조립은 릴리스 식별자를 요구한다(출하는 `ReleaseWiring` 이 낸다).
        context.registerBean(ReleaseIdentifier::class.java, Supplier { WIRING_TEST_RELEASE })
        context.registerBean(StrategyRepository::class.java, Supplier { strategyRepository })
        context.registerBean(CandidateSourcePort::class.java, Supplier { CandidateSourcePort { emptyList() } })
        context.registerBean(
            WatchSubjectPort::class.java,
            Supplier { WatchSubjectPort { WatchSubjectOutcome.Unavailable } },
        )
        context.registerBean(LicenseGatePort::class.java, Supplier { LicenseGatePort { _, _ -> eligible() } })
        context.registerBean(MlAnalysisPort::class.java, Supplier { alwaysUnavailableMl() })
        context.registerBean(
            CorrelationIdFactory::class.java,
            Supplier<CorrelationIdFactory> { SequentialCorrelationIdFactory() },
        )
        context.registerBean(Clock::class.java, Supplier { Clock { COMMIT_NOW } })
        val failure =
            try {
                context.refresh()
                null
            } catch (
                @Suppress("TooGenericExceptionCaught") thrown: Exception,
            ) {
                thrown
            }
        return Booted(context, failure)
    }

    private fun <T> withBoot(
        vararg properties: String,
        strategyRepository: StrategyRepository = TestStrategyRepository(strategyWithCap()),
        block: (Booted) -> T,
    ): T {
        val booted = boot(*properties, strategyRepository = strategyRepository)
        return try {
            block(booted)
        } finally {
            booted.context.close()
        }
    }

    private fun validProperties(currentActiveBids: String = "0") =
        arrayOf(
            "bidvector.evaluation.mode=once",
            "bidvector.evaluation.candidate-cap=10",
            "bidvector.evaluation.commit.current-active-bids=$currentActiveBids",
        )

    @Test
    fun `mode 가 없으면 러너 빈이 없다 — 기본 꺼짐`() {
        withBoot("bidvector.evaluation.candidate-cap=10") { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeanNamesForType(ApplicationRunner::class.java)
                .toList()
                .shouldBeEmpty()
            booted.context
                .getBeanNamesForType(EvaluationCommitRunner::class.java)
                .toList()
                .shouldBeEmpty()
        }
    }

    @Test
    fun `mode 가 once 이고 설정이 유효하면 러너 빈이 하나다`() {
        withBoot(*validProperties()) { booted ->
            booted.failure shouldBe null
            booted.context
                .getBeanNamesForType(EvaluationCommitRunner::class.java)
                .toList()
                .size shouldBe 1
        }
    }

    /**
     * `candidate-cap` 은 상시 필수다(`EvaluationProperties` 가 기본값을 두지 않는다) — 커밋
     * 러너를 켠 배포도 그 값을 대야 한다(알려진 제한, D-6F10-18 ⑨).
     */
    @Test
    fun `candidate-cap 이 없으면 기동에 실패한다`() {
        val properties = validProperties().filterNot { it.startsWith("bidvector.evaluation.candidate-cap") }

        withBoot(*properties.toTypedArray()) { booted ->
            booted.failure shouldNotBe null
        }
    }

    @Test
    fun `current-active-bids 가 없으면 기동에 실패한다 — 0 을 지어내지 않는다`() {
        val properties = validProperties().filterNot { it.contains("current-active-bids") }

        withBoot(*properties.toTypedArray()) { booted ->
            booted.failure shouldNotBe null
        }
    }

    @Test
    fun `current-active-bids 가 음수면 기동에 실패한다`() {
        withBoot(*validProperties(currentActiveBids = "-1")) { booted ->
            booted.failure shouldNotBe null
            booted.failure!!.stackTraceToString() shouldContain "current-active-bids"
        }
    }

    /**
     * R1-M-4 / cr L-11 — **전략을 run 당 정확히 한 번 읽는다.** 앞 판은 배선이 한 번(상한을
     * 뽑는 자리) 읽고 use case 가 `evaluate()` 진입에서 또 읽어, 두 스냅샷이 다른 개정에서 올 수
     * 있었다(payload 에 적힌 개정과 실제로 쓴 상한이 어긋난다 — 6D-2 재현 등식의 입력 불일치).
     *
     * 세는 방법: 저장소 대역이 `load()` 호출을 센다. 배선이 빈을 만든 뒤 use case 가 몇 번 더
     * 읽는지는 `evaluate()` 를 돌려야 보이지만, **배선 단계의 읽기가 정확히 한 번**이고 그 값이
     * 고정 저장소로 넘어간다는 것은 여기서 잴 수 있다 — 고정하지 않으면 그 뒤 읽기가 저장소로
     * 다시 간다.
     */
    @Test
    fun `배선과 use case 를 합쳐 전략 읽기가 run 당 한 번이다`() {
        val counting = CommitCountingStrategyRepository(strategyWithCap())

        withBoot(*validProperties(), strategyRepository = counting) { booted ->
            booted.failure shouldBe null
            // **`evaluate()` 까지 돌려야 센다.** 배선 단계 읽기만 세면 고정 여부와 무관하게 늘
            // 1 이다(실측: 고정을 걷는 변이가 초록이었다) — 두 번째 읽기는 use case 안에 있다.
            // 후보가 빈 목록이라 DB·ML 에는 닿지 않는다.
            //
            // M15/15A — run 은 이제 **계정마다** 선다. 공장이 그 계정의 전략을 한 번 읽어 고정하고
            // 넘기므로, 「run 당 한 번」의 run 이 `forAccount` 가 낸 것 하나다.
            val runs = booted.context.getBean(EvaluationCommitRuns::class.java)
            val run =
                runs.forAccount(WIRING_TEST_ACCOUNT, channelHolder = WIRING_TEST_ACCOUNT)
                    ?: error("여력 상한이 있는 전략인데 run 이 없다")
            runBlocking { run.evaluate() }

            counting.loads shouldBe 1
        }
    }

    /**
     * dry-run 과 같은 fail-closed — 여력 상한이 없는 전략으로는 커밋 run 을 **만들지 않는다**.
     *
     * **M15/15A 가 뒤집은 것은 범위이지 결과가 아니다**(주장을 지우지 않고 바꾼다). 앞 판은 상한
     * 미설정이 **기동 실패**였다. 계정이 여럿이면 한 계정의 미설정이 다른 계정의 평가까지 멈추므로,
     * 이제는 **그 계정의 run 이 없다**(`null`)가 되고 러너가 그 수를 센다. 지어낸 상한으로
     * 평가하지 않는다는 결과는 같다.
     */
    @Test
    fun `전략에 여력 상한이 없으면 그 계정의 run 이 없다 — 기동은 멈추지 않는다`() {
        withBoot(
            *validProperties(),
            strategyRepository = TestStrategyRepository(freshStrategy()),
        ) { booted ->
            booted.failure shouldBe null
            booted.context
                .getBean(EvaluationCommitRuns::class.java)
                .forAccount(WIRING_TEST_ACCOUNT, channelHolder = WIRING_TEST_ACCOUNT) shouldBe null
        }
    }

    /**
     * **출하 기본값 둘을 잠근다**(r3 verifier L-3). 두 상한은 설정에 없어도 서야 하고(미설정이
     * 「무제한」을 뜻하면 안 된다) 그 수가 바로 비용 상한이다 — 값이 조용히 커지면 상한이 사라진
     * 것과 같다. 가입 상한에서 같은 구멍을 `SignUpThrottleWiringTest` 가 닫았다.
     */
    @Test
    fun `평가 상한 둘은 설정이 없어도 50 과 3 이다`() {
        withBoot(*validProperties()) { booted ->
            val properties = booted.context.getBean(EvaluationProperties::class.java)
            properties.maxAccountsPerRun shouldBe 50
            properties.maxConsecutiveAccountFailures shouldBe 3
        }
    }

    /** 접두 바인딩도 함께 잠근다 — 키 이름이 바뀌면 운영자가 준 값이 조용히 무시된다. */
    @Test
    fun `설정이 주어지면 그 값이 바인딩된다`() {
        withBoot(
            *validProperties(),
            "bidvector.evaluation.max-accounts-per-run=7",
            "bidvector.evaluation.max-consecutive-account-failures=2",
        ) { booted ->
            val properties = booted.context.getBean(EvaluationProperties::class.java)
            properties.maxAccountsPerRun shouldBe 7
            properties.maxConsecutiveAccountFailures shouldBe 2
        }
    }

    /** 0 은 상한이 아니라 「아무도 평가하지 않는다」다 — 지어내지 않고 기동을 멈춘다. */
    @Test
    fun `평가 상한이 1 미만이면 기동에 실패한다`() {
        withBoot(*validProperties(), "bidvector.evaluation.max-accounts-per-run=0") { booted ->
            booted.failure shouldNotBe null
            booted.failure!!.stackTraceToString() shouldContain "max-accounts-per-run"
        }
        withBoot(*validProperties(), "bidvector.evaluation.max-consecutive-account-failures=0") { booted ->
            booted.failure shouldNotBe null
            booted.failure!!.stackTraceToString() shouldContain "max-consecutive-account-failures"
        }
    }
}

private fun strategyWithCap(maxActiveBids: Int = 10) =
    when (
        val result =
            validate(
                StrategyDraft(focusCategories = listOf("CAT-1"), maxActiveBids = maxActiveBids),
                StrategyRevision(1),
                STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>,
            )
    ) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test fixture 가 유효하지 않다: ${result.violations}")
    }

private fun eligible(): LicenseVerdict = LicenseVerdict.Eligible(emptySet())

private fun alwaysUnavailableMl(): MlAnalysisPort =
    MlAnalysisPort { _, _: Notice, _ -> MlAnalysisOutcome.SimilarityProjectionNotReady }

/**
 * `load()` 호출을 세는 저장소 대역 — 「run 당 한 번」의 유일한 증거다(R1-M-4).
 *
 * 같은 패키지의 `EvaluationDryRunFactoryTest` 에도 같은 일을 하는 private fake 가 있다 —
 * 둘 다 `private` 이라 공유할 수 없고(파일 스코프), 이름만 달리 둔다. 공용으로 올리면 두
 * test 가 한 대역의 변경에 함께 묶인다.
 */
private class CommitCountingStrategyRepository(
    private val strategy: bidvector.strategy.OperatorStrategy,
) : StrategyRepository {
    var loads = 0
        private set

    override fun load(account: AccountId): bidvector.strategy.OperatorStrategy {
        loads += 1
        return strategy
    }

    override fun save(
        account: AccountId,
        applied: bidvector.workflow.strategy.AppliedStrategy,
    ) = error("전략을 쓰지 않는다")
}
