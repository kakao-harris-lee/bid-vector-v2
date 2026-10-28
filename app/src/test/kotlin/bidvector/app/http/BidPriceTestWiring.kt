package bidvector.app.http

import bidvector.adapters.evaluation.NoticeWatchSubjectPort
import bidvector.adapters.ml.UnavailableMlAnalysis
import bidvector.app.wiring.BidPriceRequestFactory
import bidvector.workflow.evaluation.CorrelationIdFactory
import bidvector.workflow.strategy.Clock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

/**
 * 7C ② 의 test 조립. **`@TestConfiguration` 이라 컴포넌트 스캔에 걸리지 않는다**(D-6A2b-9 —
 * 되살린 `TypeExcludeFilter` 가 걷어낸다). 쓰는 test 가 `classes` 에 명시 source 로 준다.
 * 이 가름이 필요한 이유는 `@Configuration` 이면 출하 `BidVectorApplication` 의 스캔 뿌리
 * (`bidvector.app`)에도 잡혀 `EvaluationWiring` 의 같은 이름 빈과 충돌하기 때문이다 — E2E 가
 * 통째로 기동 실패한다. 빈을 `HttpTestApplication` 에 두지 않고 가른 이유는 크기다:
 * `HttpTestSupport.kt` 가 파일 500줄 한도에 닿아 있어, 거기에 조립을 계속 얹으면 다음 slice 가
 * 그 한도에 걸린다(공유 파일이라 그 비용을 남이 치른다).
 *
 * 출하 조립과 **같은 형태**다 — ML 은 항상-미가용 구현, 감시 입력은 실 production 매퍼. 바뀌는
 * 것은 공고·자격의 공급원뿐이라, 이 위에서 재는 것은 「HTTP 층이 결과를 어떤 응답으로 옮기는가」다.
 */
@TestConfiguration
open class BidPriceTestWiring {
    /** 기본값이 「공고 없음」이라 설정하지 않은 test 에서는 404 다. */
    @Bean
    open fun mutableNoticeByIdPort(): MutableNoticeByIdPort = MutableNoticeByIdPort()

    @Bean
    open fun mutableLicenseJudgementPort(): MutableLicenseJudgementPort = MutableLicenseJudgementPort()

    @Bean
    open fun bidPriceRequestFactory(
        strategyRepository: TestStrategyRepository,
        noticeByIdPort: MutableNoticeByIdPort,
        licenseJudgementPort: MutableLicenseJudgementPort,
        clock: Clock,
        correlationIdFactory: CorrelationIdFactory,
    ): BidPriceRequestFactory =
        BidPriceRequestFactory(
            notices = noticeByIdPort,
            strategyRepository = strategyRepository,
            licenseJudgements = licenseJudgementPort,
            watchSubjects = NoticeWatchSubjectPort(),
            mlAnalysis = UnavailableMlAnalysis(),
            correlationIds = correlationIdFactory,
            clock = clock,
        )
}
