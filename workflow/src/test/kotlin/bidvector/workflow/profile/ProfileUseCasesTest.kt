package bidvector.workflow.profile

import bidvector.qualification.LicenseName
import bidvector.qualification.OperatorLicenses
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.ProfileFacts
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

/**
 * 프로필 use case — **mock framework 없이 값과 fake port 로** 잰다(v2-지침서 §5).
 *
 * 여기서 잠그는 것: 조회가 세 원천을 모아 내고 판정 대기 축을 **그대로** 싣는다는 것, 쓰기가 받은
 * 계정만 port 에 넘긴다는 것(2b 표적 2 의 use case 쪽 절반 — HTTP 쪽 절반은 실 DB E2E 가 잰다),
 * 그리고 **행 수 상한이 앱에서 집행된다**는 것(D-7C4-8 Q1).
 */
class ProfileUseCasesTest {
    private val account = AccountId(7)
    private val other = AccountId(8)
    private val now = Instant.parse("2026-10-26T01:00:00Z")
    private val facts = ProfileFacts(emptySet(), OperatorLicenses.NotDeclared, emptyList())

    private fun capacity(name: String) = ConstructionCapacity(LicenseName(name), 1_000L, 2026)

    @Test
    fun `조회는 세 원천을 모으고 판정 대기 축을 그대로 싣는다`() {
        val details = BusinessDetails.EMPTY.copy(tradeName = TradeName("합성상호"))
        val store =
            FakeBusinessProfileStore(
                mapOf(account.value to details),
                mapOf(account.value to listOf(capacity("합성면허"))),
            )
        val view = ReadProfileUseCase(OperatorProfilePort { facts }, store).read(account)

        view.qualification shouldBe facts
        view.details shouldBe details
        view.capacities shouldContainExactly listOf(capacity("합성면허"))
        view.unjudgedAxes shouldContainExactly UnjudgedQualificationAxis.entries
    }

    @Test
    fun `조회는 받은 계정으로만 읽는다 — 다른 계정의 값이 섞이지 않는다`() {
        val store =
            FakeBusinessProfileStore(
                mapOf(other.value to BusinessDetails.EMPTY.copy(tradeName = TradeName("남의상호"))),
                mapOf(other.value to listOf(capacity("남의면허"))),
            )
        val view = ReadProfileUseCase(OperatorProfilePort { null }, store).read(account)

        view.qualification shouldBe null
        view.details shouldBe BusinessDetails.EMPTY
        view.capacities shouldBe emptyList()
    }

    @Test
    fun `사업 정보 절 교체는 받은 계정과 주입된 시계로 쓴다`() {
        val store = FakeBusinessProfileStore()
        val details = BusinessDetails.EMPTY.copy(headquartersRegion = HeadquartersRegion.SEJONG)

        ReplaceBusinessDetailsUseCase(store) { now }.replace(account, details)

        store.details(account) shouldBe details
        store.detailWrites shouldContainExactly listOf(Triple(account.value, details, now))
    }

    @Test
    fun `시평액 상한 — 새 면허는 상한에서 거부되고 이미 있는 면허의 갱신은 통과한다`() {
        val full = (1..ProfileInputPolicy.CAPACITY_ROWS_MAX).map { capacity("합성면허$it") }
        val store = FakeBusinessProfileStore(capacities = mapOf(account.value to full))
        val useCase = UpsertConstructionCapacityUseCase(store) { now }

        useCase.upsert(account, capacity("새면허")) shouldBe
            ProfileWriteOutcome.RowLimitExceeded(ProfileInputPolicy.CAPACITY_ROWS_MAX)
        store.capacityWrites shouldBe emptyList() // 거부는 **쓰지 않는다**.

        val updated = full.first().copy(amountWon = 2_000L)
        useCase.upsert(account, updated) shouldBe StorageWriteOutcome.Written
        store.capacityWrites.map { it.second } shouldContainExactly listOf(updated)
    }

    @Test
    fun `시평액 상한은 그 계정의 행만 센다 — 남의 행이 상한을 먹지 않는다`() {
        val full = (1..ProfileInputPolicy.CAPACITY_ROWS_MAX).map { capacity("남의면허$it") }
        val store = FakeBusinessProfileStore(capacities = mapOf(other.value to full))

        UpsertConstructionCapacityUseCase(store) { now }.upsert(account, capacity("내면허")) shouldBe
            StorageWriteOutcome.Written
    }

    @Test
    fun `저장이 제약으로 거부하면 그 결과를 그대로 낸다 — 문면을 지어 붙이지 않는다`() {
        val rejection = StorageWriteOutcome.RejectedByStorage("23514")
        val store = FakeBusinessProfileStore(rejection = rejection)

        ReplaceBusinessDetailsUseCase(store) { now }.replace(account, BusinessDetails.EMPTY) shouldBe rejection
        UpsertConstructionCapacityUseCase(store) { now }.upsert(account, capacity("합성면허")) shouldBe rejection
    }

    @Test
    fun `삭제는 지운 행이 있었는지 그대로 낸다`() {
        val store = FakeBusinessProfileStore(capacities = mapOf(account.value to listOf(capacity("합성면허"))))
        val useCase = RemoveConstructionCapacityUseCase(store)

        useCase.remove(account, LicenseName("합성면허")) shouldBe true
        useCase.remove(account, LicenseName("없는면허")) shouldBe false
    }
}

/**
 * 계정별 저장을 흉내 내는 fake — **port 가 받은 계정으로만** 읽고 쓴다(소유 경계를 값으로 잰다).
 *
 * **app test 의 `InMemoryBusinessProfileStore` 와 본문이 거의 같다**(r2 CR L-8) — 재는 축이 다르다:
 * 이쪽은 use case 에 **어떤 계정이 넘어왔는가**를 기록하고, 저쪽은 Spring 조립에 꽂혀 HTTP 거동을 받친다.
 *
 * 키가 [AccountId] 가 아니라 그 `value` 인 것은 의도다: `AccountId` 는 `equals` 를 재정의하지 않는
 * 평범한 class 라 **동일성으로 같다**. 인스턴스를 키로 쓰면 같은 계정을 두 번 만든 호출이 조용히
 * 다른 칸을 보게 되어, fake 가 실 저장소와 다른 답을 낸다.
 */
private class FakeBusinessProfileStore(
    details: Map<Long, BusinessDetails> = emptyMap(),
    capacities: Map<Long, List<ConstructionCapacity>> = emptyMap(),
    /** 저장 층이 늘 거부하는 fake — 제약 거부가 use case 를 **지나가는지**만 본다. */
    private val rejection: StorageWriteOutcome.RejectedByStorage? = null,
) : BusinessProfileStorePort {
    private val detailsByAccount = details.toMutableMap()
    private val capacitiesByAccount = capacities.mapValues { it.value.toMutableList() }.toMutableMap()

    val detailWrites = mutableListOf<Triple<Long, BusinessDetails, Instant>>()
    val capacityWrites = mutableListOf<Pair<Long, ConstructionCapacity>>()

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
        detailWrites += Triple(account.value, details, at)
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
        capacityWrites += account.value to capacity
        return StorageWriteOutcome.Written
    }

    override fun removeCapacity(
        account: AccountId,
        licenseName: LicenseName,
    ): Boolean = capacitiesByAccount[account.value]?.removeAll { it.licenseName == licenseName } ?: false
}
