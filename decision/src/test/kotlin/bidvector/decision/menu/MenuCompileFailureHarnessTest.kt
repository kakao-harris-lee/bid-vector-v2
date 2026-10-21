package bidvector.decision.menu

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

/**
 * 메뉴 생성 경계의 컴파일 실패 하네스(1B·strategy `CompileFailureHarnessTest` 관례) —
 * `kotlin-compiler-embeddable` 로 음성 fixture 를 이 모듈의 산출 classpath 위에서 **별도
 * 컴파일 단위**로 돌린다. 모듈 밖에서 보이는 것만 재는 것이 요점이다: 같은 모듈 test 는
 * `internal` 생성자를 그냥 부를 수 있어 폐쇄를 재지 못한다.
 *
 * **이 하네스가 DEC-05 무조건 acceptance 첫째(「값만 있고 근거 없는 옵션을 만들 수 없다」)의
 * 유일한 기계적 증명이다** — 「근거를 채우는 호출부가 오늘은 하나뿐」은 증명이 아니고,
 * 생성 경로가 닫혀 있다는 사실만이 증명이다. 생성자를 공개로 되돌리는 변이에서 fixture 1 이
 * 양성이 되어 RED 다.
 *
 * fixture 1 — [BidMenuOption] 은 모듈 밖에서 직접 지을 수 없다(유일한 생성 경로는
 * [composeBidMenu]). fixture 2 — [BidMenuOutcome.Offered] 도 같다. 둘을 나눈 이유는 명제가
 * 다르기 때문이다: 하나는 「근거 없는 **옵션**」, 하나는 「판정을 거치지 않은 **정상 3안**」의
 * 위조다(밴드 붕괴를 정상 3안으로 바꿔치는 경로가 여기로 열린다).
 */
class MenuCompileFailureHarnessTest {
    @Test
    fun `1 BidMenuOption 은 모듈 밖에서 직접 지을 수 없고 커널 경유는 컴파일된다`() {
        assertNegativeFails("1-menu-option-direct-construction", "cannot access")
        assertPositiveCompiles("1-menu-via-kernel")
    }

    @Test
    fun `1-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "1-menu-option-direct-construction-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    @Test
    fun `2 Offered 는 모듈 밖에서 직접 지을 수 없고 커널 경유는 컴파일된다`() {
        assertNegativeFails("2-menu-offered-direct-construction", "cannot access")
        assertPositiveCompiles("1-menu-via-kernel")
    }

    @Test
    fun `2-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "2-menu-offered-direct-construction-typo",
            realDiagnosticFragment = "cannot access",
        )
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

private fun assertMutantDoesNotMatchRealFragment(
    mutantFixtureName: String,
    realDiagnosticFragment: String,
) {
    val result = compileFixture("mutant-$mutantFixtureName")
    io.kotest.assertions.withClue(result.output) {
        result.exitCode shouldBe ExitCode.COMPILATION_ERROR
        result.output shouldNotContain realDiagnosticFragment
    }
}

private data class CompileResult(
    val exitCode: ExitCode,
    val output: String,
)

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

/** Gradle test worker 는 classic classpath 로 뜬다 — `java.class.path` 가 decision·shared-kernel 산출물을 담는다. */
private fun testClasspath(): String = System.getProperty("java.class.path")
