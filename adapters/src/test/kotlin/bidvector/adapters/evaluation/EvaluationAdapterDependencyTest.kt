package bidvector.adapters.evaluation

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6F2-1, `StrategyAdapterDependencyTest`
 * 와 같은 형태·같은 이유). `evaluation` 패키지는 [bidvector.workflow.evaluation]
 * (`CandidateSourcePort`·`CorrelationIdFactory`)·[bidvector.workflow.event](`CorrelationId`)·
 * [bidvector.workflow.strategy](`Clock`)·[bidvector.procurement](`Notice`·`NoticeId`·
 * `NoticeStatus`·`isBiddable`)·`shared-kernel`(`NoticeRound` 등 값 타입)을 봐야 한다.
 * `bidvector.adapters.persistence`는 domain이 아니라 **같은 모듈의 다른 adapter 패키지**다
 * — SQL 문(`Sql.SELECT_OPEN_CANDIDATES`)과 `Notice` 복원 경로([reconstructNotice])가 거기
 * 산다(`PersistenceAdapterDependencyTest`·`StrategyAdapterDependencyTest`의 allow-list를
 * 넓히지 않는다 — 이 test는 그 test들과 별개다). `bidvector.adapters.evaluation` 자신은
 * 이 패키지의 클래스끼리 서로 참조할 수 있어야 하므로 포함한다.
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.evaluation",
        "bidvector.workflow.event",
        "bidvector.workflow.strategy",
        "bidvector.procurement",
        "bidvector.sharedkernel",
        "bidvector.adapters.persistence",
        "bidvector.adapters.evaluation",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

/**
 * 바이트코드 내부 이름(`a/b/C`)과 이름 기반 클래스 로드가 남기는 점 표기 좌표(`a.b.C`) 양쪽에서
 * `bidvector...` 부분을 뽑는다 — 상수 풀 전체를 훑는다(`StrategyAdapterDependencyTest`와 같은
 * 정규식·같은 근거 — verifier r3 MEDIUM-5).
 */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * D-6F2-1 — **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v`로 훑는다.**
 * import 문 없이 전체 한정 좌표로 직접 참조하는 우회(scope.md 우회 6)를 소스 텍스트 정규식
 * 형태(`EventAdapterDependencyTest`)는 못 본다(6F-1 verifier MUT-E3 실측) — 신설 게이트는
 * 구조로 닫는 쪽만 쓴다(CLAUDE.md 「게이트 술어는 문자열이 아니라 구조로」).
 */
class EvaluationAdapterDependencyTest {
    @Test
    fun `evaluation 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/evaluation")
        check(classesDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        classFiles.shouldNotBeEmpty() // 빈 디렉터리를 "위반 없음"으로 오판하지 않는다.

        val violations = classFiles.flatMap(::disallowedBytecodeReferences).distinct()
        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다(문자열 판정 자체는 이 값으로 잰다). */
    @Test
    fun `허용 밖 domain 모듈을 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.strategy") shouldBe true
        isDisallowed("bidvector.workflow.ml") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.evaluation") shouldBe false
        isDisallowed("bidvector.workflow.event") shouldBe false
        isDisallowed("bidvector.workflow.strategy") shouldBe false
        isDisallowed("bidvector.procurement") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.evaluation") shouldBe false
    }

    /**
     * D-6F2-9 ② 참조 단언(verifier r1 HIGH-1 수정) — ①(거동 등식, `JdbcCandidateSourceTest`)
     * 은 SQL이 **낸 결과값**만 잰다. `biddableStatuses()`를 안 쓰고 우연히 같은 리터럴
     * (`"Open"`·`"Renoticed"`)을 SQL에 하드코딩해도 그 값은 통과하므로, 여기서는 컴파일된
     * `JdbcCandidateSource`의 상수 풀이 **실제로 `biddableStatuses`를 참조**하는지를 잰다 —
     * SQL 문자열을 grep 하지 않는다(스타일 하나로 열리는 문자열 술어를 쓰지 않는다,
     * CLAUDE.md 「게이트 술어는 구조로」). 이 상수 풀은 컴파일러가 실제로 만든 참조라
     * `biddableStatuses()` 호출을 지우면(리터럴 배열로 되돌리면) 이 이름 자체가 상수 풀에서
     * 사라진다 — 소스 문법과 무관하게 참조 유무만 재는 구조 게이트다.
     */
    @Test
    fun `JdbcCandidateSource 의 컴파일된 클래스는 biddableStatuses 를 참조한다`() {
        val classFile = File("build/classes/kotlin/main/bidvector/adapters/evaluation/JdbcCandidateSource.class")
        check(classFile.isFile) {
            "빌드 산출물을 찾지 못했다: ${classFile.absolutePath} — :adapters:compileKotlin 선행 필요"
        }

        javapOutput(classFile) shouldContain "biddableStatuses"
    }
}

/**
 * `javap`를 OS `PATH`의 이름 조회가 아니라 **실행 중인 JVM의 `java.home`**에서 해석한다
 * (`StrategyAdapterDependencyTest`와 같은 이유 — CI의 JDK 21과 다른 `javap`를 부를 여지를
 * 없앤다).
 */
private val javapExecutable: String by lazy {
    val javaHome = System.getProperty("java.home")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
}

/** `javap -p -v`의 원문 출력 — [disallowedBytecodeReferences]와 참조 단언 test가 공유한다. */
private fun javapOutput(classFile: File): String {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }
    return output
}

private fun disallowedBytecodeReferences(classFile: File): List<String> =
    BIDVECTOR_INTERNAL_NAME
        .findAll(javapOutput(classFile))
        .map { it.value.replace('/', '.') }
        .filter(::isDisallowed)
        .toList()
