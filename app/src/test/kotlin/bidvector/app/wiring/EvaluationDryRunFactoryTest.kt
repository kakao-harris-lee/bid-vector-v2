package bidvector.app.wiring

import bidvector.adapters.evaluation.InvalidEvaluationRequestException
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
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.WatchSubjectOutcome
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.event.CorrelationId
import bidvector.workflow.strategy.AppliedStrategy
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/** `load()` 호출 횟수를 세는 fake — D-6A3-5 「전략은 요청당 정확히 한 번」 계수 test 전용. */
private class CountingStrategyRepository(
    private val strategy: bidvector.strategy.OperatorStrategy,
) : StrategyRepository {
    var loadCount: Int = 0
        private set

    override fun load(): bidvector.strategy.OperatorStrategy {
        loadCount++
        return strategy
    }

    override fun save(applied: AppliedStrategy) {
        error("이 test 는 save() 를 쓰지 않는다")
    }
}

/** `forRequest()` 자체는 이 넷을 부르지 않는다(use case 를 짓기만 한다) — 호출되면 실패시켜 그 사실을 잠근다. */
private class UnreachablePort : CandidateSourcePort, WatchSubjectPort, LicenseGatePort, MlAnalysisPort {
    override fun openCandidates(): List<Notice> = error("forRequest() 는 이 port 를 부르지 않는다")

    override fun subjectFor(notice: Notice): WatchSubjectOutcome = error("forRequest() 는 이 port 를 부르지 않는다")

    override fun verdictFor(notice: Notice): LicenseVerdict = error("forRequest() 는 이 port 를 부르지 않는다")

    override suspend fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome = error("forRequest() 는 이 port 를 부르지 않는다")
}

private class UnreachableCorrelationIdFactory : CorrelationIdFactory {
    override fun newId(): CorrelationId = error("forRequest() 는 이 port 를 부르지 않는다")
}

private class UnreachableClock : Clock {
    override fun now(): Instant = error("forRequest() 는 이 port 를 부르지 않는다")
}

private fun strategyWithCap(maxActiveBids: Int?): bidvector.strategy.OperatorStrategy {
    val policy = STRATEGY_POLICY.resolve(LocalDate.now()) as Resolution.Resolved<StrategyPolicyData>
    val draft = StrategyDraft(focusCategories = listOf("CAT-1"), maxActiveBids = maxActiveBids)
    return when (val result = validate(draft, StrategyRevision(1), policy)) {
        is StrategyValidation.Valid -> result.strategy
        is StrategyValidation.Invalid -> error("test fixture가 유효하지 않다: ${result.violations}")
    }
}

private fun factoryWith(repository: StrategyRepository): EvaluationDryRunFactory {
    val unreachable = UnreachablePort()
    return EvaluationDryRunFactory(
        strategyRepository = repository,
        candidateSource = unreachable,
        watchSubjects = unreachable,
        licenseGate = unreachable,
        mlAnalysis = unreachable,
        correlationIds = UnreachableCorrelationIdFactory(),
        clock = UnreachableClock(),
    )
}

/**
 * D-6A3-5·D-6A3-4 — [EvaluationDryRunFactory.forRequest]의 순서·계수 계약을 use case 를
 * 실제로 구동하지 않고(위 `UnreachablePort` 넷이 호출되면 스스로 실패한다) 직접 잰다.
 * `EvaluationDryRunControllerTest`·`EvaluationDryRunE2ETest`는 HTTP 층 전체를 거쳐 같은
 * 분기를 간접으로 재확인한다 — 이 test는 그 판정이 어디서 나는지(팩토리 자신)를 좁혀 잠근다.
 */
class EvaluationDryRunFactoryTest {
    @Test
    fun `forRequest 는 전략을 정확히 한 번 읽는다`() {
        val repository = CountingStrategyRepository(strategyWithCap(10))

        factoryWith(repository).forRequest(3)

        repository.loadCount shouldBe 1
    }

    @Test
    fun `maxActiveBids 가 없으면 MaxActiveBidsNotConfiguredException 이다`() {
        val repository = CountingStrategyRepository(strategyWithCap(null))

        shouldThrow<MaxActiveBidsNotConfiguredException> { factoryWith(repository).forRequest(0) }
    }

    @Test
    fun `currentActiveBids 가 음수면 저장소 상태보다 먼저 거부한다 — load 는 아예 안 부른다`() {
        // strategy 자체는 maxActiveBids 가 없어도(둘 다 실패할 조건이어도) 400 이 먼저다.
        val repository = CountingStrategyRepository(strategyWithCap(null))

        shouldThrow<InvalidEvaluationRequestException> { factoryWith(repository).forRequest(-1) }

        repository.loadCount shouldBe 0
    }
}
