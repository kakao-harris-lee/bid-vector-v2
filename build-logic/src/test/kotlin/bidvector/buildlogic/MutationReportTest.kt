package bidvector.buildlogic

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * `mutations.xml` 해석. **판정의 분자는 우리가 세는 `status` 가 아니라 pitest 자신이 쓴
 * `detected` 속성**이다 — `KILLED` 밖에도 탐지로 세는 상태(`TIMED_OUT`)가 있고, 그 분류를
 * 우리가 다시 열거하면 도구가 분류를 늘리는 날 두 자리가 어긋난다.
 */
class MutationReportTest {
    @TempDir
    lateinit var dir: File

    private fun report(vararg rows: String): File =
        File(dir, "mutations.xml").apply {
            writeText(
                rows.joinToString(
                    separator = "\n",
                    prefix = "<mutations partial=\"true\">\n",
                    postfix = "\n</mutations>\n",
                ),
            )
        }

    private fun row(
        detected: Boolean,
        status: String,
        mutatedClass: String,
    ) = "<mutation detected='$detected' status='$status'>" +
        "<sourceFile>X.kt</sourceFile><mutatedClass>$mutatedClass</mutatedClass>" +
        "<mutatedMethod>f</mutatedMethod><lineNumber>1</lineNumber>" +
        "<mutator>org.pitest.mutationtest.engine.gregor.mutators.NegateConditionalsMutator</mutator>" +
        "</mutation>"

    @Test
    fun `변이 수와 탐지 수를 센다`() {
        val parsed =
            MutationReport.parse(
                report(
                    row(detected = true, status = "KILLED", mutatedClass = "a.B"),
                    row(detected = true, status = "TIMED_OUT", mutatedClass = "a.B"),
                    row(detected = false, status = "SURVIVED", mutatedClass = "a.C"),
                    row(detected = false, status = "NO_COVERAGE", mutatedClass = "a.C"),
                ),
            )
        assertEquals(4, parsed.total)
        assertEquals(2, parsed.detected)
    }

    @Test
    fun `TIMED_OUT 도 탐지로 센다 — status 를 다시 열거하지 않는다`() {
        val parsed = MutationReport.parse(report(row(detected = true, status = "TIMED_OUT", mutatedClass = "a.B")))
        assertEquals(1, parsed.detected)
    }

    @Test
    fun `변이가 닿은 클래스 집합을 낸다`() {
        val parsed =
            MutationReport.parse(
                report(
                    row(detected = true, status = "KILLED", mutatedClass = "a.B"),
                    row(detected = false, status = "SURVIVED", mutatedClass = "a.B"),
                    row(detected = true, status = "KILLED", mutatedClass = "a.C\$Inner"),
                ),
            )
        assertEquals(setOf("a.B", "a.C\$Inner"), parsed.mutatedClasses)
    }

    @Test
    fun `변이가 하나도 없는 리포트도 해석된다`() {
        val parsed = MutationReport.parse(report())
        assertEquals(0, parsed.total)
        assertEquals(emptySet(), parsed.mutatedClasses)
    }

    @Test
    fun `detected 속성이 없으면 거부한다`() {
        // 속성이 사라진 리포트를 「탐지 0」으로 읽으면 도구 출력 변경이 조용한 전수 실패가 되고,
        // 「탐지 전부」로 읽으면 조용한 전수 통과가 된다. 둘 다 아니라 읽기를 거부한다.
        val malformed =
            File(dir, "mutations.xml").apply {
                writeText(
                    "<mutations><mutation status='KILLED'><mutatedClass>a.B</mutatedClass></mutation></mutations>",
                )
            }
        val error = assertFailsWith<IllegalStateException> { MutationReport.parse(malformed) }
        assertTrue(error.message.orEmpty().contains("detected"), "${error.message}")
    }

    @Test
    fun `mutatedClass 가 없으면 거부한다`() {
        val malformed =
            File(dir, "mutations.xml").apply {
                writeText("<mutations><mutation detected='true' status='KILLED'/></mutations>")
            }
        val error = assertFailsWith<IllegalStateException> { MutationReport.parse(malformed) }
        assertTrue(error.message.orEmpty().contains("mutatedClass"), "${error.message}")
    }
}
