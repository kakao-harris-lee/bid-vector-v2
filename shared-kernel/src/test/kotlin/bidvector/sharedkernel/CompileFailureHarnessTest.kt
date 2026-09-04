package bidvector.sharedkernel

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
 * 컴파일 실패 하네스 — `ADR 0002` §6 ①이 요구하는 "상호 대입이 컴파일되지 않는다"를
 * 기계로 증명한다(설계 검토 §4.9). `kotlin-compiler-embeddable` 로 음성 fixture를 이 모듈의
 * 산출 classpath 위에서 **별도 컴파일 단위**로 돌려 실패와 기대 진단을 단언하고, 음성마다
 * 2~3 토큰(함수명·변수명·import)만 다른 양성 쌍둥이가 성공함을 함께 단언한다(verifier r1
 * L-3 정정 — 「한 토큰만」은 부정확했다) — exit code만 보면 오타도 통과시킨다
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
        assertNegativeFails("3-multiplication-rule", "receiver type mismatch")
        assertPositiveCompiles("3-multiplication-rule")
    }

    /**
     * verifier r1 M-2 — 식별자 부분 문자열(`"times"`)은 오타만 낸 변이(`timesTypo`)도
     * 만족시킨다(그 진단문에 "times"가 부분 문자열로 들어 있다). "receiver type mismatch"는
     * 후보 시그니처가 있는데 receiver 타입이 안 맞아 나는 진단 **종류**라 오타(후보 자체가
     * 없음)의 "unresolved reference" 진단에는 나타나지 않는다 — 실측으로 확인한다.
     */
    @Test
    fun `3-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "3-multiplication-rule-typo",
            realDiagnosticFragment = "receiver type mismatch",
        )
    }

    @Test
    fun `4 raw Double 로는 금액을 못 만들고 Long 으로는 만들 수 있다`() {
        assertNegativeFails("4-raw-double-construction", "type mismatch")
        assertPositiveCompiles("4-raw-long-construction")
    }

    @Test
    fun `5 internal 원 단위 접근자는 모듈 밖에서 못 부르고 export 는 부를 수 있다`() {
        assertNegativeFails("5-internal-accessor", "cannot access")
        assertPositiveCompiles("5-export")
    }

    /**
     * verifier r1 M-2 — `"amount"`는 오타 변이(`amountTypo`)의 진단문에도 부분 문자열로
     * 들어 있다("unresolved reference 'amountTypo'"의 "amountTypo"가 "amount"를 포함).
     * "cannot access"는 선언이 실존하고 접근만 막힌 경우의 진단 종류라 오타(선언 자체가
     * 없음)의 "unresolved reference" 진단에는 나타나지 않는다 — 실측으로 확인한다.
     */
    @Test
    fun `5-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "5-internal-accessor-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    @Test
    fun `6 파생 Money 는 모듈 밖에서 직접 생성할 수 없고 입력 Money 는 생성할 수 있다 (verifier r1 H-1)`() {
        assertNegativeFails("6-derived-money-direct-construction", "cannot access")
        assertPositiveCompiles("6-input-money-direct-construction")
    }

    /**
     * 규칙(verifier r1 M-2) — 모든 음성 fixture 는 변이 쌍둥이를 갖는다: 정당한 양성
     * 경로에 오타만 넣은 코드가 그 진단 단편을 만족시키지 않아야 한다(verifier r2 L-7).
     */
    @Test
    fun `6-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "6-derived-money-direct-construction-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    @Test
    fun `7 vat 고정 Money 는 vatTreatment 인자를 받지 않고 인자 없이는 생성할 수 있다 (운영자 결정 B9)`() {
        assertNegativeFails("7-vat-fixed-money-explicit-vat", "too many arguments for")
        assertPositiveCompiles("7-vat-fixed-money-no-vat-arg")
    }

    @Test
    fun `7-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다 (verifier r2 L-7)`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "7-vat-fixed-money-explicit-vat-typo",
            realDiagnosticFragment = "too many arguments for",
        )
    }

    /**
     * verifier r2 H-3 — `DerivationRecord`의 생성자만 닫고 그것을 나르는 [Derived]를 열어
     * 두면 값과 계산 정책 version의 결속(decision 17)이 `copy(derivedFrom = …)`로 깨진다.
     * 읽기(`positive-8`)는 여전히 열려 있어야 한다 — 소비자는 값을 읽어야 한다.
     */
    @Test
    fun `8 Derived 의 기록은 copy 로 교체할 수 없고 읽을 수는 있다 (verifier r2 H-3)`() {
        assertNegativeFails("8-derived-record-swap", "cannot access")
        assertPositiveCompiles("8-derived-record-read")
    }

    @Test
    fun `8-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "8-derived-record-swap-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    /** verifier r2 H-3 — `Derived(a.value, b.derivedFrom)`로 값과 다른 계산의 기록을 갈아 끼워 위조한다. */
    @Test
    fun `9 Derived 는 값과 기록을 재조합해 위조할 수 없고 값을 읽을 수는 있다 (verifier r2 H-3)`() {
        assertNegativeFails("9-derived-recombine-forge", "cannot access")
        assertPositiveCompiles("9-derived-value-read")
    }

    @Test
    fun `9-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "9-derived-recombine-forge-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    /**
     * verifier r2 H-3 — `Derived`만 닫고 [Measurement.Measured]를 열어 두면 임의 타입 값을
     * 진짜 `DerivationRecord`로 감싼 `Derived`를 다시 `Measurement.Measured`로 포장해
     * 모듈 밖에서 "판정"을 조립할 수 있다. `Unmeasurable` 형제는 `ReasonCode`만 나르므로
     * 위조 대상이 없어 계속 공개다(positive-10).
     */
    @Test
    fun `10 Measurement Measured 는 임의 타입을 진짜 기록으로 포장할 수 없고 Unmeasurable 은 여전히 공개다 (verifier r2 H-3)`() {
        assertNegativeFails("10-measured-arbitrary-type-wrap", "cannot access")
        assertPositiveCompiles("10-unmeasurable-still-public")
    }

    @Test
    fun `10-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "10-measured-arbitrary-type-wrap-typo",
            realDiagnosticFragment = "cannot access",
        )
    }

    /**
     * verifier r4 H-1 — 제네릭 `fun <T : Money> compareKnownVat(left: T, right: T)` 는
     * Kotlin 이 `T` 를 두 인자의 최소 상위 타입(LUB)으로 추론해 basis 가 다른 두 `Money`
     * 값도 `T = Money` 로 컴파일시켰다(회귀 — `MoneyTest` 의 옛 test 가 같은 타입 쌍만
     * 불러 이 구멍을 놓쳤다). 타입별 오버로드 여섯으로 되돌린 지금은 이 fixture(basis
     * 교차 쌍 호출)가 **모듈 밖에서도** 컴파일되지 않아야 한다 — 회귀가 다시 열리면 이
     * test 가 초록으로 남아 알려주지 못하므로, 그 자체가 이 fixture 의 존재 이유다.
     */
    @Test
    fun `11 compareKnownVat 는 basis 교차 쌍을 받지 않고 같은 basis 쌍은 받는다 (verifier r4 H-1)`() {
        assertNegativeFails("11-cross-basis-compare", "type mismatch")
        assertPositiveCompiles("11-same-basis-compare")
    }

    @Test
    fun `11-M2 계약 위반 없는 오타 변이는 새 단언을 만족시키지 않는다`() {
        assertMutantDoesNotMatchRealFragment(
            mutantFixtureName = "11-cross-basis-compare-typo",
            realDiagnosticFragment = "type mismatch",
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

/**
 * 변이(오타만 넣고 계약 위반은 없는 fixture)가 여전히 컴파일에 실패하되(참조 자체가
 * 없으므로), 실 위반 fixture 를 식별하는 진단 단편은 **만족시키지 않음**을 확인한다
 * (verifier r1 M-2) — 단언이 종류를 보는지, 부분 문자열만 보는지를 가르는 실측이다.
 */
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
