package bidvector.workflow.evaluation

import bidvector.qualification.LicenseName
import bidvector.qualification.OperatorLicenses
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.ReasonCode
import bidvector.sharedkernel.RoundingPolicy
import bidvector.strategy.CategoryCode
import bidvector.strategy.FullScopeText
import bidvector.strategy.KeywordScopeText
import bidvector.strategy.WatchSubject
import bidvector.workflow.embedding.TextKind
import bidvector.workflow.prediction.ModelReleaseSelector
import bidvector.workflow.prediction.OptimizationObjective
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration

/**
 * 텍스트 합성 규약 v1(scope.md D-4B6A-1~4, 설계 검토 (5) 1) — golden 텍스트·상한 절단·
 * 금액/마감/id 부재·version·allow-list 구조를 고정한다. golden 값은 [TextSynthesis.kt]
 * KDoc이 고정한 규약(카테고리·키워드텍스트·전문을 공백으로 이어 붙이고 빈 조각은
 * 생략)에서 유도했다.
 */
class TextSynthesisTest {
    @Test
    fun `NOTICE golden 텍스트 — 카테고리 정렬, 키워드텍스트, 전문 순서로 공백 결합`() {
        val subject =
            WatchSubject(
                categories = setOf(CategoryCode("B-CAT"), CategoryCode("A-CAT")),
                keywordText = KeywordScopeText("클라우드 보안 시스템 구축"),
                fullText = FullScopeText("서울특별시 발주 클라우드 인프라 통합 구축 사업 개요"),
                baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
            )

        val outcome = synthesizeNoticeText(subject, TEST_POLICY)

        val synthesized = (outcome as SynthesisOutcome.Synthesized).text
        synthesized.value shouldBe
            "A-CAT B-CAT 클라우드 보안 시스템 구축 서울특별시 발주 클라우드 인프라 통합 구축 사업 개요"
        synthesized.kind shouldBe TextKind.NOTICE
        synthesized.version shouldBe TEST_POLICY.synthesisVersion
    }

    @Test
    fun `NOTICE 합성은 baseAmount 값과 무관하다 — 금액은 인자에서 읽히지 않는다`() {
        val absentForPolicy =
            WatchSubject(
                categories = setOf(CategoryCode("CAT")),
                keywordText = KeywordScopeText("키워드"),
                fullText = FullScopeText("전문"),
                baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
            )
        val absentForOtherReason = absentForPolicy.copy(baseAmount = Fact.Absent(ReasonCode.EMPTY_INPUT))

        val firstText = (synthesizeNoticeText(absentForPolicy, TEST_POLICY) as SynthesisOutcome.Synthesized).text.value
        val secondText =
            (synthesizeNoticeText(absentForOtherReason, TEST_POLICY) as SynthesisOutcome.Synthesized).text.value

        firstText shouldBe secondText
    }

    @Test
    fun `NOTICE 빈 subject 는 SynthesisOutcome Empty`() {
        val blank =
            WatchSubject(
                categories = emptySet(),
                keywordText = KeywordScopeText(""),
                fullText = FullScopeText("   "),
                baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
            )

        synthesizeNoticeText(blank, TEST_POLICY) shouldBe SynthesisOutcome.Empty
    }

    @Test
    fun `PROFILE golden 텍스트 — 업종 정렬, 면허 정렬, 지역 순서로 공백 결합(allow-list 세 항목만)`() {
        val profile =
            ProfileFacts(
                businessTypes = setOf(CategoryCode("B-TYPE"), CategoryCode("A-TYPE")),
                licenses = OperatorLicenses.Declared(listOf(LicenseName("정보통신공사업"), LicenseName("소프트웨어사업"))),
                regionTerms = listOf("서울", "부산"),
            )

        val outcome = synthesizeProfileText(profile, TEST_POLICY)

        val synthesized = (outcome as SynthesisOutcome.Synthesized).text
        synthesized.value shouldBe "A-TYPE B-TYPE 소프트웨어사업 정보통신공사업 서울 부산"
        synthesized.kind shouldBe TextKind.OPERATOR_PROFILE
        synthesized.version shouldBe TEST_POLICY.synthesisVersion
    }

    @Test
    fun `PROFILE 면허 NotDeclared 는 그 조각을 생략한다 — 존재하지 않는 값을 지어내지 않는다`() {
        val profile =
            ProfileFacts(
                businessTypes = setOf(CategoryCode("A-TYPE")),
                licenses = OperatorLicenses.NotDeclared,
                regionTerms = listOf("서울"),
            )

        val outcome = synthesizeProfileText(profile, TEST_POLICY)

        val synthesized = (outcome as SynthesisOutcome.Synthesized).text
        synthesized.value shouldBe "A-TYPE 서울"
    }

    @Test
    fun `PROFILE 빈 fact 는 SynthesisOutcome Empty`() {
        val blank =
            ProfileFacts(
                businessTypes = emptySet(),
                licenses = OperatorLicenses.NotDeclared,
                regionTerms = emptyList(),
            )

        synthesizeProfileText(blank, TEST_POLICY) shouldBe SynthesisOutcome.Empty
    }

    @Test
    fun `verifier F-1 — NOTICE 절단 뒤 공백만 남으면 Synthesized 가 아니라 Empty`() {
        val shippedPolicy = OPPORTUNITY_POLICY.entries.single().second
        val subject =
            WatchSubject(
                categories = emptySet(),
                keywordText = KeywordScopeText(""),
                fullText = FullScopeText(" ".repeat(shippedPolicy.textMaxChars) + "x"),
                baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
            )

        synthesizeNoticeText(subject, shippedPolicy) shouldBe SynthesisOutcome.Empty
    }

    @Test
    fun `verifier F-1 — PROFILE 절단 뒤 공백만 남으면 Synthesized 가 아니라 Empty`() {
        val shippedPolicy = OPPORTUNITY_POLICY.entries.single().second
        val profile =
            ProfileFacts(
                businessTypes = emptySet(),
                licenses = OperatorLicenses.NotDeclared,
                regionTerms = listOf(" ".repeat(shippedPolicy.textMaxChars) + "x"),
            )

        synthesizeProfileText(profile, shippedPolicy) shouldBe SynthesisOutcome.Empty
    }

    @Test
    fun `상한 초과 텍스트는 뒤를 자른다`() {
        val subject =
            WatchSubject(
                categories = setOf(CategoryCode("CAT")),
                keywordText = KeywordScopeText("키워드텍스트"),
                fullText = FullScopeText("전문에 해당하는 아주 긴 문장을 여기 넣는다"),
                baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
            )
        val untruncated = (synthesizeNoticeText(subject, TEST_POLICY) as SynthesisOutcome.Synthesized).text.value
        val shortPolicy = TEST_POLICY.copy(textMaxChars = 10)

        val truncated = (synthesizeNoticeText(subject, shortPolicy) as SynthesisOutcome.Synthesized).text.value

        truncated.length shouldBe 10
        truncated shouldBe untruncated.substring(0, 10)
    }
}

internal val TEST_POLICY =
    OpportunityPolicyData(
        synthesisVersion = SynthesisVersion("test-v1"),
        keywords = listOf("보안", "클라우드"),
        textMaxChars = 4000,
        embeddingBudget = Duration.ofSeconds(2),
        predictionBudget = Duration.ofSeconds(3),
        releaseSelector = ModelReleaseSelector.LatestPromoted,
        objective = OptimizationObjective.SCENARIO_TRIPLE,
        categoryOffset = BigDecimal.ZERO,
        recommendedAmountRounding = RoundingPolicy(scaleDigits = 0, mode = RoundingMode.HALF_UP),
    )
