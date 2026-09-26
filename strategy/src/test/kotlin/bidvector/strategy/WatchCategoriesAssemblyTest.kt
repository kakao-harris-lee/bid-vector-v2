package bidvector.strategy

import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.ReasonCode
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.of
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

private val BLANK_FRAGMENT_ARB: Arb<String?> =
    Arb.of(null, "", "   ", "\t", "\n", "\u3000", "\u00A0")

private fun subjectOf(categories: Set<CategoryCode>): WatchSubject =
    WatchSubject(
        categories = categories,
        keywordText = assembleKeywordScopeText("공고명", null),
        fullText = assembleFullScopeText("공고명", null, null, null),
        baseAmount = Fact.Absent(ReasonCode.EMPTY_INPUT),
    )

private fun focusOn(vararg terms: String): WatchRules =
    WatchRules(
        focusCategories = terms.map(::CategoryCode).toSet(),
        focusRegionTerms = emptyList(),
        excludeRegionTerms = emptyList(),
        requiredKeywordTerms = emptyList(),
        excludeKeywordTerms = emptyList(),
        budget = BudgetBound(null, null, BudgetBoundInclusivity.Inclusive),
    )

/**
 * D-6F9-4(M6/6F-9) — 감시 「관심 업종」 집합 조립. 대분류·용역구분·공공조달분류 코드·주공종 네 값 중 **비어 있지 않은 것만**
 * 모은다. 어댑터가 집합을 스스로 짓지 않고(우회: 빈 값·공백 코드가 집합에 들어가 모든 규칙과 맞거나 아무것과도 안 맞는다)
 * 이 커널을 부르며, 값의 정규화(trim)와 빈 값 제외 규칙이 이 한 자리에 있다. 비교는 규칙 쪽이 양쪽을 trim·대소문자 접기로
 * 한다(`WatchRules`) — 이 커널은 비교 규칙을 다시 쓰지 않는다.
 */
class WatchCategoriesAssemblyTest {
    private fun categoriesOf(
        businessDivision: String? = null,
        serviceDivision: String? = null,
        classificationCode: String? = null,
        mainConstructionType: String? = null,
    ): Set<CategoryCode> =
        assembleWatchCategories(businessDivision, serviceDivision, classificationCode, mainConstructionType)

    @Test
    fun `네 값이 모두 있으면 네 원소다 — 각자 자기 값이고 서로 접히지 않는다`() {
        categoriesOf("용역", "기술용역", "81111500", "전기공사업") shouldBe
            setOf(CategoryCode("용역"), CategoryCode("기술용역"), CategoryCode("81111500"), CategoryCode("전기공사업"))
    }

    @Test
    fun `값 하나만 있으면 그 원소 하나뿐이다 — 인자 자리가 바뀌어도 값이 사라지지 않는다`() {
        categoriesOf(businessDivision = "공사") shouldBe setOf(CategoryCode("공사"))
        categoriesOf(serviceDivision = "일반용역") shouldBe setOf(CategoryCode("일반용역"))
        categoriesOf(classificationCode = "abc123") shouldBe setOf(CategoryCode("abc123"))
        categoriesOf(mainConstructionType = "토목공사업") shouldBe setOf(CategoryCode("토목공사업"))
    }

    @Test
    fun `값이 없으면 빈 집합이다`() {
        categoriesOf() shouldBe emptySet()
    }

    @Test
    fun `원소는 trim 된 값이다 — 앞뒤 공백을 싣지 않는다`() {
        categoriesOf(businessDivision = " 공사 ", mainConstructionType = "\t전기공사업\u3000") shouldBe
            setOf(CategoryCode("공사"), CategoryCode("전기공사업"))
    }

    @Test
    fun `빈 값과 공백류만 있는 값은 어느 자리에서도 원소가 되지 않는다 — 경계 표본 전수`() {
        runBlocking {
            checkAll(BLANK_FRAGMENT_ARB, BLANK_FRAGMENT_ARB, BLANK_FRAGMENT_ARB, BLANK_FRAGMENT_ARB) { a, b, c, d ->
                categoriesOf(a, b, c, d) shouldBe emptySet()
            }
        }
    }

    @Test
    fun `빈 값이 섞여도 비어 있지 않은 값만 남는다`() {
        categoriesOf("용역", " ", null, "\u3000") shouldBe setOf(CategoryCode("용역"))
    }

    @Test
    fun `같은 값이 두 자리에 와도 집합이라 하나다`() {
        categoriesOf(businessDivision = "용역", serviceDivision = "용역") shouldBe setOf(CategoryCode("용역"))
    }

    @Test
    fun `행동 — 운영자가 관심 업종에 대분류·용역구분·주공종·분류번호 무엇을 적어도 맞는다 — 공백·대소문자는 규칙이 접는다`() {
        val subject = subjectOf(categoriesOf("공사", "기술용역", "AB12", "전기공사업"))

        listOf("공사", "기술용역", "ab12", " 전기공사업 ").forEach { focus ->
            withClue(focus) { focusOn(focus).evaluate(subject).shouldBeInstanceOf<WatchVerdict.Passed>() }
        }
        listOf("용역", "건축공사업", "물품").forEach { focus ->
            withClue(focus) {
                focusOn(focus).evaluate(subject) shouldBe WatchVerdict.Rejected(setOf(WatchRuleId.FocusCategory))
            }
        }
    }

    @Test
    fun `행동 — 업무구분이 하나도 없는 공고는 어떤 관심 업종과도 맞지 않는다 — 빈 값이 모든 규칙과 맞지 않는다`() {
        val nothing = subjectOf(categoriesOf(" ", null, "", "\t"))

        focusOn("공사", "기술용역").evaluate(nothing) shouldBe
            WatchVerdict.Rejected(setOf(WatchRuleId.FocusCategory))
    }
}
