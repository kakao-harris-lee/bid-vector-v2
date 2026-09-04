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

    /**
     * **verifier r18 F-1.** 완전수식 참조가 다른 수식 표현의 **수신자**(여기서는 `.toString()`
     * 호출의 receiver)이면, 바깥 노드(`selector` 가 `KtCallExpression` 이라 `collectSegments`
     * 가 null)가 후보를 못 만드는데도 안쪽 노드가 「부모가 dot-qualified」라는 이유만으로
     * 방문에서 빠졌다. 설계 검토 §4 S-2 단언 6항 — 자식을 건너뛰는 것은 부모가 **실제로 후보를
     * 만들었을 때만**이다.
     */
    @Test
    fun `F-1 완전수식 참조 뒤에 메서드 호출이 이어져도 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val status: String = java.net.HttpURLConnection.HTTP_OK.toString()\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    @Test
    fun `F-1 완전수식 호출의 수신자도 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val encoded: String = java.net.URLEncoder.encode(\"x\", \"UTF-8\")\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.net.URLEncoder" }, "$refs")
    }

    @Test
    fun `F-1 생성자 호출의 완전수식 callee 도 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val name: String = java.io.File(\"x\").name\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.io.File" }, "$refs")
    }

    /** 회귀 — 후보를 만든 노드의 자식은 여전히 중복 보고되지 않는다. */
    @Test
    fun `F-1 정상 체인은 여전히 한 번만 잡는다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val v: Int = java.net.HttpURLConnection.HTTP_OK\n}\n",
            )
        assertEquals(1, refs.count { it.fqn == "java.net.HttpURLConnection" }, "$refs")
    }

    /**
     * **verifier r18 F-3.** 대문자·숫자·`_` 만으로 된 두 글자 이상 세그먼트(`MAX_VALUE`)는
     * 멤버(상수) 접근으로 보고 타입 체인에 잇지 않는다 — 이어 붙이면 `java.lang.Integer$MAX_VALUE`
     * 처럼 존재하지 않는 중첩 클래스 후보가 생겨 허용된 `java.lang.Integer` 접근까지 오탐으로
     * 잡힌다. `java.util.Map.Entry` 처럼 실제 중첩 클래스 이름(`Entry`, 소문자를 포함)은 이
     * 규칙의 영향을 받지 않는다(회귀).
     */
    @Test
    fun `F-3 SCREAMING_CASE 세그먼트는 멤버로 보고 후보에서 뺀다`() {
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val max: Int = java.lang.Integer.MAX_VALUE\n}\n",
            )
        assertEquals(setOf("java.lang.Integer"), refs.map { it.fqn }.toSet(), "$refs")
    }

    @Test
    fun `F-3 두 글자 미만 대문자 세그먼트는 여전히 타입 체인으로 잇는다`() {
        // 한 글자 대문자 세그먼트(제네릭 타입 파라미터류)는 SCREAMING_CASE 로 보지 않는다.
        val refs =
            SourceReferences.extract(
                "Probe.kt",
                "package p\n\nclass C {\n    val e: java.util.Map.Entry<String, String>? = null\n}\n",
            )
        assertTrue(refs.any { it.fqn == "java.util.Map\$Entry" }, "$refs")
    }
}
