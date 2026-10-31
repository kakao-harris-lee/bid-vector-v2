package bidvector.workflow.profile

import bidvector.strategy.OperatorStrategy
import bidvector.strategy.StrategyDraft
import bidvector.workflow.access.AccountId
import bidvector.workflow.evaluation.OperatorProfilePort
import bidvector.workflow.evaluation.ProfileFacts
import bidvector.workflow.evaluation.testStrategy
import bidvector.workflow.strategy.Clock
import bidvector.workflow.strategy.StrategyReader
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

/**
 * [ReadOnboardingChecklistUseCase] — **원천을 모으는 자리**만 잰다(판정 전수는
 * [OnboardingChecklistTest] 가 커널에서 잰다).
 *
 * 여기서 잠그는 것 셋:
 *  · 계정은 **V22 읽기에만** 넘어간다 — 싱글턴 port 둘(자격 사실 · 전략)은 계정을 받지 않는다.
 *  · 「오늘」이 **KST 달력일**이다 — 만료 당일 밤 23:30 KST 와 그 한 시간 뒤가 다른 판정을 낸다.
 *  · 감시 규칙은 **전략에서 온다** — use case 가 빈 규칙을 지어내지 않는다.
 *
 * 값은 전부 합성이다(evidence 실값 0).
 */
class ReadOnboardingChecklistUseCaseTest {
    private val account = AccountId(7L)
    private val other = AccountId(8L)

    /** 만료일 당일의 KST 밤 — 23:30 KST 다(UTC 로는 전날 14:30). */
    private val lateNightKst = Instant.parse("2026-10-30T14:30:00Z")

    /** 그 한 시간 뒤 — KST 로는 **다음 날** 00:30 이고 UTC 로는 여전히 10-30 이다. */
    private val afterKstMidnight = Instant.parse("2026-10-30T15:30:00Z")

    private val expiresOn = LocalDate.of(2026, 10, 30)

    /** 감시 필드가 하나도 없는 전략 — 「전략 미설정」이 아니라 **빈 규칙**이다. */
    private val emptyStrategy = testStrategy(StrategyDraft())

    private val registered =
        NaraReadiness.Registered(setOf(NaraRegistrationField.GOODS), expiresOn)

    @Test
    fun `계정은 V22 읽기에만 넘어간다 — 싱글턴 port 는 계정을 받지 않는다`() {
        val business = RecordingBusinessProfileReader()

        useCase(business = business).read(account)

        business.detailReads shouldContainExactly listOf(account.value)
        business.capacityReads shouldContainExactly listOf(account.value)
    }

    @Test
    fun `다른 계정의 사업 정보는 판정에 들어오지 않는다`() {
        val business =
            RecordingBusinessProfileReader(
                details = mapOf(other.value to BusinessDetails.EMPTY.copy(tradeName = TradeName("남의 상호"))),
            )

        val checklist = useCase(business = business).read(account)

        checklist.pending shouldContain OnboardingItem.TRADE_NAME
        checklist.completed shouldNotContain OnboardingItem.TRADE_NAME
    }

    /**
     * 「오늘」의 구역이 **거동을 가른다** — 같은 만료일 · 같은 저장 상태에서 시계만 한 시간 움직이면
     * 나라장터 준비 항목이 완료에서 미완으로 옮겨 간다. 구역을 UTC 로 바꾸면 두 단언 중 하나가
     * 반드시 깨진다(두 시각이 UTC 로는 같은 날짜다).
     */
    @Test
    fun `오늘은 KST 달력일이다 — 만료 당일 밤과 자정 뒤가 다른 판정이다`() {
        val business = RecordingBusinessProfileReader(details = mapOf(account.value to detailsWith(registered)))

        val beforeMidnight = useCase(business = business, now = lateNightKst).read(account)
        val afterMidnight = useCase(business = business, now = afterKstMidnight).read(account)

        beforeMidnight.completed shouldContain OnboardingItem.NARA_READINESS
        afterMidnight.pending shouldContain OnboardingItem.NARA_READINESS
    }

    @Test
    fun `감시 규칙은 전략에서 온다 — 빈 규칙을 지어내지 않는다`() {
        val configured =
            testStrategy(StrategyDraft(focusCategories = listOf("합성업종"), candidateLimit = 3))

        val withRules = useCase(strategy = FixedStrategyReader(configured)).read(account)
        val withoutRules = useCase(strategy = FixedStrategyReader(emptyStrategy)).read(account)

        withRules.completed shouldContain OnboardingItem.WATCH_CONDITIONS
        withoutRules.pending shouldContain OnboardingItem.WATCH_CONDITIONS
    }

    /**
     * 세 상태가 **접히지 않고** 그대로 올라온다 — 등록분야를 아직 답하지 않은 계정의 시평액 항목은
     * 완료도 미완도 아니고 「선행: 나라장터 준비」다(D-7C4-8 Q3 · D-7C4C-1).
     */
    @Test
    fun `선행 대기 항목은 짝과 함께 올라오고 완료·미완 어디에도 없다`() {
        val checklist = useCase().read(account)

        checklist.awaiting shouldContainExactly
            listOf(AwaitingItem(OnboardingItem.CONSTRUCTION_CAPACITY, OnboardingItem.NARA_READINESS))
        checklist.completed shouldNotContain OnboardingItem.CONSTRUCTION_CAPACITY
        checklist.pending shouldNotContain OnboardingItem.CONSTRUCTION_CAPACITY
    }

    @Test
    fun `진행 상태를 저장하지 않는다 — 같은 입력을 두 번 읽으면 두 번 판정한다`() {
        val business = RecordingBusinessProfileReader()
        val useCase = useCase(business = business)

        useCase.read(account)
        useCase.read(account)

        business.detailReads shouldBe listOf(account.value, account.value)
    }

    private fun detailsWith(readiness: NaraReadiness): BusinessDetails =
        BusinessDetails.EMPTY.copy(naraReadiness = readiness)

    private fun useCase(
        business: BusinessProfileReader = RecordingBusinessProfileReader(),
        strategy: StrategyReader = FixedStrategyReader(emptyStrategy),
        now: Instant = lateNightKst,
        facts: ProfileFacts? = null,
    ): ReadOnboardingChecklistUseCase =
        ReadOnboardingChecklistUseCase(
            qualification = OperatorProfilePort { facts },
            business = business,
            strategies = strategy,
            clock = Clock { now },
        )
}

/**
 * V22 **읽기만** 하는 fake — 계정으로 키잡고 받은 계정을 기록한다. 쓰기 fake
 * ([ProfileUseCasesTest] 의 `FakeBusinessProfileStore`)를 쓰지 않는 이유는 이 use case 가 읽기
 * port 만 받기 때문이다 — 쓰기 능력을 쥔 이중체를 꽂으면 test 가 실제보다 넓은 권한을 전제한다.
 *
 * 키가 [AccountId] 가 아니라 그 `value` 인 것은 의도다: `AccountId` 는 `equals` 를 재정의하지 않아
 * **동일성으로 같다**(형제 fake 와 같은 근거).
 */
private class RecordingBusinessProfileReader(
    private val details: Map<Long, BusinessDetails> = emptyMap(),
    private val capacities: Map<Long, List<ConstructionCapacity>> = emptyMap(),
) : BusinessProfileReader {
    val detailReads = mutableListOf<Long>()
    val capacityReads = mutableListOf<Long>()

    override fun details(account: AccountId): BusinessDetails {
        detailReads += account.value
        return details[account.value] ?: BusinessDetails.EMPTY
    }

    override fun capacities(account: AccountId): List<ConstructionCapacity> {
        capacityReads += account.value
        return capacities[account.value].orEmpty()
    }
}

/** 한 전략만 내는 fake — [StrategyReader] 는 `fun interface` 가 아니라 람다로 줄 수 없다. */
private class FixedStrategyReader(
    private val strategy: OperatorStrategy,
) : StrategyReader {
    override fun load(): OperatorStrategy = strategy
}
