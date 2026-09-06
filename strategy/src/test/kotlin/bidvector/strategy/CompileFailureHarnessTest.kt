package bidvector.strategy

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
 * 컴파일 실패 하네스(1B `CompileFailureHarnessTest` 관례, scope.md 위협 모델 (c)(d)) —
 * `kotlin-compiler-embeddable`로 음성 fixture를 이 모듈의 산출 classpath 위에서 별도
 * 컴파일 단위로 돌려 실패와 기대 진단을 단언하고, 2~3 토큰만 다른 양성 쌍둥이의 컴파일
 * 성공을 함께 단언한다. 이 helper 함수들은 shared-kernel 판을 그대로 옮긴 것이다 —
 * test 소스셋 중복은 CPD `fail.source-sets=main`에 걸리지 않는다(config/quality/
 * cpd.properties).
 *
 * fixture 1 — `BudgetBound`는 `BaseAmount`만 받는다(R-BASIS-01, ④). `EstimatedAmount`를
 * 넣으면 basis 교차 대입이라 컴파일되지 않는다.
 * fixture 2 — `WatchSubject.keywordText`는 `KeywordScopeText`만 받는다(STR-02, D-5).
 * `FullScopeText` 값을 그 자리에 넣으면 컴파일되지 않는다(위협 모델 (d) — 선택을
 * 명시하는 서명까지만 강제한다, 값의 정직성은 어댑터 책임).
 */
class CompileFailureHarnessTest {
    @Test
    fun `1 BudgetBound 는 EstimatedAmount 를 받지 않고 BaseAmount 는 받는다`() {
        assertNegativeFails("1-budget-bound-cross-basis", "type mismatch")
        assertPositiveCompiles("1-budget-bound-same-basis")
    }

    @Test
    fun `1-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "1-budget-bound-cross-basis-typo",
            realDiagnosticFragment = "type mismatch",
        )
    }

    @Test
    fun `2 WatchSubject keywordText 는 FullScopeText 를 받지 않고 KeywordScopeText 는 받는다`() {
        assertNegativeFails("2-keyword-scope-cross-text", "type mismatch")
        assertPositiveCompiles("2-keyword-scope-own-text")
    }

    @Test
    fun `2-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "2-keyword-scope-cross-text-typo",
            realDiagnosticFragment = "type mismatch",
        )
    }

    /**
     * verifier r1 F-1 — `Score.of`는 `internal`이라 모듈 밖에서 못 부르고(`strategy` 저장소
     * 전체 유일 호출자는 `StrategyValidation.kt`), 이미 만들어진 `Score`를 옮겨 담는
     * `MatchScore`의 값 읽기와 `ScoreRange`의 생성·`contains`는 여전히 공개다.
     */
    @Test
    fun `3 Score of 는 모듈 밖에서 못 부르고 ScoreRange 공개 API 는 부를 수 있다`() {
        assertNegativeFails("3-score-of-outside-module", "cannot access")
        assertPositiveCompiles("3-score-range-public")
    }

    @Test
    fun `3-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "3-score-of-outside-module-typo",
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

/** Gradle test worker는 classic classpath로 뜬다 — `java.class.path`가 strategy·shared-kernel 산출물을 담는다. */
private fun testClasspath(): String = System.getProperty("java.class.path")
