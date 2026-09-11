package bidvector.workflow.evaluation

import bidvector.sharedkernel.Resolution
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File
import java.time.LocalDate
import java.util.Properties

/**
 * [OpportunityPolicyData] 불변식(scope.md ⑥) + 출하 `OPPORTUNITY_POLICY` 값 대조
 * (D-4B6A-4 — `textMaxChars`는 2E `contract-policy.properties`의
 * `embedding.text.max-chars`와 같아야 한다). 정책 파일은 `ContractPolicySupport`(adapters
 * test 전용)를 재사용할 수 없어(모듈 경계) 이 test가 직접 상대 경로로 읽는다
 * (`MlGateRegistrationTest`가 `gate-tests.properties`를 읽는 것과 같은 관례).
 */
class OpportunityPolicyDataTest {
    @Test
    fun `keywords 에 공백 항목이 있으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            OpportunityPolicyData(SynthesisVersion("v1"), listOf("보안", "  "), 4000)
        }
    }

    @Test
    fun `keywords 가 비어 있으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            OpportunityPolicyData(SynthesisVersion("v1"), emptyList(), 4000)
        }
    }

    @Test
    fun `keywords 에 중복이 있으면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            OpportunityPolicyData(SynthesisVersion("v1"), listOf("보안", "보안"), 4000)
        }
    }

    @Test
    fun `keywords 에 대문자가 섞이면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            OpportunityPolicyData(SynthesisVersion("v1"), listOf("Cloud"), 4000)
        }
    }

    @Test
    fun `keywords 가 전부 소문자면 생성 성공`() {
        OpportunityPolicyData(SynthesisVersion("v1"), listOf("cloud", "보안"), 4000).keywords shouldBe
            listOf("cloud", "보안")
    }

    @Test
    fun `textMaxChars 가 0 이하면 생성 실패`() {
        shouldThrow<IllegalArgumentException> {
            OpportunityPolicyData(SynthesisVersion("v1"), listOf("보안"), 0)
        }
        shouldThrow<IllegalArgumentException> {
            OpportunityPolicyData(SynthesisVersion("v1"), listOf("보안"), -1)
        }
    }

    @Test
    fun `출하 값 — 키워드 14, legacy EXECUTION_COMPLEXITY_KEYWORDS 와 일치`() {
        val policy = OPPORTUNITY_POLICY.resolve(LocalDate.of(2026, 9, 11)).shouldBeResolved()

        policy.keywords shouldBe
            listOf(
                "통합",
                "고도화",
                "운영",
                "유지관리",
                "24시간",
                "대규모",
                "다기관",
                "클라우드",
                "센터",
                "실시간",
                "연계",
                "보안",
                "이관",
                "플랫폼",
            )
        policy.keywords.size shouldBe 14
    }

    @Test
    fun `출하 값 — synthesisVersion v1`() {
        val policy = OPPORTUNITY_POLICY.resolve(LocalDate.of(2026, 9, 11)).shouldBeResolved()

        policy.synthesisVersion shouldBe SynthesisVersion("v1")
    }

    @Test
    fun `출하 값 — textMaxChars 가 2E contract-policy properties 의 embedding text max-chars 와 같다`() {
        val policy = OPPORTUNITY_POLICY.resolve(LocalDate.of(2026, 9, 11)).shouldBeResolved()

        val propertiesFile = File("../config/quality/contract-policy.properties")
        check(propertiesFile.isFile) { "정책 파일을 찾지 못했다: ${propertiesFile.absolutePath}" }
        val properties = Properties().apply { propertiesFile.reader(Charsets.UTF_8).use(::load) }
        val embeddingTextMaxChars =
            properties.getProperty("embedding.text.max-chars")?.trim()?.toIntOrNull()
                ?: error("정책 키 'embedding.text.max-chars' 가 없거나 정수가 아니다")

        policy.textMaxChars shouldBe embeddingTextMaxChars
    }

    private fun <T> Resolution<T>.shouldBeResolved(): T {
        check(this is Resolution.Resolved<T>) { "정책이 resolve 되지 않았다: $this" }
        return value
    }
}
