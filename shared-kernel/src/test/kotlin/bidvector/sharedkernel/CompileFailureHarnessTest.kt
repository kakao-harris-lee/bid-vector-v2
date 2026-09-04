package bidvector.sharedkernel

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

/**
 * 컴파일 실패 하네스 — `ADR 0002` §6 ①이 요구하는 "상호 대입이 컴파일되지 않는다"를
 * 기계로 증명한다(설계 검토 §4.9). `kotlin-compiler-embeddable` 로 음성 fixture를 이 모듈의
 * 산출 classpath 위에서 **별도 컴파일 단위**로 돌려 실패와 기대 진단을 단언하고, 음성마다
 * 한 토큰만 다른 양성 쌍둥이가 성공함을 함께 단언한다 — exit code만 보면 오타도 통과시킨다
 * (설계 검토 §4.9 하드 요구, 「공허한 통과」방지).
 *
 * fixture 여섯째(`VatTreatment` 다른 금액의 비교)는 여기 없다 — `Money`가 basis 축을
 * 컴파일 단계에서 막는 것과 달리, 같은 타입(`BaseAmount`) 값 둘의 `vatTreatment` 비교는
 * `Comparable<BaseAmount>`가 필드 값을 보지 않으므로 **컴파일러가 구조적으로 구별할 수
 * 없다** — 그 축은 정의상 런타임 실패 계약이다(설계 검토 §5 L-1, `ArithmeticTest`의
 * `P-3c`가 실측한다). 여기서 negative fixture를 억지로 만들면 실제로는 컴파일이 성공해
 * 거짓 판정이 된다.
 */
class CompileFailureHarnessTest {
    @Test
    fun `1 basis 교차 대입은 컴파일되지 않고 같은 basis 대입은 컴파일된다`() {
        assertNegativeFails("1-basis-cross-assignment", "type mismatch")
        assertPositiveCompiles("1-basis-same-type")
    }

    @Test
    fun `2 Rate 축 교차는 컴파일되지 않고 같은 축 대입은 컴파일된다`() {
        assertNegativeFails("2-rate-axis-cross", "type mismatch")
        assertPositiveCompiles("2-rate-axis-same")
    }

    @Test
    fun `3 곱셈 규칙 위반은 컴파일되지 않고 승인된 조합은 컴파일된다`() {
        assertNegativeFails("3-multiplication-rule", "times")
        assertPositiveCompiles("3-multiplication-rule")
    }

    @Test
    fun `4 raw Double 로는 금액을 못 만들고 Long 으로는 만들 수 있다`() {
        assertNegativeFails("4-raw-double-construction", "type mismatch")
        assertPositiveCompiles("4-raw-long-construction")
    }

    @Test
    fun `5 internal 원 단위 접근자는 모듈 밖에서 못 부르고 export 는 부를 수 있다`() {
        assertNegativeFails("5-internal-accessor", "amount")
        assertPositiveCompiles("5-export")
    }
}

private fun assertNegativeFails(
    fixtureName: String,
    expectedDiagnosticFragment: String,
) {
    val result = compileFixture("negative-$fixtureName")
    result.exitCode shouldBe ExitCode.COMPILATION_ERROR
    result.output shouldContain expectedDiagnosticFragment
}

private fun assertPositiveCompiles(fixtureName: String) {
    val result = compileFixture("positive-$fixtureName")
    io.kotest.assertions.withClue(result.output) {
        result.exitCode shouldBe ExitCode.OK
    }
}

private data class CompileResult(
    val exitCode: ExitCode,
    val output: String,
)

/** 음성 fixture 마다 [assertNegativeFails]가 이미 exit code로 실패를 확인하므로 여기서 던지지 않는다. */
private fun compileFixture(baseName: String): CompileResult {
    val source = readFixtureResource(baseName)
    val workDir = Files.createTempDirectory("compile-fixture-$baseName").toFile()
    val sourceFile = File(workDir, "$baseName.kt").apply { writeText(source) }
    val outputDir = File(workDir, "out").apply { mkdirs() }
    val buffer = ByteArrayOutputStream()
    val exitCode =
        PrintStream(buffer).use { printStream ->
            K2JVMCompiler().exec(
                printStream,
                "-cp",
                testClasspath(),
                "-d",
                outputDir.absolutePath,
                "-no-stdlib",
                "-no-reflect",
                sourceFile.absolutePath,
            )
        }
    return CompileResult(exitCode, buffer.toString())
}

private fun readFixtureResource(baseName: String): String {
    val resourcePath = "/compile-fixtures/$baseName.kt.txt"
    val stream =
        object {}.javaClass.getResourceAsStream(resourcePath)
            ?: error("fixture resource not found: $resourcePath")
    return stream.bufferedReader().use { it.readText() }
}

/**
 * Gradle test worker는 module-path가 아니라 classic classpath로 뜬다(이 모듈이 JPMS를
 * 쓰지 않는다) — `java.class.path`가 shared-kernel 산출물과 kotlin-stdlib을 모두 담는다.
 */
private fun testClasspath(): String = System.getProperty("java.class.path")
