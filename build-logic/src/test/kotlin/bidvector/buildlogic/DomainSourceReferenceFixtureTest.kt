package bidvector.buildlogic

import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertTrue

/**
 * 커밋된 fixture 로 소스 게이트의 판정 전체(추출 + 정책)를 잰다 — 설계 검토 §4 「음성 fixture
 * 둘 + 완전수식 판」과 「양성 대조」. task 를 거치지 않고 순수 함수만으로 같은 판정을 재현한다 —
 * task 수준 실측은 `commands.md` 가 갖는다(알려진 제한 12 와 같은 형태).
 */
class DomainSourceReferenceFixtureTest {
    private val policy = SourceReferencePolicy.load(File(REAL_POLICY))

    @Test
    fun `인라인 상수 leak 을 소스 층이 잡는다`() {
        assertViolates(File(VIOLATING, "procurement/InlinedConstantLeak.kt"), "java.net.HttpURLConnection")
    }

    @Test
    fun `class literal leak 을 소스 층이 잡는다`() {
        assertViolates(File(VIOLATING, "decision/ClassLiteralLeak.kt"), "java.net.HttpURLConnection")
    }

    @Test
    fun `import 없는 완전수식 참조 leak 을 소스 층이 잡는다`() {
        assertViolates(File(VIOLATING, "settlement/FullyQualifiedReferenceLeak.kt"), "java.net.HttpURLConnection")
    }

    /**
     * **Codex 14차 #1 회귀 고정.** 다른 함수의 동명 지역 변수(`val java = 1`)가 이 함수의
     * 완전수식 참조를 지우지 못한다 — production 게이트 회귀 fixture.
     */
    @Test
    fun `다른 함수의 동명 지역 변수가 완전수식 참조를 지우지 못한다`() {
        assertViolates(File(VIOLATING, "qualification/ShadowedRootLeak.kt"), "java.net.HttpURLConnection")
    }

    /**
     * **verifier r20 H-1 회귀 고정.** 같은 함수 안, 참조 **뒤쪽**의 동명 지역 변수(`val java = 1`)
     * 도 그 앞선 참조를 지우지 못한다 — production 게이트 회귀 fixture.
     */
    @Test
    fun `참조 뒤쪽의 동명 지역 변수가 앞선 완전수식 참조를 지우지 못한다`() {
        assertViolates(File(VIOLATING, "qualification/TrailingShadowLeak.kt"), "java.net.HttpURLConnection")
    }

    @Test
    fun `실제 도메인 형태는 위반이 없다`() {
        val file = File(ALLOWED, "settlement/DomainShapes.kt")
        val violations = violationsIn(file)
        assertTrue(violations.isEmpty(), "허용 fixture 가 위반으로 잡혔다: $violations")
    }

    private fun assertViolates(
        file: File,
        expectedFqn: String,
    ) {
        val violations = violationsIn(file)
        assertTrue(violations.any { it.fqn == expectedFqn }, "$file 이 '$expectedFqn' 사유로 잡히지 않았다: $violations")
    }

    private fun violationsIn(file: File) =
        SourceReferences.extract(file.path, file.readText()).filterNot { ref ->
            if (ref.wildcard) policy.admitsWildcard(ref.fqn) else policy.admits(ref.fqn)
        }

    private companion object {
        val REAL_POLICY: String =
            System.getProperty("bidvector.architecture.policy")
                ?: error("시스템 속성 'bidvector.architecture.policy' 가 없다 — 빌드가 넘긴다")
        val VIOLATING: String =
            System.getProperty("bidvector.archfixture.violating")
                ?: error("시스템 속성 'bidvector.archfixture.violating' 가 없다 — 빌드가 넘긴다")
        val ALLOWED: String =
            System.getProperty("bidvector.archfixture.allowed")
                ?: error("시스템 속성 'bidvector.archfixture.allowed' 가 없다 — 빌드가 넘긴다")
    }
}
