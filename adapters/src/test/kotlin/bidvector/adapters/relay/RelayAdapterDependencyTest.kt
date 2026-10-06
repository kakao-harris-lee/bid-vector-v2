package bidvector.adapters.relay

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6F10-18 ①,
 * `EvaluationAdapterDependencyTest`·`StrategyAdapterDependencyTest` 와 같은 형태·같은 이유).
 *
 * **이 패키지가 존재하는 이유가 이 목록이다.** relay 조립은 `workflow.notification`
 * (`DispatchNotification`·정책·환경·`RelayOutboxNotifications`)과 `adapters.event`
 * (`JdbcOutboxPort`·`JdbcInboxPort`·lease·`ConsumerTransactions`)를 **둘 다** 이름으로 불러야
 * 한다. `adapters.event` 에 두면 `EventAdapterDependencyTest` 가 막는데, 그 게이트는
 * `workflow.notification` 을 허용 루트 밖으로 두고 **양성 대조로 명시 단언**한다 — codec 이
 * 알림 타입을 보지 않는다는 것이 그 게이트의 뜻이다. 그 단언을 지우는 것(게이트의 자기 검사를
 * 약화)보다 간선을 새 패키지 안에 가두는 편이 좁다.
 *
 * `workflow.strategy` 는 `OperatorId`(`RelayTarget.owner`), `sharedkernel` 은 정책 해소 값
 * 타입, `adapters.persistence` 는 트랜잭션 경계([bidvector.adapters.persistence.TransactionBoundary])다.
 * `workflow.evaluation`·`procurement`·`decision` 은 **목록에 없다** — relay 는 판정을 다시
 * 조립하지 않고 payload 투영만 읽는다.
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.event",
        "bidvector.workflow.notification",
        "bidvector.workflow.strategy",
        "bidvector.sharedkernel",
        "bidvector.adapters.event",
        "bidvector.adapters.persistence",
        "bidvector.adapters.relay",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

/**
 * 바이트코드 내부 이름(`a/b/C`)과 이름 기반 클래스 로드가 남기는 점 표기 좌표(`a.b.C`) 양쪽에서
 * `bidvector...` 부분을 뽑는다 — 상수 풀 전체를 훑는다.
 */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * **소스 텍스트가 아니라 컴파일된 클래스의 상수 풀을 `javap -p -v` 로 훑는다**(D-6F2-1 과 같은
 * 근거). import 문 없이 전체 한정 좌표로 직접 참조하는 우회를 소스 텍스트 정규식 형태는 보지
 * 못한다(6F-1 verifier MUT-E3 실측) — 신설 게이트는 구조로 닫는 쪽만 쓴다.
 */
class RelayAdapterDependencyTest {
    @Test
    fun `relay 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/relay")
        check(classesDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        classFiles.shouldNotBeEmpty() // 빈 디렉터리를 "위반 없음"으로 오판하지 않는다.

        val violations = classFiles.flatMap(::disallowedBytecodeReferences).distinct()

        violations shouldBe emptyList()
    }

    /** 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다. */
    @Test
    fun `허용 밖 좌표를 심은 표본은 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.workflow.evaluation") shouldBe true
        isDisallowed("bidvector.procurement") shouldBe true
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
        isDisallowed("bidvector.app") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.event") shouldBe false
        isDisallowed("bidvector.workflow.notification") shouldBe false
        isDisallowed("bidvector.workflow.strategy") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.event") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.relay") shouldBe false
    }

    /**
     * D-6F10-2 「사본 SQL 0」의 참조 단언 — 조립은 종단 전이를 **port 로만** 지난다. 이
     * 패키지의 어느 class 도 `EventSql` 을 참조하지 않는다(6D-1 의 test relay 가 raw
     * `MARK_DELIVERED` 를 직접 실행한 자리를 반복하지 않는다). 상수 풀이라 import 를 쓰든
     * 전체 한정 좌표를 쓰든 같다.
     */
    @Test
    fun `relay 패키지는 전이 SQL 상수를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/relay")
        check(classesDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        classFiles.shouldNotBeEmpty()

        val sqlReferences =
            classFiles.flatMap { classFile ->
                BIDVECTOR_INTERNAL_NAME
                    .findAll(javapOutput(classFile))
                    .map { it.value.replace('/', '.') }
                    .filter { it.startsWith("bidvector.adapters.event.EventSql") }
                    .toList()
            }

        sqlReferences shouldBe emptyList()
    }
}

/**
 * `javap` 를 OS `PATH` 의 이름 조회가 아니라 **실행 중인 JVM 의 `java.home`** 에서 해석한다
 * (`StrategyAdapterDependencyTest` 와 같은 이유 — CI 의 JDK 21 과 다른 `javap` 를 부를 여지를
 * 없앤다).
 */
private val javapExecutable: String by lazy {
    val javaHome = System.getProperty("java.home")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
}

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
