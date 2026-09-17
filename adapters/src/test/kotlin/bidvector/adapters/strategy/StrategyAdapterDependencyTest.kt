package bidvector.adapters.strategy

import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.io.File

/**
 * 이 패키지가 참조해도 되는 `bidvector.*` 좌표의 루트(D-6B1-9, `EventAdapterDependencyTest`
 * 와 같은 형태·같은 이유). `strategy` 패키지는 [bidvector.workflow.strategy]
 * (`EditSessionRepository`·`EditSessionSnapshot` 등, D-6B1-7)·[bidvector.strategy]
 * (`StrategyDraft` 등, 스냅숏 nested 타입이 참조)·`shared-kernel`(값 타입)을 봐야 한다.
 * `bidvector.adapters.persistence`는 domain이 아니라 **같은 모듈의 다른 adapter
 * 패키지**다 — 세션 SQL 문자열([bidvector.adapters.persistence.Sql])이 거기 산다
 * (`PersistenceAdapterDependencyTest`의 3D allow-list(procurement·shared-kernel)는
 * 넓히지 않는다 — 이 test는 그 test와 별개다, D-6B1-9 근거). `bidvector.adapters.strategy`
 * 자신은 이 패키지의 클래스끼리(예: `JdbcEditSessionRepository` → `EditSessionRow`) 서로
 * 참조할 수 있어야 하므로 포함한다.
 */
private val ALLOWED_ROOTS =
    setOf(
        "bidvector.workflow.strategy",
        "bidvector.strategy",
        "bidvector.sharedkernel",
        "bidvector.adapters.persistence",
        "bidvector.adapters.strategy",
    )

private fun isDisallowed(importedPackage: String): Boolean =
    importedPackage.startsWith("bidvector.") &&
        ALLOWED_ROOTS.none { importedPackage == it || importedPackage.startsWith("$it.") }

/**
 * 바이트코드 내부 이름(`a/b/C`)과 이름 기반 클래스 로드가 남기는 점 표기 좌표
 * (`a.b.C`) 양쪽에서 `bidvector...` 부분을 뽑는다 — 상수 풀 전체를 훑는다.
 *
 * **verifier r3 MEDIUM-5 수정** — 이전 판(`bidvector/[A-Za-z0-9_/$]+`, 슬래시 형태만)은
 * `Class.forName("bidvector.procurement...")`처럼 이름으로 클래스를 로드하면 상수 풀에
 * 남는 점 표기 Utf8 문자열을 놓쳤다(그 로드는 런타임 classpath 에 있어 실제로 성공한다).
 * 문자 클래스에 `.`을 더해 두 표기 모두 잡는다 — 허용 루트는 어차피 `isDisallowed`가
 * 거른다.
 */
private val BIDVECTOR_INTERNAL_NAME = Regex("""bidvector[/.][A-Za-z0-9_/.$]+""")

/**
 * S-3B 계열, MEDIUM-2 시정(verifier r1) — **소스 텍스트가 아니라 컴파일된 클래스의 상수
 * 풀(constant pool)을 `javap -p -v`로 훑는다.** 이전 판(`^import (bidvector\.…)` 정규식)은
 * import 문 없이 전체 한정 좌표로 직접 참조하면(`bidvector.procurement.NoticeId(...)`처럼
 * import 없이 씀) 보지 못했고 전건 `check --rerun-tasks`가 BUILD SUCCESSFUL 이었다
 * (verifier MUT-E3). 상수 풀은 소스가 import 를 썼는지·들여쓰기가 어떤지와 무관하게
 * **컴파일러가 실제로 만든 타입 참조**를 담으므로 그 우회가 구조적으로 닫힌다.
 *
 * `javap`(JDK 번들 도구)를 외부 프로세스로 부르는 것은 이 저장소에 새 패턴이지만, 여기
 * 말고는 옮길 자리가 없다 — Kotlin/JVM 에 「소스만 보고 컴파일된 참조를 판정」하는 표준
 * API 는 없고, ASM 등 바이트코드 라이브러리를 새로 끌어오는 것은 `javap` 하나 실행하는
 * 것보다 비용이 크다(팀장 지시 "비용이 그보다 크면 코딩하지 말고 멈추고 보고" — 이 저장소가
 * 이미 갖고 있는 JDK 도구로 되므로 비용이 작은 쪽을 골랐다).
 */
class StrategyAdapterDependencyTest {
    @Test
    fun `strategy 패키지의 컴파일된 클래스는 허용 루트 밖의 bidvector 좌표를 참조하지 않는다`() {
        val classesDir = File("build/classes/kotlin/main/bidvector/adapters/strategy")
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
        isDisallowed("bidvector.procurement") shouldBe true
        isDisallowed("bidvector.decision") shouldBe true
        isDisallowed("bidvector.qualification") shouldBe true
        isDisallowed("bidvector.workflow.event") shouldBe true
        isDisallowed("bidvector.adapters.ml") shouldBe true
    }

    @Test
    fun `허용 루트(자기 패키지 포함)는 이 술어에 걸리지 않는다`() {
        isDisallowed("bidvector.workflow.strategy") shouldBe false
        isDisallowed("bidvector.strategy") shouldBe false
        isDisallowed("bidvector.sharedkernel") shouldBe false
        isDisallowed("bidvector.adapters.persistence") shouldBe false
        isDisallowed("bidvector.adapters.strategy") shouldBe false
    }
}

private fun disallowedBytecodeReferences(classFile: File): List<String> {
    val process =
        ProcessBuilder("javap", "-p", "-v", classFile.absolutePath)
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
