package bidvector.app.wiring

import bidvector.adapters.evaluation.JdbcCandidateSource
import bidvector.adapters.evaluation.JdbcNoticeById
import bidvector.adapters.evaluation.NoticeWatchSubjectPort
import bidvector.adapters.ml.UnavailableMlAnalysis
import bidvector.adapters.profile.JdbcOperatorProfileRepository
import bidvector.adapters.qualification.JdbcRequirementStore
import bidvector.adapters.qualification.StoredRequirementLicenseGate
import bidvector.adapters.qualification.StoredRequirementLicenseJudgement
import bidvector.qualification.LICENSE_QUALIFICATION_POLICY
import bidvector.qualification.LicenseQualificationPolicyData
import bidvector.sharedkernel.Resolution
import bidvector.workflow.evaluation.CandidateSourcePort
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.evaluation.LicenseGatePort
import bidvector.workflow.evaluation.LicenseJudgementPort
import bidvector.workflow.evaluation.MlAnalysisPort
import bidvector.workflow.evaluation.NoticeByIdPort
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.WatchSubjectPort
import bidvector.workflow.notificationinbox.NotificationInboxPort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyRepository
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.LocalDate
import javax.sql.DataSource

/**
 * 평가 포트 아홉 중 **싱글턴 여덟**의 조립(D-6A3-8, `OPEN-6F-ASSEMBLY` dry-run 닫음). 요청
 * 스코프 둘(`CapacityPort`·`NotificationRequestPort`)과 요청마다 use case 를 짓는
 * `EvaluationDryRunFactory`는 별도 파일이다 — 이 클래스는 요청 사이에 공유돼도 안전한
 * 것만 낸다(전부 무상태 또는 read-only 어댑터).
 *
 * **`MlAnalysisPort` 는 [UnavailableMlAnalysis] 고정이다(D-6A3-2, 운영자 결정 1).**
 * `OpportunityAnalysis`·`GrpcBidPredictionGateway`·`GrpcEmbeddingGateway`(adapters.ml)는
 * 이 파일 어디에도 나타나지 않는다 — 의존 게이트(`app.wiring`이 `adapters.ml`에서 참조하는
 * 클래스 집합 == {UnavailableMlAnalysis})가 그 사실을 잠근다.
 *
 * **면허 정책은 [PersistenceWiring.strategyRepository]와 같은 형태로 해소한다** — 해소
 * 실패는 기동 실패다(값을 지어내지 않는다, D-6F1-6과 같은 규율).
 */
@Configuration
@EnableConfigurationProperties(EvaluationProperties::class)
open class EvaluationWiring {
    @Bean
    open fun candidateSourcePort(
        dataSource: DataSource,
        clock: Clock,
        properties: EvaluationProperties,
    ): CandidateSourcePort = JdbcCandidateSource(dataSource, clock, properties.candidateCap)

    @Bean
    open fun watchSubjectPort(): WatchSubjectPort = NoticeWatchSubjectPort()

    @Bean
    open fun operatorProfilePort(dataSource: DataSource): OperatorProfilePort =
        JdbcOperatorProfileRepository(dataSource)

    @Bean
    open fun licenseGatePort(
        dataSource: DataSource,
        operatorProfilePort: OperatorProfilePort,
    ): LicenseGatePort =
        StoredRequirementLicenseGate(JdbcRequirementStore(dataSource), operatorProfilePort, resolvedLicensePolicy)

    /**
     * M7/7C ② — 자격 판정 **봉투**를 내는 포트. [licenseGatePort] 와 같은 세 입력(요건 저장소·
     * 프로필·해소된 정책)으로 **같은 판정**을 내고, 단건 평가 조립이 그 `verdict` 를 커널에 pin
     * 한다. 정책 해소 자리는 [resolvedLicensePolicy] 하나다 — 두 빈이 각자 `resolve` 하면 서로
     * 다른 날짜에 해소될 수 있다.
     */
    @Bean
    open fun licenseJudgementPort(
        dataSource: DataSource,
        operatorProfilePort: OperatorProfilePort,
    ): LicenseJudgementPort =
        StoredRequirementLicenseJudgement(
            JdbcRequirementStore(dataSource),
            operatorProfilePort,
            resolvedLicensePolicy,
        )

    /** M7/7C ② — 공고 한 행을 키로 읽는 포트(쓰기 능력이 조립 그래프에 들어오지 않는다). */
    @Bean
    open fun noticeByIdPort(dataSource: DataSource): NoticeByIdPort = JdbcNoticeById(dataSource)

    /**
     * 해소 실패는 기동 실패다 — 값을 지어내지 않는다(D-6F1-6 과 같은 규율).
     *
     * **`fun` 이 아니라 `val` 인 것이 요점이다**(`cr r1 M2`). 함수였을 때는 두 빈이 각자 불러
     * `LocalDate.now()` 를 **두 번** 읽었다. 자정 경계에 기동하면서 그 사이에 날이 바뀌면 게이트와
     * 봉투가 **서로 다른 정책 version** 을 받고, 응답의 정책 출처 칸이 커널 판정과 갈린다.
     * 조립 한 번에 해소도 한 번이다.
     */
    private val resolvedLicensePolicy: Resolution.Resolved<LicenseQualificationPolicyData> =
        when (val resolution = LICENSE_QUALIFICATION_POLICY.resolve(LocalDate.now())) {
            is Resolution.Resolved -> resolution
            else -> error("면허 자격 정책이 해소되지 않았다: $resolution")
        }

    @Bean
    open fun mlAnalysisPort(): MlAnalysisPort = UnavailableMlAnalysis()

    /**
     * 요청 스코프 use case 팩토리(D-6A3-8) — [StrategyRepository]는 `PersistenceWiring`,
     * [Clock]·[CorrelationIdFactory]는 `BidVectorApplication`이 이미 낸 빈을 그대로
     * 받는다(재선언하지 않는다 — 같은 값의 두 번째 빈 정의가 생기지 않게 한다).
     */
    @Bean
    open fun evaluationDryRunFactory(
        strategyRepository: StrategyRepository,
        candidateSourcePort: CandidateSourcePort,
        watchSubjectPort: WatchSubjectPort,
        licenseGatePort: LicenseGatePort,
        mlAnalysisPort: MlAnalysisPort,
        correlationIdFactory: CorrelationIdFactory,
        clock: Clock,
    ): EvaluationDryRunFactory =
        EvaluationDryRunFactory(
            strategyRepository = strategyRepository,
            candidateSource = candidateSourcePort,
            watchSubjects = watchSubjectPort,
            licenseGate = licenseGatePort,
            mlAnalysis = mlAnalysisPort,
            correlationIds = correlationIdFactory,
            clock = clock,
        )

    /**
     * M7/7C ② — 공고 단건·운영자 요청 평가의 요청 스코프 팩토리. 후보 스캔 포트를 **받지
     * 않는다**(요청당 비용이 공고 수에 비례하지 않는다) — 지목된 공고 한 행을 읽어 그것만 후보로
     * 세운다. 나머지 협력자는 dry-run 과 **같은 빈**이라 두 경로가 같은 판정 정책을 본다.
     */
    @Suppress("LongParameterList")
    @Bean
    open fun bidPriceRequestFactory(
        noticeByIdPort: NoticeByIdPort,
        strategyRepository: StrategyRepository,
        licenseJudgementPort: LicenseJudgementPort,
        watchSubjectPort: WatchSubjectPort,
        mlAnalysisPort: MlAnalysisPort,
        correlationIdFactory: CorrelationIdFactory,
        clock: Clock,
        notificationInboxPort: NotificationInboxPort,
    ): BidPriceRequestFactory =
        BidPriceRequestFactory(
            notices = noticeByIdPort,
            strategyRepository = strategyRepository,
            licenseJudgements = licenseJudgementPort,
            watchSubjects = watchSubjectPort,
            mlAnalysis = mlAnalysisPort,
            correlationIds = correlationIdFactory,
            clock = clock,
            // 쓰기 빈은 `NotificationInboxWiring` 한 자리다(D-7C3B-2) — 이 경로가 R-4 의 종착점이다.
            notificationInbox = notificationInboxPort,
        )
}

/**
 * 후보 스캔 상한(D-6A3-7, `OPEN-6F2-CANDIDATE-BOUND` 닫음) — 기본값 없음. 미설정이면 Spring
 * relaxed binding 이 `BindException`으로 기동을 fail-fast 시킨다([PersistenceProperties]와
 * 같은 규율) — 조용히 무제한으로 돌지 않는다.
 */
@ConfigurationProperties(prefix = "bidvector.evaluation")
data class EvaluationProperties(
    val candidateCap: Int,
)
