package bidvector.app.wiring

import bidvector.app.evaluation.EvaluationCommitRunner
import bidvector.app.http.SequentialCorrelationIdFactory
import bidvector.app.http.TestStrategyRepository
import bidvector.app.http.freshStrategy
import bidvector.procurement.Notice
import bidvector.qualification.LicenseVerdict
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
import org.junit.jupiter.api.Test
import org.postgresql.ds.PGSimpleDataSource
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.util.function.Supplier
import javax.sql.DataSource

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
        context.registerBean(StrategyRepository::class.java, Supplier { strategyRepository })
        context.registerBean(CandidateSourcePort::class.java, Supplier { CandidateSourcePort { emptyList() } })
        context.registerBean(
            WatchSubjectPort::class.java,
            Supplier { WatchSubjectPort { WatchSubjectOutcome.Unavailable } },
        )
        context.registerBean(LicenseGatePort::class.java, Supplier { LicenseGatePort { eligible() } })
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
    fun `배선은 전략을 한 번만 읽고 그 값을 고정해 넘긴다`() {
        val counting = CommitCountingStrategyRepository(strategyWithCap())

        withBoot(*validProperties(), strategyRepository = counting) { booted ->
            booted.failure shouldBe null
            counting.loads shouldBe 1
        }
    }

    /** dry-run 과 같은 fail-closed — 여력 상한이 없는 전략으로는 커밋 run 을 만들지 않는다. */
    @Test
    fun `전략에 여력 상한이 없으면 기동에 실패한다`() {
        withBoot(
            *validProperties(),
            strategyRepository = TestStrategyRepository(freshStrategy()),
        ) { booted ->
            booted.failure shouldNotBe null
            booted.failure!!.stackTraceToString() shouldContain "maxActiveBids"
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
    MlAnalysisPort { _: Notice, _ -> MlAnalysisOutcome.SimilarityProjectionNotReady }

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

    override fun load(): bidvector.strategy.OperatorStrategy {
        loads += 1
        return strategy
    }

    override fun save(applied: bidvector.workflow.strategy.AppliedStrategy) = error("전략을 쓰지 않는다")
}
