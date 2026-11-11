package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 환경 스니핑 축(OPS-13)이 쓰는 **수신자·멤버 쌍** 추출. 좌표(FQN) 축이 이 형태를 아예 보지 못하는
 * 것이 이 추출기가 따로 있는 이유다 — `System` 은 `java.lang` 기본 import 로 해석돼 소스에 패키지
 * 접두가 없고, 후보 조립은 첫 세그먼트가 대문자면 단순 이름 참조로 보아 건너뛴다.
 */
class SourceMemberCallsTest {
    @Test
    fun `단순 수신자의 호출을 쌍으로 낸다`() {
        assertTrue(pairs("fun f() = System.getenv(\"CI\")\n").contains("System.getenv"))
    }

    @Test
    fun `완전수식 수신자는 마지막 세그먼트로 센다`() {
        assertTrue(pairs("fun f() = java.lang.System.getenv(\"CI\")\n").contains("System.getenv"))
    }

    @Test
    fun `호출 참조로 넘기는 형태도 같은 쌍이다 — 호출 자리만 보면 열린다`() {
        assertTrue(pairs("fun f() = System::getenv\n").contains("System.getenv"))
    }

    @Test
    fun `좌표 축은 이 형태를 보지 못한다 — 두 축이 따로 있는 이유`() {
        val source = "package p\n\nfun f() = System.getenv(\"CI\")\n"

        assertEquals(emptyList(), SourceReferences.extract("Probe.kt", source).map { it.fqn })
        assertTrue(pairs(source).contains("System.getenv"))
    }

    @Test
    fun `import 목록과 package 선언은 쌍을 만들지 않는다`() {
        val source = "package a.b\n\nimport c.d.Thing\n\nfun f() = 1\n"

        assertEquals(emptyList(), pairs(source))
    }

    @Test
    fun `지역 수신자도 쌍을 낸다 — 별표 수신자 규칙이 쓰는 입력`() {
        val found = pairs("fun f(environment: Any) = environment.getActiveProfiles()\n")

        assertTrue(found.contains("environment.getActiveProfiles"), "$found")
    }

    private fun pairs(source: String): List<String> =
        withKotlinPsi { factory ->
            SourceMemberCalls
                .extractFrom(factory.createFile("Probe.kt", source), "Probe.kt")
                .map { "${it.receiver}.${it.member}" }
        }
}
