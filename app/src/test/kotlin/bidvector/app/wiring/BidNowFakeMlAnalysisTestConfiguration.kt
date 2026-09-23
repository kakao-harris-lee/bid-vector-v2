package bidvector.app.wiring

import bidvector.decision.MlUnavailableReason
import bidvector.decision.UnitScore
import bidvector.procurement.Notice
import bidvector.workflow.evaluation.MlAnalysisOutcome
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.PredictionEvidence
import bidvector.workflow.event.CorrelationId
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.math.BigDecimal

/**
 * D-6A3-17(a) 거동 test 전용 배선(검토 라운드 1 HIGH-1 둘째 다리, code-reviewer MEDIUM ·
 * verifier M3 동시 해소) — `BidNow`를 내는 [MlAnalysisPort] fake 를 실 `EvaluationDryRunFactory`
 * 에 꽂아 `wouldNotifyNoticeIds`·`bidNowNoticeIds`가 실제로 채워지는 경로를 잰다.
 *
 * **production 배선에 새 주입 자리를 열지 않는다.** `@Profile("evaluation-bidnow-fake")`
 * 로 잠가 이 profile 을 활성화하는 test([EvaluationDryRunBidNowE2ETest]) 밖에서는 이 class 가
 * 컴포넌트 스캔(`BidVectorApplication`의 `bidvector.app` 하위 전부)에 걸려도 `@Bean` 이 하나도
 * 등록되지 않는다(Spring 조건 평가가 `@Profile` 불일치 시 `@Configuration` 자체를 건너뛴다) —
 * `EvaluationWiring.mlAnalysisPort()`(`UnavailableMlAnalysis` 고정, D-6A3-2)는 다른 모든 test
 * 에서 그대로 유일한 배선이다. `@Primary`는 by-type 주입(`EvaluationWiring.evaluation
 * DryRunFactory`의 `mlAnalysisPort: MlAnalysisPort` 파라미터)의 동점을 이 fake 쪽으로 깬다 —
 * `EvaluationWiring`의 `@Bean` 메서드 자체는 무변경(같은 이름 재정의가 아니라 별도 이름의
 * 경쟁 빈).
 */
@TestConfiguration
@Profile("evaluation-bidnow-fake")
open class BidNowFakeMlAnalysisTestConfiguration {
    @Bean
    @Primary
    open fun fakeAlwaysBidNowMlAnalysisPort(): MlAnalysisPort = AlwaysBidNowMlAnalysisPort()
}

/**
 * priority 0.9(테스트 전략의 `bidNowThreshold`인 0.9 이상, `VerdictLadder.priorityBidNowOutcome`)
 * 를 내 확정 `Verdict.BidNow`를 낳는 fake — `evidence`는 예측 자체를 재지 않는 이 test 의
 * 관심 밖이라 `NotPredicted` 고정값을 싣는다(workflow 모듈 test fixture `bidNowAnalysis()`와
 * 같은 관례 — 모듈 경계로 재사용은 못 하지만 형태는 그대로 옮긴다).
 */
private class AlwaysBidNowMlAnalysisPort : MlAnalysisPort {
    override suspend fun analyze(
        notice: Notice,
        correlationId: CorrelationId,
    ): MlAnalysisOutcome =
        MlAnalysisOutcome.Analyzed(
            priorityScore = UnitScore(BigDecimal("0.9")),
            probabilityScore = null,
            matchedScore = null,
            evidence = PredictionEvidence.NotPredicted(MlUnavailableReason.ScoreNotProvided),
        )
}
