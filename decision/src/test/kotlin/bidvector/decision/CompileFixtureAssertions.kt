package bidvector.decision

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Files

/**
 * 컴파일 실패 하네스의 공용 단언 — `kotlin-compiler-embeddable` 로 음성 fixture 를 이 모듈의
 * 산출 classpath 위에서 **별도 컴파일 단위**로 돌린다. 모듈 밖에서 보이는 것만 재는 것이
 * 요점이다: 같은 모듈 test 는 `internal` 생성자를 그냥 부를 수 있어 폐쇄를 재지 못한다.
 *
 * 이 파일이 생긴 이유는 **같은 모듈에 하네스가 둘**이 됐기 때문이다(DEC-05 메뉴 경계 ·
 * DEC-10 게이트 경계). 배관을 한 벌 더 베끼면 「컴파일 인자 하나를 한쪽만 고치는」 자리가
 * 생긴다 — 모듈마다 한 벌은 그 모듈의 classpath 가 다르기 때문이지만, 한 모듈 안에서 두 벌은
 * 중복일 뿐이다.
 */
internal fun assertNegativeFails(
    fixtureName: String,
    vararg expectedDiagnosticFragments: String,
) {
    val result = compileFixture("negative-$fixtureName")
    withClue(result.output) {
        result.exitCode shouldBe ExitCode.COMPILATION_ERROR
        expectedDiagnosticFragments.forEach { fragment -> result.output shouldContain fragment }
    }
}

internal fun assertPositiveCompiles(fixtureName: String) {
    val result = compileFixture("positive-$fixtureName")
    withClue(result.output) {
        result.exitCode shouldBe ExitCode.OK
    }
}

/**
 * 변이(오타만 넣고 계약 위반은 없는 fixture)가 여전히 컴파일에 실패하되, 실 위반 fixture 를
 * 식별하는 진단 단편은 **만족시키지 않음**을 확인한다 — 단언이 진단의 **종류**를 보는지,
 * 부분 문자열만 보는지를 가르는 실측이다.
 */
internal fun assertMutantDoesNotMatchRealFragment(
    mutantFixtureName: String,
    realDiagnosticFragment: String,
) {
    val result = compileFixture("mutant-$mutantFixtureName")
    withClue(result.output) {
        result.exitCode shouldBe ExitCode.COMPILATION_ERROR
        result.output shouldNotContain realDiagnosticFragment
    }
}

/**
 * `copy` 봉쇄의 **진단 문면**을 지목하는 조각.
 *
 * 그냥 `"copy"` 로는 **아무것도 걸러내지 못한다**(DEC-05 r5 V5-M1 실측): 하네스가 소스를
 * `compile-fixture-<baseName>/<baseName>.kt` 에 쓰고 컴파일러가 모든 진단 줄 앞에 **그 경로**를
 * 붙이며 **원본 소스 줄**까지 찍는데, copy fixture 의 `baseName` 에는 전부 `copy` 가 들어 있다.
 * 그래서 `copy` 와 무관한 오류에서도 `output.contains("copy")` 가 **참**이었다.
 *
 * `cannot access 'fun copy(` 는 경로에도 소스 echo 에도 나타날 수 없어 **심볼을 지목한다**.
 */
internal const val COPY_DENIED = "cannot access 'fun copy("

private data class CompileResult(
    val exitCode: ExitCode,
    val output: String,
)

/** 음성 fixture 마다 [assertNegativeFails] 가 이미 exit code 로 실패를 확인하므로 여기서 던지지 않는다. */
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
 * Gradle test worker 는 module-path 가 아니라 classic classpath 로 뜬다(이 모듈이 JPMS 를 쓰지
 * 않는다) — `java.class.path` 가 이 모듈 산출물과 kotlin-stdlib 을 모두 담는다.
 */
private fun testClasspath(): String = System.getProperty("java.class.path")
