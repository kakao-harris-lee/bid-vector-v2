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

/**
 * D-6F4W-12 — 조립 함수 조각 하나의 경계 표본(전부 blank·일부 blank·전부 비blank·빈 문자열·
 * 공백 낀 비blank). 전건 문자열 열거가 아니라 이 경계들만으로 `isNotBlank` 술어가 실제로
 * 거르는 경계를 덮는다.
 *
 * **verifier r1 LOW-1** — ASCII 공백(`" "`)만 있으면 `filter(String::isNotBlank)`가
 * `filter { it.trim(' ').isNotEmpty() }`로 바뀌어도 이 property test 가 못 잡는다(그
 * 변이는 ASCII 공백만 blank 로 본다). 탭(`\t`)·전각 공백(`　`, 한국어 공고명에서
 * 현실적)·줄바꿈없는 공백(` `)을 더해 그 변이를 닫는다 — Kotlin `Char.isWhitespace`가
 * 셋 다 Java `Character.isWhitespace ∪ isSpaceChar`로 blank 로 보므로 원래 술어와는
 * 여전히 일치한다.
 */
private val TEXT_FRAGMENT_ARB: Arb<String?> =
    Arb.of(null, "", "   ", "x", " x ", "x x", "\t", "　", " ")

private fun noBudgetRules(
    focusRegionTerms: List<String> = emptyList(),
    requiredKeywordTerms: List<String> = emptyList(),
): WatchRules =
    WatchRules(
        focusCategories = emptySet(),
        focusRegionTerms = focusRegionTerms,
        excludeRegionTerms = emptyList(),
        requiredKeywordTerms = requiredKeywordTerms,
        excludeKeywordTerms = emptyList(),
        budget = BudgetBound(null, null, BudgetBoundInclusivity.Inclusive),
    )

private fun subjectOf(
    keyword: KeywordScopeText,
    full: FullScopeText,
): WatchSubject = WatchSubject(emptySet(), keyword, full, Fact.Absent(ReasonCode.EMPTY_INPUT))

/**
 * M6/6F-4(D-6F4-3·3b) — 감시 텍스트 조립 순수 함수. `assembleKeywordScopeText`/
 * `assembleFullScopeText`는 이 slice의 실질이고, 어댑터(M3)에서 이 함수를 실제로 부르는
 * 호출자는 아직 없다(`OPEN-6F4-TITLE-WIRING`, D-6F4-4b — 수집→canonical 배선이 이 slice
 * 밖이다). 그래서 이 test는 함수 자체의 조립 규칙과, `WatchRules.evaluate`를 거친 행동까지
 * 잠근다 — legacy가 주석으로만 막던 오탐을 이 조립 함수는 시그니처로 막는다는 것이 요점이다.
 *
 * **이 배제는 M6/6F-4-w(D-6F4W-7)부터 생성자 자체로 막힌다** — `KeywordScopeText`/`FullScopeText`는
 * `private constructor` + `@ConsistentCopyVisibility` 로 닫혀 이 두 조립 함수(와 그 companion
 * factory)만 값을 낼 수 있다. 그 전(D-6F4-3c, 2026-09-19)에는 결과 타입의 공개 생성자로 이
 * 배제를 우회할 수 있었다는 것이 이 폐쇄의 동기다(verifier r2 MEDIUM-3 — 요건 텍스트로
 * `KeywordScopeText`를 직접 만들어 필수 키워드를 만족시키는 test 가 초록이었다).
 */
class WatchTextAssemblyTest {
    @Test
    fun `키워드 대상은 공고명과 공종을 공백으로 이어 붙인다`() {
        val text = assembleKeywordScopeText(noticeTitle = "정보시스템 유지보수 용역", businessCategoryLabel = "기술용역")
        text.value shouldBe "정보시스템 유지보수 용역 기술용역"
    }

    @Test
    fun `공고명만 있으면 키워드 대상은 그 값만 싣는다`() {
        assembleKeywordScopeText(noticeTitle = "도로 보수 공사", businessCategoryLabel = null).value shouldBe
            "도로 보수 공사"
    }

    @Test
    fun `공종만 있으면 키워드 대상은 그 값만 싣는다`() {
        assembleKeywordScopeText(noticeTitle = null, businessCategoryLabel = "공사").value shouldBe
            "공사"
    }

    /** D-6F4-4b — 값이 없으면 없는 것이다. 빈 문자열을 지어내지 않고 `""`으로 닫는다. */
    @Test
    fun `공고명과 공종이 둘 다 없으면 키워드 대상은 빈 문자열이다`() {
        assembleKeywordScopeText(noticeTitle = null, businessCategoryLabel = null).value shouldBe ""
    }

    @Test
    fun `지역 대상은 키워드 조각 뒤에 발주기관명 둘을 더 잇는다`() {
        val text =
            assembleFullScopeText(
                noticeTitle = "정보시스템 유지보수 용역",
                businessCategoryLabel = "기술용역",
                demandAgencyName = "해양수산부",
                noticeAgencyName = "국가정보자원관리원",
            )
        text.value shouldBe "정보시스템 유지보수 용역 기술용역 해양수산부 국가정보자원관리원"
    }

    /** D-6F4-4b — 네 조각이 전부 없으면 지역 대상도 빈 문자열이다. */
    @Test
    fun `네 조각이 전부 없으면 지역 대상도 빈 문자열이다`() {
        assembleFullScopeText(null, null, null, null).value shouldBe ""
    }

    /**
     * D-6F4-3 — legacy가 주석으로만 막던 오탐(발주기관명이 필수 키워드를 거짓 만족)을
     * [assembleKeywordScopeText]는 시그니처로 막는다(기관명 인자 자체가 없다, D-6F4-3c —
     * 결과 타입을 직접 만들면 우회한다는 한계는 이 함수의 방어 범위 밖). 같은 값이 지역
     * 규칙에는 정당하게 보인다.
     */
    @Test
    fun `발주기관명은 필수 키워드를 만족시키지 않지만 같은 값이 지역 규칙에는 보인다`() {
        val keyword = assembleKeywordScopeText(noticeTitle = "정보시스템 유지보수 용역", businessCategoryLabel = "기술용역")
        val full =
            assembleFullScopeText(
                noticeTitle = "정보시스템 유지보수 용역",
                businessCategoryLabel = "기술용역",
                demandAgencyName = "해양수산부",
                noticeAgencyName = null,
            )
        val subject = subjectOf(keyword, full)

        val keywordVerdict = noBudgetRules(requiredKeywordTerms = listOf("해양수산부")).evaluate(subject)
        keywordVerdict shouldBe WatchVerdict.Rejected(setOf(WatchRuleId.RequiredKeyword))

        val regionVerdict = noBudgetRules(focusRegionTerms = listOf("해양수산부")).evaluate(subject)
        regionVerdict.shouldBeInstanceOf<WatchVerdict.Passed>()
        regionVerdict.matched shouldBe setOf(WatchRuleId.FocusRegion)
    }

    /**
     * D-6F4-3b — V2 `qualification_text`(면허제한 오퍼레이션 응답: 면허명·허용업종·업종분야)는
     * [assembleKeywordScopeText]의 인자 자체가 아니다. 면허 어휘를 필수 키워드로 걸어도
     * 공고명·공종에 없으면 매칭되지 않는다 — 요건 텍스트가 애초에 조립에 들어올 자리가
     * 없다는 것을 행동으로 보인다(legacy `requirements`처럼 업무 서술이 아니라는 실측,
     * scope.md D-6F4-3b).
     */
    @Test
    fun `면허 요건 어휘는 필수 키워드를 만족시키지 않는다`() {
        val keyword = assembleKeywordScopeText(noticeTitle = "정보시스템 유지보수 용역", businessCategoryLabel = "기술용역")
        val full = assembleFullScopeText("정보시스템 유지보수 용역", "기술용역", null, null)
        val subject = subjectOf(keyword, full)

        val verdict = noBudgetRules(requiredKeywordTerms = listOf("전기공사업")).evaluate(subject)

        verdict shouldBe WatchVerdict.Rejected(setOf(WatchRuleId.RequiredKeyword))
    }

    /** 값이 없는 축(공고명·공종 둘 다 null)에서도 조립·판정이 예외 없이 값으로 흐른다. */
    @Test
    fun `공고명과 공종이 둘 다 없어도 감시 판정은 예외 없이 값으로 흐른다`() {
        val keyword = assembleKeywordScopeText(noticeTitle = null, businessCategoryLabel = null)
        val full = assembleFullScopeText(null, null, demandAgencyName = "해양수산부", noticeAgencyName = null)
        val subject = subjectOf(keyword, full)

        noBudgetRules(requiredKeywordTerms = listOf("기술용역")).evaluate(subject) shouldBe
            WatchVerdict.Rejected(setOf(WatchRuleId.RequiredKeyword))
        noBudgetRules(focusRegionTerms = listOf("해양수산부")).evaluate(subject).shouldBeInstanceOf<WatchVerdict.Passed>()
    }

    /**
     * D-6F4W-12 — 「출력은 `""` 이거나 비공백 문자를 포함한다」는 D-6F4W-2(부재 → `Found`
     * (빈 텍스트))가 기대는 불변식이다. `joinNonBlankParts`의 `isNotBlank` 술어 한 글자가
     * `isNotEmpty`로 바뀌면 조용히 깨진다(부재가 `""`와 공백 문자열 두 모양으로 갈려
     * 하류의 `isEmpty()`가 후자를 놓친다) — 이 test 가 그 술어를 잠근다.
     */
    @Test
    fun `D-6F4W-12 조립 결과는 완전히 비거나 비공백 문자를 포함한다 — 경계 조합 property`() {
        runBlocking {
            checkAll(TEXT_FRAGMENT_ARB, TEXT_FRAGMENT_ARB, TEXT_FRAGMENT_ARB, TEXT_FRAGMENT_ARB) {
                title,
                label,
                demandAgencyName,
                noticeAgencyName,
                ->
                val keyword = assembleKeywordScopeText(title, label)
                val full = assembleFullScopeText(title, label, demandAgencyName, noticeAgencyName)

                withClue("keywordText=\"${keyword.value}\"") {
                    (keyword.value.isEmpty() || keyword.value.isNotBlank()) shouldBe true
                }
                withClue("fullText=\"${full.value}\"") {
                    (full.value.isEmpty() || full.value.isNotBlank()) shouldBe true
                }
            }
        }
    }
}
