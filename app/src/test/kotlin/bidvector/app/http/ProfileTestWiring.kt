package bidvector.app.http

import bidvector.qualification.LicenseName
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.OperatorProfileStorePort
import bidvector.workflow.evaluation.ProfileFacts
import bidvector.workflow.profile.BusinessDetails
import bidvector.workflow.profile.BusinessProfileStorePort
import bidvector.workflow.profile.ConstructionCapacity
import bidvector.workflow.profile.ListConstructionCapacitiesUseCase
import bidvector.workflow.profile.ReadProfileUseCase
import bidvector.workflow.profile.RemoveConstructionCapacityUseCase
import bidvector.workflow.profile.ReplaceBusinessDetailsUseCase
import bidvector.workflow.profile.ReplaceQualificationFactsUseCase
import bidvector.workflow.profile.StorageWriteOutcome
import bidvector.workflow.profile.UpsertConstructionCapacityUseCase
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.Instant

/**
 * 프로필 경로의 **HTTP 층 이중체 조립**(M7/7C④ 2b) — 실 DB 없이 요청 판독 · 응답 모양 · 401 ·
 * 상한 거부만 잰다. `HttpTestApplication` 이 `@Import` 로 들인다(스캔 대상이 아니라 명시 수입이다 —
 * 출하 조립이 이 조립을 주워 가지 않는다).
 *
 * 소유 경계와 실제 질의가 계정으로 좁혀지는지는 **실 DB test** 가 진다(`JdbcBusinessProfileStoreTest` ·
 * 프로필 E2E) — fake 로는 증명되지 않는다.
 */
@TestConfiguration
open class ProfileTestWiring {
    @Bean
    open fun inMemoryBusinessProfileStore(): InMemoryBusinessProfileStore = InMemoryBusinessProfileStore()

    @Bean
    open fun inMemoryOperatorProfile(): InMemoryOperatorProfile = InMemoryOperatorProfile()

    @Bean
    open fun readProfileUseCase(
        inMemoryOperatorProfile: InMemoryOperatorProfile,
        inMemoryBusinessProfileStore: InMemoryBusinessProfileStore,
    ): ReadProfileUseCase = ReadProfileUseCase(inMemoryOperatorProfile, inMemoryBusinessProfileStore)

    @Bean
    open fun listConstructionCapacitiesUseCase(
        inMemoryBusinessProfileStore: InMemoryBusinessProfileStore,
    ): ListConstructionCapacitiesUseCase = ListConstructionCapacitiesUseCase(inMemoryBusinessProfileStore)

    @Bean
    open fun replaceQualificationFactsUseCase(
        inMemoryOperatorProfile: InMemoryOperatorProfile,
    ): ReplaceQualificationFactsUseCase = ReplaceQualificationFactsUseCase(inMemoryOperatorProfile)

    @Bean
    open fun replaceBusinessDetailsUseCase(
        inMemoryBusinessProfileStore: InMemoryBusinessProfileStore,
        clock: FixedClock,
    ): ReplaceBusinessDetailsUseCase = ReplaceBusinessDetailsUseCase(inMemoryBusinessProfileStore, clock::now)

    @Bean
    open fun upsertConstructionCapacityUseCase(
        inMemoryBusinessProfileStore: InMemoryBusinessProfileStore,
        clock: FixedClock,
    ): UpsertConstructionCapacityUseCase = UpsertConstructionCapacityUseCase(inMemoryBusinessProfileStore, clock::now)

    @Bean
    open fun removeConstructionCapacityUseCase(
        inMemoryBusinessProfileStore: InMemoryBusinessProfileStore,
    ): RemoveConstructionCapacityUseCase = RemoveConstructionCapacityUseCase(inMemoryBusinessProfileStore)
}

/**
 * V22 저장의 이중체 — 계정은 **`value` 로** 키잡는다(`AccountId` 는 `equals` 를 재정의하지 않아
 * 동일성으로 같다. 인스턴스를 키로 쓰면 같은 계정의 두 요청이 서로 다른 칸을 본다).
 *
 * [rejection] 을 세우면 모든 쓰기가 제약 거부를 낸다 — 어댑터가 드라이버 예외를 값으로 바꾼 뒤의
 * 거동(400 · 문면 없음)을 HTTP 층에서 재는 데 쓴다.
 *
 * **workflow 쪽 `FakeBusinessProfileStore` 와 본문이 거의 같다**(r2 CR L-8). 합치지 않는 이유는 모듈이
 * 다르고(이쪽은 `app` test, 저쪽은 `workflow` test) 재는 축도 다르기 때문이다 — 저쪽은 use case 에 **어떤
 * 계정이 넘어왔는가**를 기록하고, 이쪽은 Spring 조립에 꽂혀 HTTP 거동만 받친다. 한쪽을 고칠 때 다른
 * 쪽도 보라는 표시로 서로를 가리켜 둔다.
 */
class InMemoryBusinessProfileStore : BusinessProfileStorePort {
    @Volatile var rejection: StorageWriteOutcome.RejectedByStorage? = null

    private val detailsByAccount = mutableMapOf<Long, BusinessDetails>()
    private val capacitiesByAccount = mutableMapOf<Long, MutableList<ConstructionCapacity>>()

    fun reset() {
        rejection = null
        detailsByAccount.clear()
        capacitiesByAccount.clear()
    }

    override fun details(account: AccountId): BusinessDetails = detailsByAccount[account.value] ?: BusinessDetails.EMPTY

    override fun capacities(account: AccountId): List<ConstructionCapacity> =
        capacitiesByAccount[account.value].orEmpty().sortedBy { it.licenseName.value }

    override fun replaceDetails(
        account: AccountId,
        details: BusinessDetails,
        at: Instant,
    ): StorageWriteOutcome {
        rejection?.let { return it }
        detailsByAccount[account.value] = details
        return StorageWriteOutcome.Written
    }

    override fun upsertCapacity(
        account: AccountId,
        capacity: ConstructionCapacity,
        at: Instant,
    ): StorageWriteOutcome {
        rejection?.let { return it }
        val rows = capacitiesByAccount.getOrPut(account.value) { mutableListOf() }
        rows.removeAll { it.licenseName == capacity.licenseName }
        rows += capacity
        return StorageWriteOutcome.Written
    }

    override fun removeCapacity(
        account: AccountId,
        licenseName: LicenseName,
    ): Boolean = capacitiesByAccount[account.value]?.removeAll { it.licenseName == licenseName } ?: false
}

/** V12 자격 사실의 이중체 — **싱글턴**이다(계정을 받지 않는다, 실 표와 같은 모양). */
class InMemoryOperatorProfile :
    OperatorProfilePort,
    OperatorProfileStorePort {
    @Volatile private var facts: ProfileFacts? = null

    fun reset() {
        facts = null
    }

    override fun current(): ProfileFacts? = facts

    override fun save(facts: ProfileFacts) {
        this.facts = facts
    }
}
