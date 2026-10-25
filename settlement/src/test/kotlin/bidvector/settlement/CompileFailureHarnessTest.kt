package bidvector.settlement

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
 * 컴파일 실패 하네스(1B·1E `CompileFailureHarnessTest` 관례) — `kotlin-compiler-embeddable`
 * 로 음성 fixture 를 이 모듈의 산출 classpath 위에서 별도 컴파일 단위로 돌려 실패와 기대
 * 진단을 단언하고, 몇 토큰만 다른 **양성 쌍둥이**의 컴파일 성공을 함께 단언한다. helper
 * 함수들은 1E 판을 그대로 옮긴 것이다(test 소스셋 중복은 `duplicate-policy.properties` 의
 * `fail.source-sets=main` 밖이다).
 *
 * fixture 1 — **판정 함수가 금액을 받지 않는다.** legacy 는 *"caller symmetry and audit only"*
 * 로 금액 둘을 받았고(`award_verification.py:127-128`) 그 형태는 후속 개발자가 금액을 판정에
 * 끌어들이기 쉽다. 금액을 더한 호출이 **컴파일되지 않는다**는 것이 그 방어의 실물이다.
 * fixture 2 — `NormalizedCompanyName` 의 유일한 생성 경로는 `normalizeCompanyName` 이다
 * (private 생성자). 모듈 밖에서 임의 문자열을 「정규화된 상호」로 승격할 수 없다.
 * fixture 3 — `copy()` 도 같은 가시성으로 닫힌다(`@ConsistentCopyVisibility`) — fixture 2 를
 * 우회하는 두 번째 길.
 * fixture 4 — 조회 관문 `AwardLookupDecision.Lookup` 의 유일한 생성 경로는 `decideAwardLookup`
 * 이다(`internal` 생성자). 모듈 밖에서 아무 문자열을 정규화해 `Lookup` 을 직접 만들어 판정에
 * 넣는 길 — 「미설정이면 판정하지 않는다」를 호출부 성실성으로 되돌리는 길 — 이 닫힌다.
 * fixture 5 — `Lookup.copy()` 로 운영자 상호를 바꿔치우는 길도 같은 가시성으로 닫힌다.
 * fixture 6 — 확정 판정 `SettledAwardOutcome.Won` 을 모듈 밖에서 만들어(금액 비교 따위로)
 * 기록에 넣는 길이 닫힌다. 양성 쌍둥이는 판정 함수를 거친 기록이다.
 * fixture 7 — `SettledAwardOutcome.Lost` 도 같다. 모듈 밖에서 만든 패찰이 먼저 기록되면 기록 불변
 * 규율 때문에 뒤의 참 낙찰이 영구히 막힌다.
 *
 * fixture 의 상호는 전부 가상이고 **ASCII 로만** 적는다(컴파일러 소스 인코딩에 기대지 않는다).
 */
class CompileFailureHarnessTest {
    @Test
    fun `1 판정 함수는 금액 인자를 받지 않고 금액 없는 호출은 컴파일된다`() {
        assertNegativeFails("1-judge-with-amount", TOO_MANY_ARGUMENTS)
        assertPositiveCompiles("1-judge-without-amount")
    }

    @Test
    fun `1-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment("1-judge-with-amount-typo", TOO_MANY_ARGUMENTS)
    }

    @Test
    fun `2 NormalizedCompanyName 직접 생성은 모듈 밖에서 컴파일되지 않고 정규화 경유는 컴파일된다`() {
        assertNegativeFails("2-normalized-name-direct-construction", PRIVATE_IN)
        assertPositiveCompiles("2-normalized-name-via-normalize")
    }

    @Test
    fun `2-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment("2-normalized-name-direct-construction-typo", PRIVATE_IN)
    }

    @Test
    fun `3 NormalizedCompanyName copy 는 모듈 밖에서 컴파일되지 않는다`() {
        assertNegativeFails("3-normalized-name-copy-bypass", PRIVATE_IN)
        assertPositiveCompiles("2-normalized-name-via-normalize")
    }

    @Test
    fun `3-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment("3-normalized-name-copy-bypass-typo", PRIVATE_IN)
    }

    @Test
    fun `4 조회 관문 Lookup 직접 생성은 모듈 밖에서 컴파일되지 않고 관문 경유는 컴파일된다`() {
        assertNegativeFails("4-lookup-direct-construction", INTERNAL_IN)
        assertPositiveCompiles("1-judge-without-amount")
    }

    @Test
    fun `4-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment("4-lookup-direct-construction-typo", INTERNAL_IN)
    }

    @Test
    fun `5 조회 관문 Lookup copy 는 모듈 밖에서 컴파일되지 않는다`() {
        assertNegativeFails("5-lookup-copy-bypass", INTERNAL_IN)
        assertPositiveCompiles("1-judge-without-amount")
    }

    @Test
    fun `5-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment("5-lookup-copy-bypass-typo", INTERNAL_IN)
    }

    @Test
    fun `6 확정 판정 Won 은 모듈 밖에서 만들어지지 않고 판정 경유 기록은 컴파일된다`() {
        assertNegativeFails("6-won-direct-construction", INTERNAL_IN)
        assertPositiveCompiles("6-won-via-judge")
    }

    @Test
    fun `6-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment("6-won-direct-construction-typo", INTERNAL_IN)
    }

    @Test
    fun `7 확정 판정 Lost 는 모듈 밖에서 만들어지지 않고 판정 경유 기록은 컴파일된다`() {
        assertNegativeFails("7-lost-direct-construction", INTERNAL_IN)
        assertPositiveCompiles("6-won-via-judge")
    }

    @Test
    fun `7-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment("7-lost-direct-construction-typo", INTERNAL_IN)
    }
}

/**
 * 기대 진단 조각. **가시성을 구별하는 조각**을 쓴다 — `"cannot access"` 는 `private` 과
 * `internal` 을 가르지 못해 `internal` 퇴행이 초록이 된다(6F-4-w r1 HIGH 실측).
 */
private const val PRIVATE_IN = "it is private in"

/** `internal` 생성자의 진단 조각 — `private` 과 갈린다(위와 같은 사유). */
private const val INTERNAL_IN = "it is internal in"

/** 인자 수 계약의 진단 조각. 실제 컴파일러 출력으로 고정했다(evidence `commands.md`). */
private const val TOO_MANY_ARGUMENTS = "too many arguments for 'fun judgeAwardOutcome("

private fun assertNegativeFails(
    fixtureName: String,
    expectedDiagnosticFragment: String,
) {
    val result = compileFixture("negative-$fixtureName")
    io.kotest.assertions.withClue(result.output) {
        result.exitCode shouldBe ExitCode.COMPILATION_ERROR
    }
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

/** Gradle test worker 는 classic classpath 로 뜬다 — `java.class.path` 가 settlement·shared-kernel 산출물을 담는다. */
private fun testClasspath(): String = System.getProperty("java.class.path")
