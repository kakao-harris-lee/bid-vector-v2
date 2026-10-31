package bidvector.app.wiring

import bidvector.adapters.profile.JdbcBusinessProfileStore
import bidvector.adapters.profile.JdbcOperatorProfileWriter
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.OperatorProfileStorePort
import bidvector.workflow.profile.BusinessProfileStorePort
import bidvector.workflow.profile.ListConstructionCapacitiesUseCase
import bidvector.workflow.profile.ReadOnboardingChecklistUseCase
import bidvector.workflow.profile.ReadProfileUseCase
import bidvector.workflow.profile.RemoveConstructionCapacityUseCase
import bidvector.workflow.profile.ReplaceBusinessDetailsUseCase
import bidvector.workflow.profile.ReplaceQualificationFactsUseCase
import bidvector.workflow.profile.UpsertConstructionCapacityUseCase
import bidvector.workflow.profile.operatorTradeNameOf
import bidvector.workflow.result.OperatorTradeNamePort
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyReader
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

/**
 * 프로필 경로 조립(M7/7C④ 2b) — 이 조립이 없으면 프로필 endpoint 가 뜨지 않는다.
 *
 * **controller 가 받는 것은 use case 뿐이다**(D-6A2b-8): 저장 port 를 돌려주는 공개 경로가 여기에
 * 없고, `app.http.denied-types` 가 그 이름을 핸들러 주입 표면에서 다시 거부한다. 그래서 HTTP 층은
 * use case 밖에서 프로필을 바꿀 방법이 없다.
 *
 * **자격 판정 쪽 조립은 이 파일의 V22 port 를 받지 않는다**(2b 표적 4) — `SuggestionWiring` ·
 * `EvaluationWiring` 의 매개변수 타입이 그 경계이고 `ProfileAssemblyBoundaryTest` 가 집합으로 잰다.
 */
@Configuration
open class ProfileWiring {
    /**
     * 운영자 상호 하나를 **좁은 포트로 내보낸다**(M7/7C⑤-b 가 소비한다).
     *
     * 소비자가 `BusinessProfileStorePort` 를 직접 받으면 두 경계가 함께 무너진다 — 「V22 타입을
     * 조립하는 자리는 이 class 하나」(`ProfileAssemblyBoundaryTest`)와 「V22 표 이름은 허용된
     * 어댑터 자리에서만」(`BusinessProfileIsolationGateTest`). 그래서 **V22 를 아는 쪽이
     * 좁은 읽기를 publish** 하고 소비자는 상호 하나만 안다.
     *
     * 상호는 **원문 그대로** 나간다 — 정규화는 대사 시점의 커널이 한 번만 한다(이중 정규화 금지).
     * 「미설정」은 `null` 이 아니라 값이다.
     */
    @Bean
    open fun operatorTradeNamePort(businessProfileStore: BusinessProfileStorePort): OperatorTradeNamePort =
        operatorTradeNameOf(businessProfileStore)

    @Bean
    open fun businessProfileStore(dataSource: DataSource): BusinessProfileStorePort =
        JdbcBusinessProfileStore(dataSource)

    /**
     * V12 자격 사실의 **저장** port — 조회 port 빈(`EvaluationWiring`)과 가른다. 구현도 다른
     * 클래스다(읽기 어댑터는 쓰기 port 를 구현하지 않는다, 모양 래칫 D-3).
     */
    @Bean
    open fun operatorProfileStore(dataSource: DataSource): OperatorProfileStorePort =
        JdbcOperatorProfileWriter(dataSource)

    /**
     * 조회 use case 는 **읽기 port 타입으로만** 저장소를 본다(`StrategyReader` 와 같은 규율) — 읽기 자리에
     * 쓰기 능력이 서명으로 들어오지 않는다. 읽기 전용 빈을 따로 두지 않는 이유는 그러면 같은 인스턴스가
     * 두 타입으로 등록돼 **주입 후보가 둘**이 되고, 그때 어느 쪽이 꽂히는지가 매개변수 **이름**으로 갈리기
     * 때문이다(이름을 고치면 조용히 다른 빈이 꽂힌다).
     */
    @Bean
    open fun readProfileUseCase(
        operatorProfilePort: OperatorProfilePort,
        businessProfileStore: BusinessProfileStorePort,
    ): ReadProfileUseCase = ReadProfileUseCase(operatorProfilePort, businessProfileStore)

    @Bean
    open fun listConstructionCapacitiesUseCase(
        businessProfileStore: BusinessProfileStorePort,
    ): ListConstructionCapacitiesUseCase = ListConstructionCapacitiesUseCase(businessProfileStore)

    /**
     * 체크리스트 조회(M7/7C④-3) — **이 조립에 두는 자리가 정해져 있다.**
     * `ProfileAssemblyBoundaryTest` 가 「V22 타입에 의존하는 `app.wiring` class 는 [ProfileWiring]
     * 하나」를 집합으로 재므로, 체크리스트 전용 조립을 새로 만들면 그 게이트가 붉다.
     *
     * 이 빈이 전략 읽기를 받는 것은 판정의 입력이라서다(감시 금액대 · 관심 업종) — 쓰기 port 가 아니다.
     *
     * **비활성화 경로**: 이 빈을 빼면 체크리스트 경로만 사라진다(컨트롤러가 use case 를 요구하므로
     * 기동이 실패한다) — 프로필 operation 여섯은 그대로 산다.
     */
    @Bean
    open fun readOnboardingChecklistUseCase(
        operatorProfilePort: OperatorProfilePort,
        businessProfileStore: BusinessProfileStorePort,
        strategyReader: StrategyReader,
        clock: Clock,
    ): ReadOnboardingChecklistUseCase =
        ReadOnboardingChecklistUseCase(
            qualification = operatorProfilePort,
            business = businessProfileStore,
            strategies = strategyReader,
            clock = clock,
        )

    @Bean
    open fun replaceQualificationFactsUseCase(
        operatorProfileStore: OperatorProfileStorePort,
    ): ReplaceQualificationFactsUseCase = ReplaceQualificationFactsUseCase(operatorProfileStore)

    @Bean
    open fun replaceBusinessDetailsUseCase(
        businessProfileStore: BusinessProfileStorePort,
        clock: Clock,
    ): ReplaceBusinessDetailsUseCase = ReplaceBusinessDetailsUseCase(businessProfileStore, clock::now)

    @Bean
    open fun upsertConstructionCapacityUseCase(
        businessProfileStore: BusinessProfileStorePort,
        clock: Clock,
    ): UpsertConstructionCapacityUseCase = UpsertConstructionCapacityUseCase(businessProfileStore, clock::now)

    @Bean
    open fun removeConstructionCapacityUseCase(
        businessProfileStore: BusinessProfileStorePort,
    ): RemoveConstructionCapacityUseCase = RemoveConstructionCapacityUseCase(businessProfileStore)
}
