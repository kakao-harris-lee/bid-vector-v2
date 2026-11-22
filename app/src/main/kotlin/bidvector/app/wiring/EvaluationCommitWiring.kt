package bidvector.app.wiring

import bidvector.adapters.evaluation.EvaluationCommitRuns
import bidvector.adapters.persistence.ReleaseIdentifier
import bidvector.app.collection.CollectionLog
import bidvector.app.collection.CollectionTermination
import bidvector.app.evaluation.EvaluationCommitRunner
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.notificationinbox.NotificationAudiencePort
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyReader
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
 * 전략은 **계정마다 run 당 정확히 한 번** 저장소에서 읽는다(R1-M-4 의 계정별 판) — 그 읽기와
 * 고정은 `EvaluationCommitRuns` 가 진다. 이 조립이 하는 일은 협력자를 꽂는 것뿐이다.
 */
@Configuration
// NOTI-01(M9/ops-code) — 알림함 쓰기·청중 빈을 **명시 import** 한다. 그 조립은 production 에서
// 상시 활성이라 중복이지만(같은 타입·같은 빈 이름이라 멱등), 이 배선을 **혼자 켰을 때** 그 빈이
// 없어 기동이 실패하는 숨은 의존을 남기지 않는다 — `EvaluationProperties` 를 자기 등재에 함께
// 적은 것과 같은 근거이고, `CollectionTerminationWiring` 이 겪은 그 자리다.
@Import(CollectionTerminationWiring::class, NotificationInboxWiring::class)
@ConditionalOnProperty(prefix = "bidvector.evaluation", name = ["mode"], havingValue = "once")
@EnableConfigurationProperties(EvaluationCommitProperties::class, EvaluationProperties::class)
open class EvaluationCommitWiring {
    /**
     * **M15/15A — run 하나가 아니라 계정별 run 공장이다.** 앞 판은 이 자리에서 싱글턴 전략을
     * **기동 시점에** 한 번 읽어 run 하나를 만들었고, 그 run 이 모든 계정의 알림함에 같은 새
     * 후보를 적었다. 이제 계정 전수와 계정별 전략 읽기는 공장이 지고, 조립은 협력자만 꽂는다.
     *
     * 전략을 **읽기 port 타입으로** 받는다(`StrategyReader`) — 이 조립은 전략을 쓰지 않는다
     * (`SuggestionWiring` 과 같은 규율).
     */
    @Suppress("LongParameterList")
    @Bean
    open fun evaluationCommitRuns(
        dataSource: DataSource,
        strategyReader: StrategyReader,
        candidateSourcePort: CandidateSourcePort,
        watchSubjectPort: WatchSubjectPort,
        licenseGatePort: LicenseGatePort,
        mlAnalysisPort: MlAnalysisPort,
        correlationIdFactory: CorrelationIdFactory,
        clock: Clock,
        commitProperties: EvaluationCommitProperties,
        // NOTI-01 — 쓰기 빈과 청중 빈은 상시 활성인 `NotificationInboxWiring` 이 이미 낸다.
        // 같은 값의 둘째 빈 정의를 만들지 않는다(다른 포트들과 같은 규율).
        notificationInboxPort: NotificationInboxPort,
        notificationAudiencePort: NotificationAudiencePort,
        release: ReleaseIdentifier,
    ): EvaluationCommitRuns =
        EvaluationCommitRuns(
            dataSource = dataSource,
            strategies = strategyReader,
            candidateSource = candidateSourcePort,
            watchSubjects = watchSubjectPort,
            licenseGate = licenseGatePort,
            mlAnalysis = mlAnalysisPort,
            correlationIds = correlationIdFactory,
            clock = clock,
            notificationInbox = notificationInboxPort,
            audience = notificationAudiencePort,
            currentActiveBids = commitProperties.currentActiveBids,
            release = release,
        )

    /**
     * 러너도 **주입된 시계**를 받는다(보존 러너 선례 cr L-4) — 회전 창의 달력일을 그 시계로
     * 고른다. 러너가 시계를 안 받고 시스템 시계로 날짜를 고르면 test 가 창을 움직일 수 없고,
     * run 공장과 러너가 서로 다른 날을 볼 수 있다.
     */
    @Bean
    open fun evaluationCommitRunner(
        runs: EvaluationCommitRuns,
        properties: EvaluationProperties,
        clock: Clock,
        termination: CollectionTermination,
    ): EvaluationCommitRunner {
        val logger = LoggerFactory.getLogger(EvaluationCommitRunner::class.java)
        return EvaluationCommitRunner(
            runs = runs,
            candidateCap = properties.candidateCap,
            maxAccountsPerRun = properties.maxAccountsPerRun,
            maxConsecutiveAccountFailures = properties.maxConsecutiveAccountFailures,
            clock = clock,
            log = CollectionLog { logger.info(it) },
            termination = termination,
        )
    }
}
