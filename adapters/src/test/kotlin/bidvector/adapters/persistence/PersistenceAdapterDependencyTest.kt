package bidvector.adapters.persistence

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(`ProfileAdapterDependencyTest`·
 * `StrategyAdapterDependencyTest`와 같은 형태·같은 이유). persistence 가 보는 domain 은
 * [bidvector.procurement](3A canonical fact)와 [bidvector.sharedkernel] 둘이다 —
 * 앞 판의 주장도 이 둘이었다. `bidvector.adapters.snapshot`·`bidvector.adapters.persistence`
 * 는 domain 이 아니라 **같은 모듈의 adapter 패키지**다(jsonb 파싱 헬퍼와 이 패키지 자신).
 *
 * **앞 판보다 주장이 넓다.** 금지 목록(층 밖 domain 루트 여섯)은 열거하지 않은 좌표를
 * 전부 허용했다 — 새 domain 모듈이 생기면 그 목록이 조용히 낡는다. 허용 목록은
 * 열거되지 않은 좌표를 전부 거부하므로 그 낡음이 열거가 아니라 구성으로 닫힌다.
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.procurement",
        "bidvector.sharedkernel",
        "bidvector.adapters.persistence",
        "bidvector.adapters.snapshot",
    )

private fun isDisallowed(coordinate: String): Boolean =
    coordinate.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { coordinate == it || coordinate.startsWith("$it.") }

/**
 * 바이트코드 내부 이름(`a/b/C`)과 이름 기반 클래스 로드가 남기는 점 표기 좌표(`a.b.C`) 양쪽에서
 * `bidvector...` 부분을 뽑는다 — 상수 풀 전체를 훑는다(형제 dependency test 들과 같은
 * 정규식·같은 근거).
 */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * `OPEN-PERSISTENCE-GATE-PREDICATE-TYPE` 처방 — **소스 텍스트가 아니라 컴파일된 클래스의
 * 상수 풀을 `javap -p -v`로 훑는다.** 앞 판은 `^import (bidvector\.…)` 정규식이라 import 문
 * 없이 전체 한정 좌표로 직접 참조하는 우회를 통과시켰다(D-6F5-18 이 그 우회를 실측하고
 * 「처방은 범위가 아니라 **술어 종류**」로 이 OPEN 을 세웠다). 형제 여덟과 같은 층으로
 * 옮기면 우회하려면 게이트 자체를 바꿔야 한다(CLAUDE.md 「게이트 술어는 문자열이 아니라
 * 구조로」).
 *
 * **이 test 가 재는 것**: 이 패키지의 class 가 허용 루트 **밖**의 `bidvector` 좌표를
 * 참조하지 않는다. 재지 않는 것: 다른 패키지·모듈(이 test 의 모집단이 아니다) · 컴파일 시
 * 인라인돼 상수 풀에 흔적을 남기지 않는 `const val`(형제 여덟과 공유하는 등재된 사각,
 * `OPEN-BYTECODE-GATE-CONST-VAL-BLINDSPOT`) · 값이 흐르는 경로 일반.
 */
class PersistenceAdapterDependencyTest {
    @Test
    fun `persistence 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/persistence")
        check(classesDir.isDirectory) {
            "빌드 산출물을 찾지 못했다: ${classesDir.absolutePath} — :adapters:compileKotlin 선행 필요"
        }
        val classFiles = classesDir.walkTopDown().filter { it.isFile && it.extension == "class" }.toList()
        classFiles.shouldNotBeEmpty() // 빈 디렉터리를 "위반 없음"으로 오판하지 않는다.

        val violations = classFiles.flatMap(::disallowedBytecodeReferences).distinct()
        violations shouldBe emptyList()
    }

    /**
     * 양성 대조 — 술어가 늘 통과만 하는 회귀를 막는다. 앞 판의 금지 목록 여섯을 그대로 들어,
     * 술어 종류를 바꾸면서 **앞 판이 잡던 것을 잃지 않았음**을 같은 자리에서 보인다.
     */
    @Test
    fun `층 밖 domain 루트와 허용 밖 adapter 패키지는 이 술어에 걸린다 — 양성 대조`() {
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.strategy") shouldBe true
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.settlement") shouldBe true
        isDisallowed("bidvector.workflow") shouldBe true
        isDisallowed("bidvector.app") shouldBe true
        isDisallowed("bidvector.workflow.evaluation") shouldBe true
        isDisallowed("bidvector.adapters.evaluation") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.procurement") shouldBe false
        isDisallowed("bidvector.procurement.Notice") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.sharedkernel.export") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.snapshot") shouldBe false
    }

    /**
     * 접두 일치가 **세그먼트 경계**에서만 성립함을 고정한다 — `startsWith(root)` 만 쓰면
     * `bidvector.procurementX` 같은 이웃 좌표가 허용으로 새고, 그 새는 이름은 지어낼 수 있다.
     */
    @Test
    fun `허용 루트의 이름 접두를 공유하는 다른 패키지는 허용되지 않는다`() {
        isDisallowed("bidvector.procurementlegacy") shouldBe true
        isDisallowed("bidvector.adapters.persistencex") shouldBe true
    }
}

/**
 * `javap`를 OS `PATH`의 이름 조회가 아니라 **실행 중인 JVM의 `java.home`**에서 해석한다
 * (형제 dependency test 들과 같은 이유 — CI의 JDK 21과 다른 `javap`를 부를 여지를 없앤다).
 */
private val javapExecutable: String by lazy {
    val javaHome = System.getProperty("java.home")
    val isWindows = System.getProperty("os.name").startsWith("Windows", ignoreCase = true)
    File(javaHome, "bin/${if (isWindows) "javap.exe" else "javap"}").absolutePath
}

private fun disallowedBytecodeReferences(classFile: File): List<String> {
    val process =
        ProcessBuilder(javapExecutable, "-p", "-v", classFile.absolutePath)
            .redirectErrorStream(true)
            .start()
    val output = process.inputStream.bufferedReader().readText()
    val exitCode = process.waitFor()
    check(exitCode == 0) { "javap 실행 실패(exit=$exitCode): ${classFile.name}\n$output" }

    return BIDVECTOR_INTERNAL_NAME
        .findAll(output)
        .map { it.value.replace('/', '.') }
        .filter(::isDisallowed)
        .toList()
}
