package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * PSI 추출의 음성 — 설계 검토 §4 S-1~S-4·S-9·S-10·S-16. 정책과 무관하게 **추출된 FQN 후보
 * 자체**를 잰다(순수 함수, 인라인 소스 문자열).
 */
class SourceReferencesTest {
    @Test
    fun `S-1 import 없는 완전수식 참조를 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val accepted: Int = java.net.HttpURLConnection.HTTP_OK\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    @Test
    fun `S-2 별칭 import 는 원 FQN 을 낸다`() {
        val refs = SourceReferences.extract("Probe.kt", "package p\n\nimport java.net.HttpURLConnection as Conn\n")
        assertEquals(listOf("java.net.HttpURLConnection"), refs.map { it.fqn })
        assertTrue(refs.none { it.wildcard })
    }

    @Test
    fun `S-3 정확 패키지 밖의 wildcard import 를 패키지 FQN 으로 낸다`() {
        val wildcard = SourceReferences.extract("Probe.kt", "package p\n\nimport java.net.*\n").single()
        assertEquals("java.net", wildcard.fqn)
        assertTrue(wildcard.wildcard)
    }

    @Test
    fun `S-4 클래스 단위 패키지의 wildcard 도 같은 형태로 낸다`() {
        val wildcard = SourceReferences.extract("Probe.kt", "package p\n\nimport java.util.*\n").single()
        assertEquals("java.util", wildcard.fqn)
        assertTrue(wildcard.wildcard)
    }

    @Test
    fun `S-9 애노테이션의 완전수식 타입 참조를 잡는다`() {
        val refs = SourceReferences.extract("Probe.kt", "package p\n\n@java.beans.Transient\nclass C\n")
        assertTrue(refs.any { it.fqn == "java.beans.Transient" }, "$refs")
    }

    @Test
    fun `S-10 import 없는 class literal 을 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    fun target(): Any = java.net.HttpURLConnection::class.java\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    @Test
    fun `S-16 백틱 식별자를 원 이름으로 읽는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val accepted: Int = `java`.`net`.HttpURLConnection.HTTP_OK\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    @Test
    fun `단순 이름 참조는 완전수식 단언에서 건너뛴다 — import 나 같은 파일 선언이 푼다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nimport java.net.HttpURLConnection\n\nclass C {\n" +
                    "    val accepted: Int = HttpURLConnection.HTTP_OK\n}\n",
            )
        assertEquals(listOf("java.net.HttpURLConnection"), refs.map { it.fqn })
    }

    @Test
    fun `값 체인은 건너뛴다 — 대문자 세그먼트가 없다`() {
        val refs = SourceReferences.extract("Probe.kt", "package p\n\nclass C {\n    val x = a.b.c\n}\n")
        assertTrue(refs.isEmpty(), "$refs")
    }

    @Test
    fun `package 선언과 import 지시자는 완전수식 단언의 입력에서 뺀다`() {
        val refs = SourceReferences.extract("Probe.kt", "package bidvector.procurement\n\nclass C\n")
        assertTrue(refs.isEmpty(), "$refs")
    }

    @Test
    fun `중첩 클래스는 두 형태를 모두 낸다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val e: java.util.Map.Entry<String, String>? = null\n}\n",
            )
        assertEquals(setOf("java.util.Map", "java.util.Map\$Entry"), refs.map { it.fqn }.toSet())
    }
}
