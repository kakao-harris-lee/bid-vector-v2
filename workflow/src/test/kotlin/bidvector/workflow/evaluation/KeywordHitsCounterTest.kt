package bidvector.workflow.evaluation

import bidvector.strategy.FullScopeText
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * 정책 키워드 매칭(4B-5 D-4B5-5 인계, legacy `opportunity_analysis/scoring.py`
 * `_estimate_execution_complexity_score` — `sum(1 for keyword in KEYWORDS if keyword in
 * text)`) — 소문자 부분 문자열, 같은 키워드 중복 출현은 1 회.
 */
class KeywordHitsCounterTest {
    @Test
    fun `일치 0건이면 count 0`() {
        val hits = KeywordHitsCounter.count(FullScopeText("아무 관련 없는 전문입니다"), TEST_POLICY)

        hits.count shouldBe 0
    }

    @Test
    fun `대소문자 무관 — 영문 키워드는 소문자 매칭`() {
        val policy = TEST_POLICY.copy(keywords = listOf("cloud"))

        val hits = KeywordHitsCounter.count(FullScopeText("CLOUD 인프라 구축"), policy)

        hits.count shouldBe 1
    }

    @Test
    fun `같은 키워드가 여러 번 나와도 1 회만 센다`() {
        val policy = TEST_POLICY.copy(keywords = listOf("보안"))

        val hits = KeywordHitsCounter.count(FullScopeText("보안 보안 보안 시스템"), policy)

        hits.count shouldBe 1
    }

    @Test
    fun `정책 키워드 14 전부가 텍스트에 있으면 count 14`() {
        val fullText =
            FullScopeText(
                OPPORTUNITY_POLICY.entries
                    .single()
                    .second.keywords
                    .joinToString(" "),
            )
        val policy = OPPORTUNITY_POLICY.entries.single().second

        val hits = KeywordHitsCounter.count(fullText, policy)

        hits.count shouldBe 14
    }

    @Test
    fun `일부만 일치하면 일치한 수만 센다`() {
        val policy = TEST_POLICY.copy(keywords = listOf("보안", "클라우드"))

        val hits = KeywordHitsCounter.count(FullScopeText("클라우드 기반 서비스"), policy)

        hits.count shouldBe 1
    }

    @Test
    fun `verifier F-3 — 단어 내부에 나타나도 매치된다(단어 경계를 보지 않는다)`() {
        val policy = TEST_POLICY.copy(keywords = listOf("보안"))

        val hits = KeywordHitsCounter.count(FullScopeText("정보안내 시스템"), policy)

        hits.count shouldBe 1
    }

    @Test
    fun `verifier F-3 — 글자 사이에 공백이 끼면 매치되지 않는다`() {
        val policy = TEST_POLICY.copy(keywords = listOf("보안"))

        val hits = KeywordHitsCounter.count(FullScopeText("보 안 시스템"), policy)

        hits.count shouldBe 0
    }
}
