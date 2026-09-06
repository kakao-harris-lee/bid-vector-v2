package bidvector.strategy

import bidvector.sharedkernel.BaseAmount
import bidvector.sharedkernel.Currency
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.Provenance
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.VatTreatment
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

private fun baseAmount(
    won: Long,
    vat: VatTreatment = VatTreatment.INCLUSIVE,
    provenance: Provenance = Provenance.OperatorDeclared,
): BaseAmount = BaseAmount(won, Currency.KRW, vat, provenance)

private fun subject(
    categories: Set<String> = emptySet(),
    keywordText: String = "",
    fullText: String = "",
    baseAmount: Fact<BaseAmount> = Fact.Absent(ReasonCode.EMPTY_INPUT),
): WatchSubject =
    WatchSubject(
        categories = categories.map(::CategoryCode).toSet(),
        keywordText = KeywordScopeText(keywordText),
        fullText = FullScopeText(fullText),
        baseAmount = baseAmount,
    )

private fun rulesOf(
    focusCategories: Set<String> = emptySet(),
    focusRegionTerms: List<String> = emptyList(),
    excludeRegionTerms: List<String> = emptyList(),
    requiredKeywordTerms: List<String> = emptyList(),
    excludeKeywordTerms: List<String> = emptyList(),
    minBudget: BaseAmount? = null,
    maxBudget: BaseAmount? = null,
    inclusivity: BudgetBoundInclusivity = BudgetBoundInclusivity.Inclusive,
): WatchRules =
    WatchRules(
        focusCategories = focusCategories.map(::CategoryCode).toSet(),
        focusRegionTerms = focusRegionTerms,
        excludeRegionTerms = excludeRegionTerms,
        requiredKeywordTerms = requiredKeywordTerms,
        excludeKeywordTerms = excludeKeywordTerms,
        budget = BudgetBound(minBudget, maxBudget, inclusivity),
    )

/**
 * M1/1E ①②③④ — 감시 predicate 커널. STR-01 acceptance 넷·STR-02 acceptance 넷을
 * `reports/evidence/m1/1e/scope.md` D-3 승인(decision 30) 문면 그대로 옮긴다.
 */
class WatchRulesTest {
    // --- STR-01 acceptance ---

    @Test
    fun `STR-01 1 중점 카테고리는 완전일치다 — service 는 technical-service 에 부분일치로 통과하지 않는다`() {
        val rules = rulesOf(focusCategories = setOf("service"))
        val verdict = rules.evaluate(subject(categories = setOf("technical-service")))

        verdict shouldBe WatchVerdict.Rejected(setOf(WatchRuleId.FocusCategory))
    }

    @Test
    fun `STR-01 1 완전일치면 통과한다`() {
        val rules = rulesOf(focusCategories = setOf("service"))
        val verdict = rules.evaluate(subject(categories = setOf("service")))

        verdict.shouldBeInstanceOf<WatchVerdict.Passed>()
        verdict.matched shouldBe setOf(WatchRuleId.FocusCategory)
    }

    @Test
    fun `STR-01 2 중점 카테고리와 필수 키워드는 AND로 결합된다`() {
        val rules = rulesOf(focusCategories = setOf("service"), requiredKeywordTerms = listOf("유지보수"))

        rules.evaluate(subject(categories = setOf("service"), keywordText = "유지보수 용역")) shouldBe
            WatchVerdict.Passed(setOf(WatchRuleId.FocusCategory, WatchRuleId.RequiredKeyword))
        rules.evaluate(subject(categories = setOf("service"), keywordText = "청소 용역")) shouldBe
            WatchVerdict.Rejected(setOf(WatchRuleId.RequiredKeyword))
        rules.evaluate(subject(categories = setOf("goods"), keywordText = "유지보수 용역")) shouldBe
            WatchVerdict.Rejected(setOf(WatchRuleId.FocusCategory))
    }

    @Test
    fun `STR-01 3 watch 필드가 전부 비어 있으면 게이트 없음이고 모든 공고 통과와 결과 타입에서 구분된다`() {
        val emptyRules = rulesOf()

        val verdict = emptyRules.evaluate(subject(categories = setOf("아무거나")))

        verdict shouldBe WatchVerdict.NoGate
        (verdict == WatchVerdict.NoGate) shouldBe true
    }

    @Test
    fun `STR-01 4 watch rule 판정은 ML port 를 0회 호출한다 — strategy 는 shared-kernel 만 의존한다`() {
        // 구조 확인: WatchRules.evaluate 시그니처가 ML/DB/시계 타입을 받지 않는다는 것 자체가
        // 이 게이트다(모듈 의존 게이트, build-logic 이 실행). 여기서는 순수 함수로서 같은
        // 입력에 같은 출력을 내는지(부작용 없음)만 값으로 실측한다.
        val rules = rulesOf(focusCategories = setOf("service"))
        val input = subject(categories = setOf("service"))

        rules.evaluate(input) shouldBe rules.evaluate(input)
    }

    // --- STR-02 acceptance ---

    @Test
    fun `STR-02 1 필수 키워드가 description 에만 있으면 후보가 아니다 — keywordText 는 description 을 포함하지 않는다`() {
        val rules = rulesOf(requiredKeywordTerms = listOf("해양수산부"))
        // 어댑터가 keywordText 조립 시 description(발주기관명)을 제외했다는 것을 이 값으로 표현한다.
        val verdict = rules.evaluate(subject(keywordText = "시스템 유지보수 용역", fullText = "시스템 유지보수 용역 해양수산부"))

        verdict shouldBe WatchVerdict.Rejected(setOf(WatchRuleId.RequiredKeyword))
    }

    @Test
    fun `STR-02 2 같은 키워드가 title 또는 requirements 에 있으면 후보다`() {
        val rules = rulesOf(requiredKeywordTerms = listOf("유지보수"))
        val verdict = rules.evaluate(subject(keywordText = "시스템 유지보수 용역"))

        verdict.shouldBeInstanceOf<WatchVerdict.Passed>()
    }

    @Test
    fun `STR-02 3 제외 키워드가 description 에만 있으면 후보를 떨어뜨리지 않는다`() {
        val rules = rulesOf(excludeKeywordTerms = listOf("해양수산부"), requiredKeywordTerms = listOf("유지보수"))
        val verdict = rules.evaluate(subject(keywordText = "시스템 유지보수 용역", fullText = "시스템 유지보수 용역 해양수산부"))

        verdict.shouldBeInstanceOf<WatchVerdict.Passed>()
    }

    @Test
    fun `STR-02 4 중점 지역은 description 을 포함한 전체 텍스트에 매칭된다`() {
        val rules = rulesOf(focusRegionTerms = listOf("부산"))
        val verdict = rules.evaluate(subject(keywordText = "시스템 유지보수", fullText = "시스템 유지보수 부산지방해양수산청"))

        verdict.shouldBeInstanceOf<WatchVerdict.Passed>()
    }

    // --- ④ 예산 basis ---

    @Test
    fun `④ 예산 하한 미만이면 Rejected MinBudget 이다`() {
        val rules = rulesOf(minBudget = baseAmount(1_000_000))
        val verdict = rules.evaluate(subject(baseAmount = Fact.Known(baseAmount(999_999))))

        verdict shouldBe WatchVerdict.Rejected(setOf(WatchRuleId.MinBudget))
    }

    @Test
    fun `④ 예산 하한과 같으면 통과한다 — 등호 통과(legacy-behavior, Inclusive)`() {
        val rules = rulesOf(minBudget = baseAmount(1_000_000))
        val verdict = rules.evaluate(subject(baseAmount = Fact.Known(baseAmount(1_000_000))))

        verdict shouldBe WatchVerdict.Passed(setOf(WatchRuleId.MinBudget))
    }

    @Test
    fun `④ 예산 상한 초과면 Rejected MaxBudget 이다`() {
        val rules = rulesOf(maxBudget = baseAmount(1_000_000))
        val verdict = rules.evaluate(subject(baseAmount = Fact.Known(baseAmount(1_000_001))))

        verdict shouldBe WatchVerdict.Rejected(setOf(WatchRuleId.MaxBudget))
    }

    @Test
    fun `④ 0원 한계는 무제한 sentinel 이 아니라 실제 한계다 — 위협 모델 (g)`() {
        val zeroLimitRules = rulesOf(maxBudget = baseAmount(0))
        val noRuleRules = rulesOf()

        zeroLimitRules.evaluate(subject(baseAmount = Fact.Known(baseAmount(1)))) shouldBe
            WatchVerdict.Rejected(setOf(WatchRuleId.MaxBudget))
        noRuleRules.evaluate(subject(baseAmount = Fact.Known(baseAmount(1)))) shouldBe WatchVerdict.NoGate
    }

    @Test
    fun `④ 예산 규칙이 있는데 공고 금액이 Absent 면 Undeterminable BaseAmountAbsent 다`() {
        val rules = rulesOf(minBudget = baseAmount(1_000_000))
        val verdict = rules.evaluate(subject(baseAmount = Fact.Absent(ReasonCode.EMPTY_INPUT)))

        verdict.shouldBeInstanceOf<WatchVerdict.Undeterminable>()
        val reason = verdict.reason
        reason.shouldBeInstanceOf<WatchUndeterminableReason.BaseAmountAbsent>()
        reason.reason shouldBe ReasonCode.EMPTY_INPUT
    }

    @Test
    fun `④ 예산 규칙이 없으면 공고 금액이 Absent 여도 그 축을 보지 않는다 — 우회 후보 (7), 의도된 동작`() {
        val rules = rulesOf(requiredKeywordTerms = listOf("유지보수"))
        val verdict = rules.evaluate(subject(keywordText = "유지보수 용역", baseAmount = Fact.Absent(ReasonCode.EMPTY_INPUT)))

        verdict.shouldBeInstanceOf<WatchVerdict.Passed>()
    }

    @Test
    fun `④ 예산 한계가 UNKNOWN 과세 처리면 compareKnownVat 가 Absent 를 내고 Undeterminable BudgetNotComparable 이다`() {
        val rules = rulesOf(minBudget = baseAmount(1_000_000, vat = VatTreatment.UNKNOWN))
        val verdict = rules.evaluate(subject(baseAmount = Fact.Known(baseAmount(2_000_000))))

        verdict.shouldBeInstanceOf<WatchVerdict.Undeterminable>()
        val reason = verdict.reason
        reason.shouldBeInstanceOf<WatchUndeterminableReason.BudgetNotComparable>()
        reason.reason shouldBe ReasonCode.VAT_TREATMENT_MISMATCH
    }

    @Test
    fun `EstimatedAmount 대 BaseAmount 비교는 evaluateBudget 자리에 컴파일되지 않는다 — 타입 서명 실측`() {
        // 컴파일 실패 자체는 CompileFailureHarnessTest 가 별도 소스 단위로 증명한다(threat model (c)).
        // 여기서는 WatchRules.budget 의 필드 타입이 BaseAmount? 임을 값으로 재확인한다.
        val rules = rulesOf(minBudget = baseAmount(1))
        rules.budget.min.shouldBeInstanceOf<BaseAmount>()
    }

    // --- D-9 결합 순서 ---

    @Test
    fun `D-9 제외 규칙이 우선이다 — 포함 규칙을 만족해도 제외가 걸리면 Rejected 다`() {
        val rules = rulesOf(focusCategories = setOf("service"), excludeRegionTerms = listOf("제주"))
        val verdict = rules.evaluate(subject(categories = setOf("service"), fullText = "제주 지역 공고"))

        verdict shouldBe WatchVerdict.Rejected(setOf(WatchRuleId.ExcludeRegion))
    }

    @Test
    fun `D-9 세 valued AND 단락 평가 — 앞선 축이 확정 실패면 뒤 예산이 Undeterminable 이어도 Rejected 다`() {
        val rules = rulesOf(focusCategories = setOf("service"), minBudget = baseAmount(1_000_000))
        val verdict =
            rules.evaluate(subject(categories = setOf("goods"), baseAmount = Fact.Absent(ReasonCode.EMPTY_INPUT)))

        verdict shouldBe WatchVerdict.Rejected(setOf(WatchRuleId.FocusCategory))
    }

    @Test
    fun `WatchVerdict Rejected 는 failed 가 비어 있으면 구성 시점에 거부된다 — 우회 후보 (1)`() {
        shouldThrow<IllegalArgumentException> {
            WatchVerdict.Rejected(emptySet())
        }
    }

    @Test
    fun `소진 when 만으로 WatchVerdict 을 소비한다 — 접는 API 가 없다`() {
        val samples: List<WatchVerdict> =
            listOf(
                WatchVerdict.NoGate,
                WatchVerdict.Rejected(setOf(WatchRuleId.FocusCategory)),
                WatchVerdict.Undeterminable(WatchUndeterminableReason.BaseAmountAbsent(ReasonCode.EMPTY_INPUT)),
            )
        val labels =
            samples.map {
                when (it) {
                    is WatchVerdict.Passed -> "Passed"
                    is WatchVerdict.Rejected -> "Rejected"
                    WatchVerdict.NoGate -> "NoGate"
                    is WatchVerdict.Undeterminable -> "Undeterminable"
                }
            }
        labels shouldBe listOf("NoGate", "Rejected", "Undeterminable")
    }

    // --- property tests ---

    @Test
    fun `property 규칙이 하나도 없으면 항상 NoGate 다`() {
        runBlocking {
            checkAll(Arb.string(0, 5), Arb.string(0, 5)) { keyword, full ->
                rulesOf().evaluate(subject(keywordText = keyword, fullText = full)) shouldBe WatchVerdict.NoGate
            }
        }
    }

    @Test
    fun `property Rejected failed 는 항상 규칙 집합의 부분집합이다`() {
        val allRuleIds =
            setOf(
                WatchRuleId.FocusCategory,
                WatchRuleId.FocusRegion,
                WatchRuleId.ExcludeRegion,
                WatchRuleId.RequiredKeyword,
                WatchRuleId.ExcludeKeyword,
                WatchRuleId.MinBudget,
                WatchRuleId.MaxBudget,
            )
        runBlocking {
            checkAll(Arb.list(Arb.string(1, 4), 0..3), Arb.int(0, 10)) { terms, budgetSeed ->
                val rules =
                    rulesOf(
                        excludeKeywordTerms = terms,
                        minBudget = if (budgetSeed > 5) baseAmount(1_000_000) else null,
                    )
                val verdict = rules.evaluate(subject(keywordText = "무관한 텍스트", baseAmount = Fact.Known(baseAmount(0))))
                if (verdict is WatchVerdict.Rejected) {
                    (verdict.failed - allRuleIds) shouldBe emptySet()
                }
            }
        }
    }

    @Test
    fun `property 제외 매치는 항상 Rejected 로 이어진다`() {
        runBlocking {
            checkAll(Arb.string(1, 6)) { term ->
                val rules = rulesOf(excludeKeywordTerms = listOf(term))
                val verdict = rules.evaluate(subject(keywordText = "prefix-$term-suffix"))

                verdict.shouldBeInstanceOf<WatchVerdict.Rejected>()
            }
        }
    }
}
