package bidvector.adapters.event

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
 * verifier r1 M-2 시정 — 4C-1 이 닫은 값 획득 축(설계 검토 (2) 우회 1, (2b) 마지막 행)이
 * `workflow` 밖에서 여전히 닫혀 있는지 상시 probe 로 증명한다(1B `CompileFailureHarnessTest`
 * 관례 — `kotlin-compiler-embeddable`로 이 모듈의 test classpath 위에서 별도 컴파일 단위를
 * 돌린다). `adapters`가 이미 `workflow` 밖의 실제 소비 모듈이므로 별도 fixture 모듈을 짓지
 * 않는다 — 이 test worker의 classpath 자체가 「workflow 밖에서 컴파일」이다.
 *
 * 여섯 access point(verifier r1이 수동으로 확인한 넷 + **verifier r2 M-4 시정**으로
 * 더한 둘): [bidvector.workflow.event.EventEnvelope] 생성자·`transitionOutbox`·
 * `OutboxTransition.ToDelivered` 생성자·`OutboxEntry.restore`(폐기 축)와
 * `newEnvelope`·`forStrategyUpdated`(**획득 축** — 둘 다 완성된 봉투를 돌려주는
 * `internal` 팩토리라, r1의 probe 넷만으로는 이 두 함수가 `internal`→`public`으로
 * 한 단어 바뀌어도 이 test가 초록이었다: `app`이 `forStrategyUpdated`를 통해 진짜
 * 봉투를 조립할 수 있었다, verifier r2 실측). 양성 대조는 하나를 공유한다 —
 * `ClaimedOutboxRow`(공개 생성자)가 정상 컴파일돼 harness 자체가 항상 실패만 내는
 * 고장이 아님을 확인한다(1B 관례의 sanity 목적과 같다).
 */
class EventInternalClosureCompileTest {
    @Test
    fun `EventEnvelope 생성자는 workflow 밖에서 internal 이고 ClaimedOutboxRow 는 공개다`() {
        assertNegativeFails("1-event-envelope-ctor")
        assertPositiveCompiles()
    }

    @Test
    fun `transitionOutbox 는 workflow 밖에서 internal 이다`() {
        assertNegativeFails("2-transition-outbox")
        assertPositiveCompiles()
    }

    @Test
    fun `OutboxTransition ToDelivered 생성자는 workflow 밖에서 internal 이다`() {
        assertNegativeFails("3-outbox-transition-to-delivered-ctor")
        assertPositiveCompiles()
    }

    @Test
    fun `OutboxEntry restore 는 workflow 밖에서 internal 이다`() {
        assertNegativeFails("4-outbox-entry-restore")
        assertPositiveCompiles()
    }

    @Test
    fun `newEnvelope 는 workflow 밖에서 internal 이다`() {
        assertNegativeFails("5-new-envelope")
        assertPositiveCompiles()
    }

    @Test
    fun `forStrategyUpdated 는 workflow 밖에서 internal 이다`() {
        assertNegativeFails("6-for-strategy-updated")
        assertPositiveCompiles()
    }
}

private fun assertNegativeFails(fixtureName: String) {
    val result = compileFixture("negative-$fixtureName")
    io.kotest.assertions.withClue(result.output) {
        result.exitCode shouldBe ExitCode.COMPILATION_ERROR
    }
    result.output shouldContain "cannot access"
}

private fun assertPositiveCompiles() {
    val result = compileFixture("positive-claimed-outbox-row-public")
    io.kotest.assertions.withClue(result.output) {
        result.exitCode shouldBe ExitCode.OK
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

/** Gradle test worker는 classic classpath로 뜬다 — `java.class.path`가 workflow 산출물을 담는다. */
private fun testClasspath(): String = System.getProperty("java.class.path")
