package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 함수 길이 측정의 음성·양성. **텍스트 매칭을 버린 이유가 여기 고정된다** — 억제 표기를 아무리
 * 바꿔도 길이는 그대로 측정된다.
 */
class KotlinFunctionLengthsTest {
    @TempDir
    lateinit var dir: File

    private fun measure(source: String): List<MeasuredFunction> {
        val file = File(dir, "Probe.kt").apply { writeText(source) }
        return measureFunctions(listOf(file))
    }

    private fun body(lines: Int) = (1..lines).joinToString("\n") { "        total += $it" }

    @Test
    fun `긴 함수를 잰다`() {
        val f =
            measure(
                "package p\n\nclass C {\n    fun run(): Int {\n        var total = 0\n${body(
                    50,
                )}\n        return total\n    }\n}\n",
            )
        val run = f.single { it.name == "run" }
        assertTrue(run.lines > 50, "실제 ${run.lines} 줄")
    }

    @Test
    fun `다중 행 Suppress 는 길이에 영향이 없다`() {
        val suppressed =
            measure(
                "package p\n\nclass C {\n    @Suppress(\n        \"LongMethod\",\n    )\n    fun run(): Int {\n" +
                    "        var total = 0\n${body(50)}\n        return total\n    }\n}\n",
            ).single { it.name == "run" }
        val plain =
            measure(
                "package p\n\nclass C {\n    fun run(): Int {\n        var total = 0\n${body(
                    50,
                )}\n        return total\n    }\n}\n",
            ).single { it.name == "run" }
        assertEquals(plain.lines, suppressed.lines, "억제 표기가 길이를 바꾸면 안 된다")
    }

    @Test
    fun `KDoc 과 애노테이션은 길이에 들지 않는다`() {
        val documented =
            measure(
                "package p\n\nclass C {\n    /**\n     * a\n     * b\n     */\n    @Deprecated(\"x\")\n    fun run(): Int = 1\n}\n",
            ).single { it.name == "run" }
        assertEquals(1, documented.lines, "funKeyword 부터 세야 한다")
    }

    @Test
    fun `문자열과 주석 속 중괄호에 속지 않는다`() {
        val f =
            measure(
                "package p\n\nclass C {\n    fun run(): String {\n        val a = \"\"\"}\"\"\"\n" +
                    "        // }\n        val b = \"}\"\n        return a + b\n    }\n\n    fun other(): Int = 2\n}\n",
            )
        assertEquals(2, f.count { it.name != "<람다>" }, "함수 둘이 갈려야 한다: ${f.map { it.name }}")
        assertEquals(6, f.single { it.name == "run" }.lines)
    }

    @Test
    fun `식 본문 함수를 다음 함수까지 삼키지 않는다`() {
        val f = measure("package p\n\nclass C {\n    fun short(): Int = 1\n\n    fun other(): Int = 2\n}\n")
        assertEquals(1, f.single { it.name == "short" }.lines)
    }

    @Test
    fun `람다도 잰다 — 함수를 쪼개 우회할 수 없다`() {
        val f =
            measure(
                "package p\n\nclass C {\n    fun run(xs: List<Int>): Int =\n        xs.sumOf {\n" +
                    "${body(30)}\n            it\n        }\n}\n",
            )
        val lambda = f.single { it.name == "<람다>" }
        assertTrue(lambda.lines > 30, "람다 ${lambda.lines} 줄")
    }
}
