package bidvector.buildlogic

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * `OPEN-KTLINT-UNUSED-IMPORTS-SILENT` 의 **순수 코어**. 각 단언은 「쓰였다」의 한 형태를 고정한다 —
 * 오탐이 미탐보다 나쁜 쪽이 이 축이다(쓰이는 import 를 지우라고 하면 컴파일이 깨진다).
 */
class UnusedImportsTest {
    @Test
    fun `호출로 쓰이면 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nfun run() = Thing()\n"))
    }

    @Test
    fun `아무 데서도 쓰이지 않으면 잡는다`() {
        assertEquals(listOf("a.b.Thing"), unused("import a.b.Thing\nfun run() = 1\n"))
    }

    @Test
    fun `타입 표면에 쓰이면 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nfun run(): Thing? = null\n"))
    }

    @Test
    fun `타입 인자에만 쓰여도 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nfun run(): List<Thing> = emptyList()\n"))
    }

    @Test
    fun `애노테이션으로만 쓰여도 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Marked\n@Marked\nclass Holder\n"))
    }

    @Test
    fun `문자열 템플릿 안에서만 쓰여도 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nfun run() = \"\${Thing.NAME}\"\n"))
    }

    @Test
    fun `KDoc 링크에만 쓰여도 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing\n\n/** [Thing] 을 설명한다. */\nclass Holder\n"))
    }

    @Test
    fun `별칭 import 는 별칭이 도입 이름이다 — 원 이름을 써도 쓰인 것이 아니다`() {
        assertEquals(listOf("a.b.Thing"), unused("import a.b.Thing as Alias\nfun run() = Thing\n"))
    }

    @Test
    fun `별칭을 쓰면 위반이 없다`() {
        assertEquals(emptyList(), unused("import a.b.Thing as Alias\nfun run() = Alias()\n"))
    }

    @Test
    fun `import 목록 자신은 쓰임으로 세지 않는다 — 세면 게이트가 공허해진다`() {
        assertEquals(listOf("a.b.Thing", "c.d.Other"), unused("import a.b.Thing\nimport c.d.Other\nfun run() = 1\n"))
    }

    @Test
    fun `package 선언의 세그먼트도 쓰임으로 세지 않는다`() {
        assertEquals(listOf("a.b.Thing"), unused("package a.b\n\nimport a.b.Thing\nfun run() = 1\n"))
    }

    @Test
    fun `문법이 쓰는 이름은 면제한다 — 기호로 불려 본문에 이름이 없다`() {
        val source = "import org.gradle.kotlin.dsl.assign\nfun run() = 1\n"

        assertEquals(listOf("org.gradle.kotlin.dsl.assign"), unused(source))
        assertEquals(emptyList(), unused(source, syntaxNames = setOf("assign")))
    }

    @Test
    fun `wildcard import 는 대상이 아니다 — 도입 이름이 열거되지 않는다`() {
        assertEquals(emptyList(), unused("import a.b.*\nfun run() = 1\n"))
    }

    @Test
    fun `같은 짧은 이름을 그 파일이 선언하면 쓰인 것으로 본다 — 알려진 미탐`() {
        assertEquals(emptyList(), unused("import a.b.Thing\nclass Thing\n"))
    }

    private fun unused(
        source: String,
        syntaxNames: Set<String> = emptySet(),
    ): List<String> = UnusedImports.detect("Probe.kt", source, syntaxNames).map { it.fqn }
}
