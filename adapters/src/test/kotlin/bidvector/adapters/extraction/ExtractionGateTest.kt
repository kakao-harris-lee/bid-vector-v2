package bidvector.adapters.extraction

import bidvector.procurement.FetchedDocument
import bidvector.sharedkernel.Fact
import bidvector.sharedkernel.ReasonCode
import bidvector.strategy.BudgetBound
import bidvector.strategy.BudgetBoundInclusivity
import bidvector.strategy.CategoryCode
import bidvector.strategy.FullScopeText
import bidvector.strategy.KeywordScopeText
import bidvector.strategy.WatchRules
import bidvector.strategy.WatchSubject
import bidvector.strategy.WatchVerdict
import bidvector.strategy.evaluate
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import java.time.Instant

private val RULES =
    WatchRules(
        focusCategories = setOf(CategoryCode("공사")),
        focusRegionTerms = emptyList(),
        excludeRegionTerms = emptyList(),
        requiredKeywordTerms = emptyList(),
        excludeKeywordTerms = emptyList(),
        budget = BudgetBound(null, null, BudgetBoundInclusivity.Inclusive),
    )

private fun subject(category: String) =
    WatchSubject(
        categories = setOf(CategoryCode(category)),
        keywordText = KeywordScopeText(""),
        fullText = FullScopeText(""),
        baseAmount = Fact.Absent(ReasonCode.POLICY_NOT_APPLICABLE),
    )

private fun fetchedDocument() =
    FetchedDocument(
        bytes = "면허: 전기공사업".toByteArray(),
        mediaType = "text/plain",
        sha256 = "deadbeef",
        fetchedAt = Instant.EPOCH,
        sourceUrl = sampleAttachmentUrl("https://example.test/spec.txt"),
    )

/**
 * M3/3C ② D-3C-6 — 감시 게이트는 **`WatchVerdict.Passed`가 아니면 LLM port 를 전혀
 * 부르지 않는다**. `WatchVerdict.Passed`의 유일한 생성 경로는 1E `WatchRules.evaluate`
 * 이므로(`internal constructor`), 이 test 는 실제 평가 결과로만 `verdict`를 얻는다
 * (우회 (1) 방어 — test 헬퍼로 `Passed`를 직접 조립할 수 없다는 사실 자체가 컴파일
 * 시점 증거이고, 이 test 는 그 위에 **실행 증거**를 더한다).
 */
class ExtractionGateTest {
    @Test
    fun `탈락한 공고는 LLM port 를 0회 부른다`() {
        val server = FakeLlmServer.start(listOf(FakeLlmResponse.Reply(200, validExtractionResponseJson())))
        server.use {
            val verdict = RULES.evaluate(subject(category = "용역"))
            verdict.shouldBeInstanceOf<WatchVerdict.Rejected>()

            val gated = WatchGatedExtractor(buildExtractor(server))
            val outcome = gated.extract(verdict, fetchedDocument())

            outcome.shouldBeInstanceOf<GatedOutcome.Skipped>()
            server.requestCount shouldBe 0
        }
    }

    @Test
    fun `통과한 공고는 LLM port 를 정확히 1회 부른다`() {
        val server = FakeLlmServer.start(listOf(FakeLlmResponse.Reply(200, validExtractionResponseJson())))
        server.use {
            val verdict = RULES.evaluate(subject(category = "공사"))
            verdict.shouldBeInstanceOf<WatchVerdict.Passed>()

            val gated = WatchGatedExtractor(buildExtractor(server, policy = testExtractionPolicy(chunkChars = 400)))
            val outcome = gated.extract(verdict, fetchedDocument())

            outcome.shouldBeInstanceOf<GatedOutcome.Ran>()
            server.requestCount shouldBe 1
        }
    }

    @Test
    fun `NoGate 도 LLM port 를 0회 부른다`() {
        val server = FakeLlmServer.start(listOf(FakeLlmResponse.Reply(200, validExtractionResponseJson())))
        server.use {
            val noRules = WatchRules.empty(BudgetBoundInclusivity.Inclusive)
            val verdict = noRules.evaluate(subject(category = "공사"))
            verdict.shouldBeInstanceOf<WatchVerdict.NoGate>()

            val outcome = WatchGatedExtractor(buildExtractor(server)).extract(verdict, fetchedDocument())

            outcome.shouldBeInstanceOf<GatedOutcome.Skipped>()
            server.requestCount shouldBe 0
        }
    }
}
