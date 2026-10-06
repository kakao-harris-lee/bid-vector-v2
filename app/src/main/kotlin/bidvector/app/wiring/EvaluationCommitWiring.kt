package bidvector.app.wiring

import bidvector.adapters.evaluation.EvaluationCommitRun
import bidvector.adapters.evaluation.RequestCapacityPort
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.evaluation.EvaluationCommitRunner
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import javax.sql.DataSource

/**
 * 평가 커밋 러너의 입력(D-6F10-20) — **기본값 없음**.
 *
 * [currentActiveBids] 가 설정에서 오는 이유(`OPEN-6F10-CAPACITY-SOURCE`): 이 저장소에
 * **실 진행 입찰 계수기가 없다**. `CapacityPort` 의 유일한 production 구현
 * [RequestCapacityPort] 는 dry-run 요청 본문의 값을 그대로 옮기고, 그 값의 정직성은 그
 * 경계 밖이라고 선언돼 있다(D-6A3-5).
 *
 * **6A-3 운영자 결정 ③ 은 「현재값 0 고정」을 기각하고 「호출자가 댄다」를 택했다** — 그
 * 결정에서 호출자는 dry-run 요청을 보내는 주체였고, **일회 러너에서 그 호출자는 설정을 주는
 * 운영자**다. 그러므로 이 키는 그 결정의 다른 적용이지 새 결정이 아니다. 미설정은 기동
 * 실패다(지어내지 않는다) — 여력을 0 으로 지어내면 용량 보류(`CapacityHold`) 판정이 조용히
 * 사라진다.
 *
 * 후보 상한은 **기존 `bidvector.evaluation.candidate-cap` 을 그대로 쓴다**(둘째 상한 금지,
 * D-6F10-20) — 그 값은 `EvaluationProperties` 가 이미 바인딩하고 상시 필수다.
 */
@ConfigurationProperties(prefix = "bidvector.evaluation.commit")
data class EvaluationCommitProperties(
    val currentActiveBids: Int,
)

/**
 * 일회성 평가 커밋 배선(D-6F10-18 ⑧) — **`bidvector.evaluation.mode=once` 일 때만** 올라온다.
 * `EvaluationProperties`(prefix `bidvector.evaluation`)는 **무편집**이다: `mode` 는
 * `@ConditionalOnProperty` 가 Environment 에서 직접 읽으므로 그 타입이 선언할 필요가 없다
 * (수집 선례 — `CollectionProperties` 도 `mode` 를 선언하지 않는다).
 *
 * **`EvaluationProperties` 를 자기 `@EnableConfigurationProperties` 에 함께 적는다** — 상시
 * 활성인 `EvaluationWiring` 이 이미 그것을 등재하므로 production 에서는 중복이지만(같은
 * 타입·같은 빈 이름이라 멱등이다), 이 배선을 **혼자 켰을 때** 그 빈이 없어 기동이 실패하는
 * 숨은 의존을 남기지 않는다(`CollectionTerminationWiring` 이 갈래 사이 숨은 의존으로 겪은
 * 것과 같은 자리 — 배선 test 가 그것을 잡았다).
 *
 * 포트 싱글턴 다섯은 `EvaluationWiring`(상시 활성)이 이미 낸 빈을 그대로 받는다 — 같은 값의
 * 두 번째 빈 정의를 만들지 않는다. 그래서 이 배선이 더하는 것은 **알림 요청 port 의 교체**
 * 하나다: dry-run 은 `RecordingNotificationRequestPort`, 커밋은 production
 * `OutboxNotificationRequestPort`(어댑터 조립 안).
 *
 * 전략은 **run 당 한 번** 저장소에서 읽는다 — `PinnedStrategyRepository` 로 감싸지 않는 이유는
 * use case 자신이 `evaluate()` 진입에서 `load()` 를 정확히 한 번 부르기 때문이다(dry-run 은
 * 응답 조립이 전략을 또 읽어야 해서 감쌌다, D-6A3-5).
 */
@Configuration
@Import(CollectionTerminationWiring::class)
@ConditionalOnProperty(prefix = "bidvector.evaluation", name = ["mode"], havingValue = "once")
@EnableConfigurationProperties(EvaluationCommitProperties::class, EvaluationProperties::class)
open class EvaluationCommitWiring {
    @Suppress("LongParameterList")
    @Bean
    open fun evaluationCommitRun(
        dataSource: DataSource,
        strategyRepository: StrategyRepository,
        candidateSourcePort: CandidateSourcePort,
        watchSubjectPort: WatchSubjectPort,
        licenseGatePort: LicenseGatePort,
        mlAnalysisPort: MlAnalysisPort,
        correlationIdFactory: CorrelationIdFactory,
        clock: Clock,
        evaluationProperties: EvaluationProperties,
        commitProperties: EvaluationCommitProperties,
    ): EvaluationCommitRun {
        require(commitProperties.currentActiveBids >= 0) {
            "bidvector.evaluation.commit.current-active-bids 는 음수일 수 없다"
        }
        val strategy = strategyRepository.load()
        val maxActiveBids =
            requireNotNull(strategy.maxActiveBids) {
                "전략에 여력 상한(maxActiveBids)이 없다 — 커밋 run 을 거부한다(dry-run 과 같은 fail-closed)"
            }.value
        return EvaluationCommitRun(
            dataSource = dataSource,
            strategies = strategyRepository,
            candidateSource = candidateSourcePort,
            watchSubjects = watchSubjectPort,
            licenseGate = licenseGatePort,
            mlAnalysis = mlAnalysisPort,
            capacity = RequestCapacityPort(commitProperties.currentActiveBids, maxActiveBids),
            correlationIds = correlationIdFactory,
            clock = clock,
            analysisBudget = strategy.candidateLimit?.value,
        )
    }

    @Bean
    open fun evaluationCommitRunner(
        run: EvaluationCommitRun,
        properties: EvaluationProperties,
        termination: CollectionTermination,
    ): EvaluationCommitRunner {
        val logger = LoggerFactory.getLogger(EvaluationCommitRunner::class.java)
        return EvaluationCommitRunner(
            run = run,
            candidateCap = properties.candidateCap,
            log = CollectionLog { logger.info(it) },
            termination = termination,
        )
    }
}
