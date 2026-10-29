package bidvector.app.wiring

import bidvector.adapters.profile.JdbcBusinessProfileStore
import bidvector.adapters.profile.JdbcOperatorProfileWriter
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.OperatorProfileStorePort
import bidvector.workflow.profile.BusinessProfileStorePort
import bidvector.workflow.profile.ListConstructionCapacitiesUseCase
import bidvector.workflow.profile.ReadProfileUseCase
import bidvector.workflow.profile.RemoveConstructionCapacityUseCase
import bidvector.workflow.profile.ReplaceBusinessDetailsUseCase
import bidvector.workflow.profile.ReplaceQualificationFactsUseCase
import bidvector.workflow.profile.UpsertConstructionCapacityUseCase
import bidvector.workflow.strategy.Clock
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
