package bidvector.workflow.profile

import bidvector.qualification.LicenseName
import bidvector.qualification.OperatorLicenses
import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.VatTreatment
import bidvector.strategy.BudgetBound
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.CategoryCode
import bidvector.strategy.WatchRules
import bidvector.workflow.evaluation.ProfileFacts
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * [judgeOnboardingChecklist] — **서버 판정 전수**(scope test 「항목 × 완료/미완료」). 항목마다
 * 「미완 스냅샷」과 「그 항목만 채운 스냅샷」을 짝으로 두고, 그 항목이 미완 → 완료로 옮겨 가는지
 * 잰다. 표(`OnboardingItem`)에 항목을 더하고 여기 짝을 적지 않으면 전수 test 가 붉다.
 *
 * 값은 전부 합성이다(evidence 실값 0).
 */
class OnboardingChecklistTest {
    private val today = LocalDate.of(2026, 10, 22)

    private val emptyWatch = WatchRules.empty(BudgetBoundInclusivity.Inclusive)

    private val blank =
        OnboardingSnapshot(
            qualification = null,
            details = BusinessDetails.EMPTY,
            capacities = emptyList(),
            watchRules = emptyWatch,
            today = today,
        )

    private val registeredConstruction =
        NaraReadiness.Registered(setOf(NaraRegistrationField.CONSTRUCTION), today.plusYears(1))

    private val capacity = ConstructionCapacity(LicenseName("합성면허"), 1_000_000L, 2026)

    private val won = BaseAmount(1_000_000L, Currency.KRW, VatTreatment.INCLUSIVE, Provenance.OperatorDeclared)

    private fun withDetails(details: BusinessDetails) = blank.copy(details = details)

    private fun withQualification(businessTypes: Set<CategoryCode>) =
        blank.copy(qualification = ProfileFacts(businessTypes, OperatorLicenses.NotDeclared, emptyList()))

    private fun withWatch(rules: WatchRules) = blank.copy(watchRules = rules)

    private val constructionRegistered = withDetails(BusinessDetails.EMPTY.copy(naraReadiness = registeredConstruction))

    /** 항목 → (그 항목이 적용되면서 미완인 스냅샷, 같은 조건에서 그 항목만 채운 스냅샷). */
    private val pairs: Map<OnboardingItem, Pair<OnboardingSnapshot, OnboardingSnapshot>> =
        mapOf(
            OnboardingItem.TRADE_NAME to
                (blank to withDetails(BusinessDetails.EMPTY.copy(tradeName = TradeName("합성상호")))),
            OnboardingItem.NARA_READINESS to (blank to constructionRegistered),
            OnboardingItem.BUSINESS_TYPES to
                (withQualification(emptySet()) to withQualification(setOf(CategoryCode("합성업종")))),
            OnboardingItem.LICENSES to (blank to withQualification(emptySet())),
            OnboardingItem.HEADQUARTERS_REGION to
                (blank to withDetails(BusinessDetails.EMPTY.copy(headquartersRegion = HeadquartersRegion.SEJONG))),
            OnboardingItem.CONSTRUCTION_CAPACITY to
                (constructionRegistered to constructionRegistered.copy(capacities = listOf(capacity))),
            OnboardingItem.BUDGET_RANGE to
                (blank to withWatch(emptyWatch.copy(budget = BudgetBound(null, won, emptyWatch.budget.inclusivity)))),
            OnboardingItem.WATCH_CONDITIONS to
                (blank to withWatch(emptyWatch.copy(focusCategories = setOf(CategoryCode("합성업종"))))),
        )

    @Test
    fun `전수 — 표의 모든 항목에 미완·완료 짝이 있다`() {
        pairs.keys shouldBe OnboardingItem.entries.toSet()
    }

    @Test
    fun `전수 — 각 항목은 그 짝에서 미완에서 완료로 옮겨 간다`() {
        pairs.forEach { (item, pair) ->
            val (before, after) = pair
            judgeOnboardingChecklist(before).pending.contains(item) shouldBe true
            judgeOnboardingChecklist(after).completed.contains(item) shouldBe true
        }
    }

    @Test
    fun `완료와 미완은 서로소이고 합은 적용되는 항목 전부다(선언 순서)`() {
        (listOf(blank) + pairs.values.flatMap { it.toList() }).forEach { snapshot ->
            val result = judgeOnboardingChecklist(snapshot)
            result.completed.intersect(result.pending.toSet()) shouldBe emptySet()
            (result.completed + result.pending).toSet() shouldBe
                OnboardingItem.entries.filter { it.applies(snapshot) }.toSet()
        }
    }

    @Test
    fun `빈 계정은 시평액을 뺀 일곱이 전부 미완이다`() {
        val result = judgeOnboardingChecklist(blank)
        result.completed shouldBe emptyList()
        result.pending shouldContainExactly
            OnboardingItem.entries.filter { it != OnboardingItem.CONSTRUCTION_CAPACITY }
    }

    @Test
    fun `시평액 항목은 공사로 등록한 계정에만 나온다`() {
        val service = NaraReadiness.Registered(setOf(NaraRegistrationField.SERVICE), today)
        val serviceOnly = withDetails(BusinessDetails.EMPTY.copy(naraReadiness = service))
        val result = judgeOnboardingChecklist(serviceOnly.copy(capacities = listOf(capacity)))
        (result.completed + result.pending) shouldNotContain OnboardingItem.CONSTRUCTION_CAPACITY
    }

    /** 면허 「미신고」는 운영자가 고른 상태다 — 미완으로 두면 세 상태 납작화 쪽으로 몬다. */
    @Test
    fun `면허 미신고와 신고 빈 목록은 둘 다 완료다 — 미설정만 미완이다`() {
        val notDeclared = withQualification(emptySet())
        val declaredEmpty =
            blank.copy(qualification = ProfileFacts(emptySet(), OperatorLicenses.Declared(emptyList()), emptyList()))

        judgeOnboardingChecklist(notDeclared).completed.contains(OnboardingItem.LICENSES) shouldBe true
        judgeOnboardingChecklist(declaredEmpty).completed.contains(OnboardingItem.LICENSES) shouldBe true
        judgeOnboardingChecklist(blank).pending.contains(OnboardingItem.LICENSES) shouldBe true
    }

    @Test
    fun `나라장터 준비 — 만료일이 오늘이면 완료, 어제면 미완`() {
        fun readiness(expiresOn: LocalDate?) =
            blank.copy(
                details =
                    BusinessDetails.EMPTY.copy(
                        naraReadiness = NaraReadiness.Registered(setOf(NaraRegistrationField.GOODS), expiresOn),
                    ),
            )

        judgeOnboardingChecklist(readiness(today)).completed.contains(OnboardingItem.NARA_READINESS) shouldBe true
        val expired = readiness(today.minusDays(1))
        judgeOnboardingChecklist(expired).pending.contains(OnboardingItem.NARA_READINESS) shouldBe true
        judgeOnboardingChecklist(readiness(null)).pending.contains(OnboardingItem.NARA_READINESS) shouldBe true
    }

    @Test
    fun `나라장터 준비 — 분야가 비었거나 미등록이면 미완`() {
        val noFields =
            withDetails(BusinessDetails.EMPTY.copy(naraReadiness = NaraReadiness.Registered(emptySet(), today)))
        val notRegistered = withDetails(BusinessDetails.EMPTY.copy(naraReadiness = NaraReadiness.NotRegistered))

        judgeOnboardingChecklist(noFields).pending.contains(OnboardingItem.NARA_READINESS) shouldBe true
        judgeOnboardingChecklist(notRegistered).pending.contains(OnboardingItem.NARA_READINESS) shouldBe true
    }
}
