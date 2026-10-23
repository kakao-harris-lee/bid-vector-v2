package bidvector.app.http

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * **production 에 주체 라벨을 계정으로 되읽는 길이 0 이다**(M7/7B 계약 D-7B-2) — 구조로 잰다.
 *
 * 행동 test(`AuthenticatedAccountTest`)는 「지금의 접근자가 라벨을 안 읽는다」를 보지만, 새
 * 핸들러가 라벨을 직접 파싱하는 것은 못 본다. 그래서 `bidvector.app` 의 **컴파일된 class 전수**를
 * javap 로 읽어 세 자리를 **집합 등식**으로 잠근다(포함이 아니라 등식 — 늘어도 줄어도 붉다):
 *
 * 1. 상수 풀에 `account:` 로 시작하는 문자열을 가진 class — 라벨을 **만드는** 자리 하나뿐이다.
 *    파싱하려면 그 접두를 어딘가 적어야 하므로, 새 파싱 자리는 여기서 붉다.
 * 2. `authenticatedSubject()` 를 부르는 class — 운영자 주체를 쓰는 기존 자리 하나뿐이다.
 *    접두 없이 라벨을 읽는 변형(`substringAfter(':')` 등)은 여기서 붉다.
 * 3. 주체 속성 키 문자열을 가진 class — 그 속성을 **적거나 감사에 옮기는** 자리뿐이다. 접근자를
 *    우회해 `getAttribute` 로 라벨을 직접 읽는 변형은 여기서 붉다.
 *
 * 등식의 오른쪽은 **관측값**이다(2026-10-23 javap 실측). 정당한 새 자리가 생기면 이 집합을
 * 고치는 커밋이 그 자리를 이름으로 드러낸다 — 그것이 이 test 의 목적이다.
 */
class AccountCarrierStructureTest {
    private val outputs: Map<String, String> by lazy { appClassOutputs() }

    @Test
    fun `account 접두 문자열은 라벨을 만드는 자리 하나에만 있다`() {
        outputs.keys.shouldNotBeEmpty()

        classesWithUtf8 { it.startsWith(ACCOUNT_LABEL_PREFIX) } shouldBe LABEL_WRITERS
    }

    @Test
    fun `주체 라벨 접근자를 부르는 자리는 운영자 주체 하나뿐이다 — 계정 범위 핸들러는 없다`() {
        callersOf(SUBJECT_ACCESSOR) shouldBe SUBJECT_READERS
    }

    @Test
    fun `주체 속성 키를 아는 자리는 적는 쪽과 감사 쪽뿐이다`() {
        classesWithUtf8 { it == AUDIT_SUBJECT_ATTRIBUTE } shouldBe SUBJECT_KEY_HOLDERS
    }

    @Test
    fun `계정 범위 핸들러는 typed 접근자를 쓴다`() {
        callersOf(ACCOUNT_ACCESSOR) shouldBe ACCOUNT_READERS
    }

    /** 양성 대조 — 술어가 javap 문면에서 실제로 잡는지, 바이트코드 없이 고정한다. */
    @Test
    fun `라벨을 파싱하는 새 class 는 세 술어에 걸린다 — 양성 대조`() {
        val parser =
            listOf(
                "#12 = Utf8               account:",
                "#30 = Utf8               bidvector.http.subject",
                "4: invokestatic  #20  // Method bidvector/app/http/ErrorBodyKt.authenticatedSubject:(L…;)L…;",
            ).joinToString("\n")
        val simulated = mapOf("http/NewHandler" to parser)

        utf8Holders(simulated) { it.startsWith(ACCOUNT_LABEL_PREFIX) } shouldBe setOf("http/NewHandler")
        utf8Holders(simulated) { it == AUDIT_SUBJECT_ATTRIBUTE } shouldBe setOf("http/NewHandler")
        callers(simulated, SUBJECT_ACCESSOR) shouldBe setOf("http/NewHandler")
    }

    private fun classesWithUtf8(predicate: (String) -> Boolean): Set<String> = utf8Holders(outputs, predicate)

    private fun callersOf(method: String): Set<String> = callers(outputs, method)

    private companion object {
        const val ACCOUNT_LABEL_PREFIX = "account:"
        const val SUBJECT_ACCESSOR = "bidvector/app/http/ErrorBodyKt.authenticatedSubject:"
        const val ACCOUNT_ACCESSOR = "bidvector/app/http/AuthenticatedAccountKt.authenticatedAccount:"

        /** 라벨을 만드는 자리 — `BrowserSessionAuthenticator.subjectOf`. */
        val LABEL_WRITERS = setOf("http/BrowserSessionAuthenticator\$Companion")

        /** 운영자 주체(`OperatorId`)를 라벨에서 얻는 기존 자리 — 전략 편집의 감사 주체. */
        val SUBJECT_READERS = setOf("http/StrategyEditControllerKt")

        /** 속성을 적는 필터 · 라벨을 감사 행에 옮기는 필터 · 접근자의 정의. */
        val SUBJECT_KEY_HOLDERS =
            setOf("http/ErrorBodyKt", "http/RequestAuthenticationFilter", "http/RequestAuditFilter")

        /** 계정 범위 경로의 주체 진입점 셋. */
        val ACCOUNT_READERS =
            setOf("http/BidCaseCommandController", "http/BidCaseQueryController", "http/BidCaseHttp")
    }
}

private val UTF8_LINE = Regex("""=\s+Utf8\s+(.*)$""")

private fun utf8Holders(
    outputs: Map<String, String>,
    predicate: (String) -> Boolean,
): Set<String> =
    outputs
        .filterValues { text -> text.lineSequence().any { line -> utf8Value(line)?.let(predicate) == true } }
        .keys

private fun utf8Value(line: String): String? =
    UTF8_LINE
        .find(line)
        ?.groupValues
        ?.get(1)

/** 그 메서드를 **부르는** class — 정의한 class 자신은 빼고 센다(정의는 호출이 아니다). */
private fun callers(
    outputs: Map<String, String>,
    method: String,
): Set<String> {
    // 키는 `bidvector/app/` 아래 상대 경로다 — 메서드 소유자도 같은 모양으로 맞춘다.
    val owner = method.substringBefore('.').removePrefix("bidvector/app/")
    return outputs
        .filterKeys { it != owner }
        .filterValues { text -> text.lineSequence().any { it.contains("Method $method") } }
        .keys
}

private fun appClassOutputs(): Map<String, String> {
    val root = File("build/classes/kotlin/main/bidvector/app")
    check(root.isDirectory) { "빌드 산출물을 찾지 못했다: ${root.absolutePath} — :app:compileKotlin 선행 필요" }
    return root
        .walkTopDown()
        .filter { it.isFile && it.extension == "class" }
        .associate { it.relativeTo(root).path.removeSuffix(".class") to javap(it) }
}

private fun javap(classFile: File): String {
    val process =
        ProcessBuilder(javapPath(), "-p", "-v", "-c", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }
    return output
}

private fun javapPath(): String = File(System.getProperty("java.home"), "bin/javap").absolutePath
